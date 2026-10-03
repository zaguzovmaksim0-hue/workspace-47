package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

/** Request-parser integration only; no WebView, E2E or live signing service. */
class NativeJavaPropertiesRequestTest {
    @Test fun standardJavaSerializerCanProvideAccentedPdfMetadata() {
        val original = mapOf("signReason" to "Renovación: trámite nº 3", "signatureProductionCity" to "Cádiz",
            "signerContact" to "Persona #1", "headless" to "false")
        val encoded = ByteArrayOutputStream().also { out ->
            Properties().apply { original.forEach { (k, v) -> setProperty(k, v) } }.store(out, "Synthetic only")
        }.toByteArray()
        val result = parseBytes(encoded, "PAdES", nativePadesFixture())
        accepted(result).use {
            assertEquals(original["signReason"], it.padesOptions!!.reason)
            assertEquals(original["signatureProductionCity"], it.padesOptions!!.location)
            assertEquals(original["signerContact"], it.padesOptions!!.contact)
        }
    }

    @Test fun escapedLineBreaksContinueOneLogicalProperty() {
        for (ending in listOf("\n", "\r\n", "\r")) {
            val text = "# comment$ending" + "mo\\${ending}  de=im\\${ending}\tplicit$ending"
            accepted(parse(text)).use { assertFalse(it.detached) }
        }
    }

    @Test fun remoteValuesKeepSignificantSpacesAndLiteralBackslashes() {
        val text = "message=\\ leading value  \npath=C:\\\\docs\\\\file\\:signed"
        accepted(parse(text, "XAdEStri", extra = mapOf("serverurl" to SERVICE))).use {
            assertEquals(" leading value  ", it.remoteOptions!!.properties["message"])
            assertEquals("C:\\docs\\file:signed", it.remoteOptions!!.properties["path"])
        }
    }

    @Test fun escapedCertificateKeyAndSeparatorRetainExactIdentityBinding() {
        val identity = nonExportableSyntheticIdentity().identity
        val cert = Base64.getEncoder().encodeToString(identity.certificate.encoded)
        val props = "filt\\u0065rs=encodedcert\\:$cert\nheadless=true"
        accepted(parse(props)).use {
            assertTrue(it.requiresExactCertificate)
            assertTrue(it.matchesCertificate(identity.certificate))
            assertTrue(it.detached)
        }
    }

    @Test fun retrievedDescriptorUsesTheSamePropertyGrammarExactlyOnce() {
        val values = fields("CAdES", "synthetic".toByteArray()) + ("properties" to b64("mode=im\\u0070licit".toByteArray()))
        accepted(AfirmaServletInvocationParser.parseRetrieved(AfirmaServletOperation.SIGN, SOURCE, values)).use {
            assertFalse(it.detached)
            assertArrayEquals("synthetic".toByteArray(), it.payloadCopy())
        }
    }

    @Test fun escapedUnknownSignatureRequirementsAreStillUnsupported() {
        for (props in listOf("policy\\u0049dentifier=1.2.3", "headless=maybe", "mode=unknown")) {
            assertTrue(AfirmaServletInvocationParser.parse(uri(props), SOURCE) is AfirmaServletParseResult.Unsupported ||
                AfirmaServletInvocationParser.parse(uri(props), SOURCE) is AfirmaServletParseResult.Invalid)
        }
    }

    @Test fun duplicateKeysAreDetectedAfterEscapeDecoding() {
        for (text in listOf("mode=explicit\nmo\\u0064e=implicit", "mode=explicit\nmo\\\n de=implicit")) {
            val result = parse(text)
            assertTrue("Decoded duplicate must not select one value: $result", result is AfirmaServletParseResult.Invalid)
        }
    }

    @Test fun keyWithoutDelimiterHasAnEmptyValueForARemoteService() {
        accepted(parse("optionalFlag\n# ordinary comment", "XAdEStri", extra = mapOf("serverurl" to SERVICE))).use {
            assertEquals(mapOf("optionalFlag" to ""), it.remoteOptions!!.properties)
        }
    }

    @Test fun malformedOrBinaryPropertiesNeverProduceASigningRequest() {
        for (text in listOf("mode=im\\u00xxplicit", "mode=explicit\u0000", "mode=explicit\nmode=implicit", "serverUrl=bad\u001b")) {
            assertFalse(parse(text) is AfirmaServletParseResult.Accepted)
        }
    }

    @Test fun ordinaryExistingPropertiesRemainAccepted() {
        accepted(parse("! unchanged\nmode : explicit\r\n")).use { assertTrue(it.detached) }
    }

    private fun accepted(result: AfirmaServletParseResult): AfirmaServletInvocation {
        assertTrue(result.toString(), result is AfirmaServletParseResult.Accepted)
        return (result as AfirmaServletParseResult.Accepted).invocation
    }
    private fun parse(text: String, format: String = "CAdES", data: ByteArray = "synthetic".toByteArray(), extra: Map<String, String> = emptyMap()) =
        parseBytes(text.toByteArray(), format, data, extra)
    private fun parseBytes(bytes: ByteArray, format: String, data: ByteArray, extra: Map<String, String> = emptyMap()) =
        AfirmaServletInvocationParser.parse(url(fields(format, data) + extra + ("properties" to b64(bytes))), SOURCE)
    private fun uri(text: String) = url(fields("CAdES", "synthetic".toByteArray()) + ("properties" to b64(text.toByteArray())))
    private fun fields(format: String, data: ByteArray) = mapOf("id" to "Properties123", "stservlet" to STORAGE,
        "format" to format, "algorithm" to "SHA256withRSA", "dat" to b64(data))
    private fun url(values: Map<String, String>) = "afirma://sign?" + values.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().encodeToString(bytes)
    companion object {
        private const val SOURCE = "https://portal.synthetic.example"
        private const val STORAGE = "https://storage.synthetic.example/put"
        private const val SERVICE = "https://signer.synthetic.example/service"
    }
}
