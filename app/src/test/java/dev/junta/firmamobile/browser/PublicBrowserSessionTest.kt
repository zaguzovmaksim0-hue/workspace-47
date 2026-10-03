package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.TrustMode
import dev.junta.firmamobile.ui.profileRequiresWebMessageBridge
import java.net.URI
import org.junit.Assert.*
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
class PublicBrowserSessionTest {
    private val registry = BuiltInSiteProfiles.qaRegistry
    private val known = ProfileId("junta-andalucia")

    @Test fun aPublicEntryNeedsNoSyntheticOrExistingProfile() {
        val url = URI("https://unprofiled.synthetic.example/form?step=one#section")
        assertEquals(url.toASCIIString(), BrowserSessionStatePolicy.validatedEntryUrl(registry, null, url))
        val resolution = BrowserUrlPolicy(registry, null).resolve(url.toASCIIString())
        assertEquals(TrustMode.BROWSE_ONLY, resolution.trustMode)
        assertNull(resolution.site)
        assertFalse(profileRequiresWebMessageBridge(null))
    }

    @Test fun aKnownSigningUrlDoesNotPromoteAnUnprofiledSessionEvenWithAnInjectedActiveId() {
        val start = checkNotNull(registry.profile(known)).startUrl.toASCIIString()
        val policy = BrowserUrlPolicy(registry, null)
        for (active in listOf(null, known)) {
            val resolution = policy.resolve(start, active)
            assertEquals(TrustMode.BROWSE_ONLY, resolution.trustMode)
            assertNull(resolution.site)
        }
    }

    @Test fun navigationHistoryAndReturnToKnownOriginNeverAcquireAProfile() {
        val controller = BrowserTrustController(BrowserUrlPolicy(registry, null), SensitiveFlowInvalidator {})
        for (url in listOf("https://unprofiled.synthetic.example/", registry.profile(known)!!.startUrl.toASCIIString(), "https://sede.juntadeandalucia.es/", "https://identity.synthetic.example/login")) {
            val state = controller.navigate(url)
            assertNull(state.activeProfileId)
            assertNull(state.resolution.site)
            assertEquals(TrustMode.BROWSE_ONLY, state.resolution.trustMode)
        }
        controller.back(); controller.reload()
        assertNull(controller.navigate(registry.profile(known)!!.startUrl.toASCIIString()).activeProfileId)
    }

    @Test fun malformedOrLocalEntriesCannotExploitTheNullableProfilePath() {
        for (raw in listOf("http://public.example", "https://user@public.example", "file:///secret", "https://localhost/", "https://127.0.0.1/", "https://public.example:444/", "https://host.local/")) {
            assertNull(raw, BrowserSessionStatePolicy.validatedEntryUrl(registry, null, URI(raw)))
            assertNull(raw, BrowserUrlPolicy(registry, null).resolve(raw).uri)
        }
    }

    @Test fun anExplicitUnknownProfileStillFailsInsteadOfSilentlyBecomingPublic() {
        val invalid = ProfileId("does-not-exist-synthetic")
        assertThrows(IllegalArgumentException::class.java) { BrowserUrlPolicy(registry, invalid) }
        assertThrows(IllegalArgumentException::class.java) { JuntaNavigationPolicy(invalid, registry) }
        assertNull(BrowserSessionStatePolicy.validatedEntryUrl(registry, invalid, URI("https://public.example/")))
    }

    @Test fun anExistingProfileRetainsItsOriginalTrustedStartBehavior() {
        val start = registry.profile(known)!!.startUrl
        val selected = BrowserUrlPolicy(registry, known).resolve(start.toASCIIString())
        assertEquals(known, selected.site?.profile?.profileId)
        assertNotEquals(TrustMode.BROWSE_ONLY, selected.trustMode)
        assertEquals(start.toASCIIString(), BrowserSessionStatePolicy.validatedEntryUrl(registry, known, start))
    }

    @Test fun unprofiledNavigationPermitsPublicHttpsButNotTheLegacyPrivilegedUriHandler() {
        val policy = JuntaNavigationPolicy(null, registry)
        val page = "https://unprofiled.synthetic.example/"
        for (url in listOf("https://identity.synthetic.example/login?state=a#b", registry.profile(known)!!.startUrl.toASCIIString())) {
            assertEquals(NavigationDecision.AllowInWebView, policy.decide(url, page))
        }
        assertTrue(policy.decide("http://identity.synthetic.example/login", page) is NavigationDecision.Block)
        assertTrue(policy.decide("file:///private", page) is NavigationDecision.Block)
        // Actual native AutoFirma uses its separately owned/confirmed callback,
        // not a fabricated grant to the legacy per-profile scheme handler.
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN),
            policy.decide("afirma://sign?dat=c3ludGhldGlj", page))
    }
}
