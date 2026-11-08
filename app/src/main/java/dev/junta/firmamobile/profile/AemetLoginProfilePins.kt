package dev.junta.firmamobile.profile

import java.net.URI

// Independent pins: catalog changes cannot turn the login's AUTO alias into general document signing.
internal fun validateAemetLoginProfile(profile: SiteProfile) {
    if (profile.profileId.value != "aemet-public-solicitud-navigation") return
    if (profile.capabilities.isEmpty() && profile.clientAuthPolicy == null) return
    require(profile.profileVersion == 2)
    require(profile.displayName == "AEMET — Solicitud certificados y datos")
    require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
    require(profile.activation == ProfileActivation.QA_ONLY)
    require(profile.startUrl == URI("https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home"))
    require(profile.initiatorOrigins == setOf(ExactOrigin.parse("https://sede.aemet.gob.es")))
    require(profile.redirectOrigins.isEmpty() && profile.trustedBrowseOrigins.isEmpty())
    require(profile.endpoints.isEmpty() && profile.clientAuthPolicy == null)
    require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1))
    require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
    require(profile.operationPolicies == mapOf(
        ProtocolOperation.SIGN to OperationPolicy(
            operation = ProtocolOperation.SIGN,
            safeDescription = "Acceso con certificado a la Sede electrónica de AEMET",
            inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
            callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
            capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
            endpointId = null,
            algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
            format = SignatureFormat.CADES,
            packaging = SignaturePackaging.DETACHED,
            mode = SignatureMode.EXPLICIT,
            fixedExtraProperties = emptyMap(),
            allowedExtraProperties = emptySet(),
        ),
    ))
    require(profile.evidence.map { it.url }.toSet().size == profile.evidence.size)
    require(profile.evidence.associate { it.url.toASCIIString() to it.reviewedOn.toString() } ==
        listOf(
            "https://www.aemet.es/es/sede_electronica",
            "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home",
            "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/solicitudes",
            "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/nuevaSolicitud",
            "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/formularioSolicitud?tipoSolicitud=L1",
            "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso",
        ).associateWith { "2026-08-23" } + listOf(
            "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/selectLogin",
            "https://sede.aemet.gob.es/AEMET/htdocs/web/js/firma.js",
            "https://sede.aemet.gob.es/AEMET/htdocs/web/js/miniapplet.js",
        ).associateWith { "2026-11-08" })
}
