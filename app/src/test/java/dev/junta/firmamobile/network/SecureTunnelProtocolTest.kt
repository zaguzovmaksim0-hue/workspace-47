package dev.junta.firmamobile.network

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTunnelProtocolTest {
    @Test
    fun encodeConnectProducesTheExactFixedCrlfRequest() {
        val encoded = SecureTunnelProtocol.encodeConnect(
            SecureTunnelConnectRequest(authorization = "synthetic-qa-token".toCharArray()),
        )

        assertArrayEquals(
            (
                "CONNECT ws024.juntadeandalucia.es:443 HTTP/1.1\r\n" +
                    "Host: ws024.juntadeandalucia.es:443\r\n" +
                    "Authorization: Bearer synthetic-qa-token\r\n" +
                    "X-WS024-Tunnel-Version: 1\r\n" +
                    "\r\n"
                ).encodeToByteArray(),
            encoded,
        )

        // The returned request belongs to its caller and can be cleared after writing.
        encoded.fill(0)
        assertTrue(encoded.all { it == 0.toByte() })
    }

    @Test
    fun encodeConnectRejectsAnyAuthorityVersionOrCredentialInjection() {
        val invalidRequests = listOf(
            SecureTunnelConnectRequest(
                authority = "ws024.juntadeandalucia.es:444",
                authorization = "synthetic-qa-token".toCharArray(),
            ),
            SecureTunnelConnectRequest(
                protocolVersion = "2",
                authorization = "synthetic-qa-token".toCharArray(),
            ),
            SecureTunnelConnectRequest(authorization = "token\r\nInjected: yes".toCharArray()),
            SecureTunnelConnectRequest(authorization = "token\n".toCharArray()),
            SecureTunnelConnectRequest(authorization = charArrayOf('t', '\u0000', 'k')),
            SecureTunnelConnectRequest(authorization = "tóken".toCharArray()),
        )

        invalidRequests.forEach { request ->
            assertThrows(IllegalArgumentException::class.java) {
                SecureTunnelProtocol.encodeConnect(request)
            }
        }
    }

    @Test
    fun credentialHasOnlyAControlledBorrowAndIsClearedOnClose() {
        val owned = "synthetic-qa-token".toCharArray()
        val credential = QATunnelCredential(owned)

        credential.withValue { value ->
            assertSame(owned, value)
            assertArrayEquals("synthetic-qa-token".toCharArray(), value)
        }
        credential.close()

        assertArrayEquals(CharArray(owned.size), owned)
        assertThrows(IllegalStateException::class.java) {
            credential.withValue { error("closed credentials must not be borrowed") }
        }
    }

    @Test
    fun readResponseAcceptsExact200AndLeavesTheFirstInnerTlsByteUnread() {
        val input = ByteArrayInputStream(
            "HTTP/1.1 200 Connection Established\r\nRelay: synthetic\r\n\r\n".encodeToByteArray() +
                byteArrayOf(0x16, 0x03, 0x03),
        )

        assertEquals(SecureTunnelConnectResult.Established, SecureTunnelProtocol.readResponse(input))
        assertEquals(0x16, input.read())
    }

    @Test
    fun readResponseAcceptsAHeaderWhoseTerminatorEndsAtTheInclusiveLimit() {
        val prefix = "HTTP/1.1 200 OK\r\nX: "
        val suffix = "\r\n\r\n"
        val response = prefix + "a".repeat(
            SecureTunnelProtocol.MAX_RESPONSE_HEADER_BYTES - prefix.length - suffix.length,
        ) + suffix

        assertEquals(SecureTunnelProtocol.MAX_RESPONSE_HEADER_BYTES, response.encodeToByteArray().size)
        assertEquals(
            SecureTunnelConnectResult.Established,
            SecureTunnelProtocol.readResponse(ByteArrayInputStream(response.encodeToByteArray())),
        )
    }

    @Test
    fun readResponseAcceptsAHeaderWhoseTerminatorEndsAt8191Bytes() {
        val prefix = "HTTP/1.1 200 OK\r\nX: "
        val suffix = "\r\n\r\n"
        val response = prefix + "a".repeat(
            SecureTunnelProtocol.MAX_RESPONSE_HEADER_BYTES - 1 - prefix.length - suffix.length,
        ) + suffix

        assertEquals(SecureTunnelProtocol.MAX_RESPONSE_HEADER_BYTES - 1, response.encodeToByteArray().size)
        assertEquals(
            SecureTunnelConnectResult.Established,
            SecureTunnelProtocol.readResponse(ByteArrayInputStream(response.encodeToByteArray())),
        )
    }

    @Test
    fun readResponseRejectsUnfinishedOrOversizedHeadersAtTheLimit() {
        val prefix = "HTTP/1.1 200 OK\r\nX: "
        val suffix = "\r\n\r\n"
        val response = prefix + "a".repeat(
            SecureTunnelProtocol.MAX_RESPONSE_HEADER_BYTES - prefix.length - suffix.length + 1,
        ) + suffix

        assertRejected(SecureTunnelRejectCode.HEADER_TOO_LARGE, response)
        assertRejected(SecureTunnelRejectCode.INCOMPLETE_HEADER, "HTTP/1.1 200 OK\r\n")
    }

    @Test
    fun readResponseRejectsEofIncompleteHeaderExactlyAt8192Bytes() {
        val prefix = "HTTP/1.1 200 OK\r\nX: "
        val incompleteAtLimit = prefix + "a".repeat(
            SecureTunnelProtocol.MAX_RESPONSE_HEADER_BYTES - prefix.length,
        )
        assertEquals(SecureTunnelProtocol.MAX_RESPONSE_HEADER_BYTES, incompleteAtLimit.encodeToByteArray().size)
        assertRejected(SecureTunnelRejectCode.HEADER_TOO_LARGE, incompleteAtLimit)
    }

    @Test
    fun readResponseRejectsInvalidLineEndingsAndStatusLines() {
        assertRejected(SecureTunnelRejectCode.MALFORMED_LINE_ENDING, "HTTP/1.1 200 OK\n\n")
        assertRejected(SecureTunnelRejectCode.MALFORMED_LINE_ENDING, "HTTP/1.1 200 OK\rX: y\r\n\r\n")
        assertRejected(SecureTunnelRejectCode.UNSUPPORTED_HTTP_VERSION, "HTTP/1.0 200 OK\r\n\r\n")
        assertRejected(SecureTunnelRejectCode.MALFORMED_STATUS_LINE, "HTTP/1.1 200OK\r\n\r\n")
        assertRejected(SecureTunnelRejectCode.MALFORMED_STATUS_LINE, "HTTP/1.1 200\r\n\r\n")
        assertEquals(
            SecureTunnelConnectResult.Established,
            SecureTunnelProtocol.readResponse(ByteArrayInputStream("HTTP/1.1 200 \r\n\r\n".encodeToByteArray())),
        )
        assertRejected(SecureTunnelRejectCode.STATUS_NOT_OK, "HTTP/1.1 204 No Content\r\n\r\n")
    }

    @Test
    fun readResponseRejectsDuplicateStatusLinesObsFoldAndMalformedFields() {
        assertRejected(
            SecureTunnelRejectCode.MALFORMED_HEADER,
            "HTTP/1.1 200 OK\r\nHTTP/1.1 200 OK\r\n\r\n",
        )
        assertRejected(
            SecureTunnelRejectCode.MALFORMED_HEADER,
            "HTTP/1.1 200 OK\r\n folded: forbidden\r\n\r\n",
        )
        assertRejected(
            SecureTunnelRejectCode.MALFORMED_HEADER,
            "HTTP/1.1 200 OK\r\nNot A Header\r\n\r\n",
        )
    }

    @Test
    fun readResponseRejectsDeclaredBodyFramingWithoutReadingTheBody() {
        val input = ByteArrayInputStream(
            "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nbody".encodeToByteArray(),
        )

        assertEquals(
            SecureTunnelConnectResult.Rejected(SecureTunnelRejectCode.BODY_FRAMING_DECLARED),
            SecureTunnelProtocol.readResponse(input),
        )
        assertEquals('b'.code, input.read())
        assertRejected(
            SecureTunnelRejectCode.BODY_FRAMING_DECLARED,
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n",
        )
        assertRejected(
            SecureTunnelRejectCode.BODY_FRAMING_DECLARED,
            "HTTP/1.1 200 OK\r\ncOnTeNt-LeNgTh: 4\r\n\r\n",
        )
        assertRejected(
            SecureTunnelRejectCode.BODY_FRAMING_DECLARED,
            "HTTP/1.1 200 OK\r\ntRaNsFeR-EnCoDiNg: chunked\r\n\r\n",
        )
    }

    private fun assertRejected(expected: SecureTunnelRejectCode, response: String) {
        assertEquals(
            SecureTunnelConnectResult.Rejected(expected),
            SecureTunnelProtocol.readResponse(ByteArrayInputStream(response.encodeToByteArray())),
        )
    }
}
