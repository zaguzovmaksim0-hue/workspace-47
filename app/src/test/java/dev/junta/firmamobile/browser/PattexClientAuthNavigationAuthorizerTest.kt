package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PattexClientAuthNavigationAuthorizerTest {
    private val profileId = ProfileId("extremadura-pattex-client-auth")
    private val source = "https://pattex.juntaex.es/PATTEX/externos.jsf?info=060~user~pass~SEDE_ALTA~https://pattex.juntaex.es~codigo"
    private val target = "https://pattex.juntaex.es/PATTEX/accesoCertificadoSEDE.jsf"

    @Test
    fun exactObservedPattexTransitionProducesOneBoundedClientAuthGrant() {
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        val authorized = authorizer.observeTopLevelNavigation(profileId, source, target, 10, true)
        assertEquals(profileId, authorized?.profileId)
        assertEquals("pattex.juntaex.es", authorized?.target?.host)
        assertEquals("/PATTEX/accesoCertificadoSEDE.jsf", authorized?.target?.rawPath)
        assertNull(authorizer.observeTopLevelNavigation(profileId, source, target, 10, true))
    }

    @Test
    fun pattexGrantRejectsEverySourceOrTargetExpansion() {
        val invalid = listOf(
            source.replace("info=060", "info=061") to target,
            source + "&extra=1" to target,
            source to target + "?extra=1",
            source to target.replace("accesoCertificadoSEDE.jsf", "accesoCertificadoSEDE.jsf/other"),
            source to target.replace("pattex.juntaex.es", "pattex.juntaex.es.evil.example"),
            source to target.replace("pattex.juntaex.es", "pattex.juntaex.es:8443"),
        )
        invalid.forEachIndexed { index, (badSource, badTarget) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
            assertNull(
                "$badSource -> $badTarget",
                fresh.observeTopLevelNavigation(profileId, badSource, badTarget, 20L + index, true),
            )
        }
    }
}
