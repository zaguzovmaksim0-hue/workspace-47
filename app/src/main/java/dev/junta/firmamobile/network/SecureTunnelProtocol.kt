package dev.junta.firmamobile.network

import dev.junta.firmamobile.network.SecureTunnelRejectCode.*
import java.io.IOException
import java.io.InputStream

/** Owns the supplied credential array until [close] clears it. */
internal class QATunnelCredential internal constructor(
    private val opaqueValue: CharArray,
) : AutoCloseable {
    private var closed = false

    /**
     * Gives a caller temporary access to the owned value while this credential remains open.
     * The array must not be retained by [block].
     */
    @Synchronized
    internal fun <T> withValue(block: (CharArray) -> T): T {
        check(!closed) { "QA tunnel credential is closed" }
        return block(opaqueValue)
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            opaqueValue.fill('\u0000')
            closed = true
        }
    }
}

internal data class SecureTunnelConnectRequest(
    val authority: String = SecureTunnelProtocol.FIXED_AUTHORITY,
    val protocolVersion: String = SecureTunnelProtocol.VERSION,
    val authorization: CharArray,
)

internal sealed interface SecureTunnelConnectResult {
    data object Established : SecureTunnelConnectResult

    data class Rejected(val code: SecureTunnelRejectCode) : SecureTunnelConnectResult
}

internal enum class SecureTunnelRejectCode {
    INPUT_FAILURE,
    INCOMPLETE_HEADER,
    HEADER_TOO_LARGE,
    MALFORMED_LINE_ENDING,
    MALFORMED_STATUS_LINE,
    UNSUPPORTED_HTTP_VERSION,
    STATUS_NOT_OK,
    MALFORMED_HEADER,
    BODY_FRAMING_DECLARED,
}

internal object SecureTunnelProtocol {
    const val FIXED_AUTHORITY = "ws024.juntadeandalucia.es:443"
    const val VERSION = "1"
    const val MAX_RESPONSE_HEADER_BYTES = 8192

    private val connectPrefix = (
        "CONNECT $FIXED_AUTHORITY HTTP/1.1\r\n" +
            "Host: $FIXED_AUTHORITY\r\n" +
            "Authorization: Bearer "
        ).encodeToByteArray()
    private val connectSuffix = "\r\nX-WS024-Tunnel-Version: $VERSION\r\n\r\n".encodeToByteArray()
    private val contentLengthName = "Content-Length".encodeToByteArray()
    private val transferEncodingName = "Transfer-Encoding".encodeToByteArray()
    private val httpPrefix = "HTTP/".encodeToByteArray()
    private val http11Prefix = "HTTP/1.1".encodeToByteArray()
    private val successfulStatus = "200".encodeToByteArray()

    /**
     * Encodes the fixed CONNECT request. The returned array is caller-owned and must be cleared
     * by that caller after it has been written to the outer TLS stream.
     */
    fun encodeConnect(request: SecureTunnelConnectRequest): ByteArray {
        require(request.authority == FIXED_AUTHORITY) { "Unexpected CONNECT authority" }
        require(request.protocolVersion == VERSION) { "Unexpected tunnel protocol version" }
        require(request.authorization.isNotEmpty()) { "Missing tunnel credential" }

        val credentialBytes = ByteArray(request.authorization.size)
        try {
            request.authorization.forEachIndexed { index, character ->
                require(character in '!'..'~') { "Invalid tunnel credential" }
                credentialBytes[index] = character.code.toByte()
            }

            return ByteArray(connectPrefix.size + credentialBytes.size + connectSuffix.size).also { requestBytes ->
                var offset = 0
                connectPrefix.copyInto(requestBytes, destinationOffset = offset)
                offset += connectPrefix.size
                credentialBytes.copyInto(requestBytes, destinationOffset = offset)
                offset += credentialBytes.size
                connectSuffix.copyInto(requestBytes, destinationOffset = offset)
            }
        } finally {
            credentialBytes.fill(0)
        }
    }

