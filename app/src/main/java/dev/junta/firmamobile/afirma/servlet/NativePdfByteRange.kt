package dev.junta.firmamobile.afirma.servlet

/** Verifies a single signature gap while retaining the complete original PDF
 * revision and covering every byte outside the actual hex Contents string. */
internal object NativePdfByteRange {
    fun extract(document: ByteArray, range: IntArray, expectedOriginal: ByteArray): ByteArray? {
        if (!valid(document, range, expectedOriginal)) return null
        val start = range[1]; val end = range[2]; val remaining = range[3]
        return ByteArray(start + remaining).also { result ->
            document.copyInto(result, 0, 0, start)
            document.copyInto(result, start, end, document.size)
        }
    }

    fun digest(document: ByteArray, range: IntArray, expectedOriginal: ByteArray, algorithm: String): ByteArray? {
        if (!valid(document, range, expectedOriginal)) return null
        return java.security.MessageDigest.getInstance(algorithm).apply {
            update(document, 0, range[1])
            update(document, range[2], range[3])
        }.digest()
    }

    private fun valid(document: ByteArray, range: IntArray, expectedOriginal: ByteArray): Boolean {
        if (document.size !in 1..NativeAfirmaLimits.PDF_OUTPUT_BYTES ||
            expectedOriginal.size !in 1..NativeAfirmaLimits.PDF_INPUT_BYTES ||
            range.size != 4 || range[0] != 0 || document.size < expectedOriginal.size) return false
        val start = range[1]; val end = range[2]; val remaining = range[3]
        if (start < expectedOriginal.size || start < 1 || end.toLong() < start.toLong() + 4 ||
            end > document.size || remaining < 0 || end.toLong() + remaining != document.size.toLong()) return false
        if (document[start] != '<'.code.toByte() || document[end - 1] != '>'.code.toByte()) return false
        val hexCount = end - start - 2
        if (hexCount % 2 != 0) return false
        for (i in expectedOriginal.indices) if (document[i] != expectedOriginal[i]) return false
        for (i in start + 1 until end - 1) {
            val c = document[i].toInt()
            if (c !in 48..57 && c !in 65..70 && c !in 97..102) return false
        }
        return true
    }
}
