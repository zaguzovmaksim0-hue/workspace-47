package dev.junta.firmamobile.signing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** QA-only local XAdES Enveloped adapter for the exact public Transportes procedure-login contract. */
class TransportesXadesEnvelopedAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matchesContract() || certificateChain.isEmpty()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val signingCertificate = certificateChain.first()
        if (signingCertificate.publicKey !is RSAPublicKey || !signingCertificate.isAllowedAuthenticationCertificate()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(isExactChallenge(data))
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    TransportesXadesEnvelopedCodec.createPreSign(data, signingCertificate)
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedInfo,
                    state = TransportesXadesPreSignState(
                        material.unsignedDocument,
                        material.signingCertificateFingerprint,
                    ),
                ),
            )
        } catch (_: Exception) {
            ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
    }

    override suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult {
        if (!request.matchesContract() || !request.matchesPayloadContract()) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? TransportesXadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    TransportesXadesEnvelopedCodec.complete(
                        unsignedDocument = ownedState.unsignedDocument(),
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                    )
                }
                ProtocolCompletionResult.Success(LocalSignature(result))
            } catch (_: Exception) {
                ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
            }
        }
    }

    private fun NormalizedSignRequest.matchesContract(): Boolean =
        protocolId == ID &&
            context.profileId == PROFILE_ID &&
            context.profileVersion == PROFILE_VERSION &&
            context.origin.serialized == INITIATOR_ORIGIN &&
            context.pageUrl == AUTH_PAGE_URL &&
            algorithm == SigningAlgorithm.SHA1_WITH_RSA &&
            format == SigningFormat.XADES &&
            safeDescription == SAFE_DESCRIPTION

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                isExactChallenge(data) && extraProperties == EXPECTED_EXTRA_PROPERTIES
            }
        }
    }.getOrDefault(false)

    private fun X509Certificate.isAllowedAuthenticationCertificate(): Boolean {
        val now = Date.from(clock.instant())
        if (now.before(notBefore) || now.after(notAfter)) return false
        val usage = keyUsage ?: return false
        return usage.isNotEmpty() && usage[0]
    }

    companion object {
        val ID = SigningProtocolId("transportes-qys-xades-enveloped-v1")
        const val PROFILE_ID = "transportes-qys-cert-login"
        const val PROFILE_VERSION = 1
        const val START_URL = "https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002"
        const val AUTH_PAGE_URL =
            "https://sede.transportes.gob.es/MFOM.genericprocedure.web/Autenticacion.aspx"
        const val INITIATOR_ORIGIN = "https://sede.transportes.gob.es"
        const val SAFE_DESCRIPTION =
            "Acceso con certificado a Quejas y Sugerencias del Ministerio de Transportes"
        const val EXPECTED_EXTRA_PROPERTIES =
            "format=XAdES Enveloped\n" +
                "includeOnlySigningCertificate=true\n" +
                "nodeToSign=tag1\n" +
                "applySystemDate=false\n" +
                "filters.1=keyusage.digitalsignature:true;nonexpired:\n" +
                "sticky=true\n"
        val EXPECTED_EXTRA_PROPERTIES_MAP = linkedMapOf(
            "format" to "XAdES Enveloped",
            "includeOnlySigningCertificate" to "true",
            "nodeToSign" to "tag1",
            "applySystemDate" to "false",
            "filters.1" to "keyusage.digitalsignature:true;nonexpired:",
            "sticky" to "true",
        )

        internal fun isExactChallenge(data: ByteArray): Boolean {
            if (data.size != CHALLENGE_BYTES) return false
            val text = runCatching { data.toString(Charsets.UTF_8) }.getOrNull() ?: return false
            val match = CHALLENGE_REGEX.matchEntire(text) ?: return false
            return isExactTimestamp(match.groupValues[1])
        }

        private val CHALLENGE_REGEX = Regex(
            """<\?xml version="1\.0" encoding="UTF-8"\?><tag1 Id="tag1"><tag1_timestamp>(\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2})</tag1_timestamp></tag1>""",
        )
        internal fun isExactTimestamp(value: String): Boolean = runCatching {
            LocalDateTime.parse(value, CHALLENGE_TIMESTAMP_FORMATTER)
            true
        }.getOrDefault(false)

        private val CHALLENGE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT)
        private const val CHALLENGE_BYTES = 113
    }
}

