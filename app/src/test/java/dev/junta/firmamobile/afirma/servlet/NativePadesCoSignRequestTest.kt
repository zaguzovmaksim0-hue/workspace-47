package dev.junta.firmamobile.afirma.servlet

import com.tom_roush.pdfbox.pdmodel.PDDocument
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.freshSyntheticIdentity
import java.net.URI
import java.net.URLEncoder
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NativePadesCoSignRequestTest {
    @Test fun canonicalPdfCoSignAliasesRejectMislabeledAndUnsupportedFormats() {
        for (format in listOf("PAdES", "PAdES Detached", "Adobe PDF")) {
            val accepted = parse(fields(nativePadesFixture(), format)) as AfirmaServletParseResult.Accepted
            accepted.invocation.use {
                assertEquals(AfirmaServletOperation.COSIGN, it.operation)
                assertTrue(it.pdfCoSign); assertNotNull(it.padesOptions)
            }
        }
        for (format in listOf("XAdES", "PAdES-LTA")) {
            assertTrue(parse(fields(nativePadesFixture(), format)) is AfirmaServletParseResult.Unsupported)
        }
        // CAdES co-sign is now supported, but raw PDF bytes are not a CMS signature.
        assertTrue(parse(fields(nativePadesFixture(), "CAdES")) is AfirmaServletParseResult.Invalid)
        assertTrue(parse(fields(nativePadesFixture()), "countersign") is AfirmaServletParseResult.Unsupported)
        assertTrue(parse(fields(nativePadesFixture()), "signandsave") is AfirmaServletParseResult.Unsupported)
    }

    @Test fun operationCannotBeSilentlySubstitutedThroughOpOrCop() {
        assertTrue(parse(fields(nativePadesFixture()).apply { this["op"] = "sign" }) is AfirmaServletParseResult.Invalid)
        assertTrue(parse(fields(nativePadesFixture()).apply { this["cop"] = "cosign" }, "sign") is AfirmaServletParseResult.Unsupported)
        assertTrue(parse(fields(nativePadesFixture()).apply { this["op"] = "cosign" }) is AfirmaServletParseResult.Accepted)
        val xml = AfirmaConfigurationXml.parse("<cosign><e k='op' v='cosign'/></cosign>".toByteArray())
        assertEquals(AfirmaServletOperation.COSIGN, xml.operation)
    }

    @Test fun exactNativePipelineSendsSecondPdfOnlyAfterIndependentAuthorization() = runBlocking {
        val key = freshSyntheticIdentity(); val clock = Clock.fixed(key.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
        val engine = NativePadesEngine(clock = clock)
        val first = (engine.sign(nativePadesFixture(), key, SigningAlgorithm.SHA256_WITH_RSA) as LocalSignatureResult.Success)
            .signature.use { it.withBytes { b -> b.copyOf() } }
        val invocation = (parse(fields(first)) as AfirmaServletParseResult.Accepted).invocation
        var sends = 0; var authorized = false; var value = ""
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { url, session, data ->
            assertEquals(URI("https://storage.example/put"), url); assertEquals("PdfCo123", session)
            assertTrue(authorized); sends++; value = data; AfirmaDeliveryResult.ACKNOWLEDGED
        }, clock = clock)
        assertEquals("cosign", operation.details.operation)
        assertTrue(operation.details.format!!.startsWith("PDF"))
        assertNotNull(operation.details.payloadSha256)
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key) { authorized = true })
        val fields = value.split('|'); assertEquals(2, fields.size)
        assertArrayEquals(key.certificate.encoded, AfirmaIntermediateCipher.decode(fields[0], null))
        val second = AfirmaIntermediateCipher.decode(fields[1], null)
        assertTrue(engine.verify(second, first, key.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        PDDocument.load(second).use { assertEquals(2, it.signatureDictionaries.size) }
        try { operation.execute(key) {}; fail("One protocol upload only") } catch (_: IllegalStateException) { }
        assertEquals(1, sends); operation.close(); first.fill(0); second.fill(0)
    }

    @Test fun unsignedOrDamagedInputDoesNotReachUploadAuthorization() = runBlocking {
        val key = freshSyntheticIdentity(); val clock = Clock.fixed(key.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
        var approved = 0; var sent = 0
        val request = (parse(fields(nativePadesFixture())) as AfirmaServletParseResult.Accepted).invocation
        val operation = NativeAfirmaOperation(request, AfirmaResultTransport { _, _, _ -> sent++; AfirmaDeliveryResult.ACKNOWLEDGED }, clock = clock)
        try { operation.execute(key) { approved++ }; fail("Co-sign requires a prior valid signature") }
        catch (_: IllegalStateException) { }
        assertEquals(0, approved); assertEquals(0, sent); operation.close()
    }

    @Test fun validCoSignStillCannotUploadAfterOwnerAuthorizationIsDenied() = runBlocking {
        val key = freshSyntheticIdentity(); val clock = Clock.fixed(key.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
        val first = (NativePadesEngine(clock = clock).sign(nativePadesFixture(), key, SigningAlgorithm.SHA256_WITH_RSA) as LocalSignatureResult.Success)
            .signature.use { it.withBytes { b -> b.copyOf() } }
        var sent = 0
        val operation = NativeAfirmaOperation((parse(fields(first)) as AfirmaServletParseResult.Accepted).invocation,
            AfirmaResultTransport { _, _, _ -> sent++; AfirmaDeliveryResult.ACKNOWLEDGED }, clock = clock)
        try { operation.execute(key) { throw CancellationException("Synthetic navigation change") }; fail("No upload after owner loss") }
        catch (_: CancellationException) { }
        assertEquals(0, sent); operation.close(); first.fill(0)
    }

    @Test fun encryptedCoSignDescriptorRetainsItsOperationAndCannotBecomeSign() = runBlocking {
        val values = fields(nativePadesFixture()).apply { this["op"] = "cosign"; this["key"] = "87654321" }
        val xml = "<cosign>" + values.entries.joinToString("") { "<e k='${it.key}' v='${form(it.value)}'/>" } + "</cosign>"
        val response = AfirmaIntermediateCipher.encode(xml.toByteArray(), "12345678").toByteArray()
        val reduced = "afirma://cosign?fileid=CoFile123&rid=PdfCo123&rtservlet=https%3A%2F%2Fretrieve.example%2Fget&stservlet=https%3A%2F%2Fstorage.example%2Fput&key=12345678"
        val deferred = (AfirmaServletInvocationParser.parse(reduced, "https://unprofiled.example/form") as AfirmaServletParseResult.Deferred).invocation
        var fetched = 0
        val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> fetched++; AfirmaRetrievedBytes(response) })
        val operation = resolver.resolve(deferred)
        assertEquals("cosign", operation.details.operation); assertNotNull(operation.details.format)
        assertEquals(1, fetched); assertTrue(response.all { it == 0.toByte() })
        try { resolver.resolve(deferred); fail("Repeated retrieval") } catch (_: IllegalStateException) { }
        operation.close()
        val mixed = xml.replace("<cosign>", "<sign>").replace("</cosign>", "</sign>")
        val badResponse = AfirmaIntermediateCipher.encode(mixed.toByteArray(), "12345678").toByteArray()
        val bad = (AfirmaServletInvocationParser.parse(reduced, "https://unprofiled.example/form") as AfirmaServletParseResult.Deferred).invocation
        try { AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> AfirmaRetrievedBytes(badResponse) }).resolve(bad); fail("No change of operation") }
        catch (expected: AfirmaRetrievalException) { assertEquals(AfirmaRetrievalProblem.INVALID, expected.problem) }
    }

    @Test fun explicitCancellationOfCoSignNeedsNoPrivateIdentity() = runBlocking {
        val operation = NativeAfirmaOperation((parse(fields(nativePadesFixture())) as AfirmaServletParseResult.Accepted).invocation,
            AfirmaResultTransport { _, _, value -> assertEquals("CANCEL", value); AfirmaDeliveryResult.ACKNOWLEDGED })
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.notifyCancellation {})
        operation.close()
    }

    private fun fields(pdf: ByteArray, format: String = "PAdES") = linkedMapOf("id" to "PdfCo123", "stservlet" to "https://storage.example/put",
        "format" to format, "algorithm" to "SHA256withRSA", "dat" to Base64.getEncoder().encodeToString(pdf))
    private fun parse(fields: Map<String, String>, operation: String = "cosign") = AfirmaServletInvocationParser.parse(
        "afirma://$operation?" + fields.entries.joinToString("&") { "${it.key}=${form(it.value)}" }, "https://unprofiled.example/form")
    private fun form(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
