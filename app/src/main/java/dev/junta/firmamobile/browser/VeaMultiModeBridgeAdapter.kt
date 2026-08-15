package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.BoundedReplayLedger
import dev.junta.firmamobile.security.MonotonicSecurityTime
import dev.junta.firmamobile.signing.PrecalculatedHashAlgorithm
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningErrorCode
import java.io.StringReader
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

data class VeaMultiModeBridgeRequest(
    val requestId: UUID,
    val documentId: UUID,
    val operationArray: List<String>,
    val dataArray: List<String>,
    val originalDataArray: List<String>?,
    val arrayLength: Int,
    val algorithm: String,
    val format: String,
    val extraProperties: String,
    val hashAlgorithm: PrecalculatedHashAlgorithm,
    val hashes: List<ByteArray>,
    val profileId: ProfileId,
    val sourceOrigin: TrustedOrigin,
    val navigationEpoch: Long,
    val pageUrl: String,
)

sealed interface VeaMultiModeBridgeRouteResult {
    data object NotApplicable : VeaMultiModeBridgeRouteResult

    data class Accepted(val request: VeaMultiModeBridgeRequest) : VeaMultiModeBridgeRouteResult

    data class Cancelled(
        val requestId: UUID,
        val documentId: UUID,
    ) : VeaMultiModeBridgeRouteResult

    data class Rejected(
        val requestId: UUID?,
        val code: SigningErrorCode,
    ) : VeaMultiModeBridgeRouteResult
}

