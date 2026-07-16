package dev.junta.firmamobile.browser

enum class BrowserTransitionReason {
    NAVIGATE,
    RELOAD,
    BACK,
    FORWARD,
    PROFILE_SWITCH,
}

fun interface SensitiveFlowInvalidator {
    fun invalidate(reason: BrowserTransitionReason)
}
