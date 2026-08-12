package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.TrustMode

data class BrowserTrustState(
    val epoch: Long,
    val activeProfileId: ProfileId?,
    val resolution: BrowserUrlResolution,
)

class BrowserTrustController(
    private val urlPolicy: BrowserUrlPolicy,
    private val invalidator: SensitiveFlowInvalidator,
) {
    private var epoch = 0L
    private var state = BrowserTrustState(
        epoch = epoch,
        activeProfileId = null,
        resolution = BrowserUrlResolution(null, null, TrustMode.BLOCKED),
    )

    @Synchronized
    fun current(): BrowserTrustState = state

    @Synchronized
    fun navigate(rawUrl: String): BrowserTrustState = transition(BrowserTransitionReason.NAVIGATE) {
        val resolution = urlPolicy.resolve(rawUrl, state.activeProfileId)
        val active = when (resolution.trustMode) {
            TrustMode.TRUSTED_SIGNING, TrustMode.TRUSTED_CLIENT_AUTH ->
                resolution.site?.profile?.profileId
            TrustMode.TRUSTED_BROWSE -> state.activeProfileId.takeIf {
                it == resolution.site?.profile?.profileId
            }
            TrustMode.BROWSE_ONLY, TrustMode.EXTERNAL_ONLY, TrustMode.BLOCKED -> null
        }
        BrowserTrustState(epoch, active, resolution)
    }

    @Synchronized
    fun reload(): BrowserTrustState = invalidateOnly(BrowserTransitionReason.RELOAD)

    @Synchronized
    fun back(): BrowserTrustState = invalidateOnly(BrowserTransitionReason.BACK)

    @Synchronized
    fun forward(): BrowserTrustState = invalidateOnly(BrowserTransitionReason.FORWARD)

    @Synchronized
    fun switchProfile(profileId: ProfileId?): BrowserTrustState =
        transition(BrowserTransitionReason.PROFILE_SWITCH) {
            require(profileId == null || urlPolicy.isActiveProfile(profileId))
            BrowserTrustState(
                epoch = epoch,
                activeProfileId = null,
                resolution = BrowserUrlResolution(null, null, TrustMode.BLOCKED),
            )
        }

    private fun invalidateOnly(reason: BrowserTransitionReason): BrowserTrustState = transition(reason) {
        state.copy(epoch = epoch, resolution = state.resolution.copy(trustMode = TrustMode.BLOCKED))
    }

    private inline fun transition(
        reason: BrowserTransitionReason,
        next: () -> BrowserTrustState,
    ): BrowserTrustState {
        invalidator.invalidate(reason)
        check(epoch != Long.MAX_VALUE) { "Browser trust epoch exhausted" }
        epoch++
        return next().also { state = it }
    }
}
