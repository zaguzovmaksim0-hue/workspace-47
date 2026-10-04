package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.OpaqueRsaFixture
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.MessageDigest
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test

class NativeCadesPolicyCryptoTest {
    @Test fun actualSignedPolicyWorksAcrossSignatureDigestsAndBothPackagingForms() {
        val key = nonExportableSyntheticIdentity(); val engine = NativeCadesEngine(clock = generalClockFor(key.identity))
        for (algorithm in SigningAlgorithm.entries) for (hash in listOf("SHA-1", "SHA-256", "SHA-384", "SHA-512")) for (detached in listOf(false, true)) {
            val policy = checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy(hash)))
            val encoded = take(engine.signWithPolicy(DATA, key.identity, algorithm, detached, policy))
            try {
                assertTrue(engine.verifyWithPolicy(encoded, DATA, key.identity.certificate, algorithm, detached, policy))
                assertFalse(engine.verifyWithPolicy(encoded, DATA, key.identity.certificate, algorithm, detached, null))
                val cms = if (detached) CMSSignedData(CMSProcessableByteArray(DATA), encoded) else CMSSignedData(encoded)
                val signer = cms.signerInfos.signers.single()
                assertTrue(NativeCadesPolicy.matches(signer.signedAttributes, policy))
                assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(key.identity.certificate)))
            } finally { encoded.fill(0) }
        }
        assertEquals(0, key.encodedReads.get())
    }

    @Test fun signedPolicyCannotBeReplacedByAnotherHashEvenWithTheSameOid() {
        val key = nonExportableSyntheticIdentity(); val policy = normal(); val other = checkNotNull(NativeCadesPolicy.parse(
            NativeCadesPolicyRequestTest.policy() + ("policyIdentifierHash" to NativeCadesPolicyRequestTest.b64(ByteArray(32)))))
        val engine = NativeCadesEngine(clock = generalClockFor(key.identity)); val alg = SigningAlgorithm.SHA256_WITH_RSA
        val output = take(engine.signWithPolicy(DATA, key.identity, alg, true, policy))
        try { assertFalse(engine.verifyWithPolicy(output, DATA, key.identity.certificate, alg, true, other)) }
        finally { output.fill(0) }
    }

    @Test fun changingOnlyPolicyBytesBreaksTheRealCryptographicSignature() {
        val key = nonExportableSyntheticIdentity(); val policy = normal(); val engine = NativeCadesEngine(clock = generalClockFor(key.identity))
        val output = take(engine.signWithPolicy(DATA, key.identity, SigningAlgorithm.SHA256_WITH_RSA, true, policy))
        val hash = policy.hashCopy(); val offsets = (0..output.size - hash.size).filter { at -> hash.indices.all { output[at + it] == hash[it] } }
        assertEquals(1, offsets.size)
        output[offsets.single()] = (output[offsets.single()].toInt() xor 1).toByte()
        try {
            val signer = CMSSignedData(CMSProcessableByteArray(DATA), output).signerInfos.signers.single()
            assertFalse(runCatching { signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(key.identity.certificate)) }.getOrDefault(false))
            assertNull(NativeCadesHistory.inspect(output, NativeCadesEngine.MAX_OUTPUT_BYTES))
        } finally { output.fill(0); hash.fill(0) }
    }

    @Test fun cosigningKeepsTheOldPolicyAndBindsTheNewSignerToItsOwnExplicitPolicy() {
        val first = nonExportableSyntheticIdentity(); val second = freshConstraintIdentity(); val policy = normal()
        val changed = checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy("SHA-512") + ("policyIdentifier" to "1.3.6.1.4.1.55555.47.2")))
        val alg = SigningAlgorithm.SHA256_WITH_RSA; val clock = generalClockFor(second.identity)
        for (detached in listOf(false, true)) {
            val input = take(NativeCadesEngine(clock = generalClockFor(first.identity)).signWithPolicy(DATA, first.identity, alg, detached, policy))
            val oldRecord = CMSSignedData(input).signerInfos.signers.single().toASN1Structure().getEncoded(ASN1Encoding.DER)
            val result = take(NativeCadesCoSignEngine(clock).cosignWithPolicy(input, second.identity, alg, detached, changed))
            try {
                val cms = if (detached) CMSSignedData(CMSProcessableByteArray(DATA), result) else CMSSignedData(result)
                assertEquals(2, cms.signerInfos.size())
                assertEquals(1, cms.signerInfos.signers.count { it.toASN1Structure().getEncoded(ASN1Encoding.DER).contentEquals(oldRecord) })
                for (signer in cms.signerInfos.signers) {
                    val holder = cms.certificates.getMatches(null).filter(signer.sid::match).single()
                    assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(holder)))
                    assertTrue(NativeCadesPolicy.matches(signer.signedAttributes, if (holder.serialNumber == second.identity.certificate.serialNumber) changed else policy))
                }
            } finally { input.fill(0); result.fill(0); oldRecord.fill(0) }
        }
    }

    @Test fun policyAndPrecomputedDocumentDigestAreSeparateSignedAttributes() {
        val key = nonExportableSyntheticIdentity(); val policy = normal(); val alg = SigningAlgorithm.SHA384_WITH_RSA
        val digest = MessageDigest.getInstance("SHA-384").digest(DATA); val engine = NativeCadesEngine(clock = generalClockFor(key.identity))
        val output = take(engine.signDigestWithPolicy(digest, key.identity, alg, policy))
        try {
            assertNull(CMSSignedData(output).signedContent)
            assertTrue(engine.verifyDigestWithPolicy(output, digest, key.identity.certificate, alg, policy))
            assertTrue(engine.verifyWithPolicy(output, DATA, key.identity.certificate, alg, true, policy))
            assertFalse(engine.verifyWithPolicy(output, digest, key.identity.certificate, alg, true, policy))
        } finally { output.fill(0); digest.fill(0) }
    }

    @Test fun opaqueKeyProviderIsUsedWithoutExportWhenAPolicyIsRequested() = OpaqueRsaFixture.use { key ->
        val engine = NativeCadesEngine(clock = generalClockFor(key.identity)); val policy = normal()
        val output = take(engine.signWithPolicy(DATA, key.identity, SigningAlgorithm.SHA256_WITH_RSA, false, policy))
        try { assertTrue(engine.verifyWithPolicy(output, DATA, key.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, false, policy)) }
        finally { output.fill(0) }
        assertEquals(1, key.signatures.get()); assertEquals(0, key.encodingReads.get())
    }

    @Test fun noPolicyRequestStillProducesOrdinaryCadesWithoutAnInventedReference() {
        val key = nonExportableSyntheticIdentity(); val engine = NativeCadesEngine(clock = generalClockFor(key.identity)); val alg = SigningAlgorithm.SHA256_WITH_RSA
        val output = take(engine.sign(DATA, key.identity, alg, true))
        try {
            assertTrue(engine.verify(output, DATA, key.identity.certificate, alg, true))
            assertFalse(engine.verifyWithPolicy(output, DATA, key.identity.certificate, alg, true, normal()))
        } finally { output.fill(0) }
    }

    companion object {
        internal val DATA: ByteArray get() = "Synthetic explicit-policy CAdES document".toByteArray()
        internal fun normal() = checkNotNull(NativeCadesPolicy.parse(NativeCadesPolicyRequestTest.policy()))
        internal fun take(result: LocalSignatureResult): ByteArray {
            assertTrue(result.toString(), result is LocalSignatureResult.Success)
            return (result as LocalSignatureResult.Success).signature.use { it.withBytes(ByteArray::copyOf) }
        }
    }
}
