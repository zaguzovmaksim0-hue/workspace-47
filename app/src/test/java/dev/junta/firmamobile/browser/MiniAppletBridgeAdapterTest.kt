package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.SensitiveSignatureCopyObserver
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
class MiniAppletBridgeAdapterTest {
    private val adapter = MiniAppletBridgeAdapter(
        clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun exactTrustedJuntaMiniAppletCallNormalizesToOneOwnedRequest() {
        val result = adapter.route(
            rawMessage = message(),
            sourceOrigin = TRUSTED_ORIGIN,
            isMainFrame = true,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(REQUEST_ID, request.requestId.toString())
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            assertEquals("www.juntadeandalucia.es", request.context.origin.host)
            assertEquals(DOCUMENT_ID, request.context.navigationId.value)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    assertArrayEquals(DATA, data)
                    assertEquals(EXTRA_PROPERTIES, extraProperties)
                }
            }
        }
    }

    @Test
    fun exactRedSaraAutoScriptCallNormalizesOnlyTheObservedXadesTuple() {
        val result = adapter.route(
            rawMessage = JSONObject()
                .put("type", "MINIAPPLET_SIGN")
                .put("documentId", DOCUMENT_ID)
                .put("requestId", REQUEST_ID)
                .put("dataB64", Base64.getEncoder().encodeToString("<r/>".encodeToByteArray()))
                .put("algorithm", "SHA512withRSA")
                .put("format", "XAdES Detached")
                .put("extraProperties", JSONObject.NULL)
                .toString(),
            sourceOrigin = Uri.parse("https://reg.redsara.es"),
            isMainFrame = true,
            navigationEpoch = 9,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals("reg-age-redsara", request.context.profileId)
            assertEquals("reg.redsara.es", request.context.origin.host)
            assertEquals(9, request.context.navigationEpoch)
            assertEquals(SigningAlgorithm.SHA512_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.XADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals("<r/>".encodeToByteArray(), data)
                    assertEquals("", properties)
                }
            }
        }
    }

    @Test
    fun redSaraLookalikeIframeWrongTupleAndPropertiesFailClosed() {
        val valid = JSONObject()
            .put("type", "MINIAPPLET_SIGN")
            .put("documentId", DOCUMENT_ID)
            .put("requestId", REQUEST_ID)
            .put("dataB64", Base64.getEncoder().encodeToString("<r/>".encodeToByteArray()))
            .put("algorithm", "SHA512withRSA")
            .put("format", "XAdES Detached")
            .put("extraProperties", JSONObject.NULL)
            .toString()
        assertRejected(valid, Uri.parse("https://reg.redsara.es.evil.example"), true)
        assertRejected(valid, Uri.parse("https://reg.redsara.es"), false)
        assertRejected(valid.replace("SHA512withRSA", "SHA256withRSA"), Uri.parse("https://reg.redsara.es"), true)
        assertRejected(valid.replace("XAdES Detached", "CAdES"), Uri.parse("https://reg.redsara.es"), true)
        assertRejected(valid.replace("null", "\"mode=explicit\""), Uri.parse("https://reg.redsara.es"), true)
    }

    @Test
    fun unknownOriginSubframeWrongAlgorithmFormatDuplicateAndOversizeFailClosed() {
        assertRejected(message(), Uri.parse("https://evil.example"), true)
        assertRejected(message(), TRUSTED_ORIGIN, false)
        assertRejected(message().replace("SHA1withRSA", "MD5withRSA"), TRUSTED_ORIGIN, true)
        assertRejected(message().replace("CAdES", "XAdES"), TRUSTED_ORIGIN, true)
        assertRejected(message().replace("mode=explicit", "mode=implicit"), TRUSTED_ORIGIN, true)
        assertRejected(message().replace("mode=explicit", "mode=explicit\nnote=unexpected"), TRUSTED_ORIGIN, true)
        assertRejected(
            message().replace(
                "\"requestId\":\"$REQUEST_ID\"",
                "\"requestId\":\"$REQUEST_ID\",\"requestId\":\"$REQUEST_ID\"",
            ),
            TRUSTED_ORIGIN,
            true,
        )
        assertRejected(
            message(dataB64 = "A".repeat(MiniAppletBridgeAdapter.MAX_DATA_BASE64_CHARS + 1)),
            TRUSTED_ORIGIN,
            true,
        )
    }

    @Test
    fun successDeliveryPostsSignatureThenCertificateOnceAndClearsOwnedBytes() {
        val posted = mutableListOf<String>()
        val signatureClears = mutableListOf<Boolean>()
        val signature = LocalSignature(
            bytes = SIGNATURE.copyOf(),
            observer = SensitiveSignatureCopyObserver(signatureClears::add),
        )
        val certificate = CERTIFICATE.copyOf()
        val channel = MiniAppletReplyChannel(
            requestId = java.util.UUID.fromString(REQUEST_ID),
            postMessage = posted::add,
        )

        assertTrue(channel.success(signature, certificate))
        assertFalse(channel.success(LocalSignature(byteArrayOf(9)), byteArrayOf(8)))
        assertFalse(channel.failure(SigningErrorCode.PROTOCOL_FAILED))

        val json = JSONObject(posted.single())
        assertEquals("MINIAPPLET_RESULT", json.getString("type"))
        assertEquals("success", json.getString("status"))
        assertEquals(Base64.getEncoder().encodeToString(SIGNATURE), json.getString("signature"))
        assertEquals(
            Base64.getEncoder().encodeToString(CERTIFICATE),
            json.getString("certificate"),
        )
        assertTrue(certificate.all { it == 0.toByte() })
        assertEquals(listOf(true), signatureClears)
    }

    @Test
    fun closedErrorDeliveryIsOneShotAndContainsNoThrowableOrPayload() {
        val posted = mutableListOf<String>()
        val channel = MiniAppletReplyChannel(
            requestId = java.util.UUID.fromString(REQUEST_ID),
            postMessage = posted::add,
        )

        assertTrue(channel.failure(SigningErrorCode.USER_CANCELLED))
        assertFalse(channel.failure(SigningErrorCode.PROTOCOL_FAILED))

        val json = JSONObject(posted.single())
        assertEquals("error", json.getString("status"))
        assertEquals("USER_CANCELLED", json.getString("errorCode"))
        assertFalse(json.has("signature"))
        assertFalse(json.has("certificate"))
        assertFalse(posted.single().contains("Exception"))
    }

    @Test
    fun trustedCancelMessageReleasesTheExactRequestOnly() {
        val result = adapter.route(
            rawMessage = JSONObject()
                .put("type", "MINIAPPLET_CANCEL")
                .put("documentId", DOCUMENT_ID)
                .put("requestId", REQUEST_ID)
                .toString(),
            sourceOrigin = TRUSTED_ORIGIN,
            isMainFrame = true,
        ) as MiniAppletBridgeRouteResult.Cancelled

        assertEquals(REQUEST_ID, result.requestId.toString())
        assertEquals(DOCUMENT_ID, result.navigationId.value)
    }

    @Test
    fun oversizedNonMiniAppletMessageRemainsAvailableToTheExistingUriRouter() {
        val existingProtocolMessage = JSONObject()
            .put("type", "AFIRMA_URI")
            .put("requestId", REQUEST_ID)
            .put("uri", "afirma://sign?dat=" + "A".repeat(MiniAppletBridgeAdapter.MAX_MESSAGE_CHARS))
            .toString()

        assertTrue(
            adapter.route(existingProtocolMessage, TRUSTED_ORIGIN, true) is
                MiniAppletBridgeRouteResult.NotApplicable,
        )
    }

    private fun assertRejected(rawMessage: String, origin: Uri, isMainFrame: Boolean) {
        assertTrue(
            adapter.route(rawMessage, origin, isMainFrame) is
                MiniAppletBridgeRouteResult.Rejected,
        )
    }

    private fun message(
        dataB64: String = Base64.getEncoder().encodeToString(DATA),
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", "SHA1withRSA")
        .put("format", "CAdES")
        .put("extraProperties", EXTRA_PROPERTIES)
        .toString()

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174001"
        const val EXTRA_PROPERTIES =
            "serverUrl=https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/" +
                "sign/TriPhaseSignatureService\nmode=explicit"
        val TRUSTED_ORIGIN: Uri = Uri.parse("https://www.juntadeandalucia.es")
        val DATA = "synthetic-miniapplet-data".encodeToByteArray()
        val SIGNATURE = byteArrayOf(1, 2, 3, 4)
        val CERTIFICATE = byteArrayOf(5, 6, 7, 8)
    }
}
