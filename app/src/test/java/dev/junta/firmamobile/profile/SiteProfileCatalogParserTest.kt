package dev.junta.firmamobile.profile

import dev.junta.firmamobile.BuildConfig
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class SiteProfileCatalogParserTest {
    @Test
    fun generatedBuildConfigSourceMatchesTheCompiledCatalog() {
        assertEquals(BuildConfig.SITE_PROFILE_CATALOG_JSON, BuiltInSiteProfiles.JSON)
        assertEquals(
            BuiltInSiteProfiles.catalog,
            SiteProfileCatalogParser.parse(BuildConfig.SITE_PROFILE_CATALOG_JSON),
        )
    }

    @Test
    fun preservesTheJuntaGoldenContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("junta-andalucia")
        }
        assertEquals(ProfileId("junta-andalucia"), profile.profileId)
        assertEquals(CompatibilityStatus.EXPERIMENTAL, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(
            "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs",
            profile.startUrl.toString(),
        )
        assertEquals(
            setOf(
                "www.juntadeandalucia.es", "sede.juntadeandalucia.es",
                "ssoweb.juntadeandalucia.es", "pfirma.juntadeandalucia.es",
                "ws024.juntadeandalucia.es", "ws050.juntadeandalucia.es",
            ),
            (profile.initiatorOrigins + profile.redirectOrigins + profile.trustedBrowseOrigins)
                .mapTo(linkedSetOf()) { it.host },
        )
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals("Autenticación con certificado", operation.safeDescription)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA, SignatureAlgorithm.SHA256_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(emptySet<String>(), operation.allowedExtraProperties)
        assertEquals(
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            operation.fixedExtraProperties.getValue("serverUrl"),
        )
        assertEquals("keyusage.digitalsignature:true;nonexpired:", operation.fixedExtraProperties.getValue("filters"))
        assertEquals(
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            profile.endpoints.getValue(EndpointId("junta-triphase")).url.toString(),
        )
    }

    @Test
    fun preservesTheExactRegAgeRedSaraContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("reg-age-redsara")
        }
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals("https://reg.redsara.es/es/", profile.startUrl.toString())
        assertEquals(setOf("https://reg.redsara.es"), profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized })
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(setOf(SignatureAlgorithm.SHA512_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.XADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertNull(operation.endpointId)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), operation.callbackContractId)
    }

    @Test
    fun preservesTheExactMelillaQaOnlyBatchContract() {
        val profileId = ProfileId("melilla-sede")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Ciudad Autónoma de Melilla — Sede Electrónica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI(
                "https://sede.melilla.es/sta/CarpetaPublic/doEvent?" +
                    "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999",
            ),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.melilla.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.isNotEmpty())

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(
            ProtocolInputAdapterId("melilla-batch-autoscript-v1"),
            operation.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("melilla-batch-result-v1"),
            operation.callbackContractId,
        )
        assertEquals("Firma por lotes en la Sede Electrónica de Melilla", operation.safeDescription)
        assertEquals(setOf(Capability.SIGN), operation.capabilities)
        assertNull(operation.endpointId)
        assertEquals(setOf(SignatureAlgorithm.SHA256_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.melilla.es.evil.example/")),
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.melilla.es:444/")),
        )
    }

    @Test
    fun preservesTheExactUgrQaOnlyCertificateContract() {
        val profileId = ProfileId("ugr-certificado-login")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.ugr.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(
            ProtocolInputAdapterId("miniapplet-autoscript-v1"),
            operation.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("miniapplet-sign-callback-v1"),
            operation.callbackContractId,
        )
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), operation.capabilities)
        assertNull(operation.endpointId)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ugr.es.evil.example/")),
        )
    }

    @Test
    fun preservesTheExactJccmQaOnlyCertificateProbeContract() {
        val profileId = ProfileId("jccm-certificate-login-probe")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI(
                "https://ventanillaelectronica.jccm.es/administracion_electronica/" +
                    "formularios/identificacion.phtml",
            ),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://ventanillaelectronica.jccm.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://ventanillaelectronica.jccm.es.evil.example/"),
            ),
        )
    }


    @Test
    fun preservesTheExactSevillaAtseQaOnlyCertificateLoginContract() {
        val profileId = ProfileId("sevilla-atse-certificate-login")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Agencia Tributaria de Sevilla — Acceso con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://www.sevilla.org")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.isNotEmpty())

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(
            ProtocolInputAdapterId("miniapplet-autoscript-v1"),
            operation.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("autoscript-sign-callback-v1"),
            operation.callbackContractId,
        )
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), operation.capabilities)
        assertNull(operation.endpointId)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.XADES, operation.format)
        assertEquals(SignaturePackaging.ATTACHED, operation.packaging)
        assertNull(operation.mode)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.sevilla.org.evil.example/")),
        )
    }

    @Test
    fun preservesTheExactCantabriaRecQaOnlyCertificateContract() {
        val profileId = ProfileId("cantabria-rec-cert-login")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://rec.cantabria.es/rec/bienvenida.htm"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://rec.cantabria.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(
            ProtocolInputAdapterId("miniapplet-autoscript-v1"),
            operation.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("miniapplet-sign-callback-v1"),
            operation.callbackContractId,
        )
        assertEquals(setOf(Capability.SIGN), operation.capabilities)
        assertEquals(setOf(SignatureAlgorithm.SHA512_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.IMPLICIT, operation.mode)
        assertNull(operation.endpointId)
        assertEquals(
            mapOf("filters" to "", "mode" to "implicit"),
            operation.fixedExtraProperties,
        )
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://rec.cantabria.es.evil.example/"),
            ),
        )
    }

    @Test
    fun preservesTheExactUnizarAuthenticationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("unizar-tramitador")
        }
        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(setOf("https://tramita.unizar.es"), profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized })
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertEquals(EndpointId("unizar-triphase"), operation.endpointId)
        assertEquals(
            mapOf(
                "precalculatedHashAlgorithm" to "SHA1",
                "serverUrl" to "https://tramita.unizar.es/afirma-server-triphase-signer-2.7.3/SignatureService",
            ),
            operation.fixedExtraProperties,
        )
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), operation.callbackContractId)
    }

    @Test
    fun preservesTheExactAragonSirawContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("aragon-siraw")
        }
        assertEquals(ProfileId("aragon-siraw"), profile.profileId)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(
            "https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw",
            profile.startUrl.toString(),
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://aplicaciones.aragon.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
        val policy = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals("Acceso con certificado a SIRAW", policy.safeDescription)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), policy.algorithms)
        assertEquals(SignatureFormat.CADES, policy.format)
        assertEquals(SignaturePackaging.DETACHED, policy.packaging)
        assertEquals(SignatureMode.EXPLICIT, policy.mode)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), policy.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), policy.callbackContractId)
        assertNull(policy.endpointId)
        assertEquals(
            mapOf("mode" to "explicit", "filter" to "nonexpired"),
            policy.fixedExtraProperties,
        )
        assertTrue(policy.allowedExtraProperties.isEmpty())
    }

    @Test
    fun preservesTheExactJuntaOfvirtualContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("junta-ofvirtual")
        }
        assertEquals(ProfileId("junta-ofvirtual"), profile.profileId)
        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            profile.startUrl.toString(),
        )
        assertEquals(
            setOf("https://ws072.juntadeandalucia.es"),
            profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized },
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals("Acceso con certificado a la Oficina Virtual", operation.safeDescription)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(
            mapOf(
                "serverUrl" to "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService",
                "filters" to "keyusage.digitalsignature:true;nonexpired:",
            ),
            operation.fixedExtraProperties,
        )
        val endpoint = profile.endpoints.getValue(EndpointId("junta-ofvirtual-triphase"))
        assertEquals("ws024.juntadeandalucia.es", endpoint.url.host)
        assertTrue(endpoint.url.originForTest() !in profile.navigationOriginsForTest())
    }

    @Test
    fun rejectsCrossProfileExactEndpointCollision() {
        val existingEndpoint =
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService"

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace(
                    "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService",
                    existingEndpoint,
                ),
            )
        }
    }

    @Test
    fun clientAuthPolicyRequiresExplicitTransitionMode() {
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(
                    "\"transitionMode\": \"REDIRECT_AFTER_SOURCE\",",
                    "",
                ),
            )
        }
    }

    @Test
    fun rejectsAnyExpansionOfTheSanidadSameOriginClientTlsContract() {
        val mutations = listOf(
            "\"tramiteId\":\"TRAM_TARDESCONPLAN\"" to
                "\"tramiteId\":\"TRAM_OTHER\"",
            "\"sourceUrls\":[\"https://sede.mscbs.gob.es/registroElectronico/formularios.htm\"]" to
                "\"sourceUrls\":[\"https://sede.mscbs.gob.es/registroElectronico/home.htm\"]",
            "\"COUNTRY\":\"ES\"},\"requiredEphemeralQueryParameters\":[]" to
                "\"COUNTRY\":\"ES\"},\"requiredEphemeralQueryParameters\":[\"session\"]",
        )

        mutations.forEach { (expected, replacement) ->
            assertTrue("missing Sanidad contract fragment: $expected", BuiltInSiteProfiles.JSON.contains(expected))
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(expected, replacement))
            }
        }
    }

    @Test
    fun rejectsDirectFixedQueryOutsideThePinnedSanidadContract() {
        val aeatPathAndQuery =
            "\"requestPath\":\"/wlpl/BUGC-JDIT/MdcAcceso\",\"fixedQueryParameters\":{}"
        val expanded =
            "\"requestPath\":\"/wlpl/BUGC-JDIT/MdcAcceso\"," +
                "\"fixedQueryParameters\":{\"unexpected\":\"1\"}"

        assertTrue(BuiltInSiteProfiles.JSON.contains(aeatPathAndQuery))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(aeatPathAndQuery, expanded),
            )
        }
    }

    @Test
    fun preservesTheExactAeatClientTlsQaContract() {
        val profileId = ProfileId("aeat-mis-datos-censales")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"),
            profile.startUrl,
        )
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://www1.agenciatributaria.gob.es")),
            policy.requestOrigins,
        )
        assertEquals(setOf(profile.startUrl), policy.sourceUrls)
        assertEquals("/wlpl/BUGC-JDIT/MdcAcceso", policy.requestPath)
        assertTrue(policy.fixedQueryParameters.isEmpty())
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertEquals(false, policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(443, policy.requestPort)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.releaseRegistry.resolve(
                URI("https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"),
            ),
        )
    }

    @Test
    fun preservesTheExactCarneJovenClientTlsContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("carne-joven-andalucia")
        }
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://ws235.juntadeandalucia.es")),
            policy.requestOrigins,
        )
        assertEquals(
            setOf(URI("https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet")),
            policy.sourceUrls,
        )
        assertEquals("/authenticationFacade", policy.requestPath)
        assertEquals(
            mapOf(
                "action" to "validateCert",
                "appId" to "IAJ.CARNETJOVEN",
                "comeBackURL" to
                    "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4v" +
                    "c2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ=",
            ),
            policy.fixedQueryParameters,
        )
        assertEquals(setOf("ticketId", "webSessionId"), policy.requiredEphemeralQueryParameters)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(443, policy.requestPort)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://ws235.juntadeandalucia.es/authenticationFacade"),
            )?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.releaseRegistry.resolve(
                URI("https://ws235.juntadeandalucia.es/authenticationFacade"),
            )?.trustMode,
        )
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace(
                    "[\"https://ws235.juntadeandalucia.es\"]",
                    "[\"https://ws235.juntadeandalucia.es\", \"https://ws236.juntadeandalucia.es\"]",
                ),
            )
        }
    }

    @Test
    fun releaseEnablesVerifiedProfilesWhileQaKeepsExperimentalPortalsAvailable() {
        val junta = ProfileId("junta-andalucia")
        val carne = ProfileId("carne-joven-andalucia")
        val education = ProfileId("educacion-convocatoria")
        val ceuta = ProfileId("ceuta-sede")
        val aragon = ProfileId("aragon-siraw")
        val ofvirtual = ProfileId("junta-ofvirtual")
        val unizar = ProfileId("unizar-tramitador")
        val releaseProfiles = setOf(carne, education, ceuta, aragon, ofvirtual, unizar)
        val qaOnly = setOf(
            junta,
            ProfileId("reg-age-redsara"),
            ProfileId("aeat-mis-datos-censales"),
            ProfileId("dgt-verificacion-equipo"),
        )

        assertEquals(releaseProfiles, BuiltInSiteProfiles.catalog.profiles
            .mapNotNull { profile ->
                profile.profileId.takeIf { BuiltInSiteProfiles.releaseRegistry.profile(it) != null }
            }
            .toSet())
        qaOnly.forEach { profileId ->
            assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
            assertTrue(BuiltInSiteProfiles.qaRegistry.profile(profileId) != null)
        }
        setOf(carne, aragon, ofvirtual, unizar).forEach { profileId ->
            assertEquals(
                CompatibilityStatus.VERIFIED_E2E,
                BuiltInSiteProfiles.releaseRegistry.profile(profileId)?.compatibilityStatus,
            )
        }
        assertEquals(
            CompatibilityStatus.EXPERIMENTAL,
            BuiltInSiteProfiles.qaRegistry.profile(junta)?.compatibilityStatus,
        )
        setOf(education, ceuta).forEach { profileId ->
            assertEquals(
                CompatibilityStatus.BROWSE_ONLY,
                BuiltInSiteProfiles.releaseRegistry.profile(profileId)?.compatibilityStatus,
            )
        }
    }

    @Test
    fun releaseRejectsEverySensitiveEnabledProfileWithoutVerifiedE2eEvidence() {
        val sensitiveCapabilities = setOf(
            Capability.SIGN,
            Capability.SELECT_CERTIFICATE,
            Capability.CLIENT_TLS_AUTH,
        )
        val sensitiveProfiles = BuiltInSiteProfiles.catalog.profiles.filter { profile ->
            profile.capabilities.any(sensitiveCapabilities::contains)
        }
        val nonE2eSensitiveProfiles = sensitiveProfiles.filter { profile ->
            profile.compatibilityStatus != CompatibilityStatus.VERIFIED_E2E
        }
        val verifiedSensitiveProfiles = sensitiveProfiles.filter { profile ->
            profile.activation == ProfileActivation.ENABLED &&
                profile.compatibilityStatus == CompatibilityStatus.VERIFIED_E2E
        }

        assertTrue(nonE2eSensitiveProfiles.isNotEmpty())
        nonE2eSensitiveProfiles.forEach { profile ->
            assertNull(profile.profileId.value, BuiltInSiteProfiles.releaseRegistry.profile(profile.profileId))
        }
        assertTrue(verifiedSensitiveProfiles.isNotEmpty())
        verifiedSensitiveProfiles.forEach { verifiedProfile ->
            val downgradedCatalog = BuiltInSiteProfiles.catalog.copy(
                profiles = BuiltInSiteProfiles.catalog.profiles.map { profile ->
                    if (profile.profileId == verifiedProfile.profileId) {
                        profile.copy(compatibilityStatus = CompatibilityStatus.VERIFIED_CONTRACT)
                    } else {
                        profile
                    }
                },
            )
            val release = SiteProfileRegistry(downgradedCatalog, BuildTrustPolicy.RELEASE)
            val qa = SiteProfileRegistry(downgradedCatalog, BuildTrustPolicy.QA)

            assertNull(verifiedProfile.profileId.value, release.profile(verifiedProfile.profileId))
            assertEquals(
                CompatibilityStatus.VERIFIED_CONTRACT,
                qa.profile(verifiedProfile.profileId)?.compatibilityStatus,
            )
        }
    }

    @Test
    fun rejectsDuplicateUnknownAndUnsupportedSchemaKeys() {
        val json = BuiltInSiteProfiles.JSON
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(json.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1"))
        }
        val catalogVersionToken = "\"catalogVersion\": ${BuiltInSiteProfiles.catalog.catalogVersion}"
        assertTrue(json.contains(catalogVersionToken))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                json.replaceFirst(
                    catalogVersionToken,
                    "\"unknown\": true, $catalogVersionToken",
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(json.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        }
    }

    @Test
    fun rejectsBlankFixedExtraPropertyOutsideCantabriaProfile() {
        val exact = "\"filter\": \"nonexpired:\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(exact))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(
                    exact,
                    "\"filter\": \"\"",
                ),
            )
        }
    }

    @Test
    fun rejectsUnregisteredProtocolAndSha1WithoutLegacyCapability() {
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace("miniapplet-autoscript-v1", "unregistered-adapter"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace("[\"SIGN\", \"LEGACY_SHA1\"]", "[\"SIGN\"]"),
            )
        }
    }

    @Test
    fun rejectsOriginConfusionAndEndpointMetadata() {
        val attacks = listOf(
            "http://www.juntadeandalucia.es",
            "https://user@www.juntadeandalucia.es",
            "https://www.juntadeandalucia.es:8443",
            "https://www.juntadeandalucia.es./",
            "https://127.0.0.1",
            "https://*.juntadeandalucia.es",
        )
        attacks.forEach { attack ->
            assertThrows(attack, IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(
                    BuiltInSiteProfiles.JSON.replaceFirst("https://www.juntadeandalucia.es\"", "$attack\""),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace("TriPhaseSignatureService\"", "TriPhaseSignatureService?override=1\""),
            )
        }
    }

    @Test
    fun releaseDoesNotActivateQaOnlyProfileAndResolutionIsExact() {
        val qaCatalog = SiteProfileCatalogParser.parse(
            BuiltInSiteProfiles.JSON.replace("\"activation\": \"ENABLED\"", "\"activation\": \"QA_ONLY\""),
        )
        val release = SiteProfileRegistry(qaCatalog, BuildTrustPolicy.RELEASE)
        val qa = SiteProfileRegistry(qaCatalog, BuildTrustPolicy.QA)

        assertNull(release.resolve(URI("https://www.juntadeandalucia.es/path")))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            qa.resolve(URI("https://www.juntadeandalucia.es/path"))?.trustMode,
        )
        assertNull(qa.resolve(URI("https://www.juntadeandalucia.es.evil.example/")))
        assertNull(qa.resolve(URI("https://www.juntadeandalucia.es:8443/")))
        assertEquals(
            TrustMode.BROWSE_ONLY,
            qa.resolve(URI("https://sede.juntadeandalucia.es/path"))?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            qa.resolveRedirect(
                ProfileId("junta-andalucia"),
                URI("https://sede.juntadeandalucia.es/path"),
            )?.trustMode,
        )
        assertNull(release.profile(ProfileId("junta-andalucia")))
        assertTrue(release.profileMetadata(ProfileId("junta-andalucia")) != null)
        assertTrue(qa.profile(ProfileId("junta-andalucia")) != null)
    }
}

private fun URI.originForTest() = ExactOrigin.parse("https://$host")

private fun SiteProfile.navigationOriginsForTest() =
    initiatorOrigins + redirectOrigins + trustedBrowseOrigins +
        (clientAuthPolicy?.requestOrigins ?: emptySet())
