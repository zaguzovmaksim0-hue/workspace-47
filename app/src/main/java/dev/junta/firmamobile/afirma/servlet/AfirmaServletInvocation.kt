package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import java.io.Closeable
import java.net.URI

internal enum class AfirmaServletOperation { SIGN, COSIGN, COUNTERSIGN, BATCH, SELECT_CERTIFICATE }

/** Sensitive URI fields deliberately have no generated data-class toString. */
internal data class AfirmaServletMetadata(
    val operation: AfirmaServletOperation,
    val sourceOrigin: String?,
    val storageUrl: URI,
    val sessionId: String,
    val key: String?,
    val algorithm: SigningAlgorithm?,
    val detached: Boolean,
    val padesOptions: NativePadesOptions? = null,
    val xadesOptions: NativeXadesOptions? = null,
    val remoteOptions: NativeRemoteOptions? = null,
    val precalculatedHash: NativePrecalculatedHash? = null,
    val cadesPolicy: NativeCadesPolicy? = null,
    val externalContext: ExternalAfirmaContext? = null,
) {
    override fun toString(): String = "AfirmaServletMetadata(operation=$operation)"
}

internal class AfirmaServletInvocation(
    private val metadata: AfirmaServletMetadata,
    payload: ByteArray,
    cipherParameters: AfirmaAesParameters? = null,
    certificateConstraint: NativeCertificateConstraint? = null,
) : Closeable {
    val operation get() = metadata.operation
    val sourceOrigin get() = metadata.sourceOrigin
    val storageUrl get() = metadata.storageUrl
    val sessionId get() = metadata.sessionId
    val key get() = metadata.key
    val algorithm get() = metadata.algorithm
    val detached get() = metadata.detached
    val padesOptions get() = metadata.padesOptions
    val xadesOptions get() = metadata.xadesOptions
    val remoteOptions get() = metadata.remoteOptions
    val precalculatedHash get() = metadata.precalculatedHash
    val cadesPolicy get() = metadata.cadesPolicy
    val externalContext get() = metadata.externalContext
    init {
        require((sourceOrigin != null) != (externalContext != null)) { "Exactly one request context required" }
        require(cadesPolicy == null || operation in setOf(AfirmaServletOperation.SIGN, AfirmaServletOperation.COSIGN) &&
            padesOptions == null && xadesOptions == null && remoteOptions == null) { "Invalid local policy invocation" }
        require(metadata.precalculatedHash == null || operation == AfirmaServletOperation.SIGN &&
            padesOptions == null && xadesOptions == null && remoteOptions == null && detached &&
            metadata.precalculatedHash.accepts(payload, algorithm)) { "Invalid precomputed-hash invocation" }
    }
    private var ownedCertificateConstraint = certificateConstraint?.copy()
    val requiresExactCertificate: Boolean = certificateConstraint?.requiresExactCertificate ?: false
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
