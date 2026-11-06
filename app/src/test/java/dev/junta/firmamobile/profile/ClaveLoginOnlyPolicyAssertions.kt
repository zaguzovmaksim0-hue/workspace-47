package dev.junta.firmamobile.profile

import org.junit.Assert.assertEquals
import java.net.URI

// Tenerife and CAIB prove only the Cl@ve certificate login: their signature is the filing itself.
fun assertClaveLoginOnlyPolicy(profile: SiteProfile) {
    val policy = checkNotNull(profile.clientAuthPolicy)
    assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
    assertEquals(HttpMethod.POST, policy.requestMethod)
    assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
    assertEquals(setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")), policy.sourceUrls)
    assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
    assertEquals(443, policy.requestPort)
    assertEquals(15, policy.grantTtlSeconds)
    assertEquals(
        setOf("2026-11-06"),
        profile.evidence.filter { it.url.host.endsWith("clave.gob.es") }.map { it.reviewedOn.toString() }.toSet(),
    )
}
