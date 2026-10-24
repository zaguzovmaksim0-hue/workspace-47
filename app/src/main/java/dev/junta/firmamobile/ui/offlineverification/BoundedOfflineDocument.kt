package dev.junta.firmamobile.ui.offlineverification

import java.io.Closeable
import java.io.InputStream

private const val MAX_OFFLINE_CMS_BYTES = 1_048_576
private const val MAX_OFFLINE_ORIGINAL_BYTES = 524_288

internal enum class OfflineInputKind(val maxBytes: Int) {
    CMS(MAX_OFFLINE_CMS_BYTES),
    ORIGINAL(MAX_OFFLINE_ORIGINAL_BYTES),
}

/** The source is temporary and never included in UI state, diagnostics or saved state. */
internal interface OfflineDocumentSource {
    fun declaredSize(): Long?
    fun open(): OfflineOpenedDocument
}

internal class OfflineOpenedDocument(
    val declaredSize: Long?,
    val stream: InputStream,
) : Closeable {
    override fun close() = stream.close()
}

internal class OfflineInputTooLargeException : Exception()

internal object BoundedOfflineDocument {
    fun read(
        source: OfflineDocumentSource,
        kind: OfflineInputKind,
        checkActive: () -> Unit,
    ): ByteArray {
        checkActive()
        checkSize(source.declaredSize(), kind)
        checkActive()
        val scratch = ByteArray(kind.maxBytes + 1)
        return try {
            val size = source.open().use { document ->
                checkSize(document.declaredSize, kind)
                readInto(document.stream, scratch, kind, checkActive)
            }
            // Close the source before allocating the returned, caller-owned snapshot.
            checkActive()
            scratch.copyOf(size)
        } finally {
            scratch.fill(0)
        }
    }

    private fun checkSize(size: Long?, kind: OfflineInputKind) {
        if (size != null && size > kind.maxBytes.toLong()) throw OfflineInputTooLargeException()
    }

    private fun readInto(
        input: InputStream,
        scratch: ByteArray,
        kind: OfflineInputKind,
        checkActive: () -> Unit,
    ): Int {
        var size = 0
        while (true) {
            checkActive()
            var received = input.read(scratch, size, scratch.size - size)
            if (received == 0) {
                // A broken provider must not turn zero-length progress into an infinite loop.
                checkActive()
                val next = input.read()
                received = if (next < 0) -1 else 1
                if (next >= 0) scratch[size] = next.toByte()
            }
            if (received < 0) break
            size += received
            if (size > kind.maxBytes) throw OfflineInputTooLargeException()
        }
        return size
    }
}
