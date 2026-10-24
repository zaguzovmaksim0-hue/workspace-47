package dev.junta.firmamobile.certificate

import android.net.Uri
import java.time.Duration
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CertificateUnlockAuthenticationTest {
    private val now = Instant.parse("2026-10-18T09:00:00Z")
    private val expiry = now.plus(Duration.ofMinutes(15))
    private val reference = StoredCertificateReference(Uri.parse("content://synthetic/auth"), "synthetic.p12",
        CertificateRepository.MIME_X_PKCS12, 4096, null)
    private class Storage : CertificateUnlockRecordStorage {
        var bytes: ByteArray? = null
        var writes = 0
        override fun read() = bytes?.copyOf()
        override fun write(record: ByteArray): Boolean { writes++; bytes = record.copyOf(); return true }
        override fun clear(): Boolean { bytes = null; return true }
    }
    private class Keys : CertificateUnlockKeyProvider {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var authorize: suspend (Cipher) -> Boolean = { true }
        var prompts = 0
        override fun getOrCreate() = key
        override suspend fun authenticateCipher(cipher: Cipher): Boolean { prompts++; return authorize(cipher) }
    }
    private fun cache(storage: Storage, keys: Keys, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        EncryptedCertificateUnlockCache(storage, keys, { CertificateUnlockBootTime(1, 1L) }, { 1L }, dispatcher)

    @Test fun deniedEncryptionCannotWriteAPasswordRecord() = runTest {
        val storage = Storage(); val keys = Keys().apply { authorize = { false } }
        assertFalse(cache(storage, keys, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            .store(reference, "synthetic".toCharArray(), now, expiry, 1L))
        assertEquals(1, keys.prompts); assertEquals(0, storage.writes); assertNull(storage.bytes)
    }
    @Test fun deniedDecryptionReturnsNoPasswordAndMayBeRetried() = runTest {
        val storage = Storage(); val keys = Keys()
        val cache = cache(storage, keys, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        assertTrue(cache.store(reference, "synthetic".toCharArray(), now, expiry, 1L))
        keys.authorize = { false }
        assertNull(cache.restore(reference, now)); assertNotNull(storage.bytes)
        keys.authorize = { true }
        checkNotNull(cache.restore(reference, now)).use { assertArrayEquals("synthetic".toCharArray(), it.password) }
        assertEquals(3, keys.prompts)
    }
    @Test fun revocationDuringEncryptionApprovalCannotResurrectRecord() = runTest {
        val storage = Storage(); val keys = Keys(); val approval = CompletableDeferred<Boolean>()
        keys.authorize = { approval.await() }
        val cache = cache(storage, keys, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val result = async { cache.store(reference, "synthetic".toCharArray(), now, expiry, 1L) }
        runCurrent(); assertEquals(1, keys.prompts)
        cache.clear(); approval.complete(true)
        assertFalse(result.await()); assertNull(storage.bytes); assertEquals(0, storage.writes)
    }
    @Test fun revocationDuringDecryptionApprovalCannotReturnPassword() = runTest {
        val storage = Storage(); val keys = Keys()
        val cache = cache(storage, keys, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        assertTrue(cache.store(reference, "synthetic".toCharArray(), now, expiry, 1L))
        val approval = CompletableDeferred<Boolean>(); keys.authorize = { approval.await() }
        val result = async { cache.restore(reference, now) }
        runCurrent(); cache.clear(); approval.complete(true)
        assertNull(result.await()); assertNull(storage.bytes)
    }
    @Test fun cancellationDuringApprovalCannotCommitPassword() = runTest {
        val storage = Storage(); val keys = Keys(); val approval = CompletableDeferred<Boolean>()
        keys.authorize = { approval.await() }
        val cache = cache(storage, keys, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val result = async { cache.store(reference, "synthetic".toCharArray(), now, expiry, 1L) }
        runCurrent(); result.cancel(); result.join(); approval.complete(true); runCurrent()
        assertTrue(result.isCancelled); assertNull(storage.bytes); assertEquals(0, storage.writes)
    }
    @Test fun expiredRecordIsRejectedBeforePrompt() = runTest {
        val storage = Storage(); val keys = Keys()
        val cache = cache(storage, keys, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        assertTrue(cache.store(reference, "synthetic".toCharArray(), now, expiry, 1L))
        assertNull(cache.restore(reference, expiry)); assertEquals(1, keys.prompts)
    }
}
