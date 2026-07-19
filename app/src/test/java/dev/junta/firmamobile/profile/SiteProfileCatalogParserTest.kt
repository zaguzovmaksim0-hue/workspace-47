package dev.junta.firmamobile.profile

import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
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
    fun bundledResourceMatchesTheCompiledCatalog() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resource = context.resources.openRawResource(R.raw.site_profiles_v1)
            .bufferedReader().use { it.readText() }

        assertEquals(BuiltInSiteProfiles.catalog, SiteProfileCatalogParser.parse(resource))
    }

    @Test
    fun preservesTheJuntaGoldenContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("junta-andalucia")
        }
        assertEquals(ProfileId("junta-andalucia"), profile.profileId)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
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
    fun preservesTheExactUnizarAuthenticationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("unizar-tramitador")
        }
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
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
    fun preservesTheExactCarneJovenClientTlsContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("carne-joven-andalucia")
        }
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(
            setOf(ExactOrigin.parse("https://ws235.juntadeandalucia.es")),
            policy.requestOrigins,
        )
        assertEquals(
            setOf(URI("https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet")),
            policy.sourceUrls,
        )
        assertEquals("/authenticationFacade", policy.requestPath)
        assertEquals(setOf("ticketId", "webSessionId"), policy.requiredEphemeralQueryParameters)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
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
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.releaseRegistry.resolve(
                URI("https://ws235.juntadeandalucia.es/authenticationFacade"),
            ),
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
    fun releaseEnablesOnlyVerifiedJuntaWhileQaKeepsExperimentalPortalsAvailable() {
        val junta = ProfileId("junta-andalucia")
        val qaOnly = setOf(
            ProfileId("reg-age-redsara"),
            ProfileId("unizar-tramitador"),
            ProfileId("carne-joven-andalucia"),
        )

        assertEquals(setOf(junta), BuiltInSiteProfiles.catalog.profiles
            .mapNotNull { profile ->
                profile.profileId.takeIf { BuiltInSiteProfiles.releaseRegistry.profile(it) != null }
            }
            .toSet())
        qaOnly.forEach { profileId ->
            assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
            assertTrue(BuiltInSiteProfiles.qaRegistry.profile(profileId) != null)
        }
        assertEquals(
            CompatibilityStatus.VERIFIED_E2E,
            BuiltInSiteProfiles.releaseRegistry.profile(junta)?.compatibilityStatus,
        )
    }

    @Test
    fun releaseRejectsSensitiveEnabledProfileWithoutVerifiedE2eEvidence() {
        val downgradedCatalog = SiteProfileCatalogParser.parse(
            BuiltInSiteProfiles.JSON.replaceFirst(
                "\"compatibilityStatus\": \"VERIFIED_E2E\"",
                "\"compatibilityStatus\": \"VERIFIED_CONTRACT\"",
            ),
        )
        val release = SiteProfileRegistry(downgradedCatalog, BuildTrustPolicy.RELEASE)
        val qa = SiteProfileRegistry(downgradedCatalog, BuildTrustPolicy.QA)
        val junta = ProfileId("junta-andalucia")

        assertNull(release.profile(junta))
        assertTrue(qa.profile(junta) != null)
    }

    @Test
    fun rejectsDuplicateUnknownAndUnsupportedSchemaKeys() {
        val json = BuiltInSiteProfiles.JSON
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(json.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(json.replaceFirst("\"catalogVersion\": 4", "\"unknown\": true, \"catalogVersion\": 4"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(json.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
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
