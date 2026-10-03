package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class NativeSha384CryptoTest {
    private val algorithm = SigningAlgorithm.SHA384_WITH_RSA

    @Test fun detachedCadesUsesTheRequestedDigestAndRejectsChangedData() {
        val key = nonExportableSyntheticIdentity(); val payload = "SHA-384 synthetic content".toByteArray()
        val bytes = signature(NativeCadesEngine(clock = generalClockFor(key.identity)).sign(payload, key.identity, algorithm, true))
        try {
            val signer = CMSSignedData(CMSProcessableByteArray(payload), bytes).signerInfos.signers.single()
            assertEquals("2.16.840.1.101.3.4.2.2", signer.digestAlgOID)
            assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(key.identity.certificate)))
            val tampered = payload.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
            assertFalse(runCatching {
                CMSSignedData(CMSProcessableByteArray(tampered), bytes).signerInfos.signers.single()
                    .verify(JcaSimpleSignerInfoVerifierBuilder().build(key.identity.certificate))
            }.getOrDefault(false))
            tampered.fill(0); assertEquals(0, key.encodedReads.get())
        } finally { bytes.fill(0); payload.fill(0) }
    }

    @Test fun sha384PdfCanBeCosignedWithoutLosingItsPriorSignature() {
        val key = nonExportableSyntheticIdentity(); val engine = NativePadesEngine(clock = generalClockFor(key.identity))
        val original = nativePadesFixture()
        val first = signature(engine.sign(original, key.identity, algorithm))
        val second = signature(engine.sign(first, key.identity, SigningAlgorithm.SHA256_WITH_RSA, requireExistingSignature = true))
        try {
            assertTrue(engine.verify(first, original, key.identity.certificate, algorithm))
            assertFalse(engine.verify(first, original, key.identity.certificate, SigningAlgorithm.SHA512_WITH_RSA))
            assertTrue(engine.verify(second, first, key.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA))
            assertArrayEquals(original, first.copyOf(original.size))
            assertArrayEquals(first, second.copyOf(first.size))
            assertEquals(0, key.encodedReads.get())
        } finally { original.fill(0); first.fill(0); second.fill(0) }
    }

    @Test fun allXadesPackagingsUseExactSha384UrisAndRejectWrongDigest() {
        val key = nonExportableSyntheticIdentity(); val engine = NativeXadesEngine(clock = generalClockFor(key.identity))
        val payload = "<sample><value>synthetic</value></sample>".toByteArray()
        for (packaging in NativeXadesPackaging.entries) {
            val options = NativeXadesOptions(packaging, "application/xml", null)
            val signed = signature(engine.sign(payload, key.identity, algorithm, options))
            try {
                assertTrue(engine.verify(signed, payload, key.identity.certificate, algorithm, options))
                assertFalse(engine.verify(signed, payload, key.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, options))
                val doc = NativeSigningXml.parse(signed)
                val info = doc.getElementsByTagNameNS(NativeSigningXml.DS, "SignedInfo").item(0) as Element
                val method = info.getElementsByTagNameNS(NativeSigningXml.DS, "SignatureMethod").item(0) as Element
                assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha384", method.getAttribute("Algorithm"))
                val digests = info.getElementsByTagNameNS(NativeSigningXml.DS, "DigestMethod")
                assertEquals(3, digests.length)
                for (index in 0 until digests.length) {
                    assertEquals("http://www.w3.org/2001/04/xmldsig-more#sha384", (digests.item(index) as Element).getAttribute("Algorithm"))
                }
                val modified = payload.copyOf().also { it[18] = (it[18].toInt() xor 1).toByte() }
                assertFalse(engine.verify(signed, modified, key.identity.certificate, algorithm, options))
                modified.fill(0)
            } finally { signed.fill(0) }
        }
        payload.fill(0); assertEquals(0, key.encodedReads.get())
    }

    private fun signature(result: LocalSignatureResult): ByteArray {
        assertTrue("A real signature is required, not an admission-only result", result is LocalSignatureResult.Success)
        return (result as LocalSignatureResult.Success).signature.use { it.withBytes { bytes -> bytes.copyOf() } }
    }
}
