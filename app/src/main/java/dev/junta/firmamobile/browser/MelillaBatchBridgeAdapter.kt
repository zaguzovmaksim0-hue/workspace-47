package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.MelillaBatchUrlError
import dev.junta.firmamobile.network.MelillaBatchUrlOperation
import dev.junta.firmamobile.network.MelillaBatchUrlPolicy
import dev.junta.firmamobile.network.MelillaBatchUrlValidation
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import java.io.StringReader
import java.util.UUID
import org.json.JSONObject

data class MelillaBatchDocument(
    val id: String,
    val dataReference: String,
    val format: String? = null,
    val suboperation: String? = null,
) {
    val datareference: String get() = dataReference
}

data class MelillaBatchBridgeRequest(
    val requestId: UUID,
    val documentId: UUID,
    val batchPreSignerUrl: String,
    val batchPostSignerUrl: String,
    val operationId: String,
    val algorithm: String,
    val format: String,
    val suboperation: String,
    val stopOnError: Boolean,
    val documents: List<MelillaBatchDocument>,
    val profileId: ProfileId,
    val sourceOrigin: TrustedOrigin,
    val navigationEpoch: Long,
) {
    val documentos: List<MelillaBatchDocument> get() = documents
}

typealias MelillaBatchRequest = MelillaBatchBridgeRequest

sealed interface MelillaBatchBridgeRouteResult {
    data object NotApplicable : MelillaBatchBridgeRouteResult

    data class Accepted(val request: MelillaBatchBridgeRequest) : MelillaBatchBridgeRouteResult

    data class Rejected(
        val requestId: UUID?,
        val code: SigningErrorCode,
    ) : MelillaBatchBridgeRouteResult
}

/**
 * Parses the portal-owned Melilla AutoFirma batch envelope.
 *
 * This adapter is intentionally separate from [MiniAppletBridgeAdapter]. The
 * latter remains a single-sign adapter and must keep returning NotApplicable
 * for MINIAPPLET_BATCH.
 */
