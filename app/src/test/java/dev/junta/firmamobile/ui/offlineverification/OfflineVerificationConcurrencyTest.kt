package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.verification.OfflineCmsResult
import dev.junta.firmamobile.verification.OfflineCmsStatus
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineVerificationConcurrencyTest {
    @Test
    fun providerIoAndCryptoRunOutsideTheCallingThread() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val callingThread = Thread.currentThread()
            var metadataThread: Thread? = null
            var streamThread: Thread? = null
            var cryptoThread: Thread? = null
            val source = object : OfflineDocumentSource {
                override fun declaredSize(): Long {
                    metadataThread = Thread.currentThread()
                    return 1L
                }
                override fun open(): OfflineOpenedDocument {
                    streamThread = Thread.currentThread()
                    return OfflineOpenedDocument(1, ByteArrayInputStream(byteArrayOf(51)))
                }
            }
            val owner = OfflineVerificationController(this, dispatcher) { _, _ ->
                cryptoThread = Thread.currentThread()
                OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
            }
            owner.completeSelection(owner.beginSelection(OfflineInputKind.CMS)!!, source)
            owner.verify()
            withTimeout(5_000) { owner.state.first { it.outcome != null } }
            assertNotSame(callingThread, metadataThread)
            assertNotSame(callingThread, streamThread)
            assertNotSame(callingThread, cryptoThread)
            owner.close()
        }
    }

    @Test
    fun dismissedNonpreemptibleOperationCannotAccumulateWorkOrPublishIntoNewDialog() = runBlocking {
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            var oldSnapshot: ByteArray? = null
            val old = OfflineVerificationController(this, dispatcher) { cms, _ ->
                oldSnapshot = cms
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                OfflineCmsResult(OfflineCmsStatus.VERIFIED, 8)
            }
            val nextSource = SyntheticOfflineDocument(byteArrayOf(62))
            val next = OfflineVerificationController(this, dispatcher) { _, _ ->
                OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
            }
            try {
                old.completeSelection(old.beginSelection(OfflineInputKind.CMS)!!, SyntheticOfflineDocument(byteArrayOf(61)))
                old.verify()
                withTimeout(5_000) { while (entered.count != 0L) delay(1) }
                old.close()
                next.completeSelection(next.beginSelection(OfflineInputKind.CMS)!!, nextSource)
                next.verify()
                yield()
                // With two executor threads this barrier runs after the next worker has
                // entered/suspended. Without the shared gate it would have opened its source.
                withContext(dispatcher) { Unit }
                assertEquals(0, nextSource.opens)
                assertTrue(next.state.value.busy)
                release.countDown()
                withTimeout(5_000) { next.state.first { it.outcome != null } }
                assertEquals(OfflineVerificationState(), old.state.value)
                assertEquals(1, next.state.value.verifiedSigners)
                assertTrue(oldSnapshot!!.all { it == 0.toByte() })
                assertEquals(1, nextSource.opens)
            } finally {
                release.countDown()
                old.close(); next.close()
            }
        }
    }
}
