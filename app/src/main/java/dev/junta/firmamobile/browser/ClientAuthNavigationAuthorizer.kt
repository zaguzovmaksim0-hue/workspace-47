package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.hasLinkedEphemeralParameters
import dev.junta.firmamobile.profile.matchesRequestUrl
import dev.junta.firmamobile.profile.matchesSourceUrl
import dev.junta.firmamobile.profile.strictClientAuthHttpsUri
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.net.URI
import java.time.Duration

@ConsistentCopyVisibility
data class AuthorizedClientAuthTarget internal constructor(
    val profileId: ProfileId,
    internal val target: URI,
    internal val policy: ClientAuthPolicy,
    internal val certificateRules: dev.junta.firmamobile.profile.CertificateFilterRules,
    internal val observedAtMonotonicNanos: Long,
    internal val lifetimeNanos: Long,
) {
    internal fun isExpiredOrInvalid(
        nowNanos: Long = MonotonicSecurityTime.nowNanos(),
    ): Boolean = MonotonicSecurityTime.isExpiredOrInvalid(
        observedAtMonotonicNanos,
        lifetimeNanos,
        nowNanos,
    )

    internal fun refreshedAfterUserConfirmation(
        nowNanos: Long = MonotonicSecurityTime.nowNanos(),
    ): AuthorizedClientAuthTarget = copy(observedAtMonotonicNanos = nowNanos)
}

/**
 * Authorizes Client TLS only for an explicit profile transition contract:
 * either one exact direct source-to-target navigation or the existing bounded
 * two-stage source/redirect flow. Ephemeral values are checked in memory and
 * never retained separately.
 */
