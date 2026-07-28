package dev.junta.firmamobile.ui

import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.BrowserSessionStatePolicy
import dev.junta.firmamobile.browser.BrowserNavigationCallbacks
import dev.junta.firmamobile.browser.BrowserTrustController
import dev.junta.firmamobile.browser.BrowserUrlPolicy
import dev.junta.firmamobile.browser.AuthorizedClientAuthTarget
import dev.junta.firmamobile.browser.ClientAuthGrant
import dev.junta.firmamobile.browser.ClientAuthNavigationAuthorizer
import dev.junta.firmamobile.browser.ClientAuthRequestHandler
import dev.junta.firmamobile.browser.ClientAuthWebViewClient
import dev.junta.firmamobile.browser.JuntaNavigationPolicy
import dev.junta.firmamobile.browser.JuntaWebViewClient
import dev.junta.firmamobile.browser.MiniAppletBridgeMode
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.browser.SensitiveFlowInvalidator
import dev.junta.firmamobile.browser.TrustedJuntaWebView
import dev.junta.firmamobile.browser.WebMessageBridge
import dev.junta.firmamobile.browser.WebMessageBridgeAttachment
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.TrustMode
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.signing.SigningUiState
import java.util.UUID
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

@Composable
fun BrowserScreen(
    profileId: ProfileId,
    entryUrl: URI,
    certificateState: CertificateUiState.Unlocked,
    logger: SanitizedLogger,
    signingState: SigningUiState,
    onMiniAppletRequest: (MiniAppletBridgeRequest, SigningReplySink) -> Unit,
    onMiniAppletCancel: (UUID) -> Unit,
    onConfirmSigning: (UUID) -> Unit,
    onCancelSigning: (SigningCancelReason, UUID?) -> Unit,
    onDismissSigningState: () -> Unit,
    onExitBrowser: () -> Unit,
    onOpenExternal: (Uri) -> Unit,
    onChangeCertificate: () -> Unit,
    onLockCertificate: () -> Unit,
    onClearSession: () -> Unit,
    clientCertificateIdentityProvider: () -> UnlockedIdentity?,
    onWebViewChanged: (WebView?) -> Unit,
    onNavigationEpochChanged: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val selectedServiceId = profileId
    val validatedEntryUrl = remember(selectedServiceId, entryUrl) {
        checkNotNull(
            BrowserSessionStatePolicy.validatedEntryUrl(
                registry = BuiltInSiteProfiles.runtimeRegistry,
                profileId = selectedServiceId,
                entryUrl = entryUrl,
            ),
        ) { "Browser entry URL does not belong to the selected profile" }
    }
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
    val webViewRef = remember { AtomicReference<WebView?>() }
    val bridgeRef = remember { AtomicReference<WebMessageBridgeAttachment?>() }
    val dedicatedClientRef = remember { AtomicReference<ClientAuthWebViewClient?>() }
    val dedicatedWebViewRef = remember { AtomicReference<TrustedJuntaWebView?>() }
    val pendingNormalUrl = remember { AtomicReference<String?>() }
    val navigationEpoch = remember { mutableLongStateOf(0L) }
    val navigationPolicy = remember(selectedServiceId) {
        JuntaNavigationPolicy(selectedServiceId)
    }
    val clientAuthAuthorizer = remember {
        ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.runtimeRegistry)
    }
    var clientAuthGrant by remember { mutableStateOf<ClientAuthGrant?>(null) }
    var pendingClientAuthTarget by remember {
        mutableStateOf<AuthorizedClientAuthTarget?>(null)
    }
    var pendingRequest by remember { mutableStateOf<AfirmaRequest?>(null) }
    var blockedReason by remember { mutableStateOf<NavigationBlockReason?>(null) }
    var browserError by remember { mutableStateOf<BrowserErrorCode?>(null) }
    var compatibilityError by remember { mutableStateOf(false) }
    var currentUrl by remember(profileId, entryUrl) {
        mutableStateOf(validatedEntryUrl)
    }
    var pageProgress by remember { mutableIntStateOf(100) }
    var webViewRecreationEpoch by remember { mutableIntStateOf(0) }

    fun advanceNavigationEpoch() {
        bridgeRef.get()?.abandonMiniAppletRequests()
        check(navigationEpoch.longValue != Long.MAX_VALUE)
        navigationEpoch.longValue++
        onNavigationEpochChanged(navigationEpoch.longValue)
    }

    fun abandonClientAuth() {
        dedicatedClientRef.getAndSet(null)?.abandon()
        dedicatedWebViewRef.set(null)
        clientAuthAuthorizer.invalidate()
        WebView.clearClientCertPreferences(null)
    }

    val handleAfirmaRequest: (AfirmaRequest) -> Unit = { request ->
        pendingRequest = request
    }
    val callbacks = remember(
        selectedServiceId,
        entryUrl,
        onOpenExternal,
        onCancelSigning,
        onWebViewChanged,
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

            override fun onAfirmaRequest(request: AfirmaRequest) {
                handleAfirmaRequest(request)
            }

            override fun onNavigationBlocked(reason: NavigationBlockReason) {
                blockedReason = reason
            }

            override fun onBrowserError(error: BrowserErrorCode) {
                browserError = error
                pageProgress = 100
            }

            override fun onRenderProcessGone(view: WebView) {
                if (!webViewRef.compareAndSet(view, null)) return
                onWebViewChanged(null)
                effectiveTopLevelProfileId = null
                pendingClientAuthTarget = null
                clientAuthGrant = null
                pendingRequest = null
                bridgeRef.getAndSet(null)?.close()
                abandonClientAuth()
                advanceNavigationEpoch()
                onCancelSigning(SigningCancelReason.NAVIGATION, null)
                browserError = BrowserErrorCode.RENDER_PROCESS_GONE
                pageProgress = 100
                webViewRecreationEpoch++
            }

            override fun onTopLevelNavigationStarted(url: String) {
                pageProgress = 0
                browserError = null
                blockedReason = null
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
    DisposableEffect(onCancelSigning) {
        onDispose {
            onCancelSigning(SigningCancelReason.BACKGROUND, null)
            bridgeRef.getAndSet(null)?.close()
            webViewRef.getAndSet(null)?.let { webView ->
                onWebViewChanged(null)
                webView.stopLoading()
                webView.destroy()
            }
            abandonClientAuth()
        }
    }

    val selectedProfile = BuiltInSiteProfiles.runtimeRegistry.profile(selectedServiceId)
    val effectiveProfile = effectiveTopLevelProfileId?.let(
        BuiltInSiteProfiles.runtimeRegistry::profile,
    )
    val currentResolution = runCatching {
        BuiltInSiteProfiles.runtimeRegistry.resolve(java.net.URI(currentUrl))
    }.getOrNull()
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
    BrowserLayout(
        currentUrl = currentUrl,
        profileName = effectiveProfile?.displayName
            ?: selectedProfile?.displayName
            ?: stringResource(R.string.app_name),
        trustLabel = trustLabel,
        certificateOwner = certificateState.summary.ownerName,
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
            pendingClientAuthTarget = null
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.RELOAD, null)
            browserError = null
            if (!leavingClientAuth) webViewRef.get()?.reload()
        },
        onChangeCertificate = {
            clientAuthGrant = null
            abandonClientAuth()
            onCancelSigning(SigningCancelReason.CERTIFICATE_LOCKED, null)
            onChangeCertificate()
        },
        onLockCertificate = {
            clientAuthGrant = null
            abandonClientAuth()
            onCancelSigning(SigningCancelReason.CERTIFICATE_LOCKED, null)
            onLockCertificate()
        },
        onClearSession = {
            clientAuthGrant = null
            abandonClientAuth()
            onCancelSigning(SigningCancelReason.CERTIFICATE_LOCKED, null)
            pendingRequest = null
            webViewRef.get()?.apply {
                stopLoading()
                clearCache(true)
                clearHistory()
                clearFormData()
            }
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            WebStorage.getInstance().deleteAllData()
            onClearSession()
        },
    ) { modifier ->
        Column(modifier = modifier) {
            val notice = browserNotice(
                compatibilityError = compatibilityError,
                blockedReason = blockedReason,
                browserError = browserError,
            )
            notice?.let { message ->
                BrowserNoticeBanner(
                    message = message,
                    onRetry = if (browserError != null) {
                        {
                            onCancelSigning(SigningCancelReason.RELOAD, null)
                            browserError = null
                            pageProgress = 0
                            if (clientAuthGrant != null) {
                                val activeStartUrl = BuiltInSiteProfiles.runtimeRegistry
                                    .profile(selectedServiceId)
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
                    } else {
                        null
                    },
                )
            }
            BrowserLoadingIndicator(visible = pageProgress in 0..99)
            key(clientAuthGrant != null, webViewRecreationEpoch) {
                AndroidView(
                    factory = {
                    TrustedJuntaWebView(context).also { webView ->
                        webView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        webViewRef.set(webView)
                        webView.setPageProgressListener { progress ->
                            webView.post {
                                if (webViewRef.get() === webView) pageProgress = progress
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
                                onClientAuthTarget = { authorized ->
                                    if (authorized.profileId == effectiveTopLevelProfileId) {
                                        pendingClientAuthTarget = authorized
                                    } else {
                                        clientAuthAuthorizer.invalidate()
                                    }
                                },
                            )
                            webView.webViewClient = client
                            if (profileRequiresWebMessageBridge(selectedProfile)) {
                                val attachment = WebMessageBridge(
                                    profileId = selectedServiceId,
                                    logger = logger,
                                    onAfirmaRequest = handleAfirmaRequest,
                                    onMiniAppletRequest = { request, reply ->
                                        onMiniAppletRequest(request, reply)
                                    },
                                    onMiniAppletCancel = onMiniAppletCancel,
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
                                ).attach(webView)
                                bridgeRef.set(attachment)
                                if (!attachment.listenerAttached ||
                                    !attachment.documentStartScriptAttached
                                ) {
                                    webView.post { compatibilityError = true }
                                }
                            } else {
                                bridgeRef.set(null)
                            }
                        } else {
                            onWebViewChanged(browserWebViewForSigning(webView, dedicated = true))
                            val handler = ClientAuthRequestHandler(
                                grant = tlsGrant,
                                identityProvider = clientCertificateIdentityProvider,
                                currentNavigationEpoch = { navigationEpoch.longValue },
                                clearClientCertPreferences = {
                                    webView.post { WebView.clearClientCertPreferences(null) }
                                },
                            )
                            val client = ClientAuthWebViewClient(
                                grant = tlsGrant,
                                requestHandler = handler,
                                callbacks = callbacks,
                            )
                            dedicatedClientRef.set(client)
                            dedicatedWebViewRef.set(webView)
                            webView.webViewClient = client
                            WebView.clearClientCertPreferences {
                                webView.post {
                                    if (webViewRef.get() === webView) {
                                        webView.loadUrl(tlsGrant.authorized.target.toASCIIString())
                                    }
                                }
                            }
                        }
                        if (tlsGrant == null) {
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
                        if (dedicatedWebViewRef.compareAndSet(webView, null)) {
                            dedicatedClientRef.getAndSet(null)?.abandon()
                        }
                        if (webViewRef.compareAndSet(webView, null)) onWebViewChanged(null)
                        webView.stopLoading()
                        webView.destroy()
                    },
                )
            }
        }
    }

    pendingRequest?.let { request ->
        AfirmaObservationDialog(
            request = request,
            certificateOwner = certificateState.summary.ownerName,
            onDismiss = { pendingRequest = null },
        )
    }

    pendingClientAuthTarget?.let { authorized ->
        ClientAuthConfirmationDialog(
            organization = effectiveProfile?.displayName
                ?: selectedProfile?.displayName
                ?: stringResource(R.string.app_name),
            host = authorized.target.host,
            certificateOwner = certificateState.summary.ownerName,
            onContinue = {
                if (authorized.profileId != effectiveTopLevelProfileId) {
                    pendingClientAuthTarget = null
                    abandonClientAuth()
                } else {
                    pendingClientAuthTarget = null
                    advanceNavigationEpoch()
                    onCancelSigning(SigningCancelReason.NAVIGATION, null)
                    bridgeRef.getAndSet(null)?.close()
                    clientAuthGrant = ClientAuthGrant(
                        authorized = authorized,
                        navigationEpoch = navigationEpoch.longValue,
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
    onClearSession: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var confirmClearSession by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            IndustrialBrowserTopBar(
                profileName = profileName,
                host = BrowserAddressPresentation.hostOf(currentUrl),
                trustLabel = trustLabel,
                onBack = onBack,
                onHome = onHome,
                onReload = onReload,
                onChangeCertificate = onChangeCertificate,
                onLockCertificate = onLockCertificate,
                onClearSessionRequested = { confirmClearSession = true },
                windowInsets = browserInsets.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                modifier = Modifier.testTag(BROWSER_TOOLBAR_TAG),
                editingContent = null,
            )
        },
        bottomBar = {
            BrowserCertificateStrip(
                certificateOwner = certificateOwner,
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

    if (confirmClearSession) {
        AlertDialog(
            onDismissRequest = { confirmClearSession = false },
            title = { Text(stringResource(R.string.browser_clear_session_title)) },
            text = { Text(stringResource(R.string.browser_clear_session_copy)) },
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
}

@Composable
private fun browserNotice(
    compatibilityError: Boolean,
    blockedReason: NavigationBlockReason?,
    browserError: BrowserErrorCode?,
): String? = when {
    compatibilityError -> stringResource(R.string.browser_compatibility_error)
    browserError != null -> stringResource(R.string.browser_load_error)
    blockedReason == NavigationBlockReason.PLAY_STORE_FALLBACK ->
        stringResource(R.string.browser_play_store_blocked)
    blockedReason != null -> stringResource(R.string.browser_navigation_blocked)
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
        registry.resolve(uri)?.profile?.profileId == profileId ||
            registry.resolveRedirect(profileId, uri)?.profile?.profileId == profileId
    }.getOrDefault(false)

internal fun profileRequiresWebMessageBridge(
    profile: dev.junta.firmamobile.profile.SiteProfile?,
): Boolean = profile?.capabilities?.any { capability ->
    capability == dev.junta.firmamobile.profile.Capability.SIGN ||
        capability == dev.junta.firmamobile.profile.Capability.SELECT_CERTIFICATE ||
        capability == dev.junta.firmamobile.profile.Capability.AFIRMA_URI
} == true

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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
