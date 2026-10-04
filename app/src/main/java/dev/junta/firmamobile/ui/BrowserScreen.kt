package dev.junta.firmamobile.ui

import android.net.Uri
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.WebView
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.view.doOnAttach
import dev.junta.firmamobile.browser.BrowserPopupTransport
import dev.junta.firmamobile.browser.PopupOwnerLease
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentController
import dev.junta.firmamobile.afirma.servlet.AfirmaFallbackController
import dev.junta.firmamobile.afirma.servlet.AfirmaFallbackPrompt
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocationParser
import dev.junta.firmamobile.afirma.servlet.AfirmaServletParseResult
import dev.junta.firmamobile.afirma.servlet.NativeAfirmaOperation
import dev.junta.firmamobile.afirma.servlet.AfirmaDeferredResolver
import dev.junta.firmamobile.afirma.servlet.AfirmaRetrievalController
import dev.junta.firmamobile.afirma.servlet.AfirmaRetrievalPrompt
import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.BrowserSessionStatePolicy
import dev.junta.firmamobile.browser.BrowserNavigationCallbacks
import dev.junta.firmamobile.browser.BrowserTrustController
import dev.junta.firmamobile.browser.BrowserUrlPolicy
import dev.junta.firmamobile.browser.CarneJovenPreTlsRetryController
import dev.junta.firmamobile.browser.AuthorizedClientAuthTarget
import dev.junta.firmamobile.browser.ClientAuthGrant
import dev.junta.firmamobile.browser.InteractiveClientAuthController
import dev.junta.firmamobile.browser.InteractiveClientAuthPrompt
import dev.junta.firmamobile.browser.InteractiveClientAuthProblem
import dev.junta.firmamobile.browser.ClientCertPreferenceClearRequest
import dev.junta.firmamobile.browser.ClientCertPreferenceClearResult
import dev.junta.firmamobile.browser.ClientCertPreferenceCoordinator
import dev.junta.firmamobile.browser.CertificateSelectionBridgeRequest
import dev.junta.firmamobile.browser.CertificateSelectionReplyChannel
import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState
import dev.junta.firmamobile.browser.ClientAuthNavigationAuthorizer
import dev.junta.firmamobile.browser.ClientAuthRequestHandler
import dev.junta.firmamobile.browser.ClientAuthWebViewClient
import dev.junta.firmamobile.browser.EuskadiClientAuthPostBridgeAdapter
import dev.junta.firmamobile.browser.JuntaNavigationPolicy
import dev.junta.firmamobile.browser.JuntaWebViewClient
import dev.junta.firmamobile.browser.MiniAppletBridgeMode
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.browser.MelillaBatchBridgeRequest
import dev.junta.firmamobile.browser.MelillaBatchReplyChannel
import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.browser.SensitiveFlowInvalidator
import dev.junta.firmamobile.browser.SiteClearResult
import dev.junta.firmamobile.browser.SiteDataCleaner
import dev.junta.firmamobile.browser.TrustedJuntaWebView
import dev.junta.firmamobile.browser.WebMessageBridge
import dev.junta.firmamobile.browser.ValenciaCertificateSelectionBridgeAdapter
import dev.junta.firmamobile.browser.XuntaCertificateSelectionBridgeAdapter
import dev.junta.firmamobile.browser.WebMessageBridgeAttachment
import dev.junta.firmamobile.browser.WebViewProfileCapabilities
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.HttpMethod
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.matchesReturnUrl
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.TrustMode
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.signing.SigningUiState
import java.util.UUID
import java.net.URI
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

private class BrowserPopupState(
    val token: UUID,
    val entryOrigin: URI,
    val transport: BrowserPopupTransport,
) {
    var child: WebView? = null
}

private data class PendingCertificateSelection(
    val request: CertificateSelectionBridgeRequest,
    val reply: CertificateSelectionReplyChannel,
    val certificateFingerprint: String,
    val certificateOwner: String,
)

private data class PendingInPlaceClientAuthChallenge(
    val authorized: AuthorizedClientAuthTarget,
    val request: ClientCertRequest,
    val navigationEpoch: Long,
)

internal fun certificateSelectionFingerprint(identity: UnlockedIdentity): String? = runCatching {
    val certificateDer = identity.certificate.encoded
    try {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificateDer)
        try {
            Base64.getEncoder().encodeToString(digest)
        } finally {
            digest.fill(0)
        }
    } finally {
        certificateDer.fill(0)
    }
}.getOrNull()

internal fun shouldUseConfirmedInPlaceClientAuth(policy: ClientAuthPolicy): Boolean =
    policy.transitionMode in setOf(
        ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
        ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE,
    ) &&
        policy.requestMethod == HttpMethod.GET

internal fun dispatchConfirmedClientAuthPreparation(
    policy: ClientAuthPolicy,
    beginInPlace: () -> Unit,
    beginDedicated: () -> Unit,
) {
    if (shouldUseConfirmedInPlaceClientAuth(policy)) beginInPlace() else beginDedicated()
}

internal fun requiresFreshJuntaBrowserSession(profile: SiteProfile?): Boolean =
    profile?.clientAuthPolicy?.requestOrigins?.contains(JUNTA_SHARED_CLIENT_AUTH_ORIGIN) == true

private val JUNTA_SHARED_CLIENT_AUTH_ORIGIN =
    ExactOrigin.parse("https://ws235.juntadeandalucia.es")

internal fun certificateEligibleForSelection(
    profileId: String,
    certificate: X509Certificate,
    now: Instant = Instant.now(),
): Boolean {
    if (profileId != ValenciaCertificateSelectionBridgeAdapter.PROFILE_ID &&
        profileId != XuntaCertificateSelectionBridgeAdapter.PROFILE_ID
    ) return true
    if (!certificate.publicKey.algorithm.equals("RSA", ignoreCase = true)) return false
    if (runCatching { certificate.checkValidity(Date.from(now)) }.isFailure) return false
    if (profileId == XuntaCertificateSelectionBridgeAdapter.PROFILE_ID) return true
    val keyUsage = certificate.keyUsage ?: return false
    return keyUsage.size > 1 && keyUsage[1]
}

