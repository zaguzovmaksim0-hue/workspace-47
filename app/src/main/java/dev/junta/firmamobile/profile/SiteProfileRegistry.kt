package dev.junta.firmamobile.profile

import android.net.Uri
import dev.junta.firmamobile.BuildConfig
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
        ProfileActivation.ENABLED -> buildPolicy == BuildTrustPolicy.QA || profile.isReleaseEligible()
    }

    private fun SiteProfile.isReleaseEligible(): Boolean {
        val hasSensitiveCapability = capabilities.any {
            it == Capability.SIGN ||
                it == Capability.SELECT_CERTIFICATE ||
                it == Capability.CLIENT_TLS_AUTH
        }
        return !hasSensitiveCapability || compatibilityStatus == CompatibilityStatus.VERIFIED_E2E
    }

    private fun SiteProfile.trustMode(origin: ExactOrigin): TrustMode? {
        if (origin in (clientAuthPolicy?.requestOrigins ?: emptySet())) return TrustMode.BROWSE_ONLY
        if (origin in initiatorOrigins) {
            return when {
                Capability.SIGN in capabilities || Capability.SELECT_CERTIFICATE in capabilities ->
                    TrustMode.TRUSTED_SIGNING
                Capability.CLIENT_TLS_AUTH in capabilities -> TrustMode.TRUSTED_CLIENT_AUTH
                else -> TrustMode.TRUSTED_BROWSE
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
    val qaRegistry: SiteProfileRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SiteProfileRegistry(catalog, BuildTrustPolicy.QA)
    }
    val runtimeRegistry: SiteProfileRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (BuildConfig.ALLOW_QA_PROFILES) qaRegistry else releaseRegistry
    }

    const val JSON = """
{
  "schemaVersion": 1,
  "catalogVersion": 4,
  "profiles": [
    {
      "profileId": "junta-andalucia",
      "profileVersion": 1,
      "displayName": "Junta de Andalucía",
      "compatibilityStatus": "VERIFIED_E2E",
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
          "safeDescription": "Autenticación con certificado",
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
            "filters": "keyusage.digitalsignature:true;nonexpired:"
          },
          "allowedExtraProperties": []
        }
      ],
      "capabilities": ["SIGN", "LEGACY_SHA1", "AFIRMA_URI"],
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
    },
    {
      "profileId": "reg-age-redsara",
      "profileVersion": 1,
      "displayName": "Registro Electrónico General (REG-AGE)",
      "compatibilityStatus": "VERIFIED_CONTRACT",
      "activation": "QA_ONLY",
      "startUrl": "https://reg.redsara.es/es/",
      "initiatorOrigins": ["https://reg.redsara.es"],
      "redirectOrigins": [],
      "trustedBrowseOrigins": [],
      "endpoints": [],
      "operationPolicies": [
        {
          "operation": "SIGN",
          "safeDescription": "Firma del resumen XML del registro",
          "inputAdapterId": "miniapplet-autoscript-v1",
          "callbackContractId": "autoscript-sign-callback-v1",
          "capabilities": ["SIGN"],
          "endpointId": null,
          "algorithms": ["SHA512_WITH_RSA"],
          "format": "XADES",
          "packaging": "DETACHED",
          "mode": null,
          "fixedExtraProperties": {},
          "allowedExtraProperties": []
        }
      ],
      "capabilities": ["SIGN"],
      "clientAuthPolicy": null,
      "certificateRules": {
        "allowedKeyAlgorithms": ["RSA"],
        "requireDigitalSignatureKeyUsage": true
      },
      "evidence": [
        {
          "url": "https://reg.redsara.es/es/chunk-64DWZJJG.js",
          "reviewedOn": "2026-07-18"
        },
        {
          "url": "https://reg.redsara.es/es/scripts-IIDJLUBL.js",
          "reviewedOn": "2026-07-18"
        }
      ]
    },
    {
      "profileId": "unizar-tramitador",
      "profileVersion": 1,
      "displayName": "Universidad de Zaragoza — Oficina Virtual",
      "compatibilityStatus": "VERIFIED_CONTRACT",
      "activation": "QA_ONLY",
      "startUrl": "https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent",
      "initiatorOrigins": ["https://tramita.unizar.es"],
      "redirectOrigins": [],
      "trustedBrowseOrigins": [],
      "endpoints": [
        {
          "endpointId": "unizar-triphase",
          "purpose": "TRIPHASE",
          "url": "https://tramita.unizar.es/afirma-server-triphase-signer-2.7.3/SignatureService",
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
          "safeDescription": "Acceso con certificado a la Oficina Virtual",
          "inputAdapterId": "miniapplet-autoscript-v1",
          "callbackContractId": "autoscript-sign-callback-v1",
          "capabilities": ["SIGN", "LEGACY_SHA1"],
          "endpointId": "unizar-triphase",
          "algorithms": ["SHA1_WITH_RSA"],
          "format": "CADES",
          "packaging": "DETACHED",
          "mode": null,
          "fixedExtraProperties": {
            "precalculatedHashAlgorithm": "SHA1",
            "serverUrl": "https://tramita.unizar.es/afirma-server-triphase-signer-2.7.3/SignatureService"
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
          "url": "https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent",
          "reviewedOn": "2026-07-18"
        },
        {
          "url": "https://tramita.unizar.es/tramitador/js/implementaciones/implementacionIFirma_ES.js",
          "reviewedOn": "2026-07-18"
        }
      ]
    },
    {
      "profileId": "carne-joven-andalucia",
      "profileVersion": 1,
      "displayName": "Carné Joven Europeo de Andalucía",
      "compatibilityStatus": "VERIFIED_CONTRACT",
      "activation": "QA_ONLY",
      "startUrl": "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp",
      "initiatorOrigins": ["https://ws104.juntadeandalucia.es"],
      "redirectOrigins": [],
      "trustedBrowseOrigins": [],
      "endpoints": [],
      "operationPolicies": [],
      "capabilities": ["CLIENT_TLS_AUTH"],
      "clientAuthPolicy": {
        "requestOrigins": ["https://ws235.juntadeandalucia.es"],
        "sourceUrls": ["https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"],
        "requestPath": "/authenticationFacade",
        "fixedQueryParameters": {
          "action": "validateCert",
          "appId": "IAJ.CARNETJOVEN",
          "comeBackURL": "https://ws104.juntadeandalucia.es/carneJoven/servlet/ReturnAuthenticationServlet"
        },
        "requiredEphemeralQueryParameters": ["ticketId", "webSessionId"],
        "allowEmptyIssuerList": true,
        "grantTtlSeconds": 15
      },
      "certificateRules": {
        "allowedKeyAlgorithms": ["RSA"],
        "requireDigitalSignatureKeyUsage": true
      },
      "evidence": [
        {
          "url": "https://www.juntadeandalucia.es/servicios/sede/tramites/procedimientos/detalle/24721.html",
          "reviewedOn": "2026-07-18"
        },
        {
          "url": "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet",
          "reviewedOn": "2026-07-18"
        }
      ]
    }
  ]
}
"""
}
