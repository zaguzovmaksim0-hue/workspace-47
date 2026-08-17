package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.ByteArrayInputStream
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
import javax.xml.transform.OutputKeys
import kotlinx.coroutines.test.runTest
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class CdtiXadesEnvelopingAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = CdtiXadesEnvelopingAdapter(clock)

    @Test
    fun createsSha512XadesEnvelopingForExactDynamicCdtiChallenge() = runTest {
        val request = request(CHALLENGE)
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedInfo ->
            JcaLocalSignatureEngine().sign(signedInfo, identity, SigningAlgorithm.SHA512_WITH_RSA)
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }

        verifyEnvelope(result, identity.certificate)

        result.fill(0)
        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
    }

    @Test
    fun acceptsNoncanonicalBase64TailUsedByLiveCdtiChallenge() {
        val decoded = Base64.getDecoder().decode(padded(CHALLENGE))
        assertTrue(CdtiXadesEnvelopingAdapter.isExactChallenge(decoded))
        assertFalse(
            Base64.getEncoder().withoutPadding().encodeToString(decoded) == CHALLENGE,
        )
    }

    @Test
    fun rejectsDecodedPayloadThatCannotOriginateFromExactCdtiTokenShape() {
        val arbitrary = ByteArray(47) { 0xff.toByte() }
        assertFalse(CdtiXadesEnvelopingAdapter.isExactChallenge(arbitrary))
    }

    @Test
    fun rejectsMalformedChallengeAndWrongProperties() = runTest {
        listOf(
            request("CertExp${"a".repeat(31)}${"b".repeat(24)}"),
            request("CertExp${"A".repeat(32)}${"b".repeat(24)}"),
            request("WrongAA${"a".repeat(32)}${"b".repeat(24)}"),
            request(CHALLENGE, "filters=expired"),
        ).forEach { request ->
            val result = adapter.prepare(request, identity.chain)
            assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL), result)
            request.close()
        }
    }

    private fun request(
        challenge: String,
        properties: String = "filters=nonexpired",
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174400"),
        protocolId = SigningProtocolId("cdti-xades-enveloping-v1"),
        context = SigningContext(
            profileId = "cdti-certificate-validation",
            profileVersion = 1,
            origin = TrustedOrigin("https", "sede.cdti.gob.es", 443),
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174401"),
            navigationEpoch = 61,
            observedAt = clock.instant(),
            pageUrl = CDTI_PAGE,
        ),
        algorithm = SigningAlgorithm.SHA512_WITH_RSA,
        format = SigningFormat.XADES,
        safeDescription = "Validación de certificado digital en CDTI",
        payload = MiniAppletPayloadCodec.encode(Base64.getDecoder().decode(padded(challenge)), properties),
    )


    private fun padded(value: String): String =
        value + "=".repeat((4 - value.length % 4) % 4)

    private fun verifyEnvelope(result: ByteArray, expectedCertificate: X509Certificate) {
        Init.init()
        val document = parse(result)
        val signature = document.documentElement
        val signedInfo = signature.singleChild(DS_NS, "SignedInfo")
        assertEquals(
            RSA_SHA512,
            signedInfo.singleChild(DS_NS, "SignatureMethod").getAttribute("Algorithm"),
        )
        val dataReference = signedInfo.directChildren(DS_NS, "Reference")
            .single { it.getAttribute("Type") == OBJECT_TYPE }
        val dataObject = document.findById(dataReference.fragmentId())
        assertArrayEquals(Base64.getDecoder().decode(padded(CHALLENGE)), Base64.getMimeDecoder().decode(dataObject.textContent.trim()))

        val certificateBytes = Base64.getMimeDecoder().decode(
            signature.getElementsByTagNameNS(DS_NS, "X509Certificate").item(0).textContent,
        )
        assertArrayEquals(expectedCertificate.encoded, certificateBytes)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
        val signatureValue = Base64.getMimeDecoder().decode(
            signature.singleChild(DS_NS, "SignatureValue").textContent,
        )
        assertTrue(Signature.getInstance("SHA512withRSA").run {
            initVerify(certificate.publicKey)
            update(canonicalize(signedInfo))
            verify(signatureValue)
        })
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

    private fun Element.singleChild(namespace: String, name: String): Element =
        directChildren(namespace, name).single()

    private fun Element.directChildren(namespace: String, name: String): List<Element> =
        childNodes.asElements().filter { it.namespaceURI == namespace && it.localName == name }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun Element.fragmentId(): String = getAttribute("URI").also {
        require(it.startsWith("#") && it.length > 1)
    }.substring(1)

    private fun Document.findById(id: String): Element =
        documentElement.walkElements().single { it.getAttribute("Id") == id }

    private fun Element.walkElements(): Sequence<Element> = sequence {
        yield(this@walkElements)
        childNodes.asElements().forEach { yieldAll(it.walkElements()) }
    }

    private companion object {
        const val CHALLENGE = "CertExp4780e003d3164fc3ac515844b4eb30f8s15utgmbrvl0kdvkk01oaz55"
        const val CDTI_PAGE =
            "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"
        const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
        const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
        const val RSA_SHA512 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"
        const val OBJECT_TYPE = "http://www.w3.org/2000/09/xmldsig#Object"
    }
}