    /**
     * Reads exactly one HTTP CONNECT response header from [input], without wrapping or buffering
     * it. Bytes after the terminating CRLFCRLF are deliberately left for the inner TLS stream.
     */
    fun readResponse(input: InputStream): SecureTunnelConnectResult {
        val header = ByteArray(MAX_RESPONSE_HEADER_BYTES)
        var count = 0
        var lineEndingState = ReadingLine

        while (count < MAX_RESPONSE_HEADER_BYTES) {
            val byte = try {
                input.read()
            } catch (_: IOException) {
                return rejected(INPUT_FAILURE)
            }
            if (byte == -1) return rejected(INCOMPLETE_HEADER)

            header[count++] = byte.toByte()
            lineEndingState = when (lineEndingState) {
                ReadingLine -> when (byte) {
                    CarriageReturn -> AfterLineCarriageReturn
                    LineFeed -> return rejected(MALFORMED_LINE_ENDING)
                    else -> ReadingLine
                }

                AfterLineCarriageReturn -> if (byte == LineFeed) {
                    AtLineStart
                } else {
                    return rejected(MALFORMED_LINE_ENDING)
                }

                AtLineStart -> when (byte) {
                    CarriageReturn -> AfterTerminalCarriageReturn
                    LineFeed -> return rejected(MALFORMED_LINE_ENDING)
                    else -> ReadingLine
                }

                AfterTerminalCarriageReturn -> if (byte == LineFeed) {
                    return parseResponseHeader(header, count)
                } else {
                    return rejected(MALFORMED_LINE_ENDING)
                }

                else -> error("Unknown header parser state")
            }
        }

        return rejected(HEADER_TOO_LARGE)
    }

    private fun parseResponseHeader(header: ByteArray, headerSize: Int): SecureTunnelConnectResult {
        // Keep the CRLF terminating the final header field; exclude only the empty-line CRLF.
        val headerEnd = headerSize - CRLF_SIZE
        val statusEnd = findCrlf(header, 0, headerEnd) ?: return rejected(MALFORMED_STATUS_LINE)
        validateStatusLine(header, 0, statusEnd)?.let { return rejected(it) }

        var lineStart = statusEnd + CRLF_SIZE
        while (lineStart < headerEnd) {
            val lineEnd = findCrlf(header, lineStart, headerEnd) ?: return rejected(MALFORMED_HEADER)
            val headerCode = validateHeaderField(header, lineStart, lineEnd)
            if (headerCode != null) return rejected(headerCode)
            lineStart = lineEnd + CRLF_SIZE
        }
        return SecureTunnelConnectResult.Established
    }

    private fun validateStatusLine(
        bytes: ByteArray,
        start: Int,
        endExclusive: Int,
    ): SecureTunnelRejectCode? {
        if (!matchesAscii(bytes, start, endExclusive, httpPrefix)) {
            return MALFORMED_STATUS_LINE
        }
        if (!matchesAscii(bytes, start, endExclusive, http11Prefix)) {
            return UNSUPPORTED_HTTP_VERSION
        }
        val statusStart = start + HTTP_11_PREFIX_SIZE
        if (endExclusive < statusStart + 1 + STATUS_CODE_SIZE || bytes[statusStart].unsigned() != Space) {
            return MALFORMED_STATUS_LINE
        }
        val codeStart = statusStart + 1
        if ((codeStart until codeStart + STATUS_CODE_SIZE).any { !bytes[it].unsigned().isAsciiDigit() }) {
            return MALFORMED_STATUS_LINE
        }
        if (!matchesAscii(bytes, codeStart, endExclusive, successfulStatus)) {
            return STATUS_NOT_OK
        }
        val afterCode = codeStart + STATUS_CODE_SIZE
        if (afterCode >= endExclusive || bytes[afterCode].unsigned() != Space) {
            return MALFORMED_STATUS_LINE
        }
        if ((afterCode + 1 until endExclusive).any { !bytes[it].unsigned().isReasonPhraseByte() }) {
            return MALFORMED_STATUS_LINE
        }
        return null
    }

