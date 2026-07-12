package dev.junta.firmamobile.afirma

import android.net.Uri
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import org.junit.Assert.assertEquals
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
class AfirmaUriParserTest {
    private val parser = AfirmaUriParser()
    private val trustedOrigin = checkNotNull(
        JuntaOriginPolicy.originFor(Uri.parse("https://www.juntadeandalucia.es/login")),
    )

    @Test
    fun parsesSignAndPreservesEncodedAndExactlyOnceDecodedValues() {
        val result = parser.parse(
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES" +
                "&dat=YWJjLQ&properties=a%252Bb" +
                "&stservlet=https%3A%2F%2Fws024.juntadeandalucia.es%2Fsign",
            trustedOrigin,
        ) as AfirmaParseResult.Success

        assertEquals(AfirmaOperation.SIGN, result.request.operation)
        assertEquals("SHA256withRSA", result.request.singleValue("algorithm"))
        assertEquals("CAdES", result.request.singleValue("format"))
        assertEquals("YWJjLQ", result.request.singleValue("dat"))
        val properties = result.request.parameters.getValue("properties").single()
        assertEquals("a%252Bb", properties.encodedValue)
        assertEquals("a%2Bb", properties.decodedValue)
    }

    @Test
    fun acceptsOnlyImplementedOperations() {
        val accepted = mapOf(
            "afirma://selectcert" to AfirmaOperation.SELECT_CERTIFICATE,
            "afirma://websocket" to AfirmaOperation.WEBSOCKET,
        )
        accepted.forEach { (rawUri, operation) ->
            val result = parser.parse(rawUri, trustedOrigin) as AfirmaParseResult.Success
            assertEquals(operation, result.request.operation)
        }

        assertFailure(
            parser.parse("afirma://batch?id=1", trustedOrigin),
            AfirmaParseErrorCode.UNSUPPORTED_OPERATION,
        )
    }

    @Test
    fun rejectsMissingSignContractAndDuplicateCriticalValues() {
        assertFailure(
            parser.parse("afirma://sign?format=CAdES&dat=abc", trustedOrigin),
            AfirmaParseErrorCode.MISSING_REQUIRED_PARAMETER,
        )
        assertFailure(
            parser.parse(
                "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=one&dat=two",
                trustedOrigin,
            ),
            AfirmaParseErrorCode.DUPLICATE_CRITICAL_PARAMETER,
        )
        assertFailure(
            parser.parse(
                "afirma://sign?algorithm=one&Algorithm=two&format=CAdES",
                trustedOrigin,
            ),
            AfirmaParseErrorCode.DUPLICATE_CRITICAL_PARAMETER,
        )
    }

    @Test
    fun rejectsUntrustedOriginAndMalformedOrOversizedUris() {
        assertFailure(
            parser.parse(
                "afirma://sign?algorithm=SHA256withRSA&format=CAdES",
                TrustedOrigin("https", "evil.example", 443),
            ),
            AfirmaParseErrorCode.UNTRUSTED_ORIGIN,
        )
        assertFailure(
            parser.parse(
                "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=%ZZ",
                trustedOrigin,
            ),
            AfirmaParseErrorCode.MALFORMED_ENCODING,
        )
        val oversized = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=" +
            "a".repeat(AfirmaUriParser.MAX_URI_CHARS)
        assertFailure(
            parser.parse(oversized, trustedOrigin),
            AfirmaParseErrorCode.URI_TOO_LARGE,
        )
    }

    @Test
    fun validatesEveryObservedCallbackUrlAgainstTheExactAllowlist() {
        val rejectedCallbacks = listOf(
            "https%3A%2F%2Fevil.example%2Fsign",
            "https%3A%2F%2F127.0.0.1%2Fsign",
            "javascript%3Aalert%281%29",
            "https%3A%2F%2Fuser%40ws024.juntadeandalucia.es%2Fsign",
            "https%3A%2F%2Fws024.juntadeandalucia.es%3A8443%2Fsign",
        )
        rejectedCallbacks.forEach { callback ->
            assertFailure(
                parser.parse(
                    "afirma://sign?algorithm=SHA256withRSA&format=CAdES" +
                        "&serverurl=$callback",
                    trustedOrigin,
                ),
                AfirmaParseErrorCode.UNSAFE_CALLBACK_URL,
            )
        }
    }

    @Test
    fun preservesBase64UrlDataAndUnknownParametersForRuntimeObservation() {
        val result = parser.parse(
            "afirma://sign?algorithm=SHA1withRSA&format=CAdES" +
                "&dat=ab_CD-12&portalFutureField=value",
            trustedOrigin,
        ) as AfirmaParseResult.Success

        assertEquals("ab_CD-12", result.request.singleValue("dat"))
        assertEquals("value", result.request.singleValue("portalfuturefield"))
        assertTrue(result.request.rawUri.startsWith("afirma://sign?"))
    }

    private fun assertFailure(result: AfirmaParseResult, code: AfirmaParseErrorCode) {
        assertEquals(code, (result as AfirmaParseResult.Failure).code)
    }
}
