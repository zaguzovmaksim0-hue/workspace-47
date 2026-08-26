package dev.junta.firmamobile.diagnostics

import dev.junta.firmamobile.profile.ProfileId

/**
 * Lifecycle-scoped handles for confirmation actions already owned by BrowserScreen.
 * This is not an external ingress; debug/QA diagnostics may invoke it, while release has no receiver.
 */
internal data class BrowserInteractiveControl(
    val profileId: ProfileId,
    val confirmClientAuth: () -> Boolean,
    val cancelClientAuth: () -> Boolean,
    val confirmCertificateSelection: () -> Boolean,
    val cancelCertificateSelection: () -> Boolean,
)
