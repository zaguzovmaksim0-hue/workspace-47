package dev.junta.firmamobile.browser.download

import java.text.Normalizer

/** Advisory filename only; never a filesystem path or executable command. */
internal object DownloadFileName {
    fun safe(suggested: String?): String {
        val raw = suggested.orEmpty().take(8192)
        val bounded = raw.substring(0, raw.offsetByCodePoints(0, minOf(4096, raw.codePointCount(0, raw.length))))
        val cleaned = buildString {
            Normalizer.normalize(bounded, Normalizer.Form.NFC).codePoints().forEach { point ->
                when {
                    Character.isISOControl(point) || Character.getType(point) in setOf(Character.FORMAT.toInt(), Character.SURROGATE.toInt()) -> Unit
                    point <= 0x7f && point.toChar() in "/\\:*?\"<>|" -> append('_')
                    else -> appendCodePoint(point)
                }
            }
        }.trim { it.isWhitespace() || it == '.' }
        if (cleaned.isEmpty()) return "documento.bin"
        val guarded = if (DEVICE.matches(cleaned.substringBefore('.'))) "_$cleaned" else cleaned
        val extension = guarded.substringAfterLast('.', "").takeIf { EXT.matches(it) }?.let { ".$it" }.orEmpty()
        val stem = if (extension.isNotEmpty()) guarded.dropLast(extension.length) else guarded
        val room = 180 - extension.toByteArray(Charsets.UTF_8).size
        val name = buildString {
            var bytes = 0
            val iterator = stem.codePoints().iterator()
            while (iterator.hasNext()) {
                val cp = iterator.nextInt()
                val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
                if (bytes + size > room) break
                appendCodePoint(cp); bytes += size
            }
        }.trimEnd { it.isWhitespace() || it == '.' }
        return (name + extension).takeIf { name.isNotEmpty() } ?: "documento.bin"
    }
    private val DEVICE = Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]", RegexOption.IGNORE_CASE)
    private val EXT = Regex("[A-Za-z0-9]{1,10}")
}
