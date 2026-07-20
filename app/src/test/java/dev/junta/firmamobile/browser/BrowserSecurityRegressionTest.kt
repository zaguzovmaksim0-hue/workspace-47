package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.TrustMode
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.SigningContext
import dev.junta.firmamobile.signing.SigningErrorCode
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class BrowserSecurityRegressionTest {
    private val qaRegistry = SiteProfileRegistry(
        BuiltInSiteProfiles.catalog,
        BuildTrustPolicy.QA,
    )

    @Test
    fun selectedServiceIsNotUsedAsTheEffectiveTopLevelSecurityProfile() {
        val source = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )

        assertTrue(
            "BrowserScreen must keep catalog selection separate from effective trust state",
            "selectedServiceId" in source && "effectiveTopLevelProfileId" in source,
        )
        assertFalse(
            "The catalog-selected profile must not be injected directly into the bridge/client",
            "activeProfileId = { profileId }" in source,
        )
    }

    @Test
    fun browserAndBridgePoliciesAreConstructedWithTheSelectedProfile() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val bridgeSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt",
        )
        val activitySource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/MainActivity.kt",
        )

        assertTrue("Browser URL policy must be profile-scoped", "selectedProfileId = selectedServiceId" in screenSource)
        assertTrue("Navigation policy must be profile-scoped", "JuntaNavigationPolicy(selectedServiceId)" in screenSource)
        assertTrue("WebView client must receive the scoped policy", "navigationPolicy = navigationPolicy" in screenSource)
        assertTrue("Bridge must receive the selected profile", "profileId = selectedServiceId" in screenSource)
        assertTrue("Bridge origin rules must be profile-scoped", "webMessageOriginRules(profileId)" in bridgeSource)
        assertFalse("Bridge must not use the global origin-rule union", "JuntaOriginPolicy.webMessageOriginRules," in bridgeSource)
        assertTrue("Signing coordinator origin must be selected-profile scoped", "signingOriginFor(" in activitySource)
    }

    @Test
    fun crossProfileNavigationCannotRebindTheSelectedSecurityProfile() {
        val invalidations = mutableListOf<BrowserTransitionReason>()
        val junta = profile("junta-andalucia")
        val redSara = profile("reg-age-redsara")
        val controller = BrowserTrustController(
            BrowserUrlPolicy(qaRegistry, junta.profileId),
            SensitiveFlowInvalidator(invalidations::add),
        )

        val initial = controller.navigate(junta.startUrl.toASCIIString())
        val crossProfile = controller.navigate(redSara.startUrl.toASCIIString())

        assertEquals(junta.profileId, initial.activeProfileId)
        assertEquals(TrustMode.BLOCKED, crossProfile.resolution.trustMode)
        assertEquals(null, crossProfile.activeProfileId)
        assertEquals(2L, crossProfile.epoch)
        assertEquals(
            listOf(BrowserTransitionReason.NAVIGATE, BrowserTransitionReason.NAVIGATE),
            invalidations,
        )
    }

    @Test
    fun rawWebViewHistoryIsNeverSavedOrRestored() {
        val activitySource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/MainActivity.kt",
        )
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val sessionPolicySource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/BrowserSessionStatePolicy.kt",
        )

        assertFalse("Activity must not save a WebView Bundle", "webView.saveState" in activitySource)
        assertFalse("Browser screen must not restore WebView history", "restoreState(" in screenSource)
        assertFalse("Browser screen must not capture WebView history", "saveState(" in screenSource)
        assertFalse(
            "The legacy raw-history holder must be deleted",
            projectFileExists(
                "app/src/main/java/dev/junta/firmamobile/browser/WebViewStateHolder.kt",
            ),
        )
        assertTrue(
            "Legacy saved history must be explicitly discarded",
            "discardLegacyWebViewState(savedInstanceState)" in activitySource &&
                "LEGACY_WEBVIEW_HISTORY_KEY" in sessionPolicySource,
        )
        assertTrue(
            "Every fresh WebView must load the revalidated catalog entry URL",
            "validatedEntryUrl" in screenSource &&
                "webView.loadUrl(validatedEntryUrl)" in screenSource,
        )
    }

    @Test
    fun foreignIframeCannotChangeTheEffectiveTopLevelProfile() {
        val redSara = profile("reg-age-redsara")
        val controller = BrowserTrustController(
            BrowserUrlPolicy(qaRegistry, redSara.profileId),
            SensitiveFlowInvalidator {},
        )
        val current = controller.navigate(redSara.startUrl.toASCIIString())
        val epochBefore = current.epoch
        val adapter = MiniAppletBridgeAdapter(
            activeProfileId = { controller.current().activeProfileId },
        )

        val result = adapter.route(
            rawMessage = juntaMessage(),
            sourceOrigin = Uri.parse("https://www.juntadeandalucia.es"),
            isMainFrame = false,
            navigationEpoch = epochBefore,
        )

        assertTrue(result is MiniAppletBridgeRouteResult.Rejected)
        assertEquals(
            SigningErrorCode.NAVIGATION_CHANGED,
            (result as MiniAppletBridgeRouteResult.Rejected).code,
        )
        assertEquals(redSara.profileId, controller.current().activeProfileId)
        assertEquals(epochBefore, controller.current().epoch)
    }

    @Test
    fun staleBridgeBindingIsRejectedAfterNavigationEpochAdvances() {
        val origin = TrustedOrigin("https", "reg.redsara.es", 443)
        var epoch = 11L
        val posted = mutableListOf<String>()
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { epoch },
            currentOrigin = { origin },
        )
        val requestId = UUID.fromString(REQUEST_ID)
        val channel = checkNotNull(
            registry.create(
                requestId = requestId,
                context = signingContext(origin, epoch),
                postMessage = posted::add,
            ),
        )

        epoch++

        assertFalse(channel.success(LocalSignature(byteArrayOf(1)), byteArrayOf(2)))
        assertTrue(posted.isEmpty())
        assertTrue(registry.abandonAll().isEmpty())
    }

    @Test
    fun unresolvedOriginCannotMatchMissingEffectiveProfile() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )

        assertTrue(
            "Resolved profile must be non-null before matching",
            "resolvedProfileId != null" in screenSource,
        )
        assertTrue(
            "Effective profile must be non-null before matching",
            "effectiveTopLevelProfileId != null" in screenSource,
        )
        assertTrue(
            "Only equal non-null profile identifiers may match",
            "resolvedProfileId == effectiveTopLevelProfileId" in screenSource,
        )
    }

    @Test
    fun rendererDeathInvalidatesBothNormalAndClientTlsSessions() {
        val clientSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt",
        )
        val clientAuthSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt",
        )
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )

        assertTrue(
            "Normal WebView renderer death must identify the exact affected WebView",
            "callbacks.onRenderProcessGone(view)" in clientSource,
        )
        assertTrue(
            "Client TLS renderer death must abandon its one-shot grant",
            "requestHandler.abandon()" in clientAuthSource &&
                "callbacks.onRenderProcessGone(view)" in clientAuthSource,
        )
        assertTrue(
            "Only the active WebView may trigger renderer recovery",
            "webViewRef.compareAndSet(view, null)" in screenSource,
        )
        assertTrue(
            "Renderer death must close bridge state and advance the navigation epoch",
            "bridgeRef.getAndSet(null)?.close()" in screenSource &&
                "advanceNavigationEpoch()" in screenSource,
        )
        assertTrue(
            "Renderer death must force a fresh WebView",
            "webViewRecreationEpoch++" in screenSource &&
                "key(clientAuthGrant != null, webViewRecreationEpoch)" in screenSource,
        )
        assertFalse(
            "A fresh WebView must never restore the dead renderer's history",
            "restoreOrLoad" in screenSource || "restoreState(" in screenSource,
        )
    }

    private fun profile(id: String) = BuiltInSiteProfiles.catalog.profiles.single {
        it.profileId == ProfileId(id)
    }

    private fun juntaMessage(): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", Base64.getEncoder().encodeToString("payload".encodeToByteArray()))
        .put("algorithm", "SHA1withRSA")
        .put("format", "CAdES")
        .put("extraProperties", "mode=explicit")
        .toString()

    private fun signingContext(origin: TrustedOrigin, epoch: Long) = SigningContext(
        profileId = "reg-age-redsara",
        profileVersion = 1,
        origin = origin,
        navigationId = NavigationId(DOCUMENT_ID),
        navigationEpoch = epoch,
        observedAt = Instant.parse("2030-01-01T00:00:00Z"),
    )

    private fun projectFileExists(relativePath: String): Boolean {
        val userDirectory = requireNotNull(System.getProperty("user.dir")) {
            "user.dir system property is unavailable"
        }
        var directory = File(userDirectory).canonicalFile
        repeat(8) {
            if (File(directory, relativePath).isFile) return true
            directory = directory.parentFile ?: return@repeat
        }
        return false
    }

    private fun projectSource(relativePath: String): String {
        val userDirectory = requireNotNull(System.getProperty("user.dir")) {
            "user.dir system property is unavailable"
        }
        var directory = File(userDirectory).canonicalFile
        repeat(8) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Project source not found: $relativePath")
    }

    private companion object {
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174111"
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000"
    }
}
