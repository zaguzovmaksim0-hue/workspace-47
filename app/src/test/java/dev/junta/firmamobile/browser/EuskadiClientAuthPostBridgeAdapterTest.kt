package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
class EuskadiClientAuthPostBridgeAdapterTest {
    private var monotonic = 1_000_000L
    private val profileId = ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID)
    private val adapter = EuskadiClientAuthPostBridgeAdapter(
        profileRegistry = BuiltInSiteProfiles.qaRegistry,
        activeProfileId = { profileId },
        monotonicNanos = { monotonic },
    )

    @Test
    fun exactObservedPostIsAuthorizedWithOpaqueFormBody() {
        val result = adapter.route(
            rawMessage = validMessage(REQUEST_ID),
            sourceOrigin = Uri.parse(EuskadiClientAuthPostBridgeAdapter.IZENPE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9,
            currentPageUrl = EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE,
        )

        val accepted = result as EuskadiClientAuthPostBridgeRouteResult.Accepted
        val authorized = accepted.request.authorized
        assertEquals(profileId, authorized.profileId)
        assertEquals(EuskadiClientAuthPostBridgeAdapter.TARGET_URL, authorized.target.toASCIIString())
        assertEquals(false, authorized.policy.allowEmptyIssuerList)
        assertEquals(setOf("RSA", "EC"), authorized.certificateRules.allowedKeyAlgorithms)
        val body = checkNotNull(authorized.postBody).toString(StandardCharsets.UTF_8)
        val fields = body.split('&').associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            name to URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }
        assertEquals(setOf("request", "x_correlation_id"), fields.keys)
        assertEquals(OPAQUE_REQUEST, fields["request"])
        assertEquals(CORRELATION_ID, fields["x_correlation_id"])
    }

    @Test
    fun replayAndExpandedContextFailClosed() {
        val source = Uri.parse(EuskadiClientAuthPostBridgeAdapter.IZENPE_ORIGIN)
        val first = adapter.route(validMessage(REQUEST_ID), source, true, 2, EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE)
        assertTrue(first is EuskadiClientAuthPostBridgeRouteResult.Accepted)
        assertRejected(
            adapter.route(validMessage(REQUEST_ID), source, true, 2, EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE),
            SigningErrorCode.INVALID_REQUEST,
        )

        val cases = listOf(
            adapter.route(validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a121"), Uri.parse("https://evil.example"), true, 2, EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE),
            adapter.route(validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a122"), source, false, 2, EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE),
            adapter.route(validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a123"), source, true, 2, EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE + "?extra=1"),
            adapter.route(validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a124"), source, true, Long.MAX_VALUE, EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE),
        )
        assertTrue(cases.all { it is EuskadiClientAuthPostBridgeRouteResult.Rejected })
    }

    @Test
    fun unexpectedKeysAndUnsafeOpaqueValuesFailClosed() {
        val source = Uri.parse(EuskadiClientAuthPostBridgeAdapter.IZENPE_ORIGIN)
        val invalid = listOf(
            validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a131").dropLast(1) + ",\"extra\":1}",
            validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a132", request = "has space"),
            validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a133", correlation = "bad+base64"),
            validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a134", request = "x".repeat(4097)),
            validMessage("9736cbef-2cf7-4d72-ae4e-c5efabc2a135", correlation = "x".repeat(1025)),
        )
        invalid.forEach { raw ->
            assertTrue(
                adapter.route(raw, source, true, 3, EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE) is
                    EuskadiClientAuthPostBridgeRouteResult.Rejected,
            )
        }
    }

    private fun assertRejected(result: EuskadiClientAuthPostBridgeRouteResult, code: SigningErrorCode) {
        val rejected = result as EuskadiClientAuthPostBridgeRouteResult.Rejected
        assertEquals(code, rejected.code)
    }

    private fun validMessage(
        requestId: String,
        request: String = OPAQUE_REQUEST,
        correlation: String = CORRELATION_ID,
    ): String = org.json.JSONObject()
        .put("type", "EUSKADI_CLIENT_AUTH_POST")
        .put("requestId", requestId)
        .put("request", request)
        .put("xCorrelationId", correlation)
        .toString()

    private companion object {
        const val REQUEST_ID = "9736cbef-2cf7-4d72-ae4e-c5efabc2a120"
        const val OPAQUE_REQUEST = "eyJhbGciOiJSUzI1NiJ9.payload-with_dash/segment.value"
        const val CORRELATION_ID = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo0123456789"
    }
}
