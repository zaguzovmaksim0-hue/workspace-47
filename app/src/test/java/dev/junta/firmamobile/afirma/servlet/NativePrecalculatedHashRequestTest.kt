package dev.junta.firmamobile.afirma.servlet

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** Actual request parser; no browser, personal key or signing-service call. */
class NativePrecalculatedHashRequestTest {
    @Test fun allFourDigestFamiliesAndOfficialPackagingModesAreAcceptedWithoutHashingAgain() {
        for ((hash, signature) in families) for (mode in listOf(null, "explicit", "implicit")) {
            val digest = MessageDigest.getInstance(hash).digest(DATA)
            val properties = "precalculatedHashAlgorithm=$hash" + (mode?.let { "\nmode=$it" } ?: "")
            val parsed = parse(values(signature, digest, properties))
            assertTrue("$hash/$mode: $parsed", parsed is AfirmaServletParseResult.Accepted)
            (parsed as AfirmaServletParseResult.Accepted).invocation.use {
                assertTrue("A precomputed digest never contains an original document", it.detached)
                assertArrayEquals(digest, it.payloadCopy()); assertEquals(digest.size, it.dataSize)
            }
        }
    }

    @Test fun compactAndCaseVariantsOfKnownDigestNamesHaveTheSameMeaning() {
        for ((hash, signature) in families) {
            val digest = MessageDigest.getInstance(hash).digest(DATA)
            for (name in listOf(hash.lowercase(), hash.replace("-", ""), hash.replace("-", "").lowercase())) {
                val result = parse(values(signature, digest, "precalculatedHashAlgorithm=$name"))
                assertTrue("$name: $result", result is AfirmaServletParseResult.Accepted)
                (result as AfirmaServletParseResult.Accepted).invocation.close()
            }
        }
    }

    @Test fun retrievedConfigurationIsNotConvertedIntoAnotherHashOrDecodedTwice() {
        val digest = MessageDigest.getInstance("SHA-256").digest(DATA)
        val fields = values("SHA256withRSA", digest, "precalculatedHashAlgorithm=SHA-256")
        val parsed = AfirmaServletInvocationParser.parseRetrieved(AfirmaServletOperation.SIGN, PAGE, fields)
        assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
        (parsed as AfirmaServletParseResult.Accepted).invocation.use { assertArrayEquals(digest, it.payloadCopy()) }
    }

    @Test fun wrongLengthsAndTextualHexAreNeverTreatedAsADocument() {
        for ((hash, signature) in families) {
            val digest = MessageDigest.getInstance(hash).digest(DATA)
            for (bad in listOf(ByteArray(0), ByteArray(digest.size - 1), ByteArray(digest.size + 1), digest.joinToString("") { "%02x".format(it) }.toByteArray())) {
                assertFalse(parse(values(signature, bad, "precalculatedHashAlgorithm=$hash")) is AfirmaServletParseResult.Accepted)
            }
        }
    }

    @Test fun mismatchedAlgorithmsAndUnknownHashNamesRemainUnsupported() {
        val digest = MessageDigest.getInstance("SHA-256").digest(DATA)
        for (hash in listOf("MD5", "SHA3-256", "SHA-128", "SHA256withRSA", "", "SHA-256 ")) {
            assertFalse(parse(values("SHA256withRSA", digest, "precalculatedHashAlgorithm=$hash")) is AfirmaServletParseResult.Accepted)
        }
        assertFalse(parse(values("SHA512withRSA", digest, "precalculatedHashAlgorithm=SHA-256")) is AfirmaServletParseResult.Accepted)
    }

    @Test fun precomputedDigestIsNotAnImplicitPermissionForOtherOperationsOrFormats() {
        val fields = values("SHA256withRSA", ByteArray(32), "precalculatedHashAlgorithm=SHA-256")
        for (format in listOf("PAdES", "XAdES")) assertFalse(parse(fields + ("format" to format)) is AfirmaServletParseResult.Accepted)
        for (operation in listOf("cosign", "countersign")) assertFalse(parse(fields, operation) is AfirmaServletParseResult.Accepted)
        assertFalse(parse(fields + ("properties" to b64("precalculatedHashAlgorithm=SHA-256\npolicyIdentifier=unknown".toByteArray()))) is AfirmaServletParseResult.Accepted)
    }

    @Test fun ordinaryDigestSizedContentWithoutThePropertyRemainsAnOrdinaryDocument() {
        for (length in listOf(20, 32, 48, 64)) {
            val parsed = parse(values("SHA256withRSA", ByteArray(length), "mode=implicit"))
            assertTrue(parsed is AfirmaServletParseResult.Accepted)
            (parsed as AfirmaServletParseResult.Accepted).invocation.use { assertFalse(it.detached) }
        }
    }

    @Test fun existingServiceRequestsKeepTheirOwnOpaqueServerParameters() {
        val fields = values("SHA256withRSA", DATA, "precalculatedHashAlgorithm=SHA-256") +
            mapOf("format" to "CAdEStri", "serverurl" to "https://signer.synthetic.example/service")
        val parsed = parse(fields)
        assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
        (parsed as AfirmaServletParseResult.Accepted).invocation.use {
            assertNotNull(it.remoteOptions)
            assertEquals("SHA-256", it.remoteOptions!!.properties["precalculatedHashAlgorithm"])
            assertArrayEquals(DATA, it.payloadCopy())
        }
    }

    private fun parse(values: Map<String, String>, operation: String = "sign") = AfirmaServletInvocationParser.parse(
        "afirma://$operation?" + values.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }, PAGE)
    private fun values(algorithm: String, data: ByteArray, properties: String) = mapOf("id" to "Hash123", "stservlet" to STORAGE,
        "format" to "CAdES", "algorithm" to algorithm, "dat" to b64(data), "properties" to b64(properties.toByteArray()))
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun b64(data: ByteArray) = Base64.getUrlEncoder().encodeToString(data)
    companion object {
        private val DATA = "Generated test document for hash-only request".toByteArray()
        private const val PAGE = "https://portal.synthetic.example"
        private const val STORAGE = "https://storage.synthetic.example/put"
        private val families = listOf("SHA-1" to "SHA1withRSA", "SHA-256" to "SHA256withRSA", "SHA-384" to "SHA384withRSA", "SHA-512" to "SHA512withRSA")
    }
}
