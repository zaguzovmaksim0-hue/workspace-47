package dev.junta.firmamobile.profile

import java.net.URI

// Keep these reviewed login pins separate from Cl@ve's provider contracts.
internal fun validateOwnTlsLoginProfile(profile: SiteProfile) {
    // Navigation-only fixtures carry no sensitive authority; any capability must satisfy the complete pins.
    if (profile.clientAuthPolicy == null && profile.capabilities.isEmpty()) return
    val policy = when (profile.profileId.value) {
        "diputacion-huelva-sede-public" -> {
            require(profile.startUrl == URI("https://sede.diphuelva.es/"))
            require(profile.initiatorOrigins == setOf(ExactOrigin.parse("https://sede.diphuelva.es")))
            require(profile.redirectOrigins.isEmpty())
            ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse("https://authserver.diphuelva.es")),
                sourceUrls = setOf(
                    URI(
                        "https://sede.diphuelva.es/opencms/system/modules/gsede/" +
                            "elements/secciones/autenticacion/autenticacion.jsp",
                    ),
                ),
                requestPath = "/",
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = setOf("ticket"),
                allowEmptyIssuerList = false,
                grantTtlSeconds = 15,
            )
        }
        "comunidad-madrid-cuenta-digital-53f1" -> {
            require(profile.startUrl == URI("https://digital.comunidad.madrid/ext/53F1"))
            require(profile.initiatorOrigins == setOf(ExactOrigin.parse("https://digital.comunidad.madrid")))
            require(profile.redirectOrigins == setOf(ExactOrigin.parse("https://gestiona.comunidad.madrid")))
            ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse("https://gestiona2.comunidad.madrid")),
                sourceUrls = setOf(URI("https://gestiona.comunidad.madrid/auto_login/seleccion-idp.jsf")),
                requestMethod = HttpMethod.POST,
                requestPath = "/auto_certificado/SelCertificado",
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                sourceRequiredEphemeralQueryParameters = setOf("id"),
                allowEmptyIssuerList = false,
                grantTtlSeconds = 15,
            )
        }
        else -> return
    }
    require(profile.profileVersion == 1)
    require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
    require(profile.activation == ProfileActivation.QA_ONLY)
    require(profile.trustedBrowseOrigins.isEmpty())
    require(profile.endpoints.isEmpty() && profile.operationPolicies.isEmpty())
    require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
    require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
    require(profile.clientAuthPolicy == policy)
}
