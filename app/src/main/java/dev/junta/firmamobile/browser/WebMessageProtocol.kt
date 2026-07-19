package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaUriParser
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import org.json.JSONObject

data class WebBridgeMessage(
    val requestId: String,
    val uri: String,
    val sourceOrigin: TrustedOrigin,
)

enum class WebMessageErrorCode {
    MESSAGE_TOO_LARGE,
    UNTRUSTED_ORIGIN,
    MALFORMED_JSON,
    UNSUPPORTED_TYPE,
    INVALID_REQUEST_ID,
    INVALID_URI,
    UNSUPPORTED_URI_SCHEME,
}

sealed interface WebMessageParseResult {
    data class Success(val message: WebBridgeMessage) : WebMessageParseResult

    data class Failure(val code: WebMessageErrorCode) : WebMessageParseResult
}

enum class WebMessageReplyStatus(val wireValue: String) {
    ACCEPTED("accepted"),
    REJECTED("rejected"),
}

object WebMessageProtocol {
    const val MAX_MESSAGE_CHARS = AfirmaUriParser.MAX_URI_CHARS + 4096
    private const val MESSAGE_TYPE = "AFIRMA_URI"
    private const val REPLY_TYPE = "AFIRMA_ACK"

    fun parse(rawMessage: String, sourceOrigin: Uri): WebMessageParseResult {
        if (rawMessage.length > MAX_MESSAGE_CHARS) {
            return WebMessageParseResult.Failure(WebMessageErrorCode.MESSAGE_TOO_LARGE)
        }
        val trustedOrigin = JuntaOriginPolicy.originFor(sourceOrigin)
            ?: return WebMessageParseResult.Failure(WebMessageErrorCode.UNTRUSTED_ORIGIN)
        val profile = BuiltInSiteProfiles.runtimeRegistry.resolve(trustedOrigin)?.profile
        if (profile == null || Capability.AFIRMA_URI !in profile.capabilities) {
            return WebMessageParseResult.Failure(WebMessageErrorCode.UNTRUSTED_ORIGIN)
        }
        val json = try {
            JSONObject(rawMessage)
        } catch (_: Exception) {
            return WebMessageParseResult.Failure(WebMessageErrorCode.MALFORMED_JSON)
        }
        if (json.optString("type") != MESSAGE_TYPE) {
            return WebMessageParseResult.Failure(WebMessageErrorCode.UNSUPPORTED_TYPE)
        }
        val requestId = json.optString("requestId")
        if (!UUID_PATTERN.matches(requestId)) {
            return WebMessageParseResult.Failure(WebMessageErrorCode.INVALID_REQUEST_ID)
        }
        val rawUri = json.optString("uri")
        if (rawUri.isBlank() || rawUri.length > AfirmaUriParser.MAX_URI_CHARS) {
            return WebMessageParseResult.Failure(WebMessageErrorCode.INVALID_URI)
        }
        val scheme = try {
            Uri.parse(rawUri).scheme
        } catch (_: Exception) {
            null
        }
        if (!scheme.equals("afirma", ignoreCase = true) &&
            !scheme.equals("intent", ignoreCase = true)
        ) {
            return WebMessageParseResult.Failure(WebMessageErrorCode.UNSUPPORTED_URI_SCHEME)
        }
        return WebMessageParseResult.Success(
            WebBridgeMessage(
                requestId = requestId,
                uri = rawUri,
                sourceOrigin = trustedOrigin,
            ),
        )
    }

    fun replyJson(
        requestId: String,
        status: WebMessageReplyStatus,
        errorCode: String? = null,
    ): String {
        require(UUID_PATTERN.matches(requestId))
        require(errorCode == null || ERROR_CODE_PATTERN.matches(errorCode))
        return JSONObject()
            .put("type", REPLY_TYPE)
            .put("requestId", requestId)
            .put("status", status.wireValue)
            .apply { if (errorCode != null) put("errorCode", errorCode) }
            .toString()
    }

    private val UUID_PATTERN = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
    )
    private val ERROR_CODE_PATTERN = Regex("[A-Z][A-Z0-9_]{0,63}")
}
