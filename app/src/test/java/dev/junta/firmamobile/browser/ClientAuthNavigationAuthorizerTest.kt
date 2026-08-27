package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileActivation
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientAuthNavigationAuthorizerTest {
    private val monotonic = MutableMonotonicClock(1_000_000_000L)
    private val authorizer = ClientAuthNavigationAuthorizer(
        BuiltInSiteProfiles.qaRegistry,
        monotonic::nowNanos,
    )

    @Test
    fun exactDirectSourceTransitionProducesOneBoundedAeatTarget() {
        val direct = aeatAuthorizer()

        val result = direct.observeTopLevelNavigation(
            activeProfileId = AEAT_PROFILE,
            currentUrl = AEAT_SOURCE,
            targetUrl = AEAT_TARGET,
            currentEpoch = 40,
            isModernMainFrameRequest = true,
        )

        assertEquals(AEAT_PROFILE, result?.profileId)
        assertEquals("www1.agenciatributaria.gob.es", result?.target?.host)
        assertEquals("/wlpl/BUGC-JDIT/MdcAcceso", result?.target?.rawPath)
        assertNull(
            direct.observeTopLevelNavigation(
                AEAT_PROFILE, AEAT_SOURCE, AEAT_TARGET, 40, true,
            ),
        )
    }

    @Test
    fun exactDirectToledoTransitionAuthorizesOnlyPort843AndTheObservedSource() {
        val toledo = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)

        val authorized = toledo.observeTopLevelNavigation(
            TOLEDO_PROFILE, TOLEDO_SOURCE, TOLEDO_TARGET, 60, true,
        )

        assertEquals(TOLEDO_PROFILE, authorized?.profileId)
        assertEquals(843, authorized?.target?.port)
        assertEquals("/SIGEM_AutenticacionWeb/validacionCertificado.do", authorized?.target?.rawPath)

        listOf(
            TOLEDO_TARGET.replace(":843", ""),
            TOLEDO_TARGET.replace(":843", ":844"),
            "$TOLEDO_TARGET?extra=1",
        ).forEach { invalidTarget ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                invalidTarget,
                fresh.observeTopLevelNavigation(
                    TOLEDO_PROFILE, TOLEDO_SOURCE, invalidTarget, 61, true,
                ),
            )
        }

        val wrongSource = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos,
        )
        assertNull(
            wrongSource.observeTopLevelNavigation(
                TOLEDO_PROFILE,
                TOLEDO_SOURCE.replace("TRAM_31", "TRAM_32"),
                TOLEDO_TARGET,
                62,
                true,
            ),
        )
    }

    @Test
    fun leonDynamicSourceAuthorizesOnlyTheSameObservedIdTokenOnExactCertificateHost() {
        val leon = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        val authorized = leon.observeTopLevelNavigation(
            LEON_PROFILE, LEON_SOURCE, LEON_TARGET, 70, true,
        )

        assertEquals(LEON_PROFILE, authorized?.profileId)
        assertEquals("identificacionssl.sedipualba.es", authorized?.target?.host)
        assertEquals("/", authorized?.target?.rawPath)
        assertEquals(
            "idtoken=$LEON_TOKEN&idioma=es&entidad=24000",
            authorized?.target?.rawQuery,
        )
        assertNull(leon.observeTopLevelNavigation(LEON_PROFILE, LEON_SOURCE, LEON_TARGET, 70, true))
    }

    @Test
    fun leonDynamicSourceRejectsTokenHostPathAndQueryExpansion() {
        val attacks = listOf(
            LEON_SOURCE.replace(LEON_TOKEN, LEON_OTHER_TOKEN) to LEON_TARGET,
            LEON_SOURCE.replace("idioma=es", "idioma=en") to LEON_TARGET,
            "$LEON_SOURCE&extra=1" to LEON_TARGET,
            LEON_SOURCE to LEON_TARGET.replace(LEON_TOKEN, LEON_OTHER_TOKEN),
            LEON_SOURCE to LEON_TARGET.replace("idioma=es", "idioma=en"),
            LEON_SOURCE to LEON_TARGET.replace("entidad=24000", "entidad=02000"),
            LEON_SOURCE to "$LEON_TARGET&extra=1",
            LEON_SOURCE to LEON_TARGET.replace("identificacionssl.sedipualba.es", "identificacionssl.sedipualba.es.evil.example"),
            LEON_SOURCE to LEON_TARGET.replace("https://identificacionssl.sedipualba.es/", "https://identificacionssl.sedipualba.es/other"),
            LEON_SOURCE to "$LEON_TARGET#fragment",
        )

        attacks.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    LEON_PROFILE, source, target, 71L + index, true,
                ),
            )
        }
    }

    @Test
    fun mallorcaDynamicSourceAuthorizesOnlyTheSameObservedIdTokenOnExactCertificateHost() {
        val mallorca = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        val authorized = mallorca.observeTopLevelNavigation(
            MALLORCA_PROFILE, MALLORCA_SOURCE, MALLORCA_TARGET, 80, true,
        )

        assertEquals(MALLORCA_PROFILE, authorized?.profileId)
        assertEquals("identificacionssl.sedipualba.es", authorized?.target?.host)
        assertEquals("/", authorized?.target?.rawPath)
        assertEquals(
            "idtoken=$MALLORCA_TOKEN&idioma=ca&entidad=07700",
            authorized?.target?.rawQuery,
        )
        assertNull(
            mallorca.observeTopLevelNavigation(
                MALLORCA_PROFILE, MALLORCA_SOURCE, MALLORCA_TARGET, 80, true,
            ),
        )
    }

    @Test
    fun mallorcaDynamicSourceRejectsTokenHostPathAndQueryExpansion() {
        val attacks = listOf(
            MALLORCA_SOURCE.replace(MALLORCA_TOKEN, MALLORCA_OTHER_TOKEN) to MALLORCA_TARGET,
            MALLORCA_SOURCE.replace("idioma=ca", "idioma=es") to MALLORCA_TARGET,
            "$MALLORCA_SOURCE&extra=1" to MALLORCA_TARGET,
            MALLORCA_SOURCE to MALLORCA_TARGET.replace(MALLORCA_TOKEN, MALLORCA_OTHER_TOKEN),
            MALLORCA_SOURCE to MALLORCA_TARGET.replace("idioma=ca", "idioma=es"),
            MALLORCA_SOURCE to MALLORCA_TARGET.replace("entidad=07700", "entidad=24000"),
            MALLORCA_SOURCE to "$MALLORCA_TARGET&extra=1",
            MALLORCA_SOURCE to MALLORCA_TARGET.replace(
                "identificacionssl.sedipualba.es",
                "identificacionssl.sedipualba.es.evil.example",
            ),
            MALLORCA_SOURCE to MALLORCA_TARGET.replace(
                "https://identificacionssl.sedipualba.es/",
                "https://identificacionssl.sedipualba.es/other",
            ),
            MALLORCA_SOURCE to "$MALLORCA_TARGET#fragment",
        )

        attacks.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    MALLORCA_PROFILE, source, target, 81L + index, true,
                ),
            )
        }
    }

    @Test
    fun navarraCaseMappedEphemeralParameterAuthorizesOnlyTheSameObservedSessionValue() {
        val navarra = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        val authorized = navarra.observeTopLevelNavigation(
            NAVARRA_PROFILE, NAVARRA_SOURCE, NAVARRA_TARGET, 75, true,
        )

        assertEquals(NAVARRA_PROFILE, authorized?.profileId)
        assertEquals("ateka.navarra.es", authorized?.target?.host)
        assertEquals("/ateka/Certificate/login", authorized?.target?.rawPath)
        assertEquals("returnUrl=$NAVARRA_TOKEN", authorized?.target?.rawQuery)
        assertNull(
            navarra.observeTopLevelNavigation(
                NAVARRA_PROFILE, NAVARRA_SOURCE, NAVARRA_TARGET, 75, true,
            ),
        )
    }

    @Test
    fun navarraCaseMappedEphemeralParameterRejectsCrossSessionAndEveryTargetExpansion() {
        val attacks = listOf(
            NAVARRA_SOURCE.replace(NAVARRA_TOKEN, NAVARRA_OTHER_TOKEN) to NAVARRA_TARGET,
            NAVARRA_SOURCE to NAVARRA_TARGET.replace(NAVARRA_TOKEN, NAVARRA_OTHER_TOKEN),
            NAVARRA_SOURCE.replace("ReturnUrl=", "returnUrl=") to NAVARRA_TARGET,
            NAVARRA_SOURCE to NAVARRA_TARGET.replace("returnUrl=", "ReturnUrl="),
            NAVARRA_SOURCE to "$NAVARRA_TARGET&extra=1",
            NAVARRA_SOURCE to NAVARRA_TARGET.replace("/Certificate/login", "/Certificate/login/other"),
            NAVARRA_SOURCE to NAVARRA_TARGET.replace("ateka.navarra.es", "ateka.navarra.es.evil.example"),
            NAVARRA_SOURCE to NAVARRA_TARGET.replace("ateka.navarra.es", "ateka.navarra.es:8443"),
            NAVARRA_SOURCE to "$NAVARRA_TARGET#fragment",
        )

        attacks.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    NAVARRA_PROFILE, source, target, 76L + index, true,
                ),
            )
        }
    }

    @Test
    fun menorcaSameOriginClientTlsAuthorizesOnlyMatchingLinkedUrlParameter() {
        val menorca = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        val authorized = menorca.observeTopLevelNavigation(
            MENORCA_PROFILE, MENORCA_SOURCE, MENORCA_TARGET, 72, true,
        )

        assertEquals(MENORCA_PROFILE, authorized?.profileId)
        assertEquals("www.carpetaciutadana.org", authorized?.target?.host)
        assertEquals("/cime/Login/LoginCert.aspx", authorized?.target?.rawPath)
        assertEquals("URL=$MENORCA_RETURN", authorized?.target?.rawQuery)
        assertNull(
            menorca.observeTopLevelNavigation(
                MENORCA_PROFILE, MENORCA_SOURCE, MENORCA_TARGET, 72, true,
            ),
        )
    }

    @Test
    fun menorcaSameOriginClientTlsRejectsUnlinkedOrExpandedTransition() {
        val invalidCalls = listOf(
            MENORCA_SOURCE.replace(MENORCA_RETURN, MENORCA_OTHER_RETURN) to MENORCA_TARGET,
            MENORCA_SOURCE to MENORCA_TARGET.replace(MENORCA_RETURN, MENORCA_OTHER_RETURN),
            "$MENORCA_SOURCE&extra=1" to MENORCA_TARGET,
            MENORCA_SOURCE to "$MENORCA_TARGET&extra=1",
            MENORCA_SOURCE.replace("/Login/Login.aspx", "/Login/Other.aspx") to MENORCA_TARGET,
            MENORCA_SOURCE to MENORCA_TARGET.replace("/Login/LoginCert.aspx", "/Login/LoginCert.aspx/other"),
            MENORCA_SOURCE to MENORCA_TARGET.replace(
                "www.carpetaciutadana.org",
                "www.carpetaciutadana.org.evil.example",
            ),
            MENORCA_SOURCE to MENORCA_TARGET.replace(
                "www.carpetaciutadana.org",
                "www.carpetaciutadana.org:8443",
            ),
        )

        invalidCalls.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    MENORCA_PROFILE, source, target, 73L + index, true,
                ),
            )
        }
    }

    @Test
    fun albaceteDynamicSourceAuthorizesOnlyTheSameObservedIdTokenOnExactCertificateHost() {
        val albacete = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        val authorized = albacete.observeTopLevelNavigation(
            ALBACETE_PROFILE, ALBACETE_SOURCE, ALBACETE_TARGET, 80, true,
        )

        assertEquals(ALBACETE_PROFILE, authorized?.profileId)
        assertEquals("identificacionssl.sedipualba.es", authorized?.target?.host)
        assertEquals("/", authorized?.target?.rawPath)
        assertEquals(
            "idtoken=$ALBACETE_TOKEN&idioma=es&entidad=02000",
            authorized?.target?.rawQuery,
        )
        assertNull(albacete.observeTopLevelNavigation(ALBACETE_PROFILE, ALBACETE_SOURCE, ALBACETE_TARGET, 80, true))
    }

    @Test
    fun albaceteDynamicSourceRejectsTokenEntityHostPathAndQueryExpansion() {
        val attacks = listOf(
            ALBACETE_SOURCE.replace(ALBACETE_TOKEN, ALBACETE_OTHER_TOKEN) to ALBACETE_TARGET,
            ALBACETE_SOURCE.replace("idioma=es", "idioma=en") to ALBACETE_TARGET,
            "$ALBACETE_SOURCE&extra=1" to ALBACETE_TARGET,
            ALBACETE_SOURCE to ALBACETE_TARGET.replace(ALBACETE_TOKEN, ALBACETE_OTHER_TOKEN),
            ALBACETE_SOURCE to ALBACETE_TARGET.replace("idioma=es", "idioma=en"),
            ALBACETE_SOURCE to ALBACETE_TARGET.replace("entidad=02000", "entidad=24000"),
            ALBACETE_SOURCE to "$ALBACETE_TARGET&extra=1",
            ALBACETE_SOURCE to ALBACETE_TARGET.replace("identificacionssl.sedipualba.es", "identificacionssl.sedipualba.es.evil.example"),
            ALBACETE_SOURCE to ALBACETE_TARGET.replace("https://identificacionssl.sedipualba.es/", "https://identificacionssl.sedipualba.es/other"),
            ALBACETE_SOURCE to "$ALBACETE_TARGET#fragment",
        )

        attacks.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    ALBACETE_PROFILE, source, target, 81L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactTeaAlegacionesDirectTransitionProducesOneBoundedTarget() {
        val result = authorizer.observeTopLevelNavigation(
            activeProfileId = TEA_PROFILE,
            currentUrl = TEA_SOURCE,
            targetUrl = TEA_TARGET,
            currentEpoch = 42,
            isModernMainFrameRequest = true,
        )

        assertEquals(TEA_PROFILE, result?.profileId)
        assertEquals("www1.tea.hacienda.gob.es", result?.target?.host)
        assertEquals("/wlpl/TEAC-TRAM/SedeTRAM", result?.target?.rawPath)
        assertEquals("tram=0", result?.target?.rawQuery)
        assertNull(
            authorizer.observeTopLevelNavigation(
                TEA_PROFILE, TEA_SOURCE, TEA_TARGET, 42, true,
            ),
        )
    }

    @Test
    fun teaAlegacionesDirectTransitionRejectsEverySourceAndTargetExpansion() {
        val invalidCalls = listOf<Pair<String, String>>(
            TEA_SOURCE.replace("alegaciones.html", "solicitudes.html") to TEA_TARGET,
            TEA_SOURCE to TEA_TARGET.replace("tram=0", "tram=2"),
            TEA_SOURCE to "$TEA_TARGET&extra=1",
            TEA_SOURCE to "$TEA_TARGET&tram=0",
            TEA_SOURCE to TEA_TARGET.replace("/SedeTRAM", "/SedeTRAM/other"),
            TEA_SOURCE to TEA_TARGET.replace("www1.tea.hacienda.gob.es", "www1.tea.hacienda.gob.es.evil.example"),
            TEA_SOURCE to TEA_TARGET.replace("www1.tea.hacienda.gob.es", "www1.tea.hacienda.gob.es:8443"),
        )

        invalidCalls.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    TEA_PROFILE, source, target, 43L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactSameOriginSanidadFixedQueryTransitionProducesOneBoundedTarget() {
        val result = authorizer.observeTopLevelNavigation(
            activeProfileId = SANIDAD_PROFILE,
            currentUrl = SANIDAD_SOURCE,
            targetUrl = SANIDAD_TARGET,
            currentEpoch = 45,
            isModernMainFrameRequest = true,
        )

        assertEquals(SANIDAD_PROFILE, result?.profileId)
        assertEquals("sede.mscbs.gob.es", result?.target?.host)
        assertEquals("/SIGEM_AutenticacionWeb/validacionCertificado.do", result?.target?.rawPath)
        assertNull(
            authorizer.observeTopLevelNavigation(
                SANIDAD_PROFILE, SANIDAD_SOURCE, SANIDAD_TARGET, 45, true,
            ),
        )
    }

    @Test
    fun sanidadDirectTransitionRejectsEverySourcePathAndQueryExpansion() {
        val invalidCalls = listOf<Pair<String, String>>(
            SANIDAD_SOURCE.replace("formularios.htm", "home.htm") to SANIDAD_TARGET,
            SANIDAD_SOURCE to SANIDAD_TARGET.replace("TRAM_TARDESCONPLAN", "TRAM_OTHER"),
            SANIDAD_SOURCE to SANIDAD_TARGET.replace("COUNTRY=ES", "COUNTRY=FR"),
            SANIDAD_SOURCE to SANIDAD_TARGET.replace("LANG=es", "LANG=en"),
            SANIDAD_SOURCE to SANIDAD_TARGET.replace("ENTIDAD_ID=000", "ENTIDAD_ID=001"),
            SANIDAD_SOURCE to "$SANIDAD_TARGET&extra=1",
            SANIDAD_SOURCE to "$SANIDAD_TARGET&COUNTRY=ES",
            SANIDAD_SOURCE to SANIDAD_TARGET.replace("/validacionCertificado.do", "/validacionCertificado.do/other"),
            SANIDAD_SOURCE to SANIDAD_TARGET.replace("sede.mscbs.gob.es", "sede.mscbs.gob.es.evil.example"),
            SANIDAD_SOURCE to SANIDAD_TARGET.replace("sede.mscbs.gob.es", "sede.mscbs.gob.es:8443"),
        )

        invalidCalls.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    SANIDAD_PROFILE, source, target, 50L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactEducationClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val education = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val authorized = education.observeTopLevelNavigation(
            EDUCATION_PROFILE, EDUCATION_SOURCE, EDUCATION_TARGET, 78, true,
        )

        assertEquals(EDUCATION_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            education.observeTopLevelNavigation(EDUCATION_PROFILE, EDUCATION_SOURCE, EDUCATION_TARGET, 78, true),
        )

        listOf(
            EDUCATION_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
            "$EDUCATION_TARGET?extra=1",
            EDUCATION_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
            EDUCATION_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"),
        ).forEachIndexed { index, invalidTarget ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                invalidTarget,
                fresh.observeTopLevelNavigation(
                    EDUCATION_PROFILE, EDUCATION_SOURCE, invalidTarget, 180L + index, true,
                ),
            )
        }

        listOf(
            "https://pasarela.clave.gob.es/Proxy2/ServiceProvider",
            "$EDUCATION_SOURCE?extra=1",
            "https://pasarela.clave.gob.es.evil.example/Proxy2/ServiceRedirect",
        ).forEachIndexed { index, invalidSource ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                invalidSource,
                fresh.observeTopLevelNavigation(
                    EDUCATION_PROFILE, invalidSource, EDUCATION_TARGET, 190L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactAirefClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val airef = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val authorized = airef.observeTopLevelNavigation(
            AIREF_PROFILE, AIREF_SOURCE, AIREF_TARGET, 79, true,
        )

        assertEquals(AIREF_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            airef.observeTopLevelNavigation(AIREF_PROFILE, AIREF_SOURCE, AIREF_TARGET, 79, true),
        )

        listOf(
            AIREF_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
            "$AIREF_TARGET?extra=1",
            AIREF_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
            AIREF_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"),
        ).forEachIndexed { index, invalidTarget ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                invalidTarget,
                fresh.observeTopLevelNavigation(
                    AIREF_PROFILE, AIREF_SOURCE, invalidTarget, 80L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactCatalunyaClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val catalunya = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val authorized = catalunya.observeTopLevelNavigation(
            CATALUNYA_PROFILE, CATALUNYA_SOURCE, CATALUNYA_TARGET, 79, true,
        )

        assertEquals(CATALUNYA_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            catalunya.observeTopLevelNavigation(CATALUNYA_PROFILE, CATALUNYA_SOURCE, CATALUNYA_TARGET, 79, true),
        )

        listOf(
            CATALUNYA_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
            "$CATALUNYA_TARGET?extra=1",
            CATALUNYA_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
            CATALUNYA_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"),
        ).forEachIndexed { index, invalidTarget ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                invalidTarget,
                fresh.observeTopLevelNavigation(
                    CATALUNYA_PROFILE, CATALUNYA_SOURCE, invalidTarget, 90L + index, true,
                ),
            )
        }

        val wrongSource = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        assertNull(
            wrongSource.observeTopLevelNavigation(
                CATALUNYA_PROFILE,
                "https://pasarela.clave.gob.es/Proxy2/ServiceProvider?unexpected=1",
                CATALUNYA_TARGET,
                99,
                true,
            ),
        )
    }

    @Test
    fun exactOurenseClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val ourense = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val authorized = ourense.observeTopLevelNavigation(
            OURENSE_PROFILE, OURENSE_SOURCE, OURENSE_TARGET, 84, true,
        )

        assertEquals(OURENSE_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            ourense.observeTopLevelNavigation(OURENSE_PROFILE, OURENSE_SOURCE, OURENSE_TARGET, 84, true),
        )

        val invalidCalls = listOf(
            "https://pasarela.clave.gob.es/Proxy2/ServiceProvider" to OURENSE_TARGET,
            OURENSE_SOURCE to OURENSE_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
            OURENSE_SOURCE to "$OURENSE_TARGET?extra=1",
            OURENSE_SOURCE to OURENSE_TARGET.replace(
                "pasarela-ident.clave.gob.es",
                "pasarela-ident.clave.gob.es.evil.example",
            ),
            OURENSE_SOURCE to OURENSE_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"),
        )
        invalidCalls.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    OURENSE_PROFILE, source, target, 90L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactDiputacionSevillaClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val sevilla = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val authorized = sevilla.observeTopLevelNavigation(
            SEVILLA_DIPUTACION_PROFILE, SEVILLA_DIPUTACION_SOURCE, SEVILLA_DIPUTACION_TARGET, 89, true,
        )

        assertEquals(SEVILLA_DIPUTACION_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            sevilla.observeTopLevelNavigation(
                SEVILLA_DIPUTACION_PROFILE, SEVILLA_DIPUTACION_SOURCE, SEVILLA_DIPUTACION_TARGET, 89, true,
            ),
        )

        listOf(
            SEVILLA_DIPUTACION_SOURCE.replace("ServiceRedirect", "ServiceProvider") to SEVILLA_DIPUTACION_TARGET,
            SEVILLA_DIPUTACION_SOURCE to SEVILLA_DIPUTACION_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
            SEVILLA_DIPUTACION_SOURCE to "$SEVILLA_DIPUTACION_TARGET?extra=1",
            SEVILLA_DIPUTACION_SOURCE to SEVILLA_DIPUTACION_TARGET.replace(
                "pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example",
            ),
            SEVILLA_DIPUTACION_SOURCE to SEVILLA_DIPUTACION_TARGET.replace(
                "pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443",
            ),
        ).forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    SEVILLA_DIPUTACION_PROFILE, source, target, 90L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactDiputacionACorunaClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val coruna = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val authorized = coruna.observeTopLevelNavigation(
            CORUNA_PROFILE, CORUNA_SOURCE, CORUNA_TARGET, 89, true,
        )

        assertEquals(CORUNA_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            coruna.observeTopLevelNavigation(CORUNA_PROFILE, CORUNA_SOURCE, CORUNA_TARGET, 89, true),
        )

        listOf(
            CORUNA_SOURCE.replace("ServiceRedirect", "ServiceProvider") to CORUNA_TARGET,
            CORUNA_SOURCE to CORUNA_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
            CORUNA_SOURCE to "$CORUNA_TARGET?extra=1",
            CORUNA_SOURCE to CORUNA_TARGET.replace(
                "pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example",
            ),
            CORUNA_SOURCE to CORUNA_TARGET.replace(
                "pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443",
            ),
        ).forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(CORUNA_PROFILE, source, target, 90L + index, true),
            )
        }
    }

    @Test
    fun exactMugejuClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val mugeju = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val authorized = mugeju.observeTopLevelNavigation(
            MUGEJU_PROFILE, MUGEJU_SOURCE, MUGEJU_TARGET, 80, true,
        )

        assertEquals(MUGEJU_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            mugeju.observeTopLevelNavigation(MUGEJU_PROFILE, MUGEJU_SOURCE, MUGEJU_TARGET, 80, true),
        )

        listOf(
            MUGEJU_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
            "$MUGEJU_TARGET?extra=1",
            MUGEJU_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
            MUGEJU_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"),
        ).forEachIndexed { index, invalidTarget ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                invalidTarget,
                fresh.observeTopLevelNavigation(
                    MUGEJU_PROFILE, MUGEJU_SOURCE, invalidTarget, 81L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactTwoStageValladolidRedirectAuthorizesOnlyTheObservedClientTlsPort() {
        val valladolid = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)

        assertNull(
            valladolid.observeTopLevelNavigation(
                VALLADOLID_PROFILE,
                VALLADOLID_INDEX,
                VALLADOLID_SOURCE,
                80,
                true,
            ),
        )
        valladolid.onTopLevelPageStarted(VALLADOLID_SOURCE, 81)

        val authorized = valladolid.observeTopLevelNavigation(
            VALLADOLID_PROFILE,
            VALLADOLID_SOURCE,
            VALLADOLID_TARGET,
            81,
            true,
        )

        assertEquals(VALLADOLID_PROFILE, authorized?.profileId)
        assertEquals(21460, authorized?.target?.port)
        assertEquals("/c/portal/cert-login", authorized?.target?.rawPath)

        val wrongPortSource = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos,
        )
        assertNull(
            wrongPortSource.observeTopLevelNavigation(
                VALLADOLID_PROFILE,
                VALLADOLID_INDEX.replace(".es/", ".es:21460/"),
                VALLADOLID_SOURCE,
                85,
                true,
            ),
        )
        wrongPortSource.onTopLevelPageStarted(VALLADOLID_SOURCE, 86)
        assertNull(
            wrongPortSource.observeTopLevelNavigation(
                VALLADOLID_PROFILE, VALLADOLID_SOURCE, VALLADOLID_TARGET, 86, true,
            ),
        )

        listOf(
            VALLADOLID_TARGET.replace(":21460", ""),
            VALLADOLID_TARGET.replace(":21460", ":21461"),
            "$VALLADOLID_TARGET?extra=1",
        ).forEach { invalidTarget ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                fresh.observeTopLevelNavigation(
                    VALLADOLID_PROFILE, VALLADOLID_INDEX, VALLADOLID_SOURCE, 90, true,
                ),
            )
            fresh.onTopLevelPageStarted(VALLADOLID_SOURCE, 91)
            assertNull(
                invalidTarget,
                fresh.observeTopLevelNavigation(
                    VALLADOLID_PROFILE, VALLADOLID_SOURCE, invalidTarget, 91, true,
                ),
            )
        }
    }

    @Test
    fun hostileNavigationCannotResetConsumedDirectGrantInTheSameEpoch() {
        val direct = aeatAuthorizer()
        assertEquals(
            AEAT_PROFILE,
            direct.observeTopLevelNavigation(
                AEAT_PROFILE, AEAT_SOURCE, AEAT_TARGET, 70, true,
            )?.profileId,
        )

        assertNull(
            direct.observeTopLevelNavigation(
                AEAT_PROFILE, AEAT_SOURCE, "$AEAT_TARGET?extra=1", 70, true,
            ),
        )
        assertNull(
            direct.observeTopLevelNavigation(
                AEAT_PROFILE, AEAT_SOURCE, AEAT_TARGET, 70, true,
            ),
        )
    }

    @Test
    fun aeatDirectTransitionRejectsLegacySubframeWrongProfileAndWrongSource() {
        val invalidCalls = listOf<(ClientAuthNavigationAuthorizer) -> AuthorizedClientAuthTarget?>(
            { it.observeTopLevelNavigation(AEAT_PROFILE, AEAT_SOURCE, AEAT_TARGET, 50, false) },
            { it.observeTopLevelNavigation(null, AEAT_SOURCE, AEAT_TARGET, 50, true) },
            { it.observeTopLevelNavigation(PROFILE, AEAT_SOURCE, AEAT_TARGET, 50, true) },
            { it.observeTopLevelNavigation(AEAT_PROFILE, null, AEAT_TARGET, 50, true) },
            {
                it.observeTopLevelNavigation(
                    AEAT_PROFILE,
                    "https://sede.agenciatributaria.gob.es/Sede/other.html",
                    AEAT_TARGET,
                    50,
                    true,
                )
            },
            {
                it.observeTopLevelNavigation(
                    AEAT_PROFILE,
                    "https://sede.agenciatributaria.gob.es.evil.example/Sede/mi-area-personal.html",
                    AEAT_TARGET,
                    50,
                    true,
                )
            },
        )

        invalidCalls.forEach { call -> assertNull(call(aeatAuthorizer())) }
    }

    @Test
    fun aeatDirectTransitionRejectsEveryTargetExpansion() {
        val invalidTargets = listOf(
            AEAT_TARGET.replace("www1.agenciatributaria.gob.es", "www1.agenciatributaria.gob.es.evil.example"),
            AEAT_TARGET.replace("/MdcAcceso", "/Other"),
            AEAT_TARGET.replace("/MdcAcceso", "/MdcAcceso%2Fother"),
            AEAT_TARGET.replace("www1.agenciatributaria.gob.es", "www1.agenciatributaria.gob.es:8443"),
            "$AEAT_TARGET#fragment",
            "$AEAT_TARGET?extra=1",
            "$AEAT_TARGET?",
        )

        invalidTargets.forEach { target ->
            assertNull(
                target,
                aeatAuthorizer().observeTopLevelNavigation(
                    AEAT_PROFILE, AEAT_SOURCE, target, 60, true,
                ),
            )
        }
    }

    @Test
    fun exactTwoStageTopLevelRedirectProducesOneBoundedTarget() {
        assertNull(arm(epoch = 4))
        authorizer.onTopLevelPageStarted(SOURCE, 5)

        val result = authorize(TARGET, epoch = 5)

        assertEquals(ProfileId("carne-joven-andalucia"), result?.profileId)
        assertEquals("ws235.juntadeandalucia.es", result?.target?.host)
        assertEquals("/authenticationFacade", result?.target?.path)
        assertNull(authorize(TARGET, epoch = 5))
    }

    @Test
    fun immediateTargetAtArmingEpochNAuthorizesOnce() {
        assertNull(arm(epoch = 4))

        val result = authorize(TARGET, epoch = 4)

        assertEquals(ProfileId("carne-joven-andalucia"), result?.profileId)
        assertEquals("ws235.juntadeandalucia.es", result?.target?.host)
        assertEquals("/authenticationFacade", result?.target?.path)
        assertNull(authorize(TARGET, epoch = 4))
    }

    @Test
    fun directLegacyIframeWrongProfileEpochAndExpiredSourceNeverAuthorize() {
        assertNull(authorize(TARGET, epoch = 7))
        assertNull(
            authorizer.observeTopLevelNavigation(
                PROFILE,
                INDEX,
                SOURCE,
                7,
                isModernMainFrameRequest = false,
            ),
        )
        assertNull(authorize(TARGET, epoch = 8))

        arm(epoch = 10)
        assertNull(authorize(TARGET, epoch = 12))

        arm(epoch = 20)
        monotonic.advance(Duration.ofSeconds(16))
        assertNull(authorize(TARGET, epoch = 21))

        assertNull(
            authorizer.observeTopLevelNavigation(
                ProfileId("junta-andalucia"), INDEX, SOURCE, 30, true,
            ),
        )
        assertNull(authorize(TARGET, epoch = 31))
    }

    @Test
    fun wrongFixedEmptyDuplicateAndExtraQueryParametersFailClosed() {
        val attacks = listOf(
            TARGET.replace("validateCert", "other"),
            TARGET.replace("ticketId=synthetic-ticket", "ticketId="),
            TARGET.replace(
                "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4v" +
                    "c2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
                "https%3A%2F%2Fws104.juntadeandalucia.es%2FcarneJoven%2Fservlet%2F" +
                    "ReturnAuthenticationServlet",
            ),
            "$TARGET&extra=1",
            "$TARGET&ticketId=duplicate",
            TARGET.replace("/authenticationFacade", "/other"),
            TARGET.replace("ws235.juntadeandalucia.es", "ws235.juntadeandalucia.es.evil.example"),
        )
        attacks.forEachIndexed { index, attack ->
            val epoch = 100L + index * 2
            arm(epoch)
            authorizer.onTopLevelPageStarted(SOURCE, epoch + 1)
            assertNull(attack, authorize(attack, epoch + 1))
        }
    }

    @Test
    fun unrelatedPageStartInvalidatesThePendingSource() {
        arm(200)
        authorizer.onTopLevelPageStarted("https://ws104.juntadeandalucia.es/other", 201)
        assertNull(authorize(TARGET, 201))
    }

    @Test
    fun invalidationEncodedDuplicatesFragmentsPortsAndWrongCurrentOriginFailClosed() {
        arm(300)
        authorizer.invalidate()
        authorizer.onTopLevelPageStarted(SOURCE, 301)
        assertNull(authorize(TARGET, 301))

        val attacks = listOf(
            "$TARGET#fragment",
            TARGET.replace("ws235.juntadeandalucia.es", "ws235.juntadeandalucia.es:8443"),
            "$TARGET&%74icketId=encoded-duplicate",
            TARGET.replace("/authenticationFacade", "/authenticationFacade%2Fother"),
        )
        attacks.forEachIndexed { index, attack ->
            val epoch = 310L + index * 2
            arm(epoch)
            authorizer.onTopLevelPageStarted(SOURCE, epoch + 1)
            assertNull(attack, authorize(attack, epoch + 1))
        }

        assertNull(
            authorizer.observeTopLevelNavigation(
                PROFILE,
                "https://example.org/",
                SOURCE,
                400,
                true,
            ),
        )
        assertNull(authorize(TARGET, 401))
    }

    @Test
    fun paddedUnpaddedAndUrlSafeBase64ComeBackUrlAreEquivalent() {
        val stdPaddedTarget = TARGET
        val stdUnpaddedTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ",
        )
        val urlSafePaddedTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D".replace('/', '_').replace('+', '-'),
        )
        val urlSafeUnpaddedTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ".replace('/', '_').replace('+', '-'),
        )

        listOf(stdPaddedTarget, stdUnpaddedTarget, urlSafePaddedTarget, urlSafeUnpaddedTarget).forEachIndexed { index, target ->
            val epoch = 500L + index * 2
            arm(epoch)
            authorizer.onTopLevelPageStarted(SOURCE, epoch + 1)
            val result = authorize(target, epoch + 1)
            assertEquals(PROFILE, result?.profileId)
            assertEquals("ws235.juntadeandalucia.es", result?.target?.host)
        }
    }

    @Test
    fun nonEquivalentAndInvalidBase64ComeBackUrlFailClosed() {
        val nonEquivalentTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly9ldmlsLmV4YW1wbGUvUmV0dXJuQXV0aGVudGljYXRpb25TZXJ2bGV0",
        )
        val invalidBase64Target = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "!!!invalid-base64!!!",
        )
        val leadingWhitespaceTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "%20aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
        )
        val trailingWhitespaceTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D%20",
        )
        val invalidMod1LengthTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0c",
        )

        val invalidTargets = listOf(
            nonEquivalentTarget,
            invalidBase64Target,
            leadingWhitespaceTarget,
            trailingWhitespaceTarget,
            invalidMod1LengthTarget,
        )

        invalidTargets.forEachIndexed { index, attack ->
            val epoch = 600L + index * 2
            arm(epoch)
            authorizer.onTopLevelPageStarted(SOURCE, epoch + 1)
            assertNull(attack, authorize(attack, epoch + 1))
        }
    }

    @Test
    fun aragonSsLoginRedirectAuthorizesOnlyExactClientCertificateEndpoint() {
        val aragon = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        assertNull(
            aragon.observeTopLevelNavigation(
                ARAGON_TRAMITES_PROFILE, ARAGON_TRAMITES_INDEX, ARAGON_SSLOGIN_SOURCE, 880, true,
            ),
        )
        aragon.onTopLevelPageStarted(ARAGON_SSLOGIN_SOURCE, 881)
        val authorized = aragon.observeTopLevelNavigation(
            ARAGON_TRAMITES_PROFILE, ARAGON_SSLOGIN_SOURCE, ARAGON_SSLOGIN_TARGET, 881, true,
        )

        assertEquals(ARAGON_TRAMITES_PROFILE, authorized?.profileId)
        assertEquals("login1.loginssl.aragon.es", authorized?.target?.host)
        assertEquals("/sife_login/SSLOGIN/idByCert", authorized?.target?.rawPath)
        assertEquals(443, authorized?.policy?.requestPort)
        assertNull(
            aragon.observeTopLevelNavigation(
                ARAGON_TRAMITES_PROFILE, ARAGON_SSLOGIN_SOURCE, ARAGON_SSLOGIN_TARGET, 881, true,
            ),
        )
    }

    @Test
    fun aragonSsLoginRedirectRejectsSourceTargetAndProfileExpansion() {
        val invalidSources = listOf(
            ARAGON_SSLOGIN_SOURCE.replace("login.loginssl.aragon.es", "login.loginssl.aragon.es.evil.example"),
            ARAGON_SSLOGIN_SOURCE.replace("/sife_login/SSLOGIN", "/sife_login/OTHER"),
            ARAGON_SSLOGIN_SOURCE.replace("consumeResponse", "otherResponse"),
            "$ARAGON_SSLOGIN_SOURCE&extra=1",
        )
        invalidSources.forEachIndexed { index, source ->
            val aragon = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            val epoch = 890L + index * 2
            assertNull(
                source,
                aragon.observeTopLevelNavigation(
                    ARAGON_TRAMITES_PROFILE, ARAGON_TRAMITES_INDEX, source, epoch, true,
                ),
            )
            aragon.onTopLevelPageStarted(source, epoch + 1)
            assertNull(
                source,
                aragon.observeTopLevelNavigation(
                    ARAGON_TRAMITES_PROFILE, source, ARAGON_SSLOGIN_TARGET, epoch + 1, true,
                ),
            )
        }

        val invalidTargets = listOf(
            ARAGON_SSLOGIN_TARGET.replace("login1.loginssl.aragon.es", "login1.loginssl.aragon.es.evil.example"),
            ARAGON_SSLOGIN_TARGET.replace("/idByCert", "/other"),
            ARAGON_SSLOGIN_TARGET.replace("consumeResponse", "otherResponse"),
            "$ARAGON_SSLOGIN_TARGET&extra=1",
            ARAGON_SSLOGIN_TARGET.replace("login1.loginssl.aragon.es", "login1.loginssl.aragon.es:444"),
        )
        invalidTargets.forEachIndexed { index, target ->
            val aragon = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            val epoch = 910L + index * 2
            assertNull(
                aragon.observeTopLevelNavigation(
                    ARAGON_TRAMITES_PROFILE, ARAGON_TRAMITES_INDEX, ARAGON_SSLOGIN_SOURCE, epoch, true,
                ),
            )
            aragon.onTopLevelPageStarted(ARAGON_SSLOGIN_SOURCE, epoch + 1)
            assertNull(
                target,
                aragon.observeTopLevelNavigation(
                    ARAGON_TRAMITES_PROFILE, ARAGON_SSLOGIN_SOURCE, target, epoch + 1, true,
                ),
            )
        }

        val wrongProfile = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        assertNull(
            wrongProfile.observeTopLevelNavigation(
                ProfileId("aragon-siraw"), ARAGON_TRAMITES_INDEX, ARAGON_SSLOGIN_SOURCE, 940, true,
            ),
        )
    }

    @Test
    fun laRiojaDynamicCasSourceAuthorizesOnlyExactClientCertificateEndpoint() {
        val rioja = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        assertNull(
            rioja.observeTopLevelNavigation(
                LA_RIOJA_PROFILE, LA_RIOJA_INDEX, LA_RIOJA_SOURCE, 900, true,
            ),
        )
        rioja.onTopLevelPageStarted(LA_RIOJA_SOURCE, 901)

        val authorized = rioja.observeTopLevelNavigation(
            LA_RIOJA_PROFILE, LA_RIOJA_SOURCE, LA_RIOJA_TARGET, 901, true,
        )

        assertEquals(LA_RIOJA_PROFILE, authorized?.profileId)
        assertEquals("ias1.larioja.org", authorized?.target?.host)
        assertEquals("/clientcertSSL/login", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            rioja.observeTopLevelNavigation(
                LA_RIOJA_PROFILE, LA_RIOJA_SOURCE, LA_RIOJA_TARGET, 901, true,
            ),
        )
    }

    @Test
    fun laRiojaDynamicCasSourceRejectsEverySourceExpansion() {
        val invalidSources = listOf(
            LA_RIOJA_SOURCE.replace("inst=G", "inst=X"),
            LA_RIOJA_SOURCE.replace("apli=OFIVIR", "apli=OTHER"),
            LA_RIOJA_SOURCE.replace("nodo=CIUDANO", "nodo=OTHER"),
            LA_RIOJA_SOURCE.replace("&param=synthetic-param", ""),
            LA_RIOJA_SOURCE.replace("&TARGET=", "&missing="),
            "$LA_RIOJA_SOURCE&extra=1",
            "$LA_RIOJA_SOURCE&param=duplicate",
            LA_RIOJA_SOURCE.replace("/casLR/login", "/casLR/other"),
            LA_RIOJA_SOURCE.replace("ias1.larioja.org", "ias1.larioja.org.evil.example"),
            LA_RIOJA_SOURCE.replace("ias1.larioja.org", "ias1.larioja.org:444"),
            "$LA_RIOJA_SOURCE#fragment",
        )

        invalidSources.forEachIndexed { index, source ->
            val rioja = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            val epoch = 920L + index * 2
            assertNull(
                source,
                rioja.observeTopLevelNavigation(
                    LA_RIOJA_PROFILE, LA_RIOJA_INDEX, source, epoch, true,
                ),
            )
            rioja.onTopLevelPageStarted(source, epoch + 1)
            assertNull(
                source,
                rioja.observeTopLevelNavigation(
                    LA_RIOJA_PROFILE, source, LA_RIOJA_TARGET, epoch + 1, true,
                ),
            )
        }
    }

    @Test
    fun laRiojaDynamicCasSourceRejectsEveryTargetExpansionAndUntrustedContext() {
        val invalidTargets = listOf(
            LA_RIOJA_TARGET.replace("ias1.larioja.org", "ias1.larioja.org.evil.example"),
            LA_RIOJA_TARGET.replace("/clientcertSSL/login", "/clientcertSSL/other"),
            LA_RIOJA_TARGET.replace("ias1.larioja.org", "ias1.larioja.org:444"),
            "$LA_RIOJA_TARGET?extra=1",
            "$LA_RIOJA_TARGET#fragment",
        )

        invalidTargets.forEachIndexed { index, target ->
            val rioja = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            val epoch = 960L + index * 2
            assertNull(
                rioja.observeTopLevelNavigation(
                    LA_RIOJA_PROFILE, LA_RIOJA_INDEX, LA_RIOJA_SOURCE, epoch, true,
                ),
            )
            rioja.onTopLevelPageStarted(LA_RIOJA_SOURCE, epoch + 1)
            assertNull(
                target,
                rioja.observeTopLevelNavigation(
                    LA_RIOJA_PROFILE, LA_RIOJA_SOURCE, target, epoch + 1, true,
                ),
            )
        }

        val wrongProfile = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )
        assertNull(
            wrongProfile.observeTopLevelNavigation(
                PROFILE, LA_RIOJA_INDEX, LA_RIOJA_SOURCE, 980, true,
            ),
        )
        assertNull(
            wrongProfile.observeTopLevelNavigation(
                LA_RIOJA_PROFILE, LA_RIOJA_INDEX, LA_RIOJA_SOURCE, 981, false,
            ),
        )
        assertNull(
            wrongProfile.observeTopLevelNavigation(
                LA_RIOJA_PROFILE, LA_RIOJA_SOURCE, LA_RIOJA_TARGET, 981, true,
            ),
        )
    }

    @Test
    fun pendingClientAuthTtlUsesMonotonicTime() {
        val shortLived = shortTtlAuthorizer(ttlSeconds = 1)
        assertNull(
            shortLived.observeTopLevelNavigation(
                PROFILE,
                INDEX,
                SOURCE,
                800,
                true,
            ),
        )

        monotonic.advance(Duration.ofSeconds(1))

        assertNull(
            shortLived.observeTopLevelNavigation(
                PROFILE,
                SOURCE,
                TARGET,
                800,
                true,
            ),
        )
    }

    @Test
    fun subframeRequestClearsPendingAndFailsClosed() {
        arm(700)
        assertNull(
            authorizer.observeTopLevelNavigation(
                PROFILE,
                SOURCE,
                TARGET,
                700,
                isModernMainFrameRequest = false,
            ),
        )
        assertNull(authorize(TARGET, 700))
        assertNull(authorize(TARGET, 701))
    }

    @Test
    fun regAgeExactPostResourceRequestAuthorizesOnlyTheObservedClaveCertificateTarget() {
        val regAge = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        val authorized = regAge.observeTopLevelResourceRequest(
            activeProfileId = REG_AGE_PROFILE,
            currentUrl = REG_AGE_CLAVE_SOURCE,
            targetUrl = REG_AGE_CERT_TARGET,
            method = "POST",
            currentEpoch = 118,
            isMainFrameRequest = true,
        )

        assertEquals(REG_AGE_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertEquals(443, authorized?.policy?.requestPort)
        assertEquals(true, authorized?.policy?.allowEmptyIssuerList)
    }

    @Test
    fun regAgeInPlaceClientTlsRejectsSourceTargetMethodAndFrameExpansion() {
        data class Attempt(val source: String, val target: String, val method: String, val mainFrame: Boolean)
        val invalid = listOf(
            Attempt(REG_AGE_CLAVE_SOURCE, REG_AGE_CERT_TARGET, "GET", true),
            Attempt(REG_AGE_CLAVE_SOURCE, "$REG_AGE_CERT_TARGET?extra=1", "POST", true),
            Attempt(REG_AGE_CLAVE_SOURCE, REG_AGE_CERT_TARGET.replace("/AuthenticateCitizen", "/Other"), "POST", true),
            Attempt(
                REG_AGE_CLAVE_SOURCE,
                REG_AGE_CERT_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
                "POST",
                true,
            ),
            Attempt(REG_AGE_CLAVE_SOURCE.replace("ServiceProvider", "ServiceRedirect"), REG_AGE_CERT_TARGET, "POST", true),
            Attempt("$REG_AGE_CLAVE_SOURCE?extra=1", REG_AGE_CERT_TARGET, "POST", true),
            Attempt(REG_AGE_CLAVE_SOURCE, REG_AGE_CERT_TARGET, "POST", false),
        )

        invalid.forEachIndexed { index, attempt ->
            val fresh = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            assertNull(
                "${attempt.source} -> ${attempt.target} [${attempt.method}]",
                fresh.observeTopLevelResourceRequest(
                    activeProfileId = REG_AGE_PROFILE,
                    currentUrl = attempt.source,
                    targetUrl = attempt.target,
                    method = attempt.method,
                    currentEpoch = 119L + index,
                    isMainFrameRequest = attempt.mainFrame,
                ),
            )
        }
    }

    @Test
    fun tarragonaExactPostResourceRequestAuthorizesOnlyTheObservedValidCertificateTarget() {
        val tarragona = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        val authorized = tarragona.observeTopLevelResourceRequest(
            activeProfileId = TARRAGONA_PROFILE,
            currentUrl = TARRAGONA_VALID_SOURCE,
            targetUrl = TARRAGONA_CERT_TARGET,
            method = "POST",
            currentEpoch = 120,
            isMainFrameRequest = true,
        )

        assertEquals(TARRAGONA_PROFILE, authorized?.profileId)
        assertEquals("cert.valid.aoc.cat", authorized?.target?.host)
        assertEquals("/o/oauth2/cert", authorized?.target?.rawPath)
        assertEquals(443, authorized?.policy?.requestPort)
    }

    @Test
    fun tarragonaInPlaceClientTlsRejectsSourceTargetAndMethodExpansion() {
        val invalidCalls = listOf(
            Triple(TARRAGONA_VALID_SOURCE, TARRAGONA_CERT_TARGET, "GET"),
            Triple(TARRAGONA_VALID_SOURCE, "$TARRAGONA_CERT_TARGET?extra=1", "POST"),
            Triple(TARRAGONA_VALID_SOURCE, TARRAGONA_CERT_TARGET.replace("/cert", "/other"), "POST"),
            Triple(TARRAGONA_VALID_SOURCE, TARRAGONA_CERT_TARGET.replace("cert.valid.aoc.cat", "cert.valid.aoc.cat.evil.example"), "POST"),
            Triple(TARRAGONA_VALID_SOURCE.replace("client_id=valid.dipta.cat", "client_id=evil.example"), TARRAGONA_CERT_TARGET, "POST"),
            Triple(TARRAGONA_VALID_SOURCE.replace("redirect_uri=https%3A%2F%2Fegovern.altanet.org%2Fvalid%2Fcode", "redirect_uri=https%3A%2F%2Fevil.example%2Fcode"), TARRAGONA_CERT_TARGET, "POST"),
            Triple(TARRAGONA_VALID_SOURCE.replace("&state=synthetic-state", ""), TARRAGONA_CERT_TARGET, "POST"),
            Triple("$TARRAGONA_VALID_SOURCE&extra=1", TARRAGONA_CERT_TARGET, "POST"),
        )

        invalidCalls.forEachIndexed { index, (source, target, method) ->
            val fresh = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            assertNull(
                "$source -> $target [$method]",
                fresh.observeTopLevelResourceRequest(
                    activeProfileId = TARRAGONA_PROFILE,
                    currentUrl = source,
                    targetUrl = target,
                    method = method,
                    currentEpoch = 130L + index,
                    isMainFrameRequest = true,
                ),
            )
        }
    }

    private fun shortTtlAuthorizer(ttlSeconds: Int): ClientAuthNavigationAuthorizer {
        val base = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == PROFILE }
        val profile = base.copy(
            clientAuthPolicy = checkNotNull(base.clientAuthPolicy).copy(
                grantTtlSeconds = ttlSeconds,
            ),
        )
        val registry = SiteProfileRegistry(
            BuiltInSiteProfiles.catalog.copy(profiles = listOf(profile)),
            BuildTrustPolicy.QA,
        )
        return ClientAuthNavigationAuthorizer(registry, monotonic::nowNanos)
    }

    private fun aeatAuthorizer(): ClientAuthNavigationAuthorizer {
        val base = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == PROFILE }
        val profile = base.copy(
            profileId = AEAT_PROFILE,
            profileVersion = 1,
            displayName = "AEAT — Mis datos censales",
            compatibilityStatus = CompatibilityStatus.VERIFIED_CONTRACT,
            activation = ProfileActivation.QA_ONLY,
            startUrl = URI(AEAT_SOURCE),
            initiatorOrigins = setOf(ExactOrigin.parse("https://sede.agenciatributaria.gob.es")),
            redirectOrigins = emptySet(),
            trustedBrowseOrigins = emptySet(),
            endpoints = emptyMap(),
            operationPolicies = emptyMap(),
            capabilities = setOf(Capability.CLIENT_TLS_AUTH),
            clientAuthPolicy = ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse("https://www1.agenciatributaria.gob.es")),
                sourceUrls = setOf(URI(AEAT_SOURCE)),
                requestPath = "/wlpl/BUGC-JDIT/MdcAcceso",
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = 15,
            ),
            evidence = emptyList(),
        )
        val registry = SiteProfileRegistry(
            BuiltInSiteProfiles.catalog.copy(profiles = listOf(profile)),
            BuildTrustPolicy.QA,
        )
        return ClientAuthNavigationAuthorizer(registry, monotonic::nowNanos)
    }

    private fun arm(epoch: Long) = authorizer.observeTopLevelNavigation(
        activeProfileId = PROFILE,
        currentUrl = INDEX,
        targetUrl = SOURCE,
        currentEpoch = epoch,
        isModernMainFrameRequest = true,
    )

    private fun authorize(target: String, epoch: Long) = authorizer.observeTopLevelNavigation(
        activeProfileId = PROFILE,
        currentUrl = SOURCE,
        targetUrl = target,
        currentEpoch = epoch,
        isModernMainFrameRequest = true,
    )

    private class MutableMonotonicClock(private var nanos: Long) {
        fun nowNanos(): Long = nanos

        fun advance(duration: Duration) {
            nanos += duration.toNanos()
        }
    }

    @Test
    fun catalunyaSeuExactValidCertificateTransitionProducesOneBoundedTarget() {
        val catalunya = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        val authorized = catalunya.observeTopLevelNavigation(
            CATALUNYA_SEU_PROFILE, CATALUNYA_SEU_SOURCE, CATALUNYA_SEU_TARGET, 91, true,
        )

        assertEquals(CATALUNYA_SEU_PROFILE, authorized?.profileId)
        assertEquals("cert.valid.aoc.cat", authorized?.target?.host)
        assertEquals("/o/oauth2/cert", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            catalunya.observeTopLevelNavigation(
                CATALUNYA_SEU_PROFILE, CATALUNYA_SEU_SOURCE, CATALUNYA_SEU_TARGET, 91, true,
            ),
        )
    }

    @Test
    fun catalunyaSeuCertificateTransitionRejectsEverySourceOrTargetExpansion() {
        val attacks = listOf(
            CATALUNYA_SEU_SOURCE.replace("state=state", "state=other") to CATALUNYA_SEU_TARGET,
            CATALUNYA_SEU_SOURCE.replace("lang=ca", "lang=es") to CATALUNYA_SEU_TARGET,
            "$CATALUNYA_SEU_SOURCE&extra=1" to CATALUNYA_SEU_TARGET,
            CATALUNYA_SEU_SOURCE to "$CATALUNYA_SEU_TARGET?extra=1",
            CATALUNYA_SEU_SOURCE to "$CATALUNYA_SEU_TARGET/other",
            CATALUNYA_SEU_SOURCE to CATALUNYA_SEU_TARGET.replace(
                "cert.valid.aoc.cat",
                "cert.valid.aoc.cat.evil.example",
            ),
            CATALUNYA_SEU_SOURCE to CATALUNYA_SEU_TARGET.replace(
                "cert.valid.aoc.cat",
                "cert.valid.aoc.cat:8443",
            ),
            CATALUNYA_SEU_SOURCE to "$CATALUNYA_SEU_TARGET#fragment",
            CATALUNYA_SEU_SOURCE.replace("valid.aoc.cat", "valid.aoc.cat.evil.example") to
                CATALUNYA_SEU_TARGET,
        )

        attacks.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(
                BuiltInSiteProfiles.qaRegistry,
                monotonic::nowNanos,
            )
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    CATALUNYA_SEU_PROFILE, source, target, 92L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactTwoStageJaenRedirectAuthorizesOnlyObservedCert2QueryContract() {
        val jaen = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)

        assertNull(
            jaen.observeTopLevelNavigation(
                JAEN_PROFILE,
                JAEN_INDEX,
                JAEN_SOURCE,
                84,
                true,
            ),
        )
        jaen.onTopLevelPageStarted(JAEN_SOURCE, 85)

        val authorized = jaen.observeTopLevelNavigation(
            JAEN_PROFILE,
            JAEN_SOURCE,
            JAEN_TARGET,
            85,
            true,
        )

        assertEquals(JAEN_PROFILE, authorized?.profileId)
        assertEquals("cert2.dipujaen.es", authorized?.target?.host)
        assertEquals("/", authorized?.target?.rawPath)
        assertEquals(443, authorized?.policy?.requestPort)
    }

    @Test
    fun jaenTwoStageRedirectRejectsSourceTargetAndQueryExpansion() {
        val invalidCalls = listOf(
            JAEN_SOURCE.replace("/Certificado", "/Certificado/other") to JAEN_TARGET,
            JAEN_SOURCE to JAEN_TARGET.replace("cert2.dipujaen.es", "cert2.dipujaen.es.evil.example"),
            JAEN_SOURCE to JAEN_TARGET.replace("cert2.dipujaen.es", "cert2.dipujaen.es:8443"),
            JAEN_SOURCE to JAEN_TARGET.replace("https://cert2.dipujaen.es/", "https://cert2.dipujaen.es/other"),
            JAEN_SOURCE to JAEN_TARGET.replace("key=$JAEN_KEY", "key="),
            JAEN_SOURCE to JAEN_TARGET.replace("key=$JAEN_KEY&", ""),
            JAEN_SOURCE to JAEN_TARGET.replace(
                "back=https%3A%2F%2Fsede.dipujaen.es%2FIniciarSesion%2FCertificado",
                "back=https%3A%2F%2Fevil.example%2Fcallback",
            ),
            JAEN_SOURCE to "$JAEN_TARGET&extra=1",
            JAEN_SOURCE to "$JAEN_TARGET#fragment",
        )

        invalidCalls.forEachIndexed { index, (source, target) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    JAEN_PROFILE, JAEN_INDEX, source, 100L + index * 2, true,
                ),
            )
            fresh.onTopLevelPageStarted(source, 101L + index * 2)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelNavigation(
                    JAEN_PROFILE, source, target, 101L + index * 2, true,
                ),
            )
        }

        val unarmed = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        assertNull(
            unarmed.observeTopLevelNavigation(
                JAEN_PROFILE, JAEN_SOURCE, JAEN_TARGET, 130, true,
            ),
        )
    }

    private companion object {
        val CATALUNYA_SEU_PROFILE = ProfileId("catalunya-seu-registre-client-auth")
        const val CATALUNYA_SEU_SOURCE =
            "https://valid.aoc.cat/o/oauth2/auth?lang=ca&scope=autenticacio_usuari&state=state&" +
                "redirect_uri=https%3A%2F%2Fovt.gencat.cat%2Fgsitfc%2FAppJava%2Fredirectservlet&" +
                "response_type=code&client_id=gsit.gencat.cat&approval_prompt=auto"
        const val CATALUNYA_SEU_TARGET = "https://cert.valid.aoc.cat/o/oauth2/cert"
        val JAEN_PROFILE = ProfileId("diputacion-jaen-sede")
        const val JAEN_KEY = "w47SyntheticJaenEphemeralKey0123456789"
        const val JAEN_INDEX = "https://sede.dipujaen.es/SolicitudGenerica"
        const val JAEN_SOURCE = "https://sede.dipujaen.es/IniciarSesion/Certificado"
        const val JAEN_TARGET =
            "https://cert2.dipujaen.es/?key=$JAEN_KEY&" +
                "back=https%3A%2F%2Fsede.dipujaen.es%2FIniciarSesion%2FCertificado"
        val PROFILE = ProfileId("carne-joven-andalucia")
        val AEAT_PROFILE = ProfileId("aeat-mis-datos-censales")
        val TEA_PROFILE = ProfileId("tea-alegaciones-certificado")
        val EDUCATION_PROFILE = ProfileId("educacion-convocatoria")
        const val EDUCATION_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val EDUCATION_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val AIREF_PROFILE = ProfileId("airef-instancia-general")
        const val AIREF_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val AIREF_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val OURENSE_PROFILE = ProfileId("diputacion-ourense-sede")
        const val OURENSE_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val OURENSE_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val SEVILLA_DIPUTACION_PROFILE = ProfileId("diputacion-sevilla-sede")
        const val SEVILLA_DIPUTACION_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val SEVILLA_DIPUTACION_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val CORUNA_PROFILE = ProfileId("diputacion-a-coruna-solicitud-general")
        const val CORUNA_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val CORUNA_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val CATALUNYA_PROFILE = ProfileId("catalunya-peticio-generica-client-auth")
        const val CATALUNYA_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val CATALUNYA_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val MUGEJU_PROFILE = ProfileId("mugeju-remision-documentacion-client-auth")
        const val MUGEJU_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val MUGEJU_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val VALLADOLID_PROFILE = ProfileId("diputacion-valladolid-sede")
        val MALLORCA_PROFILE = ProfileId("consell-mallorca-sede")
        const val MALLORCA_TOKEN = "12345678-w47SyntheticMallorcaToken0123456789"
        const val MALLORCA_OTHER_TOKEN = "87654321-w47OtherMallorcaToken9876543210"
        const val MALLORCA_SOURCE =
            "https://cim.secimallorca.net/segex/identificacion_opciones.aspx?idtoken=$MALLORCA_TOKEN&idioma=ca"
        const val MALLORCA_TARGET =
            "https://identificacionssl.sedipualba.es/?idtoken=$MALLORCA_TOKEN&idioma=ca&entidad=07700"
        val NAVARRA_PROFILE = ProfileId("navarra-sede-registro-general")
        const val NAVARRA_TOKEN = "w47SyntheticNavarraSessionToken0123456789"
        const val NAVARRA_OTHER_TOKEN = "w47OtherNavarraSessionToken9876543210"
        const val NAVARRA_SOURCE =
            "https://ateka.navarra.es/ateka/router?ReturnUrl=$NAVARRA_TOKEN"
        const val NAVARRA_TARGET =
            "https://ateka.navarra.es/ateka/Certificate/login?returnUrl=$NAVARRA_TOKEN"
        val LEON_PROFILE = ProfileId("diputacion-leon-sede")
        val ARAGON_TRAMITES_PROFILE = ProfileId("aragon-solicitud-general-client-auth")
        const val ARAGON_TRAMITES_INDEX =
            "https://aplicaciones.aragon.es/tramitar/solicitud-general/identificacion"
        const val ARAGON_CONSUME_RESPONSE =
            "https://aplicaciones.aragon.es/mfe_core/rest/identification/TTO/" +
                "aHR0cHM6Ly9hcGxpY2FjaW9uZXMuYXJhZ29uLmVzL3RyYW1pdGFyL3NvbGljaXR1ZC1nZW5lcmFsL2lkZW50aWZpY2FjaW9uL2lkZW50aWZpY2Fkbw==/" +
                "SSLOGIN/consumeResponse"
        val ARAGON_ENCODED_RETURN = java.net.URLEncoder.encode(ARAGON_CONSUME_RESPONSE, "UTF-8")
        val ARAGON_SSLOGIN_SOURCE =
            "https://login.loginssl.aragon.es/sife_login/SSLOGIN?redirect.url=$ARAGON_ENCODED_RETURN"
        val ARAGON_SSLOGIN_TARGET =
            "https://login1.loginssl.aragon.es/sife_login/SSLOGIN/idByCert?redirect.url=$ARAGON_ENCODED_RETURN"
        val LA_RIOJA_PROFILE = ProfileId("la-rioja-oficina-electronica")
        const val LA_RIOJA_INDEX =
            "https://ias1.larioja.org/oficinavirtual/presentacion?act_codi=24697"
        const val LA_RIOJA_SOURCE =
            "https://ias1.larioja.org/casLR/login?inst=G&apli=OFIVIR&nodo=CIUDANO&" +
                "param=synthetic-param&TARGET=https%3A%2F%2Fias1.larioja.org%2Foficinavirtual%2F" +
                "presentacion%3Fact_codi%3D24697%26flow%3Dsynthetic"
        const val LA_RIOJA_TARGET = "https://ias1.larioja.org/clientcertSSL/login"
        val ALBACETE_PROFILE = ProfileId("diputacion-albacete-portal")
        const val ALBACETE_TOKEN = "12345678-w47SyntheticAlbaceteToken012345"
        const val ALBACETE_OTHER_TOKEN = "87654321-w47OtherAlbaceteToken987654"
        const val ALBACETE_SOURCE =
            "https://sede.dipualba.es/segex/identificacion_opciones.aspx?idtoken=$ALBACETE_TOKEN&idioma=es"
        const val ALBACETE_TARGET =
            "https://identificacionssl.sedipualba.es/?idtoken=$ALBACETE_TOKEN&idioma=es&entidad=02000"
        const val LEON_TOKEN = "12345678-w47SyntheticLeonToken0123456789"
        const val LEON_OTHER_TOKEN = "87654321-w47OtherLeonToken9876543210"
        const val LEON_SOURCE =
            "https://sede.dipuleon.es/segex/identificacion_opciones.aspx?idtoken=$LEON_TOKEN&idioma=es"
        const val LEON_TARGET =
            "https://identificacionssl.sedipualba.es/?idtoken=$LEON_TOKEN&idioma=es&entidad=24000"
        val SANIDAD_PROFILE = ProfileId("ministerio-sanidad-certificado")
        val MENORCA_PROFILE = ProfileId("menorca-carpeta-ciutadana")
        const val MENORCA_RETURN =
            "https%3A%2F%2Fwww.carpetaciutadana.org%2Fcime%2Fsolicituds%2F" +
                "iniciartramit.aspx%3FTIPO%3DREGE%5EIDIOMA%3D1"
        const val MENORCA_OTHER_RETURN =
            "https%3A%2F%2Fwww.carpetaciutadana.org%2Fcime%2Fsolicituds%2Fother.aspx"
        const val MENORCA_SOURCE =
            "https://www.carpetaciutadana.org/cime/Login/Login.aspx?URL=$MENORCA_RETURN"
        const val MENORCA_TARGET =
            "https://www.carpetaciutadana.org/cime/Login/LoginCert.aspx?URL=$MENORCA_RETURN"
        val TOLEDO_PROFILE = ProfileId("diputacion-toledo-sede")
        const val AEAT_SOURCE =
            "https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"
        const val AEAT_TARGET =
            "https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"
        const val TOLEDO_SOURCE =
            "https://diputacion.toledo.gob.es/SIGEM_AutenticacionWeb/" +
                "seleccionEntidad.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_31&" +
                "SESION_ID=&ENTIDAD_ID=&LANG=&COUNTRY="
        const val TOLEDO_TARGET =
            "https://diputacion.toledo.gob.es:843/SIGEM_AutenticacionWeb/validacionCertificado.do"
        const val TEA_SOURCE =
            "https://sede.tea.hacienda.gob.es/TEA/alegaciones.html"
        const val TEA_TARGET =
            "https://www1.tea.hacienda.gob.es/wlpl/TEAC-TRAM/SedeTRAM?tram=0"
        const val SANIDAD_SOURCE =
            "https://sede.mscbs.gob.es/registroElectronico/formularios.htm"
        const val SANIDAD_TARGET =
            "https://sede.mscbs.gob.es/SIGEM_AutenticacionWeb/validacionCertificado.do?" +
                "REDIRECCION=RegistroTelematico&tramiteId=TRAM_TARDESCONPLAN&" +
                "ENTIDAD_ID=000&LANG=es&COUNTRY=ES"
        const val VALLADOLID_INDEX =
            "https://www.sede.diputaciondevalladolid.es/tgauth/login"
        const val VALLADOLID_SOURCE =
            "https://www.sede.diputaciondevalladolid.es/c/portal/cert-login"
        const val VALLADOLID_TARGET =
            "https://www.sede.diputaciondevalladolid.es:21460/c/portal/cert-login"
        val REG_AGE_PROFILE = ProfileId("reg-age-redsara")
        const val REG_AGE_CLAVE_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val REG_AGE_CERT_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val TARRAGONA_PROFILE = ProfileId("diputacion-tarragona-sede")
        const val TARRAGONA_VALID_SOURCE =
            "https://valid.aoc.cat/o/oauth2/auth?response_type=code&client_id=valid.dipta.cat&" +
                "redirect_uri=https%3A%2F%2Fegovern.altanet.org%2Fvalid%2Fcode&" +
                "scope=autenticacio_usuari&state=synthetic-state&access_type=online&approval_prompt=auto"
        const val TARRAGONA_CERT_TARGET = "https://cert.valid.aoc.cat/o/oauth2/cert"
        const val INDEX = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&ticketId=synthetic-ticket&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
    }
}