private class TransportesXadesPreSignState(
    unsignedDocument: ByteArray,
    signingCertificateFingerprint: ByteArray,
) : PreSignState {
    private var document = unsignedDocument
    private var fingerprint = signingCertificateFingerprint
    private var closed = false

    @Synchronized
    fun unsignedDocument(): ByteArray = check(!closed).let { document }

    @Synchronized
    fun signingCertificateFingerprint(): ByteArray = check(!closed).let { fingerprint }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        document.fill(0)
        fingerprint.fill(0)
        document = ByteArray(0)
        fingerprint = ByteArray(0)
    }
}

internal data class TransportesXadesPreSignMaterial(
    val unsignedDocument: ByteArray,
    val signedInfo: ByteArray,
    val signingCertificateFingerprint: ByteArray,
)

internal object TransportesXadesEnvelopedCodec {
    init {
        Init.init()
    }

    fun createPreSign(
        data: ByteArray,
        signingCertificate: X509Certificate,
    ): TransportesXadesPreSignMaterial {
        require(TransportesXadesEnvelopedAdapter.isExactChallenge(data))
        val rsaPublicKey = signingCertificate.publicKey as? RSAPublicKey
            ?: error("RSA certificate required")
        val output = parse(data)
        val root = output.documentElement
        require(root.localName == "tag1" && root.namespaceURI == null && root.getAttribute(ID) == "tag1")

        val dataDigest = digest(canonicalize(root))
        val signatureId = "Signature-${UUID.randomUUID()}"
        val signatureValue = output.ds("SignatureValue")
        val keyInfo = createKeyInfo(output, signingCertificate, rsaPublicKey)
        val signedPropertiesId = "$signatureId-SignedProperties"
        val signedProperties = createSignedProperties(output, signedPropertiesId, signingCertificate)
        val qualifyingProperties = output.xades("QualifyingProperties").apply {
            setAttribute("Target", "#$signatureId")
            appendChild(signedProperties)
        }
        val xadesObject = output.ds("Object").apply { appendChild(qualifyingProperties) }
        val signature = output.ds("Signature").apply {
            setAttribute(ID, signatureId)
            appendChild(signatureValue)
            appendChild(keyInfo)
            appendChild(xadesObject)
        }
        root.appendChild(signature)

        val signedInfo = createSignedInfo(
            document = output,
            dataDigest = dataDigest,
            signedPropertiesId = signedPropertiesId,
            signedPropertiesDigest = digest(canonicalize(signedProperties)),
        )
        signature.insertBefore(signedInfo, signatureValue)
        val unsigned = serialize(output)
        require(unsigned.size <= MAX_OUTPUT_BYTES)
        return TransportesXadesPreSignMaterial(
            unsignedDocument = unsigned,
            signedInfo = canonicalize(signedInfo),
            signingCertificateFingerprint = MessageDigest.getInstance(SHA_256)
                .digest(signingCertificate.encoded),
        )
    }

    fun complete(
        unsignedDocument: ByteArray,
        signingCertificateFingerprint: ByteArray,
        signatureValue: ByteArray,
    ): ByteArray {
        require(unsignedDocument.isNotEmpty() && unsignedDocument.size <= MAX_OUTPUT_BYTES)
        require(signatureValue.isNotEmpty() && signatureValue.size <= MAX_SIGNATURE_BYTES)
        require(signingCertificateFingerprint.size == SHA_256_BYTES)
        val document = parse(unsignedDocument)
        val signature = document.documentElement.directChildrenDs("Signature").single()
        val signatureValueElement = signature.singleChildDs("SignatureValue")
        require(signatureValueElement.textContent.isEmpty())
        signatureValueElement.textContent = b64(signatureValue)
        val result = serialize(document)
        require(validate(result, signingCertificateFingerprint))
        return result
    }

