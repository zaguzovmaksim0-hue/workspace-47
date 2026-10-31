package dev.junta.firmamobile.afirma.servlet

import java.net.URI
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPublicSignerTransportTest {
    @Test
    fun unprofiledPortalCanUseThirdPublicHttpsStorageAndSigner() {
        val fields = generalFields("doc".toByteArray(), "CAdEStri") +
            ("stservlet" to "https://signature.third-public.example/StorageService") +
            ("serverurl" to "https://signature.third-public.example/TriPhaseService")
        acceptedGeneral("sign", fields).use { invocation ->
            assertEquals(
                URI("https://signature.third-public.example/StorageService"),
                invocation.storageUrl,
            )
            assertEquals(
                URI("https://signature.third-public.example/TriPhaseService"),
                checkNotNull(invocation.remoteOptions).preUrl,
            )
            nativeOperation(invocation).use { operation ->
                assertTrue(operation.details.delegatedSigning)
                assertEquals(
                    listOf("https://signature.third-public.example/TriPhaseService"),
                    operation.details.serviceDestinations,
                )
            }
        }
    }

    @Test
    fun unsafeThirdPartyStorageEndpointsRemainRejected() {
        val unsafe = listOf(
            "http://signature.third-public.example/StorageService",
            "https://127.0.0.1/StorageService",
            "https://localhost/StorageService",
            "https://user@signature.third-public.example/StorageService",
            "https://signature.third-public.example/StorageService#fragment",
        )
        for (endpoint in unsafe) {
            val fields = generalFields("doc".toByteArray(), "CAdES") +
                ("stservlet" to endpoint)
            val query = fields.entries.joinToString("&") { (name, value) ->
                name + "=" + URLEncoder.encode(value, "UTF-8").replace("+", "%20")
            }
            val result = AfirmaServletInvocationParser.parse(
                "afirma://sign?$query",
                "https://portal.third-public.example/form",
            )
            assertFalse(endpoint, result is AfirmaServletParseResult.Accepted)
        }
    }
}
