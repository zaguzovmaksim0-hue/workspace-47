package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import java.net.URI
import org.junit.Assert.assertEquals
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
class PortalPageModeTest {
    private val catalog = BuiltInSiteProfiles.catalog
    private val publicCatalog by lazy(::loadBundledPublicPortalCatalog)
    private val registries by lazy {
        listOf(BuildTrustPolicy.QA, BuildTrustPolicy.RELEASE).associateWith { SiteProfileRegistry(catalog, it) }
    }

    private fun repository(policy: BuildTrustPolicy, portals: PublicPortalCatalog = publicCatalog) =
        PortalCatalogRepository(registries.getValue(policy), catalog, portals)

    @Test
    fun `the ofvirtual start page offers its verified profile in every build`() {
        val expected = PortalLaunchTarget(ProfileId("junta-ofvirtual"), URI(OFVIRTUAL))
        val sameDocument = listOf(
            OFVIRTUAL,
            "$OFVIRTUAL;jsessionid=SYNTHETIC0001",
            "$OFVIRTUAL?execution=e1s1#synthetic",
            "https://WS072.JuntaDeAndalucia.ES/ofvirtual/auth/signInAutcertjs",
            "https://ws072.juntadeandalucia.es:443/ofvirtual/auth/signInAutcertjs",
            "https://ws072.juntadeandalucia.es/ofvirtual;synthetic=1/auth/signInAutcertjs",
        )
        for (policy in registries.keys) {
            val repository = repository(policy)
            for (page in sameDocument) assertEquals("$policy $page", expected, repository.resolvePortalModeForPage(page))
        }
    }

    @Test
    fun `lookalike, downgraded, other or malformed pages never offer a portal mode`() {
        val pages = listOf(
            "http://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            "$OFVIRTUAL/",
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/SignInAutcertjs",
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/../auth/signInAutcertjs",
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/synthetic",
            "https://ws072.juntadeandalucia.es/",
            "https://ws072.juntadeandalucia.es.evil.example/ofvirtual/auth/signInAutcertjs",
            "https://evil.example/ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            "https://synthetic@ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            "https://ws072.juntadeandalucia.es:8443/ofvirtual/auth/signInAutcertjs",
            "https://ws072 .juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            "intent://sign?id=Synthetic#Intent;scheme=afirma;end",
            "afirma://sign?id=Synthetic",
            "about:blank",
            "",
            "not a page",
        )
        for (policy in registries.keys) {
            val repository = repository(policy)
            for (page in pages) assertNull("$policy $page", repository.resolvePortalModeForPage(page))
        }
    }

    @Test
    fun `an unverified portal mode is offered only where the build exposes that profile`() {
        assertEquals(
            PortalLaunchTarget(ProfileId("junta-andalucia"), URI(OVORION)),
            repository(BuildTrustPolicy.QA).resolvePortalModeForPage(OVORION),
        )
        assertNull(repository(BuildTrustPolicy.RELEASE).resolvePortalModeForPage(OVORION))
    }

    @Test
    fun `the page selects a binding but authority still comes from the registry`() {
        val moved = publicCatalog.copy(entries = publicCatalog.entries.map { entry ->
            if (entry.profileId == ProfileId("junta-ofvirtual")) {
                entry.copy(entryUrl = URI("https://evil.example/ofvirtual/auth/signInAutcertjs"))
            } else entry
        })
        for (policy in registries.keys) {
            val repository = repository(policy, moved)
            assertNull(repository.resolvePortalModeForPage("https://evil.example/ofvirtual/auth/signInAutcertjs"))
            assertNull(repository.resolvePortalModeForPage(OFVIRTUAL))
        }
    }

    @Test
    fun `every offered mode is the registry start of an enabled capable binding of that page`() {
        for ((policy, registry) in registries) {
            val repository = repository(policy)
            val items = repository.portals()
            val pageOf = items.associateWith { item ->
                repository.resolveUniversalOpenTarget(item)?.entryUrl?.let(::portalPageKey)
            }
            var offered = 0
            for (item in items) {
                val page = repository.resolveUniversalOpenTarget(item)?.entryUrl ?: continue
                val samePage = items.filter { pageOf[it] == pageOf[item] }
                val candidates = samePage.filter { it.capabilities.isNotEmpty() }
                    .mapNotNull(repository::resolveLaunch).toSet()
                val result = repository.resolvePortalModeForPage(page.toASCIIString())
                if (result == null) {
                    assertTrue("$policy ${item.portalId} has one reviewed mode", candidates.size != 1)
                    continue
                }
                offered++
                assertEquals("$policy ${item.portalId}", setOf(result), candidates)
                assertEquals(registry.profile(result.profileId)?.startUrl, result.entryUrl)
                if (item.capabilities.isEmpty() || !item.isEnabled) {
                    assertTrue("$policy ${item.portalId} borrows a shared page", samePage.size > 1)
                }
            }
            assertTrue("$policy offers reviewed modes", offered > 0)
        }
    }

    @Test
    fun `enabled bindings without certificate capabilities never become a portal mode by themselves`() {
        // Only QA activates navigation-only profiles; release activates none of them.
        val repository = repository(BuildTrustPolicy.QA)
        val items = repository.portals()
        val capablePages = items.filter { it.capabilities.isNotEmpty() }
            .mapNotNull { repository.resolveUniversalOpenTarget(it)?.entryUrl?.let(::portalPageKey) }
            .toSet()
        val navigationOnly = items.filter { it.isEnabled && it.capabilities.isEmpty() }
            .mapNotNull { repository.resolveUniversalOpenTarget(it)?.entryUrl }
            .filter { portalPageKey(it) !in capablePages }
        assertTrue("QA has enabled navigation-only bindings", navigationOnly.isNotEmpty())
        for (page in navigationOnly) assertNull(page.toString(), repository.resolvePortalModeForPage(page.toASCIIString()))
        assertTrue(
            repository(BuildTrustPolicy.RELEASE).portals().none { it.isEnabled && it.capabilities.isEmpty() },
        )
    }

    @Test
    fun `page identity keeps only scheme, host, default port and path`() {
        assertEquals("https://host.example/", portalPageKey(URI("https://Host.Example")))
        assertEquals("https://host.example/a/b", portalPageKey(URI("https://host.example:443/a;x=1/b;y=2?q=1#f")))
        assertNotNull(portalPageKey(URI("HTTPS://host.example/a")))
        for (page in listOf("http://host.example/a", "https://u@host.example/a", "https://host.example:444/a",
            "https:opaque", "/relative/path", "mailto:someone@host.example")) {
            assertNull(page, portalPageKey(URI(page)))
        }
    }

    private companion object {
        const val OFVIRTUAL = "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
        const val OVORION = "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"
    }
}
