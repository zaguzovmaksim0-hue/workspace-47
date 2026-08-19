package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.NetworkUrlValidation
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
import dev.junta.firmamobile.network.ValidatedNetworkUrl
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Properties
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

internal class AutoFirmaCadesTriPhaseCodec(
    private val urlPolicy: SafeNetworkUrlPolicy = SafeNetworkUrlPolicy(),
    private val expectedDocumentBytes: Int? = null,
    private val expectedExtraProperties: Map<String, String>? = null,
    private val expectedSigningFormat: SigningFormat = SigningFormat.CADES,
    private val wireFormat: String = CADES_FORMAT,
    private val expectedSessionFormat: String = CADES_FORMAT,
    private val extraPropertiesValidator: ((Map<String, String>, List<X509Certificate>) -> Boolean)? = null,
) : TriPhaseProtocolCodec {
    init {
        require(expectedDocumentBytes == null || expectedDocumentBytes > 0)
        require(expectedExtraProperties == null || SERVER_URL_PROPERTY in expectedExtraProperties)
        require(expectedSigningFormat == SigningFormat.CADES || expectedSigningFormat == SigningFormat.PADES)
        require(wireFormat == CADES_FORMAT || wireFormat == PADES_WIRE_FORMAT)
        require(expectedSessionFormat == CADES_FORMAT || expectedSessionFormat == PADES_SESSION_FORMAT)
    }

    override fun decodeRequest(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): JuntaTriPhaseRequestData {
        if (request.format != expectedSigningFormat || certificateChain.isEmpty() ||
            certificateChain.size > MAX_CERTIFICATE_CHAIN_LENGTH
        ) {
            fail(TriPhaseCodecError.INVALID_REQUEST)
        }
        return try {
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { document, rawProperties ->
                    if (expectedDocumentBytes != null && document.size != expectedDocumentBytes) {
                        fail(TriPhaseCodecError.INVALID_REQUEST)
                    }
                    val properties = parseProperties(rawProperties)
                    val endpointValue = properties.getProperty(SERVER_URL_PROPERTY)
                        ?: fail(TriPhaseCodecError.INVALID_REQUEST)
                    val endpoint = when (val validation = urlPolicy.validateEndpoint(endpointValue)) {
                        is NetworkUrlValidation.Allowed -> validation.url
                        is NetworkUrlValidation.Blocked -> fail(TriPhaseCodecError.ORIGIN_NOT_ALLOWED)
                    }
                    val actualProperties =
                        properties.stringPropertyNames().associateWith(properties::getProperty)
                    expectedExtraProperties?.let { expected ->
                        if (actualProperties != expected) {
                            fail(TriPhaseCodecError.INVALID_REQUEST)
                        }
                    }
                    if (extraPropertiesValidator?.invoke(actualProperties, certificateChain) == false) {
                        fail(TriPhaseCodecError.INVALID_REQUEST)
                    }
                    val certificateDer = mutableListOf<ByteArray>()
                    try {
                        certificateChain.forEach { certificate ->
                            val encoded = certificate.encoded
                            if (encoded.isEmpty() || encoded.size > MAX_CERTIFICATE_BYTES) {
                                encoded.fill(0)
                                fail(TriPhaseCodecError.INVALID_REQUEST)
                            }
                            certificateDer += encoded
                        }
                    } catch (error: Exception) {
                        certificateDer.forEach { it.fill(0) }
                        throw error
                    }
                    JuntaTriPhaseRequestData(
                        requestOwner = request,
                        endpoint = endpoint,
                        document = document.copyOf(),
                        certificateDer = certificateDer,
                        signingPublicKey = certificateChain.first().publicKey,
                        properties = properties,
                        algorithm = request.algorithm,
                    )
                }
            }
        } catch (error: TriPhaseCodecException) {
            throw error
        } catch (_: Exception) {
            fail(TriPhaseCodecError.INVALID_REQUEST)
        }
    }

    override fun buildPreRequest(data: TriPhaseDecodedRequest): ProfileHttpRequest =
        buildPreRequest(data as? JuntaTriPhaseRequestData ?: fail(TriPhaseCodecError.INVALID_REQUEST))

    private fun buildPreRequest(data: JuntaTriPhaseRequestData): ProfileHttpRequest =
        ProfileHttpRequest(
            url = data.endpoint,
            body = buildBody { body ->
                body.literal("op=pre&cop=sign&format=")
                body.literal(wireFormat)
                body.literal("&algo=")
                body.literal(data.algorithm.wireName())
                body.literal("&cert=")
                data.writeCertificateParameter(body)
                body.literal("&doc=")
                data.withDocument { body.urlBase64(it) }
                data.writePropertiesParameter(body)
            },
        )

    override fun parsePreResponse(
        data: TriPhaseDecodedRequest,
        response: ByteArray,
    ): PreSignResult = parsePreResponse(
        data as? JuntaTriPhaseRequestData ?: fail(TriPhaseCodecError.INVALID_REQUEST),
        response,
    )

    private fun parsePreResponse(
        data: JuntaTriPhaseRequestData,
        response: ByteArray,
    ): PreSignResult {
        if (response.size > MAX_WIRE_RESPONSE_BYTES) fail(TriPhaseCodecError.RESPONSE_TOO_LARGE)
        if (response.size <= MIN_PROTOCOL_RESPONSE_BYTES || response.hasErrorPrefix()) {
            fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
        }
        val xmlBytes = decodeUrlBase64(response, MAX_DECODED_XML_BYTES)
        try {
            if (!xmlBytes.isStrictUtf8WithoutNul() ||
                xmlBytes.containsAsciiIgnoreCase("<!doctype") ||
                xmlBytes.containsAsciiIgnoreCase("<!entity")
            ) {
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            val builder = secureDocumentBuilderFactory().newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw SAXException("External XML entities are disabled") }
                setErrorHandler(
                    object : DefaultHandler() {
                        override fun warning(error: SAXParseException) = throw error

                        override fun error(error: SAXParseException) = throw error

                        override fun fatalError(error: SAXParseException) = throw error
                    },
                )
            }
            val document = builder.parse(ByteArrayInputStream(xmlBytes))
            val root = document.documentElement
            val rootAttributes = root.attributeNames()
            val legacyRootAttributes = rootAttributes == setOf(LEGACY_FORMAT_ATTRIBUTE, OPERATION_ATTRIBUTE) &&
                root.getAttribute(LEGACY_FORMAT_ATTRIBUTE) == expectedSessionFormat &&
                root.getAttribute(OPERATION_ATTRIBUTE) == LEGACY_SIGN_OPERATION
            if (root.tagName != XML_ROOT || (rootAttributes.isNotEmpty() && !legacyRootAttributes)) {
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            val rootChildren = root.elementChildren()
            if (rootChildren.size != 1 || rootChildren.single().tagName != XML_SIGNATURES) {
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            val signatures = rootChildren.single()
            val signatureAttributes = signatures.attributeNames()
            if (signatureAttributes.isNotEmpty() &&
                (signatureAttributes != setOf(FORMAT_ATTRIBUTE) ||
                    signatures.getAttribute(FORMAT_ATTRIBUTE) != expectedSessionFormat)
            ) {
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            val sessionFormat = if (signatureAttributes.isEmpty()) null else expectedSessionFormat
            val signElements = signatures.elementChildren()
            if (signElements.size != 1 || signElements.single().tagName != XML_SIGNATURE) {
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            val signElement = signElements.single()
            if (!signElement.attributeNames().all { it == ID_ATTRIBUTE || it == SIGN_ID_ATTRIBUTE }) {
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            val id = signElement.optionalBoundedAttribute(ID_ATTRIBUTE) ?: UUID.randomUUID().toString()
            val signId = signElement.optionalBoundedAttribute(SIGN_ID_ATTRIBUTE)
            val params = linkedMapOf<String, String>()
            val paramElements = signElement.elementChildren()
            if (paramElements.isEmpty() || paramElements.size > MAX_SESSION_PARAMETERS) {
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            paramElements.forEach { param ->
                if (param.tagName != XML_PARAMETER || param.attributeNames() != setOf(NAME_ATTRIBUTE) ||
                    param.elementChildren(allowText = true).isNotEmpty()
                ) {
                    fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
                }
                val name = param.getAttribute(NAME_ATTRIBUTE)
                val value = param.textContent.trim()
                if (!PARAMETER_NAME.matches(name) || value.length > MAX_SESSION_VALUE_CHARS ||
                    value.any(Char::isISOControl) ||
                    params.put(name, value) != null
                ) {
                    fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
                }
            }
            if (params.containsKey(PK1_PARAMETER)) {
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            val encodedPre = params.remove(PRE_PARAMETER)
                ?: fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            val pre = decodeStandardBase64(encodedPre, JcaLocalSignatureEngine.MAX_INPUT_BYTES)
            if (pre.isEmpty()) {
                pre.fill(0)
                fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
            val needPre = params[NEED_PRE_PARAMETER]?.let { value ->
                when {
                    value.equals("true", ignoreCase = true) -> true
                    value.equals("false", ignoreCase = true) -> false
                    else -> {
                        pre.fill(0)
                        fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
                    }
                }
            } ?: false
            return PreSignResult(
                requestOwner = data.requestOwner,
                bytesToSign = pre,
                state = JuntaPreSignState(
                    requestData = data,
                    id = id,
                    signId = signId,
                    format = sessionFormat,
                    parameters = params,
                    preSign = pre.copyOf(),
                    needPre = needPre,
                ),
            )
        } catch (error: TriPhaseCodecException) {
            throw error
        } catch (_: Exception) {
            fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
        } finally {
            xmlBytes.fill(0)
        }
    }

    override fun buildPostRequest(
        state: PreSignState,
        localSignature: LocalSignature,
    ): ProfileHttpRequest {
        val juntaState = state as? JuntaPreSignState
            ?: run {
                localSignature.close()
                fail(TriPhaseCodecError.INVALID_REQUEST)
            }
        val pk1 = try {
            localSignature.withBytes { signature ->
                if (!juntaState.verify(signature)) fail(TriPhaseCodecError.INVALID_REQUEST)
                Base64.getEncoder().encode(signature)
            }
        } finally {
            localSignature.close()
        }
        val sessionXml = try {
            juntaState.serializeSession(pk1)
        } finally {
            pk1.fill(0)
        }
        return try {
            ProfileHttpRequest(
                url = juntaState.requestData.endpoint,
                body = buildBody { body ->
                    body.literal("op=post&cop=sign&format=")
                    body.literal(wireFormat)
                    body.literal("&algo=")
                    body.literal(juntaState.requestData.algorithm.wireName())
                    body.literal("&cert=")
                    juntaState.requestData.writeCertificateParameter(body)
                    juntaState.requestData.writePropertiesParameter(body)
                    body.literal("&session=")
                    body.urlBase64(sessionXml)
                    body.literal("&doc=")
                    juntaState.requestData.withDocument { body.urlBase64(it) }
                },
            )
        } finally {
            sessionXml.fill(0)
        }
    }

    override fun parsePostResponse(response: ByteArray): LocalSignature {
        if (response.size > MAX_WIRE_RESPONSE_BYTES) fail(TriPhaseCodecError.RESPONSE_TOO_LARGE)
        if (response.size <= MIN_PROTOCOL_RESPONSE_BYTES || response.hasErrorPrefix()) {
            fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
        }
        var start = 0
        var end = response.size
        while (start < end && response[start].isAsciiWhitespace()) start++
        while (end > start && response[end - 1].isAsciiWhitespace()) end--
        if (end - start <= POST_SUCCESS_PREFIX_BYTES.size ||
            !response.matchesAt(start, POST_SUCCESS_PREFIX_BYTES)
        ) {
            fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
        }
        val encodedSignature = response.copyOfRange(start + POST_SUCCESS_PREFIX_BYTES.size, end)
        val signature = try {
            decodeUrlBase64(encodedSignature, MAX_FINAL_SIGNATURE_BYTES)
        } finally {
            encodedSignature.fill(0)
        }
        if (signature.isEmpty()) {
            signature.fill(0)
            fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
        }
        return LocalSignature(signature)
    }

    private fun parseProperties(raw: String): Properties {
        if (raw.length > MAX_EXTRA_PROPERTIES_CHARS || raw.any { it == '\u0000' }) {
            fail(TriPhaseCodecError.REQUEST_TOO_LARGE)
        }
        val properties = DuplicateRejectingProperties()
        try {
            properties.load(raw.reader())
        } catch (_: DuplicatePropertyException) {
            fail(TriPhaseCodecError.INVALID_REQUEST)
        } catch (_: Exception) {
            fail(TriPhaseCodecError.INVALID_REQUEST)
        }
        if (properties.size > MAX_EXTRA_PROPERTIES ||
            properties.stringPropertyNames().any { key ->
                key.length > MAX_PROPERTY_CHARS ||
                    properties.getProperty(key).length > MAX_PROPERTY_CHARS
            }
        ) {
            fail(TriPhaseCodecError.REQUEST_TOO_LARGE)
        }
        if (!properties.containsKey(SERVER_URL_PROPERTY)) {
            fail(TriPhaseCodecError.INVALID_REQUEST)
        }
        return properties
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                isXIncludeAware = false
            } catch (_: UnsupportedOperationException) {
                // Android parsers can omit XInclude support.
            }
            try {
                isExpandEntityReferences = false
            } catch (_: UnsupportedOperationException) {
                // The byte-level DOCTYPE rejection remains the mandatory boundary.
            }
            trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            try {
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            } catch (_: IllegalArgumentException) {
                // Android parsers can omit these attributes; DOCTYPE and entities remain disabled above.
            }
        }

    private fun DocumentBuilderFactory.trySetFeature(name: String, enabled: Boolean) {
        try {
            setFeature(name, enabled)
        } catch (_: Exception) {
            // Android's parser supports a smaller feature set than the host JAXP parser.
        }
    }

    private fun Element.elementChildren(allowText: Boolean = false): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            when (child.nodeType) {
                Node.ELEMENT_NODE -> result += child as Element
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> if (!allowText && child.nodeValue?.isNotBlank() == true) {
                    fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
                }
                Node.COMMENT_NODE -> Unit
                else -> fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
            }
        }
        return result
    }

    private fun Element.attributeNames(): Set<String> = buildSet {
        for (index in 0 until attributes.length) add(attributes.item(index).nodeName)
    }

    private fun Element.optionalBoundedAttribute(name: String): String? {
        if (!hasAttribute(name)) return null
        return getAttribute(name).takeIf {
            it.isNotBlank() && it.length <= MAX_ID_CHARS && it.none(Char::isISOControl)
        }
            ?: fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
    }

    private fun ByteArray.hasErrorPrefix(): Boolean = matchesAt(0, ERROR_PREFIX_BYTES)

    private fun ByteArray.matchesAt(offset: Int, expected: ByteArray): Boolean =
        offset >= 0 && offset + expected.size <= size &&
            expected.indices.all { index -> this[offset + index] == expected[index] }

    private fun ByteArray.containsAsciiIgnoreCase(expected: String): Boolean {
        if (expected.isEmpty() || expected.length > size) return false
        return (0..size - expected.length).any { offset ->
            expected.indices.all { index ->
                this[offset + index].toInt().toChar().lowercaseChar() ==
                    expected[index].lowercaseChar()
            }
        }
    }

    private fun ByteArray.isStrictUtf8WithoutNul(): Boolean {
        var index = 0
        while (index < size) {
            val first = this[index].toInt() and 0xff
            when {
                first in 0x01..0x7f -> index++
                first in 0xc2..0xdf -> {
                    if (!hasContinuation(index + 1)) return false
                    index += 2
                }
                first == 0xe0 -> {
                    if (!hasByteIn(index + 1, 0xa0..0xbf) || !hasContinuation(index + 2)) return false
                    index += 3
                }
                first in 0xe1..0xec || first in 0xee..0xef -> {
                    if (!hasContinuation(index + 1) || !hasContinuation(index + 2)) return false
                    index += 3
                }
                first == 0xed -> {
                    if (!hasByteIn(index + 1, 0x80..0x9f) || !hasContinuation(index + 2)) return false
                    index += 3
                }
                first == 0xf0 -> {
                    if (!hasByteIn(index + 1, 0x90..0xbf) ||
                        !hasContinuation(index + 2) || !hasContinuation(index + 3)
                    ) return false
                    index += 4
                }
                first in 0xf1..0xf3 -> {
                    if (!hasContinuation(index + 1) || !hasContinuation(index + 2) ||
                        !hasContinuation(index + 3)
                    ) return false
                    index += 4
                }
                first == 0xf4 -> {
                    if (!hasByteIn(index + 1, 0x80..0x8f) ||
                        !hasContinuation(index + 2) || !hasContinuation(index + 3)
                    ) return false
                    index += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun ByteArray.hasContinuation(index: Int): Boolean =
        hasByteIn(index, 0x80..0xbf)

    private fun ByteArray.hasByteIn(index: Int, range: IntRange): Boolean =
        index in indices && (this[index].toInt() and 0xff) in range

    private fun Byte.isAsciiWhitespace(): Boolean = when (toInt().toChar()) {
        ' ', '\t', '\r', '\n' -> true
        else -> false
    }

    private fun decodeUrlBase64(encoded: ByteArray, maxDecodedBytes: Int): ByteArray =
        decodeBase64(encoded, maxDecodedBytes, Base64.getUrlDecoder())

    private fun decodeStandardBase64(encoded: String, maxDecodedBytes: Int): ByteArray {
        val encodedBytes = encoded.toByteArray(StandardCharsets.US_ASCII)
        return try {
            decodeBase64(encodedBytes, maxDecodedBytes, Base64.getDecoder())
        } finally {
            encodedBytes.fill(0)
        }
    }

    private fun decodeBase64(
        encoded: ByteArray,
        maxDecodedBytes: Int,
        decoder: Base64.Decoder,
    ): ByteArray {
        if (encoded.isEmpty() || encoded.size.toLong() > maxDecodedBytes.toLong() * 4 / 3 + 4) {
            fail(TriPhaseCodecError.RESPONSE_TOO_LARGE)
        }
        val decoded = try {
            decoder.decode(encoded)
        } catch (_: IllegalArgumentException) {
            fail(TriPhaseCodecError.RESPONSE_FORMAT_INVALID)
        }
        if (decoded.size > maxDecodedBytes) {
            decoded.fill(0)
            fail(TriPhaseCodecError.RESPONSE_TOO_LARGE)
        }
        return decoded
    }

    private fun buildBody(write: (SensitiveBodyBuilder) -> Unit): ByteArray {
        val builder = SensitiveBodyBuilder()
        return try {
            write(builder)
            builder.finish()
        } catch (error: TriPhaseCodecException) {
            throw error
        } catch (_: Exception) {
            fail(TriPhaseCodecError.INVALID_REQUEST)
        } finally {
            builder.close()
        }
    }

    private fun SigningAlgorithm.wireName(): String = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
        SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
        SigningAlgorithm.SHA512_WITH_RSA -> fail(TriPhaseCodecError.INVALID_REQUEST)
    }

    private fun fail(code: TriPhaseCodecError): Nothing = throw TriPhaseCodecException(code)

    companion object {
        const val MAX_WIRE_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_DECODED_XML_BYTES = 1_572_864
        private const val MAX_FINAL_SIGNATURE_BYTES = 2 * 1024 * 1024
        private const val MAX_CERTIFICATE_CHAIN_LENGTH = 16
        private const val MAX_CERTIFICATE_BYTES = 65_536
        private const val MAX_EXTRA_PROPERTIES_CHARS = 65_536
        private const val MAX_EXTRA_PROPERTIES = 64
        private const val MAX_PROPERTY_CHARS = 8_192
        private const val MAX_SESSION_PARAMETERS = 64
        private const val MAX_SESSION_VALUE_CHARS = 1_048_576
        private const val MAX_ID_CHARS = 256
        private const val MIN_PROTOCOL_RESPONSE_BYTES = 8
        private val ERROR_PREFIX_BYTES = "ERR-".encodeToByteArray()
        private val POST_SUCCESS_PREFIX_BYTES = "OK NEWID=".encodeToByteArray()
        private const val SERVER_URL_PROPERTY = "serverUrl"
        private const val DOCUMENT_ID_PROPERTY = "documentId"
        private const val ONLY_SIGNING_CERT_PROPERTY = "includeOnlySignningCertificate"
        private const val XML_ROOT = "xml"
        private const val XML_SIGNATURES = "firmas"
        private const val XML_SIGNATURE = "firma"
        private const val XML_PARAMETER = "param"
        private const val FORMAT_ATTRIBUTE = "format"
        private const val LEGACY_FORMAT_ATTRIBUTE = "frmt"
        private const val OPERATION_ATTRIBUTE = "op"
        private const val LEGACY_SIGN_OPERATION = "FIRMAR"
        private const val ID_ATTRIBUTE = "Id"
        private const val SIGN_ID_ATTRIBUTE = "signid"
        private const val NAME_ATTRIBUTE = "n"
        private const val CADES_FORMAT = "CAdES"
        private const val PADES_WIRE_FORMAT = "pades"
        private const val PADES_SESSION_FORMAT = "PAdES"
        private const val PRE_PARAMETER = "PRE"
        private const val PK1_PARAMETER = "PK1"
        private const val NEED_PRE_PARAMETER = "NEED_PRE"
        private val PARAMETER_NAME = Regex("[A-Za-z0-9_.-]{1,64}")
    }

    private class DuplicateRejectingProperties : Properties() {
        override fun put(key: Any, value: Any): Any? {
            if (containsKey(key)) throw DuplicatePropertyException()
            return super.put(key, value)
        }
    }

    private class DuplicatePropertyException : RuntimeException()

    internal class SensitiveBodyBuilder : Closeable {
        private val output = ClearingByteArrayOutputStream()

        fun literal(value: String) {
            val bytes = value.toByteArray(StandardCharsets.US_ASCII)
            try {
                output.write(bytes)
                checkSize()
            } finally {
                bytes.fill(0)
            }
        }

        fun urlBase64(value: ByteArray) {
            val encoded = Base64.getUrlEncoder().encode(value)
            try {
                output.write(encoded)
                checkSize()
            } finally {
                encoded.fill(0)
            }
        }

        fun bytes(value: ByteArray) {
            output.write(value)
            checkSize()
        }

        fun finish(): ByteArray = output.toByteArray().also {
            if (it.size > ProfileHttpRequest.MAX_REQUEST_BYTES) {
                it.fill(0)
                throw TriPhaseCodecException(TriPhaseCodecError.REQUEST_TOO_LARGE)
            }
        }

        private fun checkSize() {
            if (output.size() > ProfileHttpRequest.MAX_REQUEST_BYTES) {
                throw TriPhaseCodecException(TriPhaseCodecError.REQUEST_TOO_LARGE)
            }
        }

        override fun close() = output.clear()
    }

    private class ClearingByteArrayOutputStream : ByteArrayOutputStream() {
        fun clear() {
            buf.fill(0)
            reset()
        }
    }

    internal class JuntaTriPhaseRequestData(
        val requestOwner: NormalizedSignRequest,
        val endpoint: ValidatedNetworkUrl,
        document: ByteArray,
        certificateDer: List<ByteArray>,
        val signingPublicKey: PublicKey,
        private val properties: Properties,
        val algorithm: SigningAlgorithm,
    ) : TriPhaseDecodedRequest {
        private var ownedDocument: ByteArray? = document
        private var ownedCertificates: List<ByteArray>? = certificateDer
        private var closed = false

        @Synchronized
        fun <T> withDocument(block: (ByteArray) -> T): T =
            block(checkNotNull(ownedDocument) { "Tri-phase document is closed" })

        @Synchronized
        fun writeCertificateParameter(body: SensitiveBodyBuilder) {
            val chain = checkNotNull(ownedCertificates) { "Certificate chain is closed" }
            val onlyLeaf = properties.getProperty(ONLY_SIGNING_CERT_PROPERTY)
                ?.equals("true", ignoreCase = true) == true
            (if (onlyLeaf) chain.take(1) else chain).forEachIndexed { index, certificate ->
                if (index != 0) body.literal(",")
                body.urlBase64(certificate)
            }
        }

        @Synchronized
        fun writePropertiesParameter(body: SensitiveBodyBuilder) {
            if (properties.isEmpty()) return
            val rawProperties = ClearingByteArrayOutputStream()
            val writer = OutputStreamWriter(rawProperties, StandardCharsets.UTF_8)
            try {
                properties.store(writer, "")
                writer.flush()
                val rawCopy = rawProperties.toByteArray()
                val encoded = Base64.getUrlEncoder().encode(rawCopy)
                try {
                    body.literal("&params=")
                    body.bytes(encoded)
                } finally {
                    encoded.fill(0)
                    rawCopy.fill(0)
                }
            } finally {
                rawProperties.clear()
            }
        }

        @Synchronized
        override fun close() {
            if (closed) return
            ownedDocument?.fill(0)
            ownedDocument = null
            ownedCertificates?.forEach { it.fill(0) }
            ownedCertificates = null
            properties.clear()
            closed = true
        }
    }

    private class JuntaPreSignState(
        val requestData: JuntaTriPhaseRequestData,
        private val id: String,
        private val signId: String?,
        private val format: String?,
        private val parameters: LinkedHashMap<String, String>,
        private var preSign: ByteArray?,
        private val needPre: Boolean,
    ) : PreSignState {
        private var closed = false

        fun serializeSession(pk1Base64: ByteArray): ByteArray {
            check(!closed)
            val output = ClearingByteArrayOutputStream()
            return try {
                output.write("<xml>\n <firmas".encodeToByteArray())
                if (format != null) output.write(" format=\"CAdES\"".encodeToByteArray())
                output.write(">\n  <firma Id=\"".encodeToByteArray())
                writeXmlEscaped(output, id)
                output.write('"'.code)
                signId?.let { value ->
                    output.write(" signid=\"".encodeToByteArray())
                    writeXmlEscaped(output, value)
                    output.write('"'.code)
                }
                output.write(">\n".encodeToByteArray())
                parameters.toSortedMap().forEach { (name, value) ->
                    writeStringParameter(output, name, value)
                }
                preSign?.takeIf { needPre }?.let { pre ->
                    val encodedPre = Base64.getEncoder().encode(pre)
                    try {
                        writeAsciiParameter(output, PRE_PARAMETER, encodedPre)
                    } finally {
                        encodedPre.fill(0)
                    }
                }
                writeAsciiParameter(output, PK1_PARAMETER, pk1Base64)
                output.write("  </firma>\n </firmas>\n</xml>".encodeToByteArray())
                output.toByteArray()
            } finally {
                output.clear()
            }
        }

        fun verify(signatureBytes: ByteArray): Boolean = try {
            val input = checkNotNull(preSign) { "Pre-sign state is closed" }
            Signature.getInstance(requestData.algorithm.jcaName()).run {
                initVerify(requestData.signingPublicKey)
                update(input)
                verify(signatureBytes)
            }
        } catch (_: Exception) {
            false
        }

        private fun writeStringParameter(
            output: ByteArrayOutputStream,
            name: String,
            value: String,
        ) {
            output.write("   <param n=\"".encodeToByteArray())
            writeXmlEscaped(output, name)
            output.write("\">".encodeToByteArray())
            writeXmlEscaped(output, value)
            output.write("</param>\n".encodeToByteArray())
        }

        private fun writeAsciiParameter(
            output: ByteArrayOutputStream,
            name: String,
            value: ByteArray,
        ) {
            output.write("   <param n=\"$name\">".encodeToByteArray())
            output.write(value)
            output.write("</param>\n".encodeToByteArray())
        }

        override fun close() {
            if (closed) return
            requestData.close()
            preSign?.fill(0)
            preSign = null
            parameters.clear()
            closed = true
        }

        private fun writeXmlEscaped(output: ByteArrayOutputStream, value: String) {
            var index = 0
            while (index < value.length) {
                val codePoint = value.codePointAt(index)
                when (codePoint) {
                    '&'.code -> output.write("&amp;".encodeToByteArray())
                    '<'.code -> output.write("&lt;".encodeToByteArray())
                    '>'.code -> output.write("&gt;".encodeToByteArray())
                    '"'.code -> output.write("&quot;".encodeToByteArray())
                    '\''.code -> output.write("&apos;".encodeToByteArray())
                    else -> output.writeUtf8CodePoint(codePoint)
                }
                index += Character.charCount(codePoint)
            }
        }

        private fun ByteArrayOutputStream.writeUtf8CodePoint(codePoint: Int) {
            when {
                codePoint <= 0x7f -> write(codePoint)
                codePoint <= 0x7ff -> {
                    write(0xc0 or (codePoint shr 6))
                    write(0x80 or (codePoint and 0x3f))
                }
                codePoint <= 0xffff -> {
                    write(0xe0 or (codePoint shr 12))
                    write(0x80 or ((codePoint shr 6) and 0x3f))
                    write(0x80 or (codePoint and 0x3f))
                }
                else -> {
                    write(0xf0 or (codePoint shr 18))
                    write(0x80 or ((codePoint shr 12) and 0x3f))
                    write(0x80 or ((codePoint shr 6) and 0x3f))
                    write(0x80 or (codePoint and 0x3f))
                }
            }
        }
    }
}

internal typealias JuntaTriPhaseCodec = AutoFirmaCadesTriPhaseCodec

internal typealias JuntaTriPhaseRequestData =
    AutoFirmaCadesTriPhaseCodec.JuntaTriPhaseRequestData
