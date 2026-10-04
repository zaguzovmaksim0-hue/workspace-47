package dev.junta.firmamobile.afirma.servlet

/** Checks revision byte layout; CMS/dictionary binding and authentication are external. */
internal object NativePdfRevisionRange {
    private val header = byteArrayOf(37, 80, 68, 70, 45)
    private val eof = byteArrayOf(37, 37, 69, 79, 70)

    fun revisionEnd(document: ByteArray, range: IntArray): Int? {
        if (document.size !in 5..2_097_152 || range.size != 4) return null
        for (i in header.indices) if (document[i] != header[i]) return null
        val start = range[1].toLong()
        val end = range[2].toLong()
        val remaining = range[3].toLong()
        if (range[0] != 0 || start < 5 || end < start + 4 ||
            end > document.size.toLong() || remaining < 1) return null
        val coveredEndLong = end + remaining
        if (coveredEndLong > document.size.toLong()) return null
        val first = start.toInt()
        val next = end.toInt()
        if ((next - first - 2) % 2 != 0 ||
            document[first] != 60.toByte() || document[next - 1] != 62.toByte()) return null
        for (i in first + 1 until next - 1) {
            val value = document[i].toInt()
            if (value !in 48..57 && value !in 65..70 && value !in 97..102) return null
        }

        val coveredEnd = coveredEndLong.toInt()
        val scanStart = maxOf(0, coveredEnd - 38)
        var markerEnd = coveredEnd
        var spaces = 0
        while (markerEnd > scanStart && isPdfWhitespace(document[markerEnd - 1])) {
            if (++spaces > 32) return null
            markerEnd--
        }
        if (markerEnd - scanStart < eof.size) return null
        for (i in eof.indices) if (document[markerEnd - eof.size + i] != eof[i]) return null
        return coveredEnd
    }

    fun extract(document: ByteArray, range: IntArray): ByteArray? {
        val coveredEnd = revisionEnd(document, range) ?: return null
        val start = range[1]
        val end = range[2]
        return ByteArray(start + coveredEnd - end).also { result ->
            document.copyInto(result, 0, 0, start)
            document.copyInto(result, start, end, coveredEnd)
        }
    }

    private fun isPdfWhitespace(value: Byte): Boolean = when (value.toInt()) {
        0, 9, 10, 12, 13, 32 -> true
        else -> false
    }
}
