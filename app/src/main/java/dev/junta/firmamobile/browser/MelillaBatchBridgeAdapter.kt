package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.ExtremaduraBatchUrlPolicy
import dev.junta.firmamobile.network.MelillaBatchUrlError
import dev.junta.firmamobile.network.MelillaBatchUrlOperation
import dev.junta.firmamobile.network.MelillaBatchUrlPolicy
import dev.junta.firmamobile.network.MelillaBatchUrlValidation
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import java.io.StringReader
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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

    data class Cancelled(
        val requestId: UUID,
        val documentId: UUID,
    ) : MelillaBatchBridgeRouteResult

    data class Rejected(
        val requestId: UUID?,
        val code: SigningErrorCode,
    ) : MelillaBatchBridgeRouteResult
}

/** Parses the portal-owned Melilla AutoFirma batch envelope. */
class MelillaBatchBridgeAdapter(
    activeProfileId: () -> ProfileId? = { null },
    currentNavigationEpoch: () -> Long? = { null },
    currentDocumentId: () -> UUID? = { null },
    urlPolicy: MelillaBatchUrlPolicy = MelillaBatchUrlPolicy(),
    currentOrigin: () -> TrustedOrigin? = { null },
) {
    private val delegate = StaBatchBridgeAdapter(
        activeProfileId = activeProfileId,
        currentNavigationEpoch = currentNavigationEpoch,
        currentDocumentId = currentDocumentId,
        currentOrigin = currentOrigin,
        contract = StaBatchBridgeContract(
            profileId = ProfileId(PROFILE_ID),
            origin = TrustedOrigin("https", "sede.melilla.es", 443),
            urlValidator = StaBatchBridgeUrlValidator { rawUrl, operation, operationId, documentId ->
                urlPolicy.validate(rawUrl, operation, operationId, documentId)
            },
        ),
    )

    fun route(rawMessage: String, sourceOrigin: Uri, isMainFrame: Boolean, navigationEpoch: Long = 0L) =
        delegate.route(rawMessage, sourceOrigin, isMainFrame, navigationEpoch)
    fun abandon(requestId: UUID? = null): Boolean = delegate.abandon(requestId)
    fun invalidateDocument(documentId: UUID?) = delegate.invalidateDocument(documentId)
    fun abandonAll() = delegate.abandonAll()

    companion object {
        const val PROFILE_ID = "melilla-sede"
        const val SOURCE_ORIGIN = "https://sede.melilla.es"
        const val TYPE = "MINIAPPLET_BATCH"
        const val BATCH_RESULT_TYPE = "MINIAPPLET_BATCH_RESULT"
        const val MAX_MESSAGE_CHARS = 786_432
        const val MAX_DOCUMENTS = 128
    }
}

/** Fixed-profile Extremadura wrapper over the shared observed STA batch envelope. */
class ExtremaduraBatchBridgeAdapter(
    activeProfileId: () -> ProfileId? = { null },
    currentNavigationEpoch: () -> Long? = { null },
    currentDocumentId: () -> UUID? = { null },
    urlPolicy: ExtremaduraBatchUrlPolicy = ExtremaduraBatchUrlPolicy(),
    currentOrigin: () -> TrustedOrigin? = { null },
) {
    private val delegate = StaBatchBridgeAdapter(
        activeProfileId = activeProfileId,
        currentNavigationEpoch = currentNavigationEpoch,
        currentDocumentId = currentDocumentId,
        currentOrigin = currentOrigin,
        contract = StaBatchBridgeContract(
            profileId = ProfileId(PROFILE_ID),
            origin = TrustedOrigin("https", "tramites.juntaex.es", 443),
            urlValidator = StaBatchBridgeUrlValidator { rawUrl, operation, operationId, documentId ->
                urlPolicy.validate(rawUrl, operation, operationId, documentId)
            },
        ),
    )

    fun route(rawMessage: String, sourceOrigin: Uri, isMainFrame: Boolean, navigationEpoch: Long = 0L) =
        delegate.route(rawMessage, sourceOrigin, isMainFrame, navigationEpoch)
    fun abandon(requestId: UUID? = null): Boolean = delegate.abandon(requestId)
    fun invalidateDocument(documentId: UUID?) = delegate.invalidateDocument(documentId)
    fun abandonAll() = delegate.abandonAll()

    companion object {
        const val PROFILE_ID = "extremadura-tramites"
        const val SOURCE_ORIGIN = "https://tramites.juntaex.es"
        const val TYPE = "MINIAPPLET_BATCH"
        const val BATCH_RESULT_TYPE = "MINIAPPLET_BATCH_RESULT"
        const val MAX_MESSAGE_CHARS = 786_432
        const val MAX_DOCUMENTS = 128
    }
}

