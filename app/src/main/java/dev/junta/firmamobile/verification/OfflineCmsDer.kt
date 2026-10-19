package dev.junta.firmamobile.verification

private const val OFFLINE_CMS_MAX_DER_DEPTH = 32
private const val OFFLINE_CMS_DER_TAG_NUMBER_MASK = 0x1f
private const val OFFLINE_CMS_DER_CONSTRUCTED_MASK = 0x20
private const val OFFLINE_CMS_DER_CONTINUATION_MASK = 0x80
private const val OFFLINE_CMS_DER_UNSIGNED_BYTE_MASK = 0xff
private const val OFFLINE_CMS_DER_SHORT_LENGTH_LIMIT = 0x80
private const val OFFLINE_CMS_DER_LONG_LENGTH_COUNT_MASK = 0x7f
private const val OFFLINE_CMS_DER_MAX_LENGTH_OCTETS = 4
private const val OFFLINE_CMS_DER_MAX_TAG_OCTETS = 5
private const val OFFLINE_CMS_DER_EXPECTED_ROOTS = 1
private const val OFFLINE_CMS_DER_BITS_PER_BYTE = 8

private data class OfflineCmsDerIdentifier(
    val nextOffset: Int,
    val constructed: Boolean,
)

private data class OfflineCmsDerLength(
    val valueOffset: Int,
    val valueLength: Long,
)

private data class OfflineCmsDerElement(
    val contentOffset: Int,
    val endOffset: Int,
    val constructed: Boolean,
)

internal object OfflineCmsDer {
    fun hasSafeNesting(encoded: ByteArray): Boolean {
        if (encoded.isEmpty()) {
            return false
        }

        val ends = IntArray(OFFLINE_CMS_MAX_DER_DEPTH)
        var depth = 0
        var offset = 0
        var roots = 0
        var valid = true
        while (valid && offset < encoded.size) {
            depth = completedDepth(ends, depth, offset)
            val parentEnd = if (depth == 0) encoded.size else ends[depth - 1]
            if (depth == 0) {
                roots++
            }
            val element = if (roots > OFFLINE_CMS_DER_EXPECTED_ROOTS) {
                null
            } else {
                readNestedElement(encoded, offset, parentEnd, depth)
            }
            valid = element != null
            if (element != null) {
                offset = element.contentOffset
                if (element.constructed && element.contentOffset < element.endOffset) {
                    ends[depth++] = element.endOffset
                } else {
                    offset = element.endOffset
                }
            }
        }

        depth = completedDepth(ends, depth, offset)
        return valid && roots == OFFLINE_CMS_DER_EXPECTED_ROOTS &&
            depth == 0 &&
            offset == encoded.size
    }

    private fun completedDepth(ends: IntArray, initialDepth: Int, offset: Int): Int {
        var depth = initialDepth
        while (depth > 0 && offset == ends[depth - 1]) {
            depth--
        }
        return depth
    }

    private fun readNestedElement(
        encoded: ByteArray,
        start: Int,
        parentEnd: Int,
        depth: Int,
    ): OfflineCmsDerElement? {
        val element = readDerElement(encoded, start, parentEnd) ?: return null
        return if (element.constructed && element.contentOffset < element.endOffset &&
            depth >= OFFLINE_CMS_MAX_DER_DEPTH
        ) {
            null
        } else {
            element
        }
    }

    private fun readDerElement(
        encoded: ByteArray,
        start: Int,
        parentEnd: Int,
    ): OfflineCmsDerElement? {
        val identifier = readDerIdentifier(encoded, start, parentEnd) ?: return null
        val length = readDerLength(encoded, identifier.nextOffset, parentEnd)
        return if (length == null || length.valueLength > parentEnd - length.valueOffset) {
            null
        } else {
            OfflineCmsDerElement(
                contentOffset = length.valueOffset,
                endOffset = length.valueOffset + length.valueLength.toInt(),
                constructed = identifier.constructed,
            )
        }
    }

    private fun readDerIdentifier(
        encoded: ByteArray,
        start: Int,
        parentEnd: Int,
    ): OfflineCmsDerIdentifier? {
        if (start >= parentEnd) {
            return null
        }
        val first = encoded[start].toInt() and OFFLINE_CMS_DER_UNSIGNED_BYTE_MASK
        val offset = if ((first and OFFLINE_CMS_DER_TAG_NUMBER_MASK) != OFFLINE_CMS_DER_TAG_NUMBER_MASK) {
            start + 1
        } else {
            readHighTagEnd(encoded, start + 1, parentEnd)
        }
        return offset?.let {
            OfflineCmsDerIdentifier(it, (first and OFFLINE_CMS_DER_CONSTRUCTED_MASK) != 0)
        }
    }

    private fun readHighTagEnd(encoded: ByteArray, start: Int, parentEnd: Int): Int? {
        var offset = start
        var count = 0
        while (offset < parentEnd && count < OFFLINE_CMS_DER_MAX_TAG_OCTETS) {
            val value = encoded[offset++].toInt() and OFFLINE_CMS_DER_UNSIGNED_BYTE_MASK
            count++
            if ((value and OFFLINE_CMS_DER_CONTINUATION_MASK) == 0) {
                return offset
            }
        }
        return null
    }

    private fun readDerLength(
        encoded: ByteArray,
        start: Int,
        parentEnd: Int,
    ): OfflineCmsDerLength? {
        if (start >= parentEnd) {
            return null
        }

        val first = encoded[start].toInt() and OFFLINE_CMS_DER_UNSIGNED_BYTE_MASK
        return if (first < OFFLINE_CMS_DER_SHORT_LENGTH_LIMIT) {
            OfflineCmsDerLength(start + 1, first.toLong())
        } else {
            readLongLength(encoded, start, parentEnd, first)
        }
    }

    private fun readLongLength(
        encoded: ByteArray,
        start: Int,
        parentEnd: Int,
        first: Int,
    ): OfflineCmsDerLength? {
        val count = first and OFFLINE_CMS_DER_LONG_LENGTH_COUNT_MASK
        if (count == 0 || count > OFFLINE_CMS_DER_MAX_LENGTH_OCTETS ||
            start + 1 + count > parentEnd
        ) {
            return null
        }

        var length = 0L
        for (index in 0 until count) {
            length = (length shl OFFLINE_CMS_DER_BITS_PER_BYTE) or
                (encoded[start + 1 + index].toInt() and OFFLINE_CMS_DER_UNSIGNED_BYTE_MASK).toLong()
        }
        return if (encoded[start + 1].toInt() == 0 || length < OFFLINE_CMS_DER_SHORT_LENGTH_LIMIT) {
            null
        } else {
            OfflineCmsDerLength(start + 1 + count, length)
        }
    }
}
