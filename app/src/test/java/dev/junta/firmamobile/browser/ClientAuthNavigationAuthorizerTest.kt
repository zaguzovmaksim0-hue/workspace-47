package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileActivation
import dev.junta.firmamobile.profile.matchesRequestUrl
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientAuthNavigationAuthorizerTest {
    private val monotonic = MutableMonotonicClock(1_000_000_000L)
    private val authorizer = ClientAuthNavigationAuthorizer(
        BuiltInSiteProfiles.qaRegistry,
        monotonic::nowNanos,
    )

    @Test
    fun waveFOwnTlsLoginsRequireTheReviewedTopLevelSourceAndKeepOneShotGrants() {
        val flows = listOf(
            Triple("boe-sede-public-home",
                "https://extranet.boe.es/id/check-cert.php?referer=https://extranet.boe.es/anuncios/",
                "https://check-cert.extranet.boe.es/"),
            Triple("cmt-public-navigation",
                "https://sede.cmt.gob.es/carpetaciudadana/login.aspx?returnUrl=" +
                    "https%3a%2f%2fsede.cmt.gob.es%2fcarpetaciudadana%2fcarpeta_ciudadana.aspx",
                "https://identificacionssl.sedipualba.es/" +
                    "?idtoken=20261108-AbCdEf0123456789_fixtureToken&idioma=es&entidad=99006"),
        )
        flows.forEach { (id, source, target) ->
            val profileId = ProfileId(id)
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            val start = BuiltInSiteProfiles.qaRegistry.profile(profileId)!!.startUrl.toString()
            assertNull(fresh.observeTopLevelNavigation(profileId, start, target, 4, true))
            assertNull(fresh.observeTopLevelNavigation(profileId, target, target, 4, true))
            assertNull(fresh.observeTopLevelNavigation(null, source, target, 4, true))
            assertNull(fresh.observeTopLevelNavigation(profileId, source, target, 4, false))
            assertNull(fresh.observeTopLevelResourceRequest(profileId, source, target, "GET", 4, false))
            assertNull(fresh.observeTopLevelResourceRequest(profileId, source, target, "POST", 4, true))
            assertNull(fresh.observeTopLevelNavigation(profileId, source, target, Long.MAX_VALUE, true))
            assertNull(fresh.observeTopLevelNavigation(profileId, source + "&extra=1", target, 4, true))
            val grant = fresh.observeTopLevelNavigation(profileId, start, target, 4, true, source)
            assertEquals(profileId, grant?.profileId)
            assertEquals(URI(source), grant?.source)
            assertEquals(URI(target), grant?.target)
            assertNull(fresh.observeTopLevelNavigation(profileId, source, target, 4, true))
            assertFalse(grant!!.isExpiredOrInvalid(monotonic.nowNanos()))
            monotonic.advance(Duration.ofSeconds(16))
            assertTrue(grant.isExpiredOrInvalid(monotonic.nowNanos()))
        }
        val frame = "https://sede.cmt.gob.es/segex/identificacion_opciones.aspx"
        assertNull(
            authorizer.observeTopLevelNavigation(ProfileId("cmt-public-navigation"), frame, flows[1].third, 9, true),
        )
        assertNull(
            authorizer.observeTopLevelNavigation(
                ProfileId("diputacion-leon-sede"),
                flows[1].second,
                flows[1].third,
                9,
                true,
            ),
        )
    }

    @Test
    fun staticTlsPathDoesNotSilentlyAcceptItjCookielessSessionSegments() {
        val origin = "https://procedimientossedeitj.transicionjusta.gob.es"
        val policy = BuiltInSiteProfiles.qaRegistry.profile(ProfileId("boe-sede-public-home"))!!
            .clientAuthPolicy!!.copy(
                requestOrigins = setOf(ExactOrigin.parse(origin)),
                requestPath = "/vt/vtwebregistro/AccesoConCertificado.aspx",
            )
        assertTrue(policy.matchesRequestUrl(URI("$origin/vt/vtwebregistro/AccesoConCertificado.aspx")))
        assertFalse(policy.matchesRequestUrl(URI("$origin/vt/vtwebregistro/(S(synthetic))/AccesoConCertificado.aspx")))
        assertNull(BuiltInSiteProfiles.qaRegistry.profile(ProfileId("itj-sede-public-navigation"))!!.clientAuthPolicy)
    }

    @Test
    fun waveFClaveOwnersCannotTransferTheSelectedProfileOrGrantSigning() {
        val source = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        val target = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        listOf("fondos-europeos-sede-public-home", "imserso-sede-public-navigation",
            "ine-sede-public-navigation", "sepes-transportes-public-complaints", "dgoj-public-navigation")
            .forEach { id ->
            val profile = ProfileId(id)
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(fresh.observeTopLevelResourceRequest(null, source, target, "POST", 4, true))
            assertNull(fresh.observeTopLevelResourceRequest(profile, source, target, "POST", 4, false))
            val grant = fresh.observeTopLevelResourceRequest(profile, source, target, "POST", 4, true)
            assertEquals(profile, grant?.profileId)
            assertFalse(Capability.SIGN in BuiltInSiteProfiles.qaRegistry.profile(profile)!!.capabilities)
            assertNull(fresh.observeTopLevelResourceRequest(profile, source, target, "POST", 4, true))
        }
    }

    @Test
    fun initialEntryCannotGrantClientTlsBeforeAnObservedSource() {
        BuiltInSiteProfiles.catalog.profiles.forEach { profile ->
            val initial = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            val entry = profile.startUrl.toASCIIString()
            assertNull(
                profile.profileId.value,
                initial.observeTopLevelNavigation(profile.profileId, null, entry, 0, true),
            )
            initial.onTopLevelPageStarted(entry, 1)
            assertNull(
                profile.profileId.value,
                initial.observeTopLevelResourceRequest(profile.profileId, null, entry, "GET", 1, true),
            )
        }
    }

    @Test
    fun dgojRequiresTheExactTopLevelClavePostWithoutSessionPathExceptions() {
        val profile = ProfileId("dgoj-public-navigation")
        val source = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        val target = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val start = BuiltInSiteProfiles.qaRegistry.profile(profile)!!.startUrl.toString()
        val invalidSources = listOf(null, start, target, "$source?extra=1", "$source;DGOJ_ID=synthetic",
            source.replace("ServiceRedirect", "ServiceProvider"),
            "https://sede.ordenacionjuego.gob.es/tramite/login/plogin.jjsp;DGOJ_ID=synthetic")
        invalidSources.forEach { current ->
            assertNull(authorizer.observeTopLevelResourceRequest(profile, current, target, "POST", 4, true))
        }
        val invalidTargets = listOf("$target?extra=1", "$target;DGOJ_ID=synthetic",
            target.replace("AuthenticateCitizen", "Other"),
            target.replace(".gob.es", ".gob.es:444"), target.replace(".gob.es", ".gob.es.evil.example"))
        invalidTargets.forEach { changed ->
            assertNull(authorizer.observeTopLevelResourceRequest(profile, source, changed, "POST", 4, true))
        }
        assertNull(authorizer.observeTopLevelResourceRequest(profile, source, target, "GET", 4, true))
        assertNull(authorizer.observeTopLevelResourceRequest(profile, source, target, "POST", 4, false))
        assertNull(authorizer.observeTopLevelResourceRequest(null, source, target, "POST", 4, true))
        assertNull(authorizer.observeTopLevelResourceRequest(profile, source, target, "POST", Long.MAX_VALUE, true))
        assertNull(authorizer.observeTopLevelNavigation(profile, source, target, 4, true))
        assertNull(authorizer.observeTopLevelResourceRequest(
            ProfileId("boe-sede-public-home"), source, target, "POST", 4, true,
        ))
        val grant = authorizer.observeTopLevelResourceRequest(profile, source, target, "POST", 4, true)
        assertEquals(profile, grant?.profileId)
        assertEquals(URI(source), grant?.source)
        assertEquals(URI(target), grant?.target)
        assertNull(authorizer.observeTopLevelResourceRequest(profile, source, target, "POST", 4, true))
        assertFalse(grant!!.isExpiredOrInvalid(monotonic.nowNanos()))
        monotonic.advance(Duration.ofSeconds(31))
        assertTrue(grant.isExpiredOrInvalid(monotonic.nowNanos()))

        for (attack in listOf("epoch", "expired", "profile")) {
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(fresh.observeTopLevelResourceRequest(profile, start, source, "POST", 10, true))
            if (attack == "expired") monotonic.advance(Duration.ofSeconds(16))
            val selected = if (attack == "profile") ProfileId("imserso-sede-public-navigation") else profile
            val epoch = if (attack == "epoch") 12L else 11L
            assertNull(fresh.observeTopLevelResourceRequest(selected, null, target, "POST", epoch, true))
        }
    }

    @Test
    fun ownTlsPostLoginsKeepTheSelectedSourceMethodEpochAndSingleUseBoundary() {
        val flows = listOf(
            Triple(
                "comunidad-madrid-cuenta-digital-53f1",
                "https://gestiona.comunidad.madrid/auto_login/seleccion-idp.jsf?id=synthetic-session",
                "https://gestiona2.comunidad.madrid/auto_certificado/SelCertificado",
            ),
            Triple(
                "seguridad-social-sede-autofirma", "https://idp.seg-social.es/PGIS/Login?seleccion=IPCE",
                "https://ipce.seg-social.es/IPCE/Login",
            ),
            Triple(
                "tgss-importass-client-auth", "https://idp.seg-social.es/PGIS/Login?seleccion=IPCE",
                "https://ipce.seg-social.es/IPCE/Login",
            ),
        )
        for ((id, source, target) in flows) {
            val profile = ProfileId(id)
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(fresh.observeTopLevelNavigation(profile, source, target, 20, true))
            assertNull(fresh.observeTopLevelResourceRequest(profile, source, target, "GET", 20, true))
            assertNull(fresh.observeTopLevelResourceRequest(profile, source, target, "POST", 20, false))
            assertNull(fresh.observeTopLevelResourceRequest(profile, "$source&extra=1", target, "POST", 20, true))
            // The source POST may be observed before its HTML becomes the committed current URL.
            assertNull(
                fresh.observeTopLevelResourceRequest(profile, source.substringBefore('?'), source, "POST", 20, true),
            )
            fresh.onTopLevelPageStarted(source, 21)
            val result = fresh.observeTopLevelResourceRequest(
                profile, source.substringBefore('?'), target, "POST", 21, true,
            )
            assertEquals(id, result?.profileId?.value)
            assertEquals(URI(source), result?.source)
            assertEquals(URI(target), result?.target)
            assertNull(fresh.observeTopLevelResourceRequest(profile, source, target, "POST", 21, true))

            val wrongEpoch = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            wrongEpoch.observeTopLevelResourceRequest(profile, source.substringBefore('?'), source, "POST", 20, true)
            assertNull(
                wrongEpoch.observeTopLevelResourceRequest(
                    profile, source.substringBefore('?'), target, "POST", 22, true,
                ),
            )
        }
    }

    @Test
    fun huelvaRedirectGrantRequiresTheFreshSourceAndRejectsASecondPageStart() {
        val profile = ProfileId("diputacion-huelva-sede-public")
        val home = "https://sede.diphuelva.es/opencms/sede/elements/secciones/index"
        val source = "https://sede.diphuelva.es/opencms/system/modules/gsede/" +
            "elements/secciones/autenticacion/autenticacion.jsp"
        val target = "https://authserver.diphuelva.es/?ticket=synthetic-session"
        assertNull(authorizer.observeTopLevelNavigation(profile, home, source, 10, true))
        authorizer.onTopLevelPageStarted(source, 11)
        assertNull(authorizer.observeTopLevelResourceRequest(profile, source, source, "POST", 11, true))
        assertEquals(
            profile,
            authorizer.observeTopLevelNavigation(profile, source, target, 11, true)?.profileId,
        )
        val secondStart = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        secondStart.observeTopLevelNavigation(profile, home, source, 10, true)
        secondStart.onTopLevelPageStarted(source, 11)
        secondStart.onTopLevelPageStarted(source, 12)
        assertNull(secondStart.observeTopLevelNavigation(profile, source, target, 12, true))
    }

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
        assertEquals("/wlpl/OVCT-CXEW/DialogoRepresentacion", result?.target?.rawPath)
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
    fun leonDynamicSourceAuthorizesOnlyTheExactLoginSourceOnExactCertificateHost() {
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
            LEON_SOURCE.substringBefore("?") to LEON_TARGET,
            LEON_SOURCE.replace("login.aspx", "login2.aspx") to LEON_TARGET,
            "$LEON_SOURCE&extra=1" to LEON_TARGET,
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
    fun mallorcaDynamicSourceAuthorizesOnlyTheExactLoginSourceOnExactCertificateHost() {
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
            MALLORCA_SOURCE.substringBefore("?") to MALLORCA_TARGET,
            MALLORCA_SOURCE.replace("login.aspx", "login2.aspx") to MALLORCA_TARGET,
            "$MALLORCA_SOURCE&extra=1" to MALLORCA_TARGET,
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
    fun albaceteDynamicSourceAuthorizesOnlyTheExactLoginSourceOnExactCertificateHost() {
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
            ALBACETE_SOURCE.substringBefore("?") to ALBACETE_TARGET,
            ALBACETE_SOURCE.replace("login.aspx", "login2.aspx") to ALBACETE_TARGET,
            "$ALBACETE_SOURCE&extra=1" to ALBACETE_TARGET,
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
        assertEquals("/wlpl/OVCT-CXEW/DialogoRepresentacion", result?.target?.rawPath)
        assertEquals("ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0", result?.target?.rawQuery)
        assertEquals(URI(TEA_SOURCE), result?.source)
        assertNull(
            authorizer.observeTopLevelNavigation(
                TEA_PROFILE, TEA_SOURCE, TEA_TARGET, 42, true,
            ),
        )
        assertFalse(result!!.isExpiredOrInvalid(monotonic.nowNanos()))
        monotonic.advance(Duration.ofSeconds(16))
        assertTrue(result.isExpiredOrInvalid(monotonic.nowNanos()))
    }

    @Test
    fun teaAlegacionesDirectTransitionRejectsEverySourceAndTargetExpansion() {
        val invalidCalls = listOf<Pair<String, String>>(
            "https://sede.tea.hacienda.gob.es/TEA/alegaciones.html" to TEA_TARGET,
            TEA_SOURCE.replace("rep=S", "rep=N") to TEA_TARGET,
            TEA_SOURCE.replace("aut=CPE", "aut=PE") to TEA_TARGET,
            TEA_SOURCE.replace("tram%3D0", "tram%3D2") to TEA_TARGET,
            "$TEA_SOURCE&rep=S" to TEA_TARGET,
            TEA_SOURCE.replace("sede.tea.hacienda.gob.es", "foreign.example") to TEA_TARGET,
            TEA_SOURCE to TEA_TARGET.replace("tram%3D0", "tram%3D2"),
            TEA_SOURCE to "$TEA_TARGET&extra=1",
            TEA_SOURCE to "$TEA_TARGET&ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0",
            TEA_SOURCE to "https://www1.tea.hacienda.gob.es/wlpl/TEAC-TRAM/SedeTRAM?tram=0",
            TEA_SOURCE to TEA_TARGET.replace("/DialogoRepresentacion", "/DialogoRepresentacion/other"),
            TEA_SOURCE to TEA_TARGET.replace("www1.", "www6."),
            TEA_SOURCE to TEA_TARGET.replace("www1.", "www11."),
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
    fun teaSelectorRequiresProfiledTopLevelNavigationAndRejectsResourceArming() {
        assertNull(authorizer.observeTopLevelNavigation(null, TEA_SOURCE, TEA_TARGET, 42, true))
        assertNull(authorizer.observeTopLevelNavigation(TEA_PROFILE, TEA_SOURCE, TEA_TARGET, 42, false))
        assertNull(authorizer.observeTopLevelNavigation(TEA_PROFILE, null, TEA_TARGET, 42, true))
        assertNull(authorizer.observeTopLevelNavigation(TEA_PROFILE, TEA_TARGET, TEA_TARGET, 42, true))
        assertNull(authorizer.observeTopLevelNavigation(TEA_PROFILE, TEA_SOURCE, TEA_TARGET, Long.MAX_VALUE, true))
        assertNull(authorizer.observeTopLevelResourceRequest(TEA_PROFILE, TEA_SOURCE, TEA_TARGET, "POST", 42, true))
        assertNull(authorizer.observeTopLevelResourceRequest(TEA_PROFILE, TEA_SOURCE, TEA_TARGET, "GET", 42, false))
        assertNull(authorizer.observeTopLevelNavigation(AEAT_PROFILE, TEA_SOURCE, TEA_TARGET, 42, true))
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
            SANIDAD_SOURCE to SANIDAD_TARGET.replace("TRAM_TEMPRANAATENCION", "TRAM_OTHER"),
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
        assertNull(education.observeTopLevelNavigation(
            EDUCATION_PROFILE, EDUCATION_SOURCE, EDUCATION_TARGET, 76, true,
        ))
        assertNull(education.observeTopLevelResourceRequest(
            EDUCATION_PROFILE, EDUCATION_SOURCE, EDUCATION_TARGET, "GET", 77, true,
        ))
        val authorized = education.observeTopLevelResourceRequest(
            EDUCATION_PROFILE, EDUCATION_SOURCE, EDUCATION_TARGET, "POST", 78, true,
        )

        assertEquals(EDUCATION_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)
        assertNull(
            education.observeTopLevelResourceRequest(
                EDUCATION_PROFILE, EDUCATION_SOURCE, EDUCATION_TARGET, "POST", 78, true,
            ),
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
                fresh.observeTopLevelResourceRequest(
                    EDUCATION_PROFILE, EDUCATION_SOURCE, invalidTarget, "POST", 180L + index, true,
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
                fresh.observeTopLevelResourceRequest(
                    EDUCATION_PROFILE, invalidSource, EDUCATION_TARGET, "POST", 190L + index, true,
                ),
            )
        }
    }

    @Test
    fun exactAirefClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val airef = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        // A plain navigation from Cl@ve no longer arms anything: the AFIRMA hop is a POST chain.
        assertNull(airef.observeTopLevelNavigation(AIREF_PROFILE, AIREF_SERVICE_PROVIDER, AIREF_TARGET, 79, true))
        assertNull(
            airef.observeTopLevelResourceRequest(
                AIREF_PROFILE, AIREF_SERVICE_PROVIDER, AIREF_SOURCE, "POST", 80, true,
            ),
        )
        val authorized = airef.observeTopLevelResourceRequest(
            AIREF_PROFILE, AIREF_SOURCE, AIREF_TARGET, "POST", 81, true,
        )

        assertEquals(AIREF_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)

        listOf(
            Triple(AIREF_SOURCE, AIREF_TARGET, "GET"),
            Triple(AIREF_SOURCE, AIREF_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"), "POST"),
            Triple(AIREF_SOURCE, "$AIREF_TARGET?extra=1", "POST"),
            Triple(
                AIREF_SOURCE,
                AIREF_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
                "POST",
            ),
            Triple(AIREF_SOURCE, AIREF_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"), "POST"),
            Triple("https://evil.example/Proxy2/ServiceRedirect", AIREF_TARGET, "POST"),
        ).forEachIndexed { index, (current, target, method) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$current -> $target $method",
                fresh.observeTopLevelResourceRequest(AIREF_PROFILE, current, target, method, 90L + index, true),
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
    fun claveProfilesAuthorizeOnlyTheReviewedPostToAuthenticateCitizen() {
        CLAVE_DIRECT_PROFILES.forEachIndexed { profileIndex, profile ->
            val source = if (profile.value == "madrid-sede-tarjeta-azul") {
                CLAVE_SERVICE_PROVIDER
            } else {
                CLAVE_SERVICE_REDIRECT
            }
            val base = 100L * (profileIndex + 1)
            val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            // The AFIRMA hop is a POST chain, so a plain navigation arms nothing.
            assertNull(
                "$profile",
                authorizer.observeTopLevelNavigation(profile, CLAVE_SERVICE_PROVIDER, CLAVE_TARGET, base, true),
            )
            assertNull(
                "$profile",
                authorizer.observeTopLevelResourceRequest(
                    profile, CLAVE_SERVICE_PROVIDER, CLAVE_SERVICE_REDIRECT, "POST", base + 1, true,
                ),
            )
            val authorized = authorizer.observeTopLevelResourceRequest(
                profile, source, CLAVE_TARGET, "POST", base + 2, true,
            )
            assertEquals("$profile", profile, authorized?.profileId)
            assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
            assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
            assertNull(authorized?.target?.rawQuery)

            listOf(
                Triple(source, CLAVE_TARGET, "GET"),
                Triple(
                    if (source == CLAVE_SERVICE_PROVIDER) CLAVE_SERVICE_REDIRECT else CLAVE_SERVICE_PROVIDER,
                    CLAVE_TARGET,
                    "POST",
                ),
                Triple(
                    source,
                    CLAVE_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
                    "POST",
                ),
                Triple(source, "$CLAVE_TARGET?extra=1", "POST"),
                Triple(
                    source,
                    CLAVE_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
                    "POST",
                ),
                Triple(
                    source,
                    CLAVE_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"),
                    "POST",
                ),
                Triple("https://evil.example/Proxy2/ServiceRedirect", CLAVE_TARGET, "POST"),
            ).forEachIndexed { index, (current, target, method) ->
                val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
                assertNull(
                    "$profile $current -> $target $method",
                    fresh.observeTopLevelResourceRequest(profile, current, target, method, base + 10 + index, true),
                )
            }
        }
    }

    @Test
    fun claveInPlacePostGrantOutlivesSlowTlsChallengeButSourceHopKeepsFifteenSeconds() {
        val mugeju = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        assertNull(
            mugeju.observeTopLevelResourceRequest(
                MUGEJU_PROFILE, MUGEJU_SERVICE_PROVIDER, MUGEJU_SOURCE, "POST", 80, true,
            ),
        )
        val authorized = checkNotNull(
            mugeju.observeTopLevelResourceRequest(MUGEJU_PROFILE, MUGEJU_SOURCE, MUGEJU_TARGET, "POST", 81, true),
        )
        monotonic.advance(Duration.ofSeconds(29))
        assertFalse(authorized.isExpiredOrInvalid(monotonic.nowNanos()))
        monotonic.advance(Duration.ofSeconds(2))
        assertTrue(authorized.isExpiredOrInvalid(monotonic.nowNanos()))

        // The ServiceRedirect -> AuthenticateCitizen hop itself keeps the reviewed 15 s window.
        fun armedThenRequestedAfter(delay: Duration): AuthorizedClientAuthTarget? {
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                fresh.observeTopLevelResourceRequest(
                    MUGEJU_PROFILE, MUGEJU_SERVICE_PROVIDER, MUGEJU_SOURCE, "POST", 90, true,
                ),
            )
            monotonic.advance(delay)
            return fresh.observeTopLevelResourceRequest(
                MUGEJU_PROFILE, MUGEJU_SERVICE_PROVIDER, MUGEJU_TARGET, "POST", 91, true,
            )
        }
        assertEquals(MUGEJU_PROFILE, armedThenRequestedAfter(Duration.ofSeconds(14))?.profileId)
        assertNull(armedThenRequestedAfter(Duration.ofSeconds(16)))
    }

    @Test
    fun nonClaveInPlacePostGrantKeepsItsPolicyTtl() {
        val tarragona = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val authorized = checkNotNull(
            tarragona.observeTopLevelResourceRequest(
                TARRAGONA_PROFILE, TARRAGONA_VALID_SOURCE, TARRAGONA_CERT_TARGET, "POST", 120, true,
            ),
        )
        monotonic.advance(Duration.ofSeconds(14))
        assertFalse(authorized.isExpiredOrInvalid(monotonic.nowNanos()))
        monotonic.advance(Duration.ofSeconds(2))
        assertTrue(authorized.isExpiredOrInvalid(monotonic.nowNanos()))
    }

    @Test
    fun exactMugejuClaveTransitionAuthorizesOnlyTheObservedIdentifierCertificateRequest() {
        val mugeju = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        // A plain navigation from Cl@ve no longer arms anything: the AFIRMA hop is a POST chain.
        assertNull(mugeju.observeTopLevelNavigation(MUGEJU_PROFILE, MUGEJU_SERVICE_PROVIDER, MUGEJU_TARGET, 79, true))
        assertNull(
            mugeju.observeTopLevelResourceRequest(
                MUGEJU_PROFILE, MUGEJU_SERVICE_PROVIDER, MUGEJU_SOURCE, "POST", 80, true,
            ),
        )
        val authorized = mugeju.observeTopLevelResourceRequest(
            MUGEJU_PROFILE, MUGEJU_SOURCE, MUGEJU_TARGET, "POST", 81, true,
        )

        assertEquals(MUGEJU_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
        assertNull(authorized?.target?.rawQuery)

        listOf(
            Triple(MUGEJU_SOURCE, MUGEJU_TARGET, "GET"),
            Triple(MUGEJU_SOURCE, MUGEJU_TARGET.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"), "POST"),
            Triple(MUGEJU_SOURCE, "$MUGEJU_TARGET?extra=1", "POST"),
            Triple(
                MUGEJU_SOURCE,
                MUGEJU_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
                "POST",
            ),
            Triple(MUGEJU_SOURCE, MUGEJU_TARGET.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"), "POST"),
            Triple("https://evil.example/Proxy2/ServiceRedirect", MUGEJU_TARGET, "POST"),
        ).forEachIndexed { index, (current, target, method) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$current -> $target $method",
                fresh.observeTopLevelResourceRequest(MUGEJU_PROFILE, current, target, method, 90L + index, true),
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
                AEAT_PROFILE, AEAT_SOURCE, "$AEAT_TARGET&extra=1", 70, true,
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
                    AEAT_SOURCE.replace("sede.agenciatributaria.gob.es", "sede.agenciatributaria.gob.es.evil.example"),
                    AEAT_TARGET,
                    50,
                    true,
                )
            },
            {
                it.observeTopLevelNavigation(
                    AEAT_PROFILE,
                    AEAT_SOURCE.replace("MdcAcceso", "OtroServicio"),
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
            AEAT_TARGET.replace("/DialogoRepresentacion", "/Other"),
            AEAT_TARGET.replace("/DialogoRepresentacion", "/DialogoRepresentacion%2Fother"),
            // The representation dialog is a gateway to every AEAT service: ref must stay pinned.
            AEAT_TARGET.replace("MdcAcceso", "OtroServicio"),
            AEAT_TARGET.substringBefore('?'),
            AEAT_TARGET.replace("www1.agenciatributaria.gob.es", "www1.agenciatributaria.gob.es:8443"),
            "$AEAT_TARGET#fragment",
            "$AEAT_TARGET&extra=1",
            "$AEAT_TARGET&ref=%2Fwlpl%2FOTRO",
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
    fun aragonSsLoginRedirectAuthorizesBalancedLogin2Endpoint() {
        val aragon = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        aragon.observeTopLevelNavigation(ARAGON_TRAMITES_PROFILE, ARAGON_TRAMITES_INDEX, ARAGON_SSLOGIN_SOURCE, 880, true)
        aragon.onTopLevelPageStarted(ARAGON_SSLOGIN_SOURCE, 881)
        val authorized = aragon.observeTopLevelNavigation(
            ARAGON_TRAMITES_PROFILE,
            ARAGON_SSLOGIN_SOURCE,
            ARAGON_SSLOGIN_TARGET.replace("login1.loginssl.aragon.es", "login2.loginssl.aragon.es"),
            881,
            true,
        )
        assertEquals("login2.loginssl.aragon.es", authorized?.target?.host)
        assertNull(
            aragon.observeTopLevelNavigation(
                ARAGON_TRAMITES_PROFILE,
                ARAGON_SSLOGIN_SOURCE,
                ARAGON_SSLOGIN_TARGET.replace("login1.loginssl.aragon.es", "login3.loginssl.aragon.es"),
                881,
                true,
            ),
        )
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
    fun malagaPostTargetPageStartDoesNotDropSourceBeforeTargetResourceCallback() {
        val malaga = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )

        assertNull(
            malaga.observeTopLevelResourceRequest(
                activeProfileId = MALAGA_PROFILE,
                currentUrl = MALAGA_SERVICE_PROVIDER,
                targetUrl = MALAGA_SERVICE_REDIRECT,
                method = "POST",
                currentEpoch = 220,
                isMainFrameRequest = true,
            ),
        )

        // Android WebView may publish the target page start before the target POST reaches
        // shouldInterceptRequest. The exact target page start must not discard the source arm.
        malaga.onTopLevelPageStarted(MALAGA_TARGET, currentEpoch = 221)

        val authorized = malaga.observeTopLevelResourceRequest(
            activeProfileId = MALAGA_PROFILE,
            currentUrl = MALAGA_TARGET,
            targetUrl = MALAGA_TARGET,
            method = "POST",
            currentEpoch = 221,
            isMainFrameRequest = true,
        )

        assertEquals(MALAGA_PROFILE, authorized?.profileId)
        assertEquals("pasarela-ident.clave.gob.es", authorized?.target?.host)
        assertEquals("/IdP2/AuthenticateCitizen", authorized?.target?.rawPath)
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
                requestPath = "/wlpl/OVCT-CXEW/DialogoRepresentacion",
                fixedQueryParameters = mapOf("ref" to "/wlpl/BUGC-JDIT/MdcAcceso"),
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

    @Test
    fun veaServerRedirectAuthorizesExactWs235WithoutCommittingApiSourcePage() {
        val vea = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val source = veaSource()
        val target = veaTarget()

        assertNull(
            vea.observeTopLevelNavigation(
                VEA_PROFILE, VEA_AUTH_FACADE, source, 195, true,
            ),
        )
        val authorized = vea.observeTopLevelNavigation(
            VEA_PROFILE, VEA_AUTH_FACADE, target, 195, true,
        )

        assertEquals(VEA_PROFILE, authorized?.profileId)
        assertEquals("ws235.juntadeandalucia.es", authorized?.target?.host)
        assertEquals("/authenticationFacade", authorized?.target?.rawPath)
    }

    @Test
    fun veaInPlaceGetAuthorizesOnlyExactApiSourceToExactWs235Target() {
        val vea = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        val source = veaSource()
        val target = veaTarget()

        val authorized = vea.observeTopLevelResourceRequest(
            activeProfileId = VEA_PROFILE,
            currentUrl = source,
            targetUrl = target,
            method = "GET",
            currentEpoch = 200,
            isMainFrameRequest = true,
        )

        assertEquals(VEA_PROFILE, authorized?.profileId)
        assertEquals("ws235.juntadeandalucia.es", authorized?.target?.host)
        assertEquals("/authenticationFacade", authorized?.target?.rawPath)
        assertEquals("CHIE.VEA", authorized?.policy?.fixedQueryParameters?.get("appId"))
        assertNull(
            vea.observeTopLevelResourceRequest(
                VEA_PROFILE, source, target, "GET", 200, true,
            ),
        )
    }

    @Test
    fun veaInPlaceGetRejectsSourceTargetMethodAndFrameNearMisses() {
        val invalidCalls = listOf(
            Triple(veaSource(redirectUrl = "https://evil.example/?iniciarSolicitud=true&procedureId=1&versionId=2"), veaTarget(), "GET"),
            Triple(veaSource(redirectUrl = "$VEA_START?iniciarSolicitud=false&procedureId=1&versionId=2"), veaTarget(), "GET"),
            Triple(veaSource(redirectUrl = "$VEA_START?iniciarSolicitud=true&procedureId=1&versionId=2&extra=1"), veaTarget(), "GET"),
            Triple(veaSource().replace("modoAcceso=afirma", "modoAcceso=clave"), veaTarget(), "GET"),
            Triple(veaSource().replace("codigoProcedimiento=PEG_VEA", "codigoProcedimiento=OTHER"), veaTarget(), "GET"),
            Triple(veaSource(), veaTarget(appId = "IAJ.CARNETJOVEN"), "GET"),
            Triple(veaSource(), veaTarget(action = "other"), "GET"),
            Triple(veaSource(), veaTarget(callback = "https://evil.example/return"), "GET"),
            Triple(veaSource(), veaTarget().replace("ws235.juntadeandalucia.es", "ws235.juntadeandalucia.es.evil.example"), "GET"),
            Triple(veaSource(), veaTarget() + "&extra=1", "GET"),
            Triple(veaSource(), veaTarget(), "POST"),
        )

        invalidCalls.forEachIndexed { index, (source, target, method) ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
            assertNull(
                "$source -> $target",
                fresh.observeTopLevelResourceRequest(
                    VEA_PROFILE, source, target, method, 210L + index, true,
                ),
            )
        }

        val subframe = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        assertNull(
            subframe.observeTopLevelResourceRequest(
                VEA_PROFILE, veaSource(), veaTarget(), "GET", 230, false,
            ),
        )
    }

    private fun veaSource(
        redirectUrl: String = "$VEA_START?iniciarSolicitud=true&procedureId=123&versionId=456",
    ): String = buildString {
        append(VEA_API_LOGIN)
        append("?modoAcceso=afirma")
        append("&comeBackUrl=").append(urlEncode(base64(VEA_AUTH_FACADE)))
        append("&redirectUrl=").append(urlEncode(base64(redirectUrl)))
        append("&codigoProcedimiento=PEG_VEA")
    }

    private fun veaTarget(
        appId: String = "CHIE.VEA",
        action: String = "validateCert",
        callback: String = VEA_API_RETURN,
    ): String = buildString {
        append("https://ws235.juntadeandalucia.es/authenticationFacade")
        append("?action=").append(action)
        append("&appId=").append(appId)
        append("&comeBackURL=").append(urlEncode(base64(callback)))
        append("&ticketId=synthetic-ticket")
        append("&webSessionId=synthetic-session")
    }

    private fun base64(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        val VEA_PROFILE = ProfileId("junta-andalucia-vea-peg")
        const val VEA_ORIGIN = "https://veaja.cloud.juntadeandalucia.es"
        const val VEA_START = "$VEA_ORIGIN/inicio/procedimiento-detalle/PEG_VEA"
        const val VEA_AUTH_FACADE = "$VEA_ORIGIN/authFacade"
        const val VEA_API_LOGIN = "https://api-veaja.cloud.juntadeandalucia.es/auth/login"
        const val VEA_API_RETURN = "https://api-veaja.cloud.juntadeandalucia.es/auth/returnLogin"
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
        const val AIREF_SERVICE_PROVIDER = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val AIREF_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val AIREF_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val CLAVE_DIRECT_PROFILES = listOf(
            ProfileId("madrid-sede-tarjeta-azul"),
            ProfileId("cnmv-sede-public-home"),
            ProfileId("csd-sede-public"),
            ProfileId("policia-solicitud-generica"),
            ProfileId("catastro-solicitudes-genericas"),
            ProfileId("ministerio-economia-instancia-generica"),
            ProfileId("age-portal-de-la-transparencia"),
            ProfileId("red-es-sede-public-navigation"),
            ProfileId("diputacion-almeria-solicitud-general"),
            ProfileId("diputacion-cordoba-solicitud-generica"),
            ProfileId("diputacion-palencia-solicitud-general"),
            ProfileId("diputacion-pontevedra-instancia-xenerica"),
            ProfileId("educacion-convocatoria"),
            ProfileId("extremadura-tramites"),
            ProfileId("fuerteventura-sede-electronica"),
            ProfileId("justicia-sede-judicial-private-area"),
            ProfileId("mjusticia-fundaciones-idp75"),
            ProfileId("asturias-miprincipado"),
            ProfileId("diputacion-ourense-sede"),
            ProfileId("diputacion-sevilla-sede"),
            ProfileId("diputacion-a-coruna-solicitud-general"),
        )
        const val CLAVE_SERVICE_PROVIDER = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val CLAVE_SERVICE_REDIRECT = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val CLAVE_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val CATALUNYA_PROFILE = ProfileId("catalunya-peticio-generica-client-auth")
        const val CATALUNYA_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val CATALUNYA_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val MUGEJU_PROFILE = ProfileId("mugeju-remision-documentacion-client-auth")
        const val MUGEJU_SERVICE_PROVIDER = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val MUGEJU_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val MUGEJU_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val VALLADOLID_PROFILE = ProfileId("diputacion-valladolid-sede")
        val MALLORCA_PROFILE = ProfileId("consell-mallorca-sede")
        const val MALLORCA_TOKEN = "12345678-w47SyntheticMallorcaToken0123456789"
        const val MALLORCA_SOURCE =
            "https://cim.secimallorca.net/carpetaciudadana/login.aspx?returnUrl=https%3a%2f%2fcim.secimallorca.net%2fsegex%2ftramite.aspx%3fidtramite%3d12082"
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
        const val ALBACETE_SOURCE =
            "https://sede.dipualba.es/carpetaciudadana/login.aspx?returnUrl=https%3a%2f%2fsede.dipualba.es%2fcarpetaciudadana%2ftramite.aspx%3fidtramite%3d567"
        const val ALBACETE_TARGET =
            "https://identificacionssl.sedipualba.es/?idtoken=$ALBACETE_TOKEN&idioma=es&entidad=02000"
        const val LEON_TOKEN = "12345678-w47SyntheticLeonToken0123456789"
        const val LEON_SOURCE =
            "https://sede.dipuleon.es/carpetaciudadana/login.aspx?returnUrl=https%3a%2f%2fsede.dipuleon.es%2fcarpetaciudadana%2ftramite.aspx%3fidtramite%3d20270"
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
            "https://sede.agenciatributaria.gob.es/static_files/common/html/selector_acceso/SelectorAccesos.html" +
                "?rep=S&ref=%2Fwlpl%2FBUGC-JDIT%2FMdcAcceso&aut=CPE"
        const val AEAT_TARGET =
            "https://www1.agenciatributaria.gob.es/wlpl/OVCT-CXEW/DialogoRepresentacion" +
                "?ref=%2Fwlpl%2FBUGC-JDIT%2FMdcAcceso"
        const val TOLEDO_SOURCE =
            "https://diputacion.toledo.gob.es/SIGEM_AutenticacionWeb/" +
                "seleccionEntidad.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_31&" +
                "SESION_ID=&ENTIDAD_ID=&LANG=&COUNTRY="
        const val TOLEDO_TARGET =
            "https://diputacion.toledo.gob.es:843/SIGEM_AutenticacionWeb/validacionCertificado.do"
        const val TEA_SOURCE =
            "https://sede.tea.hacienda.gob.es/static_files/common/html/selector_acceso/" +
                "SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0&aut=CPE"
        const val TEA_TARGET =
            "https://www1.tea.hacienda.gob.es/wlpl/OVCT-CXEW/DialogoRepresentacion" +
                "?ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0"
        const val SANIDAD_SOURCE =
            "https://sede.mscbs.gob.es/registroElectronico/formularios.htm"
        const val SANIDAD_TARGET =
            "https://sede.mscbs.gob.es/SIGEM_AutenticacionWeb/validacionCertificado.do?" +
                "REDIRECCION=RegistroTelematico&tramiteId=TRAM_TEMPRANAATENCION&" +
                "ENTIDAD_ID=000&LANG=es&COUNTRY=ES"
        const val VALLADOLID_INDEX =
            "https://www.sede.diputaciondevalladolid.es/tgauth/login"
        const val VALLADOLID_SOURCE =
            "https://www.sede.diputaciondevalladolid.es/c/portal/cert-login"
        const val VALLADOLID_TARGET =
            "https://www.sede.diputaciondevalladolid.es:21460/c/portal/cert-login"
        val TARRAGONA_PROFILE = ProfileId("diputacion-tarragona-sede")
        const val TARRAGONA_VALID_SOURCE =
            "https://valid.aoc.cat/o/oauth2/auth?response_type=code&client_id=valid.dipta.cat&" +
                "redirect_uri=https%3A%2F%2Fegovern.altanet.org%2Fvalid%2Fcode&" +
                "scope=autenticacio_usuari&state=synthetic-state&access_type=online&approval_prompt=auto"
        const val TARRAGONA_CERT_TARGET = "https://cert.valid.aoc.cat/o/oauth2/cert"
        val MALAGA_PROFILE = ProfileId("diputacion-malaga-instancia-general")
        const val MALAGA_SERVICE_PROVIDER =
            "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val MALAGA_SERVICE_REDIRECT =
            "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val MALAGA_TARGET =
            "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        const val INDEX = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&ticketId=synthetic-ticket&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
    }
}
