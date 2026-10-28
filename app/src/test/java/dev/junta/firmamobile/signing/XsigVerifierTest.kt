package dev.junta.firmamobile.signing

import dev.junta.firmamobile.afirma.servlet.NativeSigningXml
import dev.junta.firmamobile.afirma.servlet.NativeXadesPackaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class XsigVerifierTest {
    @Test
    fun verifiesSyntheticEnvelopingXsig() {
        assertVerified(
            XsigVerifier.verify(signed(NativeXadesPackaging.ENVELOPING).bytes, null),
            NativeXadesPackaging.ENVELOPING,
        )
    }

    @Test
    fun verifiesSyntheticInternalDetachedXsig() {
        assertVerified(
            XsigVerifier.verify(signed(NativeXadesPackaging.DETACHED).bytes, null),
            NativeXadesPackaging.DETACHED,
        )
    }

    @Test
    fun verifiesSyntheticEnvelopedXsigWithOriginal() {
        val fixture = signed(NativeXadesPackaging.ENVELOPED)
        assertVerified(XsigVerifier.verify(fixture.bytes, fixture.payload), NativeXadesPackaging.ENVELOPED)
    }

    @Test
    fun verifiesSyntheticExternallyDetachedXsigWithOriginal() {
        val fixture = signed(NativeXadesPackaging.EXTERNALLY_DETACHED)
        assertVerified(XsigVerifier.verify(fixture.bytes, fixture.payload), NativeXadesPackaging.EXTERNALLY_DETACHED)
    }

    @Test
    fun requiresOriginalForEnvelopedXsig() {
        val fixture = signed(NativeXadesPackaging.ENVELOPED)
        val result = XsigVerifier.verify(fixture.bytes, null)
        assertEquals(XsigCryptographicStatus.ORIGINAL_REQUIRED, result.cryptographicStatus)
        assertEquals(NativeXadesPackaging.ENVELOPED, result.packaging)
    }

    @Test
    fun requiresOriginalForExternallyDetachedXsig() {
        val fixture = signed(NativeXadesPackaging.EXTERNALLY_DETACHED)
        val result = XsigVerifier.verify(fixture.bytes, null)
        assertEquals(XsigCryptographicStatus.ORIGINAL_REQUIRED, result.cryptographicStatus)
        assertEquals(NativeXadesPackaging.EXTERNALLY_DETACHED, result.packaging)
    }

    @Test
    fun rejectsTamperedPayload() {
        val result = verifyAfterMutation(NativeXadesPackaging.ENVELOPING) { document ->
            val target = document.getElementsByTagNameNS(NativeSigningXml.DS, "Object").item(0) as Element
            target.textContent = "dGFtcGVyZWQ="
        }
        assertEquals(XsigCryptographicStatus.INVALID, result.cryptographicStatus)
    }

    @Test
    fun rejectsTamperedSignatureValue() {
        val result = verifyAfterMutation(NativeXadesPackaging.ENVELOPING) { document ->
            val value = document.getElementsByTagNameNS(NativeSigningXml.DS, "SignatureValue").item(0) as Element
            value.textContent = "AA=="
        }
        assertEquals(XsigCryptographicStatus.INVALID, result.cryptographicStatus)
    }

    @Test
    fun rejectsWrongEmbeddedCertificate() {
        val other = freshSyntheticIdentity()
        val result = verifyAfterMutation(NativeXadesPackaging.ENVELOPING) { document ->
            val certificate = document.getElementsByTagNameNS(NativeSigningXml.DS, "X509Certificate").item(0) as Element
            certificate.textContent = java.util.Base64.getEncoder().encodeToString(other.certificate.encoded)
        }
        assertEquals(XsigCryptographicStatus.INVALID, result.cryptographicStatus)
    }

    @Test
    fun rejectsWrongSignatureAlgorithm() {
        val result = verifyAfterMutation(NativeXadesPackaging.ENVELOPING) { document ->
            val method = document.getElementsByTagNameNS(NativeSigningXml.DS, "SignatureMethod").item(0) as Element
            method.setAttribute("Algorithm", "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512")
        }
        assertEquals(XsigCryptographicStatus.INVALID, result.cryptographicStatus)
    }

    @Test
    fun rejectsWrongMimeTypeOrDescription() {
        val result = verifyAfterMutation(NativeXadesPackaging.ENVELOPING) { document ->
            val format = document.getElementsByTagNameNS(NativeSigningXml.XADES, "DataObjectFormat").item(0) as Element
            val mimeType = format.getElementsByTagNameNS(NativeSigningXml.XADES, "MimeType").item(0) as Element
            mimeType.textContent = "application/xml"
        }
        assertEquals(XsigCryptographicStatus.INVALID, result.cryptographicStatus)
    }

    @Test
    fun returnsUnsupportedForForeignXadesProfile() {
        val result = verifyAfterMutation(NativeXadesPackaging.ENVELOPING) { document ->
            reference(document).setAttribute("URI", "http://example.invalid/data")
        }
        assertEquals(XsigCryptographicStatus.UNSUPPORTED, result.cryptographicStatus)
        assertNull(result.packaging)
    }

    private fun signed(packaging: NativeXadesPackaging): SyntheticXsig {
        val enveloped = packaging == NativeXadesPackaging.ENVELOPED
        return XsigSyntheticFixtures.sign(
            packaging = packaging,
            payload = if (enveloped) {
                "<root xmlns='urn:test'><value>synthetic</value></root>".toByteArray()
            } else {
                "synthetic verification payload".toByteArray()
            },
            mimeType = if (enveloped) "application/xml" else "application/octet-stream",
            contentDescription = "Synthetic document",
        )
    }

    private fun reference(document: Document) =
        document.getElementsByTagNameNS(NativeSigningXml.DS, "Reference").item(0) as Element

    private fun verifyAfterMutation(
        packaging: NativeXadesPackaging,
        mutate: (Document) -> Unit,
    ): XsigVerificationResult {
        val fixture = signed(packaging)
        val document = NativeSigningXml.parse(fixture.bytes)
        mutate(document)
        return XsigVerifier.verify(NativeSigningXml.serialize(document), null)
    }

    private fun assertVerified(result: XsigVerificationResult, packaging: NativeXadesPackaging) {
        assertEquals(XsigCryptographicStatus.VERIFIED, result.cryptographicStatus)
        assertEquals(packaging, result.packaging)
        assertEquals(XsigCertificateTrust.NOT_EVALUATED, result.certificateTrust)
    }
}
