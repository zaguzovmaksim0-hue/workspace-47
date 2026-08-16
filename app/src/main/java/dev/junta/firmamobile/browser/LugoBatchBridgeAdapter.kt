package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.LugoBatchUrlPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.Base64
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.w3c.dom.Element

/** Bounded bridge for Lugo's public clientSigner AutoScript.signBatch XML contract. */
class LugoBatchBridgeAdapter(
    private val activeProfileId: () -> ProfileId? = { null },
    private val currentNavigationEpoch: () -> Long? = { null },
    private val currentDocumentId: () -> UUID? = { null },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val urlPolicy: LugoBatchUrlPolicy = LugoBatchUrlPolicy(),
) {
    private var activeRequestId: UUID? = null
    private var activeDocumentId: UUID? = null

    @Synchronized
    fun route(rawMessage: String, sourceOrigin: Uri, isMainFrame: Boolean, navigationEpoch: Long = 0L): MelillaBatchBridgeRouteResult {
        if (rawMessage.length > MAX_MESSAGE_CHARS) {
            return MelillaBatchBridgeRouteResult.Rejected(null, SigningErrorCode.REQUEST_TOO_LARGE)
        }
        val streamedKeys = rawMessage.uniqueTopLevelKeys()
        val json = runCatching { JSONObject(rawMessage) }.getOrNull()
            ?: return MelillaBatchBridgeRouteResult.NotApplicable
        val type = json.optString("type")
        if (type != TYPE && type != CANCEL_TYPE) return MelillaBatchBridgeRouteResult.NotApplicable
        if (streamedKeys == null || json.keys().asSequence().toSet() != streamedKeys) {
            return rejected(json.optString("requestId").strictUuid(), SigningErrorCode.INVALID_REQUEST)
        }
        val requestId = json.optString("requestId").strictUuid()
        val documentId = json.optString("documentId").strictUuid()
        if (requestId == null || documentId == null) return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        if (!isMainFrame || navigationEpoch < 0 || currentNavigationEpoch()?.let { it != navigationEpoch } == true) {
            return rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        if (activeProfileId() != ProfileId(PROFILE_ID)) return rejected(requestId, SigningErrorCode.PROFILE_NOT_ACTIVE)
        if (!exactOrigin(sourceOrigin)) return rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED)
        if (currentOrigin()?.let { it != ORIGIN } == true || currentDocumentId()?.let { it != documentId } == true) {
            return rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        if (type == CANCEL_TYPE) {
            if (json.keys().asSequence().toSet() != setOf("type", "documentId", "requestId")) return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            val owned = activeRequestId == requestId && activeDocumentId == documentId
            activeRequestId = null; activeDocumentId = null
            return if (owned) MelillaBatchBridgeRouteResult.Cancelled(requestId, documentId)
            else rejected(requestId, SigningErrorCode.PROTOCOL_FAILED)
        }
        if (json.keys().asSequence().toSet() != REQUIRED_KEYS || activeRequestId != null) return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val pre = json.opt("batchPreSignerUrl") as? String ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val post = json.opt("batchPostSignerUrl") as? String ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val pair = urlPolicy.validatePair(pre, post) ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val extra = json.opt("extraProperties") as? String ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        if (extra != EXTRA_PROPERTIES) return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val batchB64 = json.opt("batchXml") as? String ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val document = parseBatch(batchB64) ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        activeRequestId = requestId; activeDocumentId = documentId
        return MelillaBatchBridgeRouteResult.Accepted(
            MelillaBatchBridgeRequest(
                requestId = requestId,
                documentId = documentId,
                batchPreSignerUrl = pre,
                batchPostSignerUrl = post,
                operationId = pair.first.sessionId,
                algorithm = ALGORITHM,
                format = FORMAT,
                suboperation = SUBOPERATION,
                stopOnError = true,
                documents = listOf(document),
                profileId = ProfileId(PROFILE_ID),
                sourceOrigin = ORIGIN,
                navigationEpoch = navigationEpoch,
            ),
        )
    }

    @Synchronized fun abandon(requestId: UUID? = null): Boolean {
        val active = activeRequestId ?: return false
        if (requestId != null && active != requestId) return false
        activeRequestId = null; activeDocumentId = null; return true
    }
    @Synchronized fun invalidateDocument(documentId: UUID?) { if (activeDocumentId == documentId) { activeRequestId = null; activeDocumentId = null } }
    @Synchronized fun abandonAll() { activeRequestId = null; activeDocumentId = null }

    private fun parseBatch(value: String): MelillaBatchDocument? = runCatching {
        if (!BASE64.matches(value) || value.length > MAX_BATCH_XML_BASE64_CHARS) return null
        val bytes = Base64.getDecoder().decode(value)
        if (bytes.isEmpty() || bytes.size > MAX_BATCH_XML_BYTES || containsDoctype(bytes)) {
            bytes.fill(0)
            return null
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val root = ByteArrayInputStream(bytes).use { factory.newDocumentBuilder().parse(it).documentElement }
        bytes.fill(0)
        if (root.tagName != "signbatch" || root.getAttribute("stoponerror") != "true" ||
            root.getAttribute("algorithm") != ALGORITHM || root.attributes.length != 2
        ) return null
        val signs = root.childElements()
        if (signs.size != 1 || signs[0].tagName != "singlesign") return null
        val sign = signs[0]
        if (sign.attributes.length != 1 || !sign.hasAttribute("Id")) return null
        val id = sign.getAttribute("Id").takeIf { SAFE_ID.matches(it) } ?: return null
        val children = sign.childElements()
        if (children.map { it.tagName } != listOf("datasource", "format", "suboperation", "extraparams", "signsaver")) return null
        val data = children[0].textContent.trim()
        val hash = runCatching { Base64.getDecoder().decode(data) }.getOrNull() ?: return null
        if (hash.size != 32) { hash.fill(0); return null }; hash.fill(0)
        if (children[1].textContent != FORMAT || children[2].textContent != SUBOPERATION) return null
        val decodedExtra = runCatching { String(Base64.getDecoder().decode(children[3].textContent), Charsets.UTF_8) }.getOrNull() ?: return null
        if (decodedExtra != EXTRA_PROPERTIES) return null
        val saver = children[4].childElements()
        if (saver.size != 2 || saver[0].tagName != "class" || saver[0].textContent != SIGN_SAVER ||
            saver[1].tagName != "config" || saver[1].textContent.isNotEmpty()
        ) return null
        MelillaBatchDocument(id = id, dataReference = data, format = FORMAT, suboperation = SUBOPERATION)
    }.getOrNull()

    private fun String.uniqueTopLevelKeys(): Set<String>? = try {
        JsonReader(StringReader(this)).use { reader ->
            reader.isLenient = false
            val names = linkedSetOf<String>()
            reader.beginObject()
            while (reader.hasNext()) {
                if (!names.add(reader.nextName())) return null
                reader.skipValue()
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) return null
            names
        }
    } catch (_: Exception) {
        null
    }

    private fun Element.childElements(): List<Element> = buildList {
        val nodes = childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let(::add)
    }
    private fun containsDoctype(bytes: ByteArray): Boolean = runCatching {
        bytes.toString(Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)
    }.getOrDefault(true)

    private fun String.strictUuid(): UUID? = takeIf { UUID_PATTERN.matches(it) }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private fun exactOrigin(uri: Uri) = uri.scheme == "https" && uri.host == "sede.deputacionlugo.org" && uri.port in setOf(-1,443) && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null && uri.encodedUserInfo == null
    private fun rejected(id: UUID?, code: SigningErrorCode) = MelillaBatchBridgeRouteResult.Rejected(id, code)

    companion object {
        const val PROFILE_ID = "diputacion-lugo-sede"
        const val SOURCE_ORIGIN = LugoBatchUrlPolicy.ORIGIN
        const val TYPE = "LUGO_XML_BATCH"
        const val CANCEL_TYPE = "LUGO_XML_BATCH_CANCEL"
        const val EXTRA_PROPERTIES = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\n"
        const val MAX_MESSAGE_CHARS = 786_432
        private const val ALGORITHM = "SHA256withRSA"
        private const val FORMAT = "CAdES"
        private const val SUBOPERATION = "sign"
        private const val SIGN_SAVER = "es.guadaltel.framework.clientsigner.servlet.batch.util.SignSaverFile"
        private const val MAX_BATCH_XML_BASE64_CHARS = 512 * 1024
        private const val MAX_BATCH_XML_BYTES = 384 * 1024
        private val ORIGIN = TrustedOrigin("https", "sede.deputacionlugo.org", 443)
        private val BASE64 = Regex("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?")
        private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,128}")
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
        private val REQUIRED_KEYS = setOf("type", "documentId", "requestId", "batchXml", "batchPreSignerUrl", "batchPostSignerUrl", "extraProperties")
    }
}
