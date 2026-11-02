package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.MinecoPadesAdapter
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** AutoFirma `expPolicy` names expand to public AGE policy constants; nothing is fetched. */
class NativeSignaturePolicyExpansionTest {
    private val cades = NativeSignaturePolicyExpansion.Format.CADES
    private val pades = NativeSignaturePolicyExpansion.Format.PADES

    @Test fun propertiesWithoutAPolicyNameAreReturnedUnchanged() {
        val properties = mapOf("mode" to "implicit", "policyIdentifier" to "1.2.3")
        for (format in NativeSignaturePolicyExpansion.Format.entries) {
            assertSame(properties, NativeSignaturePolicyExpansion.expand(properties, format))
        }
        assertFalse(NativeSignaturePolicyExpansion.leavesCadesModeToPolicy(properties))
    }

    @Test fun firmaAgeExpandsToTheAgePolicyForCadesAndPades() {
        for (format in listOf(cades, pades)) {
            val properties = mapOf("expPolicy" to "FirmaAGE", "mode" to "explicit")
            val expanded = checkNotNull(NativeSignaturePolicyExpansion.policyProperties(properties, format))
            assertFalse(expanded.containsKey("expPolicy"))
            assertEquals("explicit", expanded["mode"])
            val policy = checkNotNull(NativeCadesPolicy.parse(expanded))
            assertEquals(MinecoPadesAdapter.AGE_POLICY_IDENTIFIER_OID, policy.identifier)
            assertEquals("SHA-1", policy.hashAlgorithm)
            assertEquals(MinecoPadesAdapter.AGE_POLICY_HASH_ALGORITHM_OID, policy.hashOid)
            assertArrayEquals(Base64.getDecoder().decode(MinecoPadesAdapter.AGE_POLICY_HASH_BASE64), policy.hashCopy())
            assertEquals(MinecoPadesAdapter.AGE_POLICY_QUALIFIER, policy.qualifier)
        }
    }

    @Test fun expandedValuesReplaceRequestSuppliedPolicyKeys() {
        val supplied = NativeCadesPolicyRequestTest.policy() +
            mapOf("policyQualifier" to "https://policy.synthetic.example/p.pdf", "expPolicy" to "FirmaAGE")
        val expanded = checkNotNull(NativeSignaturePolicyExpansion.expand(supplied, cades))
        assertEquals("urn:oid:2.16.724.1.3.1.1.2.1.9", expanded["policyIdentifier"])
        assertEquals("http://www.w3.org/2000/09/xmldsig#sha1", expanded["policyIdentifierHashAlgorithm"])
        assertEquals(MinecoPadesAdapter.AGE_POLICY_HASH_BASE64, expanded["policyIdentifierHash"])
        assertEquals(MinecoPadesAdapter.AGE_POLICY_QUALIFIER, expanded["policyQualifier"])
        assertEquals(supplied.keys - "expPolicy", expanded.keys)
    }

    @Test fun firmaAge18IsTheCadesOnlyAgePolicy18() {
        val properties = mapOf("expPolicy" to "FirmaAGE18")
        val expanded = checkNotNull(NativeSignaturePolicyExpansion.policyProperties(properties, cades))
        val policy = checkNotNull(NativeCadesPolicy.parse(expanded))
        assertEquals("2.16.724.1.3.1.1.2.1.8", policy.identifier)
        assertEquals("SHA-1", policy.hashAlgorithm)
        assertEquals(20, policy.hashCopy().size)
        assertTrue(policy.qualifier!!.endsWith("/politica_firma_AGE_v1_8.pdf"))
        assertNull(NativeSignaturePolicyExpansion.expand(properties, pades))
    }

    @Test fun unknownOrMisspelledPolicyNamesAreRejectedNotDropped() {
        for (name in listOf("", "firmaage", "FIRMAAGE", "FirmaAGE19", " FirmaAGE", "FirmaAGE ", "FirmaAGE18 ")) {
            for (format in NativeSignaturePolicyExpansion.Format.entries) {
                val properties = mapOf("expPolicy" to name)
                assertNull("'$name' $format", NativeSignaturePolicyExpansion.expand(properties, format))
                assertNull("'$name' $format", NativeSignaturePolicyExpansion.policyProperties(properties, format))
            }
        }
    }

    @Test fun cadesModeIsLeftToThePolicyOnlyWithoutAnExplicitMode() {
        assertTrue(NativeSignaturePolicyExpansion.leavesCadesModeToPolicy(mapOf("expPolicy" to "FirmaAGE")))
        for (mode in listOf("explicit", "implicit")) {
            val properties = mapOf("expPolicy" to "FirmaAGE", "mode" to mode)
            assertFalse(mode, NativeSignaturePolicyExpansion.leavesCadesModeToPolicy(properties))
        }
        assertFalse(NativeSignaturePolicyExpansion.leavesCadesModeToPolicy(mapOf("mode" to "implicit")))
        assertEquals(1_048_576, NativeSignaturePolicyExpansion.CADES_IMPLICIT_LIMIT_BYTES)
    }

    @Test fun xmlDsigDigestUrisNormalizeForExplicitPolicies() {
        val uris = mapOf(
            "http://www.w3.org/2000/09/xmldsig#sha1" to ("SHA-1" to 20),
            "http://www.w3.org/2001/04/xmlenc#sha256" to ("SHA-256" to 32),
            "http://www.w3.org/2001/04/xmldsig-more#sha384" to ("SHA-384" to 48),
            "HTTP://WWW.W3.ORG/2001/04/XMLENC#SHA512" to ("SHA-512" to 64),
        )
        for ((uri, expected) in uris) {
            val properties = mapOf(
                "policyIdentifier" to NativeCadesPolicyRequestTest.OID,
                "policyIdentifierHashAlgorithm" to uri,
                "policyIdentifierHash" to Base64.getEncoder().encodeToString(ByteArray(expected.second) { 7 }),
            )
            assertNull(uri, runCatching { NativeCadesPolicy.parse(properties) }.getOrNull())
            val normalized = checkNotNull(NativeSignaturePolicyExpansion.policyProperties(properties, cades))
            val policy = checkNotNull(NativeCadesPolicy.parse(normalized))
            assertEquals(uri, expected.first, policy.hashAlgorithm)
            assertEquals(uri, expected.second, policy.hashCopy().size)
        }
        val canonical = mapOf("policyIdentifierHashAlgorithm" to "SHA-256")
        assertSame(canonical, NativeSignaturePolicyExpansion.normalizeDigest(canonical))
    }
}
