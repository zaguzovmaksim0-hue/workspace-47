package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.*
import java.security.PublicKey
import javax.xml.crypto.URIReferenceException
import javax.xml.crypto.URIDereferencer
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NativeXadesDocumentBoundaryTest {
    private companion object {
        const val DS = "http://www.w3.org/2000/09/xmldsig#"
        const val BOUNDARY_XML = "<?before v?><root xmlns=\"urn:boundary\">text</root><?after v?>"
    }

    @Test
    fun processingInstructionsSurviveAndSignatureValidatesIndependently() {
        withSignedXml(BOUNDARY_XML) { xml, key ->
            val document = NativeSigningXml.parse(xml)
            val nodes = topLevelNodes(document)
            assertEquals(listOf("before", "root", "after"), nodes.map { it.nodeName })
            assertEquals("v", nodes.first().textContent)
            assertEquals("v", nodes.last().textContent)
            assertTrue("Independent JSR105 validation failed", independentlyValidates(document, key))
        }
    }

    @Test
    fun namespacesWhitespaceTextAndCommentArePreserved() {
        val payload = "<root xmlns=\"urn:boundary\" xmlns:p=\"urn:prefixed\" xml:space=\"preserve\">" +
            "\n  alpha <p:item p:flag=\"synthetic\">  beta\t\n</p:item><!--synthetic comment--> gamma \n</root>"
        val expected = NativeSigningXml.parse(payload.toByteArray(Charsets.UTF_8))
        withSignedXml(payload) { xml, key ->
            val document = NativeSigningXml.parse(xml)
            assertTrue("Independent JSR105 validation failed", independentlyValidates(document, key))
            val payloadCopy = document.documentElement.cloneNode(true) as Element
            val signatures = payloadCopy.getElementsByTagNameNS(DS, "Signature")
            assertEquals("Expected one enveloped signature", 1, signatures.length)
            val signature = signatures.item(0)
            signature.parentNode.removeChild(signature)
            assertTrue(
                "Namespaces, xml:space, whitespace, text and comments must remain unchanged",
                expected.documentElement.isEqualNode(payloadCopy)
            )
        }
    }

    @Test
    fun changingEitherDocumentLevelProcessingInstructionInvalidatesSignature() {
        withSignedXml(BOUNDARY_XML) { xml, key ->
            val original = NativeSigningXml.parse(xml)
            assertTrue("Baseline signature must validate", independentlyValidates(original, key))
            val originalSignature = original.getElementsByTagNameNS(DS, "Signature").item(0)
            for (target in listOf("before", "after")) {
                val tampered = NativeSigningXml.parse(xml)
                val pi = topLevelNodes(tampered).single {
                    it.nodeType == Node.PROCESSING_INSTRUCTION_NODE && it.nodeName == target
                }
                assertEquals("v", pi.textContent)
                pi.textContent = "changed"
                assertTrue(
                    "Tampering must leave the signature subtree intact",
                    originalSignature.isEqualNode(
                        tampered.getElementsByTagNameNS(DS, "Signature").item(0)
                    )
                )
                assertFalse("Changed $target PI must fail", independentlyValidates(tampered, key))
            }
        }
    }

    private fun withSignedXml(payload: String, assertion: (ByteArray, PublicKey) -> Unit) {
        val identity = freshSyntheticIdentity()
        val result = NativeXadesEngine(generalClockFor(identity)).sign(
            payload.toByteArray(Charsets.UTF_8), identity,
            SigningAlgorithm.SHA256_WITH_RSA,
            NativeXadesOptions(NativeXadesPackaging.ENVELOPED, "application/xml", null)
        )
        assertTrue("Native XAdES signing must succeed", result is LocalSignatureResult.Success)
        val success = result as LocalSignatureResult.Success
        success.signature.use { signature ->
            signature.withBytes { xml ->
                assertion(xml.copyOf(), identity.certificate.publicKey)
            }
        }
    }

    private fun topLevelNodes(document: Document): List<Node> =
        (0 until document.childNodes.length).map { document.childNodes.item(it) }
            .filter { it.nodeType != Node.TEXT_NODE }

    private fun independentlyValidates(document: Document, key: PublicKey): Boolean {
        val factory = XMLSignatureFactory.getInstance("DOM")
        val signatures = document.getElementsByTagNameNS(DS, "Signature")
        assertEquals("Expected exactly one ds:Signature", 1, signatures.length)
        val context = DOMValidateContext(key, signatures.item(0) as Element)
        context.setProperty("org.jcp.xml.dsig.secureValidation", true)
        val elements = document.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as Element
            if (element.hasAttribute("Id")) {
                element.setIdAttribute("Id", true)
                context.setIdAttributeNS(element, null, "Id")
            }
        }
        val delegate = factory.uriDereferencer
        context.uriDereferencer = URIDereferencer { reference, xmlContext ->
            val uri = reference.uri
            if (uri == null || (uri.isNotEmpty() && !uri.startsWith("#"))) {
                throw URIReferenceException("Only empty or fragment URIs are permitted")
            }
            delegate.dereference(reference, xmlContext)
        }
        return factory.unmarshalXMLSignature(context).validate(context)
    }
}
