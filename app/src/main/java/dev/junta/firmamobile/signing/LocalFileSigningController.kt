package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateSigningSnapshot
import dev.junta.firmamobile.verification.OFFLINE_CMS_MAX_CONTENT_BYTES
import dev.junta.firmamobile.verification.OfflineCmsStatus
import dev.junta.firmamobile.verification.OfflineCmsVerifier
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface LocalFileSigningState {
    data object Idle : LocalFileSigningState
    data object Loading : LocalFileSigningState
    class Review(
        val token: Long,
        val name: String,
        val document: ActualDocumentMetadata,
        val certificateOwner: String,
    ) : LocalFileSigningState
    data object Signing : LocalFileSigningState
    class Ready(val token: Long, val name: String) : LocalFileSigningState
    data object Saving : LocalFileSigningState
    data object Saved : LocalFileSigningState
    data object Failed : LocalFileSigningState
}

/** Owns one local selection; no portal request, network, or filesystem staging. */
internal class LocalFileSigningController(
    private val session: CertificateSession,
    private val signer: CadesSigner = BouncyCastleCadesSigner(),
) : Closeable {
    private val mutableState = MutableStateFlow<LocalFileSigningState>(LocalFileSigningState.Idle)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var document: ByteArray? = null
    private var certificate: CertificateSigningSnapshot? = null
    private var signature: LocalSignature? = null

    @Synchronized
    fun begin(): Long {
        close()
        mutableState.value = LocalFileSigningState.Loading
        return generation
    }

    fun load(token: Long, name: String, open: () -> InputStream) {
        val buffer = ByteArray(OFFLINE_CMS_MAX_CONTENT_BYTES + 1)
        var loaded: ByteArray? = null
        try {
            checkCurrent(token)
            val size = open().use { input -> readBounded(token, input, buffer) }
            loaded = buffer.copyOf(size)
            synchronized(this) {
                checkCurrent(token)
                check(mutableState.value is LocalFileSigningState.Loading)
                val snapshot = checkNotNull(session.signingSnapshot())
                certificate = snapshot
                val metadata = ActualDocumentMetadata(
                    size,
                    MessageDigest.getInstance("SHA-256").digest(loaded),
                    SensitiveSigningCopyObserver {},
                )
                document = loaded
                loaded = null
                mutableState.value = LocalFileSigningState.Review(
                    token, name, metadata, snapshot.summary.ownerName,
                )
            }
        } catch (_: Exception) {
            fail(token)
        } finally {
            buffer.fill(0)
            loaded?.fill(0)
        }
    }

    fun sign(token: Long) {
        val pending = synchronized(this) {
            val review = mutableState.value as? LocalFileSigningState.Review
            if (token != generation || review?.token != token) return
            val bytes = checkNotNull(document)
            val snapshot = checkNotNull(certificate)
            document = null
            certificate = null
            review.document.close()
            mutableState.value = LocalFileSigningState.Signing
            Triple(bytes, snapshot, review.name)
        }
        val (bytes, snapshot, name) = pending
        var generated: LocalSignature? = null
        try {
            checkCurrent(token)
            val identity = checkNotNull(session.identityForSigning(snapshot))
            val result = signer.signDetached(bytes, identity, SigningAlgorithm.SHA256_WITH_RSA)
            check(result is LocalSignatureResult.Success)
            generated = result.signature
            checkCurrent(token)
            check(generated.withBytes { OfflineCmsVerifier.verify(it, bytes).status } == OfflineCmsStatus.VERIFIED)
            synchronized(this) {
                checkCurrent(token)
                checkNotNull(session.identityForSigning(snapshot))
                signature = generated
                generated = null
                mutableState.value = LocalFileSigningState.Ready(token, name)
            }
        } catch (_: Exception) {
            fail(token)
        } finally {
            bytes.fill(0)
            snapshot.close()
            generated?.close()
        }
    }

    fun save(token: Long, open: () -> OutputStream) {
        val owned = synchronized(this) {
            val ready = mutableState.value as? LocalFileSigningState.Ready
            if (token != generation || ready?.token != token) return
            val result = checkNotNull(signature)
            signature = null
            mutableState.value = LocalFileSigningState.Saving
            result
        }
        try {
            owned.use { value ->
                checkCurrent(token)
                open().use { output ->
                    value.withBytes { bytes ->
                        var offset = 0
                        while (offset < bytes.size) {
                            checkCurrent(token)
                            val count = minOf(DEFAULT_BUFFER_SIZE, bytes.size - offset)
                            output.write(bytes, offset, count)
                            offset += count
                        }
                    }
                }
            }
            synchronized(this) {
                checkCurrent(token)
                mutableState.value = LocalFileSigningState.Saved
            }
        } catch (_: Exception) {
            fail(token)
        }
    }

    @Synchronized
    override fun close() {
        generation++
        (mutableState.value as? LocalFileSigningState.Review)?.document?.close()
        document?.fill(0)
        document = null
        certificate?.close()
        certificate = null
        signature?.close()
        signature = null
        mutableState.value = LocalFileSigningState.Idle
    }

    private fun readBounded(token: Long, input: InputStream, buffer: ByteArray): Int {
        var size = 0
        while (size < buffer.size) {
            checkCurrent(token)
            val count = input.read(buffer, size, minOf(DEFAULT_BUFFER_SIZE, buffer.size - size))
            if (count < 0) break
            check(count > 0)
            size += count
        }
        require(size <= OFFLINE_CMS_MAX_CONTENT_BYTES)
        return size
    }

    @Synchronized
    private fun checkCurrent(token: Long) {
        if (token != generation) throw CancellationException()
    }

    @Synchronized
    fun fail(token: Long) {
        if (token != generation) return
        close()
        mutableState.value = LocalFileSigningState.Failed
    }
}
