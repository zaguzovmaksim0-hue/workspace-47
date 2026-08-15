package dev.junta.firmamobile.profile

import dev.junta.firmamobile.browser.VeaMultiModeBridgeAdapter
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.PrecalculatedHashAlgorithm
import dev.junta.firmamobile.signing.SigningProtocolId
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class JuntaSedeProfileCatalogBindingTest {
    private val profileId = ProfileId("junta-andalucia-sede")
    private val startUrl = URI("https://veaja.cloud.juntadeandalucia.es/inicio/")

    @Test
    fun qaProfilePreservesExactJuntaSedeContractAndReleaseStaysDisabled() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Sede electrónica de la Junta de Andalucía", profile.displayName)
        assertEquals(CompatibilityStatus.EXPERIMENTAL, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://veaja.cloud.juntadeandalucia.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-15" })

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("vea-multimode-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("vea-multimode-callback-v1"), operation.callbackContractId)
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), operation.capabilities)
        assertEquals(
            setOf(
                SignatureAlgorithm.SHA1_WITH_RSA,
                SignatureAlgorithm.SHA256_WITH_RSA,
                SignatureAlgorithm.SHA512_WITH_RSA,
            ),
            operation.algorithms,
        )
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertNull(operation.endpointId)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://evil.veaja.cloud.juntadeandalucia.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://veaja.cloud.juntadeandalucia.es:444/")))
    }

    @Test
    fun protocolRegistryBindsOnlyTheJuntaSedeSignContract() {
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN)

        assertNotNull(binding)
        assertEquals(ProtocolInputAdapterId("vea-multimode-autoscript-v1"), binding?.inputAdapterId)
        assertEquals(CallbackContractId("vea-multimode-callback-v1"), binding?.callbackContractId)
        assertEquals(SigningProtocolId("vea-multimode-autoscript-v1"), binding?.signingProtocolId)
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))
    }

    @Test
    fun publicCatalogBindsJuntaSedePendingWithoutE2ePromotion() {
        val portalId = PortalId("junta-andalucia-sede")
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0093" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("2026-08-15", entry.reviewedOn.toString())
        assertTrue(entry.limitations.contains("E2E", ignoreCase = true))

        val qa = PortalCatalogRepository(
            registry = BuiltInSiteProfiles.qaRegistry,
            profileCatalog = BuiltInSiteProfiles.catalog,
            publicCatalog = publicCatalog,
        )
        val qaItem = qa.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaItem.supportStatus)
        assertTrue(qaItem.isEnabled)
        assertEquals(
            PortalLaunchTarget(profileId = profileId, entryUrl = startUrl),
            qa.resolveLaunch(qaItem),
        )

        val release = PortalCatalogRepository(
            registry = BuiltInSiteProfiles.releaseRegistry,
            profileCatalog = BuiltInSiteProfiles.catalog,
            publicCatalog = publicCatalog,
        )
        val releaseItem = release.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.BROWSE_ONLY, releaseItem.supportStatus)
        assertFalse(releaseItem.isEnabled)
        assertNull(release.resolveLaunch(releaseItem))
    }

    @Test
    fun runtimeMultiModeSupportedFormatsAndAlgorithmsMatchProfileOperationPolicyExactly() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)

        val bridgeFormats = VeaMultiModeBridgeAdapter.SUPPORTED_FORMATS.map { SignatureFormat.valueOf(it) }.toSet()
        assertEquals(setOf(operation.format), bridgeFormats)

        val bridgeAlgorithms = VeaMultiModeBridgeAdapter.ALGORITHM_HASH_MAP.keys.map { key ->
            when (key) {
                "SHA1WITHRSA" -> SignatureAlgorithm.SHA1_WITH_RSA
                "SHA256WITHRSA" -> SignatureAlgorithm.SHA256_WITH_RSA
                "SHA512WITHRSA" -> SignatureAlgorithm.SHA512_WITH_RSA
                else -> error("Unknown algorithm: $key")
            }
        }.toSet()
        assertEquals(operation.algorithms, bridgeAlgorithms)

        val hashEnums = PrecalculatedHashAlgorithm.entries.toSet()
        val expectedHashes = setOf(
            PrecalculatedHashAlgorithm.SHA1,
            PrecalculatedHashAlgorithm.SHA256,
            PrecalculatedHashAlgorithm.SHA512,
        )
        assertEquals(expectedHashes, hashEnums)
    }
}
