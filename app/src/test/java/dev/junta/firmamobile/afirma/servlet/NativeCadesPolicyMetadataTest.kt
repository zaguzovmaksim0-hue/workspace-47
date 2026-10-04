package dev.junta.firmamobile.afirma.servlet

import java.security.MessageDigest
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.esf.SignaturePolicyIdentifier
import org.bouncycastle.asn1.cms.AttributeTable
import org.junit.Assert.*
import org.junit.Test

class NativeCadesPolicyMetadataTest {
    @Test fun independentlyDecodedPolicyHasExactOidDigestAndOptionalQualifier() {
        for (name in listOf("SHA-1", "SHA-256", "SHA-384", "SHA-512")) for (url in listOf(null, "http://policy.synthetic.example/readable.pdf", "https://policy.synthetic.example/readable.pdf#section")) {
            val props = NativeCadesPolicyRequestTest.policy(name) + if (url == null) emptyMap() else mapOf("policyQualifier" to url)
            val policy = checkNotNull(NativeCadesPolicy.parse(props))
            val decoded = SignaturePolicyIdentifier.getInstance(ASN1Primitive.fromByteArray(policy.encoded())).signaturePolicyId
            assertEquals(NativeCadesPolicyRequestTest.OID, decoded.sigPolicyId.id)
            assertEquals(policy.hashOid, decoded.sigPolicyHash.hashAlgorithm.algorithm.id)
            assertArrayEquals(MessageDigest.getInstance(name).digest("Synthetic policy bytes".toByteArray()), decoded.sigPolicyHash.hashValue.octets)
            if (url == null) assertNull(decoded.sigPolicyQualifiers) else {
                val qualifier = decoded.sigPolicyQualifiers.getInfoAt(0)
                assertEquals("1.2.840.113549.1.9.16.5.1", qualifier.sigPolicyQualifierId.id)
                assertEquals(url, DERIA5String.getInstance(qualifier.sigQualifier).string)
            }
        }
    }

    @Test fun supportedNameAliasesDoNotChangeSignedDer() {
        val reference = checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy()))
        assertArrayEquals(reference.encoded(), checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy() + ("policyQualifier" to ""))).encoded())
        for (name in listOf("SHA256", "sha-256", reference.hashOid)) {
            val changed = checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy() + mapOf(
                "policyIdentifier" to "URN:OID:" + NativeCadesPolicyRequestTest.OID, "policyIdentifierHashAlgorithm" to name)))
            assertArrayEquals(reference.encoded(), changed.encoded())
        }
    }

    @Test fun outputAndInputChangesCannotMutateAnAcceptedPolicy() {
        val props = NativeCadesPolicyRequestTest.policy().toMutableMap()
        val policy = checkNotNull(NativeCadesPolicy.parse(props)); val original = policy.encoded()
        props["policyIdentifier"] = "1.2.3.9"; props["policyIdentifierHash"] = "garbage"
        policy.hashCopy().fill(0); policy.encoded().fill(0)
        assertArrayEquals(original, policy.encoded())
        assertFalse(policy.toString().contains(NativeCadesPolicyRequestTest.OID))
    }

    @Test fun signatureVerificationRequiresTheWholeReferenceNotJustItsIdentifier() {
        val policy = checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy()))
        val attributes = AttributeTable(policy.attribute())
        assertTrue(NativeCadesPolicy.matches(attributes, policy)); assertFalse(NativeCadesPolicy.matches(attributes, null))
        for (entry in listOf("policyIdentifier" to "1.2.3.4", "policyIdentifierHash" to NativeCadesPolicyRequestTest.b64(ByteArray(32)),
            "policyQualifier" to "https://policy.synthetic.example/changed")) {
            val other = checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy() + entry))
            assertFalse(NativeCadesPolicy.matches(attributes, other))
        }
    }

    @Test fun duplicateOrMissingSignedPolicyAttributeIsRejected() {
        val policy = checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy()))
        val empty = AttributeTable(org.bouncycastle.asn1.ASN1EncodableVector())
        assertFalse(NativeCadesPolicy.matches(empty, policy)); assertTrue(NativeCadesPolicy.matches(empty, null))
        val duplicates = org.bouncycastle.asn1.ASN1EncodableVector().apply { add(policy.attribute()); add(policy.attribute()) }
        assertFalse(NativeCadesPolicy.matches(AttributeTable(duplicates), policy))
    }

    @Test fun identifierAndQualifierLimitsRejectMalformedOrUnboundedMetadata() {
        val base = NativeCadesPolicyRequestTest.policy()
        for (oid in listOf("", "1", "1.2.", "1.02.3", "1.40.0", "0.40", "3.2", "2." + "1".repeat(257), "2." + List(33) { "1" }.joinToString("."))) {
            assertThrows(IllegalArgumentException::class.java) { NativeCadesPolicy.parse(base + ("policyIdentifier" to oid)) }
        }
        for (url in listOf("file:///etc/passwd", "https://u:p@policy.synthetic.example", "https://policy.synthetic.example/a b", "https://policy.synthetic.example/" + "a".repeat(2048))) {
            assertThrows(Exception::class.java) { NativeCadesPolicy.parse(base + ("policyQualifier" to url)) }
        }
    }

    @Test fun missingHashIsNeverSynthesizedFromAnIdentifierOrUrl() {
        assertNull(NativeCadesPolicy.parse(mapOf("mode" to "explicit")))
        for (extra in listOf(emptyMap(), mapOf("policyIdentifierHash" to "0"), mapOf("policyIdentifierHash" to "AA=="))) {
            assertThrows(Exception::class.java) { NativeCadesPolicy.parse(mapOf("policyIdentifier" to NativeCadesPolicyRequestTest.OID) + extra) }
        }
    }
}
