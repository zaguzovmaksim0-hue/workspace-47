package dev.junta.firmamobile.signing

/** Bounded structural preflight before BC parsing; not a general-purpose ASN.1 parser. */
internal object SoftwareEcCadesDerGate {
    private const val MAX_DEPTH = 24
    private const val MAX_LENGTH_OCTETS = 3
    private const val CONSTRUCTED = 0x20
    private const val TAG_NUMBER = 0x1f
    private const val LONG_LENGTH = 0x80
    private const val LENGTH_MASK = 0x7f
    private const val OCTET_MASK = 0xff
    private const val OCTET_BITS = 8

    fun requireBoundedTree(bytes: ByteArray) {
        require(bytes.isNotEmpty())
        val reader = Reader(bytes)
        reader.node(bytes.size, 1)
        require(reader.position == bytes.size)
    }

    private class Reader(private val bytes: ByteArray) {
        var position = 0
            private set

        fun node(boundary: Int, depth: Int) {
            require(depth <= MAX_DEPTH)
            val tag = octet(boundary)
            require(tag != 0 && tag and TAG_NUMBER != TAG_NUMBER)
            val length = length(boundary)
            require(length <= boundary - position)
            val nodeEnd = position + length
            if (tag and CONSTRUCTED != 0) {
                while (position < nodeEnd) node(nodeEnd, depth + 1)
            } else {
                position = nodeEnd
            }
        }

        private fun length(boundary: Int): Int {
            val first = octet(boundary)
            if (first < LONG_LENGTH) return first
            val width = first and LENGTH_MASK
            require(width in 1..MAX_LENGTH_OCTETS)
            var length = octet(boundary)
            require(length != 0)
            repeat(width - 1) { length = (length shl OCTET_BITS) or octet(boundary) }
            require(length >= LONG_LENGTH)
            return length
        }

        private fun octet(boundary: Int): Int {
            require(position < boundary)
            return bytes[position++].toInt() and OCTET_MASK
        }
    }
}
