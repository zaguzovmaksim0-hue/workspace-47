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
    fun sevillaAtseSigningAdapterIsWiredIntoTheRuntimeResolver() {
        val activitySource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/MainActivity.kt",
        )
        val resolverBlock = activitySource
            .substringAfter("adapterResolver = { id ->", missingDelimiterValue = "")
            .substringBefore("\n            },")

        assertTrue(
            "MainActivity must construct the exact Sevilla ATSE signing adapter",
            "val sevillaAdapter = SevillaAtseXadesEnvelopingAdapter()" in activitySource,
        )
        assertTrue(
            "The runtime adapter resolver must map only Sevilla's exact protocol id to that adapter",
            resolverBlock.isNotEmpty() && "sevillaAdapter.id -> sevillaAdapter" in resolverBlock,
        )
    }

    @Test
    fun tenerifeSigningAdapterIsWiredIntoTheRuntimeResolver() {
        val activitySource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/MainActivity.kt",
        )
        val resolverBlock = activitySource
            .substringAfter("adapterResolver = { id ->", missingDelimiterValue = "")
            .substringBefore("\n            },")

        assertTrue(
            "MainActivity must construct the exact Tenerife signing adapter",
            "val tenerifeAdapter = TenerifeCadesDetachedAdapter()" in activitySource,
        )
        assertTrue(
            "The runtime adapter resolver must map Tenerife's exact protocol id to that adapter",
            resolverBlock.isNotEmpty() && "tenerifeAdapter.id -> tenerifeAdapter" in resolverBlock,
        )
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
    fun currentSiteClearCannotFallBackToGlobalBrowserDataDeletion() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val chromeSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt",
        )

        assertTrue(
            "Current-site clearing must call the exact-origin cleaner",
            "siteDataCleaner.clearOrigin" in screenSource,
        )
        assertTrue(
            "Global deletion must use a separate confirmed cleaner method",
            "siteDataCleaner.clearAllConfirmed" in screenSource,
        )
        assertFalse(
            "BrowserScreen must not call CookieManager global deletion directly",
            "removeAllCookies" in screenSource || "CookieManager.getInstance" in screenSource,
        )
        assertFalse(
            "BrowserScreen must not call WebStorage global deletion directly",
            "deleteAllData" in screenSource || "WebStorage.getInstance" in screenSource,
        )
        assertTrue(
            "The overflow menu must expose distinct site, session and global actions",
            "onClearCurrentSiteRequested" in chromeSource &&
                "onClearSessionRequested" in chromeSource &&
                "onDeleteAllBrowserDataRequested" in chromeSource,
        )
        assertFalse(
            "Measured MULTI_PROFILE support must not silently opt the WebView into a physical profile",
            "WebViewCompat.setProfile" in screenSource,
        )
    }

    @Test
    fun confirmedBrowserDataClearInvalidatesNavigationEpochBeforeDeletion() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val currentSiteBlock = screenSource
            .substringAfter("        onClearCurrentSite = {", missingDelimiterValue = "")
            .substringBefore("        onClearSession = {")
        val globalClearBlock = screenSource
            .substringAfter("        onDeleteAllBrowserData = {", missingDelimiterValue = "")
            .substringBefore("    ) { modifier ->")

        assertTrue("Current-site clear handler must be present", currentSiteBlock.isNotEmpty())
        assertTrue("Global clear handler must be present", globalClearBlock.isNotEmpty())
        assertTrue(
            "Current-site clear must invalidate the navigation epoch before deleting site data",
            currentSiteBlock.indexOf("advanceNavigationEpoch()") in
                0 until currentSiteBlock.indexOf("siteDataCleaner.clearOrigin"),
        )
        assertTrue(
            "Global clear must invalidate the navigation epoch before starting global deletion",
            globalClearBlock.indexOf("advanceNavigationEpoch()") in
                0 until globalClearBlock.indexOf("siteDataCleaner.clearAllConfirmed"),
        )
    }

    @Test
    fun globalBrowserDataClearRemovesResourceCacheWithoutWideningCurrentSiteClear() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val currentSiteBlock = screenSource
            .substringAfter("        onClearCurrentSite = {", missingDelimiterValue = "")
            .substringBefore("        onClearSession = {")
        val globalClearBlock = screenSource
            .substringAfter("        onDeleteAllBrowserData = {", missingDelimiterValue = "")
            .substringBefore("    ) { modifier ->")
        val stopLoadingIndex = globalClearBlock.indexOf("stopLoading()")
        val clearCacheIndex = globalClearBlock.indexOf("clearCache(true)")
        val globalDeletionIndex = globalClearBlock.indexOf("siteDataCleaner.clearAllConfirmed")

        assertTrue("Current-site clear handler must be present", currentSiteBlock.isNotEmpty())
        assertTrue("Global clear handler must be present", globalClearBlock.isNotEmpty())
        assertFalse(
            "Current-site clear must not widen into application-wide WebView resource-cache deletion",
            "clearCache(true)" in currentSiteBlock,
        )
        assertTrue(
            "Global clear must remove the WebView resource cache after stopping the initiating view and before global cookie/storage deletion",
            stopLoadingIndex >= 0 && clearCacheIndex in (stopLoadingIndex + 1) until globalDeletionIndex,
        )
    }

    @Test
    fun globalBrowserDataClearFailsClosedWithoutActiveWebViewOwner() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val globalClearBlock = screenSource
            .substringAfter("        onDeleteAllBrowserData = {", missingDelimiterValue = "")
            .substringBefore("    ) { modifier ->")
        val ownerLookupIndex = globalClearBlock.indexOf("val webView = webViewRef.get()")
        val missingOwnerBranchIndex = globalClearBlock.indexOf("if (webView == null) {")
        val invalidateIndex = globalClearBlock.indexOf("globalDataClearLease.invalidate()")
        val failureIndex = globalClearBlock.indexOf("globalClearResult = false")
        val nonNullBranchIndex = globalClearBlock.indexOf("} else {")
        val beginIndex = globalClearBlock.indexOf("globalDataClearLease.begin(webView)")
        val globalDeletionIndex = globalClearBlock.indexOf("siteDataCleaner.clearAllConfirmed")

        assertTrue(
            "Global clear completion ownership must be typed to a non-null WebView",
            "BrowserDataClearCompletionLease<WebView>()" in screenSource &&
                "BrowserDataClearCompletionLease<WebView?>()" !in screenSource,
        )
        assertTrue("Global clear handler must expose the fail-closed owner boundary", globalClearBlock.isNotEmpty())
        assertTrue(
            "A missing WebView must invalidate stale completion ownership and publish failure",
            ownerLookupIndex >= 0 &&
                missingOwnerBranchIndex in (ownerLookupIndex + 1) until invalidateIndex &&
                invalidateIndex in (missingOwnerBranchIndex + 1) until failureIndex,
        )
        assertTrue(
            "Global cookie/WebStorage deletion must live only in the admitted non-null-owner branch",
            nonNullBranchIndex in (failureIndex + 1) until beginIndex &&
                beginIndex in (nonNullBranchIndex + 1) until globalDeletionIndex,
        )
    }

    @Test
    fun globalDataClearCompletionIsBoundToTheInitiatingWebView() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )

        assertTrue(
            "Global clear must use a one-shot non-null completion lease",
            "BrowserDataClearCompletionLease<WebView>()" in screenSource &&
                "globalDataClearLease.begin(webView)" in screenSource &&
                "globalDataClearLease.consume(clearRequest)" in screenSource,
        )
        assertTrue(
            "Profile disposal must invalidate a pending global-clear completion",
            "globalDataClearLease.invalidate()" in screenSource,
        )
        assertTrue(
            "Only the exact initiating WebView may be reloaded after clear",
            "webViewRef.get() === clearRequest.owner" in screenSource &&
                "clearRequest.owner.loadUrl(validatedEntryUrl)" in screenSource,
        )
    }

    @Test
    fun bridgeCompatibilityErrorIsBoundToTheInitiatingWebView() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val attachmentFailureMarker = "if (!attachment.listenerAttached ||"
        val attachmentFailureBlock = screenSource
            .substringAfter(attachmentFailureMarker, missingDelimiterValue = "")
            .substringBefore("} else {")

        assertTrue(
            "BrowserScreen must retain the bridge attachment failure boundary",
            attachmentFailureBlock.isNotEmpty(),
        )
        assertTrue(
            "Only the exact initiating WebView may publish a deferred compatibility error",
            "webView.post {" in attachmentFailureBlock &&
                "webViewRef.get() === webView" in attachmentFailureBlock &&
                "compatibilityError = true" in attachmentFailureBlock,
        )
    }

    @Test
    fun webMessageBridgeReleaseIsBoundToTheExactWebViewOwner() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val onReleaseBlock = screenSource
            .substringAfter("onRelease = { webView ->", missingDelimiterValue = "")
            .substringBefore("},\n                )")

        assertTrue(
            "BrowserScreen must own bridge attachments through an exact-owner lease",
            "BrowserOwnedResourceLease<WebView, WebMessageBridgeAttachment>" in screenSource &&
                "bridgeAttachmentLease.bind(webView, attachment)" in screenSource,
        )
        assertTrue(
            "AndroidView release must release the exact bridge owner before destroying it",
            onReleaseBlock.isNotEmpty() &&
                "bridgeAttachmentLease.release(webView)" in onReleaseBlock &&
                onReleaseBlock.indexOf("bridgeAttachmentLease.release(webView)") <
                onReleaseBlock.indexOf("webView.destroy()"),
        )
        assertFalse(
            "Bridge lifecycle must not use an unowned attachment reference",
            "AtomicReference<WebMessageBridgeAttachment?>" in screenSource ||
                "bridgeRef.set(attachment)" in screenSource,
        )
    }

    @Test
    fun signingTtlAndReplayPathsUseMonotonicBoundedStateOnly() {
        val pendingSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/signing/PendingSignRequestStore.kt",
        )
        val coordinatorSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt",
        )
        val bridgeSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt",
        )
        val shimSource = projectSource("app/src/main/res/raw/afirma_shim.js")

        assertTrue(
            "Pending request and reply replay state must use a TTL-bounded ledger",
            "BoundedReplayLedger" in pendingSource && "BoundedReplayLedger" in bridgeSource,
        )
        assertTrue(
            "Request and active-operation boundaries must use process monotonic time",
            "MonotonicSecurityTime.isExpiredOrInvalid" in pendingSource &&
                "MonotonicSecurityTime.isExpiredOrInvalid" in coordinatorSource &&
                "MonotonicSecurityTime.remaining" in coordinatorSource,
        )
        assertFalse(
            "Civil clocks must not decide request or reply expiry",
            "Duration.between(request.context.observedAt" in coordinatorSource ||
                "clock.millis() >= binding" in bridgeSource ||
                "isExpired(expiresAt: Instant)" in pendingSource,
        )
        assertFalse(
            "Bridge security identifiers must never fall back to Math.random",
            "Math.random" in shimSource,
        )
        assertTrue(
            "AFIRMA URI forwarding must fail closed without a Web Crypto UUID",
            "const uriRequestId = secureRequestId()" in shimSource &&
                "if (!uriRequestId)" in shimSource,
        )
    }

    @Test
    fun clientTlsWebViewWaitsForGenerationBoundPreferenceBarrier() {
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val barrierSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/ClientCertPreferenceBarrier.kt",
        )
        val coordinatorSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/ClientCertPreferenceCoordinator.kt",
        )

        assertTrue(
            "Client TLS entry must request a process-scoped asynchronous clear",
            "clientCertPreferenceCoordinator.requestClear" in screenSource &&
                "ClientCertPreferenceClearResult.CLEARED" in screenSource,
        )
        assertTrue(
            "The dedicated grant may be activated only inside the successful callback",
            "clientAuthGrant = grant" in screenSource &&
                "clientAuthClearRequest.compareAndSet" in screenSource,
        )
        assertFalse(
            "BrowserScreen must never call the platform static preference API directly",
            "WebView.clearClientCertPreferences" in screenSource,
        )
        assertFalse(
            "A confirmed target must not directly activate a Client TLS grant",
            "clientAuthGrant = ClientAuthGrant" in screenSource,
        )
        assertTrue(
            "Timeout must be exactly three seconds and callbacks generation-bound",
            "Duration.ofSeconds(3)" in barrierSource &&
                "token.generation" in barrierSource &&
                "finishSuccess(token)" in coordinatorSource,
        )
        assertTrue(
            "Renderer, disposal, profile and background paths must use process cleanup",
            "DisposableEffect(selectedServiceId, onCancelSigning, clientCertPreferenceCoordinator)" in screenSource &&
                "DisposableEffect(selectedServiceId, lifecycleOwner, clientCertPreferenceCoordinator)" in screenSource &&
                "Lifecycle.Event.ON_STOP" in screenSource &&
                "abandonClientAuth" in screenSource,
        )
        assertTrue(
            "Post-callback validation failures must share one fail-closed recovery path",
            "recoverClientAuthPreparationFailure(requestAnotherClear" in screenSource,
        )
        assertTrue(
            "Profile changes must recreate local authorizer and callback ownership",
            "remember(selectedServiceId)" in screenSource &&
                "AtomicReference<ClientCertPreferenceClearRequest?>" in screenSource,
        )
    }

    @Test
    fun clientCertPreferenceFailureIsProcessScopedAndBlocksWebViewCreation() {
        val appSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt",
        )
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )

        assertTrue(
            "The preference coordinator must be owned by the Application process",
            "clientCertPreferenceCoordinator" in appSource &&
                "ClientCertPreferenceCoordinator" in appSource,
        )
        assertTrue(
            "CLEARING or FAILED must suppress all AndroidView creation",
            "clientCertPreferenceState" in screenSource &&
                "ClientCertPreferenceBarrierState.IDLE" in screenSource &&
                "if (!clientCertPreferenceBlocked)" in screenSource,
        )
        assertTrue(
            "A failed process-wide clear must have a distinct recovery request",
            "beginClientCertPreferenceRecovery" in screenSource &&
                "ClientCertPreferenceClearResult.CLEARED" in screenSource,
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
            "bridgeAttachmentLease.close()" in screenSource &&
                "advanceNavigationEpoch()" in screenSource,
        )
        assertTrue(
            "Renderer death must force a fresh WebView",
            "webViewRecreationEpoch++" in screenSource &&
                "if (!clientCertPreferenceBlocked) key(" in screenSource &&
                "webViewRecreationEpoch" in screenSource,
        )
        assertFalse(
            "A fresh WebView must never restore the dead renderer's history",
            "restoreOrLoad" in screenSource || "restoreState(" in screenSource,
        )
    }

    @Test
    fun trustedWebViewExplicitlyDisablesGeolocation() {
        val source = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt",
        )

        assertTrue(
            "WebView geolocation must be explicitly disabled in production settings",
            "setGeolocationEnabled(false)" in source,
        )
    }

    @Test
    fun manualUrlEditorSurfaceIsAbsentFromProductionBrowserChrome() {
        val addressSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserAddressBar.kt",
        )
        val chromeSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt",
        )
        val screenSource = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )

        assertTrue(
            "Sanitized host presentation must remain available",
            "object BrowserAddressPresentation" in addressSource,
        )
        assertFalse(
            "Production main source must not retain the dormant manual URL editor",
            "internal fun BrowserAddressBar(" in addressSource ||
                "BasicTextField" in addressSource ||
                "onEditingChange" in addressSource ||
                "onSubmit" in addressSource,
        )
        assertFalse(
            "Browser chrome must not retain dormant hooks that can expose arbitrary address UI",
            "onIdentityClick" in chromeSource || "editingContent" in chromeSource,
        )
        assertFalse(
            "BrowserLayout must not configure a dormant manual-editor slot",
            "editingContent = null" in screenSource,
        )
    }

    @Test
    fun browserScreenForwardsMelillaBatchRuntimeCallbacksIntoTheBridge() {
        val source = projectSource(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt",
        )
        val bridgeBlock = source
            .substringAfter("val attachment = WebMessageBridge(", missingDelimiterValue = "")
            .substringBefore(").attach(webView)")

        assertTrue(
            "BrowserScreen must expose an explicit Melilla batch request callback",
            "onMelillaBatchRequest:" in source,
        )
        assertTrue(
            "BrowserScreen must expose an explicit Melilla batch cancellation callback",
            "onMelillaBatchCancel:" in source,
        )
        assertTrue(
            "The WebMessageBridge instance must forward the exact Melilla batch request callback",
            bridgeBlock.isNotEmpty() &&
                "onMelillaBatchRequest = {" in bridgeBlock &&
                "request: MelillaBatchBridgeRequest" in bridgeBlock &&
                "reply: MelillaBatchReplyChannel" in bridgeBlock &&
                "onMelillaBatchRequest(request, reply)" in bridgeBlock,
        )
        assertTrue(
            "The WebMessageBridge instance must forward the exact Melilla batch cancellation callback",
            bridgeBlock.isNotEmpty() &&
                "onMelillaBatchCancel = onMelillaBatchCancel" in bridgeBlock,
        )
    }

    @Test
    fun mainActivityComposesMelillaBatchThroughOneOwnedSigningFlow() {
        val source = projectSource(
            "app/src/main/java/dev/junta/firmamobile/MainActivity.kt",
        )
        val browserBlock = source
            .substringAfter("BrowserScreen(", missingDelimiterValue = "")
            .substringBefore("\n                        )")
        val ordinaryPrepareBlock = source
            .substringAfter("private fun prepareMiniAppletSigning(", missingDelimiterValue = "")
            .substringBefore("\n    private fun confirmSigning")
        val batchPrepareBlock = source
            .substringAfter("private fun prepareMelillaBatchSigning(", missingDelimiterValue = "")
            .substringBefore("\n    private fun confirmSigning")

        assertTrue(
            "MainActivity must own a dedicated Melilla batch coordinator",
            "private lateinit var batchSigningCoordinator: BatchSigningCoordinator" in source,
        )
        assertTrue(
            "Melilla batch protocol must use the existing direct HTTPS transport stack",
            "MelillaBatchProtocolAdapter(transport = HttpsProfileHttpTransport())" in source,
        )
        val melillaAdapterBlock = source
            .substringAfter("melillaBatchSigningAdapter = MelillaBatchSigningAdapter(", missingDelimiterValue = "")
            .substringBefore("\n        )")
        assertTrue(
            "MainActivity must normalize only against the built-in Melilla profile registry",
            melillaAdapterBlock.isNotEmpty() &&
                "registry = BuiltInSiteProfiles.runtimeRegistry" in melillaAdapterBlock,
        )
        assertTrue(
            "BrowserScreen must forward Melilla request and cancellation into MainActivity",
            browserBlock.isNotEmpty() &&
                "onMelillaBatchRequest = ::prepareMelillaBatchSigning" in browserBlock &&
                "onMelillaBatchCancel = { requestId ->" in browserBlock,
        )
        assertTrue(
            "Ordinary signing must claim shared ownership before coordinator prepare",
            ordinaryPrepareBlock.isNotEmpty() &&
                "signingFlowOwnership.acquire(SigningFlowKind.ORDINARY, requestId)" in ordinaryPrepareBlock,
        )
        assertTrue(
            "Batch signing must claim the same shared ownership before coordinator prepare",
            batchPrepareBlock.isNotEmpty() &&
                "signingFlowOwnership.acquire(SigningFlowKind.BATCH, requestId)" in batchPrepareBlock,
        )
        assertTrue(
            "Confirm, cancel, and terminal dismissal must route through the current owner",
            "signingFlowOwnership.current()" in source &&
                "when (owner.kind)" in source &&
                "onDismissSigningState = ::dismissSigningState" in browserBlock,
        )
        val cancelBlock = source
            .substringAfter("private fun cancelSigning(", missingDelimiterValue = "")
            .substringBefore("\n    private fun dismissSigningState")
        assertTrue(
            "Active signing cancellation must retain ownership until the signing job actually completes",
            cancelBlock.isNotEmpty() &&
                "cancellationJob.invokeOnCompletion" in cancelBlock &&
                "signingFlowOwnership.release(owner.kind, owner.requestId)" in cancelBlock &&
                cancelBlock.indexOf("cancellationJob.invokeOnCompletion") <
                cancelBlock.indexOf("cancellationJob.cancel()"),
        )
    }

    @Test
    fun mainActivityComposesExtremaduraBatchThroughTheSameOwnedSigningFlow() {
        val source = projectSource(
            "app/src/main/java/dev/junta/firmamobile/MainActivity.kt",
        )
        val coordinatorBlock = source
            .substringAfter("batchSigningCoordinator = BatchSigningCoordinator(", missingDelimiterValue = "")
            .substringBefore("\n        )")
        val batchPrepareBlock = source
            .substringAfter("private fun prepareMelillaBatchSigning(", missingDelimiterValue = "")
            .substringBefore("\n    private fun confirmSigning")

        assertTrue(
            "MainActivity must own a distinct Extremadura batch normalizer",
            "private lateinit var extremaduraBatchSigningAdapter: ExtremaduraBatchSigningAdapter" in source,
        )
        assertTrue(
            "MainActivity must create fixed Melilla and Extremadura STA protocol adapters over HTTPS transport",
            "val melillaBatchProtocolAdapter = MelillaBatchProtocolAdapter(transport = HttpsProfileHttpTransport())" in source &&
                "val extremaduraBatchProtocolAdapter = ExtremaduraBatchProtocolAdapter(transport = HttpsProfileHttpTransport())" in source,
        )
        assertTrue(
            "The batch coordinator must resolve only the two fixed STA protocol adapters and bind confirmation metadata to the active profile",
            coordinatorBlock.isNotEmpty() &&
                "adapter = melillaBatchProtocolAdapter" in coordinatorBlock &&
                "melillaBatchProtocolAdapter.id -> melillaBatchProtocolAdapter" in coordinatorBlock &&
                "extremaduraBatchProtocolAdapter.id -> extremaduraBatchProtocolAdapter" in coordinatorBlock &&
                "profileRegistry = BuiltInSiteProfiles.runtimeRegistry" in coordinatorBlock,
        )
        assertTrue(
            "MainActivity must normalize an accepted batch with the adapter fixed to its exact profile id",
            batchPrepareBlock.isNotEmpty() &&
                "when (request.profileId.value)" in batchPrepareBlock &&
                "MelillaBatchBridgeAdapter.PROFILE_ID ->" in batchPrepareBlock &&
                "ExtremaduraBatchBridgeAdapter.PROFILE_ID ->" in batchPrepareBlock &&
                "melillaBatchSigningAdapter.normalize(request)" in batchPrepareBlock &&
                "extremaduraBatchSigningAdapter.normalize(request)" in batchPrepareBlock,
        )
        assertTrue(
            "Extremadura batch requests must retain the existing single BATCH ownership gate",
            batchPrepareBlock.isNotEmpty() &&
                "signingFlowOwnership.acquire(SigningFlowKind.BATCH, requestId)" in batchPrepareBlock &&
                "batchSigningCoordinator.prepare(normalized, replySink)" in batchPrepareBlock,
        )
    }

    @Test
    fun tunnelRouteDiagnosticsRequireActiveSigningRequestOwnership() {
        val source = projectSource(
            "app/src/main/java/dev/junta/firmamobile/MainActivity.kt",
        )
        val callbackBlock = source
            .substringAfter(
                "private fun onTunnelRouteEvent(requestId: UUID, event: TunnelRouteEvent) {",
                missingDelimiterValue = "",
            )
            .substringBefore("\n    private fun activeWebViewMatches")

        assertTrue(
            "Tunnel route diagnostics must be emitted only after active request ownership is accepted",
            callbackBlock.isNotEmpty() &&
                "if (signingCoordinator.onTunnelRouteEvent(requestId, event)) {" in callbackBlock &&
                "sanitizedLogger.recordTunnelRouteEvent(event)" in callbackBlock,
        )
    }

    @Test
    fun javascriptDialogsNeverUsePlatformDefaultWindows() {
        val source = projectSource(
            "app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt",
        )

        fun callbackBlock(name: String): String = source
            .substringAfter("override fun $name(", missingDelimiterValue = "")
            .substringBefore("\n    override fun ")

        val alertBlock = callbackBlock("onJsAlert")
        val beforeUnloadBlock = callbackBlock("onJsBeforeUnload")
        val confirmBlock = callbackBlock("onJsConfirm")
        val promptBlock = callbackBlock("onJsPrompt")

        assertTrue(
            "JavaScript alert must be resolved without the platform default dialog",
            alertBlock.contains("result.confirm()") && alertBlock.contains("return true"),
        )
        assertTrue(
            "JavaScript before-unload must resume without the platform default dialog",
            beforeUnloadBlock.contains("result.confirm()") && beforeUnloadBlock.contains("return true"),
        )
        assertTrue(
            "JavaScript confirm must fail closed without the platform default dialog",
            confirmBlock.contains("result.cancel()") && confirmBlock.contains("return true"),
        )
        assertTrue(
            "JavaScript prompt must fail closed without the platform default dialog",
            promptBlock.contains("result.cancel()") && promptBlock.contains("return true"),
        )
        assertFalse(
            "The hardened chrome client must not create or delegate JavaScript dialog UI",
            listOf(
                "AlertDialog",
                "Dialog(",
                "super.onJsAlert",
                "super.onJsBeforeUnload",
                "super.onJsConfirm",
                "super.onJsPrompt",
            ).any(source::contains),
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
