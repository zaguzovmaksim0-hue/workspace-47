package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.DgtVerificationCadesAdapter
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningFormat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
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
class DgtVerificationMiniAppletBridgeTest {
    private val origin = Uri.parse(DgtVerificationCadesAdapter.INITIATOR_ORIGIN)

    @Test
    fun acceptsOnlyTheExactDgtMiniAppletRequest() {
        val result = adapterFor(DgtVerificationCadesAdapter.PROFILE_ID).route(
            rawMessage = message(),
            sourceOrigin = origin,
            isMainFrame = true,
            navigationEpoch = 17,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(DgtVerificationCadesAdapter.ID, request.protocolId)
            assertEquals(DgtVerificationCadesAdapter.PROFILE_ID, request.context.profileId)
            assertEquals(DgtVerificationCadesAdapter.PROFILE_VERSION, request.context.profileVersion)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(DgtVerificationCadesAdapter.EXPECTED_PAYLOAD, data)
                    assertEquals(DgtVerificationCadesAdapter.EXPECTED_EXTRA_PROPERTIES, properties)
                }
            }
        }
    }

    @Test
    fun rejectsWrongActiveProfileOriginPayloadAndProperties() {
        assertRejected(
            message(),
            origin,
            ProfileId("aragon-siraw"),
            SigningErrorCode.ORIGIN_NOT_ALLOWED,
        )
        assertRejected(
            message(),
            Uri.parse("https://sede.dgt.gob.es.evil.example"),
            ProfileId(DgtVerificationCadesAdapter.PROFILE_ID),
            SigningErrorCode.ORIGIN_NOT_ALLOWED,
        )
        val wrongPayload = DgtVerificationCadesAdapter.EXPECTED_PAYLOAD.copyOf()
            .also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertRejected(
            message(data = wrongPayload),
            origin,
            ProfileId(DgtVerificationCadesAdapter.PROFILE_ID),
            SigningErrorCode.INVALID_REQUEST,
        )
        wrongPayload.fill(0)
        assertRejected(
            message(properties = "filter=nonexpired"),
            origin,
            ProfileId(DgtVerificationCadesAdapter.PROFILE_ID),
            SigningErrorCode.INVALID_REQUEST,
        )
    }

    private fun adapterFor(profileId: String): MiniAppletBridgeAdapter = MiniAppletBridgeAdapter(
        clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        activeProfileId = { ProfileId(profileId) },
    )

    private fun assertRejected(
        rawMessage: String,
        sourceOrigin: Uri,
        activeProfile: ProfileId,
        code: SigningErrorCode,
    ) {
        val result = adapterFor(activeProfile.value).route(
            rawMessage = rawMessage,
            sourceOrigin = sourceOrigin,
            isMainFrame = true,
        ) as MiniAppletBridgeRouteResult.Rejected
        assertEquals(code, result.code)
    }

    private fun message(
        data: ByteArray = DgtVerificationCadesAdapter.EXPECTED_PAYLOAD,
        properties: String = DgtVerificationCadesAdapter.EXPECTED_EXTRA_PROPERTIES,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", Base64.getEncoder().encodeToString(data))
        .put("algorithm", "SHA1withRSA")
        .put("format", "CAdES")
        .put("extraProperties", properties)
        .toString()

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174001"
    }
}
