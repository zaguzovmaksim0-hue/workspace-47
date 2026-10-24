package dev.junta.firmamobile.certificate

import android.net.Uri
import java.time.Duration
import java.time.Instant
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CertificateUnlockPlatformPolicyTest {
    private val now = Instant.parse("2026-10-18T09:00:00Z")
    private val expiry = now.plus(Duration.ofHours(1))
    private val reference = StoredCertificateReference(
        uri = Uri.parse("content://synthetic/certificate"),
        displayName = "synthetic.p12",
        mimeType = CertificateRepository.MIME_X_PKCS12,
        size = 4096,
        summary = null,
    )

    @Test
    fun persistentCacheRequiresAtLeastApi30() {
        for (sdk in listOf(26, 27, 28, 29)) assertFalse(supportsPersistentCertificateUnlock(sdk))
        for (sdk in listOf(30, 36)) assertTrue(supportsPersistentCertificateUnlock(sdk))
    }

    @Test
    fun unsupportedStoreNeverReadsWritesOrObtainsAKeyAndRevokesLegacyData() = runTest {
        for (sdk in listOf(26, 27, 28, 29)) {
            val storage = RecordingStorage().apply { bytes = byteArrayOf(1, 2, 3) }
            val keys = RecordingKeys()
            val password = "synthetic-password".toCharArray()
            val original = password.copyOf()
            assertFalse(cache(sdk, storage, keys).store(reference, password, now, expiry, 1L))
            assertEquals(0, storage.reads)
            assertEquals(0, storage.writes)
            assertEquals(0, keys.accesses)
            assertEquals(1, keys.revocations)
            assertNull(storage.bytes)
            assertArrayEquals(original, password)
        }
    }

    @Test
    fun unsupportedRestoreRevokesPreviouslyValidRecordWithoutReadingOrDecryptingIt() = runTest {
        for (sdk in listOf(26, 27, 28, 29)) {
            val storage = RecordingStorage()
            val keys = RecordingKeys()
            assertTrue(cache(30, storage, keys).store(reference, "synthetic".toCharArray(), now, expiry, 1L))
            keys.accesses = 0
            storage.reads = 0
            assertNull(cache(sdk, storage, keys).restore(reference, now))
            assertEquals(0, storage.reads)
            assertEquals(0, keys.accesses)
            assertEquals(1, keys.revocations)
            assertNull(storage.bytes)
        }
    }

    @Test
    fun failedLegacyRevocationStillCannotRestoreOrPersistOnUnsupportedPlatform() = runTest {
        val storage = RecordingStorage().apply { allowClear = false; bytes = byteArrayOf(1, 2, 3) }
        val keys = RecordingKeys().apply { allowRevoke = false }
        val cache = cache(27, storage, keys)
        assertNull(cache.restore(reference, now))
        assertFalse(cache.store(reference, "synthetic".toCharArray(), now, expiry, 1L))
        assertEquals(0, storage.reads)
        assertEquals(0, storage.writes)
        assertEquals(0, keys.accesses)
        assertEquals(2, keys.revocations)
    }

    @Test
    fun supportedPlatformsRetainEncryptedRoundTrip() = runTest {
        for (sdk in listOf(30, 36)) {
            val cache = cache(sdk, RecordingStorage(), RecordingKeys())
            val password = "synthetic".toCharArray()
            assertTrue(cache.store(reference, password, now, expiry, 1L))
            checkNotNull(cache.restore(reference, now)).use {
                assertArrayEquals(password, it.password)
                assertEquals(expiry, it.expiresAt)
            }
        }
    }

    private fun cache(sdk: Int, storage: RecordingStorage, keys: RecordingKeys) =
        EncryptedCertificateUnlockCache(
            storage = storage,
            keyProvider = keys,
            bootTimeSource = { CertificateUnlockBootTime(1, 1L) },
            sessionMonotonicNanos = { 1L },
            ioDispatcher = Dispatchers.Unconfined,
            persistentCacheAllowed = supportsPersistentCertificateUnlock(sdk),
        )

    private class RecordingKeys : CertificateUnlockKeyProvider {
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var accesses = 0
        var revocations = 0
        var allowRevoke = true
        override fun getOrCreate(): SecretKey { accesses++; return key }
        override fun getExisting(): SecretKey { accesses++; return key }
        override fun revoke(): Boolean { revocations++; return allowRevoke }
    }

    private class RecordingStorage : CertificateUnlockRecordStorage {
        var bytes: ByteArray? = null
        var reads = 0
        var writes = 0
        var allowClear = true
        override fun read(): ByteArray? { reads++; return bytes?.copyOf() }
        override fun write(record: ByteArray): Boolean { writes++; bytes = record.copyOf(); return true }
        override fun clear(): Boolean {
            if (!allowClear) return false
            bytes?.fill(0)
            bytes = null
            return true
        }
    }
}
