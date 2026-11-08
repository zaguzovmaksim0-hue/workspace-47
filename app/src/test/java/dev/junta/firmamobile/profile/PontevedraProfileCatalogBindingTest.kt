package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import java.net.URI
import org.junit.Assert.assertEquals
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
class PontevedraProfileCatalogBindingTest {
    private val profileId = ProfileId("diputacion-pontevedra-instancia-xenerica")
    private val portalId = PortalId("diputacion-pontevedra-sede")
    private val startUrl = URI("https://sede.depo.gal/web/public/catalog-detail/50709505")

    @Test
    fun qaCatalogLaunchesOnlyTheReviewedEntryAndKeepsReleaseDisabled() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.portalId == portalId }
        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val portal = qa.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(portal))
        assertNull(qa.resolveLaunch(profileId, URI("https://sede.depo.gal/web/public/login/login.xhtml")))
        val release = PortalCatalogRepository(
            BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, publicCatalog,
        )
        assertNull(release.resolveLaunch(profileId, startUrl))
    }

    @Test
    fun sharedClaveOriginsGrantTlsOnlyToTheSelectedProfileAndNeverSigning() {
        val qa = BuiltInSiteProfiles.qaRegistry
        val profile = requireNotNull(qa.profile(profileId))
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        val certificateUrl = URI("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen")
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, qa.resolveForProfile(profileId, certificateUrl)?.trustMode)
        assertNull(qa.resolveForProfile(ProfileId("comunidad-madrid-gestiona2-public-navigation"), certificateUrl))
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN))
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
    }
}
