package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject
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
    private val profileId = ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID)

    @Test
    fun exactObservedPostIsAuthorizedWithOnlyReviewedOpaqueFields() {
        val result = route(adapter(), validMessage())
        val accepted = result as EuskadiClientAuthPostBridgeRouteResult.Accepted
        val authorized = accepted.request.authorized
        assertEquals(profileId, authorized.profileId)
        assertEquals(EuskadiClientAuthPostBridgeAdapter.TARGET_URL, authorized.target.toASCIIString())
        assertEquals(false, authorized.policy.allowEmptyIssuerList)
        assertEquals(443, authorized.policy.requestPort)
        assertEquals(setOf("RSA", "EC"), authorized.certificateRules.allowedKeyAlgorithms)
        assertTrue(authorized.certificateRules.requireDigitalSignatureKeyUsage)
        val body = accepted.request.postBody
        try {
            val fields = body.toString(StandardCharsets.UTF_8).split('&').associate { pair ->
                val (name, value) = pair.split('=', limit = 2)
                name to URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }
            assertEquals(setOf("request", "x_correlation_id"), fields.keys)
            assertEquals(OPAQUE_REQUEST, fields["request"])
            assertEquals(CORRELATION_ID, fields["x_correlation_id"])
        } finally {
            body.fill(0)
        }
    }

    @Test
    fun wrongProfileOriginPagePathFrameMethodAndEpochFailClosed() {
        listOf(
            validMessage(profile = "junta-andalucia"),
            validMessage(method = "GET"),
            validMessage(target = EuskadiClientAuthPostBridgeAdapter.TARGET_URL + "/extra"),
            validMessage(contentType = "text/plain"),
        ).forEach { raw -> assertRejected(route(adapter(), raw), expected = null) }
        assertRejected(
            route(adapter(), validMessage(), source = Uri.parse("https://evil.example")),
            SigningErrorCode.ORIGIN_NOT_ALLOWED,
        )
        assertRejected(
            route(adapter(), validMessage(), page = EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE + "?x=1"),
            SigningErrorCode.UNOBSERVED_CONTRACT,
        )
        assertRejected(route(adapter(), validMessage(), mainFrame = false), SigningErrorCode.NAVIGATION_CHANGED)
        assertRejected(route(adapter(), validMessage(), epoch = -1), SigningErrorCode.INVALID_REQUEST)
        assertRejected(route(adapter(), validMessage(), epoch = Long.MAX_VALUE), SigningErrorCode.INVALID_REQUEST)
        val wrongActive = EuskadiClientAuthPostBridgeAdapter(
            profileRegistry = BuiltInSiteProfiles.qaRegistry,
            activeProfileId = { ProfileId("junta-andalucia") },
            monotonicNanos = { 1_000_000L },
        )
        assertRejected(route(wrongActive, validMessage()), SigningErrorCode.PROFILE_NOT_ACTIVE)
    }

    @Test
    fun exactEnvelopeRejectsMissingThirdAndDuplicateFields() {
        val base = JSONObject(validMessage())
        listOf("request", "x_correlation_id").forEach { field ->
            val missing = JSONObject(base.toString()).apply { remove(field) }.toString()
            assertRejected(route(adapter(), missing), SigningErrorCode.INVALID_REQUEST)
        }
        assertRejected(
            route(adapter(), JSONObject(base.toString()).put("extra", "not-reviewed").toString()),
            SigningErrorCode.INVALID_REQUEST,
        )
        val duplicate = validMessage().dropLast(1) + ",\"request\":\"duplicate\"}"
        assertRejected(route(adapter(), duplicate), SigningErrorCode.INVALID_REQUEST)
    }

    @Test
    fun emptyOversizeControlCharactersAndInvalidCorrelationUuidFailClosed() {
        listOf(
            validMessage(request = ""),
            validMessage(request = "x".repeat(4097)),
            validMessage(request = "abc\ndef"),
            validMessage(request = "abc\u0000def"),
            validMessage(correlation = ""),
            validMessage(correlation = "x".repeat(65)),
            validMessage(correlation = "9736cbef-2cf7-3d72-ae4e-c5efabc2a120"),
            validMessage(correlation = "9736cbef-2cf7-4d72-7e4e-c5efabc2a120"),
            validMessage(correlation = "not-a-uuid"),
        ).forEach { raw -> assertRejected(route(adapter(), raw), SigningErrorCode.INVALID_REQUEST) }
    }

    @Test
    fun requestCorrelationAndEpochReplayAreOneShot() {
        val adapter = adapter()
        val first = route(adapter, validMessage(), epoch = 7) as EuskadiClientAuthPostBridgeRouteResult.Accepted
        first.request.postBody.fill(0)
        assertRejected(route(adapter, validMessage(), epoch = 7), SigningErrorCode.INVALID_REQUEST)
        assertRejected(
            route(
                adapter,
                validMessage(requestId = SECOND_REQUEST_ID, correlation = SECOND_CORRELATION_ID),
                epoch = 7,
            ),
            SigningErrorCode.NAVIGATION_CHANGED,
        )
    }

    @Test
    fun correlationNonceCannotBeReusedAcrossEpochs() {
        val adapter = adapter()
        val first = route(adapter, validMessage(), epoch = 4) as EuskadiClientAuthPostBridgeRouteResult.Accepted
        first.request.postBody.fill(0)
        assertRejected(
            route(
                adapter,
                validMessage(requestId = SECOND_REQUEST_ID, correlation = CORRELATION_ID),
                epoch = 5,
            ),
            SigningErrorCode.INVALID_REQUEST,
        )
    }

    private fun adapter() = EuskadiClientAuthPostBridgeAdapter(
        profileRegistry = BuiltInSiteProfiles.qaRegistry,
        activeProfileId = { profileId },
        monotonicNanos = { 1_000_000L },
    )

    private fun route(
        adapter: EuskadiClientAuthPostBridgeAdapter,
        raw: String,
        source: Uri = Uri.parse(EuskadiClientAuthPostBridgeAdapter.IZENPE_ORIGIN),
        mainFrame: Boolean = true,
        epoch: Long = 9,
        page: String = EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE,
    ) = adapter.route(raw, source, mainFrame, epoch, page)

    private fun assertRejected(result: EuskadiClientAuthPostBridgeRouteResult, expected: SigningErrorCode?) {
        val rejected = result as EuskadiClientAuthPostBridgeRouteResult.Rejected
        if (expected != null) assertEquals(expected, rejected.code)
    }

    private fun validMessage(
        requestId: String = REQUEST_ID,
        profile: String = EuskadiClientAuthPostBridgeAdapter.PROFILE_ID,
        method: String = "POST",
        contentType: String = EuskadiClientAuthPostBridgeAdapter.FORM_CONTENT_TYPE,
        target: String = EuskadiClientAuthPostBridgeAdapter.TARGET_URL,
        request: String = OPAQUE_REQUEST,
        correlation: String = CORRELATION_ID,
    ): String = JSONObject()
        .put("type", "EUSKADI_CLIENT_AUTH_POST")
        .put("profileId", profile)
        .put("requestId", requestId)
        .put("method", method)
        .put("contentType", contentType)
        .put("targetUrl", target)
        .put("request", request)
        .put("x_correlation_id", correlation)
        .toString()

    private companion object {
        const val REQUEST_ID = "9736cbef-2cf7-4d72-ae4e-c5efabc2a120"
        const val SECOND_REQUEST_ID = "9736cbef-2cf7-4d72-ae4e-c5efabc2a121"
        const val OPAQUE_REQUEST = "eyJhbGciOiJSUzI1NiJ9.payload-with_dash/segment.value"
        const val CORRELATION_ID = "5df82aa2-af47-4f78-b67d-bd958e41ee81"
        const val SECOND_CORRELATION_ID = "57e141cb-1a4f-4710-a255-d1a094ef6e2c"
    }
}
