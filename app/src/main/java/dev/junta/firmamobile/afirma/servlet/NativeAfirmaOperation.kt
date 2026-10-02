package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.net.URI
import java.time.Clock
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Joins syntax, original CMS signing, wire encoding and the one approved
 * storage upload. No profile ID and no protocol-specific site URL recipe. */
internal class NativeAfirmaOperation(
    private val invocation: AfirmaServletInvocation,
    private val transport: AfirmaResultTransport = AfirmaServletTransport(),
    private val clock: Clock = Clock.systemUTC(),
    private val engine: NativeCadesEngine = NativeCadesEngine(clock = clock),
    private val padesEngine: NativePadesEngine = NativePadesEngine(clock = clock),
) : PreparedAfirmaOperation {
    private val lock = Any()
    private var closed = false
    private val invoked = AtomicBoolean(false)
    override val details: AfirmaConsentDetails = run {
        val payload = invocation.payloadCopy()
        try {
            AfirmaConsentDetails(
                sourceOrigin = invocation.sourceOrigin,
                destination = safeDestination(invocation.storageUrl),
                operation = if (invocation.operation == AfirmaServletOperation.SIGN) "sign" else "selectcert",
                format = if (invocation.operation == AfirmaServletOperation.SIGN) {
                    invocation.padesOptions?.let { "PDF · PAdES · ${it.subFilter}" }
                        ?: if (invocation.detached) "CAdES · detached" else "CAdES · attached"
                } else null,
                algorithm = invocation.algorithm?.wireName(),
                payloadBytes = payload.size,
                payloadSha256 = if (invocation.operation == AfirmaServletOperation.SIGN) {
                    MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
                } else null,
            )
        } finally { payload.fill(0) }
    }

    override fun certificateCompatible(identity: UnlockedIdentity): Boolean = runCatching {
        identity.certificate.checkValidity(Date.from(clock.instant()))
        if (invocation.operation == AfirmaServletOperation.SIGN) {
            val usage = identity.certificate.keyUsage
            identity.certificate.publicKey.algorithm.equals("RSA", true) &&
                (usage == null || usage.getOrElse(0) { false } || usage.getOrElse(1) { false })
        } else true
    }.getOrDefault(false)

    override suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
        check(invoked.compareAndSet(false, true)) { "Operation was already started" }
        val snapshot = synchronized(lock) {
            check(!closed) { "Operation is closed" }
            Snapshot(invocation.operation, invocation.storageUrl, invocation.sessionId, invocation.key,
                invocation.algorithm, invocation.detached, invocation.payloadCopy(), invocation.cipherCopy(), invocation.padesOptions)
        }
        try {
            currentCoroutineContext().ensureActive()
            check(certificateCompatible(identity)) { "Certificate is not compatible" }
            val result = withContext(Dispatchers.Default) {
                currentCoroutineContext().ensureActive()
                val certificate = identity.certificate.encoded
                try {
                    val certResult = snapshot.cipher?.encode(certificate) ?: AfirmaIntermediateCipher.encode(certificate, snapshot.key)
                    if (snapshot.operation == AfirmaServletOperation.SELECT_CERTIFICATE) certResult else {
                        val generated = snapshot.pades?.let { options ->
                            padesEngine.sign(snapshot.payload, identity, checkNotNull(snapshot.algorithm), options)
                        } ?: engine.sign(snapshot.payload, identity, checkNotNull(snapshot.algorithm), snapshot.detached)
                        when (val signed = generated) {
                            is LocalSignatureResult.Success -> signed.signature.use { signature ->
                                currentCoroutineContext().ensureActive()
                                // Upstream consumer expects certificate first, signature second,
                                // each independently encoded/encrypted, separated by a pipe.
                                certResult + "|" + signature.withBytes { snapshot.cipher?.encode(it) ?: AfirmaIntermediateCipher.encode(it, snapshot.key) }
                            }
                            is LocalSignatureResult.Failure -> error("Native signature validation failed")
                        }
                    }
                } finally { certificate.fill(0) }
            }
            currentCoroutineContext().ensureActive()
            synchronized(lock) { check(!closed) { "Operation was cancelled" } }
            authorizeUpload()
            currentCoroutineContext().ensureActive()
            synchronized(lock) { check(!closed) { "Operation was cancelled during authorization" } }
            return transport.store(snapshot.endpoint, snapshot.sessionId, result)
        } finally { snapshot.payload.fill(0); snapshot.cipher?.close() }
    }

    override suspend fun notifyCancellation(authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
        check(invoked.compareAndSet(false, true)) { "Operation was already started" }
        try {
            val (endpoint, sessionId) = synchronized(lock) {
                check(!closed) { "Operation is closed" }
                val target = invocation.storageUrl to invocation.sessionId
                // Release document and cipher buffers before awaiting authorization.
                invocation.close()
                target
            }
            currentCoroutineContext().ensureActive()
            authorizeUpload()
            currentCoroutineContext().ensureActive()
            synchronized(lock) { check(!closed) { "Operation was cancelled during authorization" } }
            return transport.store(endpoint, sessionId, "CANCEL")
        } finally { close() }
    }

    override fun close() = synchronized(lock) {
        if (!closed) { closed = true; invocation.close() }
    }

    private class Snapshot(val operation: AfirmaServletOperation, val endpoint: URI, val sessionId: String,
        val key: String?, val algorithm: SigningAlgorithm?, val detached: Boolean, val payload: ByteArray,
        val cipher: AfirmaAesParameters?, val pades: NativePadesOptions?)

    private companion object {
        fun SigningAlgorithm.wireName() = when (this) {
            SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
            SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
            SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
        }
        fun safeDestination(uri: URI): String = URI("https", null, uri.host, uri.port, uri.path, null, null).toASCIIString()
    }
}
