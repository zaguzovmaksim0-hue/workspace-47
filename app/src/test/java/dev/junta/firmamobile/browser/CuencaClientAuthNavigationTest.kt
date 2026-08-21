package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuencaClientAuthNavigationTest {
    private val monotonic = { 1_000_000_000L }

    @Test
    fun exactCuencaSourceBindsTheSameIdTokenToTheFixedEntityTarget() {
        val authorizer = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic,
        )

        val authorized = authorizer.observeTopLevelNavigation(
            PROFILE,
            SOURCE,
            TARGET,
            currentEpoch = 90,
            isModernMainFrameRequest = true,
        )

        assertEquals(PROFILE, authorized?.profileId)
        assertEquals("identificacionssl.sedipualba.es", authorized?.target?.host)
        assertEquals("/", authorized?.target?.rawPath)
        assertEquals("idtoken=$TOKEN&idioma=es&entidad=16000", authorized?.target?.rawQuery)
        assertNull(
            authorizer.observeTopLevelNavigation(
                PROFILE,
                SOURCE,
                TARGET,
                currentEpoch = 90,
                isModernMainFrameRequest = true,
            ),
        )
    }

    @Test
    fun cuencaTransitionRejectsMismatchedEntityTokenHostPathAndQuery() {
        val attacks = listOf(
            SOURCE.replace(TOKEN, OTHER_TOKEN) to TARGET,
            SOURCE.replace("idioma=es", "idioma=en") to TARGET,
            "$SOURCE&extra=1" to TARGET,
            SOURCE to TARGET.replace(TOKEN, OTHER_TOKEN),
            SOURCE to TARGET.replace("idioma=es", "idioma=en"),
            SOURCE to TARGET.replace("entidad=16000", "entidad=02000"),
            SOURCE to "$TARGET&extra=1",
            SOURCE to TARGET.replace(
                "identificacionssl.sedipualba.es",
                "identificacionssl.sedipualba.es.evil.example",
            ),
            SOURCE to TARGET.replace(
                "https://identificacionssl.sedipualba.es/",
                "https://identificacionssl.sedipualba.es/other",
            ),
            SOURCE to "$TARGET#fragment",
        )

        attacks.forEachIndexed { index, (source, target) ->
            val authorizer = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic,
            )
            assertNull(
                "$source -> $target",
                authorizer.observeTopLevelNavigation(
                    PROFILE,
                    source,
                    target,
                    currentEpoch = 91L + index,
                    isModernMainFrameRequest = true,
                ),
            )
        }
    }

    private companion object {
        val PROFILE = ProfileId("diputacion-cuenca-portal")
        const val TOKEN = "12345678-w47SyntheticCuencaToken0123456789"
        const val OTHER_TOKEN = "87654321-w47OtherCuencaToken9876543210"
        const val SOURCE =
            "https://sede.dipucuenca.es/segex/identificacion_opciones.aspx?" +
                "idtoken=$TOKEN&idioma=es"
        const val TARGET =
            "https://identificacionssl.sedipualba.es/?idtoken=$TOKEN&idioma=es&entidad=16000"
    }
}
