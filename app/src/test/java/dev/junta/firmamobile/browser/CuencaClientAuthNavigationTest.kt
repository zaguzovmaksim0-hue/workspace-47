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
            SOURCE.substringBefore("?") to TARGET,
            SOURCE.replace("login.aspx", "login2.aspx") to TARGET,
            "$SOURCE&extra=1" to TARGET,
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
        const val SOURCE =
            "https://sede.dipucuenca.es/carpetaciudadana/login.aspx?returnUrl=https%3a%2f%2fsede.dipucuenca.es%2fcarpetaciudadana%2ftramite.aspx%3fidtramite%3d12074"
        const val TARGET =
            "https://identificacionssl.sedipualba.es/?idtoken=$TOKEN&idioma=es&entidad=16000"
    }
}
