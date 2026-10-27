package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SameOriginDirectClientAuthContractTest {
    private val profiles = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON).profiles

    @Test
    fun canonicalCatalogGrantsTheExceptionToExactlyTheReviewedOwners() {
        val allowed = profiles.filter(SameOriginDirectClientAuthContract::allowsSameOriginDirect)
        assertEquals(REVIEWED, allowed.map { it.profileId.value }.toSet())
    }

    @Test
    fun predicateRejectsOwnerTransitionPortOriginSourceAndQueryMutations() {
        REVIEWED.forEach { id ->
            val profile = profiles.single { it.profileId.value == id }
            val policy = profile.clientAuthPolicy!!
            val foreign = ExactOrigin.parse("https://foreign.example")
            val mutations = mapOf(
                "unreviewed owner" to profile.copy(profileId = ProfileId("unreviewed-same-origin")),
                "no policy" to profile.copy(clientAuthPolicy = null),
                "redirect" to profile.withPolicy(
                    policy.copy(transitionMode = ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE),
                ),
                "port" to profile.withPolicy(policy.copy(requestPort = 444)),
                "second origin" to profile.withPolicy(policy.copy(requestOrigins = policy.requestOrigins + foreign)),
                "lookalike source" to profile.withPolicy(
                    policy.copy(sourceUrls = policy.sourceUrls + URI("https://${policy.requestOrigins.single().host}.evil.example/")),
                ),
            )
            mutations.forEach { (label, mutated) ->
                assertFalse("$id: $label", SameOriginDirectClientAuthContract.allowsSameOriginDirect(mutated))
            }
            val unbound = profile.withPolicy(
                policy.copy(fixedQueryParameters = emptyMap(), requiredEphemeralQueryParameters = emptySet()),
            )
            assertEquals(
                "$id: query binding",
                id == "extremadura-pattex-client-auth",
                SameOriginDirectClientAuthContract.allowsSameOriginDirect(unbound),
            )
        }
    }

    private fun SiteProfile.withPolicy(policy: ClientAuthPolicy) = copy(clientAuthPolicy = policy)

    private companion object {
        val REVIEWED = setOf(
            "ministerio-sanidad-certificado",
            "navarra-sede-registro-general",
            "menorca-carpeta-ciutadana",
            "extremadura-pattex-client-auth",
            "diputacion-soria-sede-client-auth",
        )
    }
}
