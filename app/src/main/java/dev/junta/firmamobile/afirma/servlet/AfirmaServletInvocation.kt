package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import java.io.Closeable
import java.net.URI

internal enum class AfirmaServletOperation { SIGN, SELECT_CERTIFICATE }

/** Sensitive URI fields deliberately have no generated data-class toString. */
internal class AfirmaServletInvocation(
    val operation: AfirmaServletOperation,
    val sourceOrigin: String,
    val storageUrl: URI,
    val sessionId: String,
    val key: String?,
    val algorithm: SigningAlgorithm?,
    val detached: Boolean,
    payload: ByteArray,
    cipherParameters: AfirmaAesParameters? = null,
) : Closeable {
    private var ownedCipher = cipherParameters?.copy()
    @Synchronized fun cipherCopy(): AfirmaAesParameters? = ownedCipher?.copy()
    private var ownedPayload: ByteArray? = payload.copyOf()
    val dataSize: Int = payload.size
    @Synchronized fun payloadCopy(): ByteArray = checkNotNull(ownedPayload) { "Invocation is closed" }.copyOf()
    @Synchronized override fun close() { ownedPayload?.fill(0); ownedPayload = null; ownedCipher?.close(); ownedCipher = null }
    override fun toString(): String = "AfirmaServletInvocation(operation=$operation, bytes=$dataSize)"
}

internal sealed interface AfirmaServletParseResult {
    class Accepted(val invocation: AfirmaServletInvocation) : AfirmaServletParseResult
    class Deferred(val invocation: AfirmaDeferredInvocation) : AfirmaServletParseResult
    data class Invalid(val reason: String) : AfirmaServletParseResult
    data class Unsupported(val reason: String) : AfirmaServletParseResult
}
