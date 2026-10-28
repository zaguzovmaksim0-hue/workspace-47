package dev.junta.firmamobile.signing

import dev.junta.firmamobile.afirma.servlet.NativeSigningXml
import dev.junta.firmamobile.afirma.servlet.NativeXadesPackaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class XsigProfileClassifierTest {
    @Test
    fun classifiesEnvelopingXsigFromEmbeddedObject() {
        assertEquals(
            NativeXadesPackaging.ENVELOPING,
            XsigProfileClassifier.classify(signed(NativeXadesPackaging.ENVELOPING).bytes)?.packaging,
        )
    }

    @Test
    fun classifiesInternalDetachedXsigFromAfirmaContent() {
        assertEquals(
            NativeXadesPackaging.DETACHED,
            XsigProfileClassifier.classify(signed(NativeXadesPackaging.DETACHED).bytes)?.packaging,
        )
    }

    @Test
    fun classifiesEnvelopedXsigFromEmptyReferenceAndEnvelopedTransform() {
        assertEquals(
            NativeXadesPackaging.ENVELOPED,
            XsigProfileClassifier.classify(signed(NativeXadesPackaging.ENVELOPED).bytes)?.packaging,
        )
    }

    @Test
    fun classifiesExternallyDetachedXsigFromSha256Urn() {
        assertEquals(
            NativeXadesPackaging.EXTERNALLY_DETACHED,
            XsigProfileClassifier.classify(signed(NativeXadesPackaging.EXTERNALLY_DETACHED).bytes)?.packaging,
        )
    }

    @Test
    fun rejectsMultipleSignatures() {
        assertNull(classifyAfterMutation(signed(NativeXadesPackaging.ENVELOPING)) { document ->
            document.documentElement.appendChild(
                document.createElementNS(NativeSigningXml.DS, "ds:Signature"),
            )
        })
    }

    @Test
    fun rejectsUnexpectedReferenceCount() {
        assertNull(classifyAfterMutation(signed(NativeXadesPackaging.ENVELOPING)) { document ->
            val references = document.getElementsByTagNameNS(NativeSigningXml.DS, "Reference")
            val reference = references.item(references.length - 1)
            reference.parentNode.removeChild(reference)
        })
    }

    @Test
    fun rejectsExternalHttpUriWithoutNetworkAccess() {
        assertNull(classifyAfterMutation(signed(NativeXadesPackaging.ENVELOPING)) { document ->
            reference(document).setAttribute("URI", "http://example.invalid/data")
        })
    }

    @Test
    fun rejectsUnknownTransform() {
        assertNull(classifyAfterMutation(signed(NativeXadesPackaging.ENVELOPING)) { document ->
            val transform = reference(document)
                .getElementsByTagNameNS(NativeSigningXml.DS, "Transform")
                .item(0) as Element
            transform.setAttribute("Algorithm", "http://example.invalid/transform")
        })
    }

    @Test
    fun rejectsDuplicateIds() {
        assertNull(classifyAfterMutation(signed(NativeXadesPackaging.ENVELOPING)) { document ->
            val signature = document.getElementsByTagNameNS(NativeSigningXml.DS, "Signature").item(0) as Element
            val duplicate = document.createElementNS(NativeSigningXml.DS, "ds:Object")
            duplicate.setAttribute("Id", signature.getAttribute("Id"))
            signature.appendChild(duplicate)
        })
    }

    @Test
    fun rejectsDoctypeAndEntityInput() {
        assertNull(
            XsigProfileClassifier.classify(
                "<!DOCTYPE r [<!ENTITY x SYSTEM 'file:///denied'>]><r>&x;</r>".toByteArray(),
            ),
        )
    }

    @Test
    fun rejectsOversizedXml() {
        assertNull(XsigProfileClassifier.classify(ByteArray(2_097_153) { '<'.code.toByte() }))
    }

    @Test
    fun extractsEmbeddedCertificateAndSignatureAlgorithm() {
        val fixture = signed(NativeXadesPackaging.ENVELOPING)
        val profile = XsigProfileClassifier.classify(fixture.bytes)
        assertNotNull(profile)
        assertEquals(fixture.identity.certificate, profile?.certificate)
        assertEquals(fixture.algorithm, profile?.algorithm)
        assertEquals(fixture.options.mimeType, profile?.options?.mimeType)
        assertEquals(fixture.options.contentDescription, profile?.options?.contentDescription)
    }

    private fun signed(packaging: NativeXadesPackaging): SyntheticXsig {
        val enveloped = packaging == NativeXadesPackaging.ENVELOPED
        return XsigSyntheticFixtures.sign(
            packaging = packaging,
            payload = if (enveloped) {
                "<root xmlns='urn:test'><value>synthetic</value></root>".toByteArray()
            } else {
                "synthetic signed content".toByteArray()
            },
            mimeType = if (enveloped) "application/xml" else "application/octet-stream",
            contentDescription = "Synthetic document",
        )
    }

    private fun reference(document: Document) =
        document.getElementsByTagNameNS(NativeSigningXml.DS, "Reference").item(0) as Element

    private fun classifyAfterMutation(fixture: SyntheticXsig, mutate: (Document) -> Unit): XsigProfile? {
        val document = NativeSigningXml.parse(fixture.bytes)
        mutate(document)
        return XsigProfileClassifier.classify(NativeSigningXml.serialize(document))
    }
}