@Composable
fun BrowserScreen(
    profileId: ProfileId?,
    entryUrl: URI,
    certificateState: CertificateUiState.Unlocked?,
    logger: SanitizedLogger,
    signingState: SigningUiState,
    onMiniAppletRequest: (MiniAppletBridgeRequest, SigningReplySink) -> Unit,
    onMiniAppletCancel: (UUID) -> Unit,
    onConfirmSigning: (UUID) -> Unit,
    onCancelSigning: (SigningCancelReason, UUID?) -> Unit,
    onDismissSigningState: () -> Unit,
    onExitBrowser: () -> Unit,
    onOpenExternal: (Uri) -> Unit,
    onOpenOfficialAutoFirma: (Uri) -> Unit,
    onChangeCertificate: () -> Unit,
    onLockCertificate: () -> Unit,
    onClearSession: () -> Unit,
    clientCertificateIdentityProvider: () -> UnlockedIdentity?,
    clientCertPreferenceCoordinator: ClientCertPreferenceCoordinator,
    onWebViewChanged: (WebView?) -> Unit,
    onNavigationEpochChanged: (Long) -> Unit = {},
    onMelillaBatchRequest: ((MelillaBatchBridgeRequest, MelillaBatchReplyChannel) -> Unit)? = null,
    onMelillaBatchCancel: (UUID) -> Unit = {},
    onShowFileChooser: ((WebView, android.webkit.ValueCallback<Array<Uri>>, android.webkit.WebChromeClient.FileChooserParams) -> Boolean)? = null,
    certificatePanelVisible: Boolean = false,
    mayRetainExternalReturn: () -> Boolean = { false },
    consumeExternalReturn: () -> Boolean = { false },
    popupTransport: BrowserPopupTransport? = null,
    onPopupWindowClose: () -> Unit = onExitBrowser,
    onRelatedClientCertPreferenceClear: (Boolean) -> Unit = {},
    onDocumentDownload: ((WebView, android.content.Intent) -> Boolean)? = null,
    onExternalAppRequest: ((WebView, dev.junta.firmamobile.browser.ExternalAppLink) -> Boolean)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val selectedServiceId = profileId
    val currentIdentityProvider by rememberUpdatedState(clientCertificateIdentityProvider)
    val currentMayRetainExternalReturn by rememberUpdatedState(mayRetainExternalReturn)
    val currentConsumeExternalReturn by rememberUpdatedState(consumeExternalReturn)
    val currentSigningState by rememberUpdatedState(signingState)
    val currentExternalAppRequest by rememberUpdatedState(onExternalAppRequest)
    val clientCertPreferenceState by
        clientCertPreferenceCoordinator.state.collectAsState()
    val currentClientCertPreferenceState by rememberUpdatedState(clientCertPreferenceState)
    val webViewCapabilities = remember(context) { WebViewProfileCapabilities.current(context) }
    val siteDataCleaner = remember { SiteDataCleaner() }
    val sessionDataClearLease = remember { BrowserDataClearCompletionLease<ProfileId?>() }
    val globalDataClearLease = remember { BrowserDataClearCompletionLease<WebView>() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val validatedEntryUrl = remember(selectedServiceId, entryUrl) {
        checkNotNull(
            BrowserSessionStatePolicy.validatedEntryUrl(
                registry = BuiltInSiteProfiles.runtimeRegistry,
                profileId = selectedServiceId,
                entryUrl = entryUrl,
            ),
        ) { "Browser entry URL does not belong to the selected profile" }
    }
    val selectedProfile = selectedServiceId?.let(BuiltInSiteProfiles.runtimeRegistry::profile)
    val requiresClientAuthSessionPreparation = requiresFreshJuntaBrowserSession(selectedProfile)
    val trustController = remember(selectedServiceId, validatedEntryUrl) {
        BrowserTrustController(
            urlPolicy = BrowserUrlPolicy(
                registry = BuiltInSiteProfiles.runtimeRegistry,
                selectedProfileId = selectedServiceId,
            ),
            invalidator = SensitiveFlowInvalidator {},
        )
    }
    var effectiveTopLevelProfileId by remember(selectedServiceId, entryUrl) {
        mutableStateOf(
            trustController.navigate(validatedEntryUrl).activeProfileId,
        )
    }
    var webAuthnState by remember { mutableStateOf<dev.junta.firmamobile.browser.WebAuthnEngineState?>(null) }
    val webViewRef = remember { AtomicReference<WebView?>() }
    val bridgeAttachmentLease = remember {
        BrowserOwnedResourceLease<WebView, WebMessageBridgeAttachment>()
    }
    val normalClientRef = remember { AtomicReference<JuntaWebViewClient?>() }
    val dedicatedClientRef = remember { AtomicReference<ClientAuthWebViewClient?>() }
    val dedicatedWebViewRef = remember { AtomicReference<TrustedJuntaWebView?>() }
    val inPlaceClientAuthHandlerRef = remember { AtomicReference<ClientAuthRequestHandler?>() }
    val preconfirmedInPlaceClientAuthRef = remember {
        AtomicReference<AuthorizedClientAuthTarget?>()
    }
    val pendingNormalUrl = remember { AtomicReference<String?>() }
    val pendingClientAuthPostBody = remember { AtomicReference<ByteArray?>() }
    val navigationEpoch = remember { mutableLongStateOf(0L) }
    var activePopup by remember(selectedServiceId, entryUrl) { mutableStateOf<BrowserPopupState?>(null) }
    var popupClearingCertificatePreferences by remember { mutableStateOf<UUID?>(null) }
    val popupLease = remember(selectedServiceId, entryUrl) {
        PopupOwnerLease<WebView>({ owner, epoch -> webViewRef.get() === owner && navigationEpoch.longValue == epoch })
    }
    fun closePopup(token: UUID? = activePopup?.token, restoreOwner: Boolean = true) {
        val popup = activePopup?.takeIf { it.token == token } ?: return
        activePopup = null
        popupLease.release(popup.token)
        popup.transport.cancel()
        webViewRef.get()?.visibility = android.view.View.VISIBLE
        if (restoreOwner) {
            onWebViewChanged(webViewRef.get())
            onNavigationEpochChanged(navigationEpoch.longValue)
        }
    }
    LaunchedEffect(activePopup?.token) {
        val popup = activePopup ?: return@LaunchedEffect
        webViewRef.get()?.visibility = android.view.View.INVISIBLE
        delay(15_000)
        if (activePopup === popup && !popup.transport.attached) closePopup(popup.token)
    }
    DisposableEffect(popupLease) {
        onDispose { closePopup(restoreOwner = false); popupLease.invalidate() }
    }

    val navigationPolicy = remember(selectedServiceId) {
        JuntaNavigationPolicy(selectedServiceId)
    }
    val clientAuthAuthorizer = remember(selectedServiceId) {
        ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.runtimeRegistry)
    }
    val carneJovenPreTlsRetryController = remember(selectedServiceId) {
        CarneJovenPreTlsRetryController()
    }
    val clientAuthClearRequest = remember(selectedServiceId) {
        AtomicReference<ClientCertPreferenceClearRequest?>()
    }
    var clientAuthPreparing by remember(selectedServiceId) {
        mutableStateOf(requiresClientAuthSessionPreparation)
    }
    var preserveWebViewDuringClientAuthClear by remember { mutableStateOf(false) }
    var clientAuthGrant by remember { mutableStateOf<ClientAuthGrant?>(null) }
    var pendingClientAuthTarget by remember {
        mutableStateOf<AuthorizedClientAuthTarget?>(null)
    }
    var pendingClientAuthTargetEpoch by remember { mutableLongStateOf(-1L) }
    var pendingInPlaceClientAuth by remember {
        mutableStateOf<PendingInPlaceClientAuthChallenge?>(null)
    }
    var pendingRequest by remember { mutableStateOf<AfirmaRequest?>(null) }
    var pendingCertificateSelection by remember {
        mutableStateOf<PendingCertificateSelection?>(null)
    }
    var blockedReason by remember { mutableStateOf<NavigationBlockReason?>(null) }
    var browserError by remember { mutableStateOf<BrowserErrorCode?>(null) }
    var compatibilityError by remember { mutableStateOf(false) }
    var siteClearResult by remember { mutableStateOf<SiteClearResult?>(null) }
    var globalClearResult by remember { mutableStateOf<Boolean?>(null) }
    var currentUrl by remember(profileId, entryUrl) {
        mutableStateOf(validatedEntryUrl)
    }
    var pageLoading by remember { mutableStateOf(false) }
    var webViewRecreationEpoch by remember { mutableIntStateOf(0) }
    val nativeAfirmaScope = rememberCoroutineScope()
    var nativeAfirmaPrompt by remember(selectedServiceId, validatedEntryUrl) { mutableStateOf<AfirmaConsentPrompt?>(null) }
    var nativeFallbackPrompt by remember(selectedServiceId, validatedEntryUrl) { mutableStateOf<AfirmaFallbackPrompt?>(null) }
    val nativeFallback = remember(selectedServiceId, validatedEntryUrl) {
        AfirmaFallbackController<WebView>(
            isCurrent = { owner, epoch -> webViewRef.get() === owner && navigationEpoch.longValue == epoch },
            canRespond = { activePopup == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
            onPrompt = { nativeFallbackPrompt = it },
        )
    }
    LaunchedEffect(nativeFallback, nativeFallbackPrompt?.token) {
        while (nativeFallbackPrompt != null) { delay(1_000); nativeFallback.tick() }
    }
    val nativeAfirma = remember(selectedServiceId, validatedEntryUrl) {
        AfirmaConsentController<WebView>(
            scope = nativeAfirmaScope,
            isCurrent = { owner, epoch -> webViewRef.get() === owner && navigationEpoch.longValue == epoch },
            identityProvider = { currentIdentityProvider() },
            canRespond = { activePopup == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
            onPrompt = { nativeAfirmaPrompt = it },
        )
    }
    LaunchedEffect(nativeAfirma, certificateState, certificatePanelVisible) { nativeAfirma.refreshIdentity() }
    DisposableEffect(nativeAfirma, nativeFallback) {
        onDispose { nativeAfirma.close(); nativeFallback.invalidate() }
    }
    LaunchedEffect(nativeAfirma, nativeAfirmaPrompt?.token, nativeAfirmaPrompt?.phase) {
        while (nativeAfirmaPrompt != null && nativeAfirmaPrompt?.phase != AfirmaConsentPhase.FINISHED) {
            delay(1_000)
            nativeAfirma.tick()
        }
    }
    var interactivePrompt by remember { mutableStateOf<InteractiveClientAuthPrompt?>(null) }
    var interactiveProblem by remember { mutableStateOf<InteractiveClientAuthProblem?>(null) }
    var preserveInteractiveWebViewDuringClear by remember { mutableStateOf(false) }
    val interactiveClientAuth = remember(selectedServiceId, validatedEntryUrl, clientCertPreferenceCoordinator) {
        InteractiveClientAuthController<WebView>(
            isCurrent = { owner, epoch -> webViewRef.get() === owner && navigationEpoch.longValue == epoch },
            identityProvider = { currentIdentityProvider() },
            canRespond = {
                activePopup == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    clientCertPreferenceCoordinator.state.value == ClientCertPreferenceBarrierState.IDLE
            },
            clearClientCertPreferences = {
                onRelatedClientCertPreferenceClear(true)
                preserveInteractiveWebViewDuringClear = true
                clientCertPreferenceCoordinator.requestClear { _, result ->
                    mainHandler.post {
                        preserveInteractiveWebViewDuringClear = false
                        onRelatedClientCertPreferenceClear(false)
                        if (result == ClientCertPreferenceClearResult.FAILED) {
                            browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES
                        }
                    }
                }
            },
            onPrompt = { interactivePrompt = it },
            onProblem = { interactiveProblem = it },
        )
    }
    LaunchedEffect(certificateState, certificatePanelVisible) {
        interactiveClientAuth.refreshIdentity()
    }
    LaunchedEffect(clientCertPreferenceState) {
        // Another process-scoped clear may supersede this callback. Once the
        // platform settles, an old preservation flag cannot escape its barrier.
        if (clientCertPreferenceState != ClientCertPreferenceBarrierState.CLEARING) {
            popupClearingCertificatePreferences = null
            preserveInteractiveWebViewDuringClear = false
        }
    }
    val clientCertPreferenceBlocked =
        !preserveWebViewDuringClientAuthClear && !preserveInteractiveWebViewDuringClear &&
            activePopup == null && popupClearingCertificatePreferences == null &&
            (clientCertPreferenceState != ClientCertPreferenceBarrierState.IDLE || clientAuthPreparing)

    var nativeRetrievalPrompt by remember(selectedServiceId, validatedEntryUrl) { mutableStateOf<AfirmaRetrievalPrompt?>(null) }
    val nativeRetrieval = remember(selectedServiceId, validatedEntryUrl, nativeAfirma) {
        AfirmaRetrievalController<WebView>(
            scope = nativeAfirmaScope,
            resolver = AfirmaDeferredResolver(),
            isCurrent = { owner, epoch -> webViewRef.get() === owner && navigationEpoch.longValue == epoch },
            canRespond = { activePopup == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
            onPrompt = { nativeRetrievalPrompt = it },
            onPrepared = { owner, epoch, operation ->
                val busy = pendingInPlaceClientAuth != null || pendingClientAuthTarget != null ||
                    pendingCertificateSelection != null || pendingRequest != null || interactivePrompt != null ||
                    nativeAfirma.hasPending || nativeFallback.hasPending || clientAuthPreparing ||
                    currentSigningState !is SigningUiState.Idle
                if (busy) { operation.close(); false } else nativeAfirma.offer(owner, epoch, operation)
            },
        )
    }
    DisposableEffect(nativeRetrieval) { onDispose { nativeRetrieval.close() } }
    LaunchedEffect(nativeRetrieval, nativeRetrievalPrompt?.token, nativeRetrievalPrompt?.loading) {
        while (nativeRetrievalPrompt?.loading == true) { delay(1_000); nativeRetrieval.tick() }
    }

    fun advanceNavigationEpoch() {
        closePopup()
        nativeRetrieval.invalidate()
        nativeAfirma.invalidate()
        nativeFallback.invalidate()
        interactiveClientAuth.cancelPending()
        bridgeAttachmentLease.current()?.abandonMiniAppletRequests()
        check(navigationEpoch.longValue != Long.MAX_VALUE)
        navigationEpoch.longValue++
        onNavigationEpochChanged(navigationEpoch.longValue)
    }

    fun cancelPendingCertificateSelection(code: dev.junta.firmamobile.signing.SigningErrorCode) {
        pendingCertificateSelection?.reply?.failure(code)
        pendingCertificateSelection = null
    }

    fun prepareCertificateSelection(
        request: CertificateSelectionBridgeRequest,
        reply: CertificateSelectionReplyChannel,
    ) {
        if (pendingCertificateSelection != null ||
            request.context.profileId != selectedServiceId?.value ||
            request.context.navigationEpoch != navigationEpoch.longValue
        ) {
            reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.PROTOCOL_FAILED)
            return
        }
        val identity = clientCertificateIdentityProvider()
        if (identity == null) {
            reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.CERTIFICATE_LOCKED)
            return
        }
        if (!certificateEligibleForSelection(request.context.profileId, identity.certificate)) {
            reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.PROTOCOL_FAILED)
            return
        }
        val fingerprint = certificateSelectionFingerprint(identity)
        if (fingerprint == null) {
            reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.PROTOCOL_FAILED)
            return
        }
        pendingCertificateSelection = PendingCertificateSelection(
            request = request,
            reply = reply,
            certificateFingerprint = fingerprint,
            certificateOwner = identity.summary.ownerName,
        )
    }

    fun cancelClientAuthClearCallback() {
        clientAuthClearRequest.getAndSet(null)?.let(
            clientCertPreferenceCoordinator::cancelCallback,
        )
        clientAuthPreparing = false
    }

    fun requestProcessClientCertPreferenceClear() {
        // A generic-choice revocation already owns an in-flight process clear.
        if (!preserveInteractiveWebViewDuringClear) {
            // Closing an unprofiled child also revokes process-wide choices.
            // Notify the retained parent BEFORE the barrier enters CLEARING;
            // otherwise it interprets this as an unrelated reset and rebuilds
            // its original WebView, losing the form we deliberately retained.
            onRelatedClientCertPreferenceClear(true)
            clientCertPreferenceCoordinator.requestClear { _, _ ->
                mainHandler.post { onRelatedClientCertPreferenceClear(false) }
            }
        }
    }

    fun cancelPendingInPlaceClientAuth() {
        pendingInPlaceClientAuth?.request?.ignore()
        pendingInPlaceClientAuth = null
    }

    fun abandonClientAuth() {
        interactiveClientAuth.cancelPending()
        interactiveClientAuth.revokeCachedChoice()
        carneJovenPreTlsRetryController.reset()
        cancelClientAuthClearCallback()
        preserveWebViewDuringClientAuthClear = false
        preconfirmedInPlaceClientAuthRef.set(null)
        pendingClientAuthPostBody.getAndSet(null)?.fill(0)
        pendingClientAuthTarget = null
        cancelPendingInPlaceClientAuth()
        clientAuthGrant = null
        inPlaceClientAuthHandlerRef.getAndSet(null)?.abandon()
        val dedicatedClient = dedicatedClientRef.getAndSet(null)
        dedicatedWebViewRef.set(null)
        clientAuthAuthorizer.invalidate()
        if (dedicatedClient != null) {
            dedicatedClient.abandon()
        } else {
            requestProcessClientCertPreferenceClear()
        }
    }

    fun recoverClientAuthPreparationFailure(requestAnotherClear: Boolean) {
        carneJovenPreTlsRetryController.reset()
        cancelClientAuthClearCallback()
        pendingClientAuthPostBody.getAndSet(null)?.fill(0)
        pendingClientAuthTarget = null
        clientAuthGrant = null
        val dedicatedClient = dedicatedClientRef.getAndSet(null)
        dedicatedWebViewRef.set(null)
        clientAuthAuthorizer.invalidate()
        if (dedicatedClient != null) {
            dedicatedClient.abandon()
        } else if (requestAnotherClear) {
            requestProcessClientCertPreferenceClear()
        }
        pendingNormalUrl.set(validatedEntryUrl)
        bridgeAttachmentLease.close()
        advanceNavigationEpoch()
        onCancelSigning(SigningCancelReason.NAVIGATION, null)
        browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES
        pageLoading = false
        webViewRecreationEpoch++
    }

    fun finishConfirmedInPlaceClientAuthAfterNavigation(handler: ClientAuthRequestHandler) {
        if (!inPlaceClientAuthHandlerRef.compareAndSet(handler, null)) return
        if (!handler.delegatePreferenceCleanup()) {
            handler.abandon()
            return
        }
        preserveWebViewDuringClientAuthClear = true
        val request = clientCertPreferenceCoordinator.requestClear { completedRequest, result ->
            mainHandler.post {
                if (!clientAuthClearRequest.compareAndSet(completedRequest, null)) return@post
                preserveWebViewDuringClientAuthClear = false
                if (result == ClientCertPreferenceClearResult.FAILED) {
                    browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES
                    pageLoading = false
                }
            }
        }
        clientAuthClearRequest.set(request)
    }

    fun beginConfirmedInPlaceClientAuthPreparation(
        authorized: AuthorizedClientAuthTarget,
    ) {
        cancelClientAuthClearCallback()
        val webView = webViewRef.get()
        val client = normalClientRef.get()
        if (webView == null || client == null) {
            recoverClientAuthPreparationFailure(requestAnotherClear = true)
            return
        }
        browserError = null
        pageLoading = true
        clientAuthPreparing = true
        preserveWebViewDuringClientAuthClear = true
        webView.isEnabled = false
        val expectedEpoch = navigationEpoch.longValue
        val request = clientCertPreferenceCoordinator.requestClear { completedRequest, result ->
            mainHandler.post {
                if (!clientAuthClearRequest.compareAndSet(completedRequest, null)) return@post
                clientAuthPreparing = false
                preserveWebViewDuringClientAuthClear = false
                if (webViewRef.get() === webView) webView.isEnabled = true
                when (result) {
                    ClientCertPreferenceClearResult.CLEARED -> {
                        val stillOwned = webViewRef.get() === webView && normalClientRef.get() === client
                        if (!stillOwned ||
                            authorized.profileId != effectiveTopLevelProfileId ||
                            expectedEpoch != navigationEpoch.longValue ||
                            authorized.isExpiredOrInvalid() ||
                            !client.armConfirmedInPlaceClientAuth(authorized, navigationEpoch.longValue)
                        ) {
                            recoverClientAuthPreparationFailure(requestAnotherClear = true)
                            return@post
                        }
                        preconfirmedInPlaceClientAuthRef.set(authorized)
                        webView.loadUrl(authorized.source.toASCIIString())
                    }

                    ClientCertPreferenceClearResult.FAILED -> {
                        recoverClientAuthPreparationFailure(requestAnotherClear = false)
                    }
                }
            }
        }
        clientAuthClearRequest.set(request)
    }

    fun beginClientAuthPreparation(grant: ClientAuthGrant) {
        cancelClientAuthClearCallback()
        clientAuthGrant = null
        browserError = null
        pageLoading = true
        clientAuthPreparing = true
        val request = clientCertPreferenceCoordinator.requestClear { completedRequest, result ->
            mainHandler.post {
                if (!clientAuthClearRequest.compareAndSet(completedRequest, null)) return@post
                clientAuthPreparing = false
                when (result) {
                    ClientCertPreferenceClearResult.CLEARED -> {
                        if (grant.authorized.profileId != effectiveTopLevelProfileId ||
                            grant.navigationEpoch != navigationEpoch.longValue ||
                            grant.authorized.isExpiredOrInvalid()
                        ) {
                            recoverClientAuthPreparationFailure(requestAnotherClear = true)
                            return@post
                        }
                        clientAuthGrant = grant
                    }

                    ClientCertPreferenceClearResult.FAILED -> {
                        recoverClientAuthPreparationFailure(requestAnotherClear = false)
                    }
                }
            }
        }
        clientAuthClearRequest.set(request)
    }

    fun beginClientCertPreferenceRecovery() {
        carneJovenPreTlsRetryController.reset()
        cancelClientAuthClearCallback()
        pendingClientAuthPostBody.getAndSet(null)?.fill(0)
        pendingClientAuthTarget = null
        cancelPendingInPlaceClientAuth()
        clientAuthGrant = null
        pendingNormalUrl.set(validatedEntryUrl)
        browserError = null
        pageLoading = true
        clientAuthPreparing = true
        val profile = selectedProfile
        if (profile?.clientAuthPolicy == null) {
            clientAuthPreparing = false
            return
        }
        val sessionClearRequest = sessionDataClearLease.begin(selectedServiceId)
        siteDataCleaner.clearProfileSession(profile, webViewCapabilities) { sessionCleared ->
            mainHandler.post {
                if (!sessionDataClearLease.consume(sessionClearRequest)) return@post
                if (!sessionCleared) {
                    clientAuthPreparing = false
                    browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES
                    pageLoading = false
                    return@post
                }
                val request = clientCertPreferenceCoordinator.requestClear { completedRequest, result ->
                    mainHandler.post {
                        if (!clientAuthClearRequest.compareAndSet(completedRequest, null)) return@post
                        clientAuthPreparing = false
                        when (result) {
                            ClientCertPreferenceClearResult.CLEARED -> {
                                browserError = null
                                pageLoading = true
                                webViewRecreationEpoch++
                            }

                            ClientCertPreferenceClearResult.FAILED -> {
                                browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES
                                pageLoading = false
                            }
                        }
                    }
                }
                clientAuthClearRequest.set(request)
            }
        }
    }

    LaunchedEffect(selectedServiceId, requiresClientAuthSessionPreparation) {
        if (requiresClientAuthSessionPreparation) beginClientCertPreferenceRecovery()
    }

    val handleAfirmaRequest: (AfirmaRequest) -> Unit = { request ->
        // URI notifications have no per-call reply channel. A second observer
        // must not replace consent or consume a concurrently loaded request.
        val busy = activePopup != null || nativeAfirma.hasPending || nativeFallback.hasPending || nativeRetrieval.hasPending ||
            pendingRequest != null || pendingCertificateSelection != null || pendingInPlaceClientAuth != null ||
            pendingClientAuthTarget != null || interactivePrompt != null || clientAuthPreparing ||
            currentSigningState !is SigningUiState.Idle
        if (!busy) pendingRequest = request
    }
    val callbacks = remember(
        selectedServiceId,
        entryUrl,
        onOpenExternal,
        onCancelSigning,
        onWebViewChanged,
        clientCertPreferenceCoordinator,
    ) {
        object : BrowserNavigationCallbacks {
            override fun openExternal(uri: Uri) {
                pendingClientAuthTarget = null
                clientAuthGrant = null
                pendingRequest = null
                abandonClientAuth()
                advanceNavigationEpoch()
                onCancelSigning(SigningCancelReason.NAVIGATION, null)
                onOpenExternal(uri)
            }

            override fun openOfficialAutoFirma(uri: Uri) {
                pendingClientAuthTarget = null
                clientAuthGrant = null
                pendingRequest = null
                abandonClientAuth()
                advanceNavigationEpoch()
                onCancelSigning(SigningCancelReason.NAVIGATION, null)
                onOpenOfficialAutoFirma(uri)
            }

            override fun onAfirmaRequest(request: AfirmaRequest) {
                handleAfirmaRequest(request)
            }

            override fun onNavigationBlocked(reason: NavigationBlockReason) {
                blockedReason = reason
            }

            override fun onBrowserError(error: BrowserErrorCode) {
                nativeRetrieval.invalidate()
                nativeAfirma.invalidate()
                nativeFallback.invalidate()
                interactiveClientAuth.cancelPending()
                if (error == BrowserErrorCode.SSL_ERROR || error == BrowserErrorCode.SAFE_BROWSING) {
                    interactiveClientAuth.revokeCachedChoice()
                }
                browserError = error
                pageLoading = false
            }

            override fun onRenderProcessGone(view: WebView) {
                if (!webViewRef.compareAndSet(view, null)) return
                onWebViewChanged(null)
                effectiveTopLevelProfileId = null
                pendingClientAuthTarget = null
                clientAuthGrant = null
                pendingRequest = null
                bridgeAttachmentLease.close()
                abandonClientAuth()
                advanceNavigationEpoch()
                onCancelSigning(SigningCancelReason.NAVIGATION, null)
                browserError = BrowserErrorCode.RENDER_PROCESS_GONE
                pageLoading = false
                webViewRecreationEpoch++
            }

            override fun onTopLevelNavigationStarted(url: String) {
                val inPlaceHandler = inPlaceClientAuthHandlerRef.get()
                val continuingConfirmedClientAuth =
                    inPlaceHandler?.hasProceeded() == true && inPlaceHandler.isAuthFlowUrl(url)
                if (continuingConfirmedClientAuth) {
                    pageLoading = true
                    browserError = null
                    blockedReason = null
                    return
                }
                inPlaceClientAuthHandlerRef.getAndSet(null)?.abandon()
                cancelPendingInPlaceClientAuth()
                pageLoading = true
                browserError = null
                blockedReason = null
                pendingClientAuthPostBody.getAndSet(null)?.fill(0)
                pendingClientAuthTarget = null
                val previousEffectiveProfileId = effectiveTopLevelProfileId
                effectiveTopLevelProfileId = trustController.navigate(url).activeProfileId
                if (previousEffectiveProfileId != effectiveTopLevelProfileId) {
                    clientAuthGrant = null
                    pendingRequest = null
                    abandonClientAuth()
                }
                advanceNavigationEpoch()
                onCancelSigning(SigningCancelReason.NAVIGATION, null)
            }

            override fun onTopLevelUrlChanged(url: String) {
                currentUrl = safeBrowserDisplayUrl(url)
            }

            override fun onTopLevelPageFinished(url: String) {
                val handler = inPlaceClientAuthHandlerRef.get() ?: return
                if (handler.hasProceeded() && !handler.isAuthFlowUrl(url)) {
                    finishConfirmedInPlaceClientAuthAfterNavigation(handler)
                }
            }
        }
    }

    fun goBack() {
        if (clientAuthGrant != null) {
            pendingNormalUrl.set(validatedEntryUrl)
            clientAuthGrant = null
            abandonClientAuth()
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.NAVIGATION, null)
            return
        }
        pendingClientAuthPostBody.getAndSet(null)?.fill(0)
        advanceNavigationEpoch()
        onCancelSigning(SigningCancelReason.NAVIGATION, null)
        val webView = webViewRef.get()
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            abandonClientAuth()
            pendingClientAuthTarget = null
            onExitBrowser()
        }
    }

    BackHandler(onBack = ::goBack)
    DisposableEffect(selectedServiceId, onCancelSigning, clientCertPreferenceCoordinator) {
        onDispose {
            nativeRetrieval.invalidate()
            nativeAfirma.invalidate()
            nativeFallback.invalidate()
            interactiveClientAuth.close()
            sessionDataClearLease.invalidate()
            globalDataClearLease.invalidate()
            onCancelSigning(SigningCancelReason.BACKGROUND, null)
            bridgeAttachmentLease.close()
            // AndroidView.onRelease is the sole native-view owner. Disposing
            // Compose state must not also destroy an attached child WebView.
            webViewRef.getAndSet(null)?.let {
                onWebViewChanged(null)
            }
            abandonClientAuth()
        }
    }

    DisposableEffect(selectedServiceId, lifecycleOwner, clientCertPreferenceCoordinator, nativeRetrieval, nativeAfirma) {
        var wasBackgrounded = false
        var retainedLegacyState = false
        var retainedFingerprint: String? = null
        fun revokeLegacyBackgroundFlow() {
            pendingClientAuthTarget = null
            clientAuthGrant = null
            pendingNormalUrl.set(validatedEntryUrl)
            abandonClientAuth()
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.BACKGROUND, null)
            webViewRecreationEpoch++
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                wasBackgrounded = true
                val expectedReturn = currentMayRetainExternalReturn()
                if (!expectedReturn) closePopup()
                nativeRetrieval.onBackground()
                nativeAfirma.onBackground(expectedReturn)
                interactiveClientAuth.onBackground(expectedReturn)
                val hadClientAuthState = pendingClientAuthTarget != null ||
                    pendingInPlaceClientAuth != null ||
                    inPlaceClientAuthHandlerRef.get() != null ||
                    clientAuthGrant != null ||
                    clientAuthPreparing ||
                    currentClientCertPreferenceState != ClientCertPreferenceBarrierState.IDLE
                retainedLegacyState = hadClientAuthState && expectedReturn
                retainedFingerprint = if (retainedLegacyState) {
                    currentIdentityProvider()?.let(::certificateSelectionFingerprint)
                } else null
                if (hadClientAuthState && !expectedReturn && !preserveInteractiveWebViewDuringClear) {
                    revokeLegacyBackgroundFlow()
                }
            } else if (event == Lifecycle.Event.ON_START && wasBackgrounded) {
                wasBackgrounded = false
                // Only the visible child consumes the single-use return. The
                // retained parent merely observes validity; consuming here
                // would make the same return appear expired to its child.
                val returnStillValid = if (activePopup != null) currentMayRetainExternalReturn()
                    else currentConsumeExternalReturn()
                nativeRetrieval.onForeground()
                nativeAfirma.onForeground(returnStillValid)
                interactiveClientAuth.onForeground(returnStillValid)
                val sameIdentity = retainedFingerprint == currentIdentityProvider()?.let(::certificateSelectionFingerprint)
                val grantLive = clientAuthGrant?.authorized?.isExpiredOrInvalid() != true &&
                    pendingInPlaceClientAuth?.authorized?.isExpiredOrInvalid() != true &&
                    pendingClientAuthTarget?.isExpiredOrInvalid() != true
                if (retainedLegacyState && (!returnStillValid || !sameIdentity || !grantLive)) {
                    revokeLegacyBackgroundFlow()
                }
                retainedLegacyState = false
                retainedFingerprint = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val effectiveProfile = effectiveTopLevelProfileId?.let(
        BuiltInSiteProfiles.runtimeRegistry::profile,
    )
    val currentResolution = effectiveTopLevelProfileId?.let { activeProfileId ->
        runCatching {
            BuiltInSiteProfiles.runtimeRegistry.resolveForProfile(
                activeProfileId,
                java.net.URI(currentUrl),
            )
        }.getOrNull()
    }
    val resolvedProfileId = currentResolution?.profile?.profileId
    val isEffectiveProfileOrigin =
        resolvedProfileId != null &&
            effectiveTopLevelProfileId != null &&
            resolvedProfileId == effectiveTopLevelProfileId
    val trustLabel = when {
        clientAuthGrant != null ||
            (isEffectiveProfileOrigin && effectiveProfile?.clientAuthPolicy != null) ->
            stringResource(R.string.browser_trust_client_auth)
        isEffectiveProfileOrigin &&
            currentResolution.origin in
                (effectiveProfile?.initiatorOrigins ?: emptySet()) &&
            effectiveProfile?.capabilities?.contains(
                dev.junta.firmamobile.profile.Capability.SIGN,
            ) == true ->
            stringResource(R.string.browser_trust_signing)
        isEffectiveProfileOrigin -> stringResource(R.string.browser_trust_browse)
        else -> stringResource(R.string.browser_trust_browse_only)
    }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().then(
        if (activePopup != null) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier,
    )) {
    BrowserLayout(
        publicBrowsing = selectedServiceId == null,
        webAuthnState = webAuthnState,
        currentUrl = currentUrl,
        profileName = effectiveProfile?.displayName
            ?: selectedProfile?.displayName
            ?: stringResource(R.string.app_name),
        trustLabel = trustLabel,
        certificateOwner = certificateState?.summary?.ownerName ?: stringResource(R.string.browser_no_unlocked_certificate),
        certificateAvailable = certificateState != null,
        onOpenInBrowser = { webViewRef.get()?.url?.let { onOpenExternal(Uri.parse(it)) } },
        onBack = ::goBack,
        onHome = {
            clientAuthGrant = null
            pendingClientAuthTarget = null
            abandonClientAuth()
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.NAVIGATION, null)
            onExitBrowser()
        },
        onReload = {
            val leavingClientAuth = clientAuthGrant != null
            if (leavingClientAuth) {
                pendingNormalUrl.set(validatedEntryUrl)
                clientAuthGrant = null
                abandonClientAuth()
            }
            pendingClientAuthPostBody.getAndSet(null)?.fill(0)
            pendingClientAuthTarget = null
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.RELOAD, null)
            browserError = null
            if (!leavingClientAuth) webViewRef.get()?.reload()
        },
        onChangeCertificate = {
            interactiveClientAuth.cancelPending()
            interactiveClientAuth.revokeCachedChoice()
            cancelPendingCertificateSelection(
                dev.junta.firmamobile.signing.SigningErrorCode.CERTIFICATE_LOCKED,
            )
            if (clientAuthGrant != null || inPlaceClientAuthHandlerRef.get() != null ||
                pendingClientAuthTarget != null || pendingInPlaceClientAuth != null
            ) {
                clientAuthGrant = null
                abandonClientAuth()
            }
            onCancelSigning(SigningCancelReason.CERTIFICATE_LOCKED, null)
            onChangeCertificate()
        },
        onLockCertificate = {
            cancelPendingCertificateSelection(
                dev.junta.firmamobile.signing.SigningErrorCode.CERTIFICATE_LOCKED,
            )
            clientAuthGrant = null
            abandonClientAuth()
            onCancelSigning(SigningCancelReason.CERTIFICATE_LOCKED, null)
            onLockCertificate()
        },
        onClearCurrentSite = {
            clientAuthGrant = null
            abandonClientAuth()
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.NAVIGATION, null)
            pendingRequest = null
            globalClearResult = null
            val webView = webViewRef.get()
            webView?.apply {
                stopLoading()
                clearHistory()
                clearFormData()
            }
            val exactUrl = webView?.url ?: currentUrl
            siteClearResult = runCatching {
                siteDataCleaner.clearOrigin(URI(exactUrl), webViewCapabilities)
            }.getOrDefault(SiteClearResult.FAILED)
            webView?.loadUrl(validatedEntryUrl)
        },
        onClearSession = {
            cancelPendingCertificateSelection(
                dev.junta.firmamobile.signing.SigningErrorCode.CERTIFICATE_LOCKED,
            )
            clientAuthGrant = null
            abandonClientAuth()
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.CERTIFICATE_LOCKED, null)
            pendingRequest = null
            val webView = webViewRef.get()
            webView?.apply {
                stopLoading()
                clearHistory()
                clearFormData()
            }
            val profile = selectedProfile
            if (profile == null) {
                // No fabricated profile: clear only the displayed public site's
                // data and report any platform cookie limitation accurately.
                siteClearResult = runCatching {
                    siteDataCleaner.clearOrigin(URI(currentUrl), webViewCapabilities)
                }.getOrDefault(SiteClearResult.FAILED)
                val clearRequest = sessionDataClearLease.begin(null)
                clientCertPreferenceCoordinator.requestClear { _, result ->
                    mainHandler.post {
                        if (!sessionDataClearLease.consume(clearRequest)) return@post
                        if (result == ClientCertPreferenceClearResult.CLEARED) onClearSession()
                        else browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES
                    }
                }
            } else {
                clientAuthPreparing = true
                val sessionClearRequest = sessionDataClearLease.begin(selectedServiceId)
                siteDataCleaner.clearProfileSession(profile, webViewCapabilities) { sessionCleared ->
                    mainHandler.post {
                        if (!sessionDataClearLease.consume(sessionClearRequest)) return@post
                        if (!sessionCleared) {
                            clientAuthPreparing = false
                            browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES
                            pageLoading = false
                            return@post
                        }
                        val request = clientCertPreferenceCoordinator.requestClear { completedRequest, result ->
                            mainHandler.post {
                                if (!clientAuthClearRequest.compareAndSet(completedRequest, null)) return@post
                                clientAuthPreparing = false
                                if (result == ClientCertPreferenceClearResult.CLEARED) {
                                    onClearSession()
                                } else {
                                    browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES
                                    pageLoading = false
                                }
                            }
                        }
                        clientAuthClearRequest.set(request)
                    }
                }
            }
        },
        onDeleteAllBrowserData = {
            clientAuthGrant = null
            abandonClientAuth()
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.NAVIGATION, null)
            pendingRequest = null
            siteClearResult = null
            val webView = webViewRef.get()
            if (webView == null) {
                globalDataClearLease.invalidate()
                globalClearResult = false
            } else {
                val clearRequest = globalDataClearLease.begin(webView)
                webView.apply {
                    stopLoading()
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                }
                siteDataCleaner.clearAllConfirmed { cleared ->
                    mainHandler.post {
                        if (!globalDataClearLease.consume(clearRequest)) return@post
                        globalClearResult = cleared
                        if (cleared && webViewRef.get() === clearRequest.owner) {
                            clearRequest.owner.loadUrl(validatedEntryUrl)
                        }
                    }
                }
            }
        },
    ) { modifier ->
        Column(modifier = modifier) {
            val dataNotice = browserDataNotice(siteClearResult, globalClearResult)
            val notice = browserNotice(
                compatibilityError = compatibilityError,
                blockedReason = blockedReason,
                browserError = browserError,
                clientCertPreferenceState = clientCertPreferenceState,
                dataNotice = dataNotice,
            )
            notice?.let { message ->
                BrowserNoticeBanner(
                    message = message,
                    liveRegionMode = browserNoticeLiveRegionMode(
                        compatibilityError = compatibilityError,
                        blockedReason = blockedReason,
                        browserError = browserError,
                        clientCertPreferenceState = clientCertPreferenceState,
                        siteClearResult = siteClearResult,
                        globalClearResult = globalClearResult,
                    ),
                    onRetry = when {
                        clientCertPreferenceState == ClientCertPreferenceBarrierState.FAILED ||
                            browserError == BrowserErrorCode.CLIENT_CERT_PREFERENCES -> {
                            { beginClientCertPreferenceRecovery() }
                        }

                        browserError != null -> {
                            {
                                onCancelSigning(SigningCancelReason.RELOAD, null)
                                browserError = null
                                pageLoading = true
                                if (clientAuthGrant != null) {
                                    val activeStartUrl = selectedServiceId?.let(BuiltInSiteProfiles.runtimeRegistry::profile)
                                        ?.startUrl
                                        ?.toASCIIString()
                                        ?: validatedEntryUrl
                                    pendingNormalUrl.set(activeStartUrl)
                                    clientAuthGrant = null
                                    abandonClientAuth()
                                    advanceNavigationEpoch()
                                } else {
                                    webViewRef.get()?.reload()
                                }
                            }
                        }

                        else -> null
                    },
                )
            }
            BrowserLoadingIndicator(
                visible = clientCertPreferenceState == ClientCertPreferenceBarrierState.CLEARING ||
                    clientAuthPreparing || pageLoading,
            )
            if (!clientCertPreferenceBlocked) key(
                clientAuthGrant != null,
                webViewRecreationEpoch,
            ) {
                AndroidView(
                    factory = {
                    TrustedJuntaWebView(context).also { webView ->
                        webView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        webViewRef.set(webView)
                        webAuthnState = webView.webAuthnEngineState
                        onShowFileChooser?.let(webView::setFileChooserListener)
                        webView.setDocumentDownloadLauncher(
                            canRespond = {
                                activePopup == null && webViewRef.get() === webView &&
                                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                                    !certificatePanelVisible && currentSigningState is SigningUiState.Idle &&
                                    !nativeAfirma.hasPending && !nativeFallback.hasPending && !nativeRetrieval.hasPending &&
                                    interactivePrompt == null && pendingRequest == null && pendingCertificateSelection == null &&
                                    pendingInPlaceClientAuth == null && pendingClientAuthTarget == null &&
                                    !clientAuthPreparing && clientAuthGrant == null &&
                                    currentClientCertPreferenceState == ClientCertPreferenceBarrierState.IDLE
                            },
                            launch = onDocumentDownload,
                        )
                        webView.setPopupListeners(
                            create = popupRequest@{ owner, _, userGesture, message ->
                                if (popupTransport != null || !userGesture || owner !== webViewRef.get() ||
                                    !owner.hasWindowFocus() || !owner.isShown || certificatePanelVisible ||
                                    !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                                    activePopup != null || nativeAfirma.hasPending || nativeFallback.hasPending ||
                                    nativeRetrieval.hasPending || interactivePrompt != null ||
                                    pendingCertificateSelection != null || pendingRequest != null ||
                                    pendingInPlaceClientAuth != null || pendingClientAuthTarget != null ||
                                    clientAuthPreparing || clientAuthGrant != null ||
                                    currentClientCertPreferenceState != ClientCertPreferenceBarrierState.IDLE ||
                                    currentSigningState !is SigningUiState.Idle
                                ) return@popupRequest false
                                val page = dev.junta.firmamobile.browser.PublicBrowserAddress.parse(owner.url ?: "")
                                    ?: return@popupRequest false
                                val token = popupLease.reserve(owner, navigationEpoch.longValue) ?: return@popupRequest false
                                val transfer = BrowserPopupTransport.create(owner, message) {
                                    activePopup?.token == token && popupLease.isCurrent(token) &&
                                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                                }
                                if (transfer == null) { popupLease.release(token); return@popupRequest false }
                                activePopup = BrowserPopupState(token, URI("https://${page.host}"), transfer)
                                true
                            },
                            close = { window -> if (popupTransport != null && webViewRef.get() === window) onPopupWindowClose() },
                        )
                        webView.setPageProgressListener { progress ->
                            webView.post {
                                if (webViewRef.get() === webView) pageLoading = browserPageLoading(progress)
                            }
                        }
                        val tlsGrant = clientAuthGrant
                        if (tlsGrant == null) {
                            onWebViewChanged(browserWebViewForSigning(webView, dedicated = false))
                            val client = JuntaWebViewClient(
                                callbacks = callbacks,
                                logger = logger,
                                navigationPolicy = navigationPolicy,
                                clientAuthAuthorizer = clientAuthAuthorizer,
                                activeProfileId = { effectiveTopLevelProfileId },
                                currentNavigationEpoch = { navigationEpoch.longValue },
                                isActiveWebView = { candidate -> webViewRef.get() === candidate },
                                onExternalAppRequest = { owner, target ->
                                    val busy = activePopup != null || certificatePanelVisible ||
                                        pendingInPlaceClientAuth != null || pendingClientAuthTarget != null ||
                                        pendingCertificateSelection != null || pendingRequest != null ||
                                        interactivePrompt != null || nativeAfirma.hasPending || nativeFallback.hasPending ||
                                        nativeRetrieval.hasPending || clientAuthPreparing || clientAuthGrant != null ||
                                        currentSigningState !is SigningUiState.Idle ||
                                        clientCertPreferenceCoordinator.state.value != ClientCertPreferenceBarrierState.IDLE
                                    !busy && owner === webViewRef.get() &&
                                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                                        currentExternalAppRequest?.invoke(owner, target) == true
                                },
                                onNativeAfirmaInvocation = { owner, rawUri, page ->
                                    val busy = activePopup != null || pendingInPlaceClientAuth != null || pendingClientAuthTarget != null ||
                                        pendingCertificateSelection != null || pendingRequest != null ||
                                        interactivePrompt != null || nativeAfirma.hasPending || nativeFallback.hasPending || nativeRetrieval.hasPending || clientAuthPreparing ||
                                        currentSigningState !is SigningUiState.Idle
                                    if (busy) true else {
                                        when (val parsed = AfirmaServletInvocationParser.parse(rawUri, page)) {
                                            is AfirmaServletParseResult.Accepted -> {
                                                val operation = runCatching { dev.junta.firmamobile.afirma.servlet.nativeOperation(parsed.invocation) }.getOrElse {
                                                    parsed.invocation.close()
                                                    null
                                                }
                                                if (operation == null) nativeFallback.offer(owner, navigationEpoch.longValue, null)
                                                else nativeAfirma.offer(owner, navigationEpoch.longValue, operation)
                                            }
                                            is AfirmaServletParseResult.Deferred -> nativeRetrieval.offer(owner, navigationEpoch.longValue, parsed.invocation)
                                            is AfirmaServletParseResult.Unsupported -> nativeFallback.offer(owner, navigationEpoch.longValue, rawUri)
                                            is AfirmaServletParseResult.Invalid -> nativeFallback.offer(owner, navigationEpoch.longValue, null)
                                        }
                                        true
                                    }
                                },
                                onInteractiveClientAuthSslError = { owner, failedUrl ->
                                    interactiveClientAuth.onServerTlsError(owner, navigationEpoch.longValue, failedUrl)
                                },
                                onInteractiveClientAuthChallenge = { owner, request ->
                                    val busy = activePopup != null || pendingInPlaceClientAuth != null || pendingClientAuthTarget != null ||
                                        pendingCertificateSelection != null || pendingRequest != null ||
                                        clientAuthPreparing || nativeAfirma.hasPending || nativeFallback.hasPending || nativeRetrieval.hasPending ||
                                        currentSigningState !is SigningUiState.Idle
                                    if (busy) request.ignore() else {
                                        interactiveClientAuth.offer(
                                            owner, navigationEpoch.longValue,
                                            owner.url ?: validatedEntryUrl, request,
                                        )
                                    }
                                },
                                onClientAuthTarget = { authorized ->
                                    if (authorized.profileId == effectiveTopLevelProfileId) {
                                        pendingClientAuthPostBody.getAndSet(null)?.fill(0)
                                        pendingClientAuthTargetEpoch = navigationEpoch.longValue
                                        pendingClientAuthTarget = authorized
                                    } else {
                                        carneJovenPreTlsRetryController.reset()
                                        clientAuthAuthorizer.invalidate()
                                    }
                                },
                                onPreconfirmedClientAuthNetworkTimeout = retry@{ authorized ->
                                    val retrySource = carneJovenPreTlsRetryController.nextSource(authorized)
                                        ?: return@retry false
                                    val liveWebView = webViewRef.get()
                                    val liveClient = normalClientRef.get()
                                    if (liveWebView !== webView || liveClient == null ||
                                        authorized.profileId != effectiveTopLevelProfileId
                                    ) {
                                        carneJovenPreTlsRetryController.reset()
                                        return@retry false
                                    }
                                    val refreshed = authorized.refreshedAfterUserConfirmation()
                                    if (!liveClient.armConfirmedInPlaceClientAuth(
                                            refreshed,
                                            navigationEpoch.longValue,
                                        )
                                    ) {
                                        carneJovenPreTlsRetryController.reset()
                                        return@retry false
                                    }
                                    preconfirmedInPlaceClientAuthRef.set(refreshed)
                                    browserError = null
                                    pageLoading = true
                                    liveWebView.stopLoading()
                                    liveWebView.loadUrl(retrySource.toASCIIString())
                                    true
                                },
                                isConfirmedClientAuthReturnUrl = { rawUrl ->
                                    inPlaceClientAuthHandlerRef.get()?.isAuthFlowUrl(rawUrl) == true
                                },
                                resolveConfirmedClientAuthContinuationUrl = { rawUrl ->
                                    inPlaceClientAuthHandlerRef.get()?.resolveRequestContinuation(rawUrl)
                                },
                                onInPlaceClientAuthChallenge = onInPlaceChallenge@{ authorized, request ->
                                    if (activePopup != null || nativeAfirma.hasPending || nativeFallback.hasPending || nativeRetrieval.hasPending) {
                                        request.ignore()
                                        return@onInPlaceChallenge
                                    }
                                    if (carneJovenPreTlsRetryController.matchesRetryContract(authorized)) {
                                        carneJovenPreTlsRetryController.reset()
                                    }
                                    val currentHandler = inPlaceClientAuthHandlerRef.get()
                                    val continuation = currentHandler?.resolveRequestContinuation(
                                        authorized.target.toASCIIString(),
                                    )
                                    if (currentHandler != null && continuation == authorized &&
                                        currentHandler.hasProceeded()
                                    ) {
                                        val nextHandler = ClientAuthRequestHandler(
                                            grant = ClientAuthGrant(
                                                authorized = authorized,
                                                navigationEpoch = navigationEpoch.longValue,
                                            ),
                                            identityProvider = clientCertificateIdentityProvider,
                                            currentNavigationEpoch = { navigationEpoch.longValue },
                                            clearClientCertPreferences = {
                                                mainHandler.post {
                                                    clientCertPreferenceCoordinator.requestClear()
                                                }
                                            },
                                            onDiagnostic = { event ->
                                                logger.recordPortalCallback(
                                                    stage = event.stage,
                                                    host = authorized.target.host,
                                                )
                                            },
                                        )
                                        if (inPlaceClientAuthHandlerRef.compareAndSet(currentHandler, nextHandler) &&
                                            currentHandler.delegatePreferenceCleanup()
                                        ) {
                                            currentHandler.abandon()
                                            nextHandler.handle(request)
                                            return@onInPlaceChallenge
                                        }
                                        inPlaceClientAuthHandlerRef.compareAndSet(nextHandler, null)
                                        nextHandler.abandon()
                                        request.ignore()
                                        return@onInPlaceChallenge
                                    }
                                    val preconfirmed = preconfirmedInPlaceClientAuthRef.getAndSet(null)
                                    if (preconfirmed != null &&
                                        preconfirmed.profileId == authorized.profileId &&
                                        preconfirmed.source == authorized.source &&
                                        authorized.profileId == effectiveTopLevelProfileId &&
                                        !preconfirmed.isExpiredOrInvalid()
                                    ) {
                                        val handler = ClientAuthRequestHandler(
                                            grant = ClientAuthGrant(
                                                authorized = authorized,
                                                navigationEpoch = navigationEpoch.longValue,
                                            ),
                                            identityProvider = clientCertificateIdentityProvider,
                                            currentNavigationEpoch = { navigationEpoch.longValue },
                                            clearClientCertPreferences = {
                                                mainHandler.post {
                                                    clientCertPreferenceCoordinator.requestClear()
                                                }
                                            },
                                            onDiagnostic = { event ->
                                                logger.recordPortalCallback(
                                                    stage = event.stage,
                                                    host = authorized.target.host,
                                                )
                                            },
                                        )
                                        inPlaceClientAuthHandlerRef.getAndSet(handler)?.abandon()
                                        handler.handle(request)
                                    } else if (authorized.profileId == effectiveTopLevelProfileId &&
                                        !authorized.isExpiredOrInvalid() &&
                                        pendingInPlaceClientAuth == null
                                    ) {
                                        pendingInPlaceClientAuth = PendingInPlaceClientAuthChallenge(
                                            authorized = authorized,
                                            request = request,
                                            navigationEpoch = navigationEpoch.longValue,
                                        )
                                    } else {
                                        request.ignore()
                                        clientAuthAuthorizer.invalidate()
                                    }
                                },
                            )
                            normalClientRef.set(client)
                            webView.webViewClient = client
                            if (selectedServiceId != null && profileRequiresWebMessageBridge(selectedProfile)) {
                                val attachment = WebMessageBridge(
                                    profileId = selectedServiceId,
                                    logger = logger,
                                    onAfirmaRequest = handleAfirmaRequest,
                                    onMiniAppletRequest = { request, reply ->
                                        if (activePopup != null || nativeAfirma.hasPending || nativeFallback.hasPending || nativeRetrieval.hasPending) {
                                            reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.SIGNING_SERVICE_UNAVAILABLE)
                                        } else onMiniAppletRequest(request, reply)
                                    },
                                    onMiniAppletCancel = onMiniAppletCancel,
                                    onCertificateSelectionRequest = { request, reply ->
                                        if (activePopup != null || nativeAfirma.hasPending || nativeFallback.hasPending || nativeRetrieval.hasPending) {
                                            reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.SIGNING_SERVICE_UNAVAILABLE)
                                        } else prepareCertificateSelection(request, reply)
                                    },
                                    onCertificateSelectionCancel = { requestId ->
                                        if (pendingCertificateSelection?.request?.requestId == requestId) {
                                            pendingCertificateSelection = null
                                        }
                                    },
                                    onEuskadiClientAuthPostRequest = { request ->
                                        val canOwnRequest =
                                            request.authorized.profileId == effectiveTopLevelProfileId &&
                                                pendingClientAuthTarget == null &&
                                                clientAuthGrant == null &&
                                                !clientAuthPreparing && !nativeAfirma.hasPending &&
                                                !nativeFallback.hasPending && !nativeRetrieval.hasPending
                                        if (!canOwnRequest) {
                                            request.postBody.fill(0)
                                        } else {
                                            pendingClientAuthPostBody.getAndSet(request.postBody)?.fill(0)
                                            pendingClientAuthTargetEpoch = navigationEpoch.longValue
                                            pendingClientAuthTarget = request.authorized
                                        }
                                    },
                                    onMelillaBatchRequest = {
                                        request: MelillaBatchBridgeRequest,
                                        reply: MelillaBatchReplyChannel,
                                        ->
                                        if (activePopup != null || nativeAfirma.hasPending || nativeFallback.hasPending || nativeRetrieval.hasPending) {
                                            reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.SIGNING_SERVICE_UNAVAILABLE)
                                        } else if (onMelillaBatchRequest != null) {
                                            onMelillaBatchRequest(request, reply)
                                        }
                                        Unit // The reply result must not change this nullable callback's Unit contract.
                                    }.takeIf { onMelillaBatchRequest != null },
                                    onMelillaBatchCancel = onMelillaBatchCancel,
                                    activeProfileId = { effectiveTopLevelProfileId },
                                    miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
                                    currentNavigationEpoch = { navigationEpoch.longValue },
                                    currentOrigin = {
                                        webView.url?.let { url ->
                                            runCatching {
                                                JuntaOriginPolicy.signingOriginFor(
                                                    Uri.parse(url),
                                                    selectedServiceId,
                                                )
                                            }.getOrNull()
                                        }
                                    },
                                    currentPageUrl = { webView.url },
                                ).attach(webView)
                                bridgeAttachmentLease.bind(webView, attachment)
                                if (!attachment.listenerAttached ||
                                    !attachment.documentStartScriptAttached
                                ) {
                                    webView.post {
                                        if (webViewRef.get() === webView) compatibilityError = true
                                    }
                                }
                            } else {
                                bridgeAttachmentLease.close()
                            }
                        } else {
                            normalClientRef.set(null)
                            onWebViewChanged(browserWebViewForSigning(webView, dedicated = true))
                            val handler = ClientAuthRequestHandler(
                                grant = tlsGrant,
                                identityProvider = clientCertificateIdentityProvider,
                                currentNavigationEpoch = { navigationEpoch.longValue },
                                clearClientCertPreferences = {
                                    mainHandler.post {
                                        clientCertPreferenceCoordinator.requestClear()
                                    }
                                },
                                onDiagnostic = { event ->
                                    logger.recordPortalCallback(
                                        stage = event.stage,
                                        host = tlsGrant.authorized.target.host,
                                    )
                                },
                            )
                            val client = ClientAuthWebViewClient(
                                grant = tlsGrant,
                                requestHandler = handler,
                                callbacks = callbacks,
                                isActiveWebView = { candidate -> webViewRef.get() === candidate },
                                isTerminalReturnUrl = { rawUrl ->
                                    val uri = runCatching { URI(rawUrl) }.getOrNull()
                                    val origin = uri?.host?.let { host ->
                                        runCatching { ExactOrigin.parse("https://$host") }.getOrNull()
                                    }
                                    uri != null && origin != null &&
                                        tlsGrant.authorized.policy.matchesReturnUrl(
                                            uri,
                                            tlsGrant.authorized.target,
                                        ) &&
                                        origin in selectedProfile?.initiatorOrigins.orEmpty()
                                },
                                onTerminalReturnUrl = { rawUrl ->
                                    mainHandler.post {
                                        if (webViewRef.get() !== webView || clientAuthGrant != tlsGrant) {
                                            return@post
                                        }
                                        pendingNormalUrl.set(rawUrl)
                                        clientAuthGrant = null
                                        pageLoading = true
                                        advanceNavigationEpoch()
                                        onCancelSigning(SigningCancelReason.NAVIGATION, null)
                                    }
                                },
                            )
                            dedicatedClientRef.set(client)
                            dedicatedWebViewRef.set(webView)
                            webView.webViewClient = client
                            val targetUrl = tlsGrant.authorized.target.toASCIIString()
                            val postBody = pendingClientAuthPostBody.getAndSet(null)
                            val isEuskadiPostTarget =
                                tlsGrant.authorized.profileId.value ==
                                    EuskadiClientAuthPostBridgeAdapter.PROFILE_ID &&
                                    targetUrl == EuskadiClientAuthPostBridgeAdapter.TARGET_URL
                            if (postBody == null) {
                                if (isEuskadiPostTarget) {
                                    webView.post {
                                        if (webViewRef.get() === webView) compatibilityError = true
                                    }
                                } else {
                                    webView.loadUrl(targetUrl)
                                }
                            } else {
                                try {
                                    if (isEuskadiPostTarget) {
                                        webView.postUrl(targetUrl, postBody)
                                    } else {
                                        webView.post {
                                            if (webViewRef.get() === webView) compatibilityError = true
                                        }
                                    }
                                } finally {
                                    postBody.fill(0)
                                }
                            }
                        }
                        if (popupTransport != null) {
                            // This fresh view receives Chromium's pending native
                            // contents, preserving POST/opener state. Never load
                            // an inferred URL in place of the real popup.
                            webView.doOnAttach {
                                if (webViewRef.get() !== webView || !popupTransport.attach(webView)) {
                                    popupTransport.cancel()
                                    mainHandler.post { onPopupWindowClose() }
                                }
                            }
                        } else if (tlsGrant == null) {
                            val requestedUrl = pendingNormalUrl.getAndSet(null)
                            if (requestedUrl != null) {
                                webView.loadUrl(requestedUrl)
                            } else {
                                webView.loadUrl(validatedEntryUrl)
                            }
                        }
                    }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onRelease = { webView ->
                        bridgeAttachmentLease.release(webView)
                        inPlaceClientAuthHandlerRef.getAndSet(null)?.abandon()
                        cancelPendingInPlaceClientAuth()
                        if (dedicatedWebViewRef.compareAndSet(webView, null)) {
                            dedicatedClientRef.getAndSet(null)?.abandon()
                        }
                        if (webViewRef.compareAndSet(webView, null)) {
                            nativeRetrieval.invalidate()
                            interactiveClientAuth.cancelPending()
                            normalClientRef.set(null)
                            onWebViewChanged(null)
                        }
                        // Detach before native destruction, and let the current
                        // Chromium onCloseWindow callback unwind first. The
                        // parent frame has already regained visibility above.
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        mainHandler.post {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    },
                )
            }
        }
    }

    pendingRequest?.let { request ->
        AfirmaObservationDialog(
            request = request,
            certificateOwner = certificateState?.summary?.ownerName ?: stringResource(R.string.browser_no_unlocked_certificate),
            onDismiss = { pendingRequest = null },
            onOpenOfficial = { pendingRequest = null; onOpenOfficialAutoFirma(Uri.parse(request.rawUri)) },
        )
    }

    pendingCertificateSelection?.let { pending ->
        CertificateSelectionConfirmationDialog(
            host = pending.request.context.origin.host,
            certificateOwner = pending.certificateOwner,
            safeDescription = pending.request.safeDescription,
            onContinue = {
                val current = pendingCertificateSelection
                if (current?.request?.requestId != pending.request.requestId) return@CertificateSelectionConfirmationDialog
                val identity = clientCertificateIdentityProvider()
                val currentFingerprint = identity?.let(::certificateSelectionFingerprint)
                if (identity == null || currentFingerprint != pending.certificateFingerprint) {
                    pending.reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.CERTIFICATE_LOCKED)
                } else if (!certificateEligibleForSelection(
                        pending.request.context.profileId,
                        identity.certificate,
                    )
                ) {
                    pending.reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.PROTOCOL_FAILED)
                } else {
                    val certificateDer = runCatching { identity.certificate.encoded }.getOrNull()
                    if (certificateDer == null) {
                        pending.reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.PROTOCOL_FAILED)
                    } else {
                        pending.reply.success(certificateDer)
                    }
                }
                pendingCertificateSelection = null
            },
            onCancel = {
                if (pendingCertificateSelection?.request?.requestId == pending.request.requestId) {
                    pending.reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.USER_CANCELLED)
                    pendingCertificateSelection = null
                }
            },
        )
    }

    if (!certificatePanelVisible) {
        nativeRetrievalPrompt?.let { prompt ->
            AfirmaRetrievalDialog(prompt,
                onCancel = { nativeRetrieval.cancel(prompt.token) },
                onClose = { nativeRetrieval.dismiss(prompt.token) },
            )
        }
        nativeAfirmaPrompt?.let { prompt ->
            NativeAfirmaConsentDialog(
                prompt = prompt,
                onConfirm = { nativeAfirma.confirm(prompt.token) },
                onUnlock = onChangeCertificate,
                onCancel = {
                    if (prompt.phase == AfirmaConsentPhase.REVIEW) {
                        nativeAfirma.requestCancellation(prompt.token)
                    } else {
                        nativeAfirma.cancel(prompt.token)
                    }
                },
                onDismiss = { nativeAfirma.dismiss(prompt.token) },
            )
        }
        nativeFallbackPrompt?.let { fallback ->
            AlertDialog(
                onDismissRequest = { nativeFallback.dismiss(fallback.token) },
                title = { Text(stringResource(R.string.native_afirma_title)) },
                text = { Text(stringResource(if (fallback.invalid) R.string.native_afirma_invalid else R.string.native_afirma_unsupported)) },
                confirmButton = {
                    TextButton(onClick = { nativeFallback.dismiss(fallback.token) }) {
                        Text(stringResource(R.string.native_afirma_close))
                    }
                },
                dismissButton = {
                    if (!fallback.invalid) {
                        TextButton(onClick = {
                            nativeFallback.consume(fallback.token)?.let { onOpenOfficialAutoFirma(Uri.parse(it)) }
                        }) { Text(stringResource(R.string.native_afirma_official)) }
                    }
                },
            )
        }
        interactivePrompt?.let { prompt ->
            InteractiveClientAuthDialog(
                prompt = prompt,
                onConfirm = { interactiveClientAuth.confirm(prompt.token) },
                onUnlock = onChangeCertificate,
                onCancel = { interactiveClientAuth.cancel(prompt.token) },
            )
        }
        if (interactiveProblem != null && interactivePrompt == null) {
            AlertDialog(
                onDismissRequest = { interactiveProblem = null },
                title = { Text(stringResource(R.string.interactive_tls_title)) },
                text = { Text(stringResource(R.string.interactive_tls_ended)) },
                confirmButton = {
                    TextButton(onClick = { interactiveProblem = null }) { Text(stringResource(R.string.interactive_tls_close)) }
                },
            )
        }
    }

    pendingInPlaceClientAuth?.let { pending ->
        ClientAuthConfirmationDialog(
            organization = effectiveProfile?.displayName
                ?: selectedProfile?.displayName
                ?: stringResource(R.string.app_name),
            host = pending.authorized.target.host,
            certificateOwner = certificateState?.summary?.ownerName ?: stringResource(R.string.browser_no_unlocked_certificate),
            onContinue = {
                if (pending.authorized.profileId != effectiveTopLevelProfileId ||
                    pending.navigationEpoch != navigationEpoch.longValue ||
                    pending.authorized.isExpiredOrInvalid()
                ) {
                    pending.request.ignore()
                    pendingInPlaceClientAuth = null
                    clientAuthAuthorizer.invalidate()
                } else {
                    pendingInPlaceClientAuth = null
                    val handler = ClientAuthRequestHandler(
                        grant = ClientAuthGrant(
                            authorized = pending.authorized,
                            navigationEpoch = pending.navigationEpoch,
                        ),
                        identityProvider = clientCertificateIdentityProvider,
                        currentNavigationEpoch = { navigationEpoch.longValue },
                        clearClientCertPreferences = {
                            mainHandler.post { clientCertPreferenceCoordinator.requestClear() }
                        },
                        onDiagnostic = { event ->
                            logger.recordPortalCallback(
                                stage = event.stage,
                                host = pending.authorized.target.host,
                            )
                        },
                    )
                    inPlaceClientAuthHandlerRef.getAndSet(handler)?.abandon()
                    handler.handle(pending.request)
                }
            },
            onCancel = {
                if (pendingInPlaceClientAuth === pending) {
                    pending.request.ignore()
                    pendingInPlaceClientAuth = null
                }
                clientAuthAuthorizer.invalidate()
                onCancelSigning(SigningCancelReason.USER, null)
            },
        )
    }

    pendingClientAuthTarget?.let { authorized ->
        ClientAuthConfirmationDialog(
            organization = effectiveProfile?.displayName
                ?: selectedProfile?.displayName
                ?: stringResource(R.string.app_name),
            host = authorized.target.host,
            certificateOwner = certificateState?.summary?.ownerName ?: stringResource(R.string.browser_no_unlocked_certificate),
            onContinue = {
                if (authorized.profileId != effectiveTopLevelProfileId ||
                    pendingClientAuthTargetEpoch != navigationEpoch.longValue
                ) {
                    pendingClientAuthTarget = null
                    abandonClientAuth()
                } else {
                    carneJovenPreTlsRetryController.reset()
                    val confirmedTarget = authorized.refreshedAfterUserConfirmation()
                    pendingClientAuthTarget = null
                    onCancelSigning(SigningCancelReason.NAVIGATION, null)
                    dispatchConfirmedClientAuthPreparation(
                        policy = confirmedTarget.policy,
                        beginInPlace = {
                            beginConfirmedInPlaceClientAuthPreparation(confirmedTarget)
                        },
                        beginDedicated = {
                            advanceNavigationEpoch()
                            bridgeAttachmentLease.close()
                            webViewRef.get()?.stopLoading()
                            beginClientAuthPreparation(
                                ClientAuthGrant(
                                    authorized = confirmedTarget,
                                    navigationEpoch = navigationEpoch.longValue,
                                ),
                            )
                        },
                    )
                }
            },
            onCancel = {
                pendingClientAuthTarget = null
                clientAuthGrant = null
                abandonClientAuth()
                advanceNavigationEpoch()
                onCancelSigning(SigningCancelReason.USER, null)
            },
        )
    }

    (signingState as? SigningUiState.AwaitingConfirmation)?.let { state ->
        SigningConfirmationDialog(
            state = state,
            onConfirm = { onConfirmSigning(state.requestId) },
            onCancel = {
                onCancelSigning(SigningCancelReason.USER, state.requestId)
            },
        )
    }

    when (signingState) {
        is SigningUiState.Signing,
        is SigningUiState.ConnectingSecurely,
        is SigningUiState.Completed,
        is SigningUiState.Failed,
        -> SigningStatusDialog(
            state = signingState,
            onDismiss = onDismissSigningState,
        )

        SigningUiState.Idle,
        is SigningUiState.AwaitingConfirmation,
        -> Unit
    }
    } // Retained parent content; no profile or native key authority is copied.
    activePopup?.let { popup ->
        key(popup.token) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize().testTag("browser-popup-window")) {
                Column(Modifier.fillMaxSize()) {
                    BrowserPopupHeader { closePopup(popup.token) }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        BrowserScreen(
                            profileId = null,
                            entryUrl = popup.entryOrigin,
                            certificateState = certificateState,
                            logger = logger,
                            signingState = signingState,
                            onMiniAppletRequest = onMiniAppletRequest,
                            onMiniAppletCancel = onMiniAppletCancel,
                            onConfirmSigning = onConfirmSigning,
                            onCancelSigning = onCancelSigning,
                            onDismissSigningState = onDismissSigningState,
                            onExitBrowser = { closePopup(popup.token) },
                            onOpenExternal = onOpenExternal,
                            onOpenOfficialAutoFirma = onOpenOfficialAutoFirma,
                            onChangeCertificate = onChangeCertificate,
                            onLockCertificate = onLockCertificate,
                            onClearSession = onClearSession,
                            clientCertificateIdentityProvider = clientCertificateIdentityProvider,
                            clientCertPreferenceCoordinator = clientCertPreferenceCoordinator,
                            onWebViewChanged = { child ->
                                popup.child = child
                                if (activePopup === popup) {
                                    if (child != null && popup.transport.attached) popupLease.markAttached(popup.token)
                                    onWebViewChanged(child)
                                }
                            },
                            onNavigationEpochChanged = onNavigationEpochChanged,
                            onShowFileChooser = onShowFileChooser,
                            onDocumentDownload = onDocumentDownload,
                            onExternalAppRequest = onExternalAppRequest,
                            certificatePanelVisible = certificatePanelVisible,
                            mayRetainExternalReturn = mayRetainExternalReturn,
                            consumeExternalReturn = consumeExternalReturn,
                            popupTransport = popup.transport,
                            onPopupWindowClose = { closePopup(popup.token) },
                            onRelatedClientCertPreferenceClear = { clearing ->
                                if (clearing) popupClearingCertificatePreferences = popup.token
                                else if (popupClearingCertificatePreferences == popup.token) popupClearingCertificatePreferences = null
                            },
                        )
                    }
                }
            }
            LaunchedEffect(popup.token) {
                // Attachment is asynchronous to Compose's factory callback.
                while (activePopup === popup && !popup.transport.attached) delay(16)
                if (activePopup === popup && popup.transport.attached) popupLease.markAttached(popup.token)
            }
        }
    }
    }
}

