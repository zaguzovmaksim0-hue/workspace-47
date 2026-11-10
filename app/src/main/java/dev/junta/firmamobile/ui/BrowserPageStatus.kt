package dev.junta.firmamobile.ui

import androidx.compose.runtime.Composable
import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState

@Composable
internal fun browserPageStatus(
    state: BrowserPageStatusState,
    onRecoverClientCertPreferences: () -> Unit,
    onRetryBrowserError: () -> Unit,
) {
    val notice = browserNotice(
        compatibilityError = state.compatibilityError,
        blockedReason = state.blockedReason,
        browserError = state.browserError,
        clientCertPreferenceState = state.clientCertPreferenceState,
        dataNotice = browserDataNotice(state.siteClearResult, state.globalClearResult),
    )
    notice?.let { message ->
        BrowserNoticeBanner(
            message = message,
            liveRegionMode = browserNoticeLiveRegionMode(
                compatibilityError = state.compatibilityError,
                blockedReason = state.blockedReason,
                browserError = state.browserError,
                clientCertPreferenceState = state.clientCertPreferenceState,
                globalClearResult = state.globalClearResult,
            ),
            onRetry = when {
                state.clientCertPreferenceState == ClientCertPreferenceBarrierState.FAILED ||
                    state.browserError == BrowserErrorCode.CLIENT_CERT_PREFERENCES ->
                    onRecoverClientCertPreferences
                state.browserError != null -> onRetryBrowserError
                else -> null
            },
        )
    }
    BrowserLoadingIndicator(
        visible = browserLoadingVisible(
            clientCertPreferenceState = state.clientCertPreferenceState,
            clientAuthPreparing = state.clientAuthPreparing,
            pageLoading = state.pageLoading,
        ),
    )
}
