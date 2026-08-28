package dev.junta.firmamobile.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.matchesReturnUrl
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.security.auth.x500.X500Principal

internal data class ClientAuthGrant(
    val authorized: AuthorizedClientAuthTarget,
    val navigationEpoch: Long,
)

internal enum class ClientAuthRequestDiagnostic(val stage: String) {
    CHALLENGE_RECEIVED("client-cert-received"),
    PROCEEDED("client-cert-proceeded"),
    REJECTED_TERMINAL("client-cert-rejected-terminal"),
    REJECTED_NO_IDENTITY("client-cert-rejected-no-identity"),
    REJECTED_POLICY("client-cert-rejected-policy"),
    REJECTED_EXCEPTION("client-cert-rejected-exception"),
}

internal class ClientAuthRequestHandler(
    private val grant: ClientAuthGrant,
    private val identityProvider: () -> UnlockedIdentity?,
    private val currentNavigationEpoch: () -> Long,
    private val clearClientCertPreferences: () -> Unit,
    private val onDiagnostic: (ClientAuthRequestDiagnostic) -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) {
    private val terminal = AtomicBoolean(false)
    private val preferencesCleared = AtomicBoolean(false)

    fun handle(request: ClientCertRequest) {
        onDiagnostic(ClientAuthRequestDiagnostic.CHALLENGE_RECEIVED)
        if (!terminal.compareAndSet(false, true)) {
            request.ignore()
            onDiagnostic(ClientAuthRequestDiagnostic.REJECTED_TERMINAL)
            return
        }
        val identity = identityProvider()
        if (identity == null) {
            request.ignore()
            onDiagnostic(ClientAuthRequestDiagnostic.REJECTED_NO_IDENTITY)
            clearPreferencesOnce()
            return
        }
        if (!grant.isValidFor(request, identity, clock, monotonicNanos())) {
            request.ignore()
            onDiagnostic(ClientAuthRequestDiagnostic.REJECTED_POLICY)
            clearPreferencesOnce()
            return
        }
        var proceeded = false
        try {
            val chain = identity.chain.ifEmpty { listOf(identity.certificate) }.toTypedArray()
            identity.withPrivateKey { privateKey ->
                check(privateKey.algorithm.equals(identity.certificate.publicKey.algorithm, ignoreCase = true))
                proceeded = true
                request.proceed(privateKey, chain)
            }
            onDiagnostic(ClientAuthRequestDiagnostic.PROCEEDED)
        } catch (_: Exception) {
            if (!proceeded) request.ignore()
            onDiagnostic(ClientAuthRequestDiagnostic.REJECTED_EXCEPTION)
            clearPreferencesOnce()
        }
    }

    fun abandon() {
        terminal.compareAndSet(false, true)
        clearPreferencesOnce()
    }

    private fun clearPreferencesOnce() {
        if (preferencesCleared.compareAndSet(false, true)) clearClientCertPreferences()
    }

    private fun ClientAuthGrant.isValidFor(
        request: ClientCertRequest,
        identity: UnlockedIdentity,
        clock: Clock,
        nowNanos: Long,
    ): Boolean {
        if (currentNavigationEpoch() != navigationEpoch || authorized.isExpiredOrInvalid(nowNanos)) {
            return false
        }
        val requestOrigin = authorized.policy.requestOrigins.singleOrNull() ?: return false
        if (!request.host.equals(requestOrigin.host, ignoreCase = true) ||
            request.port != authorized.policy.requestPort
        ) {
            return false
        }
        val certificate = identity.certificate
        val algorithm = certificate.publicKey.algorithm.uppercase()
        if (algorithm !in authorized.certificateRules.allowedKeyAlgorithms) return false
        if (authorized.policy.requireOfferedKeyTypeMatch) {
            val offeredKeyTypes = request.keyTypes?.map(String::uppercase)?.toSet().orEmpty()
            if (offeredKeyTypes.isEmpty() || offeredKeyTypes.none { it == algorithm || (algorithm == "EC" && it == "ECDSA") }) {
                return false
            }
        }
        try {
            certificate.checkValidity(Date.from(clock.instant()))
        } catch (_: Exception) {
            return false
        }
        val keyUsage = certificate.keyUsage
        if (authorized.certificateRules.requireDigitalSignatureKeyUsage &&
            keyUsage != null && (keyUsage.isEmpty() || !keyUsage[0])
        ) {
            return false
        }
        val extendedKeyUsage = try {
            certificate.extendedKeyUsage
        } catch (_: Exception) {
            return false
        }
        if (authorized.policy.requireTlsClientAuthExtendedKeyUsage &&
            extendedKeyUsage != null &&
            TLS_CLIENT_AUTH_OID !in extendedKeyUsage && ANY_EXTENDED_KEY_USAGE_OID !in extendedKeyUsage
        ) {
            return false
        }
        val principals = request.principals?.toList().orEmpty()
        if (principals.isEmpty()) return authorized.policy.allowEmptyIssuerList
        val acceptableIssuerDer = principals.mapNotNull { principal ->
            (principal as? X500Principal)?.encoded
        }
        if (acceptableIssuerDer.size != principals.size) return false
        val chain = identity.chain.ifEmpty { listOf(certificate) }
        return chain.any { chainCertificate ->
            val issuer = chainCertificate.issuerX500Principal.encoded
            acceptableIssuerDer.any { acceptable -> MessageDigest.isEqual(issuer, acceptable) }
        }
    }

    private companion object {
        const val TLS_CLIENT_AUTH_OID = "1.3.6.1.5.5.7.3.2"
        const val ANY_EXTENDED_KEY_USAGE_OID = "2.5.29.37.0"
    }
}