class MelillaBatchBridgeAdapter(
    private val activeProfileId: () -> ProfileId? = { null },
    private val currentNavigationEpoch: () -> Long? = { null },
    private val currentDocumentId: () -> UUID? = { null },
    private val urlPolicy: MelillaBatchUrlPolicy = MelillaBatchUrlPolicy(),
) {
    private var activeRequestId: UUID? = null
    private val seenRequestIds = linkedSetOf<UUID>()

    @Synchronized
    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        navigationEpoch: Long = 0L,
    ): MelillaBatchBridgeRouteResult {
        if (rawMessage.length > MAX_MESSAGE_CHARS) {
            return MelillaBatchBridgeRouteResult.Rejected(
                requestId = null,
                code = SigningErrorCode.REQUEST_TOO_LARGE,
            )
        }

        val json = try {
            JSONObject(rawMessage)
        } catch (_: Exception) {
            return MelillaBatchBridgeRouteResult.NotApplicable
        }
        if (json.optString(TYPE_FIELD) != TYPE_MINIAPPLET_BATCH) {
            return MelillaBatchBridgeRouteResult.NotApplicable
        }

        val requestId = json.strictUuid(REQUEST_ID_FIELD)
        if (!hasStrictJsonKeys(rawMessage)) {
            return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
        val currentEpoch = currentNavigationEpoch()
        if (navigationEpoch < 0L || navigationEpoch == Long.MAX_VALUE ||
            currentEpoch?.let { it != navigationEpoch } == true
        ) {
            if (currentEpoch != null && currentEpoch != navigationEpoch) {
                activeRequestId = null
            }
            return rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        if (!isMainFrame) {
            return rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        if (activeProfileId() != MELILLA_PROFILE_ID) {
            return rejected(requestId, SigningErrorCode.PROFILE_NOT_ACTIVE)
        }
        if (!isExactMelillaOrigin(sourceOrigin)) {
            return rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED)
        }

        val canonicalRequestId = requestId
            ?: return rejected(null, SigningErrorCode.INVALID_REQUEST)
        val documentId = json.strictUuid(DOCUMENT_ID_FIELD)
            ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        if (currentDocumentId()?.let { it != documentId } == true) {
            return rejected(canonicalRequestId, SigningErrorCode.NAVIGATION_CHANGED)
        }

        val keys = json.keySet()
        if (!keys.containsAll(REQUIRED_KEYS) || keys.any { it !in ALLOWED_KEYS }) {
            return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        }
        val algorithm = json.optionalString(ALGORITHM_FIELD, DEFAULT_ALGORITHM)
            ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        val format = json.optionalString(FORMAT_FIELD, DEFAULT_FORMAT)
            ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        val suboperation = json.optionalString(SUBOPERATION_FIELD, DEFAULT_SUBOPERATION)
            ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        if (algorithm != DEFAULT_ALGORITHM || format !in SUPPORTED_FORMATS ||
            suboperation != DEFAULT_SUBOPERATION
        ) {
            return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        }
        if (json.optBoolean(STOP_ON_ERROR_FIELD, false) ||
            json.has(STOP_ON_ERROR_FIELD) && json.opt(STOP_ON_ERROR_FIELD) !is Boolean
        ) {
            return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        }

        val preSignerUrl = json.strictString(PRE_SIGNER_URL_FIELD)
            ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        val postSignerUrl = json.strictString(POST_SIGNER_URL_FIELD)
            ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        val preSigner = when (
            val result = urlPolicy.validate(
                preSignerUrl,
                expectedOperation = MelillaBatchUrlOperation.PRESIGN,
                expectedOperationId = null,
            )
        ) {
            is MelillaBatchUrlValidation.Allowed -> result
            is MelillaBatchUrlValidation.Rejected ->
                return rejected(canonicalRequestId, result.error.signingErrorCode())
        }
        val operationId = preSigner.binding.operationId
        val postSigner = when (
            val result = urlPolicy.validate(
                postSignerUrl,
                expectedOperation = MelillaBatchUrlOperation.POSTSIGN,
                expectedOperationId = operationId,
            )
        ) {
            is MelillaBatchUrlValidation.Allowed -> result
            is MelillaBatchUrlValidation.Rejected ->
                return rejected(canonicalRequestId, result.error.signingErrorCode())
        }

        val documentsJson = json.optJSONArray(DOCUMENTS_FIELD)
            ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        if (documentsJson.length() == 0 || documentsJson.length() > MAX_DOCUMENTS) {
            return rejected(canonicalRequestId, SigningErrorCode.REQUEST_TOO_LARGE)
        }
        val documents = ArrayList<MelillaBatchDocument>(documentsJson.length())
        val documentIds = linkedSetOf<String>()
        for (index in 0 until documentsJson.length()) {
            val document = documentsJson.optJSONObject(index)
                ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            if (!hasStrictDocumentKeys(document)) {
                return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
            val documentKeys = document.keySet()
            if (!documentKeys.containsAll(DOCUMENT_REQUIRED_KEYS) ||
                documentKeys.any { it !in DOCUMENT_ALLOWED_KEYS }
            ) {
                return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
            val documentExternalId = document.strictString(DOCUMENT_EXTERNAL_ID_FIELD)
                ?.takeIf(::isSafeOpaqueValue)
                ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            if (!documentIds.add(documentExternalId)) {
                return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
            val documentFormat = if (!document.has(DOCUMENT_FORMAT_FIELD)) {
                null
            } else {
                document.strictString(DOCUMENT_FORMAT_FIELD)
                    ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
            if (documentFormat != null && documentFormat !in SUPPORTED_FORMATS) {
                return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
            val documentSuboperation = if (!document.has(DOCUMENT_SUBOPERATION_FIELD)) {
                null
            } else {
                document.strictString(DOCUMENT_SUBOPERATION_FIELD)
                    ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
            if (documentSuboperation != null && documentSuboperation != DEFAULT_SUBOPERATION) {
                return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
            val dataReference = document.strictString(DATA_REFERENCE_FIELD)
                ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            when (
                val result = urlPolicy.validate(
                    dataReference,
                    expectedOperation = MelillaBatchUrlOperation.GETDATA,
                    expectedOperationId = operationId,
                    expectedDocumentId = documentExternalId,
                )
            ) {
                is MelillaBatchUrlValidation.Allowed -> Unit
                is MelillaBatchUrlValidation.Rejected ->
                    return rejected(canonicalRequestId, result.error.signingErrorCode())
            }
            documents += MelillaBatchDocument(
                id = documentExternalId,
                dataReference = dataReference,
                format = documentFormat,
                suboperation = documentSuboperation,
            )
        }

        if (activeRequestId != null || canonicalRequestId in seenRequestIds) {
            return rejected(canonicalRequestId, SigningErrorCode.PROTOCOL_FAILED)
        }
        activeRequestId = canonicalRequestId
        seenRequestIds += canonicalRequestId
        while (seenRequestIds.size > MAX_SEEN_REQUESTS) {
            seenRequestIds.iterator().next().let(seenRequestIds::remove)
        }
        return MelillaBatchBridgeRouteResult.Accepted(
            request = MelillaBatchBridgeRequest(
                requestId = canonicalRequestId,
                documentId = documentId,
                batchPreSignerUrl = preSignerUrl,
                batchPostSignerUrl = postSignerUrl,
                operationId = operationId,
                algorithm = algorithm,
                format = format,
                suboperation = suboperation,
                stopOnError = false,
                documents = documents.toList(),
                profileId = MELILLA_PROFILE_ID,
                sourceOrigin = TrustedOrigin(HTTPS_SCHEME, MELILLA_HOST, HTTPS_PORT),
                navigationEpoch = navigationEpoch,
            ),
        )
    }

    @Synchronized
    fun abandon(requestId: UUID? = null): Boolean {
        val active = activeRequestId ?: return false
        if (requestId != null && requestId != active) return false
        activeRequestId = null
        return true
    }

    @Synchronized
    fun abandonAll() {
        activeRequestId = null
    }

    private fun rejected(
        requestId: UUID?,
        code: SigningErrorCode,
    ): MelillaBatchBridgeRouteResult = MelillaBatchBridgeRouteResult.Rejected(requestId, code)

    private fun isExactMelillaOrigin(uri: Uri): Boolean =
        uri.scheme == HTTPS_SCHEME &&
            uri.host == MELILLA_HOST &&
            uri.port in setOf(-1, HTTPS_PORT) &&
            uri.encodedUserInfo == null &&
            uri.path.isNullOrEmpty() &&
            uri.query == null &&
            uri.fragment == null

    private fun hasStrictJsonKeys(raw: String): Boolean = try {
        JsonReader(StringReader(raw)).use { reader ->
            reader.isLenient = false
            readJsonValue(reader, 0)
            reader.peek() == JsonToken.END_DOCUMENT
        }
    } catch (_: Exception) {
        false
    }

    private fun readJsonValue(reader: JsonReader, depth: Int) {
        require(depth <= MAX_JSON_DEPTH)
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val names = linkedSetOf<String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    require(names.add(reader.nextName()))
                    readJsonValue(reader, depth + 1)
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) readJsonValue(reader, depth + 1)
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> reader.nextNull()
            else -> error("unexpected JSON token")
        }
    }

    private fun hasStrictDocumentKeys(document: JSONObject): Boolean =
        document.keySet().all { it in DOCUMENT_ALLOWED_KEYS }

    private fun JSONObject.strictString(name: String): String? = opt(name) as? String

    private fun JSONObject.optionalString(name: String, default: String? = null): String? {
        if (!has(name)) return default
        return opt(name) as? String
    }

    private fun JSONObject.strictUuid(name: String): UUID? {
        val raw = strictString(name) ?: return null
        if (!UUID_PATTERN.matches(raw)) return null
        return runCatching { UUID.fromString(raw) }
            .getOrNull()
            ?.takeIf { it.toString() == raw }
    }

    private fun JSONObject.keySet(): Set<String> = buildSet {
        val keys = keys()
        while (keys.hasNext()) add(keys.next())
    }

    private fun isSafeOpaqueValue(value: String): Boolean =
        value.isNotEmpty() &&
            value.length <= MAX_OPAQUE_VALUE_CHARS &&
            value.all { !it.isISOControl() && !it.isWhitespace() } &&
            value.none { it == '&' || it == '=' || it == '#' || it == '?' }

    private fun MelillaBatchUrlError.signingErrorCode(): SigningErrorCode = when (this) {
        MelillaBatchUrlError.ORIGIN_NOT_ALLOWED -> SigningErrorCode.ORIGIN_NOT_ALLOWED
        MelillaBatchUrlError.TOO_LONG -> SigningErrorCode.REQUEST_TOO_LARGE
        else -> SigningErrorCode.INVALID_REQUEST
    }

    companion object {
        const val PROFILE_ID = "melilla-sede"
        const val SOURCE_ORIGIN = "https://sede.melilla.es"
        const val TYPE = "MINIAPPLET_BATCH"
        const val MAX_MESSAGE_CHARS = 786_432
        const val MAX_DOCUMENTS = 128

        private const val TYPE_FIELD = "type"
        private const val DOCUMENT_ID_FIELD = "documentId"
        private const val REQUEST_ID_FIELD = "requestId"
        private const val PRE_SIGNER_URL_FIELD = "batchPreSignerUrl"
        private const val POST_SIGNER_URL_FIELD = "batchPostSignerUrl"
        private const val ALGORITHM_FIELD = "algorithm"
        private const val FORMAT_FIELD = "format"
        private const val SUBOPERATION_FIELD = "suboperation"
        private const val STOP_ON_ERROR_FIELD = "stopOnError"
        private const val DOCUMENTS_FIELD = "documentos"

        private const val DOCUMENT_EXTERNAL_ID_FIELD = "id"
        private const val DATA_REFERENCE_FIELD = "datareference"
        private const val DOCUMENT_FORMAT_FIELD = "format"
        private const val DOCUMENT_SUBOPERATION_FIELD = "suboperation"

        private const val DEFAULT_ALGORITHM = "SHA256withRSA"
        private const val DEFAULT_FORMAT = "CAdES"
        private const val DEFAULT_SUBOPERATION = "sign"
        private const val MELILLA_HOST = "sede.melilla.es"
        private const val HTTPS_SCHEME = "https"
        private const val HTTPS_PORT = 443
        private const val MAX_OPAQUE_VALUE_CHARS = 1_024
        private const val MAX_JSON_DEPTH = 16
        private const val MAX_SEEN_REQUESTS = 64

        private val MELILLA_PROFILE_ID = ProfileId(PROFILE_ID)
        private val SUPPORTED_FORMATS = setOf("CAdES", "PAdES", "XAdES")
        private val REQUIRED_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
            PRE_SIGNER_URL_FIELD,
            POST_SIGNER_URL_FIELD,
            DOCUMENTS_FIELD,
        )
        private val ALLOWED_KEYS = REQUIRED_KEYS + setOf(
            ALGORITHM_FIELD,
            FORMAT_FIELD,
            SUBOPERATION_FIELD,
            STOP_ON_ERROR_FIELD,
        )
        private val DOCUMENT_REQUIRED_KEYS = setOf(
            DOCUMENT_EXTERNAL_ID_FIELD,
            DATA_REFERENCE_FIELD,
        )
        private val DOCUMENT_ALLOWED_KEYS = DOCUMENT_REQUIRED_KEYS + setOf(
            DOCUMENT_FORMAT_FIELD,
            DOCUMENT_SUBOPERATION_FIELD,
        )
        private val UUID_PATTERN = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )
    }
}
