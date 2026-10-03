package dev.junta.firmamobile.catalog

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocationParser
import dev.junta.firmamobile.afirma.servlet.AfirmaServletParseResult
import dev.junta.firmamobile.afirma.servlet.nativeOperation
import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.BrowserNavigationCallbacks
import dev.junta.firmamobile.browser.JuntaNavigationPolicy
import dev.junta.firmamobile.browser.JuntaWebViewClient
import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.security.SanitizedLogger
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** The real bundled catalog -> universal target -> real WebViewClient -> real
 * native parser/operation selection. No website, key, network or government
 * transaction is used, and this is not presented as a live-portal E2E test. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class UniversalAutoFirmaCatalogDispatchTest {
    @Test fun everyBundledPortalReachesTheSameNativeEngineInBothBuildTrustModes() {
        val profiles = BuiltInSiteProfiles.catalog
        val bundled = loadBundledPublicPortalCatalog()
        assertTrue("The complete requested catalog must be covered", bundled.entries.size >= 180)
        val inputs = UniversalAutoFirmaFixtures.requests()
        var exercised = 0
        for (mode in listOf(BuildTrustPolicy.QA, BuildTrustPolicy.RELEASE)) {
            val registry = SiteProfileRegistry(profiles, mode)
            val repository = PortalCatalogRepository(registry, profiles, bundled)
            val view = WebView(ApplicationProvider.getApplicationContext<Context>())
            try {
                for (item in repository.portals()) {
                    val target = checkNotNull(repository.resolveUniversalOpenTarget(item)) { item.portalId.value }
                    val page = target.entryUrl.toASCIIString()
                    for ((label, raw) in inputs) {
                        var calls = 0
                        val client = JuntaWebViewClient(
                            callbacks = NoLegacyGrant(), logger = SanitizedLogger(),
                            navigationPolicy = JuntaNavigationPolicy(null, registry),
                            currentPageUrl = { page }, isActiveWebView = { it === view },
                            activeProfileId = { null },
                            onNativeAfirmaInvocation = { owner, uri, source ->
                                assertSame(view, owner); assertEquals(page, source); calls++
                                when (val parsed = AfirmaServletInvocationParser.parse(uri, source)) {
                                    is AfirmaServletParseResult.Accepted -> {
                                        val origin = URI(parsed.invocation.sourceOrigin)
                                        assertEquals(target.entryUrl.host.lowercase(), origin.host.lowercase())
                                        nativeOperation(parsed.invocation).close()
                                    }
                                    is AfirmaServletParseResult.Deferred -> parsed.invocation.close()
                                    else -> fail("${item.portalId.value}/$mode/$label: $parsed")
                                }
                                true
                            },
                        )
                        assertTrue("${item.portalId.value}/$label", client.shouldOverrideUrlLoading(view, Request(raw)))
                        assertEquals("Exactly one common handler for ${item.portalId.value}/$label", 1, calls)
                        exercised++
                    }
                }
            } finally { view.destroy() }
        }
        assertEquals(bundled.entries.size * inputs.size * 2, exercised)
        println("UNIVERSAL_AFIRMA_CATALOG entries=${bundled.entries.size} buildModes=2 requestsPerEntry=${inputs.size} dispatchChecks=$exercised liveGovernmentSignatures=0")
    }

    @Test fun genericCoverageDoesNotGrantIframeOrStaleViewTheParentAuthority() {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        var calls = 0; var current = true
        val client = JuntaWebViewClient(
            callbacks = NoLegacyGrant(), logger = SanitizedLogger(),
            navigationPolicy = JuntaNavigationPolicy(null, BuiltInSiteProfiles.qaRegistry),
            currentPageUrl = { "https://portal.synthetic.example/" }, isActiveWebView = { current },
            onNativeAfirmaInvocation = { _, _, _ -> calls++; true },
        )
        try {
            val uri = UniversalAutoFirmaFixtures.requests().first().second
            client.shouldOverrideUrlLoading(view, Request(uri, main = false))
            client.shouldOverrideUrlLoading(view, Request(uri, methodName = "POST"))
            current = false; client.shouldOverrideUrlLoading(view, Request(uri))
            assertEquals(0, calls)
        } finally { view.destroy() }
    }

    private class Request(private val raw: String, private val main: Boolean = true, private val methodName: String = "GET") : WebResourceRequest {
        override fun getUrl() = Uri.parse(raw)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = methodName
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
    private class NoLegacyGrant : BrowserNavigationCallbacks {
        override fun openExternal(uri: Uri) { fail("Common native operation must not escape to an external browser") }
        override fun openOfficialAutoFirma(uri: Uri) = Unit // Negative-frame case stays on the existing fail-closed route.
        override fun onAfirmaRequest(request: AfirmaRequest) { fail("No profile-based signing authority may be inherited") }
        override fun onNavigationBlocked(reason: NavigationBlockReason) = Unit
        override fun onBrowserError(error: BrowserErrorCode) { fail("No browser error expected") }
    }
}
