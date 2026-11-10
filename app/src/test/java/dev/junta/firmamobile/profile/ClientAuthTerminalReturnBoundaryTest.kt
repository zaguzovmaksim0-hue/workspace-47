package dev.junta.firmamobile.profile

import java.net.URI
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic boundary fixtures only; this creates no catalog profile or certificate grant. */
class ClientAuthTerminalReturnBoundaryTest {
    private val returnConstraint = ClientAuthUrlConstraint(
        origin = ExactOrigin.parse("https://portal.example"),
        path = "/signin",
        requiredEphemeralQueryParameters = setOf("code", "state"),
    )
    private val sourceDescriptor = ClientAuthUrlConstraint(
        origin = ExactOrigin.parse("https://portal.example"),
        path = "/signin",
    )
    private val policy = ClientAuthPolicy(
        transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
        requestOrigins = setOf(ExactOrigin.parse("https://auth.example")),
        sourceUrls = setOf(URI("https://portal.example/start")),
        requestPath = "/certificate",
        fixedQueryParameters = mapOf("client" to "fixture"),
        requiredEphemeralQueryParameters = setOf("state"),
        allowEmptyIssuerList = false,
        grantTtlSeconds = 15,
        sourceFixedQueryParameters = mapOf("client" to "fixture"),
        sourceRequiredEphemeralQueryParameters = setOf("returnTo"),
        sourceBase64UrlConstraints = mapOf("returnTo" to sourceDescriptor),
        returnUrlConstraints = setOf(returnConstraint),
    )
    private val certificateRequest = URI("https://auth.example/certificate?client=fixture&state=fixture-state")
    private val queryReturn = "https://portal.example/signin?code=fixture-code&state=fixture-state"

    @Test fun aQueryReturnConstraintDoesNotAuthorizeFragmentsOrDifferentScopeOrState() {
        assertTrue(policy.matchesReturnUrl(URI(queryReturn), certificateRequest))
        // A session-bound return cannot independently become a general profile browse URL.
        assertFalse(policy.matchesReturnUrl(URI(queryReturn)))
        val rejected = listOf(
            queryReturn.replace("?", "#"),
            "$queryReturn#extra=value",
            queryReturn.replace("portal.example", "other.example"),
            queryReturn.replace("portal.example", "portal.example:8443"),
            queryReturn.replace("/signin", "/other"),
            "$queryReturn&extra=value",
            "$queryReturn&code=duplicate",
            queryReturn.replace("fixture-state", "different-state"),
        )
        rejected.forEach { url ->
            assertFalse(url, policy.matchesReturnUrl(URI(url), certificateRequest))
        }
    }

    @Test fun certificateAndSourceRequestsContinueToRejectFragments() {
        val source = sourceUrl("https://portal.example/signin")
        assertTrue(policy.matchesSourceUrl(source))
        assertTrue(policy.matchesRequestUrl(certificateRequest))
        assertFalse(policy.matchesSourceUrl(URI("$source#state=fixture-state")))
        assertFalse(policy.matchesRequestUrl(URI("$certificateRequest#state=fixture-state")))
    }

    @Test fun sourceCarriedReturnDescriptorDoesNotAcquireFragmentAuthority() {
        assertTrue(policy.matchesSourceUrl(sourceUrl("https://portal.example/signin")))
        assertFalse(policy.matchesSourceUrl(sourceUrl("https://portal.example/signin#code=fixture-code")))
    }

    private fun sourceUrl(descriptor: String): URI {
        val encoded = Base64.getUrlEncoder().encodeToString(descriptor.toByteArray(Charsets.UTF_8))
        return URI("https://portal.example/start?client=fixture&returnTo=$encoded")
    }
}
