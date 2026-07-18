package dev.junta.firmamobile.ui

import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.BrowserNavigationCallbacks
import dev.junta.firmamobile.browser.ClientAuthGrant
import dev.junta.firmamobile.browser.ClientAuthNavigationAuthorizer
import dev.junta.firmamobile.browser.ClientAuthRequestHandler
import dev.junta.firmamobile.browser.ClientAuthWebViewClient
import dev.junta.firmamobile.browser.JuntaNavigationPolicy
import dev.junta.firmamobile.browser.JuntaWebViewClient
import dev.junta.firmamobile.browser.MiniAppletBridgeMode
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.browser.NavigationDecision
import dev.junta.firmamobile.browser.TrustedJuntaWebView
import dev.junta.firmamobile.browser.WebMessageBridge
import dev.junta.firmamobile.browser.WebMessageBridgeAttachment
import dev.junta.firmamobile.browser.WebViewStateHolder
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun BrowserScreen(
    certificateState: CertificateUiState.Unlocked,
    stateHolder: WebViewStateHolder,
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
    val webViewRef = remember { AtomicReference<TrustedJuntaWebView?>() }
    val bridgeRef = remember { AtomicReference<WebMessageBridgeAttachment?>() }
    val dedicatedClientRef = remember { AtomicReference<ClientAuthWebViewClient?>() }
    val dedicatedWebViewRef = remember { AtomicReference<TrustedJuntaWebView?>() }
    val dedicatedClientActive = remember { AtomicBoolean(false) }
    val pendingNormalUrl = remember { AtomicReference<String?>() }
    val discardHistory = remember { AtomicBoolean(false) }
    val navigationEpoch = remember { mutableLongStateOf(0L) }
    val navigationPolicy = remember { JuntaNavigationPolicy() }
    val clientAuthAuthorizer = remember {
        ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.releaseRegistry)
    }
    var activeProfileId by remember { mutableStateOf(ProfileId("junta-andalucia")) }
    var clientAuthGrant by remember { mutableStateOf<ClientAuthGrant?>(null) }
    var pendingRequest by remember { mutableStateOf<AfirmaRequest?>(null) }
    var blockedReason by remember { mutableStateOf<NavigationBlockReason?>(null) }
    var browserError by remember { mutableStateOf<BrowserErrorCode?>(null) }
    var compatibilityError by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(JuntaOriginPolicy.START_URL) }

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
    val callbacks = remember(onOpenExternal, onCancelSigning) {
        object : BrowserNavigationCallbacks {
            override fun openExternal(uri: Uri) {
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
            }

            override fun onTopLevelNavigationStarted(url: String) {
                advanceNavigationEpoch()
                onCancelSigning(SigningCancelReason.NAVIGATION, null)
            }

            override fun onTopLevelUrlChanged(url: String) {
                currentUrl = safeBrowserDisplayUrl(url)
                initiatorProfileForUrl(url)?.let { activeProfileId = it }
            }
        }
    }

    fun goBack() {
        if (clientAuthGrant != null) {
            val activeStartUrl = BuiltInSiteProfiles.releaseRegistry.profile(activeProfileId)
                ?.startUrl
                ?.toASCIIString()
                ?: JuntaOriginPolicy.START_URL
            pendingNormalUrl.set(activeStartUrl)
            clientAuthGrant = null
            abandonClientAuth()
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.NAVIGATION, null)
            return
        }
        advanceNavigationEpoch()
        onCancelSigning(SigningCancelReason.NAVIGATION, null)
        val webView = webViewRef.get()
        if (webView?.canGoBack() == true) webView.goBack() else onExitBrowser()
    }

    fun submitAddress(rawAddress: String) {
        val candidate = rawAddress.trim()
        val target = try {
            Uri.parse(candidate)
        } catch (_: Exception) {
            null
        }
        if (target == null || target.isOpaque ||
            !target.scheme.equals("https", ignoreCase = true) ||
            target.encodedUserInfo != null || target.host.isNullOrBlank()
        ) {
            blockedReason = NavigationBlockReason.INVALID_URL
            return
        }
        when (val decision = navigationPolicy.decide(candidate, currentUrl)) {
            NavigationDecision.AllowInWebView -> {
                initiatorProfileForUrl(candidate)?.let { activeProfileId = it }
                val leavingClientAuth = clientAuthGrant != null
                if (leavingClientAuth) pendingNormalUrl.set(candidate)
                clientAuthGrant = null
                abandonClientAuth()
                advanceNavigationEpoch()
                onCancelSigning(SigningCancelReason.NAVIGATION, null)
                browserError = null
                blockedReason = null
                if (!leavingClientAuth) webViewRef.get()?.loadUrl(candidate)
            }
            is NavigationDecision.OpenExternal -> callbacks.openExternal(decision.uri)
            is NavigationDecision.HandleAfirma -> {
                blockedReason = NavigationBlockReason.INVALID_URL
            }
            is NavigationDecision.Block -> {
                blockedReason = decision.reason
            }
        }
    }

    BackHandler(onBack = ::goBack)
    DisposableEffect(onCancelSigning) {
        onDispose {
            onCancelSigning(SigningCancelReason.BACKGROUND, null)
            bridgeRef.getAndSet(null)?.close()
            webViewRef.getAndSet(null)?.let { webView ->
                if (shouldCaptureBrowserState(discardHistory.get(), dedicatedClientActive.get())) {
                    stateHolder.capture(webView)
                }
                onWebViewChanged(null)
                webView.stopLoading()
                webView.destroy()
            }
            abandonClientAuth()
        }
    }

    BrowserLayout(
        currentUrl = currentUrl,
        certificateOwner = certificateState.summary.ownerName,
        onAddressSubmitted = ::submitAddress,
        onBack = ::goBack,
        onHome = {
            val leavingClientAuth = clientAuthGrant != null
            if (leavingClientAuth) pendingNormalUrl.set(JuntaOriginPolicy.START_URL)
            clientAuthGrant = null
            activeProfileId = ProfileId("junta-andalucia")
            abandonClientAuth()
            advanceNavigationEpoch()
            onCancelSigning(SigningCancelReason.NAVIGATION, null)
            browserError = null
            blockedReason = null
            currentUrl = JuntaOriginPolicy.START_URL
            if (!leavingClientAuth) webViewRef.get()?.loadUrl(JuntaOriginPolicy.START_URL)
        },
        onReload = {
            val activeStartUrl = BuiltInSiteProfiles.releaseRegistry.profile(activeProfileId)
                ?.startUrl
                ?.toASCIIString()
                ?: JuntaOriginPolicy.START_URL
            val leavingClientAuth = clientAuthGrant != null
            if (leavingClientAuth) {
                pendingNormalUrl.set(activeStartUrl)
                clientAuthGrant = null
                abandonClientAuth()
            }
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
            discardHistory.set(true)
            stateHolder.clear()
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
            notice?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        if (browserError != null) {
                            Button(onClick = {
                                onCancelSigning(SigningCancelReason.RELOAD, null)
                                browserError = null
                                webViewRef.get()?.reload()
                            }) {
                                Text(stringResource(R.string.browser_retry))
                            }
                        }
                    }
                }
            }
            key(clientAuthGrant != null) {
                AndroidView(
                    factory = {
                    TrustedJuntaWebView(context).also { webView ->
                        webView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        webViewRef.set(webView)
                        val tlsGrant = clientAuthGrant
                        if (tlsGrant == null) {
                            dedicatedClientActive.set(false)
                            onWebViewChanged(browserWebViewForPersistence(webView, dedicated = false))
                            val client = JuntaWebViewClient(
                                callbacks = callbacks,
                                logger = logger,
                                navigationPolicy = navigationPolicy,
                                clientAuthAuthorizer = clientAuthAuthorizer,
                                activeProfileId = { activeProfileId },
                                currentNavigationEpoch = { navigationEpoch.longValue },
                                onClientAuthTarget = { authorized ->
                                    advanceNavigationEpoch()
                                    onCancelSigning(SigningCancelReason.NAVIGATION, null)
                                    bridgeRef.getAndSet(null)?.close()
                                    dedicatedClientActive.set(true)
                                    onWebViewChanged(browserWebViewForPersistence(webView, dedicated = true))
                                    activeProfileId = authorized.profileId
                                    clientAuthGrant = ClientAuthGrant(
                                        authorized = authorized,
                                        navigationEpoch = navigationEpoch.longValue,
                                    )
                                },
                            )
                            webView.webViewClient = client
                            val attachment = WebMessageBridge(
                                logger = logger,
                                onAfirmaRequest = handleAfirmaRequest,
                                onMiniAppletRequest = { request, reply ->
                                    onMiniAppletRequest(request, reply)
                                },
                                onMiniAppletCancel = onMiniAppletCancel,
                                miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
                                currentNavigationEpoch = { navigationEpoch.longValue },
                                currentOrigin = {
                                    webView.url?.let { url ->
                                        runCatching {
                                            JuntaOriginPolicy.originFor(Uri.parse(url))
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
                            dedicatedClientActive.set(true)
                            onWebViewChanged(browserWebViewForPersistence(webView, dedicated = true))
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
                                stateHolder.restoreOrLoad(webView) { restoredUrl ->
                                    currentUrl = safeBrowserDisplayUrl(restoredUrl)
                                    initiatorProfileForUrl(restoredUrl)?.let { activeProfileId = it }
                                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserLayout(
    certificateOwner: String,
    currentUrl: String = JuntaOriginPolicy.START_URL,
    browserInsets: WindowInsets = BrowserWindowInsetsPolicy.current(),
    onAddressSubmitted: (String) -> Unit = {},
    onBack: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onChangeCertificate: () -> Unit,
    onLockCertificate: () -> Unit,
    onClearSession: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClearSession by remember { mutableStateOf(false) }
    var addressEditing by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    BrowserAddressBar(
                        currentUrl = currentUrl,
                        editing = addressEditing,
                        onEditingChange = { addressEditing = it },
                        onSubmit = onAddressSubmitted,
                    )
                },
                expandedHeight = BrowserToolbarHeight,
                windowInsets = browserInsets.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                modifier = Modifier.testTag(BROWSER_TOOLBAR_TAG),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (addressEditing) addressEditing = false else onBack()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Atrás"
                        },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            addressEditing = false
                            onHome()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Inicio"
                        },
                    ) { Text("⌂") }
                    IconButton(
                        onClick = {
                            addressEditing = false
                            onReload()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Recargar"
                        },
                    ) { Text("↻") }
                    Box {
                        IconButton(
                            onClick = {
                                addressEditing = false
                                menuExpanded = true
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Más opciones"
                            },
                        ) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_change_certificate)) },
                                onClick = {
                                    menuExpanded = false
                                    onChangeCertificate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_lock_certificate)) },
                                onClick = {
                                    menuExpanded = false
                                    onLockCertificate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_clear_session)) },
                                onClick = {
                                    menuExpanded = false
                                    confirmClearSession = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .testTag(BROWSER_BOTTOM_BAR_TAG)
                    .windowInsetsPadding(
                        browserInsets.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    ),
            ) {
                Text(
                    text = stringResource(R.string.browser_certificate_status, certificateOwner),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
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

internal fun browserWebViewForPersistence(webView: WebView?, dedicated: Boolean): WebView? =
    webView.takeUnless { dedicated }

internal fun shouldCaptureBrowserState(discardHistory: Boolean, dedicated: Boolean): Boolean =
    !discardHistory && !dedicated

internal fun initiatorProfileForUrl(rawUrl: String): ProfileId? = runCatching {
    BuiltInSiteProfiles.releaseRegistry.resolve(java.net.URI(rawUrl))
}.getOrNull()?.takeIf { resolution ->
    resolution.origin in resolution.profile.initiatorOrigins &&
        resolution.trustMode in setOf(
            TrustMode.TRUSTED_SIGNING,
            TrustMode.TRUSTED_CLIENT_AUTH,
        )
}?.profile?.profileId

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
