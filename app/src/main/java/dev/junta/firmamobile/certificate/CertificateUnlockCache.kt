package dev.junta.firmamobile.certificate

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CachedCertificateUnlock internal constructor(
    val password: CharArray,
    val expiresAt: Instant,
    internal val lease: CertificateUnlockLease,
) : Closeable {
    @Synchronized
    override fun close() {
        password.fill('\u0000')
    }
}

interface CertificateUnlockCache {
    suspend fun store(
        reference: StoredCertificateReference,
        password: CharArray,
        issuedAt: Instant,
        expiresAt: Instant,
        observedAtMonotonicNanos: Long,
    ): Boolean

    suspend fun restore(
        reference: StoredCertificateReference,
        now: Instant,
    ): CachedCertificateUnlock?

    fun clear()
}

object NoOpCertificateUnlockCache : CertificateUnlockCache {
    override suspend fun store(
        reference: StoredCertificateReference,
        password: CharArray,
        issuedAt: Instant,
        expiresAt: Instant,
        observedAtMonotonicNanos: Long,
    ): Boolean = false

    override suspend fun restore(
        reference: StoredCertificateReference,
        now: Instant,
    ): CachedCertificateUnlock? = null

    override fun clear() = Unit
}

interface CertificateUnlockRecordStorage {
    fun read(): ByteArray?

    fun write(record: ByteArray): Boolean

    fun clear()
}

fun interface CertificateUnlockKeyProvider {
    fun getOrCreate(): SecretKey
}

internal data class CertificateUnlockBootTime(
    val bootCount: Int,
    val elapsedRealtimeNanos: Long,
) {
    init {
        require(bootCount >= 0)
        require(elapsedRealtimeNanos >= 0L)
    }
}

internal fun interface CertificateUnlockBootTimeSource {
    fun read(): CertificateUnlockBootTime?
}

