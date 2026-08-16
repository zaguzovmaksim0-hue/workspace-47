package dev.junta.firmamobile.profile

import android.net.Uri
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalMechanism
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.signing.AccedaPadesAdapter
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AccedaProfileCatalogBindingTest {
    private val profileId = ProfileId(AccedaPadesAdapter.PROFILE_ID)
    private val portalId = PortalId("age-acceda")
    private val startUrl = URI("https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES")

    @Test
    fun qaProfilePreservesExactAccedaSignerContractAndReleaseStaysDisabled() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Plataforma ACCEDA — Sede electrónica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.administracionespublicas.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.isNotEmpty())

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.PADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertEquals(
            mapOf(
                "format" to "PAdES Detached",
                "expPolicy" to "FirmaAGE",
                "nonexpired" to "true",
            ),
            operation.fixedExtraProperties,
        )
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://evil.sede.administracionespublicas.gob.es/")))
    }

    @Test
    fun securityPolicyRejectsNonMatchingSeedsAndBindsProtocolInQa() {
        listOf(
            "http://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES",
            "https://user@sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES",
            "https://sede.administracionespublicas.gob.es:8443/certificado/info/idp/82/ida/0/language/es_ES",
            "https://evil.sede.administracionespublicas.gob.es/",
            "https://sede.administracionespublicas.gob.es.evil.example/",
        ).forEach { rejected ->
            assertNull(rejected, BuiltInSiteProfiles.releaseRegistry.resolve(URI(rejected)))
            assertNull(rejected, BuiltInSiteProfiles.qaRegistry.resolve(URI(rejected)))
        }
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://sede.administracionespublicas.gob.es/certificado/valida"),
            )?.trustMode,
        )

        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN)
        assertEquals(AccedaPadesAdapter.ID, binding?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), binding?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), binding?.callbackContractId)
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))

        assertEquals(
            setOf("https://sede.administracionespublicas.gob.es"),
            JuntaOriginPolicy.webMessageOriginRules(profileId),
        )
        assertEquals(
            "https://sede.administracionespublicas.gob.es",
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES"),
                profileId,
            )?.serialized,
        )
        assertEquals(
            setOf("sede.administracionespublicas.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(profileId),
        )
    }

    @Test
    fun publicCatalogBindsTheExactImplementedEntry() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.portalId == portalId }
        assertEquals(profileId, entry.profileId)
        assertEquals("ES-PUB-0003", entry.inventoryId)
        assertEquals(startUrl, entry.entryUrl)
        assertNull(entry.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("2026-07-15", entry.reviewedOn.toString())
        assertEquals(
            setOf(
                PortalMechanism.AUTOSCRIPT,
                PortalMechanism.CERTIFICATE_ACCESS,
                PortalMechanism.ELECTRONIC_SIGNATURE,
            ),
            entry.observedMechanisms,
        )
        assertEquals(
            setOf(SignatureFormat.PADES, SignatureFormat.XADES),
            entry.observedSignatureFormats,
        )
        assertTrue(entry.limitations.contains("E2E", ignoreCase = true))

        val qa = PortalCatalogRepository(
            BuiltInSiteProfiles.qaRegistry,
            BuiltInSiteProfiles.catalog,
            publicCatalog,
        )
        val release = PortalCatalogRepository(
            BuiltInSiteProfiles.releaseRegistry,
            BuiltInSiteProfiles.catalog,
            publicCatalog,
        )
        val qaPortal = qa.portals().single { it.portalId == portalId }
        val releasePortal = release.portals().single { it.portalId == portalId }

        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))

        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}
