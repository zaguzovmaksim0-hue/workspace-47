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
internal fun browserNotice(
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
    browserError == BrowserErrorCode.BLANK_DOCUMENT ->
        stringResource(R.string.browser_blank_document_error)
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
    globalClearResult: Boolean?,
): LiveRegionMode = when {
    clientCertPreferenceState == ClientCertPreferenceBarrierState.CLEARING -> LiveRegionMode.Polite
    clientCertPreferenceState == ClientCertPreferenceBarrierState.FAILED -> LiveRegionMode.Assertive
    compatibilityError -> LiveRegionMode.Assertive
    browserError != null -> LiveRegionMode.Assertive
    blockedReason != null -> LiveRegionMode.Assertive
    globalClearResult == true -> LiveRegionMode.Polite
    globalClearResult == false -> LiveRegionMode.Assertive
    else -> LiveRegionMode.Assertive
}

@Composable
internal fun browserDataNotice(
    siteClearResult: SiteClearResult?,
    globalClearResult: Boolean?,
): String? = when {
    globalClearResult == true -> stringResource(R.string.browser_delete_all_data_success)
    globalClearResult == false -> stringResource(R.string.browser_delete_all_data_failed)
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
