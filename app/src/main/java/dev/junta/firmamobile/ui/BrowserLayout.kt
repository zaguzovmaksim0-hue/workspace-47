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
    var showSiteInformation by remember(currentUrl) { mutableStateOf(false) }
    var confirmClearCurrentSite by remember { mutableStateOf(false) }
    var confirmClearSession by remember { mutableStateOf(false) }
    var confirmDeleteAllData by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            IndustrialBrowserTopBar(
                host = BrowserAddressPresentation.hostOf(currentUrl),
                onBack = onBack,
                onHome = onHome,
                onReload = onReload,
                onChangeCertificate = onChangeCertificate,
                onLockCertificate = onLockCertificate,
                onClearCurrentSiteRequested = { confirmClearCurrentSite = true },
                onClearSessionRequested = { confirmClearSession = true },
                onDeleteAllBrowserDataRequested = { confirmDeleteAllData = true },
                onOpenInBrowser = onOpenInBrowser,
                onShowSiteInformation = { showSiteInformation = true },
                windowInsets = browserInsets.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                modifier = Modifier.testTag(BROWSER_TOOLBAR_TAG),
            )
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

    if (showSiteInformation) {
        BrowserSiteInformationDialog(
            profileName = profileName,
            host = BrowserAddressPresentation.hostOf(currentUrl),
            trustLabel = trustLabel,
            publicBrowsing = publicBrowsing,
            webAuthnState = webAuthnState,
            onOpenInBrowser = onOpenInBrowser?.let { open ->
                { showSiteInformation = false; open() }
            },
            onDismiss = { showSiteInformation = false },
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
