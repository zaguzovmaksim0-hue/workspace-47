package dev.junta.firmamobile.signing

import java.io.Closeable
import kotlin.math.min
import kotlin.math.sqrt

/** Local document capability, revoked with its consent owner; never a URL or a digest. */
class PdfPreviewSource internal constructor(
    private val maxBytes: Int,
    private val release: () -> Unit = {},
    private var load: (() -> ByteArray)?,
) : Closeable {
    internal fun <T> withBytes(block: (ByteArray) -> T): T {
        val bytes = synchronized(this) { checkNotNull(load).invoke() }
        return try {
            require(bytes.size <= maxBytes && isPdf(bytes))
            block(bytes)
        } finally { bytes.fill(0) }
    }

    @Synchronized override fun close() {
        if (load != null) { load = null; release() }
    }

    override fun toString(): String = "PdfPreviewSource(redacted)"

    internal companion object {
        private const val HEADER = "%PDF-"

        fun isPdf(bytes: ByteArray): Boolean = bytes.size >= HEADER.length &&
            HEADER.indices.all { bytes[it] == HEADER[it].code.toByte() }

        fun fromLocalInput(maxBytes: Int, load: () -> ByteArray): PdfPreviewSource? {
            val probe = try { load() } catch (_: Exception) { return null }
            return try {
                if (probe.size <= maxBytes && isPdf(probe)) PdfPreviewSource(maxBytes, load = load) else null
            } finally { probe.fill(0) }
        }

        fun copyOf(bytes: ByteArray, maxBytes: Int): PdfPreviewSource {
            require(bytes.size <= maxBytes && isPdf(bytes))
            val owned = bytes.copyOf()
            return PdfPreviewSource(maxBytes, { owned.fill(0) }) { owned.copyOf() }
        }
    }
}

internal object PdfPreviewLimits {
    const val TIMEOUT_MS = 3_000L
    private const val MAX_WIDTH = 1080
    private const val MAX_HEIGHT = 1920
    private const val MAX_PIXELS = 1_500_000

    fun dimensions(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = min(
            min(MAX_WIDTH.toDouble() / width, MAX_HEIGHT.toDouble() / height),
            sqrt(MAX_PIXELS.toDouble() / (width.toDouble() * height)),
        )
        return (width * scale).toInt().coerceAtLeast(1) to
            (height * scale).toInt().coerceAtLeast(1)
    }
}
