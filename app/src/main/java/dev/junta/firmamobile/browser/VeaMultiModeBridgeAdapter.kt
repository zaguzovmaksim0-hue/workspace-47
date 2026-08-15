package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.PrecalculatedHashAlgorithm
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningErrorCode
import java.io.StringReader
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
) {
    private val abandonedRequests = mutableSetOf<UUID>()
    private var activeDocumentId: UUID? = null

    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        navigationEpoch: Long = 0L,
    ): VeaMultiModeBridgeRouteResult {
        if (navigationEpoch < 0L) return VeaMultiModeBridgeRouteResult.NotApplicable
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
        if (messageType == CANCEL_TYPE) {
            if (streamedKeys != CANCEL_KEYS) {
                return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            }
            abandonedRequests.add(requestId)
            return VeaMultiModeBridgeRouteResult.Cancelled(requestId, documentId)
        }
        if (streamedKeys != SIGN_KEYS) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
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
        if (currentDocId != null && currentDocId != documentId) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        val expectedEpoch = currentNavigationEpoch()
        if (expectedEpoch != null && expectedEpoch != navigationEpoch) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        }
        val expectedOrigin = currentOrigin()
        if (expectedOrigin != null && (expectedOrigin.scheme != "https" || expectedOrigin.host != "veaja.cloud.juntadeandalucia.es" || expectedOrigin.port != 443)) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED)
        }
        if (requestId in abandonedRequests) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.USER_CANCELLED)
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
            if (origJsonArray != null) {
                List(origJsonArray.length()) { origJsonArray.optString(it, "") }
            } else null
        }

        val algorithm = json.optString("algorithm", "").trim()
        val format = json.optString("format", "").trim()
        val extraProperties = json.optString("extraProperties", "").trim()

        if (algorithm.isEmpty() || format.isEmpty() || extraProperties.isEmpty()) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val parsedParams = parseExtraParams(extraProperties)
            ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)

        val hashAlgorithm = parsedParams.hashAlgorithm
        val matchingAlgorithm = when (algorithm.uppercase().replace("-", "")) {
            "SHA256WITHRSA" -> SigningAlgorithm.SHA256_WITH_RSA
            "SHA512WITHRSA" -> SigningAlgorithm.SHA512_WITH_RSA
            "SHA1WITHRSA" -> SigningAlgorithm.SHA1_WITH_RSA
            else -> return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        if (matchingAlgorithm !in hashAlgorithm.matchingSigningAlgorithms) {
            return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val decodedHashes = mutableListOf<ByteArray>()
        for (hashStr in dataList) {
            val decoded = hashAlgorithm.decodeHash(hashStr)
                ?: return VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
            decodedHashes.add(decoded)
        }

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
        )
        return VeaMultiModeBridgeRouteResult.Accepted(request)
    }

    fun abandon(requestId: UUID? = null): Boolean {
        if (requestId != null) {
            abandonedRequests.add(requestId)
        }
        return true
    }

    fun invalidateDocument(documentId: UUID?) {
        if (documentId != null && activeDocumentId == documentId) {
            activeDocumentId = null
        }
    }

    fun abandonAll() {
        abandonedRequests.clear()
        activeDocumentId = null
    }

    private data class ParsedVeaParams(
        val hashAlgorithm: PrecalculatedHashAlgorithm,
        val mode: String,
        val filters: String?,
    )

    private fun parseExtraParams(raw: String): ParsedVeaParams? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var mode: String? = null
        var hashAlgo: PrecalculatedHashAlgorithm? = null
        var filters: String? = null

        for (line in lines) {
            val eqIdx = line.indexOf('=')
            if (eqIdx == -1) return null
            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 1).trim()
            when (key) {
                "mode" -> {
                    if (value != "explicit") return null
                    mode = value
                }
                "precalculatedHashAlgorithm" -> {
                    hashAlgo = PrecalculatedHashAlgorithm.parse(value) ?: return null
                }
                "filters" -> {
                    filters = value
                }
                else -> return null // Reject unknown properties fail-closed
            }
        }
        if (mode != "explicit" || hashAlgo == null) return null
        return ParsedVeaParams(
            hashAlgorithm = hashAlgo,
            mode = mode,
            filters = filters,
        )
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
        const val MAX_DOCUMENTS = 128

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
        )
        private val CANCEL_KEYS = setOf(
            "type",
            "documentId",
            "requestId",
        )
    }
}

class VeaMultiModeReplyChannel(
    val requestId: UUID,
    private val documentId: UUID,
    private val navigationEpoch: Long,
    private val sourceOrigin: TrustedOrigin,
    private val postMessage: (String) -> Unit,
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val currentDocumentId: () -> UUID? = { null },
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
        if (curDoc != null && curDoc != documentId) return false
        val curOrig = currentOrigin()
        if (curOrig != null && curOrig != sourceOrigin) return false
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
