package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.CaibBatchUrlPolicy
import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpFailure
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.network.ValidatedNetworkUrl
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** CAIB PortaFIB MiniApplet.signBatch: server PRE -> local PKCS#1 -> server PK1 post-sign. */
class CaibBatchProtocolAdapter internal constructor(
    private val transportFactory: (URI) -> ProfileHttpTransport = { endpoint ->
        HttpsProfileHttpTransport(SafeNetworkUrlPolicy(setOf(endpoint)))
    },
    private val urlPolicy: CaibBatchUrlPolicy = CaibBatchUrlPolicy(),
) : BatchSigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override fun prepare(
        request: NormalizedBatchSigningRequest,
        certificateChain: List<X509Certificate>,
    ): BatchProtocolPrepareResult {
        if (!request.matchesContract() || certificateChain.isEmpty() || certificateChain.size > 16) {
            return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }
        val pair = urlPolicy.validatePair(request.preSignerUrl, request.postSignerUrl)
            ?: return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        if (pair.first.requestToken != request.operationId) {
            return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }
        val xml = request.documents.single().dataReference
        val certs = runCatching { encodeCertificateChain(certificateChain) }.getOrNull()
            ?: return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        val result = post(pair.first.url, "xml=$xml&certs=$certs")
        return when (result) {
            is ProfileHttpResult.Failure -> BatchProtocolPrepareResult.Failure(result.toSigningError())
            is ProfileHttpResult.Success -> result.response.use { response ->
                response.withBody { body ->
                    val parsed = parseTriData(body, request.documents.single().id)
                        ?: return@withBody BatchProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
                    val state = CaibPreSignState(
                        postUrl = pair.second.url,
                        expectedDocumentId = request.documents.single().id,
                        xmlParameter = xml,
                        certsParameter = certs,
                        triData = parsed.second,
                    )
                    val pre = BatchPreSignResult(request, listOf(parsed.first), state)
                    parsed.first.fill(0)
                    BatchProtocolPrepareResult.Success(pre)
                }
            }
        }
    }

    override fun complete(
        request: NormalizedBatchSigningRequest,
        preSign: BatchPreSignResult,
        localSignatures: List<LocalSignature>,
    ): BatchProtocolCompletionResult {
        if (!request.matchesContract() || localSignatures.size != 1) {
            localSignatures.forEach(LocalSignature::close)
            return BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
        val state = preSign.consumeState(request, 1) as? CaibPreSignState
        if (state == null) {
            localSignatures.forEach(LocalSignature::close)
            return BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
        val query = try {
            state.postQuery(localSignatures.single())
        } catch (_: Exception) {
            state.close()
            localSignatures.forEach(LocalSignature::close)
            return BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        } finally {
            localSignatures.forEach(LocalSignature::close)
        }
        val expectedDocumentId = state.expectedDocumentId
        val result = post(state.postUrl, query)
        state.close()
        return when (result) {
            is ProfileHttpResult.Failure -> BatchProtocolCompletionResult.Failure(result.toSigningError())
            is ProfileHttpResult.Success -> result.response.use { response ->
                response.withBody { body ->
                    if (!isValidFinalResult(body, expectedDocumentId)) {
                        BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
                    } else {
                        BatchProtocolCompletionResult.Success(BatchProtocolResponse(body.copyOf()))
                    }
                }
            }
        }
    }

    private fun NormalizedBatchSigningRequest.matchesContract(): Boolean {
        if (!isOpen() || protocolId != ID || context.profileId != PROFILE_ID || context.profileVersion != 1 ||
            context.origin != TrustedOrigin("https", "intranet.caib.es", 443) ||
            algorithm != SigningAlgorithm.SHA256_WITH_RSA || format != BatchSigningFormat.PADES ||
            suboperation != "sign" || stopOnError || documents.size != 1
        ) return false
        val document = documents.single()
        if (document.format != BatchSigningFormat.PADES || document.suboperation !in setOf(null, "sign") ||
            !REQUEST_TOKEN.matches(operationId) || !SIGNATURE_ID.matches(document.id) ||
            !URL_BASE64.matches(document.dataReference) || document.dataReference.length > MAX_BATCH_XML_BASE64_CHARS
        ) return false
        val decodedId = runCatching {
            String(Base64.getUrlDecoder().decode(document.id.padUrlBase64()), Charsets.UTF_8)
        }.getOrNull() ?: return false
        return decodedId == "$operationId|0"
    }

    private fun parseTriData(bytes: ByteArray, expectedId: String): Pair<ByteArray, ByteArray>? = runCatching {
        if (bytes.isEmpty() || bytes.size > MAX_XML_BYTES) return null
        val root = parseXml(bytes) ?: return null
        if (root.tagName != "xml" || root.attributes.length != 0) return null
        val firmas = root.childElements().singleOrNull()?.takeIf { it.tagName == "firmas" } ?: return null
        val firma = firmas.childElements().singleOrNull()?.takeIf { it.tagName == "firma" } ?: return null
        if (firma.getAttribute("Id") != expectedId) return null
        val params = firma.childElements().filter { it.tagName == "param" }
        if (params.isEmpty() || params.map { it.getAttribute("n") }.toSet().size != params.size) return null
        val preText = params.singleOrNull { it.getAttribute("n") == "PRE" }?.textContent?.trim() ?: return null
        val pre = Base64.getDecoder().decode(preText)
        if (pre.isEmpty() || pre.size > MAX_PRE_BYTES) { pre.fill(0); return null }
        pre to bytes.copyOf()
    }.getOrNull()

    private fun post(endpoint: URI, query: String): ProfileHttpResult {
        val transport = transportFactory(endpoint)
        val cancellation = ProfileHttpCancellation()
        return try {
            ProfileHttpRequest(ValidatedNetworkUrl(endpoint), ByteArray(0), encodedQuery = query).use {
                transport.post(it, cancellation)
            }
        } catch (_: Exception) {
            ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
        }
    }

    private class CaibPreSignState(
        val postUrl: URI,
        val expectedDocumentId: String,
        private var xmlParameter: String?,
        private var certsParameter: String?,
        triData: ByteArray,
    ) : BatchPreSignState {
        private var triData: ByteArray? = triData
        fun postQuery(signature: LocalSignature): String {
            val root = parseXml(checkNotNull(triData)) ?: error("bad tri-data")
            val firma = root.getElementsByTagName("firma").item(0) as? Element ?: error("missing firma")
            val params = firma.childElements().associateBy { it.getAttribute("n") }.toMutableMap()
            val pk1 = root.ownerDocument.createElement("param").apply {
                setAttribute("n", "PK1")
                textContent = signature.withBytes { Base64.getEncoder().encodeToString(it) }
            }
            params["PK1"]?.let { firma.removeChild(it) }
            firma.appendChild(pk1)
            if (params["NEED_PRE"]?.textContent?.trim() != "true") params["PRE"]?.let { firma.removeChild(it) }
            val serialized = serializeTriData(root)
            return "xml=${checkNotNull(xmlParameter)}&certs=${checkNotNull(certsParameter)}&tridata=${urlBase64(serialized.encodeToByteArray())}"
        }
        override fun close() { triData?.fill(0); triData = null; xmlParameter = null; certsParameter = null }
    }

    private fun ProfileHttpResult.Failure.toSigningError(): SigningErrorCode = when (code) {
        ProfileHttpFailure.SESSION_EXPIRED -> SigningErrorCode.SESSION_EXPIRED
        else -> SigningErrorCode.PROTOCOL_FAILED
    }

    companion object {
        val ID = SigningProtocolId("caib-portafib-batch-v1")
        const val PROFILE_ID = "caib-portafib"
        private const val MAX_XML_BYTES = 2 * 1024 * 1024
        private const val MAX_PRE_BYTES = 256 * 1024
        private const val MAX_BATCH_XML_BASE64_CHARS = 8 * 1024
        private val REQUEST_TOKEN = Regex("[A-Za-z0-9_-]{28}")
        private val SIGNATURE_ID = Regex("[A-Za-z0-9_-]{40}")
        private val URL_BASE64 = Regex("[A-Za-z0-9_-]+={0,2}")
        private val FINAL_SUCCESS_RESULTS = setOf("DONE_AND_SAVED", "DONE_BUT_NOT_SAVED_YET")

        private fun encodeCertificateChain(chain: List<X509Certificate>): String =
            chain.joinToString(";") { urlBase64(it.encoded) }
        private fun urlBase64(bytes: ByteArray): String = Base64.getUrlEncoder().encodeToString(bytes)
        private fun String.padUrlBase64(): String = this + "=".repeat((4 - length % 4) % 4)
        private fun isValidFinalResult(bytes: ByteArray, expectedId: String): Boolean {
            if (bytes.isEmpty() || bytes.size > MAX_XML_BYTES) return false
            val root = parseXml(bytes) ?: return false
            if (root.tagName != "signresults" || root.attributes.length != 0) return false
            val children = root.childElements()
            if (children.size != 1 || children.single().tagName != "signresult") return false
            val result = children.single()
            if (result.childElements().isNotEmpty() || result.attributes.length != 2) return false
            if (result.getAttribute("id") != expectedId) return false
            return result.getAttribute("result") in FINAL_SUCCESS_RESULTS
        }
        private fun parseXml(bytes: ByteArray): Element? = runCatching {
            if (containsDoctype(bytes)) return@runCatching null
            val f = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { isXIncludeAware = false }
                runCatching { isExpandEntityReferences = false }
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            }
            ByteArrayInputStream(bytes).use { f.newDocumentBuilder().parse(it).documentElement }
        }.getOrNull()
        private fun containsDoctype(bytes: ByteArray): Boolean = runCatching {
            bytes.toString(Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)
        }.getOrDefault(true)
        private fun Element.childElements(): List<Element> = buildList {
            for (i in 0 until childNodes.length) (childNodes.item(i) as? Element)?.let(::add)
        }
        private fun serializeTriData(root: Element): String {
            val firmas = root.childElements().single { it.tagName == "firmas" }
            val format = firmas.getAttribute("format").takeIf { it.isNotEmpty() }
            val b = StringBuilder("<xml>\n <firmas")
            if (format != null) b.append(" format=\"").append(xmlEscape(format)).append("\"")
            b.append(">\n")
            for (firma in firmas.childElements()) {
                b.append("  <firma Id=\"").append(xmlEscape(firma.getAttribute("Id"))).append("\">\n")
                for (p in firma.childElements()) b.append("   <param n=\"").append(xmlEscape(p.getAttribute("n"))).append("\">").append(xmlEscape(p.textContent.trim())).append("</param>\n")
                b.append("  </firma>\n")
            }
            return b.append(" </firmas>\n</xml>").toString()
        }
        private fun xmlEscape(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