/** A bridge-free, non-restored WebView client used only for one approved TLS flow. */
internal class ClientAuthWebViewClient(
    private val grant: ClientAuthGrant,
    private val requestHandler: ClientAuthRequestHandler,
    private val callbacks: BrowserNavigationCallbacks,
    private val isActiveWebView: (WebView) -> Boolean = { true },
) : WebViewClient() {
    private val initialTargetStarted = AtomicBoolean(false)

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        if (!isCurrentWebView(view)) {
            request.ignore()
            requestHandler.abandon()
            return
        }
        requestHandler.handle(request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!isCurrentWebView(view)) {
            requestHandler.abandon()
            return true
        }
        if (isAllowed(request.url.toString())) return false
        blockNavigation(notifyApplication = request.isForMainFrame)
        return true
    }

    @Deprecated("Legacy callback is never allowed to expand the TLS grant")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        if (!isCurrentWebView(view)) {
            requestHandler.abandon()
            return true
        }
        if (isAllowed(url)) return false
        blockNavigation(notifyApplication = false)
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (!isCurrentWebView(view)) {
            requestHandler.abandon()
            return
        }
        val isInitialTarget = url == grant.authorized.target.toASCIIString() &&
            initialTargetStarted.compareAndSet(false, true)
        if (!isInitialTarget) {
            requestHandler.abandon()
            callbacks.onTopLevelNavigationStarted(url)
        }
        callbacks.onTopLevelUrlChanged(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (!isCurrentWebView(view)) {
            requestHandler.abandon()
            return
        }
        callbacks.onTopLevelUrlChanged(url)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        requestHandler.abandon()
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        requestHandler.abandon()
        if (isCurrentWebView(view)) callbacks.onRenderProcessGone(view)
        return true
    }

    fun abandon() = requestHandler.abandon()

    private fun isCurrentWebView(view: WebView): Boolean = try {
        isActiveWebView(view)
    } catch (_: Exception) {
        false
    }

    private fun blockNavigation(notifyApplication: Boolean) {
        requestHandler.abandon()
        if (notifyApplication) {
            callbacks.onNavigationBlocked(NavigationBlockReason.INVALID_URL)
        }
    }

    private fun isAllowed(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host == null || uri.userInfo != null || uri.rawFragment != null) {
            return false
        }
        val effectivePort = if (uri.port == -1) 443 else uri.port
        if (effectivePort !in 1..65_535) return false
        val origin = runCatching { ExactOrigin.parse("https://${uri.host}") }.getOrNull() ?: return false
        val policy = grant.authorized.policy
        if (uri == grant.authorized.target) return true
        if (policy.returnUrlConstraints.isNotEmpty()) {
            return effectivePort == 443 && policy.matchesReturnUrl(uri)
        }
        val requestOrigins = policy.requestOrigins
        if (origin in requestOrigins && effectivePort == policy.requestPort) return true
        val returnOrigins = policy.sourceUrls.mapTo(linkedSetOf()) {
            ExactOrigin.parse("https://${it.host}")
        }
        return effectivePort == 443 && origin in returnOrigins
    }
}
