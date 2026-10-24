package dev.junta.firmamobile.ui

import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState

/** Presentation only: does not gate WebView creation or certificate access. */
internal fun browserLoadingVisible(
    clientCertPreferenceState: ClientCertPreferenceBarrierState,
    clientAuthPreparing: Boolean,
    pageLoading: Boolean,
): Boolean = clientCertPreferenceState == ClientCertPreferenceBarrierState.CLEARING ||
    clientAuthPreparing || pageLoading
