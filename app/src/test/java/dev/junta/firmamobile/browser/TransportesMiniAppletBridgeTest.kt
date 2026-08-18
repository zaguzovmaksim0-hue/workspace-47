package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningFormat
import dev.junta.firmamobile.signing.TransportesXadesEnvelopedAdapter
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
class TransportesMiniAppletBridgeTest {
    private val adapter = MiniAppletBridgeAdapter(
        clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        activeProfileId = { ProfileId(TransportesXadesEnvelopedAdapter.PROFILE_ID) },
    )

    @Test
    fun exactAuthPageNormalizesOnlyObservedTransportesContract() {
        val result = adapter.route(
            rawMessage = message(),
            sourceOrigin = ORIGIN,
            isMainFrame = true,
            navigationEpoch = 75,
            currentPageUrl = TransportesXadesEnvelopedAdapter.AUTH_PAGE_URL,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(TransportesXadesEnvelopedAdapter.PROFILE_ID, request.context.profileId)
            assertEquals(TransportesXadesEnvelopedAdapter.ID, request.protocolId)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.XADES, request.format)
            assertEquals(TransportesXadesEnvelopedAdapter.AUTH_PAGE_URL, request.context.pageUrl)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(CHALLENGE.encodeToByteArray(), data)
                    assertEquals(TransportesXadesEnvelopedAdapter.EXPECTED_EXTRA_PROPERTIES, properties)
                }
            }
        }
    }

    @Test
    fun rejectsEveryBroadenedTransportesDimension() {
        assertRejected(currentPageUrl = TransportesXadesEnvelopedAdapter.START_URL)
        assertRejected(currentPageUrl = "${TransportesXadesEnvelopedAdapter.AUTH_PAGE_URL}?x=1")
        assertRejected(origin = Uri.parse("https://sede.transportes.gob.es.evil.example"))
        assertRejected(isMainFrame = false)
        assertRejected(activeProfileId = ProfileId("junta-andalucia"))
        assertRejected(rawMessage = message(algorithm = "SHA256withRSA"))
        assertRejected(rawMessage = message(format = "XAdES Enveloping"))
        assertRejected(rawMessage = message(extraProperties = "format=XAdES Enveloped\n"))
        assertRejected(
            rawMessage = message(
                challenge = CHALLENGE.replace("17/08/2026", "31/02/2026"),
            ),
        )
    }

    private fun assertRejected(
        rawMessage: String = message(),
        origin: Uri = ORIGIN,
        isMainFrame: Boolean = true,
        activeProfileId: ProfileId = ProfileId(TransportesXadesEnvelopedAdapter.PROFILE_ID),
        currentPageUrl: String? = TransportesXadesEnvelopedAdapter.AUTH_PAGE_URL,
    ) {
        val candidate = MiniAppletBridgeAdapter(
            clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            activeProfileId = { activeProfileId },
        )
        assertTrue(
            candidate.route(
                rawMessage = rawMessage,
                sourceOrigin = origin,
                isMainFrame = isMainFrame,
                currentPageUrl = currentPageUrl,
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
    }

    private fun message(
        challenge: String = CHALLENGE,
        algorithm: String = "SHA1withRSA",
        format: String = "XAdES",
        extraProperties: String = TransportesXadesEnvelopedAdapter.EXPECTED_EXTRA_PROPERTIES,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", Base64.getEncoder().encodeToString(challenge.encodeToByteArray()))
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
        .toString()

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174520"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174521"
        val ORIGIN: Uri = Uri.parse("https://sede.transportes.gob.es")
        const val CHALLENGE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tag1 Id=\"tag1\"><tag1_timestamp>17/08/2026 17:52:13</tag1_timestamp></tag1>"
    }
}
