package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.afirma.servlet.AfirmaDeliveryResult
import dev.junta.firmamobile.afirma.servlet.AfirmaResultTransport
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocationParser
import dev.junta.firmamobile.afirma.servlet.AfirmaServletParseResult
import dev.junta.firmamobile.afirma.servlet.NativeAfirmaOperation
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic IFEF-shaped flow: public page origin, separate public HTTPS
 * storage/retrieval origin, explicit authorization and native CAdES result.
 * No real IFEF server, token, user data or certificate is used. */
class IfefUniversalAfirmaE2ETest {
    private val page = "https://sedeelectronica.ifef.es/sede/formulario"
    private val storage = "https://portaldelcontribuyente.cadiz.es/portal-afirma-signature-storage/StorageService"

    private fun intentUri(extra: Map<String, String> = emptyMap()): String {
        val values = linkedMapOf(
            "id" to "IfefSession123",
            "stservlet" to storage,
            "key" to "12345678",
            "format" to "CAdES",
            "algorithm" to "SHA256withRSA",
            "dat" to Base64.getEncoder().encodeToString("synthetic-ifef-payload".toByteArray()),
            "properties" to Base64.getEncoder().encodeToString(
                "headless=true\nfilters=nonexpired:true;authCert:true".toByteArray(),
            ),
        ) + extra
        val query = values.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")
        }
        return "intent://sign?$query#Intent;scheme=afirma;package=es.gob.afirma;end"
    }

    @Test
    fun publicIfefIntentReachesTheNativeSigningPipelineWithoutAProfile() = runBlocking {
        val raw = intentUri()
        val decision = JuntaNavigationPolicy(null).decide(raw, page)
        assertTrue("Universal AutoFirma decision: $decision", decision is NavigationDecision.HandleAfirma)
        assertEquals(AfirmaOperation.SIGN, (decision as NavigationDecision.HandleAfirma).request.operation)

        val extracted = NativeAfirmaNavigation.extract(raw)
        assertEquals("afirma://sign?" + raw.substringAfter("intent://").substringBefore("#Intent"), extracted)
        val parsed = AfirmaServletInvocationParser.parse(checkNotNull(extracted), page)
        assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
        val invocation = (parsed as AfirmaServletParseResult.Accepted).invocation

        val identity = nonExportableSyntheticIdentity()
        var authorizationCount = 0
        var storeCount = 0
        var uploadedEndpoint: URI? = null
        var uploadedResult: String? = null
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { endpoint, _, result ->
            storeCount++
            uploadedEndpoint = endpoint
            uploadedResult = result
            AfirmaDeliveryResult.ACKNOWLEDGED
        })
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(identity.identity) { authorizationCount++ })
        assertEquals(1, authorizationCount)
        assertEquals(1, storeCount)
        assertEquals(URI(storage), uploadedEndpoint)
        val wire = checkNotNull(uploadedResult)
        assertEquals(2, wire.split('|').size)
        assertEquals("https://portaldelcontribuyente.cadiz.es", operation.details.destination)
        operation.close()
    }

    @Test
    fun cancelledIfefIntentSendsCancelWithoutCreatingASignature() = runBlocking {
        val parsed = AfirmaServletInvocationParser.parse(
            checkNotNull(NativeAfirmaNavigation.extract(intentUri())), page,
        ) as AfirmaServletParseResult.Accepted
        val identity = nonExportableSyntheticIdentity()
        var authorizationCount = 0
        var storeCount = 0
        var result: String? = null
        val operation = NativeAfirmaOperation(parsed.invocation, AfirmaResultTransport { _, _, value ->
            storeCount++
            result = value
            AfirmaDeliveryResult.ACKNOWLEDGED
        })
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.notifyCancellation { authorizationCount++ })
        assertEquals(1, authorizationCount)
        assertEquals(1, storeCount)
        assertEquals("CANCEL", result)
        operation.close()
    }

    @Test
    fun unsafeIfefShapedEndpointsRemainBlockedBeforeConsent() {
        for (endpoint in listOf(
            "http://portaldelcontribuyente.cadiz.es/StorageService",
            "https://127.0.0.1/StorageService",
            "https://user@portaldelcontribuyente.cadiz.es/StorageService",
            "https://portaldelcontribuyente.cadiz.es/StorageService#fragment",
        )) {
            val raw = intentUri(mapOf("stservlet" to endpoint))
            val decision = JuntaNavigationPolicy(null).decide(raw, page)
            assertEquals(
                "Unsafe endpoint must be blocked: $endpoint",
                NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI),
                decision,
            )
        }
    }
}