    fun validate(
        signatureDocument: ByteArray,
        expectedCertificateFingerprint: ByteArray? = null,
    ): Boolean = runCatching {
        require(signatureDocument.isNotEmpty() && signatureDocument.size <= MAX_OUTPUT_BYTES)
        val document = parse(signatureDocument)
        val root = document.documentElement
        require(root.localName == "tag1" && root.namespaceURI == null && root.getAttribute(ID) == "tag1")
        val signature = root.directChildrenDs("Signature").single()
        val signedInfo = signature.singleChildDs("SignedInfo")
        require(signedInfo.singleChildDs("CanonicalizationMethod").getAttribute("Algorithm") == C14N)
        require(signedInfo.singleChildDs("SignatureMethod").getAttribute("Algorithm") == RSA_SHA1)
        val references = signedInfo.directChildrenDs("Reference")
        require(references.size == 2)

        val dataReference = references.single { it.getAttribute("URI") == "#tag1" }
        require(dataReference.digestMethod() == SHA1_URI)
        val transforms = dataReference.singleChildDs("Transforms").directChildrenDs("Transform")
            .map { it.getAttribute("Algorithm") }
        require(transforms == listOf(ENVELOPED_TRANSFORM, C14N))
        val cleanDocument = parse(signatureDocument)
        cleanDocument.documentElement.directChildrenDs("Signature").single().let {
            it.parentNode.removeChild(it)
        }
        require(
            MessageDigest.isEqual(
                digest(canonicalize(cleanDocument.documentElement)),
                Base64.getDecoder().decode(dataReference.digestValue()),
            ),
        )
        require(isExactChallengeDocument(cleanDocument))

        val signedPropertiesReference = references.single {
            it.getAttribute("Type") == SIGNED_PROPERTIES_TYPE
        }
        require(signedPropertiesReference.digestMethod() == SHA1_URI)
        val signedProperties = document.findById(signedPropertiesReference.fragmentId())
        require(signedProperties.namespaceURI == XADES_NS && signedProperties.localName == "SignedProperties")
        require(
            MessageDigest.isEqual(
                digest(canonicalize(signedProperties)),
                Base64.getDecoder().decode(signedPropertiesReference.digestValue()),
            ),
        )

        val keyInfo = signature.singleChildDs("KeyInfo")
        val certificates = keyInfo.getElementsByTagNameNS(DS_NS, "X509Certificate")
        require(certificates.length == 1)
        val certificateBytes = Base64.getDecoder().decode(certificates.item(0).textContent)
        try {
            expectedCertificateFingerprint?.let { expected ->
                require(
                    MessageDigest.isEqual(
                        MessageDigest.getInstance(SHA_256).digest(certificateBytes),
                        expected,
                    ),
                )
            }
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
            val signatureBytes = Base64.getDecoder().decode(signature.singleChildDs("SignatureValue").textContent)
            try {
                require(signatureBytes.isNotEmpty() && signatureBytes.size <= MAX_SIGNATURE_BYTES)
                require(Signature.getInstance("SHA1withRSA").run {
                    initVerify(certificate.publicKey)
                    update(canonicalize(signedInfo))
                    verify(signatureBytes)
                })
            } finally {
                signatureBytes.fill(0)
            }
        } finally {
            certificateBytes.fill(0)
        }
        require(
            signature.getElementsByTagNameNS(XADES_NS, "QualifyingProperties")
                .asElements().single().getAttribute("Target") == "#${signature.getAttribute(ID)}",
        )
        true
    }.getOrDefault(false)

    private fun isExactChallengeDocument(document: Document): Boolean {
        val root = document.documentElement
        if (root.localName != "tag1" || root.namespaceURI != null || root.getAttribute(ID) != "tag1") {
            return false
        }
        if (root.attributes.length != 1 || root.childNodes.length != 1) return false
        val timestamp = root.childNodes.item(0) as? Element ?: return false
        return timestamp.localName == "tag1_timestamp" &&
            timestamp.namespaceURI == null &&
            timestamp.attributes.length == 0 &&
            timestamp.childNodes.length == 1 &&
            TransportesXadesEnvelopedAdapter.isExactTimestamp(timestamp.textContent)
    }

    private fun createSignedInfo(
        document: Document,
        dataDigest: ByteArray,
        signedPropertiesId: String,
        signedPropertiesDigest: ByteArray,
    ): Element = document.ds("SignedInfo").apply {
        appendChild(document.ds("CanonicalizationMethod").apply { setAttribute("Algorithm", C14N) })
        appendChild(document.ds("SignatureMethod").apply { setAttribute("Algorithm", RSA_SHA1) })
        appendChild(document.ds("Reference").apply {
            setAttribute("URI", "#tag1")
            appendChild(document.ds("Transforms").apply {
                appendChild(document.ds("Transform").apply { setAttribute("Algorithm", ENVELOPED_TRANSFORM) })
                appendChild(document.ds("Transform").apply { setAttribute("Algorithm", C14N) })
            })
            appendDigest(document, dataDigest)
        })
        appendChild(document.ds("Reference").apply {
            setAttribute("URI", "#$signedPropertiesId")
            setAttribute("Type", SIGNED_PROPERTIES_TYPE)
            appendDigest(document, signedPropertiesDigest)
        })
    }

