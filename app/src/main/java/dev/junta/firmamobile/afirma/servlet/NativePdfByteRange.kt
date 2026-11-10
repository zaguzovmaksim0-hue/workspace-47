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
        if (!hasBoundedSizes(document, range, expectedOriginal)) return false
        return hasBoundedRange(document.size, range, expectedOriginal.size) &&
            hasHexGap(document, range[1], range[2]) &&
            preservesOriginal(document, expectedOriginal)
    }

    private fun preservesOriginal(document: ByteArray, original: ByteArray): Boolean {
        for (index in original.indices) if (document[index] != original[index]) return false
        return true
    }

    private fun hasBoundedSizes(document: ByteArray, range: IntArray, original: ByteArray): Boolean =
        document.size in 1..NativeAfirmaLimits.PDF_OUTPUT_BYTES &&
            original.size in 1..NativeAfirmaLimits.PDF_INPUT_BYTES &&
            range.size == RANGE_FIELDS && document.size >= original.size

    private fun hasBoundedRange(documentSize: Int, range: IntArray, originalSize: Int): Boolean {
        val start = range[1].toLong(); val end = range[2].toLong(); val remaining = range[3].toLong()
        return range[0] == 0 && start >= originalSize && start >= 1 &&
            end >= start + MIN_GAP_BYTES && end <= documentSize && remaining >= 0 &&
            end + remaining == documentSize.toLong()
    }

    private fun hasHexGap(document: ByteArray, start: Int, end: Int): Boolean {
        if (document[start] != '<'.code.toByte() || document[end - 1] != '>'.code.toByte() ||
            (end - start - HEX_DELIMITERS) % HEX_PAIR_BYTES != 0) return false
        return (start + 1 until end - 1).all { index ->
            val character = document[index].toInt()
            character in '0'.code..'9'.code || character in 'A'.code..'F'.code ||
                character in 'a'.code..'f'.code
        }
    }

    private const val RANGE_FIELDS = 4
    private const val MIN_GAP_BYTES = 4
    private const val HEX_DELIMITERS = 2
    private const val HEX_PAIR_BYTES = 2
}
