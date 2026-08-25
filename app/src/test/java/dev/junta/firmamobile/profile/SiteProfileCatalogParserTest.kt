package dev.junta.firmamobile.profile

import dev.junta.firmamobile.BuildConfig
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
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
    fun preservesTheExactJccmRegistroGenericoQaOnlyContractSeparatelyFromTheOldProbe() {
        val profileId = ProfileId("jccm-registro-generico")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("JCCM — Registro Electrónico / Solicitud Genérica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://registrounicociudadanos.jccm.es")),
            profile.initiatorOrigins,
        )
        assertEquals(
            setOf(
                ExactOrigin.parse("https://sso.jccm.es"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(setOf(SignatureAlgorithm.SHA512_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.XADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.IMPLICIT, operation.mode)
        assertEquals(
            linkedMapOf("format" to "XAdES Detached", "mode" to "implicit"),
            operation.fixedExtraProperties,
        )
        assertTrue(operation.allowedExtraProperties.isEmpty())

        val clientAuth = requireNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, clientAuth.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
            clientAuth.requestOrigins,
        )
        assertEquals(
            setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")),
            clientAuth.sourceUrls,
        )
        assertEquals("/IdP2/AuthenticateCitizen", clientAuth.requestPath)
        assertTrue(clientAuth.fixedQueryParameters.isEmpty())
        assertTrue(clientAuth.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(clientAuth.allowEmptyIssuerList)
        assertEquals(15, clientAuth.grantTtlSeconds)
        assertEquals(443, clientAuth.requestPort)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://sso.jccm.es/cas-jccm-clave/login"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNotEquals(
            profile.profileId,
            ProfileId("jccm-certificate-login-probe"),
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
    fun rejectsAnyExpansionOfTheCanariasCertificateLoginProfileContract() {
        val mutations = listOf(
            "\"SHA1_WITH_RSA\"" to "\"SHA256_WITH_RSA\"",
            "\"format\":\"CAdES Detached\"" to "\"format\":\"CAdES\"",
            "referencesDigestMethod\":\"http://www.w3.org/2001/04/xmlenc#sha512" to
                "referencesDigestMethod\":\"http://www.w3.org/2001/04/xmlenc#sha256",
            "signingCert:true;issuer.rfc2254" to "signingCert:false;issuer.rfc2254",
        )

        mutations.forEach { (expected, replacement) ->
            val canariasStart = BuiltInSiteProfiles.JSON.indexOf("\"profileId\": \"canarias-sede\"")
            assertTrue(canariasStart >= 0)
            val nextProfile = BuiltInSiteProfiles.JSON.indexOf("\"profileId\":", canariasStart + 1)
            val end = if (nextProfile >= 0) nextProfile else BuiltInSiteProfiles.JSON.length
            val block = BuiltInSiteProfiles.JSON.substring(canariasStart, end)
            assertTrue("missing Canarias contract fragment: $expected", block.contains(expected))
            val changedBlock = block.replaceFirst(expected, replacement)
            val mutated = BuiltInSiteProfiles.JSON.substring(0, canariasStart) + changedBlock +
                BuiltInSiteProfiles.JSON.substring(end)
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(mutated)
            }
        }
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
    fun directClientAuthCannotUseRedirectOriginAsSourceOutsideTheReviewedNavarraProfile() {
        val navarraId = "\"profileId\": \"navarra-sede-registro-general\""
        val unreviewedId = "\"profileId\": \"navarra-unreviewed-direct-client-auth\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(navarraId))

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(navarraId, unreviewedId))
        }
    }

    @Test
    fun navarraClientAuthMappingFailsClosedOnUnboundOrAmbiguousParameterNames() {
        val mutations = listOf(
            "\"linkedEphemeralQueryParameterMappings\": {\"ReturnUrl\":\"returnUrl\"}" to
                "\"linkedEphemeralQueryParameterMappings\":{\"ReturnUrl\":\"wrongTarget\"}",
            "\"sourceRequiredEphemeralQueryParameters\": [\"ReturnUrl\"]" to
                "\"sourceRequiredEphemeralQueryParameters\":[\"OtherSource\"]",
            "\"requiredEphemeralQueryParameters\": [\"returnUrl\"]" to
                "\"requiredEphemeralQueryParameters\":[\"OtherTarget\"]",
            "\"linkedEphemeralQueryParameterMappings\": {\"ReturnUrl\":\"returnUrl\"}" to
                "\"linkedEphemeralQueryParameters\":[\"ReturnUrl\"]," +
                    "\"linkedEphemeralQueryParameterMappings\":{\"ReturnUrl\":\"returnUrl\"}",
        )

        mutations.forEach { (expected, replacement) ->
            assertTrue("missing Navarra contract fragment: $expected", BuiltInSiteProfiles.JSON.contains(expected))
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(expected, replacement))
            }
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
    fun rejectsMenorcaClientTlsContractWithoutLinkedUrlParameter() {
        val linkedUrl = "\"linkedEphemeralQueryParameters\":[\"URL\"]"

        assertTrue(BuiltInSiteProfiles.JSON.contains(linkedUrl))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(
                    linkedUrl,
                    "\"linkedEphemeralQueryParameters\":[]",
                ),
            )
        }
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
    fun ourenseClaveClientAuthProfilePinsReviewedSharedOriginsAndRejectsUnknownOwner() {
        val profileId = ProfileId("diputacion-ourense-sede")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI(
                "https://sede.depourense.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&" +
                    "PAGE_CODE=CATALOGO&DETALLE=6269000946476474507610&lang=ES",
            ),
            profile.startUrl,
        )
        assertEquals(setOf(ExactOrigin.parse("https://sede.depourense.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")),
            policy.sourceUrls,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
            policy.requestOrigins,
        )
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace(
                    "\"profileId\": \"diputacion-ourense-sede\"",
                    "\"profileId\": \"unreviewed-clave-owner\"",
                ),
            )
        }
    }

    @Test
    fun diputacionSevillaClaveOwnershipStaysClosedToTheReviewedProfileId() {
        val reviewedId = "\"profileId\": \"diputacion-sevilla-sede\""
        val unreviewedId = "\"profileId\": \"unreviewed-sevilla-clave-owner\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(reviewedId))

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(reviewedId, unreviewedId))
        }
    }

    @Test
    fun diputacionACorunaClaveOwnershipStaysClosedToTheReviewedProfileId() {
        val reviewedId = "\"profileId\": \"diputacion-a-coruna-solicitud-general\""
        val unreviewedId = "\"profileId\": \"unreviewed-coruna-clave-owner\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(reviewedId))

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(reviewedId, unreviewedId))
        }
    }

    @Test
    fun preservesExactAragonSolicitudGeneralClientTlsContract() {
        val profileId = ProfileId("aragon-solicitud-general-client-auth")
        val start = URI("https://aplicaciones.aragon.es/tramitar/solicitud-general/identificacion")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)
        val consume =
            "https://aplicaciones.aragon.es/mfe_core/rest/identification/TTO/" +
                "aHR0cHM6Ly9hcGxpY2FjaW9uZXMuYXJhZ29uLmVzL3RyYW1pdGFyL3NvbGljaXR1ZC1nZW5lcmFsL2lkZW50aWZpY2FjaW9uL2lkZW50aWZpY2Fkbw==/" +
                "SSLOGIN/consumeResponse"

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE, policy.transitionMode)
        assertEquals(setOf(ExactOrigin.parse("https://login1.loginssl.aragon.es")), policy.requestOrigins)
        assertEquals(setOf(URI("https://login.loginssl.aragon.es/sife_login/SSLOGIN")), policy.sourceUrls)
        assertEquals(mapOf("redirect.url" to consume), policy.sourceFixedQueryParameters)
        assertEquals("/sife_login/SSLOGIN/idByCert", policy.requestPath)
        assertEquals(mapOf("redirect.url" to consume), policy.fixedQueryParameters)
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(443, policy.requestPort)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(start))
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, start)?.trustMode,
        )
    }

    @Test
    fun releaseEnablesVerifiedProfilesWhileQaKeepsExperimentalPortalsAvailable() {
        val junta = ProfileId("junta-andalucia")
        val carne = ProfileId("carne-joven-andalucia")
        val education = ProfileId("educacion-convocatoria")
        val ceuta = ProfileId("ceuta-sede")
        val lleida = ProfileId("diputacion-lleida-sede")
        val badajoz = ProfileId("diputacion-badajoz-portal")
        val aragon = ProfileId("aragon-siraw")
        val ofvirtual = ProfileId("junta-ofvirtual")
        val unizar = ProfileId("unizar-tramitador")
        val releaseProfiles = setOf(carne, aragon, ofvirtual, unizar)
        val qaOnly = setOf(
            junta,
            education,
            ProfileId("reg-age-redsara"),
            ProfileId("aeat-mis-datos-censales"),
            ProfileId("dgt-verificacion-equipo"),
            ProfileId("junta-andalucia-vea-peg"),
            ProfileId("mjusticia-fundaciones-idp75"),
            ProfileId("dgoj-public-navigation"),
            ProfileId("diputacion-alava-registro-comun"),
            ceuta,
            ProfileId("aragon-solicitud-general-client-auth"),
            lleida,
            badajoz,
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
        assertEquals(
            CompatibilityStatus.VERIFIED_CONTRACT,
            BuiltInSiteProfiles.qaRegistry.profile(education)?.compatibilityStatus,
        )
        assertEquals(
            CompatibilityStatus.VERIFIED_CONTRACT,
            BuiltInSiteProfiles.qaRegistry.profile(ceuta)?.compatibilityStatus,
        )
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
    fun `OEPM ProtegeO public launch profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("oepm-protegeo-general")
        val start = URI(
            "https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM",
        )
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.oepm.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertEquals(false, profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.oepm.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.oepm.gob.es:444/")))
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

    @Test
    fun `Alava Registro Comun profile exposes only exact QA navigation`() {
        val profileId = ProfileId("diputacion-alava-registro-comun")
        val start = URI("https://egoitza.araba.eus/izapidetu/at/01/es/0000301")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://egoitza.araba.eus")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://egoitza.araba.eus.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://egoitza.araba.eus:444/")))
    }

    @Test
    fun `Castilla Leon QUJU public form profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("castilla-leon-quju-public")
        val start = URI("https://presidencia.jcyl.es/QUJU?O=1")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://presidencia.jcyl.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertEquals(false, profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://presidencia.jcyl.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://presidencia.jcyl.es:444/")))
    }

    @Test
    fun `SEPES Transportes public profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("sepes-transportes-public-complaints")
        val start = URI("https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.transportes.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(profileId, BuiltInSiteProfiles.qaRegistry.resolve(start)?.profile?.profileId)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://sede.transportes.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://sede.transportes.gob.es:444/")))
    }

    @Test
    fun `shared Transportes origin is reviewed only for the exact SEPES pair`() {
        val parsed = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        assertTrue(parsed.profiles.any { it.profileId == ProfileId("transportes-qys-cert-login") })
        assertTrue(parsed.profiles.any { it.profileId == ProfileId("sepes-transportes-public-complaints") })

        val exactId = "\"profileId\": \"sepes-transportes-public-complaints\""
        val unreviewedId = "\"profileId\": \"sepes-transportes-public-complaints-copy\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(exactId))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(exactId, unreviewedId))
        }
    }

    @Test
    fun `BOE public Sede profile is QA only and keeps extranet outside trust`() {
        val profileId = ProfileId("boe-sede-public-home")
        val start = URI("https://www.boe.es/informacion/index.php")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www.boe.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://extranet.boe.es/quejas_el/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.boe.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.boe.es:444/")))
    }

    @Test
    fun `Fondos Europeos public Sede profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("fondos-europeos-sede-public-home")
        val start = URI("https://sedefondoscomunitarios.gob.es/")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sedefondoscomunitarios.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sedefondoscomunitarios.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sedefondoscomunitarios.gob.es:444/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://tramitesfondoseuropeos.hacienda.gob.es/dossier")))
    }

    @Test
    fun `Portal Funciona public home profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("portal-funciona-public-home")
        val start = URI("https://sede.funciona.gob.es/es/home")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.funciona.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertEquals(false, profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://auth-api.redsara.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://autentica.redsara.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.funciona.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.funciona.gob.es:444/")))
    }

    @Test
    fun `DGSFP public Sede profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("dgsfp-sede-public-home")
        val start = URI("https://www.sededgsfp.gob.es/")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www.sededgsfp.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://www.sededgsfp.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://www.sededgsfp.gob.es:444/")))
    }

    @Test
    fun `DGOJ public navigation profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("dgoj-public-navigation")
        val start = URI("https://sede.ordenacionjuego.gob.es/")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.ordenacionjuego.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(4, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ordenacionjuego.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ordenacionjuego.gob.es:444/")))
    }

    @Test
    fun `CNMV public Sede profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("cnmv-sede-public-home")
        val start = URI("https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.cnmv.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(1, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.cnmv.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.cnmv.gob.es:444/")))
    }

    @Test
    fun `Comunidad Madrid Registro General profile exposes only exact public QA navigation`() {
        val profileId = ProfileId("comunidad-madrid-registro-general")
        val start = URI("https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidor.icm")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://gestiona.comunidad.madrid")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://gestiona.comunidad.madrid.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://gestiona.comunidad.madrid:444/")))
    }

    @Test
    fun `Junta VEA PEG profile exposes only exact public QA navigation`() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val start = URI("https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://veaja.cloud.juntadeandalucia.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
    }

    @Test
    fun `El Hierro Solicitud general is QA navigation only with observed Clave handoff`() {
        val profileId = ProfileId("el-hierro-solicitud-general")
        val start = URI("https://elhierro.sedelectronica.es/catalog/tw/7944e884-3b98-48fc-abcd-d6db6ef8bd71")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://elhierro.sedelectronica.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(5, profile.evidence.size)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
    }

    @Test
    fun `Diputacion Avila Instancia General is QA navigation only with observed Clave redirects`() {
        val profileId = ProfileId("diputacion-avila-instancia-general")
        val start = URI("https://diputacionavila.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://diputacionavila.sedelectronica.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
                ExactOrigin.parse("https://pasarela-ident.clave.gob.es"),
                ExactOrigin.parse("https://pasarela-ident-sistemas.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertEquals(false, profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
    }

    @Test
    fun `Diputacion Cadiz solicitud generica is QA navigation only to the observed Clave boundary`() {
        val profileId = ProfileId("diputacion-cadiz-solicitud-generica")
        val start = URI("https://sede.dipucadiz.es/group/sede/detalle-tramite?tramite=761")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Diputación Provincial de Cádiz — Solicitud, escrito o comunicación genérica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.dipucadiz.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://sso.dipucadiz.es"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(4, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-21" })
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.dipucadiz.es.evil.example/")))
    }

    @Test
    fun `Diputacion Caceres Instancia General is QA navigation only to the observed Clave boundary`() {
        val profileId = ProfileId("diputacion-caceres-instancia-general")
        val start = URI("https://sede.dip-caceres.es/carpetaCiudadano/fichaprocedimiento.do?idproc=341")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Diputación Provincial de Cáceres — Instancia General Normalizada", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.dip-caceres.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(5, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-21" })
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.dip-caceres.es.evil.example/")))
    }


    @Test
    fun `MJusticia fundaciones launch is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("mjusticia-fundaciones-idp75")
        val start = URI("https://sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede2.mjusticia.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-19" })

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede2.mjusticia.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede2.mjusticia.gob.es:444/")))
    }

    @Test
    fun `CTBG Solicitud de Informacion is QA navigation only to observed Clave boundary`() {
        val profileId = ProfileId("ctbg-solicitud-informacion")
        val start = URI("https://sede.consejodetransparencia.gob.es/catalog/tw/01b4b72b-7f21-4d7c-9576-e1d7871624a6")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Consejo de Transparencia y Buen Gobierno (CTBG) — Solicitud de Información", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.consejodetransparencia.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(4, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-23" })
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.consejodetransparencia.gob.es.evil.example/")))
    }

    @Test
    fun `Catastro generic submissions is QA navigation only to observed Clave boundary`() {
        val profileId = ProfileId("catastro-solicitudes-genericas")
        val start = URI("https://www.sedecatastro.gob.es/Accesos/SECAccProcedimientos.aspx?Dest=22")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Dirección General del Catastro — Otras solicitudes y escritos genéricos", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www.sedecatastro.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(6, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-24" })
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://pasarela.clave.gob.es/Proxy2/ResponseRedirect"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.sedecatastro.gob.es.evil.example/")))
    }

    @Test
    fun `Asturias Sede may share only the reviewed miPrincipado redirect with the existing Asturias profile`() {
        val existing = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("asturias-miprincipado")
        }
        val navigation = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("asturias-sede-tramite-navigation")
        }
        val shared = ExactOrigin.parse("https://miprincipado.asturias.es")

        assertTrue(shared in existing.initiatorOrigins)
        assertTrue(shared in navigation.redirectOrigins)
        assertTrue(shared !in navigation.initiatorOrigins)
        assertTrue(navigation.capabilities.isEmpty())
        assertNull(navigation.clientAuthPolicy)
    }

    @Test
    fun `Asturias Sede shared origin exception fails closed for another origin or owner`() {
        val reviewedOrigins =
            "\"initiatorOrigins\": [\"https://sede.asturias.es\"],\n" +
                "      \"redirectOrigins\": [\"https://miprincipado.asturias.es\"]"
        val unreviewedOrigins =
            "\"initiatorOrigins\": [\"https://sede.asturias.es\"],\n" +
                "      \"redirectOrigins\": [\"https://tramita.asturias.es\"]"
        val reviewedOwner = "\"profileId\": \"asturias-sede-tramite-navigation\""
        val unreviewedOwner = "\"profileId\": \"asturias-sede-unreviewed-navigation\""

        assertTrue(BuiltInSiteProfiles.JSON.contains(reviewedOrigins))
        assertTrue(BuiltInSiteProfiles.JSON.contains(reviewedOwner))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(reviewedOrigins, unreviewedOrigins),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(reviewedOwner, unreviewedOwner),
            )
        }
    }

    @Test
    fun `Asturias Sede profile exposes only current redirect navigation in QA`() {
        val profileId = ProfileId("asturias-sede-tramite-navigation")
        val start = URI("https://sede.asturias.es/ast/-/dboid-6269000011903512107573")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.asturias.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://miprincipado.asturias.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
    }

    @Test
    fun `FEGA Solicitud al FEGA is QA navigation only to observed Clave boundary`() {
        val profileId = ProfileId("fega-solicitud-general-ofvsg02")
        val start = URI("https://www3.sede.fega.gob.es/ConRegExt/regmantenimientos/inicioAsientos.action?tramite=OFVSG02")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("FEGA — Solicitud al FEGA", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www3.sede.fega.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(6, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-24" })
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"))?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www3.sede.fega.gob.es.evil.example/")))
    }


    @Test
    fun `Diputacion Cordoba Solicitud Generica is QA navigation only to the public form boundary`() {
        val profileId = ProfileId("diputacion-cordoba-solicitud-generica")
        val start = URI("https://sede.dipucordoba.es/diputacion/tramites/procedimiento/8876/solicitud-generica")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Diputación Provincial de Córdoba — Solicitud Genérica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.dipucordoba.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-21" })
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.dipucordoba.es.evil.example/")))
    }

    @Test
    fun `Gipuzkoa Registro profile is QA public navigation and keeps Izenpe auth fail closed`() {
        val profileId = ProfileId("diputacion-gipuzkoa-registro-public")
        val start = URI(
            "https://egoitza.gipuzkoa.eus/WAS/CORP/WATTramiteakWEB/inicio.do?idioma=C&app=00001",
        )
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://egoitza.gipuzkoa.eus")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-19" })

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://eidas.izenpe.com/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://eidas2.izenpe.com/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://egoitza.gipuzkoa.eus.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://egoitza.gipuzkoa.eus:444/")))
    }

    @Test
    fun `Segovia Registro profile stays bounded to public Registro and Clave navigation`() {
        val profileId = ProfileId("diputacion-segovia-registro")
        val start = URI("https://sede.dipsegovia.es/registro")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Diputación de Segovia — Registro electrónico", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.dipsegovia.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(5, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-21" })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.dipsegovia.es.evil.example/registro")))
    }

    @Test
    fun `Madrid Cuenta Digital 53F1 profile exposes only exact QA browse navigation`() {
        val profileId = ProfileId("comunidad-madrid-cuenta-digital-53f1")
        val start = URI("https://digital.comunidad.madrid/ext/53F1")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://digital.comunidad.madrid")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://gestiona.comunidad.madrid")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertEquals(null, profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertEquals(false, profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-19" })
    }
}

private fun URI.originForTest() = ExactOrigin.parse("https://$host")

private fun SiteProfile.navigationOriginsForTest() =
    initiatorOrigins + redirectOrigins + trustedBrowseOrigins +
        (clientAuthPolicy?.requestOrigins ?: emptySet())
