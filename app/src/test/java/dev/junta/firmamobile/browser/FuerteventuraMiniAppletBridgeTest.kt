package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
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
class FuerteventuraMiniAppletBridgeTest {
    @Test
    fun acceptsTheExactAuthenticatedFuerteventuraPadesTuple() {
        val pdf = "%PDF-1.4\n% synthetic Fuerteventura request\n".encodeToByteArray()
        val result = MiniAppletBridgeAdapter(
            clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            activeProfileId = { ProfileId(PROFILE_ID) },
        ).route(
            rawMessage = message(pdf),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9,
            currentPageUrl = SIGNING_PAGE_URL,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(PROTOCOL_ID, request.protocolId.value)
            assertEquals(PROFILE_ID, request.context.profileId)
            assertEquals(SigningAlgorithm.SHA256_WITH_RSA, request.algorithm)
            assertEquals("PADES", request.format.name)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(pdf, data)
                    assertEquals(EXTRA_PROPERTIES, properties)
                }
            }
        }
        pdf.fill(0)
    }

    @Test
    fun rejectsWrongPageAndAnyNonExactPadesTuple() {
        val pdf = "%PDF-1.4\n% synthetic Fuerteventura request\n".encodeToByteArray()
        val adapter = MiniAppletBridgeAdapter(
            activeProfileId = { ProfileId(PROFILE_ID) },
        )
        fun route(raw: String, page: String = SIGNING_PAGE_URL) = adapter.route(
            rawMessage = raw,
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9,
            currentPageUrl = page,
        )
        assertEquals(true, route(message(pdf), "$ORIGIN/other") is MiniAppletBridgeRouteResult.Rejected)
        assertEquals(true, route(message(pdf).replace("SHA256withRSA", "SHA512withRSA")) is MiniAppletBridgeRouteResult.Rejected)
        assertEquals(true, route(message(pdf).replace("PAdES", "CAdES")) is MiniAppletBridgeRouteResult.Rejected)
        assertEquals(true, route(message(pdf).replace("obfuscateCertText= true", "obfuscateCertText= false")) is MiniAppletBridgeRouteResult.Rejected)
        pdf.fill(0)
    }

    private fun message(pdf: ByteArray): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", "123e4567-e89b-42d3-a456-426614174001")
        .put("requestId", "123e4567-e89b-42d3-a456-426614174000")
        .put("dataB64", Base64.getEncoder().encodeToString(pdf))
        .put("algorithm", "SHA256withRSA")
        .put("format", "PAdES")
        .put("extraProperties", EXTRA_PROPERTIES)
        .toString()

    private companion object {
        const val PROFILE_ID = "fuerteventura-sede-electronica"
        const val PROTOCOL_ID = "fuerteventura-register-pades-v1"
        const val ORIGIN = "https://sede.cabildofuer.es"
        const val SIGNING_PAGE_URL = "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=verYfirmar&modo=cert"
        val EXTRA_PROPERTIES = listOf(
            "signaturePositionOnPageLowerLeftX = 50",
            "signaturePositionOnPageLowerLeftY = 15",
            "signaturePositionOnPageUpperRightX = 150",
            "signaturePositionOnPageUpperRightY = 50",
            "signaturePages = all",
            "layer2Text= Firmado por \$\$SUBJECTCN\$\$ el día \$\$SIGNDATE=dd/MM/yyyy\$\$ \$\$ORGANIZATION\$\$",
            "layer2FontSize= 6",
            "layer2FontFamily= 0",
            "layer2FontStyle= 0",
            "signatureRotation= 0",
            "includeQuestionMark= false",
            "obfuscateCertText= true",
        ).joinToString(separator = "\n", postfix = "\n")
    }
}
