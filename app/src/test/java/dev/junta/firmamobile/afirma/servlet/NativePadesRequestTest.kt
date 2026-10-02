package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URI
import java.net.URLEncoder
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NativePadesRequestTest {
    @Test fun aliasesAndRetrievedValuesReachPdfDispatchWhileCadesStaysStrict() {
        val pdf = nativePadesFixture()
        for (format in listOf("PAdES", "pades", "PAdES Detached", "Adobe PDF")) {
            val parsed = parse(pdf, format) as AfirmaServletParseResult.Accepted
            parsed.invocation.use { assertNotNull(it.padesOptions); assertArrayEquals(pdf, it.payloadCopy()) }
        }
        val retrieved = AfirmaServletInvocationParser.parseRetrieved(AfirmaServletOperation.SIGN, "https://portal.example", fields(pdf)) as AfirmaServletParseResult.Accepted
        retrieved.invocation.use { assertNotNull(it.padesOptions) }
        assertTrue(parse(pdf, "PAdEStri") is AfirmaServletParseResult.Invalid) // Missing mandatory pre/post service, not a silent local fallback.
        assertTrue(parse(pdf, "CAdES", "signatureSubFilter=ETSI.CAdES.detached") is AfirmaServletParseResult.Unsupported)
    }
    @Test fun unsupportedPdfInstructionsAreNotSilentlyDiscarded() {
        for (property in listOf("signatureField=Existing", "signaturePages=1", "policyIdentifier=https://policy.example", "tsaURL=https://tsa.example", "allowSigningCertifiedPdfs=true")) {
            assertTrue(property, parse(nativePadesFixture(), properties = property) is AfirmaServletParseResult.Unsupported)
        }
        assertTrue(parse("not PDF".toByteArray()) is AfirmaServletParseResult.Invalid)
    }
    @Test fun actualPipelineDeliversACertificateAndSignedPdfOnceAfterAuthorization() = runBlocking {
        val id = nonExportableSyntheticIdentity(); val input = nativePadesFixture()
        val clock = Clock.fixed(id.identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
        for (key in listOf<String?>(null, "12345678")) {
            val invocation = (parse(input, key = key) as AfirmaServletParseResult.Accepted).invocation
            var authorized = 0; var sends = 0; var wire: String? = null
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { url, request, value ->
                assertEquals(URI("https://storage.example/put"), url); assertEquals("Pdf-123", request)
                assertEquals(1, authorized); sends++; wire = value; AfirmaDeliveryResult.ACKNOWLEDGED
            }, clock = clock)
            assertTrue(operation.details.format!!.startsWith("PDF · PAdES"))
            assertEquals(input.size, operation.details.payloadBytes)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(id.identity) { authorized++ })
            val parts = wire!!.split('|'); assertEquals(2, parts.size)
            val cert = AfirmaIntermediateCipher.decode(parts[0], key); val pdf = AfirmaIntermediateCipher.decode(parts[1], key)
            assertArrayEquals(id.identity.certificate.encoded, cert)
            assertTrue(NativePadesEngine(clock = clock).verify(pdf, input, id.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA))
            try { operation.execute(id.identity) {}; fail("Duplicate operation") } catch (_: IllegalStateException) { }
            assertEquals(1, sends); operation.close(); cert.fill(0); pdf.fill(0)
        }
        assertEquals(0, id.encodedReads.get())
    }
    @Test fun rejectedUploadAuthorityNeverSendsEvenAValidSignature() = runBlocking {
        val id = nonExportableSyntheticIdentity(); var sends = 0
        val invocation = (parse(nativePadesFixture()) as AfirmaServletParseResult.Accepted).invocation
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, _ -> sends++; AfirmaDeliveryResult.ACKNOWLEDGED },
            clock = Clock.fixed(id.identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC))
        try { operation.execute(id.identity) { throw CancellationException("synthetic stale owner") }; fail("Must cancel") }
        catch (_: CancellationException) { }
        assertEquals(0, sends); operation.close()
    }
    @Test fun cancelIsKeyFreeAndKeepsTheExistingLiteralProtocol() = runBlocking {
        val invocation = (parse(nativePadesFixture(), key = "12345678") as AfirmaServletParseResult.Accepted).invocation
        var allowed = false; var sends = 0
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, value ->
            assertTrue(allowed); assertEquals("CANCEL", value); sends++; AfirmaDeliveryResult.ACKNOWLEDGED
        })
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.notifyCancellation { allowed = true })
        assertEquals(1, sends); operation.close()
    }
    @Test fun encryptedRetrievedDescriptorProducesTheSameTypedPdfOperationExactlyOnce() = runBlocking {
        val pdf = nativePadesFixture()
        val values = fields(pdf).apply { this["key"] = "87654321" }
        val xml = "<sign>" + values.entries.joinToString("") {
            "<e k='${it.key}' v='${URLEncoder.encode(it.value, "UTF-8")}'/>"
        } + "</sign>"
        val response = AfirmaIntermediateCipher.encode(xml.toByteArray(), "12345678").toByteArray()
        val reduced = "afirma://sign?fileid=PdfFile123&rid=Pdf-123&rtservlet=https%3A%2F%2Fretrieve.example%2Fget&stservlet=https%3A%2F%2Fstorage.example%2Fput&key=12345678"
        val deferred = (AfirmaServletInvocationParser.parse(reduced, "https://unprofiled.example/form") as AfirmaServletParseResult.Deferred).invocation
        var fetches = 0; var actual: AfirmaServletInvocation? = null
        val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> fetches++; AfirmaRetrievedBytes(response) },
            operationFactory = { invocation -> actual = invocation; NativeAfirmaOperation(invocation) })
        val operation = resolver.resolve(deferred)
        assertTrue(operation.details.format!!.startsWith("PDF · PAdES"))
        assertArrayEquals(pdf, actual!!.payloadCopy()); assertEquals("87654321", actual!!.key)
        assertEquals(1, fetches); assertTrue(response.all { it == 0.toByte() })
        try { resolver.resolve(deferred); fail("Repeated consuming retrieval") } catch (_: IllegalStateException) { }
        assertEquals(1, fetches); operation.close()
    }

    private fun fields(pdf: ByteArray, format: String = "PAdES") = linkedMapOf("id" to "Pdf-123", "stservlet" to "https://storage.example/put", "format" to format, "algorithm" to "SHA256withRSA", "dat" to Base64.getEncoder().encodeToString(pdf))
    private fun parse(pdf: ByteArray, format: String = "PAdES", properties: String = "", key: String? = null): AfirmaServletParseResult {
        val values = fields(pdf, format)
        if (properties.isNotEmpty()) values["properties"] = Base64.getEncoder().encodeToString(properties.toByteArray())
        if (key != null) values["key"] = key
        return AfirmaServletInvocationParser.parse("afirma://sign?" + values.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8").replace("+", "%20") }, "https://unprofiled.example/start")
    }
}
