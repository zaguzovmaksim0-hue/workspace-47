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

    private companion object {
        val PROFILE = ProfileId("carne-joven-andalucia")
        val AEAT_PROFILE = ProfileId("aeat-mis-datos-censales")
        val TEA_PROFILE = ProfileId("tea-alegaciones-certificado")
        val AIREF_PROFILE = ProfileId("airef-instancia-general")
        const val AIREF_SOURCE = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val AIREF_TARGET = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        val VALLADOLID_PROFILE = ProfileId("diputacion-valladolid-sede")
        val NAVARRA_PROFILE = ProfileId("navarra-sede-registro-general")
        const val NAVARRA_TOKEN = "w47SyntheticNavarraSessionToken0123456789"
        const val NAVARRA_OTHER_TOKEN = "w47OtherNavarraSessionToken9876543210"
        const val NAVARRA_SOURCE =
            "https://ateka.navarra.es/ateka/router?ReturnUrl=$NAVARRA_TOKEN"
        const val NAVARRA_TARGET =
            "https://ateka.navarra.es/ateka/Certificate/login?returnUrl=$NAVARRA_TOKEN"
        val LEON_PROFILE = ProfileId("diputacion-leon-sede")
        const val LEON_TOKEN = "12345678-w47SyntheticLeonToken0123456789"
        const val LEON_OTHER_TOKEN = "87654321-w47OtherLeonToken9876543210"
        const val LEON_SOURCE =
            "https://sede.dipuleon.es/segex/identificacion_opciones.aspx?idtoken=$LEON_TOKEN&idioma=es"
        const val LEON_TARGET =
            "https://identificacionssl.sedipualba.es/?idtoken=$LEON_TOKEN&idioma=es&entidad=24000"
        val SANIDAD_PROFILE = ProfileId("ministerio-sanidad-certificado")
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
        const val INDEX = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&ticketId=synthetic-ticket&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
    }
}
