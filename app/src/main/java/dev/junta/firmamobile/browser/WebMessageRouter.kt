package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.profile.ProfileId

sealed interface WebMessageRouteResult {
    data class Accepted(
        val requestId: String,
        val request: AfirmaRequest,
    ) : WebMessageRouteResult

    data class Rejected(
        val requestId: String?,
        val errorCode: String,
    ) : WebMessageRouteResult
}

class WebMessageRouter(
    private val profileId: ProfileId,
    private val navigationPolicy: JuntaNavigationPolicy = JuntaNavigationPolicy(profileId),
) {
    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
    ): WebMessageRouteResult {
        val parsed = when (
            val result = WebMessageProtocol.parse(
                rawMessage = rawMessage,
                sourceOrigin = sourceOrigin,
                expectedProfileId = profileId,
            )
        ) {
            is WebMessageParseResult.Success -> result.message
            is WebMessageParseResult.Failure -> {
                return WebMessageRouteResult.Rejected(
                    requestId = null,
                    errorCode = result.code.name,
                )
            }
        }
        if (!isMainFrame) {
            return WebMessageRouteResult.Rejected(
                requestId = parsed.requestId,
                errorCode = ERROR_NOT_MAIN_FRAME,
            )
        }

        return when (
            val decision = navigationPolicy.decide(
                targetUrl = parsed.uri,
                currentPageUrl = parsed.sourceOrigin.serialized,
            )
        ) {
            is NavigationDecision.HandleAfirma -> WebMessageRouteResult.Accepted(
                requestId = parsed.requestId,
                request = decision.request,
            )
            is NavigationDecision.Block -> WebMessageRouteResult.Rejected(
                requestId = parsed.requestId,
                errorCode = decision.reason.name,
            )
            NavigationDecision.AllowInWebView,
            is NavigationDecision.OpenExternal,
            is NavigationDecision.UpgradeToHttps,
            -> WebMessageRouteResult.Rejected(
                requestId = parsed.requestId,
                errorCode = ERROR_UNEXPECTED_NAVIGATION,
            )
        }
    }

    private companion object {
        const val ERROR_NOT_MAIN_FRAME = "NOT_MAIN_FRAME"
        const val ERROR_UNEXPECTED_NAVIGATION = "UNEXPECTED_NAVIGATION"
    }
}
