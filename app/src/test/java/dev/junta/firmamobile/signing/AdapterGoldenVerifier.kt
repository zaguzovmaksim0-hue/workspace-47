package dev.junta.firmamobile.signing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/** Independent semantic goldens, not production codec.validate or random certificate DER snapshots. */
internal object AdapterGoldenVerifier {
    private const val DS = "http://www.w3.org/2000/09/xmldsig#"
    private const val XADES = "http://uri.etsi.org/01903/v1.3.2#"
    private const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
    private const val OBJECT = "http://www.w3.org/2000/09/xmldsig#Object"
    private const val PROPERTIES = "http://uri.etsi.org/01903#SignedProperties"
    private const val SHA512 = "http://www.w3.org/2001/04/xmlenc#sha512"
    private const val BASE64 = "http://www.w3.org/2000/09/xmldsig#base64"

    fun verify(case: AdapterGoldenCase, bytes: ByteArray, certificate: X509Certificate) {
        when (case.format) {
            SigningFormat.CADES -> verifyCms(case, bytes, certificate)
            SigningFormat.XADES -> verifyXml(case, bytes, certificate)
            else -> error("Not a golden adapter family")
        }
    }

    private fun verifyCms(case: AdapterGoldenCase, bytes: ByteArray, certificate: X509Certificate) {
        val cms = CMSSignedData(CMSProcessableByteArray(case.data), bytes)
        assertTrue(cms.isDetachedSignature)
        assertNull(CMSSignedData(bytes).signedContent)
        val signer = cms.signerInfos.signers.single()
        val (digestName, digestOid, rsaOid) = when (case.algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> Triple("SHA-1", "1.3.14.3.2.26", "1.2.840.113549.1.1.5")
            SigningAlgorithm.SHA256_WITH_RSA -> Triple("SHA-256", "2.16.840.1.101.3.4.2.1", "1.2.840.113549.1.1.11")
            SigningAlgorithm.SHA512_WITH_RSA -> Triple("SHA-512", "2.16.840.1.101.3.4.2.3", "1.2.840.113549.1.1.13")
            else -> error("Unexpected golden algorithm")
        }
        assertEquals(digestOid, signer.digestAlgOID)
        assertTrue(signer.encryptionAlgOID in setOf("1.2.840.113549.1.1.1", rsaOid))
        val attributes = signer.signedAttributes
        assertEquals(PKCSObjectIdentifiers.data, attributes[CMSAttributes.contentType].attrValues.getObjectAt(0))
        assertArrayEquals(
            digest(digestName, case.data),
            ASN1OctetString.getInstance(attributes[CMSAttributes.messageDigest].attrValues.getObjectAt(0)).octets,
        )
        assertEquals(
            goldenInstant,
            Time.getInstance(attributes[CMSAttributes.signingTime].attrValues.getObjectAt(0)).date.toInstant(),
        )
        assertNull(attributes[PKCSObjectIdentifiers.id_aa_ets_sigPolicyId])
        val signingCertificate = SigningCertificateV2.getInstance(
            attributes[PKCSObjectIdentifiers.id_aa_signingCertificateV2].attrValues.getObjectAt(0),
        )
        assertArrayEquals(digest("SHA-256", certificate.encoded), signingCertificate.certs.single().certHash)
        val holder = cms.certificates.getMatches(null).single()
        assertTrue(signer.sid.match(holder))
        assertArrayEquals(certificate.encoded, holder.encoded)
        assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(BundledBouncyCastle.provider).build(holder)))
        assertTrue(verifyRaw(case.algorithm, certificate, signer.encodedSignedAttributes, signer.signature))

        // Prove the verifier binds detached content, not merely the signed-attributes RSA value.
        val altered = case.data.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        try {
            val tampered = CMSSignedData(CMSProcessableByteArray(altered), bytes).signerInfos.signers.single()
            assertFalse(runCatching {
                tampered.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(BundledBouncyCastle.provider).build(holder))
            }.getOrDefault(false))
        } finally {
            altered.fill(0)
        }
    }

    private fun verifyXml(case: AdapterGoldenCase, bytes: ByteArray, certificate: X509Certificate) {
        Init.init()
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val root = document.documentElement
        assertEquals(DS, root.namespaceURI)
        assertEquals("Signature", root.localName)
        assertEquals(1, document.getElementsByTagNameNS(DS, "Signature").length)
        assertEquals(2, root.children(DS, "Object").size)
        val elements = root.walk().toList()
        val ids = elements.filter { it.hasAttribute("Id") }.map { it.getAttribute("Id") }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none(String::isEmpty))
        val signedInfo = root.child(DS, "SignedInfo")
        assertEquals(C14N, signedInfo.child(DS, "CanonicalizationMethod").getAttribute("Algorithm"))
        val expectedMethod = when (case.algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
            SigningAlgorithm.SHA512_WITH_RSA -> "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"
            else -> error("Unexpected XML algorithm")
        }
        assertEquals(expectedMethod, signedInfo.child(DS, "SignatureMethod").getAttribute("Algorithm"))
        val references = signedInfo.children(DS, "Reference")
        assertEquals(listOf(OBJECT, PROPERTIES, ""), references.map { it.getAttribute("Type") })
        assertEquals(3, references.map { it.getAttribute("URI") }.toSet().size)
        references.forEachIndexed { index, reference ->
            val uri = reference.getAttribute("URI")
            assertTrue(uri.startsWith("#") && uri.length > 1)
            val target = elements.single { it.getAttribute("Id") == uri.drop(1) }
            assertEquals(SHA512, reference.child(DS, "DigestMethod").getAttribute("Algorithm"))
            val content = if (index == 0) {
                assertEquals(DS, target.namespaceURI)
                assertEquals("Object", target.localName)
                assertEquals(BASE64, target.getAttribute("Encoding"))
                assertEquals(
                    BASE64,
                    reference.child(DS, "Transforms").child(DS, "Transform").getAttribute("Algorithm"),
                )
                decode(target.textContent).also { assertArrayEquals(case.data, it) }
            } else {
                assertEquals(0, reference.getElementsByTagNameNS(DS, "Transform").length)
                assertEquals(if (index == 1) XADES else DS, target.namespaceURI)
                assertEquals(if (index == 1) "SignedProperties" else "KeyInfo", target.localName)
                canonicalize(target)
            }
            assertArrayEquals(digest("SHA-512", content), decode(reference.child(DS, "DigestValue").textContent))
        }
        val keyInfo = root.child(DS, "KeyInfo")
        val certificates = keyInfo.getElementsByTagNameNS(DS, "X509Certificate")
        assertEquals(1, certificates.length)
        assertArrayEquals(certificate.encoded, decode(certificates.item(0).textContent))
        val qualifying = root.getElementsByTagNameNS(XADES, "QualifyingProperties").item(0) as Element
        assertEquals("#${root.getAttribute("Id")}", qualifying.getAttribute("Target"))
        assertEquals(goldenInstant.toString(), root.getElementsByTagNameNS(XADES, "SigningTime").item(0).textContent)
        val certDigest = root.getElementsByTagNameNS(XADES, "CertDigest").item(0) as Element
        assertEquals(SHA512, certDigest.child(DS, "DigestMethod").getAttribute("Algorithm"))
        assertArrayEquals(digest("SHA-512", certificate.encoded), decode(certDigest.child(DS, "DigestValue").textContent))
        val signature = decode(root.child(DS, "SignatureValue").textContent)
        val canonicalInfo = canonicalize(signedInfo)
        assertTrue(verifyRaw(case.algorithm, certificate, canonicalInfo, signature))
        signature[0] = (signature[0].toInt() xor 1).toByte()
        assertFalse(verifyRaw(case.algorithm, certificate, canonicalInfo, signature))
    }

    private fun verifyRaw(algorithm: SigningAlgorithm, certificate: X509Certificate, input: ByteArray, value: ByteArray) =
        Signature.getInstance(when (algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
            SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
            SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
            else -> error("Unexpected golden algorithm")
        }).run { initVerify(certificate.publicKey); update(input); verify(value) }

    private fun canonicalize(node: Node): ByteArray = ByteArrayOutputStream().let { output ->
        Canonicalizer.getInstance(C14N).canonicalizeSubtree(node, output)
        output.toByteArray()
    }

    private fun digest(name: String, bytes: ByteArray): ByteArray = MessageDigest.getInstance(name).digest(bytes)
    private fun decode(value: String): ByteArray = Base64.getMimeDecoder().decode(value)
    private fun NodeList.elements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }
    private fun Element.children(ns: String, name: String) = childNodes.elements().filter {
        it.namespaceURI == ns && it.localName == name
    }
    private fun Element.child(ns: String, name: String) = children(ns, name).single()
    private fun Element.walk(): Sequence<Element> = sequence {
        yield(this@walk)
        childNodes.elements().forEach { yieldAll(it.walk()) }
    }
}
