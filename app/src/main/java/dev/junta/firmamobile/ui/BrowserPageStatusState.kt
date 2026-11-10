package dev.junta.firmamobile.ui

import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState
import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.browser.SiteClearResult

/** Presentation snapshot only; BrowserScreen retains operation state and WebView ownership. */
internal data class BrowserPageStatusState(
    val compatibilityError: Boolean = false,
    val blockedReason: NavigationBlockReason? = null,
    val browserError: BrowserErrorCode? = null,
    val clientCertPreferenceState: ClientCertPreferenceBarrierState = ClientCertPreferenceBarrierState.IDLE,
    val siteClearResult: SiteClearResult? = null,
    val globalClearResult: Boolean? = null,
    val clientAuthPreparing: Boolean = false,
    val pageLoading: Boolean = false,
)
