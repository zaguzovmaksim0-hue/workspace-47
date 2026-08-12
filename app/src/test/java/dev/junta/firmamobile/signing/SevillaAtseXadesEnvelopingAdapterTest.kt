package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.runTest
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class SevillaAtseXadesEnvelopingAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = SevillaAtseXadesEnvelopingAdapter(clock)

    @Test
    fun createsAutoFirmaCompatibleSha1XadesEnvelopingForDynamicChallenge() = runTest {
        val request = request()
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedInfo ->
            JcaLocalSignatureEngine().sign(
                signedInfo,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }

        verifyAutoFirmaEnvelope(result, identity.certificate)

        result.fill(0)
        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
    }

    private fun request() = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174400"),
        protocolId = SigningProtocolId("sevilla-atse-xades-enveloping-v1"),
        context = SigningContext(
            profileId = "sevilla-atse-certificate-login",
            profileVersion = 1,
            origin = TrustedOrigin("https", "www.sevilla.org", 443),
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174401"),
            navigationEpoch = 48,
            observedAt = clock.instant(),
        ),
        algorithm = SigningAlgorithm.SHA1_WITH_RSA,
        format = SigningFormat.XADES,
        safeDescription = "Acceso con certificado a la Agencia Tributaria de Sevilla",
        payload = MiniAppletPayloadCodec.encode(CHALLENGE.copyOf(), ""),
    )

    private fun verifyAutoFirmaEnvelope(result: ByteArray, expectedCertificate: X509Certificate) {
        Init.init()
        val document = parse(result)
        val signature = document.documentElement
        assertEquals(DS_NS, signature.namespaceURI)
        assertEquals("Signature", signature.localName)

        val signedInfo = signature.singleChild(DS_NS, "SignedInfo")
        assertEquals(
            RSA_SHA1,
            signedInfo.singleChild(DS_NS, "SignatureMethod").getAttribute("Algorithm"),
        )
        assertEquals(
            C14N,
            signedInfo.singleChild(DS_NS, "CanonicalizationMethod").getAttribute("Algorithm"),
        )

        val references = signedInfo.directChildren(DS_NS, "Reference")
        assertEquals(3, references.size)

        val dataReference = references.single { it.getAttribute("Type") == OBJECT_TYPE }
        assertEquals(SHA512_URI, dataReference.digestMethod())
        assertTrue(
            dataReference.getElementsByTagNameNS(DS_NS, "Transform").asElements()
                .single().getAttribute("Algorithm") == BASE64_TRANSFORM,
        )
        val dataObject = document.findById(dataReference.fragmentId())
        assertEquals(DS_NS, dataObject.namespaceURI)
        assertEquals("Object", dataObject.localName)
        assertEquals(BASE64_ENCODING, dataObject.getAttribute("Encoding"))
        assertArrayEquals(CHALLENGE, Base64.getMimeDecoder().decode(dataObject.textContent.trim()))
        assertArrayEquals(
            sha512(CHALLENGE),
            Base64.getMimeDecoder().decode(dataReference.digestValue()),
        )

        val signedPropertiesReference = references.single {
            it.getAttribute("Type") == SIGNED_PROPERTIES_TYPE
        }
        assertEquals(SHA512_URI, signedPropertiesReference.digestMethod())
        val signedProperties = document.findById(signedPropertiesReference.fragmentId())
        assertEquals(XADES_NS, signedProperties.namespaceURI)
        assertEquals("SignedProperties", signedProperties.localName)
        assertArrayEquals(
            sha512(canonicalize(signedProperties)),
            Base64.getMimeDecoder().decode(signedPropertiesReference.digestValue()),
        )

        val keyInfoReference = references.single {
            val target = document.findById(it.fragmentId())
            target.namespaceURI == DS_NS && target.localName == "KeyInfo"
        }
        assertEquals(SHA512_URI, keyInfoReference.digestMethod())
        val keyInfo = document.findById(keyInfoReference.fragmentId())
        assertArrayEquals(
            sha512(canonicalize(keyInfo)),
            Base64.getMimeDecoder().decode(keyInfoReference.digestValue()),
        )

        val certificateBytes = Base64.getMimeDecoder().decode(
            keyInfo.getElementsByTagNameNS(DS_NS, "X509Certificate").item(0).textContent,
        )
        assertArrayEquals(expectedCertificate.encoded, certificateBytes)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
        val signatureValue = Base64.getMimeDecoder().decode(
            signature.singleChild(DS_NS, "SignatureValue").textContent,
        )
        assertTrue(Signature.getInstance("SHA1withRSA").run {
            initVerify(certificate.publicKey)
            update(canonicalize(signedInfo))
            verify(signatureValue)
        })

        val qualifyingProperties = signature.getElementsByTagNameNS(XADES_NS, "QualifyingProperties")
            .asElements().single()
        assertEquals("#${signature.getAttribute("Id")}", qualifyingProperties.getAttribute("Target"))
        assertEquals(
            clock.instant().toString(),
            signature.getElementsByTagNameNS(XADES_NS, "SigningTime").item(0).textContent,
        )
    }

    private fun parse(bytes: ByteArray): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun canonicalize(node: Node): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        Canonicalizer.getInstance(C14N).canonicalizeSubtree(node, output)
        return output.toByteArray()
    }

    private fun sha512(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(bytes)

    private fun Element.digestMethod(): String =
        singleChild(DS_NS, "DigestMethod").getAttribute("Algorithm")

    private fun Element.digestValue(): String = singleChild(DS_NS, "DigestValue").textContent

    private fun Element.fragmentId(): String = getAttribute("URI").also {
        require(it.startsWith("#") && it.length > 1)
    }.substring(1)

    private fun Element.singleChild(namespace: String, name: String): Element =
        directChildren(namespace, name).single()

    private fun Element.directChildren(namespace: String, name: String): List<Element> =
        childNodes.asElements().filter { it.namespaceURI == namespace && it.localName == name }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun Document.findById(id: String): Element =
        documentElement.walkElements().single { it.getAttribute("Id") == id }

    private fun Element.walkElements(): Sequence<Element> = sequence {
        yield(this@walkElements)
        childNodes.asElements().forEach { yieldAll(it.walkElements()) }
    }

    private companion object {
        val CHALLENGE = "0123456789abcdef0123456789abcdefABCDEFGH".encodeToByteArray()
        const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
        const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
        const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
        const val RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
        const val SHA512_URI = "http://www.w3.org/2001/04/xmlenc#sha512"
        const val OBJECT_TYPE = "http://www.w3.org/2000/09/xmldsig#Object"
        const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
        const val BASE64_TRANSFORM = "http://www.w3.org/2000/09/xmldsig#base64"
        const val BASE64_ENCODING = "http://www.w3.org/2000/09/xmldsig#base64"
    }
}
