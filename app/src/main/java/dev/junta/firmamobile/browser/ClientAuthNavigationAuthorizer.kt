package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
        val target = strictHttpsUri(targetUrl) ?: run {
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
        }
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
        val source = currentUrl?.let(::strictHttpsUri) ?: run {
            return null
        }
        if (source !in policy.sourceUrls || !target.matches(policy)) {
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
        if (target in policy.sourceUrls && currentBelongsTo(profile, currentUrl)) {
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
        if (source.source !in policy.sourceUrls || !target.matches(policy)) return null

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
        val uri = strictHttpsUri(url)
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
    }

    private fun currentBelongsTo(profile: SiteProfile, currentUrl: String?): Boolean {
        val current = currentUrl?.let(::strictHttpsUri) ?: return false
        return runCatching { ExactOrigin.parse("https://${current.host}") }.getOrNull() in profile.initiatorOrigins
    }

    private fun URI.matches(policy: ClientAuthPolicy): Boolean {
        if (rawPath != policy.requestPath || rawFragment != null) return false
        val origin = runCatching { ExactOrigin.parse("https://$host") }.getOrNull() ?: return false
        if (origin !in policy.requestOrigins) return false
        val expectedNames = policy.fixedQueryParameters.keys + policy.requiredEphemeralQueryParameters
        if (expectedNames.isEmpty()) return rawQuery == null
        val parameters = parseQuery(rawQuery ?: return false) ?: return false
        if (parameters.keys != expectedNames) return false
        if (policy.fixedQueryParameters.any { (name, value) ->
            val paramValue = parameters[name] ?: return false
            !isEquivalentQueryParameter(name, paramValue, value)
        }) return false
        return policy.requiredEphemeralQueryParameters.all { name ->
            val value = parameters[name]
            value != null && value.isNotEmpty() && value.length <= MAX_EPHEMERAL_CHARS &&
                value.none(Char::isISOControl)
        }
    }

    private fun isEquivalentQueryParameter(name: String, paramValue: String, expectedValue: String): Boolean {
        if (paramValue == expectedValue) return true
        if (name == "comeBackURL") {
            val paramDecoded = decodeStrictBase64(paramValue) ?: return false
            val expectedDecoded = decodeStrictBase64(expectedValue) ?: return false
            return paramDecoded.contentEquals(expectedDecoded)
        }
        return false
    }

    private fun decodeStrictBase64(input: String): ByteArray? {
        if (input.any(Char::isWhitespace)) return null
        if (input.length % 4 == 1) return null
        val normalized = input.replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> normalized
        }
        return runCatching {
            java.util.Base64.getDecoder().decode(padded)
        }.getOrNull()
    }

    private fun parseQuery(rawQuery: String): Map<String, String>? = runCatching {
        if (rawQuery.isEmpty() || rawQuery.length > MAX_QUERY_CHARS) return null
        val result = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return null
            val name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8.name())
            if (!PARAMETER_NAME.matches(name) || result.put(name, value) != null) return null
        }
        result
    }.getOrNull()

    private fun strictHttpsUri(raw: String): URI? = runCatching {
        require(raw.length <= MAX_URL_CHARS && raw.none(Char::isISOControl))
        val uri = URI(raw)
        require(!uri.isOpaque && uri.scheme == "https" && uri.host != null && uri.userInfo == null)
        require(uri.port == -1 || uri.port == 443)
        ExactOrigin.parse("https://${uri.host}")
        uri
    }.getOrNull()

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

    private companion object {
        const val MAX_URL_CHARS = 8_192
        const val MAX_QUERY_CHARS = 4_096
        const val MAX_EPHEMERAL_CHARS = 1_024
        val PARAMETER_NAME = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    }
}
