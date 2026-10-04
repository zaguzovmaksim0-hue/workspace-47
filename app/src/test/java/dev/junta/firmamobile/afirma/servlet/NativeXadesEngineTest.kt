package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import dev.junta.firmamobile.signing.freshSyntheticIdentity
import java.io.ByteArrayInputStream
import javax.xml.crypto.OctetStreamData
import javax.xml.crypto.URIDereferencer
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import org.w3c.dom.Element
import org.junit.Assert.*
import org.junit.Test

class NativeXadesEngineTest {
    @Test fun fourPackagesAndThreeAlgorithmsVerifyWithoutExportingTheKey() {
        val key = nonExportableSyntheticIdentity(); val engine = NativeXadesEngine(generalClockFor(key.identity))
        for (packaging in NativeXadesPackaging.entries) for (algorithm in SigningAlgorithm.entries) {
            val payload = payload(packaging); val options = NativeXadesOptions(packaging, "application/xml", "Synthetic document")
            val result = engine.sign(payload, key.identity, algorithm, options)
            assertTrue("$packaging/$algorithm", result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.use { signature -> signature.withBytes { xml ->
                assertTrue(engine.verify(xml, payload, key.identity.certificate, algorithm, options))
                // Independent JDK XMLDSig implementation validates every reference,
                // including SignedProperties. No arbitrary network dereferencing.
                val doc = NativeSigningXml.parse(xml)
                val elements = doc.getElementsByTagName("*")
                for (i in 0 until elements.length) (elements.item(i) as Element).let { if (it.hasAttribute("Id")) it.setIdAttribute("Id", true) }
                val factory = XMLSignatureFactory.getInstance("DOM")
                val ctx = DOMValidateContext(key.identity.certificate.publicKey, doc.getElementsByTagNameNS(NativeSigningXml.DS, "Signature").item(0))
                ctx.setProperty("org.jcp.xml.dsig.secureValidation", false)
                val fallback = factory.uriDereferencer
                ctx.uriDereferencer = URIDereferencer { ref, context ->
                    if (ref.uri.startsWith("urn:sha256:")) OctetStreamData(ByteArrayInputStream(payload))
                    else { require(ref.uri.isEmpty() || ref.uri.startsWith("#")); fallback.dereference(ref, context) }
                }
                assertTrue("Independent XML signature: $packaging/$algorithm", factory.unmarshalXMLSignature(ctx).validate(ctx))
            } }
        }
        assertEquals(0, key.encodedReads.get())
    }
    @Test fun differentInputCertificateAlgorithmAndSignatureAreRejected() {
        val id = freshSyntheticIdentity(); val other = freshSyntheticIdentity(); val engine = NativeXadesEngine(generalClockFor(id))
        val options = NativeXadesOptions(NativeXadesPackaging.ENVELOPING, "application/octet-stream", null)
        val bytes = "one document".toByteArray()
        (engine.sign(bytes, id, SigningAlgorithm.SHA256_WITH_RSA, options) as LocalSignatureResult.Success).signature.use { signature -> signature.withBytes { xml ->
            assertFalse(engine.verify(xml, "changed".toByteArray(), id.certificate, SigningAlgorithm.SHA256_WITH_RSA, options))
            assertFalse(engine.verify(xml, bytes, other.certificate, SigningAlgorithm.SHA256_WITH_RSA, options))
            assertFalse(engine.verify(xml, bytes, id.certificate, SigningAlgorithm.SHA512_WITH_RSA, options))
            val doc = NativeSigningXml.parse(xml); val value = doc.getElementsByTagNameNS(NativeSigningXml.DS, "SignatureValue").item(0)
            value.textContent = "AA=="
            assertFalse(engine.verify(NativeSigningXml.serialize(doc), bytes, id.certificate, SigningAlgorithm.SHA256_WITH_RSA, options))
        } }
    }
    @Test fun xmlEntitiesPreexistingSignaturesAndDuplicateIdsAreNotSigned() {
        val id = freshSyntheticIdentity(); val engine = NativeXadesEngine(generalClockFor(id))
        val opt = NativeXadesOptions(NativeXadesPackaging.ENVELOPED, "application/xml", null)
        for (xml in listOf("<!DOCTYPE r [<!ENTITY x SYSTEM 'file:///denied'>]><r>&x;</r>",
            "<r><Signature xmlns='http://www.w3.org/2000/09/xmldsig#'/></r>", "<r><a Id='same'/><b Id='same'/></r>")) {
            assertTrue(engine.sign(xml.toByteArray(), id, SigningAlgorithm.SHA256_WITH_RSA, opt) is LocalSignatureResult.Failure)
        }
    }
    @Test fun payloadBudgetIsAppliedBeforeSigning() {
        val id = nonExportableSyntheticIdentity(); val engine = NativeXadesEngine(generalClockFor(id.identity))
        val opt = NativeXadesOptions(NativeXadesPackaging.DETACHED, "application/octet-stream", null)
        assertTrue(engine.sign(byteArrayOf(), id.identity, SigningAlgorithm.SHA256_WITH_RSA, opt) is LocalSignatureResult.Failure)
        assertTrue(engine.sign(ByteArray(524_289), id.identity, SigningAlgorithm.SHA256_WITH_RSA, opt) is LocalSignatureResult.Failure)
        assertEquals(0, id.encodedReads.get())
    }
    private fun payload(p: NativeXadesPackaging) = if (p == NativeXadesPackaging.ENVELOPED)
        "<root xmlns='urn:test'><value>alpha</value></root>".toByteArray() else byteArrayOf(0,1,2,3,127,-1)
}
