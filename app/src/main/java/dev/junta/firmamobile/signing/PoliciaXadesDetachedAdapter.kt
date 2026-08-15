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
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Local XAdES-BES detached adapter for the exact Policía Nacional AutoScript contract. */
class PoliciaXadesDetachedAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!isExactPoliciaRequest(request) || certificateChain.isEmpty()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val signingCertificate = certificateChain.first()
        if (signingCertificate.publicKey !is RSAPublicKey) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }

        // Strict certificate filtering according to public filters:
        // filters.1=dnie:;nonexpired:
        // filters.2=keyusage.nonrepudiation:true;nonexpired:
        val now = Date.from(clock.instant())
        if (now.before(signingCertificate.notBefore) || now.after(signingCertificate.notAfter)) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }
        if (!isDnieCertificate(signingCertificate) && !hasNonRepudiationKeyUsage(signingCertificate)) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }

        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(isValidPoliciaExtraProperties(extraProperties))
                    PoliciaXadesDetachedCodec.createPreSign(data, certificateChain, clock)
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedInfo,
                    state = PoliciaXadesPreSignState(
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
        if (!isExactPoliciaRequest(request)) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? PoliciaXadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    PoliciaXadesDetachedCodec.complete(
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

    private fun isExactPoliciaRequest(request: NormalizedSignRequest): Boolean =
        request.protocolId == ID &&
            request.context.profileId == PROFILE_ID &&
            request.context.profileVersion == PROFILE_VERSION &&
            request.context.origin.scheme == "https" &&
            request.context.origin.host == HOST &&
            request.context.origin.port == 443 &&
            request.algorithm == SigningAlgorithm.SHA1_WITH_RSA &&
            request.format == SigningFormat.XADES &&
            request.safeDescription == SAFE_DESCRIPTION

    private fun isValidPoliciaExtraProperties(raw: String): Boolean {
        val observed = linkedMapOf<String, String>()
        val lines = raw.split('\n')
        if (lines.isEmpty() || lines.size > 10) return false
        for (rawLine in lines) {
            val line = rawLine.removeSuffix("\r")
            if (line.isEmpty()) continue
            val separator = line.indexOf('=')
            if (separator <= 0) return false
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (value.any(Char::isISOControl)) return false
            if (observed.put(key, value) != null) return false
        }
        return observed == EXPECTED_EXTRA_PROPERTIES
    }

    companion object {
        val ID = SigningProtocolId("policia-xades-detached-v1")
        const val PROFILE_ID = "policia-solicitud-generica"
        const val PROFILE_VERSION = 1
        const val HOST = "sede.policia.gob.es"
        const val SAFE_DESCRIPTION = "Firma de solicitud en la Sede de la Policía Nacional"
        val EXPECTED_EXTRA_PROPERTIES = linkedMapOf(
            "format" to "XAdES Detached",
            "filters.1" to "dnie:;nonexpired:",
            "filters.2" to "keyusage.nonrepudiation:true;nonexpired:",
        )
        private val DNIE_CN_REGEX = Regex("^AC DNIE\\s+[A-Za-z0-9_.-]+$", RegexOption.IGNORE_CASE)
        private val WHITESPACE_REGEX = Regex("\\s+")

        fun isDnieCertificate(cert: X509Certificate): Boolean {
            if (!hasNonRepudiationKeyUsage(cert)) {
                return false
            }

            val x500Name = runCatching {
                X500Name.getInstance(cert.issuerX500Principal.encoded)
            }.getOrNull() ?: return false

            val rdns = x500Name.rdNs ?: return false
            val cnValues = mutableListOf<String>()
            val ouValues = mutableListOf<String>()
            val oValues = mutableListOf<String>()
            val cValues = mutableListOf<String>()

            for (rdn in rdns) {
                for (tav in rdn.typesAndValues) {
                    val valueStr = IETFUtils.valueToString(tav.value)
                    when (tav.type) {
                        BCStyle.CN -> cnValues.add(valueStr)
                        BCStyle.OU -> ouValues.add(valueStr)
                        BCStyle.O -> oValues.add(valueStr)
                        BCStyle.C -> cValues.add(valueStr)
                    }
                }
            }

            if (cnValues.size != 1 || ouValues.size != 1 || oValues.size != 1 || cValues.size != 1) {
                return false
            }

            val cn = cnValues[0].trim()
            val ou = ouValues[0].trim()
            val o = oValues[0].trim().replace(WHITESPACE_REGEX, " ")
            val c = cValues[0].trim()

            if (!DNIE_CN_REGEX.matches(cn)) {
                return false
            }
            if (!ou.equals("DNIE", ignoreCase = true)) {
                return false
            }
            if (!o.equals("DIRECCION GENERAL DE LA POLICIA", ignoreCase = true)) {
                return false
            }
            if (!c.equals("ES", ignoreCase = true)) {
                return false
            }

            return true
        }

        fun hasNonRepudiationKeyUsage(cert: X509Certificate): Boolean {
            val keyUsage = cert.keyUsage ?: return false
            return keyUsage.size > 1 && keyUsage[1]
        }
    }
}

private class PoliciaXadesPreSignState(
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

internal data class PoliciaXadesPreSignMaterial(
    val unsignedDocument: ByteArray,
    val signedInfo: ByteArray,
    val signingCertificateFingerprint: ByteArray,
)

internal object PoliciaXadesDetachedCodec {
    init {
        Init.init()
    }

    fun createPreSign(
        data: ByteArray,
        certificateChain: List<X509Certificate>,
        clock: Clock,
    ): PoliciaXadesPreSignMaterial {
        require(data.isNotEmpty() && data.size <= MAX_INPUT_BYTES)
        require(certificateChain.isNotEmpty() && certificateChain.size <= MAX_CERTIFICATES)
        val signingCertificate = certificateChain.first()
        val rsaPublicKey = signingCertificate.publicKey as? RSAPublicKey
            ?: error("RSA certificate required")

        val xmlDoc = tryParseXml(data)
        val isXml = xmlDoc != null

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
            if (isXml) {
                setAttribute(MIME_TYPE, XML_MIME_TYPE)
                appendChild(output.importNode(xmlDoc.documentElement, true))
            } else {
                setAttribute(MIME_TYPE, OCTET_STREAM_MIME_TYPE)
                setAttribute(ENCODING_ATTR, BASE64_ENCODING_URI)
                textContent = Base64.getEncoder().encodeToString(data)
            }
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
            document = output,
            signedPropertiesId = signedPropertiesId,
            referenceId = referenceId,
            certificate = signingCertificate,
            clock = clock,
            isXml = isXml,
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

        val contentDigest = if (isXml) {
            digest(canonicalize(content))
        } else {
            digest(data.copyOf())
        }

        val signedInfo = createSignedInfo(
            document = output,
            contentId = contentId,
            referenceId = referenceId,
            contentDigest = contentDigest,
            isXml = isXml,
            signedPropertiesId = signedPropertiesId,
            signedPropertiesDigest = digest(canonicalize(signedProperties)),
            keyInfoId = keyInfoId,
            keyInfoDigest = digest(canonicalize(keyInfo)),
        )
        signature.insertBefore(signedInfo, signatureValue)

        val unsignedDocument = serialize(output)
        require(unsignedDocument.size <= MAX_OUTPUT_BYTES)
        return PoliciaXadesPreSignMaterial(
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

    fun recoverSignedData(signatureDocument: ByteArray): ByteArray {
        val document = parse(signatureDocument)
        val content = document.singleChildOrDescendant(ROOT, CONTENT)
        val encoding = content.getAttribute(ENCODING_ATTR)
        return if (encoding == BASE64_ENCODING_URI) {
            Base64.getDecoder().decode(content.textContent.trim())
        } else {
            val firstChild = content.childNodes.asElementList().firstOrNull()
            if (firstChild != null) {
                serialize(newDocument().apply { appendChild(importNode(firstChild, true)) })
            } else {
                content.textContent.encodeToByteArray()
            }
        }
    }

    internal fun validate(
        signatureDocument: ByteArray,
        expectedCertificateFingerprint: ByteArray? = null,
    ): Boolean = runCatching {
        val document = parse(signatureDocument)
        require(document.documentElement.nodeName == ROOT)
        val signature = document.singleDs("Signature")
        val signedInfo = signature.singleChildDs("SignedInfo")
        require(
            signedInfo.singleChildDs("SignatureMethod").getAttribute("Algorithm") == RSA_SHA1,
        )
        require(
            signedInfo.singleChildDs("CanonicalizationMethod").getAttribute("Algorithm") == C14N,
        )
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
            val transforms = reference.getElementsByTagNameNS(DS_NS, "Transform")
            val transformAlgorithm = if (transforms.length > 0) {
                (transforms.item(0) as Element).getAttribute("Algorithm")
            } else null

            val calculatedDigest = when (transformAlgorithm) {
                BASE64_ENCODING_URI -> {
                    val rawBytes = Base64.getDecoder().decode(target.textContent.trim())
                    digest(rawBytes)
                }
                C14N -> digest(canonicalize(target))
                null -> digest(canonicalize(target))
                else -> error("Unsupported transform: $transformAlgorithm")
            }
            require(MessageDigest.isEqual(calculatedDigest, expectedDigest))
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
        isXml: Boolean,
        signedPropertiesId: String,
        signedPropertiesDigest: ByteArray,
        keyInfoId: String,
        keyInfoDigest: ByteArray,
    ): Element = document.ds("SignedInfo").apply {
        appendChild(document.ds("CanonicalizationMethod").apply {
            setAttribute("Algorithm", C14N)
        })
        appendChild(document.ds("SignatureMethod").apply {
            setAttribute("Algorithm", RSA_SHA1)
        })
        appendChild(document.reference(
            id = referenceId,
            uri = "#$contentId",
            digestValue = contentDigest,
            transformAlgorithm = if (isXml) C14N else BASE64_ENCODING_URI,
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
        isXml: Boolean,
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
                appendChild(document.xades("MimeType").apply {
                    textContent = if (isXml) XML_MIME_TYPE else OCTET_STREAM_MIME_TYPE
                })
                if (!isXml) {
                    appendChild(document.xades("Encoding").apply {
                        textContent = BASE64_ENCODING_URI
                    })
                }
            })
        })
    }

    private fun Document.reference(
        uri: String,
        digestValue: ByteArray,
        id: String? = null,
        type: String? = null,
        transformAlgorithm: String? = null,
    ): Element = ds("Reference").apply {
        id?.let { setAttribute(ID, it) }
        setAttribute("URI", uri)
        type?.let { setAttribute("Type", it) }
        if (transformAlgorithm != null) {
            appendChild(ds("Transforms").apply {
                appendChild(ds("Transform").apply {
                    setAttribute("Algorithm", transformAlgorithm)
                })
            })
        }
        appendChild(ds("DigestMethod").apply {
            setAttribute("Algorithm", SHA512_URI)
        })
        appendChild(ds("DigestValue").apply {
            textContent = b64(digestValue)
        })
    }

    private fun Document.ds(name: String): Element = createElementNS(DS_NS, "ds:$name")

    private fun Document.xades(name: String): Element = createElementNS(XADES_NS, "xades:$name")

    private fun Element.singleChildDs(name: String): Element {
        val children = childNodes
        var found: Element? = null
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node is Element && node.namespaceURI == DS_NS && node.localName == name) {
                check(found == null)
                found = node
            }
        }
        return checkNotNull(found)
    }

    private fun Document.singleDs(name: String): Element {
        val list = getElementsByTagNameNS(DS_NS, name)
        require(list.length == 1)
        return list.item(0) as Element
    }

    private fun Document.singleChildOrDescendant(rootName: String, childName: String): Element {
        require(documentElement.nodeName == rootName)
        val list = documentElement.getElementsByTagName(childName)
        require(list.length == 1)
        return list.item(0) as Element
    }

    private fun Document.findById(id: String): Element =
        documentElement.walk().single { it.getAttribute(ID) == id }

    private fun Element.walk(): Sequence<Element> = sequence {
        yield(this@walk)
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) yieldAll(child.walk())
        }
    }

    private fun org.w3c.dom.NodeList.asElementList(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun newDocument(): Document = secureDocumentBuilderFactory().newDocumentBuilder().newDocument()

    private fun parse(bytes: ByteArray): Document {
        require(bytes.isNotEmpty())
        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        require(!decoded.contains("<!DOCTYPE", ignoreCase = true))
        return secureDocumentBuilderFactory().newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun tryParseXml(bytes: ByteArray): Document? = runCatching {
        require(bytes.isNotEmpty())
        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        if (decoded.contains("<!DOCTYPE", ignoreCase = true)) {
            return@runCatching null
        }
        val trimmed = decoded.trim()
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) {
            return@runCatching null
        }
        secureDocumentBuilderFactory().newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }.getOrNull()

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }

    private fun serialize(document: Document): ByteArray {
        val stream = ClearingByteArrayOutputStream()
        TransformerFactory.newInstance().apply {
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "") }
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(document), StreamResult(stream))
        return stream.toByteArrayAndZeroize()
    }

    private fun canonicalize(node: Node): ByteArray {
        val output = ClearingByteArrayOutputStream()
        Canonicalizer.getInstance(C14N).canonicalizeSubtree(node, output)
        return output.toByteArrayAndZeroize()
    }

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-512").digest(bytes).also { bytes.fill(0) }

    private fun b64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes).also { bytes.fill(0) }

    private fun java.math.BigInteger.unsigned(): ByteArray {
        val raw = toByteArray()
        return if (raw.isNotEmpty() && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }

    private class ClearingByteArrayOutputStream : ByteArrayOutputStream() {
        fun toByteArrayAndZeroize(): ByteArray {
            val result = toByteArray()
            buf.fill(0)
            reset()
            return result
        }
    }

    private const val ROOT = "AFIRMA"
    private const val CONTENT = "CONTENT"
    private const val ID = "Id"
    private const val MIME_TYPE = "MimeType"
    private const val ENCODING_ATTR = "Encoding"
    private const val XML_MIME_TYPE = "text/xml"
    private const val OCTET_STREAM_MIME_TYPE = "application/octet-stream"
    private const val BASE64_ENCODING_URI = "http://www.w3.org/2000/09/xmldsig#base64"
    private const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
    private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
    private const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
    private const val RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
    private const val SHA512_URI = "http://www.w3.org/2001/04/xmlenc#sha512"
    private const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
    private const val JCA_SIGNATURE = "SHA1withRSA"
    private const val SHA_256 = "SHA-256"
    private const val SHA_256_BYTES = 32
    private const val MAX_INPUT_BYTES = 4 * 1024 * 1024
    private const val MAX_OUTPUT_BYTES = 8 * 1024 * 1024
    private const val MAX_SIGNATURE_BYTES = 4096
    private const val MAX_CERTIFICATES = 16
}
