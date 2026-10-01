package dev.junta.firmamobile.afirma.servlet

import java.io.Closeable
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/** Configuration-download capability from the current document. No certificate
 * or signing consent is carried by this object. Sensitive fields are not in UI
 * state or toString. Each parsed instance permits at most one retrieval. */
internal class AfirmaDeferredInvocation(
    val operation: AfirmaServletOperation,
    val sourceOrigin: String,
    val retrievalUrl: URI,
    val fileId: String,
    val expectedResponseId: String?,
    val expectedStorageUrl: URI?,
    private val legacyKey: String?,
    cipher: AfirmaAesParameters?,
) : Closeable {
    private var ownedCipher = cipher?.copy()
    private var closed = false
    private val requested = AtomicBoolean(false)
    val retrievalDisplay: String = URI("https", null, retrievalUrl.host, retrievalUrl.port, retrievalUrl.path, null, null).toASCIIString()

    @Synchronized fun begin() {
        check(!closed && requested.compareAndSet(false, true)) { "Retrieval already consumed or closed" }
    }

    /** Returned XML is separately bounded and validated; legacy/AES CBC are
     * compatibility carriers, not authenticated application signatures. */
    @Synchronized fun decodeResponse(encoded: ByteArray): ByteArray {
        check(!closed && requested.get()) { "Retrieval is not active" }
        require(encoded.size <= MAX_WIRE_BYTES) { "Retrieved configuration exceeds budget" }
        val cipher = ownedCipher
        if (cipher == null && legacyKey == null) {
            // The explicit no-cipher variant is raw XML. Do not guess Base64
            // or fall back to plaintext after a configured cipher fails.
            return encoded.copyOf()
        }
        require(encoded.all { (it.toInt() and 255) < 128 }) { "Cipher response is not ASCII" }
        val wire = encoded.toString(Charsets.US_ASCII)
        val text = if (wire.endsWith("\r\n")) wire.dropLast(2) else wire.removeSuffix("\n")
        return if (cipher != null) cipher.decode(text) else AfirmaIntermediateCipher.decode(text, legacyKey)
    }

    @Synchronized override fun close() { closed = true; ownedCipher?.close(); ownedCipher = null }
    override fun toString(): String = "AfirmaDeferredInvocation(operation=$operation)"
    companion object { const val MAX_WIRE_BYTES = 2_097_152 }
}

/** A cancellable HTTP callback transfers this owned buffer once. Losing the
 * continuation race erases the buffer instead of leaking a closeable result. */
internal class AfirmaRetrievedBytes(bytes: ByteArray) : Closeable {
    private var owned: ByteArray? = bytes
    val size: Int = bytes.size
    @Synchronized fun take(): ByteArray = checkNotNull(owned) { "Retrieved bytes consumed" }.also { owned = null }
    @Synchronized override fun close() { owned?.fill(0); owned = null }
    override fun toString() = "AfirmaRetrievedBytes(size=$size)"
}
