package dev.junta.firmamobile.signing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Base64
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

/** Local XAdES Enveloping adapter for the exact public Sevilla ATSE certificate-login contract. */
class SevillaAtseXadesEnvelopingAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.isExactSevillaAtseRequest() || certificateChain.isEmpty()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(extraProperties.isEmpty())
                    require(data.isExactSevillaAtseChallenge())
                    SevillaAtseXadesEnvelopingCodec.createPreSign(data, certificateChain, clock)
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedInfo,
                    state = SevillaAtseXadesPreSignState(
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
        if (!request.isExactSevillaAtseRequest()) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? SevillaAtseXadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    SevillaAtseXadesEnvelopingCodec.complete(
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

    private fun NormalizedSignRequest.isExactSevillaAtseRequest(): Boolean =
        protocolId == ID &&
            context.profileId == PROFILE_ID &&
            context.profileVersion == PROFILE_VERSION &&
            context.origin.scheme == HTTPS &&
            context.origin.host == HOST &&
            context.origin.port == HTTPS_PORT &&
            algorithm == SigningAlgorithm.SHA1_WITH_RSA &&
            format == SigningFormat.XADES &&
            safeDescription == SAFE_DESCRIPTION

    companion object {
        val ID = SigningProtocolId("sevilla-atse-xades-enveloping-v1")
        const val PROFILE_ID = "sevilla-atse-certificate-login"
        const val SAFE_DESCRIPTION = "Acceso con certificado a la Agencia Tributaria de Sevilla"
        private const val PROFILE_VERSION = 1
        private const val HTTPS = "https"
        private const val HOST = "www.sevilla.org"
        private const val HTTPS_PORT = 443
    }
}

private class SevillaAtseXadesPreSignState(
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

internal data class SevillaAtseXadesPreSignMaterial(
    val unsignedDocument: ByteArray,
    val signedInfo: ByteArray,
    val signingCertificateFingerprint: ByteArray,
)

internal object SevillaAtseXadesEnvelopingCodec {
    init {
        Init.init()
    }

    fun createPreSign(
        data: ByteArray,
        certificateChain: List<X509Certificate>,
        clock: Clock,
    ): SevillaAtseXadesPreSignMaterial = createPreSign(
        data = data,
        certificateChain = certificateChain,
        clock = clock,
        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        payloadValidator = ByteArray::isExactSevillaAtseChallenge,
    )

    internal fun createPreSign(
        data: ByteArray,
        certificateChain: List<X509Certificate>,
        clock: Clock,
        signingAlgorithm: SigningAlgorithm,
        payloadValidator: (ByteArray) -> Boolean,
    ): SevillaAtseXadesPreSignMaterial {
        require(payloadValidator(data))
        require(signingAlgorithm in SUPPORTED_SIGNATURE_ALGORITHMS)
        require(certificateChain.isNotEmpty() && certificateChain.size <= MAX_CERTIFICATES)
        val signingCertificate = certificateChain.first()
        val rsaPublicKey = signingCertificate.publicKey as? RSAPublicKey
            ?: error("RSA certificate required")

        val output = newDocument()
        val signatureId = "Signature-${UUID.randomUUID()}"
        val signatureValueId = "$signatureId-SignatureValue"
        val keyInfoId = "$signatureId-KeyInfo"
        val dataObjectId = "Object-${UUID.randomUUID()}"
        val referenceId = "Reference-${UUID.randomUUID()}"
        val signedPropertiesId = "$signatureId-SignedProperties"

        val signature = output.ds("Signature").apply { setAttribute(ID, signatureId) }
        output.appendChild(signature)

        val signatureValue = output.ds("SignatureValue").apply { setAttribute(ID, signatureValueId) }
        val keyInfo = createKeyInfo(output, keyInfoId, rsaPublicKey, certificateChain)
        val dataObject = output.ds("Object").apply {
            setAttribute(ID, dataObjectId)
            setAttribute(MIME_TYPE, DEFAULT_MIME_TYPE)
            setAttribute(ENCODING, BASE64_ENCODING)
            textContent = b64(data)
        }
        val signedProperties = createSignedProperties(
            document = output,
            signedPropertiesId = signedPropertiesId,
            referenceId = referenceId,
            signingCertificate = signingCertificate,
            clock = clock,
        )
        val qualifyingProperties = output.xades("QualifyingProperties").apply {
            setAttribute("Target", "#$signatureId")
            appendChild(signedProperties)
        }
        val xadesObject = output.ds("Object").apply { appendChild(qualifyingProperties) }

        signature.appendChild(signatureValue)
        signature.appendChild(keyInfo)
        signature.appendChild(dataObject)
        signature.appendChild(xadesObject)

        val signedInfo = createSignedInfo(
            document = output,
            referenceId = referenceId,
            dataObjectId = dataObjectId,
            dataDigest = digest(data),
            signedPropertiesId = signedPropertiesId,
            signedPropertiesDigest = digest(canonicalize(signedProperties)),
            keyInfoId = keyInfoId,
            keyInfoDigest = digest(canonicalize(keyInfo)),
            signatureMethod = signingAlgorithm.xadesSignatureMethod(),
        )
        signature.insertBefore(signedInfo, signatureValue)

        val unsignedDocument = serialize(output)
        require(unsignedDocument.size <= MAX_OUTPUT_BYTES)
        return SevillaAtseXadesPreSignMaterial(
            unsignedDocument = unsignedDocument,
            signedInfo = canonicalize(signedInfo),
            signingCertificateFingerprint = MessageDigest.getInstance(SHA_256)
                .digest(signingCertificate.encoded),
        )
    }

    fun complete(
        unsignedDocument: ByteArray,
        signingCertificateFingerprint: ByteArray,
        signatureValue: ByteArray,
    ): ByteArray = complete(
        unsignedDocument = unsignedDocument,
        signingCertificateFingerprint = signingCertificateFingerprint,
        signatureValue = signatureValue,
        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        payloadValidator = ByteArray::isExactSevillaAtseChallenge,
    )

    internal fun complete(
        unsignedDocument: ByteArray,
        signingCertificateFingerprint: ByteArray,
        signatureValue: ByteArray,
        signingAlgorithm: SigningAlgorithm,
        payloadValidator: (ByteArray) -> Boolean,
    ): ByteArray {
        require(signingAlgorithm in SUPPORTED_SIGNATURE_ALGORITHMS)
        require(unsignedDocument.isNotEmpty() && unsignedDocument.size <= MAX_OUTPUT_BYTES)
        require(signatureValue.isNotEmpty() && signatureValue.size <= MAX_SIGNATURE_BYTES)
        require(signingCertificateFingerprint.size == SHA_256_BYTES)
        val document = parse(unsignedDocument)
        val signatureValueElement = document.documentElement.singleChildDs("SignatureValue")
        require(signatureValueElement.textContent.isEmpty())
        signatureValueElement.textContent = b64(signatureValue)
        val result = serialize(document)
        require(result.size <= MAX_OUTPUT_BYTES)
        require(
            validate(
                signatureDocument = result,
                expectedCertificateFingerprint = signingCertificateFingerprint,
                signingAlgorithm = signingAlgorithm,
                payloadValidator = payloadValidator,
            ),
        )
        return result
    }

    internal fun validate(
        signatureDocument: ByteArray,
        expectedCertificateFingerprint: ByteArray? = null,
    ): Boolean = validate(
        signatureDocument = signatureDocument,
        expectedCertificateFingerprint = expectedCertificateFingerprint,
        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        payloadValidator = ByteArray::isExactSevillaAtseChallenge,
    )

    internal fun validate(
        signatureDocument: ByteArray,
        expectedCertificateFingerprint: ByteArray? = null,
        signingAlgorithm: SigningAlgorithm,
        payloadValidator: (ByteArray) -> Boolean,
    ): Boolean = runCatching {
        require(signingAlgorithm in SUPPORTED_SIGNATURE_ALGORITHMS)
        val document = parse(signatureDocument)
        val signature = document.documentElement
        require(signature.namespaceURI == DS_NS && signature.localName == "Signature")
        require(signature.getAttribute(ID).isNotEmpty())

        val signedInfo = signature.singleChildDs("SignedInfo")
        require(
            signedInfo.singleChildDs("CanonicalizationMethod").getAttribute("Algorithm") == C14N,
        )
        require(
            signedInfo.singleChildDs("SignatureMethod").getAttribute("Algorithm") ==
                signingAlgorithm.xadesSignatureMethod(),
        )
        val references = signedInfo.directChildrenDs("Reference")
        require(references.size == 3)

        val dataReference = references.single { it.getAttribute("Type") == OBJECT_TYPE }
        require(dataReference.digestMethod() == SHA512_URI)
        val transforms = dataReference.getElementsByTagNameNS(DS_NS, "Transform").asElements()
        require(transforms.size == 1 && transforms.single().getAttribute("Algorithm") == BASE64_TRANSFORM)
        val dataObject = document.findById(dataReference.fragmentId())
        require(dataObject.namespaceURI == DS_NS && dataObject.localName == "Object")
        require(dataObject.getAttribute(ENCODING) == BASE64_ENCODING)
        val decodedData = Base64.getDecoder().decode(dataObject.textContent.trim())
        try {
            require(payloadValidator(decodedData))
            require(
                MessageDigest.isEqual(
                    digest(decodedData),
                    Base64.getDecoder().decode(dataReference.digestValue()),
                ),
            )
        } finally {
            decodedData.fill(0)
        }

        val signedPropertiesReference = references.single {
            it.getAttribute("Type") == SIGNED_PROPERTIES_TYPE
        }
        require(signedPropertiesReference.digestMethod() == SHA512_URI)
        val signedProperties = document.findById(signedPropertiesReference.fragmentId())
        require(signedProperties.namespaceURI == XADES_NS && signedProperties.localName == "SignedProperties")
        require(
            MessageDigest.isEqual(
                digest(canonicalize(signedProperties)),
                Base64.getDecoder().decode(signedPropertiesReference.digestValue()),
            ),
        )

        val keyInfoReference = references.single { reference ->
            if (reference.getAttribute("Type").isNotEmpty()) {
                false
            } else {
                val target = document.findById(reference.fragmentId())
                target.namespaceURI == DS_NS && target.localName == "KeyInfo"
            }
        }
        require(keyInfoReference.digestMethod() == SHA512_URI)
        val keyInfo = document.findById(keyInfoReference.fragmentId())
        require(
            MessageDigest.isEqual(
                digest(canonicalize(keyInfo)),
                Base64.getDecoder().decode(keyInfoReference.digestValue()),
            ),
        )

        val certificateText = keyInfo.getElementsByTagNameNS(DS_NS, "X509Certificate")
        require(certificateText.length in 1..MAX_CERTIFICATES)
        val certificateBytes = Base64.getDecoder().decode(certificateText.item(0).textContent)
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
                require(Signature.getInstance(signingAlgorithm.jcaName()).run {
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

        val qualifyingProperties = signature.getElementsByTagNameNS(XADES_NS, "QualifyingProperties")
            .asElements().single()
        require(qualifyingProperties.getAttribute("Target") == "#${signature.getAttribute(ID)}")
        true
    }.getOrDefault(false)

    private fun createSignedInfo(
        document: Document,
        referenceId: String,
        dataObjectId: String,
        dataDigest: ByteArray,
        signedPropertiesId: String,
        signedPropertiesDigest: ByteArray,
        keyInfoId: String,
        keyInfoDigest: ByteArray,
        signatureMethod: String,
    ): Element = document.ds("SignedInfo").apply {
        appendChild(document.ds("CanonicalizationMethod").apply { setAttribute("Algorithm", C14N) })
        appendChild(document.ds("SignatureMethod").apply { setAttribute("Algorithm", signatureMethod) })
        appendChild(document.ds("Reference").apply {
            setAttribute(ID, referenceId)
            setAttribute("URI", "#$dataObjectId")
            setAttribute("Type", OBJECT_TYPE)
            appendChild(document.ds("Transforms").apply {
                appendChild(document.ds("Transform").apply {
                    setAttribute("Algorithm", BASE64_TRANSFORM)
                })
            })
            appendDigest(document, dataDigest)
        })
        appendChild(document.ds("Reference").apply {
            setAttribute("URI", "#$signedPropertiesId")
            setAttribute("Type", SIGNED_PROPERTIES_TYPE)
            appendDigest(document, signedPropertiesDigest)
        })
        appendChild(document.ds("Reference").apply {
            setAttribute("URI", "#$keyInfoId")
            appendDigest(document, keyInfoDigest)
        })
    }

    private fun Element.appendDigest(document: Document, value: ByteArray) {
        appendChild(document.ds("DigestMethod").apply { setAttribute("Algorithm", SHA512_URI) })
        appendChild(document.ds("DigestValue").apply { textContent = b64(value) })
    }

    private fun createKeyInfo(
        document: Document,
        keyInfoId: String,
        rsaPublicKey: RSAPublicKey,
        chain: List<X509Certificate>,
    ): Element = document.ds("KeyInfo").apply {
        setAttribute(ID, keyInfoId)
        appendChild(document.ds("KeyValue").apply {
            appendChild(document.ds("RSAKeyValue").apply {
                appendChild(document.ds("Modulus").apply {
                    textContent = b64(rsaPublicKey.modulus.unsigned())
                })
                appendChild(document.ds("Exponent").apply {
                    textContent = b64(rsaPublicKey.publicExponent.unsigned())
                })
            })
        })
        appendChild(document.ds("X509Data").apply {
            chain.forEach { certificate ->
                appendChild(document.ds("X509Certificate").apply {
                    textContent = b64(certificate.encoded)
                })
            }
        })
    }

    private fun createSignedProperties(
        document: Document,
        signedPropertiesId: String,
        referenceId: String,
        signingCertificate: X509Certificate,
        clock: Clock,
    ): Element = document.xades("SignedProperties").apply {
        setAttribute(ID, signedPropertiesId)
        appendChild(document.xades("SignedSignatureProperties").apply {
            appendChild(document.xades("SigningTime").apply {
                textContent = DateTimeFormatter.ISO_INSTANT.format(clock.instant())
            })
            appendChild(document.xades("SigningCertificate").apply {
                appendChild(document.xades("Cert").apply {
                    appendChild(document.xades("CertDigest").apply {
                        appendChild(document.ds("DigestMethod").apply {
                            setAttribute("Algorithm", SHA512_URI)
                        })
                        appendChild(document.ds("DigestValue").apply {
                            textContent = b64(digest(signingCertificate.encoded))
                        })
                    })
                    appendChild(document.xades("IssuerSerial").apply {
                        appendChild(document.ds("X509IssuerName").apply {
                            textContent = signingCertificate.issuerX500Principal.name
                        })
                        appendChild(document.ds("X509SerialNumber").apply {
                            textContent = signingCertificate.serialNumber.toString()
                        })
                    })
                })
            })
        })
        appendChild(document.xades("SignedDataObjectProperties").apply {
            appendChild(document.xades("DataObjectFormat").apply {
                setAttribute("ObjectReference", "#$referenceId")
                appendChild(document.xades("ObjectIdentifier").apply {
                    appendChild(document.xades("Identifier").apply {
                        setAttribute("Qualifier", "OIDAsURN")
                        textContent = DEFAULT_CONTENT_OID_URN
                    })
                })
                appendChild(document.xades("MimeType").apply { textContent = DEFAULT_MIME_TYPE })
                appendChild(document.xades("Encoding").apply { textContent = BASE64_ENCODING })
            })
        })
    }

    private fun newDocument(): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }.newDocumentBuilder().newDocument()

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
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance(SHA_512).digest(bytes)

    private fun Document.ds(name: String): Element = createElementNS(DS_NS, "ds:$name")

    private fun Document.xades(name: String): Element = createElementNS(XADES_NS, "xades:$name")

    private fun Element.directChildrenDs(name: String): List<Element> =
        childNodes.asElements().filter { it.namespaceURI == DS_NS && it.localName == name }

    private fun Element.singleChildDs(name: String): Element = directChildrenDs(name).single()

    private fun Element.digestMethod(): String =
        singleChildDs("DigestMethod").getAttribute("Algorithm")

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
    private const val MIME_TYPE = "MimeType"
    private const val ENCODING = "Encoding"
    private const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
    private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
    private const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
    private const val OBJECT_TYPE = "http://www.w3.org/2000/09/xmldsig#Object"
    private const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
    private const val SHA512_URI = "http://www.w3.org/2001/04/xmlenc#sha512"
    private const val RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
    private const val RSA_SHA512 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"
    private const val BASE64_TRANSFORM = "http://www.w3.org/2000/09/xmldsig#base64"
    private const val BASE64_ENCODING = "http://www.w3.org/2000/09/xmldsig#base64"
    private const val DEFAULT_MIME_TYPE = "application/octet-stream"
    private const val DEFAULT_CONTENT_OID_URN = "urn:oid:1.2.840.113549.1.7.1"
    private const val SHA_512 = "SHA-512"
    private const val SHA_256 = "SHA-256"
    private const val MAX_OUTPUT_BYTES = 2_097_152
    private const val MAX_SIGNATURE_BYTES = 16_384
    private const val MAX_CERTIFICATES = 8
    private const val SHA_256_BYTES = 32
    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_STYLESHEET =
        "http://javax.xml.XMLConstants/property/accessExternalStylesheet"
    private val SUPPORTED_SIGNATURE_ALGORITHMS = setOf(
        SigningAlgorithm.SHA1_WITH_RSA,
        SigningAlgorithm.SHA512_WITH_RSA,
    )

    private fun SigningAlgorithm.xadesSignatureMethod(): String = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> RSA_SHA1
        SigningAlgorithm.SHA512_WITH_RSA -> RSA_SHA512
        SigningAlgorithm.SHA256_WITH_RSA -> error("unsupported XAdES Enveloping signature algorithm")
    }
}

private fun ByteArray.isExactSevillaAtseChallenge(): Boolean =
    size == SEVILLA_ATSE_CHALLENGE_BYTES && all { byte ->
        val value = byte.toInt() and 0xff
        value in 0x30..0x39 ||
            value in 0x41..0x5a ||
            value in 0x61..0x7a ||
            value == 0x5f ||
            value == 0x2d
    }

private const val SEVILLA_ATSE_CHALLENGE_BYTES = 40
