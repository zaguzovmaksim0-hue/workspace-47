package dev.junta.firmamobile.ui

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.BrowserNavigationCallbacks
import dev.junta.firmamobile.browser.JuntaNavigationPolicy
import dev.junta.firmamobile.browser.JuntaWebViewClient
import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.browser.TrustedJuntaWebView
import dev.junta.firmamobile.browser.WebMessageBridge
import dev.junta.firmamobile.browser.WebMessageBridgeAttachment
import dev.junta.firmamobile.browser.WebViewStateHolder
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.security.SanitizedLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun BrowserScreen(
    certificateState: CertificateUiState.Unlocked,
    stateHolder: WebViewStateHolder,
    logger: SanitizedLogger,
    onExitBrowser: () -> Unit,
    onOpenExternal: (Uri) -> Unit,
    onChangeCertificate: () -> Unit,
    onLockCertificate: () -> Unit,
    onClearSession: () -> Unit,
    onWebViewChanged: (WebView?) -> Unit,
) {
    val context = LocalContext.current
    val webViewRef = remember { AtomicReference<TrustedJuntaWebView?>() }
    val bridgeRef = remember { AtomicReference<WebMessageBridgeAttachment?>() }
    val discardHistory = remember { AtomicBoolean(false) }
    var pendingRequest by remember { mutableStateOf<AfirmaRequest?>(null) }
    var blockedReason by remember { mutableStateOf<NavigationBlockReason?>(null) }
    var browserError by remember { mutableStateOf<BrowserErrorCode?>(null) }
    var compatibilityError by remember { mutableStateOf(false) }

    val handleAfirmaRequest: (AfirmaRequest) -> Unit = { request ->
        pendingRequest = request
    }
    val callbacks = remember(onOpenExternal) {
        object : BrowserNavigationCallbacks {
            override fun openExternal(uri: Uri) = onOpenExternal(uri)

            override fun onAfirmaRequest(request: AfirmaRequest) {
                handleAfirmaRequest(request)
            }

            override fun onNavigationBlocked(reason: NavigationBlockReason) {
                blockedReason = reason
            }

            override fun onBrowserError(error: BrowserErrorCode) {
                browserError = error
            }
        }
    }

    fun goBack() {
        val webView = webViewRef.get()
        if (webView?.canGoBack() == true) webView.goBack() else onExitBrowser()
    }

    BackHandler(onBack = ::goBack)
    DisposableEffect(Unit) {
        onDispose {
            bridgeRef.getAndSet(null)?.close()
            webViewRef.getAndSet(null)?.let { webView ->
                if (!discardHistory.get()) stateHolder.capture(webView)
                onWebViewChanged(null)
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    BrowserLayout(
        certificateOwner = certificateState.summary.ownerName,
        onBack = ::goBack,
        onHome = {
            browserError = null
            blockedReason = null
            webViewRef.get()?.loadUrl(JuntaOriginPolicy.START_URL)
        },
        onReload = {
            browserError = null
            webViewRef.get()?.reload()
        },
        onChangeCertificate = onChangeCertificate,
        onLockCertificate = onLockCertificate,
        onClearSession = {
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
                                browserError = null
                                webViewRef.get()?.reload()
                            }) {
                                Text(stringResource(R.string.browser_retry))
                            }
                        }
                    }
                }
            }
            AndroidView(
                factory = {
                    TrustedJuntaWebView(context).also { webView ->
                        val client = JuntaWebViewClient(
                            callbacks = callbacks,
                            logger = logger,
                            navigationPolicy = JuntaNavigationPolicy(),
                        )
                        webView.webViewClient = client
                        val attachment = WebMessageBridge(
                            logger = logger,
                            onAfirmaRequest = handleAfirmaRequest,
                        ).attach(webView)
                        bridgeRef.set(attachment)
                        if (!attachment.listenerAttached ||
                            !attachment.documentStartScriptAttached
                        ) {
                            webView.post { compatibilityError = true }
                        }
                        webViewRef.set(webView)
                        onWebViewChanged(webView)
                        stateHolder.restoreOrLoad(webView)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    pendingRequest?.let { request ->
        AfirmaObservationDialog(
            request = request,
            certificateOwner = certificateState.summary.ownerName,
            onDismiss = { pendingRequest = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserLayout(
    certificateOwner: String,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browser_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Atrás"
                        },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onHome,
                        modifier = Modifier.semantics {
                            contentDescription = "Inicio"
                        },
                    ) { Text("⌂") }
                    IconButton(
                        onClick = onReload,
                        modifier = Modifier.semantics {
                            contentDescription = "Recargar"
                        },
                    ) { Text("↻") }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
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
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
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
                .padding(padding),
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
