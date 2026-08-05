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

internal class ClientAuthRequestHandler(
    private val grant: ClientAuthGrant,
    private val identityProvider: () -> UnlockedIdentity?,
    private val currentNavigationEpoch: () -> Long,
    private val clearClientCertPreferences: () -> Unit,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val terminal = AtomicBoolean(false)
    private val preferencesCleared = AtomicBoolean(false)

    fun handle(request: ClientCertRequest) {
        if (!terminal.compareAndSet(false, true)) {
            request.ignore()
            return
        }
        val identity = identityProvider()
        if (identity == null || !grant.isValidFor(request, identity, clock)) {
            request.ignore()
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
        } catch (_: Exception) {
            if (!proceeded) request.ignore()
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
    ): Boolean {
        if (currentNavigationEpoch() != navigationEpoch || !clock.instant().isBefore(authorized.expiresAt)) {
            return false
        }
        val requestOrigin = authorized.policy.requestOrigins.singleOrNull() ?: return false
        if (!request.host.equals(requestOrigin.host, ignoreCase = true) || request.port != requestOrigin.port) {
            return false
        }
        val certificate = identity.certificate
        val algorithm = certificate.publicKey.algorithm.uppercase()
        if (algorithm !in authorized.certificateRules.allowedKeyAlgorithms) return false
        val offeredKeyTypes = request.keyTypes?.map(String::uppercase)?.toSet().orEmpty()
        if (offeredKeyTypes.isEmpty() || offeredKeyTypes.none { it == algorithm || (algorithm == "EC" && it == "ECDSA") }) {
            return false
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
        if (extendedKeyUsage != null &&
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
        if (!request.isForMainFrame || isAllowed(request.url.toString())) return false
        blockNavigation()
        return true
    }

    @Deprecated("Legacy callback is never allowed to expand the TLS grant")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        if (!isCurrentWebView(view)) {
            requestHandler.abandon()
            return true
        }
        if (isAllowed(url)) return false
        blockNavigation()
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
        if (isCurrentWebView(view)) callbacks.onBrowserError(BrowserErrorCode.SSL_ERROR)
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

    private fun blockNavigation() {
        requestHandler.abandon()
        callbacks.onNavigationBlocked(NavigationBlockReason.INVALID_URL)
    }

    private fun isAllowed(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host == null || uri.userInfo != null ||
            uri.port !in setOf(-1, 443) || uri.rawFragment != null
        ) {
            return false
        }
        val origin = runCatching { ExactOrigin.parse("https://${uri.host}") }.getOrNull() ?: return false
        val requestOrigins = grant.authorized.policy.requestOrigins
        val returnOrigins = grant.authorized.policy.sourceUrls.mapTo(linkedSetOf()) {
            ExactOrigin.parse("https://${it.host}")
        }
        return origin in requestOrigins || origin in returnOrigins
    }
}
