package dev.junta.firmamobile.browser.download

import java.io.ByteArrayOutputStream
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal sealed interface FormEntry {
    val name: String
    class Text(override val name: String, val value: String) : FormEntry
    /** An empty file control; a control holding a selected file is never replayed. */
    class EmptyFile(override val name: String) : FormEntry
}

/** The encoded body of the POST that produced a download, rebuilt from the
 * form fields the page submitted. Never a data class: fields may hold session tokens. */
internal class FormSubmission private constructor(val contentType: String, private var bytes: ByteArray?) : AutoCloseable {
    val size: Int get() = bytes?.size ?: 0
    fun body(): ByteArray = checkNotNull(bytes) { "Form body discarded" }.copyOf()
    override fun close() { bytes?.fill(0); bytes = null }

    sealed interface Lookup {
        class Found(val submission: FormSubmission) : Lookup
        /** The page selected a file to upload; resending it is not supported. */
        object HasFiles : Lookup
        object Missing : Lookup
    }

    companion object {
        const val MAX_BODY = 2 * 1024 * 1024
        private const val MAX_ENTRIES = 4096
        private val random = SecureRandom()

        /** [json] is the page helper's description of the last submitted form. */
        fun fromPageRecord(json: String?, action: String): Lookup {
            if (json.isNullOrEmpty() || json.length > MAX_BODY * 3) return Lookup.Missing
            return try {
                val record = JSONObject(json)
                if (DownloadRequestEvidence.key(record.getString("action")) != DownloadRequestEvidence.key(action)) return Lookup.Missing
                if (record.optBoolean("files")) return Lookup.HasFiles
                val list = record.getJSONArray("entries")
                if (list.length() > MAX_ENTRIES) return Lookup.Missing
                val entries = (0 until list.length()).map { index ->
                    val item: JSONArray = list.getJSONArray(index)
                    if (item.optString(2) == "file") FormEntry.EmptyFile(item.getString(0))
                    else FormEntry.Text(item.getString(0), item.getString(1))
                }
                encode(record.optString("enctype"), record.optString("charset"), entries)
                    ?.let { Lookup.Found(it) } ?: Lookup.Missing
            } catch (_: Exception) { Lookup.Missing }
        }

        fun encode(enctype: String?, charsetName: String?, entries: List<FormEntry>): FormSubmission? {
            val charset = charset(charsetName)
            val normalized = entries.map {
                when (it) {
                    is FormEntry.Text -> FormEntry.Text(newlines(it.name), newlines(it.value))
                    is FormEntry.EmptyFile -> FormEntry.EmptyFile(newlines(it.name))
                }
            }
            val out = BoundedOutput()
            val type = try {
                when (enctype?.lowercase(Locale.ROOT)) {
                    "multipart/form-data" -> multipart(normalized, charset, out)
                    "text/plain" -> {
                        normalized.forEach { entry ->
                            out.write(encodeText(entry.name, charset)); out.write("=".toByteArray())
                            out.write(encodeText((entry as? FormEntry.Text)?.value.orEmpty(), charset)); out.write("\r\n".toByteArray())
                        }
                        "text/plain;charset=" + charset.name()
                    }
                    else -> {
                        normalized.forEachIndexed { index, entry ->
                            if (index > 0) out.write("&".toByteArray())
                            out.write(urlEncode(entry.name, charset)); out.write("=".toByteArray())
                            out.write(urlEncode((entry as? FormEntry.Text)?.value.orEmpty(), charset))
                        }
                        "application/x-www-form-urlencoded"
                    }
                }
            } catch (_: TooLarge) { out.discard(); return null }
            return FormSubmission(type, out.take())
        }

        /** Browsers submit ISO-8859-1 labelled pages as windows-1252 and never as UTF-16. */
        private fun charset(name: String?): Charset {
            val label = name?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (label.isEmpty() || label.startsWith("utf-16")) return Charsets.UTF_8
            val mapped = if (label in setOf("iso-8859-1", "iso8859-1", "latin1", "l1", "us-ascii", "ascii")) "windows-1252" else label
            return runCatching { Charset.forName(mapped) }.getOrDefault(Charsets.UTF_8)
        }

        private fun newlines(value: String) = value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n")

        /** Characters the form charset cannot represent become HTML numeric references, as in browsers. */
        private fun encodeText(value: String, charset: Charset): ByteArray {
            val encoder = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val out = ByteArrayOutputStream()
            var index = 0
            while (index < value.length) {
                val point = value.codePointAt(index)
                val chars = String(Character.toChars(point))
                val bytes = if (encoder.canEncode(chars)) {
                    val buffer = encoder.encode(CharBuffer.wrap(chars)); ByteArray(buffer.remaining()).also { buffer.get(it) }
                } else "&#$point;".toByteArray(Charsets.US_ASCII)
                out.write(bytes)
                index += Character.charCount(point)
            }
            return out.toByteArray()
        }

        private fun urlEncode(value: String, charset: Charset): ByteArray {
            val out = ByteArrayOutputStream()
            for (byte in encodeText(value, charset)) {
                val b = byte.toInt() and 0xff
                when {
                    b == 0x20 -> out.write('+'.code)
                    b in 0x30..0x39 || b in 0x41..0x5a || b in 0x61..0x7a || b == 0x2a || b == 0x2d || b == 0x2e || b == 0x5f -> out.write(b)
                    else -> out.write("%%%02X".format(b).toByteArray(Charsets.US_ASCII))
                }
            }
            return out.toByteArray()
        }

        private fun multipart(entries: List<FormEntry>, charset: Charset, out: BoundedOutput): String {
            val boundary = "----FirmaMobileFormBoundary" + ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            fun quoted(name: String) = encodeText(name.replace("\r\n", "%0D%0A").replace("\"", "%22"), charset)
            for (entry in entries) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"".toByteArray()); out.write(quoted(entry.name))
                when (entry) {
                    is FormEntry.Text -> {
                        out.write("\"\r\n\r\n".toByteArray()); out.write(encodeText(entry.value, charset))
                    }
                    is FormEntry.EmptyFile -> out.write("\"; filename=\"\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
                }
                out.write("\r\n".toByteArray())
            }
            out.write("--$boundary--\r\n".toByteArray())
            return "multipart/form-data; boundary=$boundary"
        }
    }

    private class TooLarge : Exception()
    private class BoundedOutput : ByteArrayOutputStream() {
        override fun write(b: ByteArray) { if (count + b.size > MAX_BODY) throw TooLarge(); super.write(b, 0, b.size) }
        fun take(): ByteArray = toByteArray().also { discard() }
        fun discard() { buf.fill(0); reset() }
    }
}
