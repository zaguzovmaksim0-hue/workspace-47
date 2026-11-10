package dev.junta.firmamobile.afirma.servlet

/** Strict standard or URL Base64 with one bounded decoded allocation.
 * Checks canonical padding bits without allocating a second encoded document.
 */
internal object BoundedBase64 {
    fun decode(value: String, maxBytes: Int): ByteArray {
        require(maxBytes >= 0 && value.length <= (maxBytes + 2L) / 3L * 4L)
        val dataLength = value.indexOf('=').let { if (it < 0) value.length else it }
        val padding = value.length - dataLength
        val remainder = dataLength % 4
        require(remainder != 1 && padding in 0..2)
        if (padding > 0) {
            require(value.length % 4 == 0 && padding == (4 - remainder) % 4)
            require((dataLength until value.length).all { value[it] == '=' })
        }
        val length = dataLength.toLong() * 6 / 8
        require(length <= maxBytes)
        val decoded = ByteArray(length.toInt())
        var accumulator = 0
        var bits = 0
        var offset = 0
        var standard = false
        var url = false
        try {
            for (index in 0 until dataLength) {
                val character = value[index]
                val digit = when (character) {
                    in 'A'..'Z' -> character - 'A'
                    in 'a'..'z' -> character - 'a' + 26
                    in '0'..'9' -> character - '0' + 52
                    '+', '/' -> { standard = true; if (character == '+') 62 else 63 }
                    '-', '_' -> { url = true; if (character == '-') 62 else 63 }
                    else -> throw IllegalArgumentException("Invalid Base64 character")
                }
                require(!(standard && url))
                accumulator = (accumulator shl 6) or digit
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    decoded[offset++] = (accumulator ushr bits).toByte()
                }
                accumulator = accumulator and ((1 shl bits) - 1)
            }
            require(accumulator == 0 && offset == decoded.size)
            return decoded
        } catch (error: Exception) {
            decoded.fill(0)
            throw error
        }
    }
}
