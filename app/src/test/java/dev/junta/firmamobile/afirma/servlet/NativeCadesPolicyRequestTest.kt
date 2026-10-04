package dev.junta.firmamobile.afirma.servlet

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** Real parser admission for an explicit, fully supplied signature policy.
 * Synthetic OID/hash only; no document, policy URL or government page is fetched. */
class NativeCadesPolicyRequestTest {
    @Test fun suppliedOidPolicyIsAcceptedForAllPolicyHashFamilies() {
        for (hash in listOf("SHA-1", "SHA-256", "SHA-384", "SHA-512")) {
            val result = request(policy(hash))
            assertTrue("$hash: $result", result is AfirmaServletParseResult.Accepted)
            (result as AfirmaServletParseResult.Accepted).invocation.close()
        }
    }

    @Test fun urnOidAndOptionalHttpsQualifierAreAcceptedAsMetadata() {
        val properties = policy() + mapOf("policyIdentifier" to "urn:oid:$OID", "policyQualifier" to "https://policy.synthetic.example/description.pdf")
        val result = request(properties)
        assertTrue(result.toString(), result is AfirmaServletParseResult.Accepted)
        (result as AfirmaServletParseResult.Accepted).invocation.use { assertTrue(it.detached) }
    }

    @Test fun retrievedRequestAndPrecalculatedHashKeepExplicitPolicy() {
        val properties = policy("SHA-512") + mapOf("precalculatedHashAlgorithm" to "SHA-256", "mode" to "implicit")
        val digest = MessageDigest.getInstance("SHA-256").digest(DATA)
        val values = fields(properties, digest)
        val result = AfirmaServletInvocationParser.parseRetrieved(AfirmaServletOperation.SIGN, PAGE, values)
        assertTrue(result.toString(), result is AfirmaServletParseResult.Accepted)
        (result as AfirmaServletParseResult.Accepted).invocation.use {
            assertTrue(it.detached); assertArrayEquals(digest, it.payloadCopy())
        }
    }

    @Test fun incompleteUnknownOrWrongLengthPoliciesAreNotSilentlyDropped() {
        for (properties in listOf(
            mapOf("policyIdentifier" to OID), policy() - "policyIdentifier", policy() - "policyIdentifierHashAlgorithm",
            policy() + ("policyIdentifierHash" to "0"), policy() + ("policyIdentifierHash" to b64(ByteArray(31))),
            policy() + ("policyIdentifierHashAlgorithm" to "MD5"), policy() + ("policyIdentifier" to "not-an-oid"),
        )) assertFalse(request(properties) is AfirmaServletParseResult.Accepted)
    }

    @Test fun aPolicyUrlCannotTriggerImplicitFetchingOrCredentialUse() {
        for (id in listOf("https://policy.synthetic.example/policy", "file:///secret", "urn:oid:1.03.6", "3.1.2", "1.40.1")) {
            assertFalse(request(policy() + ("policyIdentifier" to id)) is AfirmaServletParseResult.Accepted)
        }
        assertFalse(request(policy() + ("policyQualifier" to "https://user:secret@policy.synthetic.example/file")) is AfirmaServletParseResult.Accepted)
    }

    @Test fun unsupportedFormatsAndOtherRequirementsRemainRejected() {
        for (format in listOf("PAdES", "XAdES")) assertFalse(request(policy(), format) is AfirmaServletParseResult.Accepted)
        assertFalse(request(policy() + ("tsaURL" to "https://tsa.synthetic.example")) is AfirmaServletParseResult.Accepted)
    }

    @Test fun ordinarySigningWithoutPolicyRemainsAccepted() {
        val result = request(mapOf("mode" to "implicit"))
        assertTrue(result is AfirmaServletParseResult.Accepted)
        (result as AfirmaServletParseResult.Accepted).invocation.use { assertFalse(it.detached) }
    }

    @Test fun delegatedServicePolicyParametersRetainTheirOriginalPath() {
        val props = policy() + ("serverUrl" to "https://signer.synthetic.example/service")
        val result = request(props, "CAdEStri")
        assertTrue(result.toString(), result is AfirmaServletParseResult.Accepted)
        (result as AfirmaServletParseResult.Accepted).invocation.use { assertEquals(OID, it.remoteOptions!!.properties["policyIdentifier"]) }
    }

    private fun request(properties: Map<String, String>, format: String = "CAdES") =
        AfirmaServletInvocationParser.parseRetrieved(AfirmaServletOperation.SIGN, PAGE, fields(properties, DATA) + ("format" to format))
    private fun fields(properties: Map<String, String>, data: ByteArray) = mapOf("id" to "Policy123", "stservlet" to STORE,
        "format" to "CAdES", "algorithm" to "SHA256withRSA", "dat" to b64(data),
        "properties" to b64(properties.entries.joinToString("\n") { "${it.key}=${it.value}" }.toByteArray()))
    companion object {
        const val OID = "1.3.6.1.4.1.55555.47.1"
        const val PAGE = "https://portal.synthetic.example"
        const val STORE = "https://storage.synthetic.example/put"
        val DATA: ByteArray get() = "Synthetic signing policy request".toByteArray()
        fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        fun policy(hash: String = "SHA-256") = mapOf("policyIdentifier" to OID,
            "policyIdentifierHashAlgorithm" to hash, "policyIdentifierHash" to b64(MessageDigest.getInstance(hash).digest("Synthetic policy bytes".toByteArray())))
    }
}
