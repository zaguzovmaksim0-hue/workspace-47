package dev.junta.firmamobile.afirma.servlet

/** Strict standard or URL Base64 with one bounded decoded allocation.
 * Checks canonical padding bits without allocating a second encoded document.
 */
internal object BoundedBase64 {
    private const val CHARACTERS_PER_BLOCK = 4
    private const val BYTES_PER_BLOCK = 3
    private const val MAX_PADDING = 2
    private const val CHARACTER_BITS = 6
    private const val BYTE_BITS = 8
    private const val LOWERCASE_OFFSET = 26
    private const val DIGIT_OFFSET = 52
    private const val FIRST_SPECIAL_DIGIT = 62
    private const val SECOND_SPECIAL_DIGIT = 63
    private const val STANDARD_ALPHABET = 1
    private const val URL_ALPHABET = 2
    private const val MIXED_ALPHABET = STANDARD_ALPHABET or URL_ALPHABET

    fun decode(value: String, maxBytes: Int): ByteArray {
        require(maxBytes >= 0 &&
            value.length <= (maxBytes + MAX_PADDING.toLong()) / BYTES_PER_BLOCK * CHARACTERS_PER_BLOCK)
        val dataLength = validatedDataLength(value)
        val length = dataLength.toLong() * CHARACTER_BITS / BYTE_BITS
        require(length <= maxBytes)
        val decoded = ByteArray(length.toInt())
        var completed = false
        try {
            decodeDigits(value, dataLength, decoded)
            completed = true
            return decoded
        } finally {
            if (!completed) decoded.fill(0)
        }
    }

    private fun validatedDataLength(value: String): Int {
        val firstPadding = value.indexOf('=')
        val dataLength = if (firstPadding < 0) value.length else firstPadding
        val padding = value.length - dataLength
        val remainder = dataLength % CHARACTERS_PER_BLOCK
        require(remainder != 1 && padding in 0..MAX_PADDING)
        if (padding > 0) {
            require(value.length % CHARACTERS_PER_BLOCK == 0 &&
                padding == (CHARACTERS_PER_BLOCK - remainder) % CHARACTERS_PER_BLOCK)
            require((dataLength until value.length).all { value[it] == '=' })
        }
        return dataLength
    }

    private fun decodeDigits(value: String, dataLength: Int, decoded: ByteArray) {
        var accumulator = 0
        var bits = 0
        var offset = 0
        var alphabet = 0
        for (index in 0 until dataLength) {
            val character = value[index]
            alphabet = alphabet or alphabetMask(character)
            require(alphabet != MIXED_ALPHABET)
            accumulator = (accumulator shl CHARACTER_BITS) or digit(character)
            bits += CHARACTER_BITS
            if (bits >= BYTE_BITS) {
                bits -= BYTE_BITS
                decoded[offset++] = (accumulator ushr bits).toByte()
            }
            accumulator = accumulator and ((1 shl bits) - 1)
        }
        require(accumulator == 0 && offset == decoded.size)
    }

    private fun alphabetMask(character: Char): Int = when (character) {
        '+', '/' -> STANDARD_ALPHABET
        '-', '_' -> URL_ALPHABET
        else -> 0
    }

    private fun digit(character: Char): Int = when (character) {
        in 'A'..'Z' -> character - 'A'
        in 'a'..'z' -> character - 'a' + LOWERCASE_OFFSET
        in '0'..'9' -> character - '0' + DIGIT_OFFSET
        '+', '-' -> FIRST_SPECIAL_DIGIT
        '/', '_' -> SECOND_SPECIAL_DIGIT
        else -> throw IllegalArgumentException("Invalid Base64 character")
    }
}
