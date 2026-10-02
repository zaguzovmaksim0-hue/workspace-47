package dev.junta.firmamobile.browser

import java.util.UUID

/** Adapter for one platform callback. Implementations run on the UI thread. */
interface HttpAuthReply {
    fun proceed(username: String, password: String)
    fun cancel()
}

internal data class BrowserHttpAuthPrompt(val token: UUID, val scope: HttpAuthScope)

/** Owns a real request, never a saved username/password or a synthetic retry.
 * All access is confined to the WebView/UI thread. */
internal class BrowserHttpAuthController<Owner : Any>(
    private val isCurrent: (Owner, Long) -> Boolean,
    private val canRespond: () -> Boolean,
    private val onPrompt: (BrowserHttpAuthPrompt?) -> Unit,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private class Pending<Owner>(
        val owner: Owner, val epoch: Long, val scope: HttpAuthScope,
        val reply: HttpAuthReply, val born: Long,
    ) {
        val token: UUID = UUID.randomUUID()
        var answered = false
    }
    private var pending: Pending<Owner>? = null
    private var closed = false
    val hasPending: Boolean get() = pending != null

    fun offer(owner: Owner, epoch: Long, pageUrl: String, host: String, realm: String, reply: HttpAuthReply): Boolean {
        if (pending?.reply === reply) return true
        val scope = HttpAuthScope.from(pageUrl, host, realm)
        val born = runCatching(nowNanos).getOrNull()
        if (closed || scope == null || born == null || pending != null ||
            !runCatching { isCurrent(owner, epoch) && canRespond() }.getOrDefault(false)) {
            runCatching { reply.cancel() }
            return false
        }
        val request = Pending(owner, epoch, scope, reply, born)
        pending = request
        try {
            onPrompt(BrowserHttpAuthPrompt(request.token, scope))
        } catch (_: Exception) {
            cancelRequest(request)
            return false
        }
        if (pending !== request) return false
        if (!live(request) || !ready()) { cancelRequest(request); return false }
        return true
    }

    fun confirm(token: UUID, username: String, password: String): Boolean {
        val request = pending?.takeIf { it.token == token } ?: return false
        if (!live(request) || !ready()) { cancelRequest(request); return false }
        if (!validCredentials(username, password)) return false
        pending = null
        try {
            onPrompt(null)
        } catch (_: Exception) {
            cancelReply(request)
            return false
        }
        // UI observers can synchronously navigate or offer another challenge.
        if (pending != null || !live(request) || !ready()) { cancelReply(request); return false }
        request.answered = true
        return try {
            request.reply.proceed(username, password)
            true
        } catch (_: Exception) {
            // Chromium might have received proceed already. Never answer twice.
            false
        }
    }

    fun cancel(token: UUID) { pending?.takeIf { it.token == token }?.let(::cancelRequest) }
    fun invalidate() { pending?.let(::cancelRequest) }
    fun tick() { pending?.takeIf { !live(it) || !ready() }?.let(::cancelRequest) }
    fun close() { closed = true; invalidate() }

    private fun ready() = runCatching(canRespond).getOrDefault(false)
    private fun live(request: Pending<Owner>) = runCatching {
        val elapsed = nowNanos() - request.born
        !closed && elapsed >= 0L && elapsed < LIFETIME_NANOS && isCurrent(request.owner, request.epoch)
    }.getOrDefault(false)

    private fun cancelRequest(request: Pending<Owner>) {
        if (pending === request) {
            pending = null
            runCatching { onPrompt(null) }
        }
        cancelReply(request)
    }
    private fun cancelReply(request: Pending<Owner>) {
        if (request.answered) return
        request.answered = true
        runCatching { request.reply.cancel() }
    }
    companion object {
        private const val LIFETIME_NANOS = 120_000_000_000L
        fun validCredentials(username: String, password: String): Boolean =
            username.length in 1..1024 && password.length <= 4096 &&
                username.none(::lineControl) && password.none(::lineControl)
        private fun lineControl(ch: Char) = ch == '\u0000' || ch == '\r' || ch == '\n'
    }
}
