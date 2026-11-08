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
        "boe-sede-public-home" -> boeLoginPolicy(profile)
        "cmt-public-navigation" -> cmtLoginPolicy(profile)
        else -> return
    }
    val version = when (profile.profileId.value) {
        "boe-sede-public-home", "cmt-public-navigation" -> 2
        else -> 1
    }
    require(profile.profileVersion == version)
    require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
    require(profile.activation == ProfileActivation.QA_ONLY)
    require(profile.trustedBrowseOrigins.isEmpty())
    require(profile.endpoints.isEmpty() && profile.operationPolicies.isEmpty())
    require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
    require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
    require(profile.clientAuthPolicy == policy)
}

private fun boeLoginPolicy(profile: SiteProfile): ClientAuthPolicy {
    require(profile.displayName == "BOE — identificación de anuncios BOE/BORME")
    require(profile.startUrl == URI("https://extranet.boe.es/anuncios/"))
    require(profile.initiatorOrigins == setOf(ExactOrigin.parse("https://extranet.boe.es")))
    require(profile.redirectOrigins.isEmpty())
    require(profile.evidence.associate { it.url.toASCIIString() to it.reviewedOn.toString() } == mapOf(
        "https://www.boe.es/informacion/index.php" to "2026-08-23",
        "https://www.boe.es/informacion/firma_electronica/sistemas_firma_electronica.php" to "2026-08-23",
        "https://extranet.boe.es/anuncios/" to "2026-11-08",
        "https://extranet.boe.es/id/check-cert.php?referer=https://extranet.boe.es/anuncios/" to "2026-11-08",
        "https://check-cert.extranet.boe.es/" to "2026-11-08",
    ))
    return ClientAuthPolicy(
        transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
        requestOrigins = setOf(ExactOrigin.parse("https://check-cert.extranet.boe.es")),
        sourceUrls = setOf(
            URI("https://extranet.boe.es/id/check-cert.php?referer=https://extranet.boe.es/anuncios/"),
        ),
        requestPath = "/",
        fixedQueryParameters = emptyMap(),
        requiredEphemeralQueryParameters = emptySet(),
        allowEmptyIssuerList = true,
        grantTtlSeconds = 15,
    )
}

private fun cmtLoginPolicy(profile: SiteProfile): ClientAuthPolicy {
    require(profile.displayName == "CMT — consulta de sus trámites")
    require(profile.startUrl == URI("https://sede.cmt.gob.es/"))
    require(profile.initiatorOrigins == setOf(ExactOrigin.parse("https://sede.cmt.gob.es")))
    require(profile.redirectOrigins.isEmpty())
    require(profile.evidence.associate { it.url.toASCIIString() to it.reviewedOn.toString() } == mapOf(
        "https://sede.cmt.gob.es/" to "2026-08-23",
        "https://sede.cmt.gob.es/catalogoservicios.aspx" to "2026-08-23",
        "https://sede.cmt.gob.es/carpetaciudadana.aspx" to "2026-11-08",
        "https://sede.cmt.gob.es/carpetaciudadana/login.aspx?returnUrl=" +
            "https%3a%2f%2fsede.cmt.gob.es%2fcarpetaciudadana%2fcarpeta_ciudadana.aspx" to "2026-11-08",
        "https://sede.cmt.gob.es/segex/identificacion_opciones.aspx" to "2026-11-08",
        "https://identificacionssl.sedipualba.es/" to "2026-11-08",
    ))
    return ClientAuthPolicy(
        transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
        requestOrigins = setOf(ExactOrigin.parse("https://identificacionssl.sedipualba.es")),
        sourceUrls = setOf(
            URI(
                "https://sede.cmt.gob.es/carpetaciudadana/login.aspx?returnUrl=" +
                    "https%3a%2f%2fsede.cmt.gob.es%2fcarpetaciudadana%2fcarpeta_ciudadana.aspx",
            ),
        ),
        requestPath = "/",
        fixedQueryParameters = mapOf("idioma" to "es", "entidad" to "99006"),
        requiredEphemeralQueryParameters = setOf("idtoken"),
        allowEmptyIssuerList = true,
        grantTtlSeconds = 15,
    )
}
