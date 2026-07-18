package dev.junta.firmamobile.signing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
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

/** Local XAdES-BES detached adapter for the exact REG-AGE AutoScript contract. */
class LocalXadesDetachedAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (request.protocolId != ID || request.format != SigningFormat.XADES ||
            request.algorithm != SigningAlgorithm.SHA512_WITH_RSA || certificateChain.isEmpty()
        ) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(extraProperties.isEmpty())
                    XadesDetachedCodec.createPreSign(data, certificateChain, clock)
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedInfo,
                    state = XadesPreSignState(
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
        if (request.protocolId != ID || request.format != SigningFormat.XADES ||
            request.algorithm != SigningAlgorithm.SHA512_WITH_RSA
        ) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? XadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    XadesDetachedCodec.complete(
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

    companion object {
        val ID = SigningProtocolId("local-xades-detached-v1")
    }
}

private class XadesPreSignState(
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

internal data class XadesPreSignMaterial(
    val unsignedDocument: ByteArray,
    val signedInfo: ByteArray,
    val signingCertificateFingerprint: ByteArray,
)

internal object XadesDetachedCodec {
    init {
        Init.init()
    }

    fun createPreSign(
        data: ByteArray,
        certificateChain: List<X509Certificate>,
        clock: Clock,
    ): XadesPreSignMaterial {
        require(data.isNotEmpty() && data.size <= MAX_INPUT_BYTES)
        require(certificateChain.isNotEmpty() && certificateChain.size <= MAX_CERTIFICATES)
        val signingCertificate = certificateChain.first()
        val rsaPublicKey = signingCertificate.publicKey as? RSAPublicKey
            ?: error("RSA certificate required")
        val input = parse(data)
        val output = newDocument()
        val root = output.createElement(ROOT)
        output.appendChild(root)

        val contentId = "CONTENT-${UUID.randomUUID()}"
        val referenceId = "Reference-${UUID.randomUUID()}"
        val signatureStem = "Signature-${UUID.randomUUID()}"
        val signatureId = "$signatureStem-Signature"
        val signedPropertiesId = "$signatureStem-SignedProperties"
        val keyInfoId = "$signatureStem-KeyInfo"

        val content = output.createElement(CONTENT).apply {
            setAttribute(ID, contentId)
            setAttribute(MIME_TYPE, XML_MIME_TYPE)
            appendChild(output.importNode(input.documentElement, true))
        }
        root.appendChild(content)

        val signature = output.ds("Signature").apply {
            setAttribute(ID, signatureId)
        }
        root.appendChild(signature)

        val signatureValue = output.ds("SignatureValue").apply {
            setAttribute(ID, "$signatureStem-SignatureValue")
        }
        val keyInfo = createKeyInfo(output, keyInfoId, rsaPublicKey, certificateChain)
        val signedProperties = createSignedProperties(
            output,
            signedPropertiesId,
            referenceId,
            signingCertificate,
            clock,
        )
        val qualifyingProperties = output.xades("QualifyingProperties").apply {
            setAttribute(ID, "$signatureStem-QualifyingProperties")
            setAttribute("Target", "#$signatureId")
            appendChild(signedProperties)
        }
        val xmlObject = output.ds("Object").apply { appendChild(qualifyingProperties) }
        signature.appendChild(signatureValue)
        signature.appendChild(keyInfo)
        signature.appendChild(xmlObject)

        val signedInfo = createSignedInfo(
            document = output,
            contentId = contentId,
            referenceId = referenceId,
            contentDigest = digest(canonicalize(content)),
            signedPropertiesId = signedPropertiesId,
            signedPropertiesDigest = digest(canonicalize(signedProperties)),
            keyInfoId = keyInfoId,
            keyInfoDigest = digest(canonicalize(keyInfo)),
        )
        signature.insertBefore(signedInfo, signatureValue)

        val unsignedDocument = serialize(output)
        require(unsignedDocument.size <= MAX_OUTPUT_BYTES)
        return XadesPreSignMaterial(
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
    ): ByteArray {
        require(unsignedDocument.isNotEmpty() && unsignedDocument.size <= MAX_OUTPUT_BYTES)
        require(signatureValue.isNotEmpty() && signatureValue.size <= MAX_SIGNATURE_BYTES)
        require(signingCertificateFingerprint.size == SHA_256_BYTES)
        val document = parse(unsignedDocument)
        val signatureValueElement = document.singleDs("SignatureValue")
        require(signatureValueElement.textContent.isEmpty())
        signatureValueElement.textContent = Base64.getEncoder().encodeToString(signatureValue)
        val result = serialize(document)
        require(result.size <= MAX_OUTPUT_BYTES)
        require(validate(result, signingCertificateFingerprint))
        return result
    }

    internal fun validate(
        signatureDocument: ByteArray,
        expectedCertificateFingerprint: ByteArray? = null,
    ): Boolean = runCatching {
        val document = parse(signatureDocument)
        require(document.documentElement.nodeName == ROOT)
        val signature = document.singleDs("Signature")
        val signedInfo = signature.singleChildDs("SignedInfo")
        val signatureValue = Base64.getDecoder().decode(
            signature.singleChildDs("SignatureValue").textContent,
        )
        val keyInfo = signature.singleChildDs("KeyInfo")
        val certificateText = keyInfo.getElementsByTagNameNS(DS_NS, "X509Certificate")
        require(certificateText.length >= 1)
        val certificateBytes = Base64.getDecoder().decode(certificateText.item(0).textContent)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
        expectedCertificateFingerprint?.let {
            require(MessageDigest.isEqual(MessageDigest.getInstance(SHA_256).digest(certificateBytes), it))
        }

        val references = signedInfo.getElementsByTagNameNS(DS_NS, "Reference")
        require(references.length == 3)
        for (index in 0 until references.length) {
            val reference = references.item(index) as Element
            val uri = reference.getAttribute("URI")
            require(uri.startsWith("#") && uri.length > 1)
            val target = document.findById(uri.substring(1))
            val expectedDigest = Base64.getDecoder().decode(
                reference.singleChildDs("DigestValue").textContent,
            )
            require(MessageDigest.isEqual(digest(canonicalize(target)), expectedDigest))
        }
        Signature.getInstance(JCA_SIGNATURE).run {
            initVerify(certificate.publicKey)
            update(canonicalize(signedInfo))
            verify(signatureValue)
        }
    }.getOrDefault(false)

    private fun createSignedInfo(
        document: Document,
        contentId: String,
        referenceId: String,
        contentDigest: ByteArray,
        signedPropertiesId: String,
        signedPropertiesDigest: ByteArray,
        keyInfoId: String,
        keyInfoDigest: ByteArray,
    ): Element = document.ds("SignedInfo").apply {
        appendChild(document.ds("CanonicalizationMethod").apply {
            setAttribute("Algorithm", C14N)
        })
        appendChild(document.ds("SignatureMethod").apply {
            setAttribute("Algorithm", RSA_SHA512)
        })
        appendChild(document.reference(
            id = referenceId,
            uri = "#$contentId",
            digestValue = contentDigest,
            canonicalize = true,
        ))
        appendChild(document.reference(
            uri = "#$signedPropertiesId",
            type = SIGNED_PROPERTIES_TYPE,
            digestValue = signedPropertiesDigest,
        ))
        appendChild(document.reference(
            uri = "#$keyInfoId",
            digestValue = keyInfoDigest,
        ))
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
                    textContent = Base64.getEncoder().encodeToString(rsaPublicKey.modulus.unsigned())
                })
                appendChild(document.ds("Exponent").apply {
                    textContent = Base64.getEncoder().encodeToString(rsaPublicKey.publicExponent.unsigned())
                })
            })
        })
        appendChild(document.ds("X509Data").apply {
            chain.forEach { certificate ->
                appendChild(document.ds("X509Certificate").apply {
                    textContent = Base64.getEncoder().encodeToString(certificate.encoded)
                })
            }
        })
    }

    private fun createSignedProperties(
        document: Document,
        signedPropertiesId: String,
        referenceId: String,
        certificate: X509Certificate,
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
                            textContent = b64(digest(certificate.encoded))
                        })
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
        appendChild(document.xades("SignedDataObjectProperties").apply {
            appendChild(document.xades("DataObjectFormat").apply {
                setAttribute("ObjectReference", "#$referenceId")
                appendChild(document.xades("MimeType").apply { textContent = XML_MIME_TYPE })
            })
        })
    }

    private fun Document.reference(
        uri: String,
        digestValue: ByteArray,
        id: String? = null,
        type: String? = null,
        canonicalize: Boolean = false,
    ): Element = ds("Reference").apply {
        id?.let { setAttribute(ID, it) }
        setAttribute("URI", uri)
        type?.let { setAttribute("Type", it) }
        if (canonicalize) {
            appendChild(ds("Transforms").apply {
                appendChild(ds("Transform").apply { setAttribute("Algorithm", C14N) })
            })
        }
        appendChild(ds("DigestMethod").apply { setAttribute("Algorithm", SHA512_URI) })
        appendChild(ds("DigestValue").apply { textContent = b64(digestValue) })
    }

    private fun parse(bytes: ByteArray): Document {
        requireSafeUtf8Xml(bytes)
        return documentBuilderFactory().newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun newDocument(): Document = documentBuilderFactory().newDocumentBuilder().newDocument()

    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Android's built-in parser has no XInclude implementation and throws here.
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }

    private fun serialize(document: Document): ByteArray {
        val factory = TransformerFactory.newInstance().apply {
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_STYLESHEET, "") }
        }
        val output = ByteArrayOutputStream()
        factory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, Charsets.UTF_8.name())
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    private fun canonicalize(node: Node): ByteArray = ByteArrayOutputStream().use { output ->
        Canonicalizer.getInstance(C14N).canonicalizeSubtree(node, output)
        output.toByteArray()
    }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance(SHA_512).digest(bytes)

    private fun Document.ds(name: String): Element = createElementNS(DS_NS, "ds:$name")

    private fun Document.xades(name: String): Element = createElementNS(XADES_NS, "xades:$name")

    private fun Document.singleDs(name: String): Element =
        getElementsByTagNameNS(DS_NS, name).let { nodes ->
            require(nodes.length == 1)
            nodes.item(0) as Element
        }

    private fun Element.singleChildDs(name: String): Element =
        childNodes.asSequence().filterIsInstance<Element>()
            .single { it.namespaceURI == DS_NS && it.localName == name }

    private fun Document.findById(id: String): Element =
        documentElement.walkElements().single { it.getAttribute(ID) == id }

    private fun Element.walkElements(): Sequence<Element> = sequence {
        yield(this@walkElements)
        childNodes.asSequence().filterIsInstance<Element>().forEach { yieldAll(it.walkElements()) }
    }

    private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> =
        (0 until length).asSequence().map(::item)

    private fun java.math.BigInteger.unsigned(): ByteArray = toByteArray().let { encoded ->
        if (encoded.size > 1 && encoded[0] == 0.toByte()) encoded.copyOfRange(1, encoded.size) else encoded
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun requireSafeUtf8Xml(bytes: ByteArray) {
        val chars = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        try {
            require(!chars.containsAscii("<!DOCTYPE") && !chars.containsAscii("<!ENTITY"))
            val declarationStart = chars.indexOfAscii("<?xml")
            if (declarationStart >= 0) {
                val declarationEnd = chars.indexOfAscii("?>", declarationStart + 5)
                require(declarationEnd >= 0)
                val encoding = chars.indexOfAscii("encoding", declarationStart + 5)
                if (encoding in (declarationStart + 5)..<declarationEnd) {
                    val utf8Double = chars.indexOfAsciiIgnoreCase("\"UTF-8\"", encoding)
                    val utf8Single = chars.indexOfAsciiIgnoreCase("'UTF-8'", encoding)
                    require(utf8Double in encoding..<declarationEnd || utf8Single in encoding..<declarationEnd)
                }
            }
        } finally {
            if (!chars.isReadOnly) {
                for (index in 0 until chars.limit()) chars.put(index, '\u0000')
            }
        }
    }

    private fun java.nio.CharBuffer.containsAscii(value: String): Boolean =
        indexOfAscii(value) >= 0

    private fun java.nio.CharBuffer.indexOfAscii(value: String, fromIndex: Int = 0): Int {
        if (value.isEmpty() || limit() < value.length) return -1
        for (start in fromIndex.coerceAtLeast(0)..limit() - value.length) {
            if (value.indices.all { index -> get(start + index) == value[index] }) return start
        }
        return -1
    }

    private fun java.nio.CharBuffer.indexOfAsciiIgnoreCase(
        value: String,
        fromIndex: Int = 0,
    ): Int {
        if (value.isEmpty() || limit() < value.length) return -1
        for (start in fromIndex.coerceAtLeast(0)..limit() - value.length) {
            if (value.indices.all { index ->
                    get(start + index).lowercaseChar() == value[index].lowercaseChar()
                }
            ) return start
        }
        return -1
    }

    private const val ROOT = "AFIRMA"
    private const val CONTENT = "CONTENT"
    private const val ID = "Id"
    private const val MIME_TYPE = "MimeType"
    private const val XML_MIME_TYPE = "text/xml"
    private const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
    private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
    private const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
    private const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
    private const val SHA512_URI = "http://www.w3.org/2001/04/xmlenc#sha512"
    private const val RSA_SHA512 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"
    private const val SHA_512 = "SHA-512"
    private const val SHA_256 = "SHA-256"
    private const val JCA_SIGNATURE = "SHA512withRSA"
    private const val MAX_INPUT_BYTES = 524_288
    private const val MAX_OUTPUT_BYTES = 2_097_152
    private const val MAX_SIGNATURE_BYTES = 16_384
    private const val MAX_CERTIFICATES = 8
    private const val SHA_256_BYTES = 32
    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_STYLESHEET =
        "http://javax.xml.XMLConstants/property/accessExternalStylesheet"
}
