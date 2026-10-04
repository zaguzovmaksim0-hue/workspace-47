package dev.junta.firmamobile.afirma.servlet

/** Verifies a single signature gap while retaining the complete original PDF
 * revision and covering every byte outside the actual hex Contents string. */
internal object NativePdfByteRange {
    fun extract(document: ByteArray, range: IntArray, expectedOriginal: ByteArray): ByteArray? {
        if (document.size !in 1..2_097_152 || expectedOriginal.size !in 1..524_288 ||
            range.size != 4 || range[0] != 0 || document.size < expectedOriginal.size) return null
        val start = range[1]; val end = range[2]; val remaining = range[3]
        if (start < expectedOriginal.size || start < 1 || end.toLong() < start.toLong() + 4 ||
            end > document.size || remaining < 0 || end.toLong() + remaining != document.size.toLong()) return null
        if (document[start] != '<'.code.toByte() || document[end - 1] != '>'.code.toByte()) return null
        val hexCount = end - start - 2
        if (hexCount % 2 != 0) return null
        for (i in expectedOriginal.indices) if (document[i] != expectedOriginal[i]) return null
        for (i in start + 1 until end - 1) {
            val c = document[i].toInt()
            if (c !in 48..57 && c !in 65..70 && c !in 97..102) return null
        }
        val result = ByteArray(start + remaining)
        document.copyInto(result, 0, 0, start)
        document.copyInto(result, start, end, document.size)
        return result
    }
}