class ClientAuthNavigationAuthorizer internal constructor(
    private val registry: SiteProfileRegistry,
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) {
    private var pending: PendingSource? = null
    private var consumedDirect: DirectConsumption? = null
    private var consumedInPlace: DirectConsumption? = null

    @Synchronized
    fun observeTopLevelNavigation(
        activeProfileId: ProfileId?,
        currentUrl: String?,
        targetUrl: String,
        currentEpoch: Long,
        isModernMainFrameRequest: Boolean,
    ): AuthorizedClientAuthTarget? {
        if (!isModernMainFrameRequest || activeProfileId == null || currentEpoch == Long.MAX_VALUE) {
            clearPending()
            return null
        }
        val profile = registry.profile(activeProfileId) ?: run {
            clearPending()
            return null
        }
        val policy = profile.clientAuthPolicy ?: run {
            clearPending()
            return null
        }
        val target = strictClientAuthHttpsUri(targetUrl) ?: run {
            clearPending()
            return null
        }
        val nowNanos = monotonicNanos()

        return when (policy.transitionMode) {
            ClientAuthTransitionMode.DIRECT_FROM_SOURCE -> authorizeDirectTransition(
                profile = profile,
                policy = policy,
                currentUrl = currentUrl,
                target = target,
                currentEpoch = currentEpoch,
                nowNanos = nowNanos,
            )
            ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE -> authorizeRedirectTransition(
                profile = profile,
                policy = policy,
                currentUrl = currentUrl,
                target = target,
                currentEpoch = currentEpoch,
                nowNanos = nowNanos,
            )
            ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE -> {
                if (policy.requestMethod == dev.junta.firmamobile.profile.HttpMethod.GET) {
                    authorizeInPlaceGetRedirectTransition(
                        profile = profile,
                        policy = policy,
                        currentUrl = currentUrl,
                        target = target,
                        currentEpoch = currentEpoch,
                        nowNanos = nowNanos,
                    )
                } else {
                    null
                }
            }
        }
    }

    @Synchronized
    fun observeTopLevelResourceRequest(
        activeProfileId: ProfileId?,
        currentUrl: String?,
        targetUrl: String,
        method: String,
        currentEpoch: Long,
        isMainFrameRequest: Boolean,
    ): AuthorizedClientAuthTarget? {
        if (!isMainFrameRequest || activeProfileId == null || currentEpoch == Long.MAX_VALUE) return null
        val profile = registry.profile(activeProfileId) ?: return null
        val policy = profile.clientAuthPolicy ?: return null
        if (policy.transitionMode != ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE ||
            !method.equals(policy.requestMethod.name, ignoreCase = true)
        ) return null
        val source = currentUrl?.let(::strictClientAuthHttpsUri)?.takeIf { policy.matchesSourceUrl(it) } ?: return null
        val target = strictClientAuthHttpsUri(targetUrl)?.takeIf { policy.matchesRequestUrl(it) } ?: return null
        val nowNanos = monotonicNanos()
        val previous = consumedInPlace
        if (previous != null &&
            previous.profileId == profile.profileId && previous.source == source &&
            previous.target == target && previous.epoch == currentEpoch &&
            !previous.isExpiredOrInvalid(nowNanos)
        ) return null
        val lifetimeNanos = grantLifetimeNanos(policy)
        consumedInPlace = DirectConsumption(
            profileId = profile.profileId,
            source = source,
            target = target,
            epoch = currentEpoch,
            observedAtMonotonicNanos = nowNanos,
            lifetimeNanos = lifetimeNanos,
        )
        return authorized(profile, policy, target, nowNanos, lifetimeNanos)
    }

    private fun authorizeInPlaceGetRedirectTransition(
        profile: SiteProfile,
        policy: ClientAuthPolicy,
        currentUrl: String?,
        target: URI,
        currentEpoch: Long,
        nowNanos: Long,
    ): AuthorizedClientAuthTarget? {
        if (policy.matchesSourceUrl(target) && currentBelongsTo(profile, currentUrl)) {
            pending = PendingSource(
                profileId = profile.profileId,
                source = target,
                armingEpoch = currentEpoch,
                observedAtMonotonicNanos = nowNanos,
                lifetimeNanos = grantLifetimeNanos(policy),
            )
            return null
        }

        val source = pending
        pending = null
        if (source == null || source.profileId != profile.profileId) return null
        if (currentEpoch != source.armingEpoch && currentEpoch != source.armingEpoch + 1) return null
        if (source.isExpiredOrInvalid(nowNanos)) return null
        if (!policy.matchesSourceUrl(source.source) || !policy.matchesRequestUrl(target)) return null

        val previous = consumedInPlace
        if (previous != null &&
            previous.profileId == profile.profileId && previous.source == source.source &&
            previous.target == target && previous.epoch == currentEpoch &&
            !previous.isExpiredOrInvalid(nowNanos)
        ) return null

        val lifetimeNanos = grantLifetimeNanos(policy)
        consumedInPlace = DirectConsumption(
            profileId = profile.profileId,
            source = source.source,
            target = target,
            epoch = currentEpoch,
            observedAtMonotonicNanos = nowNanos,
            lifetimeNanos = lifetimeNanos,
        )
        return authorized(profile, policy, target, nowNanos, lifetimeNanos)
    }

    private fun authorizeDirectTransition(
        profile: SiteProfile,
        policy: ClientAuthPolicy,
        currentUrl: String?,
        target: URI,
        currentEpoch: Long,
        nowNanos: Long,
    ): AuthorizedClientAuthTarget? {
        pending = null
        val source = currentUrl?.let(::strictClientAuthHttpsUri) ?: run {
            return null
        }
        if (!policy.matchesSourceUrl(source) || !policy.matchesRequestUrl(target) ||
            !policy.hasLinkedEphemeralParameters(source, target)
        ) {
            return null
        }
        val previous = consumedDirect
        if (previous != null &&
            previous.profileId == profile.profileId &&
            previous.source == source &&
            previous.target == target &&
            previous.epoch == currentEpoch &&
            !previous.isExpiredOrInvalid(nowNanos)
        ) {
            return null
        }
        val lifetimeNanos = grantLifetimeNanos(policy)
        consumedDirect = DirectConsumption(
            profileId = profile.profileId,
            source = source,
            target = target,
            epoch = currentEpoch,
            observedAtMonotonicNanos = nowNanos,
            lifetimeNanos = lifetimeNanos,
        )
        return authorized(profile, policy, target, nowNanos, lifetimeNanos)
    }

    private fun authorizeRedirectTransition(
        profile: SiteProfile,
        policy: ClientAuthPolicy,
        currentUrl: String?,
        target: URI,
        currentEpoch: Long,
        nowNanos: Long,
    ): AuthorizedClientAuthTarget? {
        if (policy.matchesSourceUrl(target) && currentBelongsTo(profile, currentUrl)) {
            pending = PendingSource(
                profileId = profile.profileId,
                source = target,
                armingEpoch = currentEpoch,
                observedAtMonotonicNanos = nowNanos,
                lifetimeNanos = grantLifetimeNanos(policy),
            )
            return null
        }

        val source = pending
        pending = null
        if (source == null || source.profileId != profile.profileId) return null
        if (currentEpoch != source.armingEpoch && currentEpoch != source.armingEpoch + 1) return null
        if (source.isExpiredOrInvalid(nowNanos)) return null
        if (!policy.matchesSourceUrl(source.source) || !policy.matchesRequestUrl(target)) return null

        return authorized(
            profile = profile,
            policy = policy,
            target = target,
            observedAtMonotonicNanos = nowNanos,
            lifetimeNanos = grantLifetimeNanos(policy),
        )
    }

    private fun authorized(
        profile: SiteProfile,
        policy: ClientAuthPolicy,
        target: URI,
        observedAtMonotonicNanos: Long,
        lifetimeNanos: Long,
    ) = AuthorizedClientAuthTarget(
        profileId = profile.profileId,
        target = target,
        policy = policy,
        certificateRules = profile.certificateRules,
        observedAtMonotonicNanos = observedAtMonotonicNanos,
        lifetimeNanos = lifetimeNanos,
    )

    @Synchronized
    fun onTopLevelPageStarted(url: String, currentEpoch: Long) {
        val source = pending ?: return
        val uri = strictClientAuthHttpsUri(url)
        if (uri != source.source || currentEpoch != source.armingEpoch + 1 ||
            source.isExpiredOrInvalid(monotonicNanos())
        ) {
            pending = null
        }
    }

    @Synchronized
    fun invalidate() {
        clearState()
    }

    private fun clearPending() {
        pending = null
    }

    private fun clearState() {
        clearPending()
        consumedDirect = null
        consumedInPlace = null
    }

    private fun currentBelongsTo(profile: SiteProfile, currentUrl: String?): Boolean {
        val current = currentUrl?.let(::strictClientAuthHttpsUri) ?: return false
        if (current.port !in setOf(-1, 443)) return false
        return runCatching { ExactOrigin.parse("https://${current.host}") }.getOrNull() in profile.initiatorOrigins
    }

    private data class PendingSource(
        val profileId: ProfileId,
        val source: URI,
        val armingEpoch: Long,
        val observedAtMonotonicNanos: Long,
        val lifetimeNanos: Long,
    ) {
        fun isExpiredOrInvalid(nowNanos: Long): Boolean = MonotonicSecurityTime.isExpiredOrInvalid(
            observedAtMonotonicNanos,
            lifetimeNanos,
            nowNanos,
        )
    }

    private data class DirectConsumption(
        val profileId: ProfileId,
        val source: URI,
        val target: URI,
        val epoch: Long,
        val observedAtMonotonicNanos: Long,
        val lifetimeNanos: Long,
    ) {
        fun isExpiredOrInvalid(nowNanos: Long): Boolean = MonotonicSecurityTime.isExpiredOrInvalid(
            observedAtMonotonicNanos,
            lifetimeNanos,
            nowNanos,
        )
    }

    private fun grantLifetimeNanos(policy: ClientAuthPolicy): Long =
        MonotonicSecurityTime.durationNanos(Duration.ofSeconds(policy.grantTtlSeconds.toLong()))

}
