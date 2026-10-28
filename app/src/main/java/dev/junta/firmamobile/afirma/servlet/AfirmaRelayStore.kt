package dev.junta.firmamobile.afirma.servlet

import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class AfirmaRelayStore(
    private val clock: () -> Long = System::nanoTime,
) {
    data class Scope(val epoch: Long, val pageOrigin: String) {
        init {
            requireHttpsOrigin(pageOrigin)
        }

        internal fun normalized(): Scope = copy(pageOrigin = requireHttpsOrigin(pageOrigin))
    }

    sealed interface PollResult {
        data class Ready(val text: String) : PollResult
        data object Wait : PollResult
        data object Pass : PollResult
    }

    private val mutex = Mutex()
    private val sessionRegistry = SessionRegistry(clock)
    private val uploadRegistry = UploadRegistry(clock)
    private val blockedOrigins = HashMap<Scope, MutableSet<String>>()

    suspend fun markBlocked(scope: Scope, servletOrigin: String): Boolean = mutex.withLock {
        val normalizedScope = scope.normalized()
        val origin = requireHttpsOrigin(servletOrigin)
        val origins = blockedOrigins.getOrPut(normalizedScope) { mutableSetOf() }
        when {
            origin in origins -> true
            origins.size >= MAX_BLOCKED_ORIGINS -> false
            else -> origins.add(origin)
        }
    }

    suspend fun isBlocked(scope: Scope, servletOrigin: String): Boolean = mutex.withLock {
        blockedOrigins[scope.normalized()]?.contains(requireHttpsOrigin(servletOrigin)) == true
    }

    suspend fun expectResult(scope: Scope, sessionId: String): Boolean =
        sessionRegistry.expect(scope.normalized(), sessionId)

    suspend fun offerResult(scope: Scope, sessionId: String, text: String): Deferred<Boolean>? =
        sessionRegistry.offer(scope.normalized(), sessionId, text)

    suspend fun poll(scope: Scope, sessionId: String): PollResult =
        sessionRegistry.poll(scope.normalized(), sessionId)

    suspend fun awaitReceipt(receipt: Deferred<Boolean>, timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) { receipt.await() } ?: false

    suspend fun withdraw(scope: Scope, sessionId: String): Boolean =
        sessionRegistry.withdraw(scope.normalized(), sessionId)

    suspend fun putUpload(scope: Scope, fileId: String, data: String): Boolean =
        uploadRegistry.put(scope.normalized(), fileId, data)

    suspend fun takeUpload(scope: Scope, fileId: String): String? =
        uploadRegistry.take(scope.normalized(), fileId)

    suspend fun invalidate(): Unit = mutex.withLock {
        blockedOrigins.clear()
        sessionRegistry.invalidate()
        uploadRegistry.invalidate()
    }

    private class Session(val createdAtNanos: Long) {
        var text: String? = null
        val consumed = CompletableDeferred<Boolean>()
    }

    private class SessionRegistry(private val clock: () -> Long) {
        private val mutex = Mutex()
        private val sessions = HashMap<Scope, HashMap<String, Session>>()

        suspend fun expect(scope: Scope, sessionId: String): Boolean = mutex.withLock {
            if (!isValidId(sessionId)) return@withLock false
            val bySession = sessions.getOrPut(scope) { HashMap() }
            if (bySession.containsKey(sessionId)) {
                false
            } else {
                bySession[sessionId] = Session(clock())
                true
            }
        }

        suspend fun offer(scope: Scope, sessionId: String, text: String): Deferred<Boolean>? = mutex.withLock {
            if (!isValidId(sessionId) || text.isEmpty() || text.length > AFIRMA_MAX_RESULT_CHARS) {
                return@withLock null
            }
            val session = sessions[scope]?.get(sessionId)
            if (session == null || session.text != null) {
                null
            } else {
                session.text = text
                session.consumed
            }
        }

        suspend fun poll(scope: Scope, sessionId: String): PollResult = mutex.withLock {
            val session = sessions[scope]?.get(sessionId) ?: return@withLock PollResult.Pass
            val text = session.text
            when {
                text != null -> {
                    remove(scope, sessionId, consumed = true)
                    PollResult.Ready(text)
                }
                clock() - session.createdAtNanos < RESULT_TTL_NANOS -> PollResult.Wait
                else -> {
                    remove(scope, sessionId, consumed = false)
                    PollResult.Pass
                }
            }
        }

        suspend fun withdraw(scope: Scope, sessionId: String): Boolean = mutex.withLock {
            remove(scope, sessionId, consumed = false)
        }

        suspend fun invalidate(): Unit = mutex.withLock {
            sessions.values.forEach { bySession ->
                bySession.values.forEach { it.consumed.complete(false) }
            }
            sessions.clear()
        }

        private fun remove(scope: Scope, sessionId: String, consumed: Boolean): Boolean {
            val bySession = sessions[scope]
            val session = bySession?.remove(sessionId)
            if (session == null) return false
            session.consumed.complete(consumed)
            if (bySession.isEmpty()) sessions.remove(scope)
            return true
        }
    }

    private class PendingUpload(
        val scope: Scope,
        val fileId: String,
        val data: String,
        val expiresAtNanos: Long,
    )

    private class UploadRegistry(private val clock: () -> Long) {
        private val mutex = Mutex()
        private var pendingUpload: PendingUpload? = null

        suspend fun put(scope: Scope, fileId: String, data: String): Boolean = mutex.withLock {
            purgeExpired()
            val exceedsWireLimit =
                data.toByteArray(Charsets.UTF_8).size > AfirmaDeferredInvocation.MAX_WIRE_BYTES
            if (!isValidId(fileId) || exceedsWireLimit || pendingUpload != null) {
                false
            } else {
                pendingUpload = PendingUpload(scope, fileId, data, clock() + UPLOAD_TTL_NANOS)
                true
            }
        }

        suspend fun take(scope: Scope, fileId: String): String? = mutex.withLock {
            purgeExpired()
            val upload = pendingUpload
            if (upload == null || upload.scope != scope || upload.fileId != fileId) {
                null
            } else {
                pendingUpload = null
                upload.data
            }
        }

        suspend fun invalidate(): Unit = mutex.withLock {
            pendingUpload = null
        }

        private fun purgeExpired() {
            val upload = pendingUpload
            if (upload != null && clock() >= upload.expiresAtNanos) pendingUpload = null
        }
    }
}

private fun isValidId(value: String): Boolean = AfirmaServletInvocationParser.SESSION_ID.matches(value)

private fun requireHttpsOrigin(value: String): String {
    val uri = runCatching { URI(value) }.getOrNull()
        ?: throw IllegalArgumentException("Invalid HTTPS origin")
    val host = uri.host ?: throw IllegalArgumentException("Invalid HTTPS origin")
    require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS origins are accepted" }
    require(!uri.isOpaque) { "Invalid HTTPS origin" }
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
        "Invalid HTTPS origin"
    }
    require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "Invalid HTTPS origin" }
    val port = uri.port
    require(port == -1 || port in MIN_PORT..MAX_PORT) { "Invalid HTTPS origin" }
    return when {
        port == -1 || port == DEFAULT_HTTPS_PORT -> "https://$host"
        else -> "https://$host:$port"
    }
}

private const val MAX_BLOCKED_ORIGINS = 4
private const val RESULT_TTL_NANOS = 10L * 60L * 1_000_000_000L
private const val UPLOAD_TTL_NANOS = 2L * 60L * 1_000_000_000L
private const val DEFAULT_HTTPS_PORT = 443
private const val MIN_PORT = 1
private const val MAX_PORT = 65535
