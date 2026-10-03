package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import java.io.Closeable
import java.net.URI

internal enum class AfirmaServletOperation { SIGN, COSIGN, COUNTERSIGN, BATCH, SELECT_CERTIFICATE }

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
    val padesOptions: NativePadesOptions? = null,
    val xadesOptions: NativeXadesOptions? = null,
    val remoteOptions: NativeRemoteOptions? = null,
    certificateConstraint: NativeCertificateConstraint? = null,
    val precalculatedHash: NativePrecalculatedHash? = null,
) : Closeable {
    init {
        require(precalculatedHash == null || operation == AfirmaServletOperation.SIGN &&
            padesOptions == null && xadesOptions == null && remoteOptions == null && detached &&
            precalculatedHash.accepts(payload, algorithm)) { "Invalid precomputed-hash invocation" }
    }
    private var ownedCertificateConstraint = certificateConstraint?.copy()
    val requiresExactCertificate: Boolean = certificateConstraint != null
    @Synchronized fun matchesCertificate(certificate: java.security.cert.X509Certificate): Boolean =
        ownedPayload != null && (ownedCertificateConstraint?.matches(certificate) ?: true)
    val pdfCoSign: Boolean get() = operation == AfirmaServletOperation.COSIGN && padesOptions != null
    val cadesCoSign: Boolean get() = operation == AfirmaServletOperation.COSIGN && padesOptions == null && xadesOptions == null && remoteOptions == null
    private var ownedCipher = cipherParameters?.copy()
    @Synchronized fun cipherCopy(): AfirmaAesParameters? = ownedCipher?.copy()
    private var ownedPayload: ByteArray? = payload.copyOf()
    val dataSize: Int = payload.size
    @Synchronized fun payloadCopy(): ByteArray = checkNotNull(ownedPayload) { "Invocation is closed" }.copyOf()
    @Synchronized override fun close() { ownedPayload?.fill(0); ownedPayload = null; ownedCipher?.close(); ownedCipher = null; ownedCertificateConstraint?.close(); ownedCertificateConstraint = null }
    override fun toString(): String = "AfirmaServletInvocation(operation=$operation, bytes=$dataSize)"
}

internal sealed interface AfirmaServletParseResult {
    class Accepted(val invocation: AfirmaServletInvocation) : AfirmaServletParseResult
    class Deferred(val invocation: AfirmaDeferredInvocation) : AfirmaServletParseResult
    data class Invalid(val reason: String) : AfirmaServletParseResult
    data class Unsupported(val reason: String) : AfirmaServletParseResult
}
