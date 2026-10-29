package dev.junta.firmamobile.profile

import dev.junta.firmamobile.browser.AfirmaShimCompatibilityFlags
import dev.junta.firmamobile.browser.WebMessageBridge
import dev.junta.firmamobile.browser.MelillaBatchBridgeAdapter
import dev.junta.firmamobile.browser.ExtremaduraBatchBridgeAdapter
import dev.junta.firmamobile.browser.LaPalmaBatchBridgeAdapter
import dev.junta.firmamobile.browser.HuescaBatchBridgeAdapter
import dev.junta.firmamobile.browser.BurgosBatchBridgeAdapter
import dev.junta.firmamobile.browser.LugoBatchBridgeAdapter
import dev.junta.firmamobile.browser.CaibBatchBridgeAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShimFeatureCatalogResolverTest {
    private val legacyResolver by lazy { ShimFeatureResolver(BuiltInSiteProfiles.catalog) }
    private val expected = mapOf(
        "ugr-certificado-login" to ShimFeature.UGR_LOGIN,
        "cantabria-rec-cert-login" to ShimFeature.CANTABRIA_LOGIN,
        "jccm-certificate-login-probe" to ShimFeature.JCCM_PROBE,
        "jccm-registro-generico" to ShimFeature.JCCM_REGISTRO,
        "sevilla-atse-certificate-login" to ShimFeature.SEVILLA_ATSE,
        "airef-instancia-general" to ShimFeature.AIREF_SIGN,
        "cdti-certificate-validation" to ShimFeature.CDTI_SIGN,
        "policia-solicitud-generica" to ShimFeature.POLICIA_SIGN,
        "gran-canaria-sede-electronica" to ShimFeature.GRAN_CANARIA_SIGN,
        "fuerteventura-sede-electronica" to ShimFeature.FUERTEVENTURA_SIGN,
        "canarias-sede" to ShimFeature.CANARIAS_LOGIN,
        "ministerio-economia-instancia-generica" to ShimFeature.MINECO_SIGN,
        "age-acceda" to ShimFeature.ACCEDA_SIGN,
        "diputacion-badajoz-portal" to ShimFeature.BADAJOZ_SIGN,
        "isciii-certificate-selection" to ShimFeature.ISCIII_SELECTION,
        "diputacion-valencia-sede" to ShimFeature.VALENCIA_SELECTION,
        "xunta-galicia-solicitude-xenerica" to ShimFeature.XUNTA_SIGN_SELECTION,
        "euskadi-sede-electronica" to ShimFeature.EUSKADI_CLIENT_AUTH_POST,
        "melilla-sede" to ShimFeature.STA_JSON_BATCH,
        "extremadura-tramites" to ShimFeature.STA_JSON_BATCH,
        "la-palma-sede-electronica" to ShimFeature.STA_JSON_BATCH,
        "diputacion-huesca-portal" to ShimFeature.STA_JSON_BATCH,
        "diputacion-burgos-portal" to ShimFeature.STA_JSON_BATCH,
        "diputacion-lugo-sede" to ShimFeature.LUGO_XML_BATCH,
        "caib-portafib" to ShimFeature.CAIB_PORTAFIB_BATCH,
    )
    private val batchOrigins = mapOf(
        "melilla-sede" to MelillaBatchBridgeAdapter.SOURCE_ORIGIN,
        "extremadura-tramites" to ExtremaduraBatchBridgeAdapter.SOURCE_ORIGIN,
        "la-palma-sede-electronica" to LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN,
        "diputacion-huesca-portal" to HuescaBatchBridgeAdapter.SOURCE_ORIGIN,
        "diputacion-burgos-portal" to BurgosBatchBridgeAdapter.SOURCE_ORIGIN,
        "diputacion-lugo-sede" to LugoBatchBridgeAdapter.SOURCE_ORIGIN,
        "caib-portafib" to CaibBatchBridgeAdapter.SOURCE_ORIGIN,
    )

    @Test
    fun closedInventoryMatchesAllTwentyOneLegacyFlags() {
        assertEquals(21, ShimFeature.entries.size)
        assertEquals(21, ShimFeature.entries.map { it.moduleId }.toSet().size)
        assertEquals(ShimFeature.entries.toSet(), expected.values.toSet())
        ShimFeature.entries.forEach { assertEquals(it, ShimFeature.fromModuleId(it.moduleId)) }
        assertThrows(IllegalArgumentException::class.java) { ShimFeature.fromModuleId("UGR_LOGIN") }
    }

    @Test
    fun everyPackagedV1ProfileMatchesCurrentBridgeFlagsForBothBuildPoliciesAndBatchGates() {
        val catalog = BuiltInSiteProfiles.catalog
        assertEquals(1, catalog.schemaVersion)
        assertTrue(catalog.profiles.all { it.shimFeatures == null })
        for (build in BuildTrustPolicy.entries) {
            val registry = SiteProfileRegistry(catalog, build)
            for (profile in catalog.profiles) {
                val id = profile.profileId
                val active = registry.profile(id) != null
                for (gate in listOf(false, true)) {
                    val enabled = gate && active && id.value in batchOrigins
                    val flags = WebMessageBridge.shimCompatibilityFlags(id, active, enabled)
                    assertEquals("$build / $id / $gate", legacyFeatures(flags), legacyResolver.resolve(
                        registry, id, enabled, batchOrigins[id.value],
                    ))
                }
            }
        }
    }

    @Test
    fun allSevenStaProfilesAreEnumeratedAndUseJsonFallbackExceptLugoAndCaib() {
        val adapters = setOf(
            "melilla-batch-autoscript-v1", "extremadura-batch-autoscript-v1",
            "la-palma-batch-autoscript-v1", "huesca-batch-autoscript-v1", "burgos-batch-autoscript-v1",
            "lugo-clientsigner-xml-batch-v1", "caib-portafib-batch-v1",
        )
        val catalog = BuiltInSiteProfiles.catalog
        val actual = catalog.profiles.filter { profile ->
            profile.operationPolicies.values.any { it.inputAdapterId.value in adapters }
        }.map { it.profileId.value }.toSet()
        assertEquals(batchOrigins.keys, actual)
        assertEquals(7, actual.size)
        val registry = SiteProfileRegistry(catalog, BuildTrustPolicy.QA)
        for ((id, origin) in batchOrigins) {
            assertEquals(setOf(expected.getValue(id)), legacyResolver.resolve(registry, ProfileId(id), true, origin))
            assertTrue(legacyResolver.resolve(registry, ProfileId(id), false, origin).isEmpty())
        }
        assertEquals(5, batchOrigins.keys.count { expected[it] == ShimFeature.STA_JSON_BATCH })
    }

    @Test
    fun inactiveAbsentAndQaFilteredProfilesNeverSelectFeaturesEvenWithBatchGateOn() {
        val disabled = BuiltInSiteProfiles.catalog.copy(profiles = BuiltInSiteProfiles.catalog.profiles.map {
            it.copy(activation = ProfileActivation.DISABLED)
        })
        val registry = SiteProfileRegistry(disabled, BuildTrustPolicy.QA)
        for (profile in disabled.profiles) {
            assertTrue(legacyResolver.resolve(registry, profile.profileId, true, batchOrigins[profile.profileId.value]).isEmpty())
        }
        assertTrue(legacyResolver.resolve(registry, ProfileId("missing-profile"), true, "https://example.org").isEmpty())
        val release = BuiltInSiteProfiles.releaseRegistry
        for (id in expected.keys) {
            assertNull(release.profile(ProfileId(id)))
            assertTrue(legacyResolver.resolve(release, ProfileId(id), true, batchOrigins[id]).isEmpty())
        }
    }

    @Test
    fun v2EmptyIsExplicitNoFeaturesAndPreservesAllV1ProfileData() {
        val catalog = SiteProfileCatalogParser.parse(v2Json(false))
        assertEquals(BuiltInSiteProfiles.catalog.profiles, catalog.profiles.map { it.copy(shimFeatures = null) })
        val resolver = ShimFeatureResolver(catalog)
        for (profile in catalog.profiles) {
            assertEquals(emptySet<ShimFeature>(), profile.shimFeatures)
            assertTrue(resolver.resolve(BuiltInSiteProfiles.qaRegistry, profile.profileId, true,
                batchOrigins[profile.profileId.value]).isEmpty())
        }
        assertEquals(BuiltInSiteProfiles.catalog, SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON))
    }

    @Test
    fun reviewedV2SelectionsMatchV1AndDoNotWidenBuildFiltering() {
        val catalog = SiteProfileCatalogParser.parse(v2Json(true))
        val resolver = ShimFeatureResolver(catalog)
        for (build in BuildTrustPolicy.entries) {
            val registry = SiteProfileRegistry(BuiltInSiteProfiles.catalog, build)
            for (profile in catalog.profiles) {
                val id = profile.profileId
                for (gate in listOf(false, true)) {
                    assertEquals(legacyResolver.resolve(registry, id, gate, batchOrigins[id.value]),
                        resolver.resolve(registry, id, gate, batchOrigins[id.value]))
                }
            }
        }
    }

    @Test
    fun knownFeatureOnEveryForeignProfileFailsClosed() {
        for (profile in BuiltInSiteProfiles.catalog.profiles) {
            for (feature in ShimFeature.entries) {
                val candidate = profile.copy(shimFeatures = setOf(feature))
                if (expected[profile.profileId.value] == feature) {
                    ShimFeatureResolver.validate(candidate)
                } else {
                    assertThrows("${profile.profileId} / $feature", IllegalArgumentException::class.java) {
                        ShimFeatureResolver.validate(candidate)
                    }
                }
            }
        }
    }

    @Test
    fun everyMultiFeatureCombinationIncludingAllBatchFamilyConflictsIsRejected() {
        for (profile in BuiltInSiteProfiles.catalog.profiles.filter { it.profileId.value in expected }) {
            for (first in ShimFeature.entries) for (second in ShimFeature.entries.filter { it != first }) {
                assertThrows(IllegalArgumentException::class.java) {
                    ShimFeatureResolver.validate(profile.copy(shimFeatures = setOf(first, second)))
                }
            }
        }
    }

    @Test
    fun operationAdapterAndCallbackMismatchCannotAuthorizeFeatures() {
        for (profile in BuiltInSiteProfiles.catalog.profiles.filter { it.profileId.value in expected }) {
            val configured = profile.copy(shimFeatures = setOf(expected.getValue(profile.profileId.value)))
            if (configured.operationPolicies.isEmpty()) {
                assertThrows(IllegalArgumentException::class.java) {
                    ShimFeatureResolver.validate(configured.copy(clientAuthPolicy = null))
                }
                continue
            }
            assertThrows(IllegalArgumentException::class.java) {
                ShimFeatureResolver.validate(configured.copy(operationPolicies = emptyMap()))
            }
            val policies = configured.operationPolicies
            val key = policies.keys.first()
            for (operation in listOf(
                policies.getValue(key).copy(inputAdapterId = ProtocolInputAdapterId("unreviewed-adapter")),
                policies.getValue(key).copy(callbackContractId = CallbackContractId("unreviewed-callback")),
            )) {
                assertThrows(IllegalArgumentException::class.java) {
                    ShimFeatureResolver.validate(configured.copy(operationPolicies = policies + (key to operation)))
                }
            }
        }
    }

    @Test
    fun staRuntimeOriginIsRequiredAndCannotBorrowAnotherProfilesOriginInEitherSchema() {
        for (catalog in listOf(BuiltInSiteProfiles.catalog, SiteProfileCatalogParser.parse(v2Json(true)))) {
            val resolver = ShimFeatureResolver(catalog)
            for ((id, origin) in batchOrigins) {
                val invalid = listOf(null, "", "$origin/", "$origin/path", "$origin?query", "$origin#fragment",
                    origin.replace("https://", "http://"), "https://example.org") + batchOrigins.values.filter { it != origin }
                for (source in invalid) {
                    assertThrows(IllegalArgumentException::class.java) {
                        resolver.resolve(BuiltInSiteProfiles.qaRegistry, ProfileId(id), true, source)
                    }
                }
            }
        }
    }

    @Test
    fun verifiedOnlyRuntimeRegistryRemainsAuthoritative() {
        RuntimeProfileScope.resetForTest()
        try {
            RuntimeProfileScope.configure(true)
            val registry = BuiltInSiteProfiles.runtimeRegistry
            assertTrue(registry === BuiltInSiteProfiles.releaseRegistry)
            for (id in expected.keys) assertTrue(legacyResolver.resolve(registry, ProfileId(id)).isEmpty())
        } finally {
            RuntimeProfileScope.resetForTest()
        }
    }

    @Test
    fun resolverRejectsMissingV2FeaturesAndV1FeatureSmuggling() {
        assertThrows(IllegalArgumentException::class.java) {
            ShimFeatureResolver(BuiltInSiteProfiles.catalog.copy(schemaVersion = 2))
        }
        val catalog = SiteProfileCatalogParser.parse(v2Json(false))
        assertThrows(IllegalArgumentException::class.java) {
            ShimFeatureResolver(catalog.copy(schemaVersion = 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA)
        }
    }

    @Test
    fun v2CatalogCannotOverrideActiveRegistryContract() {
        val catalog = SiteProfileCatalogParser.parse(v2Json(true))
        val profile = catalog.profiles.single { it.profileId == ProfileId("ugr-certificado-login") }
        val mutations = listOf(
            profile.copy(startUrl = java.net.URI("https://example.org/")),
            profile.copy(initiatorOrigins = setOf(ExactOrigin.parse("https://example.org"))),
            profile.copy(capabilities = emptySet()),
            profile.copy(activation = ProfileActivation.ENABLED),
        )
        for (mutated in mutations) {
            val changed = catalog.copy(profiles = catalog.profiles.map {
                if (it.profileId == profile.profileId) mutated else it
            })
            val resolver = ShimFeatureResolver(changed)
            assertThrows(IllegalArgumentException::class.java) {
                resolver.resolve(BuiltInSiteProfiles.qaRegistry, profile.profileId)
            }
        }
        val missing = catalog.copy(profiles = catalog.profiles.filterNot { it.profileId == profile.profileId })
        assertTrue(ShimFeatureResolver(missing).resolve(BuiltInSiteProfiles.qaRegistry, profile.profileId).isEmpty())
    }

    private fun v2Json(configured: Boolean): String {
        val original = BuiltInSiteProfiles.JSON
        val schemaV1 = Regex("\"schemaVersion\"\\s*:\\s*1")
        check(schemaV1.containsMatchIn(original))
        val json = schemaV1.replaceFirst(original, "\"schemaVersion\":2")
        val profileKey = Regex("\"profileId\"\\s*:\\s*\"([a-z0-9-]+)\"")
        check(profileKey.findAll(json).count() == BuiltInSiteProfiles.catalog.profiles.size)
        return profileKey.replace(json) { match ->
            val module = expected[match.groupValues[1]]?.takeIf { configured }?.moduleId
            val value = module?.let { "\"$it\"" }.orEmpty()
            "${match.value},\"shimFeatures\":[$value]"
        }
    }

    @Test
    fun resolverFeaturesProduceIdenticalLegacyFlagsAndScriptsForEveryProfile() {
        for (build in BuildTrustPolicy.entries) {
            val registry = SiteProfileRegistry(BuiltInSiteProfiles.catalog, build)
            for (profile in BuiltInSiteProfiles.catalog.profiles) {
                val id = profile.profileId
                val active = registry.profile(id) != null
                for (gate in listOf(false, true)) {
                    val enabled = gate && active && id.value in batchOrigins
                    val oldFlags = WebMessageBridge.shimCompatibilityFlags(id, active, enabled)
                    val newFlags = AfirmaShimCompatibilityFlags.fromFeatures(
                        legacyResolver.resolve(registry, id, enabled, batchOrigins[id.value]),
                    )
                    assertEquals("$build / $id / $gate", oldFlags, newFlags)
                    assertSameLegacyFlags(oldFlags, newFlags)

                }
            }
        }
    }

    private fun assertSameLegacyFlags(expected: AfirmaShimCompatibilityFlags, actual: AfirmaShimCompatibilityFlags) {
        assertEquals(expected.ugr, actual.ugr)
        assertEquals(expected.cantabria, actual.cantabria)
        assertEquals(expected.jccm, actual.jccm)
        assertEquals(expected.jccmRegistro, actual.jccmRegistro)
        assertEquals(expected.sevillaAtse, actual.sevillaAtse)
        assertEquals(expected.airef, actual.airef)
        assertEquals(expected.cdti, actual.cdti)
        assertEquals(expected.policia, actual.policia)
        assertEquals(expected.granCanaria, actual.granCanaria)
        assertEquals(expected.fuerteventura, actual.fuerteventura)
        assertEquals(expected.canarias, actual.canarias)
        assertEquals(expected.mineco, actual.mineco)
        assertEquals(expected.melillaBatch, actual.melillaBatch)
        assertEquals(expected.lugoBatch, actual.lugoBatch)
        assertEquals(expected.caibBatch, actual.caibBatch)
        assertEquals(expected.isciiiCertificateSelection, actual.isciiiCertificateSelection)
        assertEquals(expected.valenciaCertificateSelection, actual.valenciaCertificateSelection)
        assertEquals(expected.xuntaGalicia, actual.xuntaGalicia)
        assertEquals(expected.euskadiClientAuthPost, actual.euskadiClientAuthPost)
        assertEquals(expected.acceda, actual.acceda)
        assertEquals(expected.badajoz, actual.badajoz)
    }

    private fun legacyFeatures(flags: AfirmaShimCompatibilityFlags): Set<ShimFeature> = listOf(
            ShimFeature.UGR_LOGIN to flags.ugr,
            ShimFeature.CANTABRIA_LOGIN to flags.cantabria,
            ShimFeature.JCCM_PROBE to flags.jccm,
            ShimFeature.JCCM_REGISTRO to flags.jccmRegistro,
            ShimFeature.SEVILLA_ATSE to flags.sevillaAtse,
            ShimFeature.AIREF_SIGN to flags.airef,
            ShimFeature.CDTI_SIGN to flags.cdti,
            ShimFeature.POLICIA_SIGN to flags.policia,
            ShimFeature.GRAN_CANARIA_SIGN to flags.granCanaria,
            ShimFeature.FUERTEVENTURA_SIGN to flags.fuerteventura,
            ShimFeature.CANARIAS_LOGIN to flags.canarias,
            ShimFeature.MINECO_SIGN to flags.mineco,
            ShimFeature.ACCEDA_SIGN to flags.acceda,
            ShimFeature.BADAJOZ_SIGN to flags.badajoz,
            ShimFeature.ISCIII_SELECTION to flags.isciiiCertificateSelection,
            ShimFeature.VALENCIA_SELECTION to flags.valenciaCertificateSelection,
            ShimFeature.XUNTA_SIGN_SELECTION to flags.xuntaGalicia,
            ShimFeature.EUSKADI_CLIENT_AUTH_POST to flags.euskadiClientAuthPost,
            ShimFeature.STA_JSON_BATCH to flags.melillaBatch,
            ShimFeature.LUGO_XML_BATCH to flags.lugoBatch,
            ShimFeature.CAIB_PORTAFIB_BATCH to flags.caibBatch,
    ).filter { it.second }.mapTo(linkedSetOf()) { it.first }
}
