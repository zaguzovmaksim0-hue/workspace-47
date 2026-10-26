package dev.junta.firmamobile.profile

import java.net.URI

/** Independent pins for the reviewed La Rioja CAS contract; catalog data cannot widen them. */
internal object LaRiojaClientAuthContract {
    fun expectedPolicy(): ClientAuthPolicy = ClientAuthPolicy(
        transitionMode = ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE,
        requestOrigins = setOf(ExactOrigin.parse(ORIGIN)),
        sourceUrls = setOf(URI(SOURCE_URL)),
        requestPath = "/clientcertSSL/login",
        fixedQueryParameters = emptyMap(),
        requiredEphemeralQueryParameters = emptySet(),
        allowEmptyIssuerList = true,
        grantTtlSeconds = GRANT_TTL_SECONDS,
        requestPort = HTTPS_PORT,
        sourceFixedQueryParameters = mapOf("inst" to "G", "apli" to "OFIVIR", "nodo" to "CIUDANO"),
        sourceRequiredEphemeralQueryParameters = setOf("param", "TARGET"),
        linkedEphemeralQueryParameters = emptySet(),
    )

    // Preserve the old overlap predicate; the parser separately requires the complete policy.
    fun allowsSameOriginRedirect(profile: SiteProfile): Boolean {
        val policy = profile.clientAuthPolicy ?: return false
        val expected = expectedPolicy()
        val transitionMatches = profile.profileId.value == PROFILE_ID &&
            policy.transitionMode == expected.transitionMode && policy.requestPort == HTTPS_PORT
        val originsMatch = policy.requestOrigins == profile.initiatorOrigins &&
            policy.sourceUrls == expected.sourceUrls
        val queriesMatch = policy.fixedQueryParameters.isEmpty() &&
            policy.requiredEphemeralQueryParameters.isEmpty() &&
            policy.sourceFixedQueryParameters == expected.sourceFixedQueryParameters &&
            policy.sourceRequiredEphemeralQueryParameters == expected.sourceRequiredEphemeralQueryParameters
        return transitionMatches && originsMatch && queriesMatch
    }

    private const val PROFILE_ID = "la-rioja-oficina-electronica"
    private const val ORIGIN = "https://ias1.larioja.org"
    private const val SOURCE_URL = "https://ias1.larioja.org/casLR/login"
    private const val HTTPS_PORT = 443
    private const val GRANT_TTL_SECONDS = 15
}