@Composable
internal fun BrowserLayout(
    certificateOwner: String,
    currentUrl: String = JuntaOriginPolicy.START_URL,
    profileName: String = "Junta de Andalucía",
    trustLabel: String = "Firma protegida",
    browserInsets: WindowInsets = BrowserWindowInsetsPolicy.current(),
    onBack: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onChangeCertificate: () -> Unit,
    onLockCertificate: () -> Unit,
    onClearCurrentSite: () -> Unit,
    onClearSession: () -> Unit,
    onDeleteAllBrowserData: () -> Unit,
    onOpenInBrowser: (() -> Unit)? = null,
    certificateAvailable: Boolean = true,
    publicBrowsing: Boolean = false,
    webAuthnState: dev.junta.firmamobile.browser.WebAuthnEngineState? = null,
    content: @Composable (Modifier) -> Unit,
) {
    var confirmClearCurrentSite by remember { mutableStateOf(false) }
    var confirmClearSession by remember { mutableStateOf(false) }
    var confirmDeleteAllData by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
            IndustrialBrowserTopBar(
                profileName = profileName,
                host = BrowserAddressPresentation.hostOf(currentUrl),
                trustLabel = trustLabel,
                onBack = onBack,
                onHome = onHome,
                onReload = onReload,
                onChangeCertificate = onChangeCertificate,
                onLockCertificate = onLockCertificate,
                onClearCurrentSiteRequested = { confirmClearCurrentSite = true },
                onClearSessionRequested = { confirmClearSession = true },
                onDeleteAllBrowserDataRequested = { confirmDeleteAllData = true },
                onOpenInBrowser = onOpenInBrowser,
                windowInsets = browserInsets.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                modifier = Modifier.testTag(BROWSER_TOOLBAR_TAG),
            )
            if (publicBrowsing) PublicBrowsingNotice()
            webAuthnState?.let { WebAuthnStatusButton(it, onOpenInBrowser) }
            }
        },
        bottomBar = {
            BrowserCertificateStrip(
                certificateOwner = certificateOwner,
                certificateAvailable = certificateAvailable,
                modifier = Modifier
                    .testTag(BROWSER_BOTTOM_BAR_TAG),
                windowInsets = browserInsets.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                ),
            )
        },
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(
                    browserInsets.only(WindowInsetsSides.Horizontal),
                ),
        )
    }

    if (confirmClearCurrentSite) {
        AlertDialog(
            onDismissRequest = { confirmClearCurrentSite = false },
            title = { Text(stringResource(R.string.browser_clear_current_site_title)) },
            text = { Text(stringResource(R.string.browser_clear_current_site_copy)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCurrentSite = false
                    onClearCurrentSite()
                }) {
                    Text(stringResource(R.string.browser_clear_current_site_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCurrentSite = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmClearSession) {
        AlertDialog(
            onDismissRequest = { confirmClearSession = false },
            title = { Text(stringResource(R.string.browser_clear_session_title)) },
            text = { Text(stringResource(if (publicBrowsing) R.string.public_browsing_clear_copy else R.string.browser_clear_session_copy)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearSession = false
                    onClearSession()
                }) {
                    Text(stringResource(R.string.browser_clear_session_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearSession = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmDeleteAllData) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAllData = false },
            title = { Text(stringResource(R.string.browser_delete_all_data_title)) },
            text = { Text(stringResource(R.string.browser_delete_all_data_copy)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAllData = false
                    onDeleteAllBrowserData()
                }) {
                    Text(stringResource(R.string.browser_delete_all_data_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAllData = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun browserNotice(
    compatibilityError: Boolean,
    blockedReason: NavigationBlockReason?,
    browserError: BrowserErrorCode?,
    clientCertPreferenceState: ClientCertPreferenceBarrierState,
    dataNotice: String?,
): String? = when {
    clientCertPreferenceState == ClientCertPreferenceBarrierState.CLEARING ->
        stringResource(R.string.browser_client_cert_preferences_clearing)
    clientCertPreferenceState == ClientCertPreferenceBarrierState.FAILED ->
        stringResource(R.string.browser_client_cert_preferences_error)
    compatibilityError -> stringResource(R.string.browser_compatibility_error)
    browserError == BrowserErrorCode.CLIENT_CERT_PREFERENCES ->
        stringResource(R.string.browser_client_cert_preferences_error)
    browserError != null -> stringResource(R.string.browser_load_error)
    blockedReason == NavigationBlockReason.PLAY_STORE_FALLBACK ->
        stringResource(R.string.browser_play_store_blocked)
    blockedReason != null -> stringResource(R.string.browser_navigation_blocked)
    dataNotice != null -> dataNotice
    else -> null
}

internal fun browserNoticeLiveRegionMode(
    compatibilityError: Boolean,
    blockedReason: NavigationBlockReason?,
    browserError: BrowserErrorCode?,
    clientCertPreferenceState: ClientCertPreferenceBarrierState,
    siteClearResult: SiteClearResult?,
    globalClearResult: Boolean?,
): LiveRegionMode = when {
    clientCertPreferenceState == ClientCertPreferenceBarrierState.CLEARING -> LiveRegionMode.Polite
    clientCertPreferenceState == ClientCertPreferenceBarrierState.FAILED -> LiveRegionMode.Assertive
    compatibilityError -> LiveRegionMode.Assertive
    browserError != null -> LiveRegionMode.Assertive
    blockedReason != null -> LiveRegionMode.Assertive
    globalClearResult == true -> LiveRegionMode.Polite
    globalClearResult == false -> LiveRegionMode.Assertive
    siteClearResult == SiteClearResult.CLEARED_EXACTLY -> LiveRegionMode.Polite
    siteClearResult != null -> LiveRegionMode.Assertive
    else -> LiveRegionMode.Assertive
}

@Composable
private fun browserDataNotice(
    siteClearResult: SiteClearResult?,
    globalClearResult: Boolean?,
): String? = when {
    globalClearResult == true -> stringResource(R.string.browser_delete_all_data_success)
    globalClearResult == false -> stringResource(R.string.browser_delete_all_data_failed)
    siteClearResult == SiteClearResult.CLEARED_EXACTLY ->
        stringResource(R.string.browser_clear_current_site_success)
    siteClearResult == SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE ->
        stringResource(R.string.browser_clear_current_site_limited)
    siteClearResult == SiteClearResult.FAILED ->
        stringResource(R.string.browser_clear_current_site_failed)
    else -> null
}

internal fun safeBrowserDisplayUrl(rawUrl: String): String = runCatching {
    val uri = Uri.parse(rawUrl)
    require(
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.encodedUserInfo == null &&
            uri.port in setOf(-1, 443),
    )
    uri.buildUpon().clearQuery().fragment(null).build().toString()
}.getOrDefault("https://")

internal fun browserWebViewForSigning(webView: WebView?, dedicated: Boolean): WebView? =
    webView.takeUnless { dedicated }

internal fun initiatorProfileForUrl(rawUrl: String): ProfileId? = runCatching {
    BuiltInSiteProfiles.runtimeRegistry.resolve(java.net.URI(rawUrl))
}.getOrNull()?.takeIf { resolution ->
    resolution.origin in resolution.profile.initiatorOrigins &&
        resolution.trustMode in setOf(
            TrustMode.TRUSTED_SIGNING,
            TrustMode.TRUSTED_CLIENT_AUTH,
        )
}?.profile?.profileId

internal fun urlBelongsToSelectedProfile(rawUrl: String, profileId: ProfileId): Boolean =
    runCatching {
        val uri = URI(rawUrl)
        val registry = BuiltInSiteProfiles.runtimeRegistry
        registry.resolveForProfile(profileId, uri)?.profile?.profileId == profileId ||
            registry.resolveRedirect(profileId, uri)?.profile?.profileId == profileId
    }.getOrDefault(false)

internal fun profileRequiresWebMessageBridge(
    profile: dev.junta.firmamobile.profile.SiteProfile?,
): Boolean {
    if (profile?.profileId?.value == EuskadiClientAuthPostBridgeAdapter.PROFILE_ID &&
        profile.capabilities == setOf(dev.junta.firmamobile.profile.Capability.CLIENT_TLS_AUTH)
    ) {
        return true
    }
    return profile?.capabilities?.any { capability ->
        capability == dev.junta.firmamobile.profile.Capability.SIGN ||
            capability == dev.junta.firmamobile.profile.Capability.SELECT_CERTIFICATE ||
            capability == dev.junta.firmamobile.profile.Capability.AFIRMA_URI
    } == true
}

@Composable
private fun CertificateSelectionConfirmationDialog(
    host: String,
    certificateOwner: String,
    safeDescription: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Compartir certificado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dominio: $host")
                Text("Operación: $safeDescription")
                Text("Certificado: $certificateOwner")
                Text("Solo se compartirá el certificado público; la clave privada no sale del dispositivo.")
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("Compartir") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ClientAuthConfirmationDialog(
    organization: String,
    host: String,
    certificateOwner: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Acceso con certificado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Organismo: $organization")
                Text("Dominio: $host")
                Text("Operación: autenticación TLS con certificado")
                Text("Certificado: $certificateOwner")
                Text("El certificado solo se enviará al dominio exacto confirmado.")
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("Continuar") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AfirmaObservationDialog(
    request: AfirmaRequest,
    certificateOwner: String,
    onDismiss: () -> Unit,
    onOpenOfficial: () -> Unit,
) {
    val algorithm = request.singleValue("algorithm") ?: stringResource(R.string.unknown_value)
    val format = request.singleValue("format") ?: stringResource(R.string.unknown_value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.afirma_request_observed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.afirma_request_site, request.origin.host))
                Text(stringResource(R.string.afirma_request_certificate, certificateOwner))
                Text(stringResource(R.string.afirma_request_format, format))
                Text(stringResource(R.string.afirma_request_algorithm, algorithm))
                Text(stringResource(R.string.afirma_request_observed_copy))
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenOfficial) { Text(stringResource(R.string.open_official_autofirma)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
