package dev.junta.firmamobile.network

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ExactOrigin
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
            .mapNotNull { BuiltInSiteProfiles.runtimeRegistry.profile(it.profileId) }
            .flatMap { profile ->
                (browserOrigins(profile.profileId) +
                    (profile.clientAuthPolicy?.requestOrigins ?: emptySet()))
                    .asSequence()
            }
            .mapTo(linkedSetOf()) { it.host }
    }

    fun browserOrigins(profileId: ProfileId): Set<ExactOrigin> {
        val profile = BuiltInSiteProfiles.runtimeRegistry.profile(profileId) ?: return emptySet()
        return profile.initiatorOrigins + profile.redirectOrigins + profile.trustedBrowseOrigins
    }

    fun browserAllowedHosts(profileId: ProfileId): Set<String> =
        browserOrigins(profileId).mapTo(linkedSetOf()) { it.host }

    fun webMessageOriginRules(profileId: ProfileId): Set<String> {
        val profile = BuiltInSiteProfiles.runtimeRegistry.profile(profileId) ?: return emptySet()
        if (profileId.value == EUSKADI_PROFILE_ID &&
            profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH)
        ) {
            return profile.redirectOrigins
                .filterTo(linkedSetOf()) { it.serialized == EUSKADI_IZENPE_ORIGIN }
                .mapTo(linkedSetOf()) { it.serialized }
        }
        val exposesNativeBridge = profile.capabilities.any { capability ->
            capability == Capability.SIGN ||
                capability == Capability.SELECT_CERTIFICATE ||
                capability == Capability.AFIRMA_URI
        }
        if (!exposesNativeBridge) return emptySet()
        return profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized }
    }

    fun isAllowed(uri: Uri): Boolean = originFor(uri) != null

    fun isAllowed(uri: Uri, profileId: ProfileId): Boolean =
        originFor(uri, profileId) != null

    fun isAllowed(origin: TrustedOrigin): Boolean =
        origin.scheme.equals(HTTPS_SCHEME, ignoreCase = true) &&
            origin.port == HTTPS_PORT &&
            normalizeHost(origin.host) in allowedHosts

    fun originFor(uri: Uri): TrustedOrigin? {
        val origin = canonicalOrigin(uri) ?: return null
        return origin.takeIf { it.host in allowedHosts }
    }

    fun originFor(uri: Uri, profileId: ProfileId): TrustedOrigin? {
        val origin = canonicalOrigin(uri) ?: return null
        val exact = ExactOrigin.fromTrusted(origin) ?: return null
        return origin.takeIf { exact in browserOrigins(profileId) }
    }

    fun signingOriginFor(uri: Uri, profileId: ProfileId): TrustedOrigin? {
        val profile = BuiltInSiteProfiles.runtimeRegistry.profile(profileId) ?: return null
        val exposesSigningProtocol = profile.capabilities.any { capability ->
            capability == Capability.SIGN ||
                capability == Capability.SELECT_CERTIFICATE ||
                capability == Capability.AFIRMA_URI
        }
        if (!exposesSigningProtocol) return null
        val origin = canonicalOrigin(uri) ?: return null
        val exact = ExactOrigin.fromTrusted(origin) ?: return null
        return origin.takeIf { exact in profile.initiatorOrigins }
    }

    private fun canonicalOrigin(uri: Uri): TrustedOrigin? {
        if (uri.isOpaque || !uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true)) return null
        if (uri.encodedUserInfo != null) return null
        val host = normalizeHost(uri.host ?: return null) ?: return null
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

    private const val EUSKADI_PROFILE_ID = "euskadi-sede-electronica"
    private const val EUSKADI_IZENPE_ORIGIN = "https://eidas.izenpe.com"
    private const val HTTPS_SCHEME = "https"
    private const val HTTPS_PORT = 443
}
