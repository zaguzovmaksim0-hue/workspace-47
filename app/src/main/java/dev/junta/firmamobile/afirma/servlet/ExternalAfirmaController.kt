package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.security.BoundedReplayLedger
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.io.Closeable
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CoroutineScope

/** Main-thread core only: the Android caller must validate the VIEW envelope and provide
 * resumed/visible/focused/unlocked ownership plus admission shared with portals.
 * receive never queues a background delivery. prepare is local and performs no I/O.
 */
internal class ExternalAfirmaController(
    scope: CoroutineScope,
    private val canRespond: () -> Boolean,
    identityProvider: () -> UnlockedIdentity?,
    private val prepare: (AfirmaServletInvocation) -> PreparedAfirmaOperation,
    private val onPrompt: (AfirmaConsentPrompt?) -> Unit,
    monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) : Closeable {
    private var owner: ExternalAfirmaContext? = null
    private var prompt: AfirmaConsentPrompt? = null
    private var closed = false
    private val consent = AfirmaConsentController<ExternalAfirmaContext>(
        scope = scope,
        isCurrent = { candidate, _ -> !closed && owner === candidate && canRespond() },
        identityProvider = identityProvider,
        canRespond = { canRespond() },
        onPrompt = { prompt = it; onPrompt(it) },
        monotonicNanos = monotonicNanos,
    )

    val hasPending: Boolean get() = consent.hasPending

    fun receive(rawUri: String): Boolean {
        if (closed || consent.hasPending) return false
        return if (canRespond()) {
            when (val parsed = AfirmaServletInvocationParser.parseExternal(rawUri)) {
                is AfirmaServletParseResult.Accepted -> offer(parsed.invocation)
                else -> false
            }
        } else false
    }

    private fun offer(invocation: AfirmaServletInvocation): Boolean {
        val context = checkNotNull(invocation.externalContext)
        var operation: PreparedAfirmaOperation? = null
        val accepted = runCatching {
            check(!ExternalAfirmaReplay.ledger.contains(context.requestSha256))
            val prepared = prepare(invocation).also { operation = it }
            check(matchesReview(invocation, prepared.details))
            owner = context
            consent.offer(context, 0L, ExternalOnceOperation(prepared, context))
        }.getOrDefault(false)
        if (!accepted) {
            operation?.close()
            invocation.close()
            owner = null
        }
        return accepted
    }

    fun refreshIdentity() = consent.refreshIdentity()

    fun confirm(token: UUID): Boolean {
        val current = prompt?.takeIf { it.token == token && it.canConfirm } ?: return false
        val owned = canRespond() && owner === current.details.externalContext
        return if (owned) consent.confirm(token) else { consent.cancel(token); false }
    }

    /** External cancellation is local; never send an automatic server CANCEL. */
    fun cancel(token: UUID) = consent.cancel(token)
    fun dismiss(token: UUID) {
        if (prompt?.token != token) return
        consent.dismiss(token)
        owner = null
    }
    fun tick() = consent.tick()
    fun onBackground() = consent.onBackground(expectedExternalReturn = false)
    fun onForeground() = consent.onForeground(returnStillValid = false)
    override fun close() { closed = true; consent.close(); owner = null }
}

/** Reserve replay authority after identity/token validation, before any native effect. */
private class ExternalOnceOperation(
    private val operation: PreparedAfirmaOperation,
    private val context: ExternalAfirmaContext,
) : PreparedAfirmaOperation by operation {
    override suspend fun execute(identity: UnlockedIdentity,
        authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult =
        executeWithCheckpoints(identity, {}, authorizeUpload)

    override suspend fun executeWithCheckpoints(identity: UnlockedIdentity, checkpoint: suspend () -> Unit,
        authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
        checkpoint()
        check(ExternalAfirmaReplay.ledger.recordNew(context.requestSha256)) { "External request already consumed" }
        return operation.executeWithCheckpoints(identity, checkpoint, authorizeUpload)
    }
}

private object ExternalAfirmaReplay {
    val ledger = BoundedReplayLedger<String>(retention = Duration.ofMinutes(2), maxEntries = 128)
}

private fun matchesReview(invocation: AfirmaServletInvocation, details: AfirmaConsentDetails): Boolean {
    val bytes = invocation.payloadCopy()
    val digest = try { MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
        finally { bytes.fill(0) }
    val remote = invocation.remoteOptions
    val recipients = listOfNotNull(remote?.preUrl, remote?.postUrl).map(::recipient).distinct()
    val samePayload = details.payloadBytes == invocation.dataSize && details.payloadSha256 == digest
    val sameRecipients = details.destination == recipient(invocation.storageUrl) &&
        details.serviceDestinations == recipients
    val sameContext = details.externalContext === invocation.externalContext && details.sourceOrigin == null
    val operation = if (invocation.operation == AfirmaServletOperation.SELECT_CERTIFICATE) "selectcert"
        else invocation.operation.name.lowercase()
    val sameOperation = details.operation == operation
    return sameContext && samePayload && sameRecipients && sameOperation
}

private fun recipient(uri: URI): String =
    URI("https", null, uri.host, uri.port, uri.path, null, null).toASCIIString()
