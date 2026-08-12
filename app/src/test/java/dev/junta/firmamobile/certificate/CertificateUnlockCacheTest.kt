package dev.junta.firmamobile.certificate

import android.net.Uri
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CertificateUnlockCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now = Instant.parse("2026-07-29T20:00:00Z")
    private val reference = StoredCertificateReference(
        uri = Uri.parse("content://documents/identity"),
        displayName = "identity.p12",
        mimeType = CertificateRepository.MIME_X_PKCS12,
        size = 4096,
        summary = null,
    )


    @Test
    fun atomicRecordStorageRoundTripsAndClearsPrivateFile() {
        val file = temporaryFolder.newFolder("no-backup").resolve("certificate_unlock.bin")
        val storage = AtomicCertificateUnlockRecordStorage(file)
        val record = ByteArray(96) { index -> (index * 7).toByte() }

        assertTrue(storage.write(record))
        assertArrayEquals(record, storage.read())
        assertTrue(file.isFile)

        storage.clear()

        assertNull(storage.read())
        assertFalse(file.exists())
    }

    @Test
    fun failedEncryptedWriteClearsAnyPreviousRecord() = runTest {
        val storage = MemoryRecordStorage().apply {
            bytes = byteArrayOf(1, 2, 3)
            allowWrite = false
        }
        val cache = cache(storage)

        assertFalse(
            cache.store(reference, "secret".toCharArray(), now, now.plus(Duration.ofHours(24)), 1_000_000_000L),
        )
        assertNull(storage.bytes)
        assertTrue(storage.clearCalls > 0)
    }

    @Test
    fun encryptedRecordRestoresUnicodePasswordBeforeOriginalExpiry() = runTest {
        val storage = MemoryRecordStorage()
        val cache = cache(storage)
        val password = "contraseña-秘密".toCharArray()
        val original = password.copyOf()
        val expiresAt = now.plus(Duration.ofHours(24))

        assertTrue(cache.store(reference, password, now, expiresAt, 1_000_000_000L))
        val restored = checkNotNull(cache.restore(reference, now.plus(Duration.ofHours(23))))

        restored.use {
            assertArrayEquals(original, it.password)
            assertEquals(expiresAt, it.expiresAt)
        }
        assertTrue(restored.password.all { it == '\u0000' })
        assertFalse(storage.bytes!!.toString(Charsets.UTF_8).contains("contraseña"))
    }

    @Test
    fun civilClockRollbackCannotRestoreAfterOriginalMonotonicRetention() = runTest {
        val storage = MemoryRecordStorage()
        val bootTime = MutableBootTimeSource(7, 1_000_000_000L)
        val sessionMonotonic = MutableSessionMonotonicClock(10_000_000_000L)
        val cache = cache(storage, bootTime, sessionMonotonic)
        val expiresAt = now.plus(Duration.ofHours(24))
        assertTrue(cache.store(reference, "secret".toCharArray(), now, expiresAt, bootTime.nowNanos()))
        bootTime.advance(Duration.ofHours(25))
        assertNull(cache.restore(reference, now.plus(Duration.ofHours(1))))
        assertNull(storage.bytes)
    }

    @Test
    fun elapsedRealtimeRollbackRejectsAndClearsPersistedUnlock() = runTest {
        val storage = MemoryRecordStorage()
        val bootTime = MutableBootTimeSource(12, 8_000_000_000L)
        val sessionMonotonic = MutableSessionMonotonicClock(25_000_000_000L)
        val cache = cache(storage, bootTime, sessionMonotonic)
        val expiresAt = now.plus(Duration.ofHours(24))
        assertTrue(cache.store(reference, "secret".toCharArray(), now, expiresAt, bootTime.nowNanos()))

        bootTime.rewind(Duration.ofSeconds(1))

        assertNull(cache.restore(reference, now.plusSeconds(1)))
        assertNull(storage.bytes)
    }

    @Test
    fun unavailableBootTimeFailsClosedForStoreAndRestore() = runTest {
        val storeStorage = MemoryRecordStorage()
        val unavailable = MutableBootTimeSource(13, 9_000_000_000L).apply { available = false }
        val storeCache = cache(storeStorage, unavailable, MutableSessionMonotonicClock())
        assertFalse(
            storeCache.store(
                reference,
                "secret".toCharArray(),
                now,
                now.plus(Duration.ofHours(24)),
                unavailable.nowNanos(),
            ),
        )
        assertNull(storeStorage.bytes)

        val restoreStorage = MemoryRecordStorage()
        val bootTime = MutableBootTimeSource(14, 10_000_000_000L)
        val restoreCache = cache(restoreStorage, bootTime, MutableSessionMonotonicClock())
        assertTrue(
            restoreCache.store(
                reference,
                "secret".toCharArray(),
                now,
                now.plus(Duration.ofHours(24)),
                bootTime.nowNanos(),
            ),
        )
        bootTime.available = false

        assertNull(restoreCache.restore(reference, now.plusSeconds(1)))
        assertNull(restoreStorage.bytes)
    }

    @Test
    fun legacyRecordMagicIsRejectedAndCleared() = runTest {
        val storage = MemoryRecordStorage()
        val cache = cache(storage)
        assertTrue(
            cache.store(
                reference,
                "secret".toCharArray(),
                now,
                now.plus(Duration.ofHours(24)),
                1_000_000_000L,
            ),
        )
        val legacyMagic = "JFMUC001".toByteArray(Charsets.US_ASCII)
        legacyMagic.copyInto(checkNotNull(storage.bytes), destinationOffset = 0)

        assertNull(cache.restore(reference, now.plusSeconds(1)))
        assertNull(storage.bytes)
    }

    @Test
    fun changedBootCountRejectsAndClearsPersistedUnlock() = runTest {
        val storage = MemoryRecordStorage()
        val bootTime = MutableBootTimeSource(11, 5_000_000_000L)
        val sessionMonotonic = MutableSessionMonotonicClock(20_000_000_000L)
        val cache = cache(storage, bootTime, sessionMonotonic)
        val expiresAt = now.plus(Duration.ofHours(24))
        assertTrue(cache.store(reference, "secret".toCharArray(), now, expiresAt, bootTime.nowNanos()))
        bootTime.bootCount += 1
        bootTime.advance(Duration.ofMinutes(5))
        assertNull(cache.restore(reference, now.plus(Duration.ofMinutes(5))))
        assertNull(storage.bytes)
    }

    @Test
    fun sameBootRestoreCarriesOnlyAuthenticatedRemainingMonotonicLease() = runTest {
        val storage = MemoryRecordStorage()
        val bootTime = MutableBootTimeSource(3, 2_000_000_000L)
        val sessionMonotonic = MutableSessionMonotonicClock(30_000_000_000L)
        val cache = cache(storage, bootTime, sessionMonotonic)
        val expiresAt = now.plus(Duration.ofHours(24))
        assertTrue(cache.store(reference, "secret".toCharArray(), now, expiresAt, bootTime.nowNanos()))
        bootTime.advance(Duration.ofHours(23))
        val restored = checkNotNull(cache.restore(reference, now.plus(Duration.ofHours(23))))
        restored.use {
            assertEquals(expiresAt, it.expiresAt)
            val lease = checkNotNull(it.lease)
            assertFalse(lease.isExpiredOrInvalid(sessionMonotonic.nowNanos()))
            sessionMonotonic.advance(Duration.ofHours(1))
            assertTrue(lease.isExpiredOrInvalid(sessionMonotonic.nowNanos()))
        }
    }

    @Test
    fun delayedStoreCannotMovePersistedLeaseOriginLaterThanManualUnlock() = runTest {
        val storage = MemoryRecordStorage()
        val bootTime = MutableBootTimeSource(9, 6_000_000_000L)
        val sessionMonotonic = MutableSessionMonotonicClock(50_000_000_000L)
        val cache = cache(storage, bootTime, sessionMonotonic)
        val expiresAt = now.plus(Duration.ofHours(24))
        val originalObservation = bootTime.nowNanos()
        bootTime.advance(Duration.ofHours(2))

        assertTrue(
            cache.store(
                reference,
                "secret".toCharArray(),
                now,
                expiresAt,
                observedAtMonotonicNanos = originalObservation,
            ),
        )
        bootTime.advance(Duration.ofHours(22))

        assertNull(cache.restore(reference, now.plus(Duration.ofHours(1))))
        assertNull(storage.bytes)
    }

    @Test
    fun exactElapsedRetentionBoundaryFailsClosed() = runTest {
        val storage = MemoryRecordStorage()
        val bootTime = MutableBootTimeSource(5, 4_000_000_000L)
        val sessionMonotonic = MutableSessionMonotonicClock(40_000_000_000L)
        val cache = cache(storage, bootTime, sessionMonotonic)
        val expiresAt = now.plus(Duration.ofHours(24))
        assertTrue(cache.store(reference, "secret".toCharArray(), now, expiresAt, bootTime.nowNanos()))
        bootTime.advance(Duration.ofHours(24))
        assertNull(cache.restore(reference, now.plus(Duration.ofHours(23))))
        assertNull(storage.bytes)
    }

    @Test
    fun expiredRecordIsClearedAndNeverRestored() = runTest {
        val storage = MemoryRecordStorage()
        val cache = cache(storage)
        cache.store(reference, "secret".toCharArray(), now, now.plus(Duration.ofHours(24)), 1_000_000_000L)

        assertNull(cache.restore(reference, now.plus(Duration.ofHours(24))))
        assertNull(storage.bytes)
    }

    @Test
    fun differentCertificateReferenceCannotReuseCachedPassword() = runTest {
        val storage = MemoryRecordStorage()
        val cache = cache(storage)
        cache.store(reference, "secret".toCharArray(), now, now.plus(Duration.ofHours(24)), 1_000_000_000L)
        val replacement = reference.copy(uri = Uri.parse("content://documents/replacement"))

        assertNull(cache.restore(replacement, now.plusSeconds(1)))
        assertNull(storage.bytes)
    }

    @Test
    fun tamperedCiphertextFailsClosedAndClearsRecord() = runTest {
        val storage = MemoryRecordStorage()
        val cache = cache(storage)
        cache.store(reference, "secret".toCharArray(), now, now.plus(Duration.ofHours(24)), 1_000_000_000L)
        storage.bytes!![storage.bytes!!.lastIndex] = (storage.bytes!!.last().toInt() xor 1).toByte()

        assertNull(cache.restore(reference, now.plusSeconds(1)))
        assertNull(storage.bytes)
    }

    @Test
    fun clearDuringBlockingRestoreCannotReturnCachedUnlock() = runTest {
        val storage = BlockingReadStorage()
        val cache = cache(storage)
        assertTrue(
            cache.store(
                reference,
                "secret".toCharArray(),
                now,
                now.plus(Duration.ofHours(24)),
                1_000_000_000L,
            ),
        )
        val restored = async(Dispatchers.Default) {
            cache.restore(reference, now.plusSeconds(1))
        }

        assertTrue(storage.readStarted.await(5, TimeUnit.SECONDS))
        cache.clear()
        storage.allowRead.countDown()

        val result = restored.await()
        try {
            assertNull(result)
            assertTrue(storage.isEmpty())
        } finally {
            result?.close()
        }
    }

    @Test
    fun clearDuringBlockingWriteCannotResurrectUnlockRecord() = runTest {
        val storage = BlockingWriteStorage()
        val cache = cache(storage)
        val store = async(Dispatchers.Default) {
            cache.store(
                reference,
                "secret".toCharArray(),
                now,
                now.plus(Duration.ofHours(24)),
                1_000_000_000L,
            )
        }

        assertTrue(storage.writeStarted.await(5, TimeUnit.SECONDS))
        cache.clear()
        storage.allowWrite.countDown()

        assertFalse(store.await())
        assertNull(storage.read())
    }

    @Test
    fun futureIssuedTimestampAndOverlongRetentionFailClosed() = runTest {
        val futureStorage = MemoryRecordStorage()
        val futureCache = cache(futureStorage)
        assertTrue(
            futureCache.store(
                reference,
                "secret".toCharArray(),
                now.plusSeconds(60),
                now.plus(Duration.ofHours(24)),
                1_000_000_000L,
            ),
        )
        assertNull(futureCache.restore(reference, now))
        assertNull(futureStorage.bytes)

        val longStorage = MemoryRecordStorage()
        val longCache = cache(longStorage)
        assertFalse(
            longCache.store(
                reference,
                "secret".toCharArray(),
                now,
                now.plus(Duration.ofHours(24)).plusMillis(1),
                1_000_000_000L,
            ),
        )
        assertNull(longStorage.bytes)
    }

    private fun cache(
        storage: CertificateUnlockRecordStorage,
        bootTime: MutableBootTimeSource = MutableBootTimeSource(),
        sessionMonotonic: MutableSessionMonotonicClock = MutableSessionMonotonicClock(),
    ): EncryptedCertificateUnlockCache = EncryptedCertificateUnlockCache(
        storage = storage,
        keyProvider = FixedKeyProvider(testKey()),
        bootTimeSource = bootTime::read,
        sessionMonotonicNanos = sessionMonotonic::nowNanos,
    )

    private class MutableBootTimeSource(
        var bootCount: Int = 1,
        private var elapsedRealtimeNanos: Long = 1_000_000_000L,
        var available: Boolean = true,
    ) {
        fun read(): CertificateUnlockBootTime? = if (available) {
            CertificateUnlockBootTime(
                bootCount = bootCount,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
            )
        } else {
            null
        }

        fun nowNanos(): Long = elapsedRealtimeNanos

        fun advance(duration: Duration) {
            elapsedRealtimeNanos = Math.addExact(elapsedRealtimeNanos, duration.toNanos())
        }

        fun rewind(duration: Duration) {
            elapsedRealtimeNanos = Math.subtractExact(elapsedRealtimeNanos, duration.toNanos())
        }
    }

    private class MutableSessionMonotonicClock(
        private var current: Long = 10_000_000_000L,
    ) {
        fun nowNanos(): Long = current
        fun advance(duration: Duration) {
            current = Math.addExact(current, duration.toNanos())
        }
    }

    private fun testKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private class FixedKeyProvider(private val key: SecretKey) : CertificateUnlockKeyProvider {
        override fun getOrCreate(): SecretKey = key
    }

    private class BlockingReadStorage : CertificateUnlockRecordStorage {
        val readStarted = CountDownLatch(1)
        val allowRead = CountDownLatch(1)

        @Volatile
        private var bytes: ByteArray? = null

        override fun read(): ByteArray? {
            val snapshot = bytes?.copyOf()
            readStarted.countDown()
            check(allowRead.await(5, TimeUnit.SECONDS))
            return snapshot
        }

        override fun write(record: ByteArray): Boolean {
            bytes = record.copyOf()
            return true
        }

        override fun clear() {
            bytes?.fill(0)
            bytes = null
        }

        fun isEmpty(): Boolean = bytes == null
    }

    private class BlockingWriteStorage : CertificateUnlockRecordStorage {
        val writeStarted = CountDownLatch(1)
        val allowWrite = CountDownLatch(1)

        @Volatile
        private var bytes: ByteArray? = null

        override fun read(): ByteArray? = bytes?.copyOf()

        override fun write(record: ByteArray): Boolean {
            writeStarted.countDown()
            check(allowWrite.await(5, TimeUnit.SECONDS))
            bytes = record.copyOf()
            return true
        }

        override fun clear() {
            bytes?.fill(0)
            bytes = null
        }
    }

    private class MemoryRecordStorage : CertificateUnlockRecordStorage {
        var bytes: ByteArray? = null
        var allowWrite = true
        var clearCalls = 0

        override fun read(): ByteArray? = bytes?.copyOf()

        override fun write(record: ByteArray): Boolean {
            if (!allowWrite) return false
            bytes = record.copyOf()
            return true
        }

        override fun clear() {
            clearCalls += 1
            bytes?.fill(0)
            bytes = null
        }
    }
}
