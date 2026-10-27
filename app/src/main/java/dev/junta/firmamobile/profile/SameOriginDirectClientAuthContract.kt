package dev.junta.firmamobile.profile

/** Reviewed owners of same-origin direct client auth; catalog data cannot add a profile to this set. */
internal object SameOriginDirectClientAuthContract {
    // Preserve the old overlap predicate; each owner's complete policy is pinned separately by the parser.
    fun allowsSameOriginDirect(profile: SiteProfile): Boolean {
        val policy = profile.clientAuthPolicy ?: return false
        val origins = policy.requestOrigins
        val transitionMatches = profile.profileId.value in PROFILE_IDS &&
            policy.transitionMode == ClientAuthTransitionMode.DIRECT_FROM_SOURCE &&
            policy.requestPort == HTTPS_PORT
        val originsMatch = origins.size == 1 &&
            policy.sourceUrls.all { ExactOrigin.parse("https://${it.host}") in origins }
        val queryBound = policy.fixedQueryParameters.isNotEmpty() ||
            policy.requiredEphemeralQueryParameters.isNotEmpty() ||
            profile.profileId.value == PATTEX_PROFILE_ID
        return transitionMatches && originsMatch && queryBound
    }

    private const val PATTEX_PROFILE_ID = "extremadura-pattex-client-auth"
    private val PROFILE_IDS = setOf(
        "ministerio-sanidad-certificado",
        "navarra-sede-registro-general",
        "menorca-carpeta-ciutadana",
        PATTEX_PROFILE_ID,
        "diputacion-soria-sede-client-auth",
    )
    private const val HTTPS_PORT = 443
}
