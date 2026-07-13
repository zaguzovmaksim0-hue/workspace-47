package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.NormalizedSignRequest
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningContext
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningFormat
import dev.junta.firmamobile.signing.SigningProtocolId
import dev.junta.firmamobile.signing.SigningReplySink
import java.io.StringReader
import java.time.Clock
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

data class MiniAppletBridgeRequest(
    val normalized: NormalizedSignRequest,
)

sealed interface MiniAppletBridgeRouteResult {
    data object NotApplicable : MiniAppletBridgeRouteResult

    data class Accepted(val request: MiniAppletBridgeRequest) : MiniAppletBridgeRouteResult

    data class Cancelled(
        val requestId: UUID,
        val navigationId: NavigationId,
    ) : MiniAppletBridgeRouteResult

    data class Rejected(
        val requestId: UUID?,
        val code: SigningErrorCode,
    ) : MiniAppletBridgeRouteResult
}

class MiniAppletBridgeAdapter(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
    ): MiniAppletBridgeRouteResult {
        if (rawMessage.length > WebMessageProtocol.MAX_MESSAGE_CHARS) {
            return MiniAppletBridgeRouteResult.NotApplicable
        }
        val streamedKeys = rawMessage.uniqueTopLevelKeys()
        val json = try {
            JSONObject(rawMessage)
        } catch (_: Exception) {
            return MiniAppletBridgeRouteResult.NotApplicable
        }
        val messageType = json.optString(TYPE_FIELD)
        if (messageType != TYPE_MINIAPPLET_SIGN && messageType != TYPE_MINIAPPLET_CANCEL) {
            return MiniAppletBridgeRouteResult.NotApplicable
        }
        if (rawMessage.length > MAX_MESSAGE_CHARS) {
            return MiniAppletBridgeRouteResult.Rejected(null, SigningErrorCode.REQUEST_TOO_LARGE)
        }
        val requestId = json.strictUuid(REQUEST_ID_FIELD)
        val requiredKeys = if (messageType == TYPE_MINIAPPLET_CANCEL) {
            CANCEL_KEYS
        } else {
            SIGN_KEYS
        }
        if (streamedKeys == null || json.keySet() != streamedKeys ||
            streamedKeys != requiredKeys
        ) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
        if (!isMainFrame) {
            return MiniAppletBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.NAVIGATION_CHANGED,
            )
        }
        val origin = JuntaOriginPolicy.originFor(sourceOrigin)
            ?: return MiniAppletBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.ORIGIN_NOT_ALLOWED,
            )
        val canonicalRequestId = requestId
            ?: return MiniAppletBridgeRouteResult.Rejected(null, SigningErrorCode.INVALID_REQUEST)
        val documentId = json.strictUuid(DOCUMENT_ID_FIELD)
            ?: return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        val navigationId = NavigationId(documentId.toString())
        if (messageType == TYPE_MINIAPPLET_CANCEL) {
            return MiniAppletBridgeRouteResult.Cancelled(canonicalRequestId, navigationId)
        }
        val algorithm = when (json.strictString(ALGORITHM_FIELD)) {
            ALGORITHM_SHA1_RSA -> SigningAlgorithm.SHA1_WITH_RSA
            ALGORITHM_SHA256_RSA -> SigningAlgorithm.SHA256_WITH_RSA
            else -> return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        if (json.strictString(FORMAT_FIELD) != FORMAT_CADES) {
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        val dataBase64 = json.strictString(DATA_FIELD)
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_DATA_BASE64_CHARS }
            ?: return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.REQUEST_TOO_LARGE,
            )
        if (!BASE64_PATTERN.matches(dataBase64)) {
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        val extraProperties = json.strictString(EXTRA_PROPERTIES_FIELD)
            ?.takeIf { it.length <= MAX_EXTRA_PROPERTIES_CHARS && it.hasSafeControls() }
            ?: return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        val decodedData = try {
            Base64.getDecoder().decode(dataBase64)
        } catch (_: IllegalArgumentException) {
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        if (decodedData.size > MAX_DECODED_DATA_BYTES) {
            decodedData.fill(0)
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.REQUEST_TOO_LARGE,
            )
        }
        val payload = try {
            MiniAppletPayloadCodec.encode(decodedData, extraProperties)
        } catch (_: IllegalArgumentException) {
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.REQUEST_TOO_LARGE,
            )
        } finally {
            decodedData.fill(0)
        }
        return MiniAppletBridgeRouteResult.Accepted(
            MiniAppletBridgeRequest(
                normalized = NormalizedSignRequest(
                    requestId = canonicalRequestId,
                    protocolId = PROTOCOL_ID,
                    context = SigningContext(
                        profileId = PROFILE_ID,
                        profileVersion = PROFILE_VERSION,
                        origin = origin,
                        navigationId = navigationId,
                        observedAt = clock.instant(),
                    ),
                    algorithm = algorithm,
                    format = SigningFormat.CADES,
                    safeDescription = SAFE_DESCRIPTION,
                    payload = payload,
                ),
            ),
        )
    }

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

    private fun JSONObject.strictUuid(name: String): UUID? {
        val value = strictString(name) ?: return null
        if (!UUID_PATTERN.matches(value)) return null
        return try {
            UUID.fromString(value).takeIf { it.toString() == value.lowercase() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun JSONObject.strictString(name: String): String? = opt(name) as? String

    private fun JSONObject.keySet(): Set<String> = buildSet {
        val keys = keys()
        while (keys.hasNext()) add(keys.next())
    }

    private fun String.hasSafeControls(): Boolean = all { character ->
        !character.isISOControl() || character == '\n' || character == '\r' || character == '\t'
    }

    companion object {
        const val MAX_DECODED_DATA_BYTES = 524_288
        const val MAX_DATA_BASE64_CHARS = 699_052
        const val MAX_EXTRA_PROPERTIES_CHARS = 65_536
        const val MAX_MESSAGE_CHARS = 786_432
        private const val TYPE_FIELD = "type"
        private const val DOCUMENT_ID_FIELD = "documentId"
        private const val REQUEST_ID_FIELD = "requestId"
        private const val DATA_FIELD = "dataB64"
        private const val ALGORITHM_FIELD = "algorithm"
        private const val FORMAT_FIELD = "format"
        private const val EXTRA_PROPERTIES_FIELD = "extraProperties"
        private const val TYPE_MINIAPPLET_SIGN = "MINIAPPLET_SIGN"
        private const val TYPE_MINIAPPLET_CANCEL = "MINIAPPLET_CANCEL"
        private const val ALGORITHM_SHA1_RSA = "SHA1withRSA"
        private const val ALGORITHM_SHA256_RSA = "SHA256withRSA"
        private const val FORMAT_CADES = "CAdES"
        private const val PROFILE_ID = "junta-andalucia"
        private const val PROFILE_VERSION = 1
        private const val SAFE_DESCRIPTION = "Autenticación con certificado"
        private val PROTOCOL_ID = SigningProtocolId("junta-miniapplet-triphase-cades-v1")
        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
        private val BASE64_PATTERN = Regex(
            "(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?",
        )
        private val SIGN_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
            DATA_FIELD,
            ALGORITHM_FIELD,
            FORMAT_FIELD,
            EXTRA_PROPERTIES_FIELD,
        )
        private val CANCEL_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
        )
    }
}

