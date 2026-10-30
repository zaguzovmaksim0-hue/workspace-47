package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.net.URI
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOperationTransportTest {
    @Test fun singlePhaseOperationUsesTheSuppliedResultTransport() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        val invocation = AfirmaServletInvocation(
            AfirmaServletMetadata(
                AfirmaServletOperation.SIGN, "https://portal.example",
                URI("https://store.example/put"), "Request123", "12345678",
                SigningAlgorithm.SHA256_WITH_RSA, true,
            ),
            "payload".toByteArray(),
        )
        val transport = RecordingResultTransport()
        nativeOperation(invocation, transport, clockFor(identity.identity)).use { operation ->
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(identity.identity) {})
        }
        assertEquals(1, transport.calls)
        assertEquals("Request123", transport.sessionId)
        assertTrue(transport.resultLength > 0)
    }

    @Test fun triPhaseOperationIsCreatedWithTheSuppliedResultTransport() {
        val fields = generalFields("doc".toByteArray(), "CAdEStri") +
            ("serverurl" to "https://service.example/tri")
        acceptedGeneral("sign", fields).use { invocation ->
            nativeOperation(invocation, RecordingResultTransport()).use { operation ->
                assertTrue(operation is NativeMultiPhaseOperation)
            }
        }
    }

    @Test fun localBatchOperationUsesTheSuppliedResultTransport() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        val descriptor = NativeProtocolJson.encode(
            mapOf(
                "algorithm" to "SHA256withRSA", "format" to "CAdES",
                "singlesigns" to listOf(mapOf("id" to "doc", "datareference" to "YWJj")),
            ),
        )
        val fields = mapOf(
            "id" to "Batch123", "stservlet" to "https://store.example/put",
            "dat" to url64(descriptor), "jsonbatch" to "true", "localBatchProcess" to "true",
        )
        acceptedGeneral("batch", fields).use { invocation ->
            val transport = RecordingResultTransport()
            nativeOperation(invocation, transport, clockFor(identity.identity)).use { operation ->
                assertEquals(
                    AfirmaDeliveryResult.ACKNOWLEDGED,
                    operation.executeWithCheckpoints(identity.identity, {}, {}),
                )
            }
            assertEquals(1, transport.calls)
            assertEquals("Batch123", transport.sessionId)
            assertTrue(transport.resultLength > 0)
        }
    }
}

private class RecordingResultTransport : AfirmaResultTransport {
    var calls = 0
    var sessionId: String? = null
    var resultLength = 0

    override suspend fun store(
        endpoint: URI,
        sessionId: String,
        result: String,
    ): AfirmaDeliveryResult {
        calls++
        this.sessionId = sessionId
        resultLength = result.length
        return AfirmaDeliveryResult.ACKNOWLEDGED
    }
}

private fun clockFor(identity: UnlockedIdentity): Clock =
    Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