private data class StaBatchBridgeContract(
    val profileId: ProfileId,
    val origin: TrustedOrigin,
    val urlValidator: StaBatchBridgeUrlValidator,
)

private fun interface StaBatchBridgeUrlValidator {
    fun validate(
        rawUrl: String,
        expectedOperation: MelillaBatchUrlOperation,
        expectedOperationId: String?,
        expectedDocumentId: String?,
    ): MelillaBatchUrlValidation
}

private class StaBatchBridgeAdapter(
    private val activeProfileId: () -> ProfileId?,
    private val currentNavigationEpoch: () -> Long?,
    private val currentDocumentId: () -> UUID?,
    private val currentOrigin: () -> TrustedOrigin?,
    private val contract: StaBatchBridgeContract,
) {
    private var activeRequestId: UUID? = null
    private var activeDocumentId: UUID? = null
    private var lastNavigationEpoch: Long? = null
    private val invalidatedDocumentIds = linkedSetOf<UUID>()
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
        val messageType = json.optString(TYPE_FIELD)
        if (messageType != TYPE && messageType != BATCH_CANCEL_TYPE) {
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
            invalidateActiveDocument()
            return rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        if (lastNavigationEpoch?.let { it != navigationEpoch } == true) {
            invalidateActiveDocument()
        }
        lastNavigationEpoch = navigationEpoch
        if (!isMainFrame) {
            return rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        if (activeProfileId() != contract.profileId) {
            return rejected(requestId, SigningErrorCode.PROFILE_NOT_ACTIVE)
        }
        if (!isExactContractOrigin(sourceOrigin)) {
            return rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED)
        }

        val canonicalRequestId = requestId
            ?: return rejected(null, SigningErrorCode.INVALID_REQUEST)
        val documentId = json.strictUuid(DOCUMENT_ID_FIELD)
            ?: return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        if (currentOrigin()?.let { it != contract.origin } == true) {
            return rejected(canonicalRequestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        if (currentDocumentId()?.let { it != documentId } == true ||
            documentId in invalidatedDocumentIds
        ) {
            return rejected(canonicalRequestId, SigningErrorCode.NAVIGATION_CHANGED)
        }

        if (messageType == BATCH_CANCEL_TYPE) {
            if (json.keySet() != BATCH_CANCEL_KEYS) {
                return rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
            invalidateDocument(documentId)
            return MelillaBatchBridgeRouteResult.Cancelled(canonicalRequestId, documentId)
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
            val result = contract.urlValidator.validate(
                preSignerUrl,
                MelillaBatchUrlOperation.PRESIGN,
                null,
                null,
            )
        ) {
            is MelillaBatchUrlValidation.Allowed -> result
            is MelillaBatchUrlValidation.Rejected ->
                return rejected(canonicalRequestId, result.error.signingErrorCode())
        }
        val operationId = preSigner.binding.operationId
        val postSigner = when (
            val result = contract.urlValidator.validate(
                postSignerUrl,
                MelillaBatchUrlOperation.POSTSIGN,
                operationId,
                null,
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
                val result = contract.urlValidator.validate(
                    dataReference,
                    MelillaBatchUrlOperation.GETDATA,
                    operationId,
                    documentExternalId,
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
        activeDocumentId = documentId
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
                profileId = contract.profileId,
                sourceOrigin = contract.origin,
                navigationEpoch = navigationEpoch,
            ),
        )
    }

    @Synchronized
    fun abandon(requestId: UUID? = null): Boolean {
        val active = activeRequestId ?: return false
        if (requestId != null && requestId != active) return false
        activeRequestId = null
        activeDocumentId = null
        return true
    }

    @Synchronized
    fun invalidateDocument(documentId: UUID?) {
        if (documentId == null) return
        invalidatedDocumentIds += documentId
        while (invalidatedDocumentIds.size > MAX_INVALIDATED_DOCUMENTS) {
            invalidatedDocumentIds.iterator().next().let(invalidatedDocumentIds::remove)
        }
        if (activeDocumentId == documentId) {
            activeRequestId = null
            activeDocumentId = null
        }
    }

    @Synchronized
    fun abandonAll() {
        invalidateActiveDocument()
    }

    private fun invalidateActiveDocument() {
        activeDocumentId?.let { documentId ->
            invalidatedDocumentIds += documentId
            while (invalidatedDocumentIds.size > MAX_INVALIDATED_DOCUMENTS) {
                invalidatedDocumentIds.iterator().next().let(invalidatedDocumentIds::remove)
            }
        }
        activeRequestId = null
        activeDocumentId = null
    }

    private fun rejected(
        requestId: UUID?,
        code: SigningErrorCode,
    ): MelillaBatchBridgeRouteResult = MelillaBatchBridgeRouteResult.Rejected(requestId, code)

    private fun isExactContractOrigin(uri: Uri): Boolean =
        uri.scheme == contract.origin.scheme &&
            uri.host == contract.origin.host &&
            uri.port in setOf(-1, contract.origin.port) &&
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
        const val TYPE = "MINIAPPLET_BATCH"
        const val BATCH_RESULT_TYPE = "MINIAPPLET_BATCH_RESULT"
        const val MAX_MESSAGE_CHARS = 786_432
        const val MAX_DOCUMENTS = 128

        private const val TYPE_FIELD = "type"
        private const val BATCH_CANCEL_TYPE = "MINIAPPLET_BATCH_CANCEL"
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
        private const val MAX_OPAQUE_VALUE_CHARS = 1_024
        private const val MAX_JSON_DEPTH = 16
        private const val MAX_SEEN_REQUESTS = 64
        private const val MAX_INVALIDATED_DOCUMENTS = 64

        private val SUPPORTED_FORMATS = setOf("CAdES", "PAdES", "XAdES")
        private val BATCH_CANCEL_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
        )
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


class MelillaBatchReplyChannel internal constructor(
    val requestId: UUID,
    private val postMessage: (String) -> Unit,
    private val onTerminal: () -> Unit = {},
    private val canDeliver: () -> Boolean = { true },
) {
    private val terminal = AtomicBoolean(false)

    fun success(validationResponse: String): Boolean {
        if (!terminal.compareAndSet(false, true)) return false
        if (!runCatching(canDeliver).getOrDefault(false) ||
            validationResponse.length > MelillaBatchBridgeAdapter.MAX_MESSAGE_CHARS ||
            !isStrictJsonValue(validationResponse)
        ) {
            onTerminal()
            return false
        }
        return try {
            val message = JSONObject()
                .put("type", MelillaBatchBridgeAdapter.BATCH_RESULT_TYPE)
                .put("requestId", requestId.toString())
                .put("status", "success")
                .put("validationResponse", validationResponse)
                .toString()
            if (message.length > MelillaBatchBridgeAdapter.MAX_MESSAGE_CHARS) {
                return false
            }
            postMessage(message)
            true
        } catch (_: Exception) {
            false
        } finally {
            onTerminal()
        }
    }

    fun failure(code: SigningErrorCode): Boolean {
        if (!terminal.compareAndSet(false, true)) return false
        if (!runCatching(canDeliver).getOrDefault(false)) {
            onTerminal()
            return false
        }
        return try {
            postMessage(
                JSONObject()
                    .put("type", MelillaBatchBridgeAdapter.BATCH_RESULT_TYPE)
                    .put("requestId", requestId.toString())
                    .put("status", "error")
                    .put("errorCode", code.name)
                    .toString(),
            )
            true
        } catch (_: Exception) {
            false
        } finally {
            onTerminal()
        }
    }

    fun abandon(): Boolean {
        if (!terminal.compareAndSet(false, true)) return false
        onTerminal()
        return true
    }

    private fun isStrictJsonValue(raw: String): Boolean = try {
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

    private companion object {
        const val MAX_JSON_DEPTH = 16
    }
}
