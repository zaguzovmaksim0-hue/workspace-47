package dev.junta.firmamobile.afirma.servlet

/** Non-recursive BER/DER envelope budget before constructing Bouncy Castle
 * objects. It does not interpret signature semantics; the CMS reader does. */
internal object NativeCmsBounds {
    private data class Frame(val end: Int, val indefinite: Boolean)
    fun accepts(bytes: ByteArray, maxBytes: Int): Boolean = runCatching {
        require(bytes.size in 2..maxBytes)
        val stack = java.util.ArrayDeque<Frame>()
        stack.addLast(Frame(bytes.size, false))
        var position = 0; var nodes = 0; var roots = 0
        while (stack.isNotEmpty()) {
            val frame = stack.peekLast()
            if (position == frame.end && !frame.indefinite) { stack.removeLast(); continue }
            require(position < frame.end && ++nodes <= 12_000)
            if (frame.indefinite && position + 1 < frame.end && bytes[position] == 0.toByte() && bytes[position + 1] == 0.toByte()) {
                position += 2; stack.removeLast(); continue
            }
            val tag = bytes[position++].toInt() and 255
            require(tag != 0)
            if (tag and 31 == 31) {
                var count = 0; var part: Int
                do {
                    require(position < frame.end && ++count <= 5)
                    part = bytes[position++].toInt() and 255
                    require(count != 1 || part and 127 != 0)
                } while (part and 128 != 0)
            }
            require(position < frame.end)
            val lengthTag = bytes[position++].toInt() and 255
            val constructed = tag and 32 != 0
            if (stack.size == 1) require(++roots == 1)
            if (lengthTag == 128) {
                require(constructed && stack.size < 32)
                stack.addLast(Frame(frame.end, true))
            } else {
                var length = if (lengthTag < 128) lengthTag.toLong() else 0L
                if (lengthTag > 128) {
                    val count = lengthTag and 127
                    require(count in 1..4 && position + count <= frame.end)
                    repeat(count) { length = length * 256 + (bytes[position++].toInt() and 255) }
                }
                require(length <= frame.end - position)
                val end = position + length.toInt()
                if (constructed) {
                    require(stack.size < 32); stack.addLast(Frame(end, false))
                } else position = end
            }
        }
        require(position == bytes.size && roots == 1)
        true
    }.getOrDefault(false)
}