    private fun validateHeaderField(
        bytes: ByteArray,
        start: Int,
        endExclusive: Int,
    ): SecureTunnelRejectCode? {
        if (start == endExclusive || bytes[start].unsigned() == Space || bytes[start].unsigned() == HorizontalTab) {
            return MALFORMED_HEADER
        }
        var colon = start
        while (colon < endExclusive && bytes[colon].unsigned() != Colon) colon++
        if (colon == start || colon == endExclusive) return MALFORMED_HEADER
        if ((start until colon).any { !bytes[it].unsigned().isTokenByte() }) return MALFORMED_HEADER
        if ((colon + 1 until endExclusive).any { !bytes[it].unsigned().isFieldValueByte() }) {
            return MALFORMED_HEADER
        }
        if (matchesAsciiIgnoreCase(bytes, start, colon, contentLengthName) ||
            matchesAsciiIgnoreCase(bytes, start, colon, transferEncodingName)
        ) {
            return BODY_FRAMING_DECLARED
        }
        return null
    }

    private fun findCrlf(bytes: ByteArray, start: Int, endExclusive: Int): Int? {
        var index = start
        while (index + 1 < endExclusive) {
            if (bytes[index].unsigned() == CarriageReturn && bytes[index + 1].unsigned() == LineFeed) {
                return index
            }
            index++
        }
        return null
    }

    private fun matchesAscii(bytes: ByteArray, start: Int, endExclusive: Int, expected: ByteArray): Boolean =
        endExclusive - start >= expected.size && expected.indices.all { index ->
            bytes[start + index] == expected[index]
        }

    private fun matchesAsciiIgnoreCase(
        bytes: ByteArray,
        start: Int,
        endExclusive: Int,
        expected: ByteArray,
    ): Boolean = endExclusive - start == expected.size && expected.indices.all { index ->
        bytes[start + index].unsigned().asciiLowercase() == expected[index].unsigned().asciiLowercase()
    }

    private fun Int.isAsciiDigit(): Boolean = this in '0'.code..'9'.code

    private fun Int.isReasonPhraseByte(): Boolean = this == HorizontalTab || this in Space..Tilde

    private fun Int.isFieldValueByte(): Boolean = this == HorizontalTab || this in Space..Tilde

    private fun Int.isTokenByte(): Boolean =
        isAsciiDigit() || this in 'A'.code..'Z'.code || this in 'a'.code..'z'.code ||
            this in TokenPunctuation

    private fun Int.asciiLowercase(): Int = if (this in 'A'.code..'Z'.code) this + 32 else this

    private fun Byte.unsigned(): Int = toInt() and 0xff

    private fun rejected(code: SecureTunnelRejectCode) = SecureTunnelConnectResult.Rejected(code)

    private const val ReadingLine = 0
    private const val AfterLineCarriageReturn = 1
    private const val AtLineStart = 2
    private const val AfterTerminalCarriageReturn = 3
    private const val CarriageReturn = '\r'.code
    private const val LineFeed = '\n'.code
    private const val HorizontalTab = '\t'.code
    private const val Space = ' '.code
    private const val Colon = ':'.code
    private const val Tilde = '~'.code
    private const val CRLF_SIZE = 2
    private const val HTTP_11_PREFIX_SIZE = 8
    private const val STATUS_CODE_SIZE = 3
    private val TokenPunctuation = intArrayOf(
        '!'.code, '#'.code, '$'.code, '%'.code, '&'.code, '\''.code, '*'.code, '+'.code,
        '-'.code, '.'.code, '^'.code, '_'.code, '`'.code, '|'.code, '~'.code,
    )
}