    private fun Element.appendDigest(document: Document, value: ByteArray) {
        appendChild(document.ds("DigestMethod").apply { setAttribute("Algorithm", SHA1_URI) })
        appendChild(document.ds("DigestValue").apply { textContent = b64(value) })
    }

    private fun createKeyInfo(
        document: Document,
        certificate: X509Certificate,
        rsaPublicKey: RSAPublicKey,
    ): Element = document.ds("KeyInfo").apply {
        appendChild(document.ds("KeyValue").apply {
            appendChild(document.ds("RSAKeyValue").apply {
                appendChild(document.ds("Modulus").apply { textContent = b64(rsaPublicKey.modulus.unsigned()) })
                appendChild(document.ds("Exponent").apply { textContent = b64(rsaPublicKey.publicExponent.unsigned()) })
            })
        })
        appendChild(document.ds("X509Data").apply {
            appendChild(document.ds("X509Certificate").apply { textContent = b64(certificate.encoded) })
        })
    }

    private fun createSignedProperties(
        document: Document,
        signedPropertiesId: String,
        certificate: X509Certificate,
    ): Element = document.xades("SignedProperties").apply {
        setAttribute(ID, signedPropertiesId)
        appendChild(document.xades("SignedSignatureProperties").apply {
            appendChild(document.xades("SigningCertificate").apply {
                appendChild(document.xades("Cert").apply {
                    appendChild(document.xades("CertDigest").apply {
                        appendChild(document.ds("DigestMethod").apply { setAttribute("Algorithm", SHA1_URI) })
                        appendChild(document.ds("DigestValue").apply { textContent = b64(digest(certificate.encoded)) })
                    })
                    appendChild(document.xades("IssuerSerial").apply {
                        appendChild(document.ds("X509IssuerName").apply {
                            textContent = certificate.issuerX500Principal.name
                        })
                        appendChild(document.ds("X509SerialNumber").apply {
                            textContent = certificate.serialNumber.toString()
                        })
                    })
                })
            })
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
        val output = ByteArrayOutputStream()
        Canonicalizer.getInstance(C14N).canonicalizeSubtree(node, output)
        return output.toByteArray()
    }

    private fun serialize(document: Document): ByteArray {
        val factory = TransformerFactory.newInstance().apply {
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_STYLESHEET, "") }
        }
        val output = ByteArrayOutputStream()
        factory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance(SHA_1).digest(bytes)
    private fun Document.ds(name: String): Element = createElementNS(DS_NS, "ds:$name")
    private fun Document.xades(name: String): Element = createElementNS(XADES_NS, "xades:$name")
    private fun Element.directChildrenDs(name: String): List<Element> =
        childNodes.asElements().filter { it.namespaceURI == DS_NS && it.localName == name }
    private fun Element.singleChildDs(name: String): Element = directChildrenDs(name).single()
    private fun Element.digestMethod(): String = singleChildDs("DigestMethod").getAttribute("Algorithm")
    private fun Element.digestValue(): String = singleChildDs("DigestValue").textContent
    private fun Element.fragmentId(): String = getAttribute("URI").also {
        require(it.startsWith("#") && it.length > 1)
    }.substring(1)
    private fun Document.findById(id: String): Element =
        documentElement.walkElements().single { it.getAttribute(ID) == id }
    private fun Element.walkElements(): Sequence<Element> = sequence {
        yield(this@walkElements)
        childNodes.asElements().forEach { yieldAll(it.walkElements()) }
    }
    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }
    private fun java.math.BigInteger.unsigned(): ByteArray = toByteArray().let { encoded ->
        if (encoded.size > 1 && encoded[0] == 0.toByte()) encoded.copyOfRange(1, encoded.size) else encoded
    }
    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private const val ID = "Id"
    private const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
    private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
    private const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
    private const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
    private const val ENVELOPED_TRANSFORM = "http://www.w3.org/2000/09/xmldsig#enveloped-signature"
    private const val SHA1_URI = "http://www.w3.org/2000/09/xmldsig#sha1"
    private const val RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
    private const val SHA_1 = "SHA-1"
    private const val SHA_256 = "SHA-256"
    private const val MAX_OUTPUT_BYTES = 1_048_576
    private const val MAX_SIGNATURE_BYTES = 16_384
    private const val SHA_256_BYTES = 32
    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_STYLESHEET =
        "http://javax.xml.XMLConstants/property/accessExternalStylesheet"
}