class MiniAppletReplyChannel internal constructor(
    override val requestId: UUID,
    private val postMessage: (String) -> Unit,
    private val onTerminal: () -> Unit = {},
) : SigningReplySink {
    private val terminal = AtomicBoolean(false)

    override fun success(signature: LocalSignature, certificateDer: ByteArray): Boolean {
        if (!terminal.compareAndSet(false, true)) {
            signature.close()
            certificateDer.fill(0)
            return false
        }
        return try {
            val signatureBase64 = signature.use { ownedSignature ->
                ownedSignature.withBytes { bytes ->
                    require(bytes.size <= MAX_SIGNATURE_BYTES)
                    Base64.getEncoder().encodeToString(bytes)
                }
            }
            require(certificateDer.size <= MAX_CERTIFICATE_BYTES)
            val certificateBase64 = Base64.getEncoder().encodeToString(certificateDer)
            postMessage(
                JSONObject()
                    .put("type", "MINIAPPLET_RESULT")
                    .put("requestId", requestId.toString())
                    .put("status", "success")
                    .put("signature", signatureBase64)
                    .put("certificate", certificateBase64)
                    .toString(),
            )
            true
        } catch (_: Exception) {
            false
        } finally {
            signature.close()
            certificateDer.fill(0)
            onTerminal()
        }
    }

    override fun failure(code: SigningErrorCode): Boolean {
        if (!terminal.compareAndSet(false, true)) return false
        return try {
            postMessage(
                JSONObject()
                    .put("type", "MINIAPPLET_RESULT")
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

    override fun abandon(): Boolean {
        if (!terminal.compareAndSet(false, true)) return false
        onTerminal()
        return true
    }

    private companion object {
        const val MAX_SIGNATURE_BYTES = 2_097_152
        const val MAX_CERTIFICATE_BYTES = 65_536
    }
}
