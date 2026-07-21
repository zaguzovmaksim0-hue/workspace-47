package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.SiteProfileRegistry
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant

@ConsistentCopyVisibility
data class AuthorizedClientAuthTarget internal constructor(
    val profileId: ProfileId,
    internal val target: URI,
    internal val policy: ClientAuthPolicy,
    internal val certificateRules: dev.junta.firmamobile.profile.CertificateFilterRules,
    internal val expiresAt: Instant,
)

/**
 * Arms client TLS only after the exact source navigation and consumes it for one
 * exact, top-level authentication-facade redirect. Ephemeral values are checked
 * in memory and never retained separately.
 */
class ClientAuthNavigationAuthorizer internal constructor(
    private val registry: SiteProfileRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var pending: PendingSource? = null

    @Synchronized
    fun observeTopLevelNavigation(
        activeProfileId: ProfileId?,
        currentUrl: String?,
        targetUrl: String,
        currentEpoch: Long,
        isModernMainFrameRequest: Boolean,
    ): AuthorizedClientAuthTarget? {
        if (!isModernMainFrameRequest || activeProfileId == null || currentEpoch == Long.MAX_VALUE) {
            pending = null
            return null
        }
        val profile = registry.profile(activeProfileId) ?: run {
            pending = null
            return null
        }
        val policy = profile.clientAuthPolicy ?: run {
            pending = null
            return null
        }
        val target = strictHttpsUri(targetUrl) ?: run {
            pending = null
            return null
        }
        val now = clock.instant()

        if (target in policy.sourceUrls && currentBelongsTo(profile, currentUrl)) {
            pending = PendingSource(
                profileId = profile.profileId,
                source = target,
                armingEpoch = currentEpoch,
                expiresAt = now.plusSeconds(policy.grantTtlSeconds.toLong()),
            )
            return null
        }

        val source = pending
        pending = null
        if (source == null) {
            return null
        }
        if (source.profileId != profile.profileId) {
            return null
        }
        if (currentEpoch != source.armingEpoch && currentEpoch != source.armingEpoch + 1) {
            return null
        }
        if (!now.isBefore(source.expiresAt)) {
            return null
        }
        if (source.source !in policy.sourceUrls) {
            return null
        }
        if (!target.matches(policy)) {
            return null
        }

        return AuthorizedClientAuthTarget(
            profileId = profile.profileId,
            target = target,
            policy = policy,
            certificateRules = profile.certificateRules,
            expiresAt = now.plusSeconds(policy.grantTtlSeconds.toLong()),
        )
    }

    @Synchronized
    fun onTopLevelPageStarted(url: String, currentEpoch: Long) {
        val source = pending ?: return
        val uri = strictHttpsUri(url)
        if (uri != source.source || currentEpoch != source.armingEpoch + 1 ||
            !clock.instant().isBefore(source.expiresAt)
        ) {
            pending = null
        }
    }

    @Synchronized
    fun invalidate() {
        pending = null
    }

    private fun currentBelongsTo(profile: SiteProfile, currentUrl: String?): Boolean {
        val current = currentUrl?.let(::strictHttpsUri) ?: return false
        return runCatching { ExactOrigin.parse("https://${current.host}") }.getOrNull() in profile.initiatorOrigins
    }

    private fun URI.matches(policy: ClientAuthPolicy): Boolean {
        if (rawPath != policy.requestPath || rawFragment != null) return false
        val origin = runCatching { ExactOrigin.parse("https://$host") }.getOrNull() ?: return false
        if (origin !in policy.requestOrigins) return false
        val parameters = parseQuery(rawQuery ?: return false) ?: return false
        val expectedNames = policy.fixedQueryParameters.keys + policy.requiredEphemeralQueryParameters
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
        val expiresAt: Instant,
    )

    private companion object {
        const val MAX_URL_CHARS = 8_192
        const val MAX_QUERY_CHARS = 4_096
        const val MAX_EPHEMERAL_CHARS = 1_024
        val PARAMETER_NAME = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    }
}
