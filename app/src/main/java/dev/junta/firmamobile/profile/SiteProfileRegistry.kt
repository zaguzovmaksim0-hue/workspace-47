package dev.junta.firmamobile.profile

import android.net.Uri
import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI

data class ResolvedSiteProfile(
    val profile: SiteProfile,
    val origin: ExactOrigin,
    val trustMode: TrustMode,
)

class SiteProfileRegistry(
    catalog: SiteProfileCatalog,
    private val buildPolicy: BuildTrustPolicy,
) {
    private val profiles = catalog.profiles.toList()

    init {
        require(catalog.schemaVersion == 1)
        require(profiles.map { it.profileId }.toSet().size == profiles.size)
    }

    fun profile(id: ProfileId): SiteProfile? = profiles.singleOrNull {
        it.profileId == id && isActive(it)
    }

    fun profileMetadata(id: ProfileId): SiteProfile? = profiles.singleOrNull { it.profileId == id }

    fun resolve(uri: URI): ResolvedSiteProfile? {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host == null || uri.userInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        val origin = runCatching { ExactOrigin.parse("https://${uri.host}") }.getOrNull() ?: return null
        return resolve(origin)
    }

    fun resolve(uri: Uri): ResolvedSiteProfile? = runCatching { URI(uri.toString()) }
        .getOrNull()?.let(::resolve)

    fun resolve(origin: TrustedOrigin): ResolvedSiteProfile? =
        ExactOrigin.fromTrusted(origin)?.let(::resolve)

    fun resolveRedirect(activeProfileId: ProfileId, uri: URI): ResolvedSiteProfile? {
        val direct = resolve(uri) ?: return null
        if (direct.profile.profileId != activeProfileId || direct.origin !in direct.profile.redirectOrigins) {
            return null
        }
        return direct.copy(trustMode = TrustMode.TRUSTED_BROWSE)
    }

    private fun resolve(origin: ExactOrigin): ResolvedSiteProfile? {
        val matches = profiles.asSequence()
            .filter(::isActive)
            .mapNotNull { profile -> profile.trustMode(origin)?.let { ResolvedSiteProfile(profile, origin, it) } }
            .toList()
        return matches.singleOrNull()
    }

    private fun isActive(profile: SiteProfile): Boolean = when (profile.activation) {
        ProfileActivation.DISABLED -> false
        ProfileActivation.QA_ONLY -> buildPolicy == BuildTrustPolicy.QA
        ProfileActivation.ENABLED -> true
    }

    private fun SiteProfile.trustMode(origin: ExactOrigin): TrustMode? {
        if (origin in (clientAuthPolicy?.requestOrigins ?: emptySet())) return TrustMode.TRUSTED_CLIENT_AUTH
        if (origin in initiatorOrigins) {
            return if (Capability.SIGN in capabilities || Capability.SELECT_CERTIFICATE in capabilities) {
                TrustMode.TRUSTED_SIGNING
            } else {
                TrustMode.TRUSTED_BROWSE
            }
        }
        if (origin in redirectOrigins) return TrustMode.BROWSE_ONLY
        if (origin in trustedBrowseOrigins) return TrustMode.TRUSTED_BROWSE
        return null
    }
}

object BuiltInSiteProfiles {
    val catalog: SiteProfileCatalog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SiteProfileCatalogParser.parse(JSON)
    }
    val releaseRegistry: SiteProfileRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SiteProfileRegistry(catalog, BuildTrustPolicy.RELEASE)
    }

    const val JSON = """
{
  "schemaVersion": 1,
  "catalogVersion": 1,
  "profiles": [
    {
      "profileId": "junta-andalucia",
      "profileVersion": 1,
      "displayName": "Junta de Andalucía",
      "compatibilityStatus": "EXPERIMENTAL",
      "activation": "ENABLED",
      "startUrl": "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs",
      "initiatorOrigins": ["https://www.juntadeandalucia.es"],
      "redirectOrigins": [
        "https://sede.juntadeandalucia.es",
        "https://ssoweb.juntadeandalucia.es",
        "https://pfirma.juntadeandalucia.es",
        "https://ws050.juntadeandalucia.es"
      ],
      "trustedBrowseOrigins": ["https://ws024.juntadeandalucia.es"],
      "endpoints": [
        {
          "endpointId": "junta-triphase",
          "purpose": "TRIPHASE",
          "url": "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
          "method": "POST",
          "requestContentTypes": ["application/x-www-form-urlencoded; charset=UTF-8"],
          "responseContentTypes": ["text/plain"],
          "maxRequestBytes": 2097152,
          "maxResponseBytes": 2097152,
          "redirects": "DENY"
        }
      ],
      "operationPolicies": [
        {
          "operation": "SIGN",
          "inputAdapterId": "miniapplet-autoscript-v1",
          "callbackContractId": "miniapplet-sign-callback-v1",
          "capabilities": ["SIGN", "LEGACY_SHA1"],
          "endpointId": "junta-triphase",
          "algorithms": ["SHA1_WITH_RSA", "SHA256_WITH_RSA"],
          "format": "CADES",
          "packaging": "DETACHED",
          "mode": "EXPLICIT",
          "fixedExtraProperties": {
            "serverUrl": "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            "mode": "explicit"
          },
          "allowedExtraProperties": []
        }
      ],
      "capabilities": ["SIGN", "LEGACY_SHA1"],
      "clientAuthPolicy": null,
      "certificateRules": {
        "allowedKeyAlgorithms": ["RSA"],
        "requireDigitalSignatureKeyUsage": true
      },
      "evidence": [
        {
          "url": "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs",
          "reviewedOn": "2026-07-15"
        }
      ]
    }
  ]
}
"""
}
