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

internal class BrowserPopupState(
    val token: UUID,
    val entryOrigin: URI,
    val transport: BrowserPopupTransport,
) {
    var child: WebView? = null
}

internal data class PendingCertificateSelection(
    val request: CertificateSelectionBridgeRequest,
    val reply: CertificateSelectionReplyChannel,
    val certificateFingerprint: String,
    val certificateOwner: String,
)

internal data class PendingInPlaceClientAuthChallenge(
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
