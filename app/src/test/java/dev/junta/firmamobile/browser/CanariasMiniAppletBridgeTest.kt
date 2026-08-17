package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.CanariasCertificateLoginCadesAdapter
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
class CanariasMiniAppletBridgeTest {
    @Test
    fun acceptsOnlyTheExactPublicCertificateLoginTuple() {
        val challenge = CHALLENGE.encodeToByteArray()
        val result = MiniAppletBridgeAdapter(
            clock = Clock.fixed(Instant.parse("2026-08-17T21:55:05Z"), ZoneOffset.UTC),
            activeProfileId = { ProfileId(PROFILE_ID) },
        ).route(
            rawMessage = message(challenge),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9,
            currentPageUrl = SIGNING_PAGE_URL,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(CanariasCertificateLoginCadesAdapter.ID, request.protocolId)
            assertEquals(PROFILE_ID, request.context.profileId)
            assertEquals(SIGNING_PAGE_URL, request.context.pageUrl)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals("CADES", request.format.name)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(challenge, data)
                    assertEquals(EXTRA_PROPERTIES, properties)
                }
            }
        }
        challenge.fill(0)
    }

    @Test
    fun rejectsWrongPageChallengeAlgorithmFormatOrProperties() {
        val adapter = MiniAppletBridgeAdapter(activeProfileId = { ProfileId(PROFILE_ID) })
        fun route(raw: String, page: String = SIGNING_PAGE_URL) = adapter.route(
            rawMessage = raw,
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9,
            currentPageUrl = page,
        )
        val exact = message(CHALLENGE.encodeToByteArray())
        assertEquals(true, route(exact, "$ORIGIN/sede/other") is MiniAppletBridgeRouteResult.Rejected)
        assertEquals(true, route(exact.replace("SHA1withRSA", "SHA256withRSA")) is MiniAppletBridgeRouteResult.Rejected)
        assertEquals(true, route(exact.replace("CAdES", "XAdES")) is MiniAppletBridgeRouteResult.Rejected)
        assertEquals(true, route(exact.replace("signingCert:true", "signingCert:false")) is MiniAppletBridgeRouteResult.Rejected)
        assertEquals(true, route(message("Mon, 17 Aug 2026 21:55:05 UTC".encodeToByteArray())) is MiniAppletBridgeRouteResult.Rejected)
    }

    private fun message(challenge: ByteArray): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", "123e4567-e89b-42d3-a456-426614174001")
        .put("requestId", "123e4567-e89b-42d3-a456-426614174000")
        .put("dataB64", Base64.getEncoder().encodeToString(challenge))
        .put("algorithm", "SHA1withRSA")
        .put("format", "CAdES")
        .put("extraProperties", EXTRA_PROPERTIES)
        .toString()
        .also { challenge.fill(0) }

    private companion object {
        const val PROFILE_ID = CanariasCertificateLoginCadesAdapter.PROFILE_ID
        const val ORIGIN = CanariasCertificateLoginCadesAdapter.INITIATOR_ORIGIN
        const val SIGNING_PAGE_URL = CanariasCertificateLoginCadesAdapter.SIGNING_PAGE_URL
        const val CHALLENGE = "Mon, 17 Aug 2026 21:55:05 GMT"
        const val EXTRA_PROPERTIES = CanariasCertificateLoginCadesAdapter.EXPECTED_EXTRA_PROPERTIES
    }
}
