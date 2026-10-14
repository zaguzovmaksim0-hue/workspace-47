package dev.junta.firmamobile.browser.download

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale

/** A document carried inside a data: address; nothing is requested from a server. */
internal class DataUrlDocument private constructor(val mimeType: String?, val bytes: ByteArray) {
    companion object {
        fun parse(raw: String, limit: Long = DocumentDownloadPlan.MAX_BYTES): DataUrlDocument? {
            if (!raw.regionMatches(0, "data:", 0, 5, ignoreCase = true)) return null
            val comma = raw.indexOf(',')
            if (comma < 0 || comma > 1024) return null
            val header = raw.substring(5, comma)
            val payload = raw.substring(comma + 1).substringBefore('#')
            val parameters = header.split(';').map { it.trim() }
            val base64 = parameters.drop(1).any { it.equals("base64", ignoreCase = true) }
            val mime = parameters.first().lowercase(Locale.ROOT).takeIf { it.contains('/') }
            return try {
                val bytes = if (base64) {
                    if (payload.length / 4L * 3 > limit + 3) return null
                    val text = String(percentDecode(payload, limit * 2), Charsets.ISO_8859_1).filterNot { it.isWhitespace() }
                    Base64.getDecoder().decode(text)
                } else percentDecode(payload, limit)
                if (bytes.size > limit) null else DataUrlDocument(mime, bytes)
            } catch (_: IllegalArgumentException) { null }
        }

        private fun percentDecode(value: String, limit: Long): ByteArray {
            val out = ByteArrayOutputStream()
            var index = 0
            while (index < value.length) {
                val c = value[index]
                if (c == '%' && index + 2 < value.length) {
                    val hex = value.substring(index + 1, index + 3).toIntOrNull(16)
                    if (hex != null) { out.write(hex); index += 3; continue }
                }
                val code = c.code
                if (code < 0x80) out.write(code) else out.write(c.toString().toByteArray(Charsets.UTF_8))
                if (out.size() > limit) throw IllegalArgumentException("Data address too large")
                index++
            }
            return out.toByteArray()
        }
    }
}
