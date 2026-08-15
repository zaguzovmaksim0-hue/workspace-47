package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import java.util.UUID
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
class ValenciaCertificateSelectionBridgeAdapterTest {
    private val adapter = ValenciaCertificateSelectionBridgeAdapter(
        profileRegistry = BuiltInSiteProfiles.qaRegistry,
        activeProfileId = { ProfileId(PROFILE_ID) },
    )

    @Test
    fun exactPublicValenciaSelectCertificateCallNormalizesWithoutNetworkExecution() {
        val result = adapter.route(
            rawMessage = message(EXTRA_PROPERTIES),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 17,
            currentPageUrl = START_URL,
        ) as CertificateSelectionBridgeRouteResult.Accepted

        assertEquals(UUID.fromString(REQUEST_ID), result.request.requestId)
        assertEquals(PROFILE_ID, result.request.context.profileId)
        assertEquals(1, result.request.context.profileVersion)
        assertEquals("portafirmas.dival.es", result.request.context.origin.host)
        assertEquals(DOCUMENT_ID, result.request.context.navigationId.value)
        assertEquals(17, result.request.context.navigationEpoch)
        assertEquals(SAFE_DESCRIPTION, result.request.safeDescription)
        assertEquals(START_URL, result.request.pageUrl)
        assertEquals(EXTRA_PROPERTIES, result.request.extraProperties)
    }

    @Test
    fun wrongOriginPageOrParametersFailClosed() {
        val wrongOrigin = adapter.route(
            rawMessage = message(EXTRA_PROPERTIES),
            sourceOrigin = Uri.parse("https://evil.example"),
            isMainFrame = true,
            navigationEpoch = 17,
            currentPageUrl = START_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, wrongOrigin.code)

        val wrongPage = adapter.route(
            rawMessage = message(EXTRA_PROPERTIES),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 17,
            currentPageUrl = "https://portafirmas.dival.es/",
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.UNOBSERVED_CONTRACT, wrongPage.code)

        val wrongParameters = adapter.route(
            rawMessage = message("filters=none"),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 17,
            currentPageUrl = START_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.INVALID_REQUEST, wrongParameters.code)

        val inactiveAdapter = ValenciaCertificateSelectionBridgeAdapter(
            profileRegistry = BuiltInSiteProfiles.qaRegistry,
            activeProfileId = { ProfileId("other-profile") },
        )
        val profileNotActive = inactiveAdapter.route(
            rawMessage = message(EXTRA_PROPERTIES),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 17,
            currentPageUrl = START_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.PROFILE_NOT_ACTIVE, profileNotActive.code)
    }

    @Test
    fun certificateReplyIsOneShotContainsNoSignatureAndClearsOwnedDer() {
        val messages = mutableListOf<String>()
        val channel = CertificateSelectionReplyChannel(
            requestId = UUID.fromString(REQUEST_ID),
            postMessage = messages::add,
        )
        val certificateDer = byteArrayOf(1, 2, 3, 4, 5)

        assertTrue(channel.success(certificateDer))
        assertTrue(certificateDer.all { it == 0.toByte() })
        assertFalse(channel.failure(SigningErrorCode.PROTOCOL_FAILED))
        assertEquals(1, messages.size)
        val result = JSONObject(messages.single())
        assertEquals("MINIAPPLET_SELECT_CERTIFICATE_RESULT", result.getString("type"))
        assertEquals("success", result.getString("status"))
        assertEquals("AQIDBAU=", result.getString("certificate"))
        assertFalse(result.has("signature"))
        assertFalse(result.has("filters"))
    }

    @Test
    fun subframesAndMalformedMessagesAreRejected() {
        val subframe = adapter.route(
            rawMessage = message(EXTRA_PROPERTIES),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = false,
            navigationEpoch = 17,
            currentPageUrl = START_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.NAVIGATION_CHANGED, subframe.code)

        val duplicateOrExtraField = JSONObject(message(EXTRA_PROPERTIES))
            .put("extra", true)
            .toString()
        val malformed = adapter.route(
            rawMessage = duplicateOrExtraField,
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 17,
            currentPageUrl = START_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.INVALID_REQUEST, malformed.code)
    }

    @Test
    fun cancelMessageRoutesToCancelled() {
        val cancelJson = JSONObject()
            .put("type", "MINIAPPLET_SELECT_CERTIFICATE_CANCEL")
            .put("documentId", DOCUMENT_ID)
            .put("requestId", REQUEST_ID)
            .toString()
        val result = adapter.route(
            rawMessage = cancelJson,
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 17,
            currentPageUrl = START_URL,
        ) as CertificateSelectionBridgeRouteResult.Cancelled

        assertEquals(UUID.fromString(REQUEST_ID), result.requestId)
        assertEquals(DOCUMENT_ID, result.navigationId.value)
    }

    private fun message(properties: String): String = JSONObject()
        .put("type", "MINIAPPLET_SELECT_CERTIFICATE")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("extraProperties", properties)
        .toString()

    private companion object {
        const val PROFILE_ID = "diputacion-valencia-sede"
        const val ORIGIN = "https://portafirmas.dival.es"
        const val START_URL = "https://portafirmas.dival.es/signingpad/xhtml/login.xhtml"
        const val DOCUMENT_ID = "11111111-1111-4111-8111-111111111111"
        const val REQUEST_ID = "22222222-2222-4222-8222-222222222222"
        const val SAFE_DESCRIPTION =
            "Compartir certificado con el Portafirmas de la Diputació de València"
        const val EXTRA_PROPERTIES =
            "filters=keyusage.nonrepudiation:true;nonexpired:true\nheadless=true"
    }
}