class VeaMultiModeBridgeAdapter(
    private val activeProfileId: () -> ProfileId? = { null },
    private val currentNavigationEpoch: () -> Long? = { null },
    private val currentDocumentId: () -> UUID? = { null },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val currentUrl: () -> String? = { null },
    monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) {
    private var activeRequestId: UUID? = null
    private var activeDocumentId: UUID? = null
    private var lastNavigationEpoch: Long? = null
    private val invalidatedDocumentIds = linkedSetOf<UUID>()
    private val replayLedger = BoundedReplayLedger<UUID>(
        monotonicNanos = monotonicNanos,
        retention = Duration.ofMinutes(5),
        maxEntries = 64,
    )

    @Synchronized
    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        navigationEpoch: Long = 0L,
    ): VeaMultiModeBridgeRouteResult {
        if (navigationEpoch < 0L || navigationEpoch == Long.MAX_VALUE) {
            return VeaMultiModeBridgeRouteResult.NotApplicable
        }
        if (rawMessage.length > MAX_MESSAGE_CHARS) {
            return VeaMultiModeBridgeRouteResult.NotApplicable
        }
        val streamedKeys = rawMessage.uniqueTopLevelKeys()
        val json = try {
            JSONObject(rawMessage)
        } catch (_: Exception) {
            return VeaMultiModeBridgeRouteResult.NotApplicable
        }
        val messageType = json.optString("type")
        if (messageType != TYPE && messageType != CANCEL_TYPE) {
            return VeaMultiModeBridgeRouteResult.NotApplicable
        }
        val requestId = json.strictUuid("requestId")
        val documentId = json.strictUuid("documentId")
        if (requestId == null || documentId == null) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val currentEpoch = currentNavigationEpoch()
        if (currentEpoch == null || currentEpoch != navigationEpoch) {
            invalidateActiveDocument()
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        if (lastNavigationEpoch?.let { it != navigationEpoch } == true) {
            invalidateActiveDocument()
        }
        lastNavigationEpoch = navigationEpoch

        if (!isMainFrame) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        val activeProfile = activeProfileId()
        if (activeProfile?.value != PROFILE_ID) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.PROFILE_NOT_ACTIVE)
        }
        val originString = sourceOrigin.toString().trimEnd('/')
        if (originString != SOURCE_ORIGIN) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED)
        }
        val currentDocId = currentDocumentId()
        if (currentDocId == null || currentDocId != documentId || documentId in invalidatedDocumentIds) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        val expectedOrigin = currentOrigin()
        if (expectedOrigin == null || expectedOrigin.scheme != "https" || expectedOrigin.host != "veaja.cloud.juntadeandalucia.es" || expectedOrigin.port != 443) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED)
        }

        if (messageType == CANCEL_TYPE) {
            if (streamedKeys != CANCEL_KEYS) {
                return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            }
            if (activeRequestId == requestId) {
                activeRequestId = null
            }
            invalidateDocument(documentId)
            return VeaMultiModeBridgeRouteResult.Cancelled(requestId, documentId)
        }

        if (streamedKeys != SIGN_KEYS) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val rawPageUrl = json.optString("pageUrl", "").trim()
        if (rawPageUrl.isEmpty()) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
        val canonicalPageUrl = canonicalizeVeaUrl(rawPageUrl)
            ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)

        val runtimeUrl = currentUrl()
        if (runtimeUrl == null) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        val canonicalRuntimeUrl = canonicalizeVeaUrl(runtimeUrl)
        if (canonicalRuntimeUrl == null || canonicalRuntimeUrl != canonicalPageUrl) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }

        if (activeRequestId != null || replayLedger.contains(requestId)) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.PROTOCOL_FAILED)
        }

        val arrayLength = json.optInt("arrayLength", -1)
        if (arrayLength !in 1..MAX_DOCUMENTS) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
        val opJsonArray = json.optJSONArray("operationArray")
            ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        if (opJsonArray.length() != arrayLength) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
        val opList = mutableListOf<String>()
        for (i in 0 until arrayLength) {
            val op = opJsonArray.optString(i, "")
            if (op != "sign") {
                return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            }
            opList.add(op)
        }
        val dataJsonArray = json.optJSONArray("dataArray")
            ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        if (dataJsonArray.length() != arrayLength) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
        val dataList = mutableListOf<String>()
        for (i in 0 until arrayLength) {
            val dataStr = dataJsonArray.optString(i, "")
            if (dataStr.isBlank() || dataStr.length > 512) {
                return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            }
            dataList.add(dataStr)
        }

        val originalDataArray: List<String>? = if (json.isNull("originalDataArray")) {
            null
        } else {
            val origJsonArray = json.optJSONArray("originalDataArray")
                ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            if (origJsonArray.length() != arrayLength) {
                return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            }
            val list = mutableListOf<String>()
            for (i in 0 until arrayLength) {
                val item = if (origJsonArray.isNull(i)) "" else origJsonArray.optString(i, "")
                if (item.isNotEmpty()) {
                    return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
                }
                list.add(item)
            }
            list
        }

        val algorithm = json.optString("algorithm", "").trim()
        val format = json.optString("format", "").trim()
        val extraProperties = json.optString("extraProperties", "").trim()

        if (algorithm.isEmpty() || format.isEmpty() || extraProperties.isEmpty()) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val normalizedFormat = format.uppercase()
        if (normalizedFormat !in SUPPORTED_FORMATS) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val parsedParams = parseExtraParams(extraProperties)
            ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)

        val hashAlgorithm = parsedParams.hashAlgorithm
        val normalizedAlg = algorithm.uppercase().replace("-", "")
        val expectedHashAlgorithm = ALGORITHM_HASH_MAP[normalizedAlg]
            ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)

        if (expectedHashAlgorithm != hashAlgorithm) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val decodedHashes = mutableListOf<ByteArray>()
        for (hashStr in dataList) {
            val decoded = hashAlgorithm.decodeHash(hashStr)
                ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            decodedHashes.add(decoded)
        }

        if (!replayLedger.recordNew(requestId)) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.PROTOCOL_FAILED)
        }

        activeRequestId = requestId
        activeDocumentId = documentId

        val request = VeaMultiModeBridgeRequest(
            requestId = requestId,
            documentId = documentId,
            operationArray = opList,
            dataArray = dataList,
            originalDataArray = originalDataArray,
            arrayLength = arrayLength,
            algorithm = algorithm,
            format = format,
            extraProperties = extraProperties,
            hashAlgorithm = hashAlgorithm,
            hashes = decodedHashes,
            profileId = ProfileId(PROFILE_ID),
            sourceOrigin = TrustedOrigin("https", "veaja.cloud.juntadeandalucia.es", 443),
            navigationEpoch = navigationEpoch,
            pageUrl = canonicalPageUrl,
        )
        return VeaMultiModeBridgeRouteResult.Accepted(request)
    }

    @Synchronized
    fun abandon(requestId: UUID? = null): Boolean {
        if (requestId != null && activeRequestId == requestId) {
            activeRequestId = null
        }
        return true
    }

    @Synchronized
    fun invalidateDocument(documentId: UUID?) {
        if (documentId != null) {
            recordInvalidatedDocument(documentId)
            if (activeDocumentId == documentId) {
                activeDocumentId = null
                activeRequestId = null
            }
        }
    }

    @Synchronized
    fun abandonAll() {
        activeRequestId = null
        activeDocumentId = null
        invalidatedDocumentIds.clear()
    }

    internal fun invalidatedDocumentIdsSize(): Int = synchronized(this) {
        invalidatedDocumentIds.size
    }

    private fun recordInvalidatedDocument(documentId: UUID) {
        invalidatedDocumentIds.remove(documentId)
        invalidatedDocumentIds.add(documentId)
        while (invalidatedDocumentIds.size > MAX_INVALIDATED_DOCUMENTS) {
            val oldest = invalidatedDocumentIds.iterator().next()
            invalidatedDocumentIds.remove(oldest)
        }
    }

    private fun invalidateActiveDocument() {
        val active = activeDocumentId
        if (active != null) {
            recordInvalidatedDocument(active)
            activeDocumentId = null
            activeRequestId = null
        }
    }

    private data class ParsedVeaParams(
        val hashAlgorithm: PrecalculatedHashAlgorithm,
        val mode: String,
        val filters: String,
    )

    private fun parseExtraParams(raw: String): ParsedVeaParams? {
        if (raw.isEmpty() || raw.length > MAX_EXTRA_PROPERTIES_CHARS) return null
        if (STANDALONE_CR_PATTERN.containsMatchIn(raw)) return null
        val lines = raw.split(LINE_SPLIT_PATTERN)
        var mode: String? = null
        var hashAlgo: PrecalculatedHashAlgorithm? = null
        var filters: String? = null
        val seenKeys = mutableSetOf<String>()

        for ((index, rawLine) in lines.withIndex()) {
            if (rawLine.isEmpty()) {
                if (index == lines.lastIndex) break
                return null
            }
            val eqIdx = rawLine.indexOf('=')
            if (eqIdx <= 0) return null
            val key = rawLine.substring(0, eqIdx)
            val value = rawLine.substring(eqIdx + 1)
            if (!seenKeys.add(key)) return null

            when (key) {
                "mode" -> {
                    if (value != "explicit") return null
                    mode = value
                }
                "precalculatedHashAlgorithm" -> {
                    hashAlgo = when (value) {
                        "SHA-1" -> PrecalculatedHashAlgorithm.SHA1
                        "SHA-256" -> PrecalculatedHashAlgorithm.SHA256
                        "SHA-512" -> PrecalculatedHashAlgorithm.SHA512
                        else -> return null
                    }
                }
                "filters" -> {
                    if (!isValidVeaFilter(value)) return null
                    filters = value
                }
                else -> return null // Reject unknown properties fail-closed
            }
        }
        if (mode != "explicit" || hashAlgo == null || filters == null) return null
        return ParsedVeaParams(
            hashAlgorithm = hashAlgo,
            mode = mode,
            filters = filters,
        )
    }

    private fun isValidVeaFilter(filterStr: String): Boolean {
        return filterStr == "nonexpired:;signingCert" || filterStr == "nonexpired:;signingCert;"
    }

    private fun String.uniqueTopLevelKeys(): Set<String>? = try {
        val keys = mutableSetOf<String>()
        val reader = JsonReader(StringReader(this))
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.NAME) {
                reader.close()
                return null
            }
            val key = reader.nextName()
            if (!keys.add(key)) {
                reader.close()
                return null
            }
            reader.skipValue()
        }
        reader.endObject()
        reader.close()
        keys
    } catch (_: Exception) {
        null
    }

    private fun JSONObject.strictUuid(field: String): UUID? {
        val text = optString(field, "")
        if (!UUID_PATTERN.matches(text)) return null
        return runCatching { UUID.fromString(text) }.getOrNull()
    }

    companion object {
        const val PROFILE_ID = "junta-andalucia-sede"
        const val SOURCE_ORIGIN = "https://veaja.cloud.juntadeandalucia.es"
        const val TYPE = "MINIAPPLET_MULTIMODE_SIGN"
        const val CANCEL_TYPE = "MINIAPPLET_MULTIMODE_CANCEL"
        const val RESULT_TYPE = "MINIAPPLET_MULTIMODE_RESULT"
        const val MAX_MESSAGE_CHARS = 786_432
        const val MAX_EXTRA_PROPERTIES_CHARS = 65_536
        const val MAX_DOCUMENTS = 128
        const val MAX_INVALIDATED_DOCUMENTS = 64

        private val STANDALONE_CR_PATTERN = Regex("\r(?!\n)")
        private val LINE_SPLIT_PATTERN = Regex("\r?\n")

        private val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        )
        private val SIGN_KEYS = setOf(
            "type",
            "documentId",
            "requestId",
            "operationArray",
            "dataArray",
            "originalDataArray",
            "arrayLength",
            "algorithm",
            "format",
            "extraProperties",
            "pageUrl",
        )
        private val CANCEL_KEYS = setOf(
            "type",
            "documentId",
            "requestId",
        )

        val SUPPORTED_FORMATS = setOf("CADES")

        val ALGORITHM_HASH_MAP = mapOf(
            "SHA256WITHRSA" to PrecalculatedHashAlgorithm.SHA256,
            "SHA512WITHRSA" to PrecalculatedHashAlgorithm.SHA512,
            "SHA1WITHRSA" to PrecalculatedHashAlgorithm.SHA1,
        )

        val ALLOWED_EXACT_PATHS = setOf(
            "/",
            "/inicio",
            "/confirmacion-modificacion-datos-contacto",
            "/documentacion-voluntaria",
            "/justificante",
            "/datos-contacto",
            "/area-personal",
        )

        val ALLOWED_PREFIX_PATHS = listOf(
            "/inicio/",
            "/borrador/",
            "/formulario/",
            "/resumen-pago/",
            "/procedimiento-detalle/",
            "/competente/",
            "/tarea/",
        )

        fun isValidVeaPath(path: String): Boolean {
            val normalized = if (path.isEmpty()) "/" else path
            return normalized in ALLOWED_EXACT_PATHS ||
                ALLOWED_PREFIX_PATHS.any { prefix -> normalized.startsWith(prefix) }
        }

        fun canonicalizeVeaUrl(rawUrl: String): String? {
            val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
            if (uri.scheme != "https" ||
                uri.host != "veaja.cloud.juntadeandalucia.es" ||
                (uri.port != -1 && uri.port != 443) ||
                uri.encodedUserInfo != null
            ) {
                return null
            }
            val rawPath = uri.path ?: "/"
            val normalizedPath = if (rawPath.isEmpty()) "/" else rawPath
            if (!isValidVeaPath(normalizedPath)) {
                return null
            }
            val query = uri.encodedQuery
            val querySuffix = if (query != null) "?$query" else ""
            return "https://veaja.cloud.juntadeandalucia.es$normalizedPath$querySuffix"
        }

        fun isValidVeaPageUrl(rawUrl: String): Boolean = canonicalizeVeaUrl(rawUrl) != null
    }
}

