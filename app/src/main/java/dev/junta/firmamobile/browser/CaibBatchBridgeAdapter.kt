package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.CaibBatchUrlPolicy
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

/** Bounded bridge for CAIB PortaFIB MiniApplet.signBatch PAdES contract. */
class CaibBatchBridgeAdapter(
    private val activeProfileId: () -> ProfileId? = { null },
    private val currentNavigationEpoch: () -> Long? = { null },
    private val currentDocumentId: () -> UUID? = { null },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val urlPolicy: CaibBatchUrlPolicy = CaibBatchUrlPolicy(),
) {
    private var activeRequestId: UUID? = null
    private var activeDocumentId: UUID? = null

    @Synchronized
    fun route(rawMessage: String, sourceOrigin: Uri, isMainFrame: Boolean, navigationEpoch: Long = 0L): MelillaBatchBridgeRouteResult {
        if (rawMessage.length > MAX_MESSAGE_CHARS) return MelillaBatchBridgeRouteResult.Rejected(null, SigningErrorCode.REQUEST_TOO_LARGE)
        val streamedKeys = rawMessage.uniqueTopLevelKeys()
        val json = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return MelillaBatchBridgeRouteResult.NotApplicable
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
        val batchB64 = json.opt("batchXml") as? String ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val parsed = parseBatch(batchB64, pair.first.requestToken) ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val extra = json.opt("extraProperties") as? String ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        if (extra != extraProperties(parsed.id)) return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        activeRequestId = requestId; activeDocumentId = documentId
        return MelillaBatchBridgeRouteResult.Accepted(
            MelillaBatchBridgeRequest(
                requestId = requestId,
                documentId = documentId,
                batchPreSignerUrl = pre,
                batchPostSignerUrl = post,
                operationId = pair.first.requestToken,
                algorithm = ALGORITHM,
                format = FORMAT,
                suboperation = SUBOPERATION,
                stopOnError = false,
                documents = listOf(parsed),
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

    private fun parseBatch(value: String, requestToken: String): MelillaBatchDocument? = runCatching {
        if (!URL_BASE64.matches(value) || value.length > MAX_BATCH_XML_BASE64_CHARS) return null
        val bytes = Base64.getUrlDecoder().decode(value.padUrlBase64())
        if (bytes.isEmpty() || bytes.size > MAX_BATCH_XML_BYTES || containsDoctype(bytes)) { bytes.fill(0); return null }
        val root = ByteArrayInputStream(bytes).use { secureFactory().newDocumentBuilder().parse(it).documentElement }
        bytes.fill(0)
        if (root.tagName != "signbatch" || root.getAttribute("stoponerror") != "false" ||
            root.getAttribute("algorithm") != ALGORITHM || root.attributes.length != 2
        ) return null
        val signs = root.childElements()
        if (signs.size != 1 || signs[0].tagName != "singlesign") return null
        val sign = signs[0]
        if (sign.attributes.length != 1 || !sign.hasAttribute("Id")) return null
        val id = sign.getAttribute("Id").takeIf { SIGNATURE_ID.matches(it) } ?: return null
        val decodedId = runCatching { String(Base64.getUrlDecoder().decode(id.padUrlBase64()), Charsets.UTF_8) }.getOrNull() ?: return null
        if (decodedId != "$requestToken|0") return null
        val children = sign.childElements()
        if (children.map { it.tagName } != listOf("datasource", "format", "suboperation", "extraparams", "signsaver")) return null
        if (children[0].textContent != dataSource(requestToken) || children[1].textContent != FORMAT || children[2].textContent != SUBOPERATION) return null
        val decodedExtra = decodeUrlBase64Utf8(children[3].textContent) ?: return null
        if (decodedExtra != extraProperties(id)) return null
        val saver = children[4].childElements()
        if (saver.size != 2 || saver[0].tagName != "class" || saver[0].textContent != SIGN_SAVER || saver[1].tagName != "config") return null
        val decodedConfig = decodeUrlBase64Utf8(saver[1].textContent) ?: return null
        if (!SAVER_CONFIG.matches(decodedConfig)) return null
        MelillaBatchDocument(id = id, dataReference = value, format = FORMAT, suboperation = SUBOPERATION)
    }.getOrNull()

    private fun secureFactory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }
    private fun decodeUrlBase64Utf8(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value.padUrlBase64()), Charsets.UTF_8)
    }.getOrNull()
    private fun String.padUrlBase64(): String = this + "=".repeat((4 - length % 4) % 4)
    private fun dataSource(token: String) = "file:///app/caib/portafib/files/PASSARELADEFIRMAWEB/$token/FORM-1-1/adaptat"

    private fun String.uniqueTopLevelKeys(): Set<String>? = try {
        JsonReader(StringReader(this)).use { reader ->
            reader.isLenient = false
            val names = linkedSetOf<String>(); reader.beginObject()
            while (reader.hasNext()) { if (!names.add(reader.nextName())) return null; reader.skipValue() }
            reader.endObject(); if (reader.peek() != JsonToken.END_DOCUMENT) return null; names
        }
    } catch (_: Exception) { null }
    private fun Element.childElements(): List<Element> = buildList { for (i in 0 until childNodes.length) (childNodes.item(i) as? Element)?.let(::add) }
    private fun containsDoctype(bytes: ByteArray): Boolean = runCatching { bytes.toString(Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true) }.getOrDefault(true)
    private fun String.strictUuid(): UUID? = takeIf { UUID_PATTERN.matches(it) }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private fun exactOrigin(uri: Uri) = uri.scheme == "https" && uri.host == "intranet.caib.es" && uri.port in setOf(-1, 443) && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null && uri.encodedUserInfo == null
    private fun rejected(id: UUID?, code: SigningErrorCode) = MelillaBatchBridgeRouteResult.Rejected(id, code)

    companion object {
        const val PROFILE_ID = "caib-portafib"
        const val SOURCE_ORIGIN = CaibBatchUrlPolicy.ORIGIN
        const val TYPE = "CAIB_XML_BATCH"
        const val CANCEL_TYPE = "CAIB_XML_BATCH_CANCEL"
        const val START_URL = "https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?tramite=CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1&idioma=es&servicioCatalogo=false&idTramiteCatalogo=4213963&parametros="
        const val MAX_MESSAGE_CHARS = 786_432
        const val ALGORITHM = "SHA256withRSA"
        const val FORMAT = "PAdES"
        const val SUBOPERATION = "sign"
        private const val SIGN_SAVER = "org.fundaciobit.pluginsib.signatureweb.afirmatriphaseserver.signsaver.SignSaverFile"
        private const val MAX_BATCH_XML_BASE64_CHARS = 8 * 1024
        private const val MAX_BATCH_XML_BYTES = 6 * 1024
        private val ORIGIN = TrustedOrigin("https", "intranet.caib.es", 443)
        private val URL_BASE64 = Regex("[A-Za-z0-9_-]+={0,2}")
        private val SIGNATURE_ID = Regex("[A-Za-z0-9_-]{40}")
        private val SAVER_CONFIG = Regex("FileName=/tmp/PluginAutofirmaBatch[0-9]+\.bin\ndebug=false")
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
        private val REQUIRED_KEYS = setOf("type", "documentId", "requestId", "batchXml", "batchPreSignerUrl", "batchPostSignerUrl", "extraProperties")

        fun extraProperties(signatureId: String): String =
            "mode=implicit\n" +
                "signatureSubFilter=ETSI.CAdES.detached\n" +
                "SignatureId=$signatureId\n" +
                "signReason=FORM-1.pdf\n" +
                "formatmobile=PAdEStri\n" +
                "formatbatch=PAdES\n" +
                "format=PAdES\n" +
                "filters.1=nonexpired:\n" +
                "allowSigningCertifiedPdfs=true\n" +
                "algorithm=SHA256withRSA\n"
    }
}
