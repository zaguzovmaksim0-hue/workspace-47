package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic invocations and an in-memory storage boundary; no personal identity or network. */
class NativeAfirmaCancellationTest {
    @Test fun unsignedCancellationNeedsNoIdentityAndSendsRawCancelForEveryCipherMode() = runTest {
        val callerContext = currentCoroutineContext()
        for (kind in AfirmaServletOperation.entries) {
            for (mode in listOf("plain", "DES", "AES")) {
                val aes = if (mode == "AES") {
                    AfirmaAesParameters(ByteArray(32) { it.toByte() }, ByteArray(16) { (it + 16).toByte() })
                } else null
                val invocation = invocation(kind, if (mode == "plain") null else "12345678", aes)
                aes?.close()
                var authorizations = 0
                var uploads = 0
                val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { endpoint, id, result ->
                    assertEquals(1, authorizations)
                    assertEquals(ENDPOINT, endpoint)
                    assertEquals(SESSION_ID, id)
                    assertEquals("CANCEL", result)
                    uploads++
                    AfirmaDeliveryResult.ACKNOWLEDGED
                })
                try {
                    assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.notifyCancellation {
                        assertEquals(callerContext, currentCoroutineContext())
                        assertEquals(0, uploads)
                        assertReleased(invocation)
                        yield()
                        assertEquals(callerContext, currentCoroutineContext())
                        authorizations++
                    })
                    assertEquals(1, authorizations)
                    assertEquals(1, uploads)
                    assertReleased(invocation)
                } finally { operation.close() }
            }
        }
    }

    @Test fun cancellationOwnsTheTerminalGateDuringAndAfterAuthorization() = runTest {
        val identity = nonExportableSyntheticIdentity()
        var authorizations = 0
        var uploads = 0
        val operation = NativeAfirmaOperation(invocation(), AfirmaResultTransport { _, _, result ->
            assertEquals("CANCEL", result)
            uploads++
            AfirmaDeliveryResult.ACKNOWLEDGED
        })
        try {
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.notifyCancellation {
                authorizations++
                expectFailure<IllegalStateException> {
                    operation.notifyCancellation { authorizations++ }
                }
                expectFailure<IllegalStateException> {
                    operation.execute(identity.identity) { authorizations++ }
                }
            })
            expectFailure<IllegalStateException> {
                operation.notifyCancellation { authorizations++ }
            }
            expectFailure<IllegalStateException> {
                operation.execute(identity.identity) { authorizations++ }
            }
            assertEquals(1, authorizations)
            assertEquals(1, uploads)
            assertEquals(0, identity.encodedReads.get())
        } finally { operation.close() }
    }

    @Test fun closeBeforeOrDuringAuthorizationBlocksStorage() = runTest {
        for (closeBefore in listOf(true, false)) {
            val invocation = invocation()
            var authorizations = 0
            var uploads = 0
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, _ ->
                uploads++
                AfirmaDeliveryResult.ACKNOWLEDGED
            })
            try {
                if (closeBefore) operation.close()
                expectFailure<IllegalStateException> {
                    operation.notifyCancellation {
                        authorizations++
                        operation.close()
                    }
                }
                expectFailure<IllegalStateException> {
                    operation.notifyCancellation { authorizations++ }
                }
                assertEquals(if (closeBefore) 0 else 1, authorizations)
                assertEquals(0, uploads)
                assertReleased(invocation)
            } finally { operation.close() }
        }
    }

    @Test fun failedOrCancelledAuthorizationIsTerminalAndNeverUploads() = runTest {
        val failures = listOf(
            CancellationException("Synthetic denial"),
            IllegalArgumentException("Synthetic authorization failure"),
        )
        for (failure in failures) {
            val invocation = invocation()
            var authorizations = 0
            var uploads = 0
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, _ ->
                uploads++
                AfirmaDeliveryResult.ACKNOWLEDGED
            })
            try {
                assertSame(failure, expectFailure<Exception> {
                    operation.notifyCancellation {
                        authorizations++
                        throw failure
                    }
                })
                expectFailure<IllegalStateException> {
                    operation.notifyCancellation { authorizations++ }
                }
                assertEquals(1, authorizations)
                assertEquals(0, uploads)
                assertReleased(invocation)
            } finally { operation.close() }
        }

        for (cancelBeforeAuthorization in listOf(true, false)) {
            val invocation = invocation()
            var authorizations = 0
            var uploads = 0
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, _ ->
                uploads++
                AfirmaDeliveryResult.ACKNOWLEDGED
            })
            try {
                val job = launch {
                    if (cancelBeforeAuthorization) currentCoroutineContext().cancel()
                    expectFailure<CancellationException> {
                        operation.notifyCancellation {
                            authorizations++
                            currentCoroutineContext().cancel()
                        }
                    }
                }
                job.join()
                assertTrue(job.isCancelled)
                expectFailure<IllegalStateException> {
                    operation.notifyCancellation { authorizations++ }
                }
                assertEquals(if (cancelBeforeAuthorization) 0 else 1, authorizations)
                assertEquals(0, uploads)
                assertReleased(invocation)
            } finally { operation.close() }
        }
    }

    @Test fun uncertainOrFailedDeliveryDoesNotRetry() = runTest {
        for (failTransport in listOf(false, true)) {
            val invocation = invocation()
            val transportFailure = IllegalStateException("Synthetic storage failure")
            var authorizations = 0
            var uploads = 0
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, result ->
                assertEquals("CANCEL", result)
                uploads++
                if (failTransport) throw transportFailure
                AfirmaDeliveryResult.UNCERTAIN
            })
            try {
                if (failTransport) {
                    assertSame(transportFailure, expectFailure<IllegalStateException> {
                        operation.notifyCancellation { authorizations++ }
                    })
                } else {
                    assertEquals(AfirmaDeliveryResult.UNCERTAIN,
                        operation.notifyCancellation { authorizations++ })
                }
                expectFailure<IllegalStateException> {
                    operation.notifyCancellation { authorizations++ }
                }
                assertEquals(1, authorizations)
                assertEquals(1, uploads)
                assertReleased(invocation)
            } finally { operation.close() }
        }
    }

    private fun invocation(
        kind: AfirmaServletOperation = AfirmaServletOperation.SIGN,
        key: String? = "12345678",
        cipherParameters: AfirmaAesParameters? = null,
    ) = AfirmaServletInvocation(
        operation = kind,
        sourceOrigin = "https://portal.example",
        storageUrl = ENDPOINT,
        sessionId = SESSION_ID,
        key = key,
        algorithm = if (kind == AfirmaServletOperation.SIGN) SigningAlgorithm.SHA256_WITH_RSA else null,
        detached = true,
        payload = if (kind == AfirmaServletOperation.SIGN) "Synthetic unsigned payload".toByteArray() else ByteArray(0),
        cipherParameters = cipherParameters,
    )

    private fun assertReleased(invocation: AfirmaServletInvocation) {
        expectFailure<IllegalStateException> { invocation.payloadCopy() }
        assertNull(invocation.cipherCopy())
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw failure
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    private companion object {
        val ENDPOINT = URI("https://store.example/StorageService?route=synthetic")
        const val SESSION_ID = "Cancel-123"
    }
}
