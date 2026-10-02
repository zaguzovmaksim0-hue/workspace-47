package dev.junta.firmamobile.browser.download

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal object BoundedDocumentCopy {
    data class Receipt(val bytes: Long, val sha256: String)
    fun copy(input: InputStream, output: OutputStream, limit: Long, expectedLength: Long = -1,
        active: () -> Boolean = { true }, progress: (Long) -> Unit = {}): Receipt {
        require(limit > 0 && expectedLength >= -1)
        if (expectedLength > limit) throw IOException("Document exceeds size limit")
        val buffer = ByteArray(16 * 1024)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            while (true) {
                if (!active()) throw IOException("Document copy cancelled")
                var read = input.read(buffer, 0, (minOf(buffer.size.toLong() - 1, limit - total) + 1).toInt())
                if (read == 0) {
                    val byte = input.read()
                    if (byte < 0) break
                    buffer[0] = byte.toByte(); read = 1
                }
                if (read < 0) break
                if (read > limit - total) throw IOException("Document exceeds size limit")
                if (!active()) throw IOException("Document copy cancelled")
                output.write(buffer, 0, read); digest.update(buffer, 0, read)
                total += read; progress(total)
            }
            if (!active()) throw IOException("Document copy cancelled")
            if (expectedLength >= 0 && total != expectedLength) throw IOException("Document length mismatch")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            return Receipt(total, hash)
        } finally { buffer.fill(0) }
    }
}
