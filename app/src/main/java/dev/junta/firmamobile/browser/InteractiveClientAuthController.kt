package dev.junta.firmamobile.browser

import android.webkit.ClientCertRequest
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Locale
import java.util.UUID

internal enum class InteractiveClientAuthProblem {
    NO_CERTIFICATE,
    INCOMPATIBLE_CERTIFICATE,
    EXPIRED,
    RESPONSE_FAILED,
}

internal data class InteractiveClientAuthPrompt(
    val token: UUID,
    val server: String,
    val pageOrigin: String,
    val certificateOwner: String?,
    val problem: InteractiveClientAuthProblem?,
) {
    val canConfirm: Boolean get() = certificateOwner != null && problem == null
}

/**
 * Owns a real TLS callback, not a URL recipe. Call on the WebView/UI thread.
 * Unlock and explicit confirmation are separate; no URL load or POST replay
 * is performed here. A retained callback never retains a private key.
 */
internal class InteractiveClientAuthController<Owner : Any>(
    private val isCurrent: (Owner, Long) -> Boolean,
    private val identityProvider: () -> UnlockedIdentity?,
    private val canRespond: () -> Boolean,
    private val clearClientCertPreferences: () -> Unit,
    private val onPrompt: (InteractiveClientAuthPrompt?) -> Unit,
    private val onProblem: (InteractiveClientAuthProblem) -> Unit = {},
    private val scheduler: ClientCertPreferenceTimeoutScheduler = AndroidClientCertPreferenceTimeoutScheduler(),
    private val clock: Clock = Clock.systemUTC(),
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) {
    private data class Presentation(
        val fingerprint: String?,
        val owner: String?,
        val problem: InteractiveClientAuthProblem?,
    )

    private class Pending<Owner : Any>(
        val owner: Owner,
        val epoch: Long,
        val request: ClientCertRequest,
        val endpoint: Endpoint,
        val pageOrigin: String,
        val receivedAtNanos: Long,
        var token: UUID = UUID.randomUUID(),
        var presentation: Presentation? = null,
        var timeout: ClientCertPreferenceTimeoutHandle? = null,
    )

    private data class Endpoint(val host: String, val port: Int) {
        val display: String get() = if (port == 443) host else "$host:$port"
    }

    private var pending: Pending<Owner>? = null
    private var cachedFingerprint: String? = null
    private var cacheTimeout: ClientCertPreferenceTimeoutHandle? = null
    private var cacheToken: UUID? = null
    private var closed = false
    private var backgrounded = false

    fun offer(owner: Owner, epoch: Long, pageUrl: String, request: ClientCertRequest) {
        if (pending?.request === request) return
        val endpoint = runCatching { endpoint(request.host, request.port) }.getOrNull()
        val pageOrigin = publicPageOrigin(pageUrl)
        if (closed || backgrounded || !runCatching { isCurrent(owner, epoch) }.getOrDefault(false) ||
            !runCatching(canRespond).getOrDefault(false) || endpoint == null || pageOrigin == null || pending != null
        ) {
            ignore(request)
            return
        }
        val receivedAt = runCatching(monotonicNanos).getOrNull()
        if (receivedAt == null) { ignore(request); return }
        val created = Pending(owner, epoch, request, endpoint, pageOrigin, receivedAt)
        pending = created
        try {
            created.timeout = scheduler.schedule(PENDING_LIFETIME.toMillis()) {
                if (pending === created) {
                    cancelPending()
                    onProblem(InteractiveClientAuthProblem.EXPIRED)
                }
            }
        } catch (_: Exception) {
            cancelPending()
            onProblem(InteractiveClientAuthProblem.RESPONSE_FAILED)
            return
        }
        refreshIdentity()
    }

    /** Called after unlock/lock/import and on return; it never approves a request. */
    fun refreshIdentity() {
        val identity = runCatching(identityProvider).getOrNull()
        if (cachedFingerprint != null && (fingerprint(identity) != cachedFingerprint ||
                runCatching { identity?.certificate?.checkValidity(java.util.Date.from(clock.instant())) }.isFailure)
        ) revokeCachedChoice()
        val current = pending ?: return
        if (!ownedAndLive(current)) {
            cancelPending()
            return
        }
        publish(current, presentation(current.request, identity))
    }

    fun confirm(token: UUID): Boolean {
        val current = pending?.takeIf { it.token == token } ?: return false
        if (backgrounded || !runCatching(canRespond).getOrDefault(false)) return false
        if (!ownedAndLive(current)) {
            cancelPending()
            onProblem(InteractiveClientAuthProblem.EXPIRED)
            return false
        }
        val actualEndpoint = runCatching { endpoint(current.request.host, current.request.port) }.getOrNull()
        if (actualEndpoint != current.endpoint) {
            cancelPending()
            onProblem(InteractiveClientAuthProblem.RESPONSE_FAILED)
            return false
        }
        val identity = runCatching(identityProvider).getOrNull()
        val observed = presentation(current.request, identity)
        if (observed != current.presentation || observed.problem != null || identity == null) {
            // An old UI click must not approve a replacement identity, even if
            // its human-readable subject is the same as the previous one.
            publish(current, observed)
            return false
        }
        pending = null
        current.timeout?.cancel()
        onPrompt(null)
        var replyAttempted = false
        return try {
            val chain = identity.chain.ifEmpty { listOf(identity.certificate) }.toTypedArray()
            identity.withPrivateKey { privateKey ->
                check(privateKey.algorithm.equals(identity.certificate.publicKey.algorithm, ignoreCase = true))
                replyAttempted = true
                current.request.proceed(privateKey, chain)
            }
            cachedFingerprint = observed.fingerprint
            // Several endpoints may be approved in one browser session. A new
            // endpoint cannot silently extend the oldest cached selection.
            if (cacheToken == null) {
                val token = UUID.randomUUID()
                cacheToken = token
                cacheTimeout = scheduler.schedule(CACHED_CHOICE_LIFETIME.toMillis()) {
                    if (cacheToken == token) revokeCachedChoice()
                }
            }
            true
        } catch (_: Exception) {
            // proceed may have reached Chromium before throwing. Never send a
            // second terminal reply to that same callback.
            if (!replyAttempted) ignore(current.request)
            cachedFingerprint = observed.fingerprint
            revokeCachedChoice()
            onProblem(InteractiveClientAuthProblem.RESPONSE_FAILED)
            false
        }
    }

    /** SslError has no request ownership flag. Only a failure at the pending
     * callback's own endpoint may terminate that callback; unrelated subresource
     * failures must not become errors for the entire open page. */
    fun onServerTlsError(owner: Owner, epoch: Long, failedUrl: String?) {
        val current = pending ?: return
        if (current.owner !== owner || current.epoch != epoch || !ownedAndLive(current)) return
        val failed = runCatching {
            val uri = URI(failedUrl ?: return)
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return
            endpoint(uri.host ?: return, if (uri.port == -1) 443 else uri.port)
        }.getOrNull()
        if (failed == current.endpoint) cancelPending()
    }

    fun cancel(token: UUID) {
        if (pending?.token == token) cancelPending()
    }

    fun cancelPending() {
        val current = pending ?: return
        pending = null
        current.timeout?.cancel()
        onPrompt(null)
        ignore(current.request)
    }

    /** Cache clearing deliberately does not mean deleting cookies or logging out. */
    fun revokeCachedChoice() {
        cacheTimeout?.cancel()
        cacheTimeout = null
        cacheToken = null
        if (cachedFingerprint == null) return
        cachedFingerprint = null
        clearClientCertPreferences()
    }

    fun onBackground(expectedExternalReturn: Boolean) {
        backgrounded = true
        if (!expectedExternalReturn) {
            cancelPending()
            revokeCachedChoice()
        }
    }

    fun onForeground(expectedReturnStillValid: Boolean) {
        if (backgrounded && !expectedReturnStillValid) {
            cancelPending()
            revokeCachedChoice()
        }
        backgrounded = false
        refreshIdentity()
    }

    fun close() {
        closed = true
        cancelPending()
        revokeCachedChoice()
    }

    private fun ownedAndLive(current: Pending<Owner>): Boolean = runCatching {
        !closed && isCurrent(current.owner, current.epoch) &&
            !MonotonicSecurityTime.isExpiredOrInvalid(
                current.receivedAtNanos, PENDING_LIFETIME.toNanos(), monotonicNanos(),
            )
    }.getOrDefault(false)

    private fun publish(current: Pending<Owner>, value: Presentation) {
        if (pending !== current) return
        if (current.presentation != value) current.token = UUID.randomUUID()
        current.presentation = value
        onPrompt(InteractiveClientAuthPrompt(
            current.token, current.endpoint.display, current.pageOrigin, value.owner, value.problem,
        ))
    }

    private fun presentation(request: ClientCertRequest, identity: UnlockedIdentity?): Presentation {
        if (identity == null) return Presentation(null, null, InteractiveClientAuthProblem.NO_CERTIFICATE)
        val fingerprint = fingerprint(identity)
        val compatible = fingerprint != null && runCatching {
            clientCertificateRejection(request, identity, clock = clock) == null
        }.getOrDefault(false)
        return Presentation(fingerprint, identity.summary.ownerName,
            if (compatible) null else InteractiveClientAuthProblem.INCOMPATIBLE_CERTIFICATE)
    }

    private fun fingerprint(identity: UnlockedIdentity?): String? = runCatching {
        val bytes = identity?.certificate?.encoded ?: return null
        try {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            try { java.util.Base64.getEncoder().encodeToString(digest) } finally { digest.fill(0) }
        } finally { bytes.fill(0) }
    }.getOrNull()

    private fun ignore(request: ClientCertRequest) {
        runCatching { request.ignore() }
    }

    private companion object {
        val PENDING_LIFETIME: Duration = Duration.ofMinutes(5)
        val CACHED_CHOICE_LIFETIME: Duration = Duration.ofMinutes(10)
        val DNS_NAME = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")

        fun endpoint(rawHost: String, port: Int): Endpoint? {
            val host = rawHost.lowercase(Locale.ROOT)
            if (host.length > 253 || port !in 1..65535 || !DNS_NAME.matches(host) ||
                host.all { it.isDigit() || it == '.' } || host.endsWith(".localhost") || host.endsWith(".local")
            ) return null
            return Endpoint(host, port)
        }

        fun publicPageOrigin(raw: String): String? = runCatching {
            if (raw.length > 16384 || raw.any(Char::isISOControl)) return null
            val uri = URI(raw)
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.isOpaque || uri.userInfo != null) return null
            val endpoint = endpoint(uri.host ?: return null, if (uri.port == -1) 443 else uri.port) ?: return null
            "https://${endpoint.display}"
        }.getOrNull()
    }
}
