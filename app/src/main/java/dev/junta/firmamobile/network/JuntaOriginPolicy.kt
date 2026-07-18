package dev.junta.firmamobile.network

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.net.IDN
import java.util.Locale

data class TrustedOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
) {
    val serialized: String
        get() = if (scheme == HTTPS_SCHEME && port == HTTPS_PORT) {
            "$scheme://$host"
        } else {
            "$scheme://$host:$port"
        }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val HTTPS_PORT = 443
    }
}

object JuntaOriginPolicy {
    const val START_URL =
        "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"

    val allowedHosts: Set<String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BuiltInSiteProfiles.catalog.profiles.asSequence()
            .mapNotNull { BuiltInSiteProfiles.releaseRegistry.profile(it.profileId) }
            .flatMap { profile ->
                (profile.initiatorOrigins + profile.redirectOrigins + profile.trustedBrowseOrigins)
                    .asSequence()
            }
            .mapTo(linkedSetOf()) { it.host }
    }

    val webMessageOriginRules: Set<String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BuiltInSiteProfiles.catalog.profiles.asSequence()
            .mapNotNull { BuiltInSiteProfiles.releaseRegistry.profile(it.profileId) }
            .flatMap { it.initiatorOrigins.asSequence() }
            .mapTo(linkedSetOf()) { it.serialized }
    }

    fun isAllowed(uri: Uri): Boolean = originFor(uri) != null

    fun isAllowed(origin: TrustedOrigin): Boolean =
        origin.scheme.equals(HTTPS_SCHEME, ignoreCase = true) &&
            origin.port == HTTPS_PORT &&
            normalizeHost(origin.host) in allowedHosts

    fun originFor(uri: Uri): TrustedOrigin? {
        if (uri.isOpaque || !uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true)) return null
        if (uri.encodedUserInfo != null) return null
        val host = normalizeHost(uri.host ?: return null) ?: return null
        if (host !in allowedHosts) return null
        val port = try {
            uri.port.takeIf { it != -1 } ?: HTTPS_PORT
        } catch (_: Exception) {
            return null
        }
        if (port != HTTPS_PORT) return null
        return TrustedOrigin(HTTPS_SCHEME, host, HTTPS_PORT)
    }

    private fun normalizeHost(host: String): String? = try {
        IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val HTTPS_SCHEME = "https"
    private const val HTTPS_PORT = 443
}