class EncryptedCertificateUnlockCache internal constructor(
    private val storage: CertificateUnlockRecordStorage,
    private val keyProvider: CertificateUnlockKeyProvider,
    private val bootTimeSource: CertificateUnlockBootTimeSource,
    private val sessionMonotonicNanos: () -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CertificateUnlockCache {
    private val invalidationGeneration = AtomicLong(0)

    override suspend fun store(
        reference: StoredCertificateReference,
        password: CharArray,
        issuedAt: Instant,
        expiresAt: Instant,
        observedAtMonotonicNanos: Long,
    ): Boolean {
        val storeGeneration = invalidationGeneration.get()
        return withContext(ioDispatcher) {
            storeOnIo(
                reference,
                password,
                issuedAt,
                expiresAt,
                observedAtMonotonicNanos,
                storeGeneration,
            )
        }
    }

    override suspend fun restore(
        reference: StoredCertificateReference,
        now: Instant,
    ): CachedCertificateUnlock? {
        val restoreGeneration = invalidationGeneration.get()
        return withContext(ioDispatcher) {
            restoreOnIo(reference, now, restoreGeneration)
        }
    }

    override fun clear() {
        invalidationGeneration.incrementAndGet()
        runCatching(storage::clear)
    }

    private fun storeOnIo(
        reference: StoredCertificateReference,
        password: CharArray,
        issuedAt: Instant,
        expiresAt: Instant,
        observedAtMonotonicNanos: Long,
        storeGeneration: Long,
    ): Boolean {
        if (!validRetention(issuedAt, expiresAt) || observedAtMonotonicNanos < 0L ||
            password.isEmpty() || password.size > MAX_PASSWORD_CHARS
        ) {
            clear()
            return false
        }
        var plainBytes: ByteArray? = null
        var referenceDigest: ByteArray? = null
        var authenticatedHeader: ByteArray? = null
        var cipherText: ByteArray? = null
        var record: ByteArray? = null
        return try {
            val currentBootTime = bootTimeSource.read() ?: run {
                clear()
                return false
            }
            if (currentBootTime.elapsedRealtimeNanos < observedAtMonotonicNanos) {
                clear()
                return false
            }
            val retentionNanos = Duration.between(issuedAt, expiresAt).toNanos()
            val ageAtStoreNanos = currentBootTime.elapsedRealtimeNanos - observedAtMonotonicNanos
            if (ageAtStoreNanos >= retentionNanos) {
                clear()
                return false
            }
            val bootTime = currentBootTime.copy(
                elapsedRealtimeNanos = observedAtMonotonicNanos,
            )
            plainBytes = password.toUtf8Bytes()
            if (plainBytes.isEmpty() || plainBytes.size > MAX_PASSWORD_BYTES) {
                clear()
                return false
            }
            referenceDigest = reference.digest()
            authenticatedHeader = createAuthenticatedHeader(
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                bootTime = bootTime,
                referenceDigest = referenceDigest,
            )
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreate())
            val iv = cipher.iv
            require(iv.size == GCM_IV_BYTES)
            cipher.updateAAD(authenticatedHeader)
            cipherText = cipher.doFinal(plainBytes)
            require(cipherText.size in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES)
            record = createRecord(authenticatedHeader, iv, cipherText)
            val written = storage.write(record)
            when {
                !written -> {
                    clear()
                    false
                }
                invalidationGeneration.get() != storeGeneration -> {
                    clear()
                    false
                }
                else -> true
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            clear()
            false
        } finally {
            plainBytes?.fill(0)
            referenceDigest?.fill(0)
            authenticatedHeader?.fill(0)
            cipherText?.fill(0)
            record?.fill(0)
        }
    }

    private fun restoreOnIo(
        reference: StoredCertificateReference,
        now: Instant,
        restoreGeneration: Long,
    ): CachedCertificateUnlock? {
        val rawRecord = try {
            storage.read()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            clear()
            return null
        } ?: return null
        var parsed: ParsedRecord? = null
        var expectedDigest: ByteArray? = null
        var plainBytes: ByteArray? = null
        return try {
            if (invalidationGeneration.get() != restoreGeneration) return null
            val sessionLeaseObservedAtNanos = sessionMonotonicNanos()
            parsed = parseRecord(rawRecord)
            val issuedAt = Instant.ofEpochMilli(parsed.issuedAtEpochMillis)
            val expiresAt = Instant.ofEpochMilli(parsed.expiresAtEpochMillis)
            if (!validRetention(issuedAt, expiresAt) || now.isBefore(issuedAt) ||
                !now.isBefore(expiresAt)
            ) {
                clear()
                return null
            }
            val currentBootTime = bootTimeSource.read() ?: run {
                clear()
                return null
            }
            val retentionNanos = Duration.between(issuedAt, expiresAt).toNanos()
            if (currentBootTime.bootCount != parsed.bootCount ||
                currentBootTime.elapsedRealtimeNanos < parsed.elapsedRealtimeNanos
            ) {
                clear()
                return null
            }
            val monotonicAgeNanos =
                currentBootTime.elapsedRealtimeNanos - parsed.elapsedRealtimeNanos
            if (monotonicAgeNanos >= retentionNanos) {
                clear()
                return null
            }
            val remainingNanos = retentionNanos - monotonicAgeNanos
            val lease = CertificateUnlockLease(
                expiresAt = expiresAt,
                observedAtMonotonicNanos = sessionLeaseObservedAtNanos,
                lifetimeNanos = remainingNanos,
            )
            expectedDigest = reference.digest()
            if (!MessageDigest.isEqual(expectedDigest, parsed.referenceDigest)) {
                clear()
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider.getOrCreate(),
                GCMParameterSpec(GCM_TAG_BITS, parsed.iv),
            )
            cipher.updateAAD(parsed.authenticatedHeader)
            plainBytes = cipher.doFinal(parsed.cipherText)
            if (plainBytes.isEmpty() || plainBytes.size > MAX_PASSWORD_BYTES) {
                clear()
                return null
            }
            val password = plainBytes.toUtf8Chars()
            if (password.isEmpty() || password.size > MAX_PASSWORD_CHARS) {
                password.fill('\u0000')
                clear()
                return null
            }
            if (invalidationGeneration.get() != restoreGeneration) {
                password.fill('\u0000')
                return null
            }
            CachedCertificateUnlock(password, expiresAt, lease)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            clear()
            null
        } finally {
            rawRecord.fill(0)
            parsed?.clear()
            expectedDigest?.fill(0)
            plainBytes?.fill(0)
        }
    }

    private fun validRetention(issuedAt: Instant, expiresAt: Instant): Boolean = runCatching {
        expiresAt.isAfter(issuedAt) && Duration.between(issuedAt, expiresAt) <= MAX_RETENTION
    }.getOrDefault(false)

    private data class ParsedRecord(
        val issuedAtEpochMillis: Long,
        val expiresAtEpochMillis: Long,
        val bootCount: Int,
        val elapsedRealtimeNanos: Long,
        val referenceDigest: ByteArray,
        val iv: ByteArray,
        val cipherText: ByteArray,
        val authenticatedHeader: ByteArray,
    ) {
        fun clear() {
            referenceDigest.fill(0)
            iv.fill(0)
            cipherText.fill(0)
            authenticatedHeader.fill(0)
        }
    }

    private companion object {
        val MAGIC = byteArrayOf('J'.code.toByte(), 'F'.code.toByte(), 'M'.code.toByte(),
            'U'.code.toByte(), 'C'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(),
            '2'.code.toByte())
        val MAX_RETENTION: Duration = Duration.ofHours(24)
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SHA_256 = "SHA-256"
        const val DIGEST_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val MIN_CIPHERTEXT_BYTES = 17
        const val MAX_CIPHERTEXT_BYTES = 8192
        const val MAX_PASSWORD_CHARS = 2048
        const val MAX_PASSWORD_BYTES = 8192
        const val AUTHENTICATED_HEADER_BYTES =
            8 + 8 + 8 + Int.SIZE_BYTES + Long.SIZE_BYTES + DIGEST_BYTES
        const val LENGTH_FIELDS_BYTES = 8
        const val MIN_RECORD_BYTES = AUTHENTICATED_HEADER_BYTES + LENGTH_FIELDS_BYTES +
            GCM_IV_BYTES + MIN_CIPHERTEXT_BYTES

        fun createAuthenticatedHeader(
            issuedAt: Instant,
            expiresAt: Instant,
            bootTime: CertificateUnlockBootTime,
            referenceDigest: ByteArray,
        ): ByteArray {
            require(referenceDigest.size == DIGEST_BYTES)
            return ByteBuffer.allocate(AUTHENTICATED_HEADER_BYTES)
                .put(MAGIC)
                .putLong(issuedAt.toEpochMilli())
                .putLong(expiresAt.toEpochMilli())
                .putInt(bootTime.bootCount)
                .putLong(bootTime.elapsedRealtimeNanos)
                .put(referenceDigest)
                .array()
        }

        fun createRecord(
            authenticatedHeader: ByteArray,
            iv: ByteArray,
            cipherText: ByteArray,
        ): ByteArray {
            require(authenticatedHeader.size == AUTHENTICATED_HEADER_BYTES)
            require(iv.size == GCM_IV_BYTES)
            return ByteBuffer.allocate(
                authenticatedHeader.size + LENGTH_FIELDS_BYTES + iv.size + cipherText.size,
            ).put(authenticatedHeader)
                .putInt(iv.size)
                .putInt(cipherText.size)
                .put(iv)
                .put(cipherText)
                .array()
        }

        fun parseRecord(record: ByteArray): ParsedRecord {
            require(record.size in MIN_RECORD_BYTES..(
                AUTHENTICATED_HEADER_BYTES + LENGTH_FIELDS_BYTES + GCM_IV_BYTES +
                    MAX_CIPHERTEXT_BYTES
                ))
            val buffer = ByteBuffer.wrap(record)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            try {
                require(MessageDigest.isEqual(MAGIC, magic))
            } finally {
                magic.fill(0)
            }
            val issuedAt = buffer.long
            val expiresAt = buffer.long
            val bootCount = buffer.int
            val elapsedRealtimeNanos = buffer.long
            require(bootCount >= 0)
            require(elapsedRealtimeNanos >= 0L)
            val referenceDigest = ByteArray(DIGEST_BYTES).also(buffer::get)
            val ivLength = buffer.int
            val cipherLength = buffer.int
            require(ivLength == GCM_IV_BYTES)
            require(cipherLength in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES)
            require(buffer.remaining() == ivLength + cipherLength)
            val iv = ByteArray(ivLength).also(buffer::get)
            val cipherText = ByteArray(cipherLength).also(buffer::get)
            val authenticatedHeader = record.copyOfRange(0, AUTHENTICATED_HEADER_BYTES)
            return ParsedRecord(
                issuedAtEpochMillis = issuedAt,
                expiresAtEpochMillis = expiresAt,
                bootCount = bootCount,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                referenceDigest = referenceDigest,
                iv = iv,
                cipherText = cipherText,
                authenticatedHeader = authenticatedHeader,
            )
        }

        fun StoredCertificateReference.digest(): ByteArray {
            val digest = MessageDigest.getInstance(SHA_256)
            digest.updateField(uri.toString())
            digest.updateField(displayName)
            digest.updateField(mimeType)
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(size ?: -1L).array())
            return digest.digest()
        }

        fun MessageDigest.updateField(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            try {
                update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                update(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        fun CharArray.toUtf8Bytes(): ByteArray {
            val encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val encoded = encoder.encode(CharBuffer.wrap(this))
            return try {
                ByteArray(encoded.remaining()).also(encoded::get)
            } finally {
                if (encoded.hasArray()) encoded.array().fill(0)
            }
        }

        fun ByteArray.toUtf8Chars(): CharArray {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val decoded = decoder.decode(ByteBuffer.wrap(this))
            return try {
                CharArray(decoded.remaining()).also(decoded::get)
            } finally {
                if (decoded.hasArray()) decoded.array().fill('\u0000')
            }
        }
    }
}

class AndroidKeystoreCertificateUnlockCache(
    context: android.content.Context,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CertificateUnlockCache {
    private val delegate = EncryptedCertificateUnlockCache(
        storage = AtomicCertificateUnlockRecordStorage(
            java.io.File(context.noBackupFilesDir, RECORD_FILE_NAME),
        ),
        keyProvider = AndroidKeystoreCertificateUnlockKeyProvider(),
        bootTimeSource = AndroidCertificateUnlockBootTimeSource(context),
        sessionMonotonicNanos = android.os.SystemClock::elapsedRealtimeNanos,
        ioDispatcher = ioDispatcher,
    )

    override suspend fun store(
        reference: StoredCertificateReference,
        password: CharArray,
        issuedAt: Instant,
        expiresAt: Instant,
        observedAtMonotonicNanos: Long,
    ): Boolean = delegate.store(
        reference,
        password,
        issuedAt,
        expiresAt,
        observedAtMonotonicNanos,
    )

    override suspend fun restore(
        reference: StoredCertificateReference,
        now: Instant,
    ): CachedCertificateUnlock? = delegate.restore(reference, now)

    override fun clear() = delegate.clear()

    private companion object {
        const val RECORD_FILE_NAME = "certificate_unlock_v1.bin"
    }
}

internal class AndroidCertificateUnlockBootTimeSource(
    private val context: android.content.Context,
) : CertificateUnlockBootTimeSource {
    override fun read(): CertificateUnlockBootTime? = runCatching {
        CertificateUnlockBootTime(
            bootCount = android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.BOOT_COUNT,
            ),
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos(),
        )
    }.getOrNull()
}

internal class AtomicCertificateUnlockRecordStorage(
    private val file: java.io.File,
) : CertificateUnlockRecordStorage {
    private val atomicFile = android.util.AtomicFile(file)

    override fun read(): ByteArray? {
        val backup = java.io.File(file.path + ".bak")
        if (!file.exists() && !backup.exists()) return null
        return atomicFile.openRead().use { input ->
            val size = input.channel.size()
            require(size in 1..MAX_RECORD_FILE_BYTES.toLong())
            val result = ByteArray(size.toInt())
            var offset = 0
            while (offset < result.size) {
                val read = input.read(result, offset, result.size - offset)
                require(read > 0)
                offset += read
            }
            require(input.read() == -1)
            result
        }
    }

    override fun write(record: ByteArray): Boolean {
        if (record.isEmpty() || record.size > MAX_RECORD_FILE_BYTES) return false
        val parent = file.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        var output: java.io.FileOutputStream? = null
        return try {
            output = atomicFile.startWrite()
            output.write(record)
            output.flush()
            output.fd.sync()
            atomicFile.finishWrite(output)
            output = null
            restrictToOwner(file)
            true
        } catch (_: Exception) {
            output?.let(atomicFile::failWrite)
            false
        }
    }

    override fun clear() {
        atomicFile.delete()
    }

    private fun restrictToOwner(target: java.io.File) {
        target.setReadable(false, false)
        target.setWritable(false, false)
        target.setExecutable(false, false)
        target.setReadable(true, true)
        target.setWritable(true, true)
    }

    private companion object {
        const val MAX_RECORD_FILE_BYTES = 16 * 1024
    }
}

internal class AndroidKeystoreCertificateUnlockKeyProvider : CertificateUnlockKeyProvider {
    override fun getOrCreate(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = runCatching { keyStore.getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()
        if (existing != null) return existing
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)

        val builder = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        ).setKeySize(256)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }
        return javax.crypto.KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        ).apply {
            init(builder.build())
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "dev.junta.firmamobile.certificate_unlock_aes_v1"
    }
}
