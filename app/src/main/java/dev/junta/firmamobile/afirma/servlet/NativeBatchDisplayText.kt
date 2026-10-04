package dev.junta.firmamobile.afirma.servlet

internal object NativeBatchDisplayText {
    // Display only; never replace wire IDs/statuses or signing inputs with this text.
    fun bounded(raw: String, maxCodePoints: Int): String {
        require(maxCodePoints in 1..512) { "maxCodePoints must be in 1..512" }
        val result = StringBuilder()
        var offset = 0
        var count = 0
        var pendingSpace = false

        while (offset < raw.length) {
            val decoded = Character.codePointAt(raw, offset)
            offset += Character.charCount(decoded)
            val codePoint = if (decoded in 0xD800..0xDFFF) 0xFFFD else decoded

            if (Character.getType(codePoint) == Character.FORMAT.toInt()) continue
            if (Character.isWhitespace(codePoint) ||
                Character.isSpaceChar(codePoint) ||
                Character.isISOControl(codePoint)
            ) {
                pendingSpace = result.isNotEmpty()
                continue
            }

            if (pendingSpace) {
                result.append(' ')
                count++
            }
            result.appendCodePoint(codePoint)
            count++
            pendingSpace = false

            if (count > maxCodePoints) {
                result.setLength(result.offsetByCodePoints(0, maxCodePoints - 1))
                result.append('\u2026')
                return result.toString()
            }
        }
        return result.toString()
    }
}
