package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class AfirmaServletInvocationParserTest {
    @Test fun ordinaryInlineSigningNeedsNoSiteProfileAndUsesOfficialExplicitDefault() {
        accepted(sign()).use {
            assertEquals(AfirmaServletOperation.SIGN, it.operation)
            assertEquals("https://unknown.example", it.sourceOrigin)
            assertEquals("https://storage.example/StorageService", it.storageUrl.toString())
            assertEquals(SigningAlgorithm.SHA256_WITH_RSA, it.algorithm)
            assertTrue(it.detached)
            assertArrayEquals("payload".toByteArray(), it.payloadCopy())
        }
    }

    @Test fun knownPropertyCaseOrderAndLineEndingsAreNormalizedWithoutChangingMode() {
        for (props in listOf("mode=implicit", "mode : IMPLICIT\r\n", "# note\nmode implicit\n", "\r\nmode=implicit\r\n")) {
            accepted(sign(mapOf("format" to "CaDeS", "properties" to b64(props)))).use { assertFalse(it.detached) }
        }
        accepted(sign(mapOf("properties" to b64("mode=explicit")))).use { assertTrue(it.detached) }
    }

    @Test fun realCallerMetadataAndParameterOrderAreAccepted() {
        val raw = sign(mapOf("jvc" to "1.8", "ver" to "4", "op" to "sign", "aw" to "true", "appname" to "Sede", "dlgload" to "false"))
        val shuffled = raw.substringBefore('?') + "?" + raw.substringAfter('?').split('&').reversed().joinToString("&")
        accepted(shuffled).use { assertArrayEquals("payload".toByteArray(), it.payloadCopy()) }
    }

    @Test fun base64PlusAndUrlSafeAlphabetKeepTheActualBytes() {
        val payload = byteArrayOf(-5, -17, -1)
        val standard = Base64.getEncoder().encodeToString(payload)
        accepted(sign(mapOf("dat" to standard))).use { assertArrayEquals(payload, it.payloadCopy()) }
        accepted(sign(mapOf("dat" to Base64.getUrlEncoder().encodeToString(payload)))).use { assertArrayEquals(payload, it.payloadCopy()) }
        accepted(sign().replace("cGF5bG9hZA%3D%3D", standard)).use { assertArrayEquals(payload, it.payloadCopy()) }
    }

    @Test fun selectionHasNoSigningPayloadAndNoAutomaticCertificateFilter() {
        accepted(select()).use { assertEquals(AfirmaServletOperation.SELECT_CERTIFICATE, it.operation); assertEquals(0, it.dataSize) }
        unsupported(select(mapOf("properties" to b64("filters=nonexpired:"))))
        invalid(select(mapOf("dat" to "AA==")))
    }

    @Test fun duplicateDecodedKeysAndDuplicatePropertiesAreRejected() {
        invalid(sign() + "&%69d=Other")
        invalid(sign() + "&dat=AA%3D%3D")
        invalid(sign(mapOf("properties" to b64("mode=implicit\nmode=explicit"))))
    }

    @Test fun requestConstraintsAreNotSilentlyDropped() {
        for (props in listOf("policyIdentifier=1.2.3", "precalculatedHashAlgorithm=SHA-256", "mode=unknown", "mode=implicit\ncontentTypeOid=1.2.3", "mode=imp\\licit")) {
            unsupported(sign(mapOf("properties" to b64(props))))
        }
        unsupported(sign(mapOf("mcv" to "99.0")))
        unsupported(sign(mapOf("sticky" to "true")))
        unsupported(sign(mapOf("unknown" to "value")))
        unsupported(sign(mapOf("algorithm" to "SHA256withECDSA")))
        unsupported(sign(mapOf("format" to "XAdES-T")))
    }

    @Test fun mixedDataIsInvalidAndUnimplementedMultiOperationVariantsRemainUnsupported() {
        invalid(sign(mapOf("fileid" to "file123", "rtservlet" to "https://store.example/retrieve")))
        unsupported(sign(mapOf("dat" to "https://store.example/data")))
        unsupported(sign(mapOf("cop" to "cosign")))
        invalid(sign().replace("afirma://sign", "afirma://batch")) // A batch requires a structured descriptor, not arbitrary payload.
        unsupported(sign().replace("&dat=cGF5bG9hZA%3D%3D", ""))
    }

    @Test fun invalidSourcesAndUnsafeStorageTargetsCannotPrompt() {
        for (page in listOf("http://unknown.example", "https://user@unknown.example", "https://127.0.0.1", "javascript:foo")) {
            assertTrue(AfirmaServletInvocationParser.parse(sign(), page) is AfirmaServletParseResult.Invalid)
        }
        for (endpoint in listOf("http://storage.example/", "https://127.0.0.1/", "https://user@storage.example/", "https://storage.example/#fragment", "https://storage.example/?id=other")) {
            invalid(sign(mapOf("stservlet" to endpoint)))
        }
        accepted(sign(mapOf("stservlet" to "https://storage.example:8443/store?route=tenant"))).use {
            assertEquals("route=tenant", it.storageUrl.query)
        }
    }

    @Test fun malformedEncodingAndAmbiguousProtocolParametersAreInvalid() {
        for (suffix in listOf("&id=%ZZ", "&appname=%C3%28", "&appname=%0A", "&appname=%", "&=x", "&")) invalid(sign() + suffix)
        for (data in listOf("?", "a", "Zh==", "a===", "a b", "ab-_+/")) invalid(sign(mapOf("dat" to data)))
        invalid(sign(mapOf("op" to "selectcert")))
        invalid(sign(mapOf("v" to "3", "ver" to "4")))
        invalid(sign(mapOf("id" to "../escape")))
        invalid(sign(mapOf("key" to "too-short")))
    }

    @Test fun limitsAreCheckedBeforeDecodingUnboundedInput() {
        invalid(sign(mapOf("dat" to "A".repeat(800000))))
        invalid("afirma://sign?x=" + "a".repeat(1048576))
        invalid(sign() + (1..70).joinToString("") { "&field$it=x" })
    }

    @Test fun payloadOwnershipAndDiagnosticsDoNotExposeUriSecrets() {
        val parsed = accepted(sign())
        val first = parsed.payloadCopy(); first.fill(0)
        assertArrayEquals("payload".toByteArray(), parsed.payloadCopy())
        assertFalse(parsed.toString().contains("Session123")); assertFalse(parsed.toString().contains("12345678"))
        parsed.close(); parsed.close()
        assertThrows(IllegalStateException::class.java) { parsed.payloadCopy() }
    }

    @Test fun advancedAesCbcIsParsedWithoutFallingBackToLegacy() {
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val iv = Base64.getEncoder().encodeToString(ByteArray(16) { (it + 32).toByte() })
        accepted(sign(mapOf("cipher" to b64("{\"iv\":\"$iv\",\"algo\":\"AES\",\"key\":\"$key\",\"legDes\":\"12345678\"}")))).use { invocation ->
            checkNotNull(invocation.cipherCopy()).use { cipher ->
                val wire = cipher.encode("data".toByteArray())
                assertFalse(wire.contains('.'))
                assertArrayEquals("data".toByteArray(), cipher.decode(wire))
            }
        }
    }

    @Test fun invalidOrUnknownAdvancedCipherNeverDowngradesToDes() {
        unsupported(sign(mapOf("cipher" to b64("{\"algo\":\"AES-GCM\",\"key\":\"AA==\",\"iv\":\"AA==\"}"))))
        invalid(sign(mapOf("cipher" to b64("{\"algo\":\"AES\",\"key\":\"AA==\",\"iv\":\"AA==\"}"))))
        invalid(sign(mapOf("cipher" to b64("{\"algo\":\"AES\",\"algo\":\"DES\"}"))))
        invalid(sign(mapOf("cipher" to b64("{\"algo\":\"AES\",\"legDes\":\"87654321\"}"))))
    }

    @Test fun propertyAndQueryKeyCaseDoesNotSilentlyChangeOfficialSemantics() {
        unsupported(sign(mapOf("properties" to b64("MODE=implicit"))))
        unsupported(sign(mapOf("properties" to b64("mode=explicit\nMODE=implicit"))))
        unsupported(sign(mapOf("FORMAT" to "CAdES")))
        unsupported(sign(mapOf("cipher" to b64("{\"ALGO\":\"AES\",\"key\":\"AA==\",\"iv\":\"AA==\"}"))))
    }

    @Test fun explicitlyDocumentedJavaAndJavaScriptLegacyKeyFieldsAreSupportedWithoutCaseFolding() {
        val key = Base64.getEncoder().encodeToString(ByteArray(32))
        val iv = Base64.getEncoder().encodeToString(ByteArray(16))
        for (legacyName in listOf("legDes", "legacydes")) {
            accepted(sign(mapOf("cipher" to b64("{\"algo\":\"AES\",\"key\":\"$key\",\"iv\":\"$iv\",\"$legacyName\":\"12345678\"}")))).use {
                assertNotNull(it.cipherCopy()?.also { value -> value.close() })
            }
        }
        invalid(sign(mapOf("cipher" to b64("{\"algo\":\"AES\",\"key\":\"$key\",\"iv\":\"$iv\",\"legDes\":\"12345678\",\"legacydes\":\"87654321\"}"))))
    }

    private fun sign(extra: Map<String, String> = emptyMap()): String = uri("sign", linkedMapOf(
        "id" to "Session123", "stservlet" to "https://storage.example/StorageService", "key" to "12345678",
        "format" to "CAdES", "algorithm" to "SHA256withRSA", "dat" to b64("payload"),
    ) + extra)
    private fun select(extra: Map<String, String> = emptyMap()): String = uri("selectcert", linkedMapOf(
        "id" to "Session123", "stservlet" to "https://storage.example/StorageService", "key" to "12345678",
    ) + extra)
    private fun uri(operation: String, values: Map<String, String>) = "afirma://$operation?" + values.entries.joinToString("&") {
        it.key + "=" + URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")
    }
    private fun b64(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun accepted(raw: String): AfirmaServletInvocation {
        val result = AfirmaServletInvocationParser.parse(raw, "https://unknown.example/form?token=not-shown#part")
        assertTrue(result.toString(), result is AfirmaServletParseResult.Accepted)
        return (result as AfirmaServletParseResult.Accepted).invocation
    }
    private fun invalid(raw: String) { assertTrue(AfirmaServletInvocationParser.parse(raw, "https://unknown.example/").toString(), AfirmaServletInvocationParser.parse(raw, "https://unknown.example/") is AfirmaServletParseResult.Invalid) }
    private fun unsupported(raw: String) { assertTrue(AfirmaServletInvocationParser.parse(raw, "https://unknown.example/").toString(), AfirmaServletInvocationParser.parse(raw, "https://unknown.example/") is AfirmaServletParseResult.Unsupported) }
}