internal fun veaMultiModeFailureReplyJson(
    requestId: UUID,
    code: SigningErrorCode,
): String = JSONObject()
    .put("type", VeaMultiModeBridgeAdapter.RESULT_TYPE)
    .put("requestId", requestId.toString())
    .put("status", "failure")
    .put("errorCode", code.name)
    .toString()

class VeaMultiModeReplyChannel(
    val requestId: UUID,
    private val documentId: UUID,
    private val navigationEpoch: Long,
    private val sourceOrigin: TrustedOrigin,
    val pageUrl: String,
    private val postMessage: (String) -> Unit,
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val currentDocumentId: () -> UUID? = { null },
    private val currentPageUrl: () -> String? = { null },
    private val onTerminal: (UUID) -> Unit = {},
) {
    private val closed = AtomicBoolean(false)

    fun success(signaturesB64: String, certificateB64: String): Boolean {
        if (!claimTerminal()) return false
        if (!isBoundaryValid()) {
            emitResult(status = "failure", errorCode = SigningErrorCode.NAVIGATION_CHANGED.name)
            return false
        }
        emitResult(
            status = "success",
            signature = signaturesB64,
            certificate = certificateB64,
        )
        return true
    }

    fun failure(code: SigningErrorCode): Boolean {
        if (!claimTerminal()) return false
        emitResult(status = "failure", errorCode = code.name)
        return true
    }

    fun abandon(): Boolean = claimTerminal()

    private fun claimTerminal(): Boolean {
        val claimed = closed.compareAndSet(false, true)
        if (claimed) {
            onTerminal(requestId)
        }
        return claimed
    }

    private fun isBoundaryValid(): Boolean {
        val curEpoch = currentNavigationEpoch()
        if (curEpoch != navigationEpoch) return false
        val curDoc = currentDocumentId()
        if (curDoc == null || curDoc != documentId) return false
        val curOrig = currentOrigin()
        if (curOrig == null || curOrig != sourceOrigin) return false
        val curUrl = currentPageUrl() ?: return false
        val canonicalCur = VeaMultiModeBridgeAdapter.canonicalizeVeaUrl(curUrl)
        val canonicalReq = VeaMultiModeBridgeAdapter.canonicalizeVeaUrl(pageUrl)
        if (canonicalCur == null || canonicalCur != canonicalReq) return false
        return true
    }

    private fun emitResult(
        status: String,
        signature: String? = null,
        certificate: String? = null,
        errorCode: String? = null,
    ) {
        val json = JSONObject()
            .put("type", VeaMultiModeBridgeAdapter.RESULT_TYPE)
            .put("requestId", requestId.toString())
            .put("status", status)
        if (signature != null) json.put("signature", signature)
        if (certificate != null) json.put("certificate", certificate)
        if (errorCode != null) json.put("errorCode", errorCode)
        runCatching { postMessage(json.toString()) }
    }
}
