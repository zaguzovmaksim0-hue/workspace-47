package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class WebMessageProtocolTest {
    @Test
    fun acceptsBoundedAfirmaEnvelopeForTheExpectedProfileAndActualSourceOrigin() {
        val result = WebMessageProtocol.parse(
            rawMessage = """{
                "type":"AFIRMA_URI",
                "requestId":"$REQUEST_ID",
                "uri":"afirma://sign?algorithm=SHA256withRSA&format=CAdES",
                "origin":"https://evil.example"
            }""".trimIndent(),
            sourceOrigin = TRUSTED_ORIGIN,
            expectedProfileId = JUNTA_PROFILE,
        ) as WebMessageParseResult.Success

        assertEquals(REQUEST_ID, result.message.requestId)
        assertEquals(
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES",
            result.message.uri,
        )
        assertEquals("www.juntadeandalucia.es", result.message.sourceOrigin.host)
    }

    @Test
    fun rejectsUntrustedOrForeignProfileOriginsUnknownTypesBadIdsAndOversizedMessages() {
        assertFailure(
            WebMessageProtocol.parse(validMessage(), Uri.parse("https://evil.example"), JUNTA_PROFILE),
            WebMessageErrorCode.UNTRUSTED_ORIGIN,
        )
        assertFailure(
            WebMessageProtocol.parse(validMessage(), RED_SARA_ORIGIN, JUNTA_PROFILE),
            WebMessageErrorCode.UNTRUSTED_ORIGIN,
        )
        assertFailure(
            WebMessageProtocol.parse(
                validMessage().replace("AFIRMA_URI", "READ_FILE"),
                TRUSTED_ORIGIN,
                JUNTA_PROFILE,
            ),
            WebMessageErrorCode.UNSUPPORTED_TYPE,
        )
        assertFailure(
            WebMessageProtocol.parse(
                validMessage().replace(REQUEST_ID, "not-a-uuid"),
                TRUSTED_ORIGIN,
                JUNTA_PROFILE,
            ),
            WebMessageErrorCode.INVALID_REQUEST_ID,
        )
        assertFailure(
            WebMessageProtocol.parse(
                " ".repeat(WebMessageProtocol.MAX_MESSAGE_CHARS + 1),
                TRUSTED_ORIGIN,
                JUNTA_PROFILE,
            ),
            WebMessageErrorCode.MESSAGE_TOO_LARGE,
        )
    }

    @Test
    fun replySerializationIsJsonAndContainsNoExecutableConcatenation() {
        val reply = WebMessageProtocol.replyJson(
            requestId = REQUEST_ID,
            status = WebMessageReplyStatus.REJECTED,
            errorCode = "INVALID_AFIRMA_URI",
        )
        val json = JSONObject(reply)

        assertEquals("AFIRMA_ACK", json.getString("type"))
        assertEquals(REQUEST_ID, json.getString("requestId"))
        assertEquals("rejected", json.getString("status"))
        assertEquals("INVALID_AFIRMA_URI", json.getString("errorCode"))
        assertFalse(reply.contains("javascript:"))
    }

    @Test
    fun malformedJsonAndNonAfirmaUrisFailClosed() {
        assertFailure(
            WebMessageProtocol.parse("{", TRUSTED_ORIGIN, JUNTA_PROFILE),
            WebMessageErrorCode.MALFORMED_JSON,
        )
        val result = WebMessageProtocol.parse(
            validMessage().replace("afirma://", "https://"),
            TRUSTED_ORIGIN,
            JUNTA_PROFILE,
        )
        assertTrue(result is WebMessageParseResult.Failure)
        assertEquals(
            WebMessageErrorCode.UNSUPPORTED_URI_SCHEME,
            (result as WebMessageParseResult.Failure).code,
        )
    }

    @Test
    fun profileWithoutAfirmaCapabilityCannotRouteAfirmaMessages() {
        assertFailure(
            WebMessageProtocol.parse(
                validMessage(),
                RED_SARA_ORIGIN,
                ProfileId("reg-age-redsara"),
            ),
            WebMessageErrorCode.UNTRUSTED_ORIGIN,
        )
    }

    private fun validMessage() =
        """{"type":"AFIRMA_URI","requestId":"$REQUEST_ID","uri":"afirma://selectcert"}"""

    private fun assertFailure(result: WebMessageParseResult, code: WebMessageErrorCode) {
        assertEquals(code, (result as WebMessageParseResult.Failure).code)
    }

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
        val JUNTA_PROFILE = ProfileId("junta-andalucia")
        val TRUSTED_ORIGIN: Uri = Uri.parse("https://www.juntadeandalucia.es")
        val RED_SARA_ORIGIN: Uri = Uri.parse("https://reg.redsara.es")
    }
}
