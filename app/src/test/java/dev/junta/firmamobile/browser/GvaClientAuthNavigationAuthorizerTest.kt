package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GvaClientAuthNavigationAuthorizerTest {
    private val profileId = ProfileId("generalitat-valenciana-client-auth")

    @Test
    fun exactGvaSourceTargetPairAuthorizesOneLinkedClientTlsGrant() {
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)

        val result = authorizer.observeTopLevelNavigation(
            activeProfileId = profileId,
            currentUrl = source(SESSION),
            targetUrl = target(SESSION),
            currentEpoch = 18,
            isModernMainFrameRequest = true,
        )

        assertEquals(profileId, result?.profileId)
        assertEquals("ptt-clave-clientcert.gva.es", result?.target?.host)
        assertEquals("/pttclave/retornoClientCert.html", result?.target?.rawPath)
        assertEquals("idioma=es&idSesion=$SESSION", result?.target?.rawQuery)
        assertNull(
            authorizer.observeTopLevelNavigation(
                profileId,
                source(SESSION),
                target(SESSION),
                18,
                true,
            ),
        )
    }

    @Test
    fun gvaClientTlsGrantRejectsSessionQueryHostPathPortAndFrameExpansion() {
        val attacks = listOf(
            source(OTHER_SESSION) to target(SESSION),
            source(SESSION) to target(OTHER_SESSION),
            "$SOURCE_BASE?idSesion=$SESSION&extra=1" to target(SESSION),
            source(SESSION) to "${target(SESSION)}&extra=1",
            source(SESSION) to "https://ptt-clave-clientcert.gva.es/pttclave/retornoClientCert.html?idioma=ca&idSesion=$SESSION",
            source(SESSION) to "https://ptt-clave-clientcert.gva.es/pttclave/other.html?idioma=es&idSesion=$SESSION",
            source(SESSION) to "https://ptt-clave-clientcert.gva.es.evil.example/pttclave/retornoClientCert.html?idioma=es&idSesion=$SESSION",
            source(SESSION) to "https://ptt-clave-clientcert.gva.es:8443/pttclave/retornoClientCert.html?idioma=es&idSesion=$SESSION",
            source(SESSION) to "${target(SESSION)}#fragment",
        )

        attacks.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    profileId,
                    source,
                    target,
                    30L + index,
                    true,
                ),
            )
        }

        assertNull(
            ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
                .observeTopLevelNavigation(
                    profileId,
                    source(SESSION),
                    target(SESSION),
                    50,
                    false,
                ),
        )
    }

    private fun source(session: String) = "$SOURCE_BASE?idSesion=$session"

    private fun target(session: String) =
        "https://ptt-clave-clientcert.gva.es/pttclave/retornoClientCert.html?idioma=es&idSesion=$session"

    private companion object {
        const val SOURCE_BASE = "https://ptt-clave.gva.es/pttclave/redirigirClave.html"
        const val SESSION = "w47-synthetic-gva-session-0108"
        const val OTHER_SESSION = "w47-synthetic-gva-session-other"
    }
}
