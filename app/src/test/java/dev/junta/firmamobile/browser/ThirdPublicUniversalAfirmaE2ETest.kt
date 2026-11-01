package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.servlet.AfirmaDeferredResolver
import dev.junta.firmamobile.afirma.servlet.AfirmaDeliveryResult
import dev.junta.firmamobile.afirma.servlet.AfirmaIntermediateCipher
import dev.junta.firmamobile.afirma.servlet.AfirmaRequestTransport
import dev.junta.firmamobile.afirma.servlet.AfirmaResultTransport
import dev.junta.firmamobile.afirma.servlet.AfirmaRetrievedBytes
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocation
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocationParser
import dev.junta.firmamobile.afirma.servlet.AfirmaServletParseResult
import dev.junta.firmamobile.afirma.servlet.NativeAfirmaOperation
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URI
import java.net.URLEncoder
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Synthetic universal (non-IFEF) AutoFirma flow for a public page outside
 * every manual allowlist. It covers raw afirma://, Android intent://,
 * explicit authorization, native CAdES, cancellation, deferred retrieval and
 * fail-closed endpoint validation. No real server, certificate or signature
 * acceptance is used. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ThirdPublicUniversalAfirmaE2ETest {
    private val page = "https://portal.third-public.example/form"
    private val storage = "https://signature.third-public.example/StorageService"
    private val retrieval = "https://portal.third-public.example/RetrievalService"

    @Test
    fun universalThirdPublicRawAfirmaUriSignsOnceAfterExplicitAuthorization() = runBlocking {
        val raw = rawUri()
        val decision = JuntaNavigationPolicy(null).decide(raw, page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME), decision)

        val parsed = AfirmaServletInvocationParser.parse(
            checkNotNull(NativeAfirmaNavigation.extract(raw)),
            page,
        )
        assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
        executeAndAssertSingleAuthorizedUpload((parsed as AfirmaServletParseResult.Accepted).invocation)
    }

    @Test
    fun universalThirdPublicIntentUriSignsOnceAfterExplicitAuthorization() = runBlocking {
        val raw = intentUri()
        val decision = JuntaNavigationPolicy(null).decide(raw, page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME), decision)

        val extracted = NativeAfirmaNavigation.extract(raw)
        assertEquals("afirma://" + raw.substringAfter("intent://").substringBefore("#Intent"), extracted)
        val parsed = AfirmaServletInvocationParser.parse(checkNotNull(extracted), page)
        assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
        executeAndAssertSingleAuthorizedUpload((parsed as AfirmaServletParseResult.Accepted).invocation)
    }

    @Test
    fun universalThirdPublicCancellationSendsCancelWithoutNativeSignature() = runBlocking {
        val parsed = AfirmaServletInvocationParser.parse(
            checkNotNull(NativeAfirmaNavigation.extract(intentUri())),
            page,
        ) as AfirmaServletParseResult.Accepted
        var authorizationCount = 0
        var storeCount = 0
        var uploadedResult: String? = null
        val operation = NativeAfirmaOperation(
            parsed.invocation,
            AfirmaResultTransport { _, _, result ->
                storeCount++
                uploadedResult = result
                AfirmaDeliveryResult.ACKNOWLEDGED
            },
        )
        assertEquals(
            AfirmaDeliveryResult.ACKNOWLEDGED,
            operation.notifyCancellation { authorizationCount++ },
        )
        assertEquals(1, authorizationCount)
        assertEquals(1, storeCount)
        assertEquals("CANCEL", uploadedResult)
        operation.close()
    }

    @Test
    fun unsafeThirdPublicEndpointsFailClosedBeforeAnyTransportInvocation() {
        for (endpoint in listOf(
            "http://signature.third-public.example/StorageService",
            "https://127.0.0.1/StorageService",
            "https://localhost/StorageService",
            "https://10.0.0.1/StorageService",
            "https://user@signature.third-public.example/StorageService",
            "https://signature.third-public.example/StorageService#fragment",
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

    @Test
    fun thirdPublicDeferredFileidLoadsOnceKeepsOriginAndThenSignsAndStores() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        val clock = Clock.fixed(identity.identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC)
        val payload = "synthetic third-public deferred payload".toByteArray()
        val outer = "afirma://sign?fileid=Config-123&rid=Result-456&key=12345678" +
            "&rtservlet=${form(retrieval)}&stservlet=${form(storage)}"
        val request = (
            AfirmaServletInvocationParser.parse(outer, page) as AfirmaServletParseResult.Deferred
            ).invocation
        val xml = "<sign>" + mapOf(
            "id" to "Result-456",
            "stservlet" to storage,
            "key" to "87654321",
            "format" to "CAdES",
            "algorithm" to "SHA512withRSA",
            "dat" to base64(payload),
        ).entries.joinToString("") { "<e k='${it.key}' v='${form(it.value)}'/>" } + "</sign>"
        val wire = (AfirmaIntermediateCipher.encode(xml.toByteArray(), "12345678") + "\n").toByteArray()

        var downloads = 0
        var uploads = 0
        var authorizationCount = 0
        var uploadedEndpoint: URI? = null
        var uploadedResult: String? = null
        val resolver = AfirmaDeferredResolver(
            AfirmaRequestTransport { endpoint, fileId ->
                downloads++
                assertEquals(URI(retrieval), endpoint)
                assertEquals("Config-123", fileId)
                AfirmaRetrievedBytes(wire)
            },
            operationFactory = { invocation ->
                NativeAfirmaOperation(
                    invocation,
                    AfirmaResultTransport { endpoint, _, result ->
                        assertTrue("Upload before authorization", authorizationCount == 1)
                        uploads++
                        uploadedEndpoint = endpoint
                        uploadedResult = result
                        AfirmaDeliveryResult.ACKNOWLEDGED
                    },
                    clock = clock,
                )
            },
        )
        val operation = resolver.resolve(request)
        assertEquals(1, downloads)
        assertEquals(0, uploads)
        assertEquals("https://portal.third-public.example", operation.details.sourceOrigin)
        assertEquals(storage, operation.details.destination)
        assertEquals(payload.size, operation.details.payloadBytes)

        assertEquals(
            AfirmaDeliveryResult.ACKNOWLEDGED,
            operation.execute(identity.identity) { authorizationCount++ },
        )
        assertEquals(1, authorizationCount)
        assertEquals(1, uploads)
        assertEquals(URI(storage), uploadedEndpoint)
        assertEquals(2, checkNotNull(uploadedResult).split('|').size)
        operation.close()
    }

    private suspend fun executeAndAssertSingleAuthorizedUpload(invocation: AfirmaServletInvocation) {
        val identity = nonExportableSyntheticIdentity()
        var authorizationCount = 0
        var storeCount = 0
        var uploadedEndpoint: URI? = null
        var uploadedResult: String? = null
        val operation = NativeAfirmaOperation(
            invocation,
            AfirmaResultTransport { endpoint, _, result ->
                storeCount++
                uploadedEndpoint = endpoint
                uploadedResult = result
                AfirmaDeliveryResult.ACKNOWLEDGED
            },
            clock = Clock.fixed(identity.identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC),
        )
        assertEquals(
            AfirmaDeliveryResult.ACKNOWLEDGED,
            operation.execute(identity.identity) { authorizationCount++ },
        )
        assertEquals(1, authorizationCount)
        assertEquals(1, storeCount)
        assertEquals(URI(storage), uploadedEndpoint)
        assertEquals(2, checkNotNull(uploadedResult).split('|').size)
        assertEquals(storage, operation.details.destination)
        operation.close()
    }

    private fun rawUri(extra: Map<String, String> = emptyMap()): String {
        val query = fields(extra).entries.joinToString("&") {
            it.key + "=" + form(it.value)
        }
        return "afirma://sign?$query"
    }

    private fun intentUri(extra: Map<String, String> = emptyMap()): String {
        val query = fields(extra).entries.joinToString("&") {
            it.key + "=" + form(it.value)
        }
        return "intent://sign?$query#Intent;scheme=afirma;package=es.gob.afirma;end"
    }

    private fun fields(extra: Map<String, String>) = linkedMapOf(
        "id" to "ThirdPublicSession123",
        "stservlet" to storage,
        "key" to "12345678",
        "format" to "CAdES",
        "algorithm" to "SHA256withRSA",
        "dat" to base64("synthetic-third-public-payload".toByteArray()),
        "properties" to base64(
            "headless=true\nfilters=nonexpired:true;authCert:true".toByteArray(),
        ),
    ) + extra

    private fun form(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}
