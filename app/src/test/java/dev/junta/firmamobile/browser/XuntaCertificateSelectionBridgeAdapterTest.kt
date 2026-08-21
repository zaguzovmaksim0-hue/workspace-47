package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.XuntaPadesTriPhaseAdapter
import java.util.UUID
import org.json.JSONObject
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
class XuntaCertificateSelectionBridgeAdapterTest {
    private val adapter = XuntaCertificateSelectionBridgeAdapter(
        profileRegistry = BuiltInSiteProfiles.qaRegistry,
        activeProfileId = { ProfileId(XuntaPadesTriPhaseAdapter.PROFILE_ID) },
    )

    @Test
    fun exactObservedSelectionNormalizesOnExactAuthenticatedPr004aPage() {
        val accepted = adapter.route(
            message("filters=nonexpired"),
            Uri.parse(XuntaPadesTriPhaseAdapter.INITIATOR_ORIGIN),
            true,
            17,
            XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL,
        ) as CertificateSelectionBridgeRouteResult.Accepted
        assertEquals(UUID.fromString(REQUEST_ID), accepted.request.requestId)
        assertEquals(XuntaPadesTriPhaseAdapter.PROFILE_ID, accepted.request.context.profileId)
        assertEquals("filters=nonexpired", accepted.request.extraProperties)
        assertEquals(XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL, accepted.request.pageUrl)
    }

    @Test
    fun wrongPageOriginOrPropertiesFailClosed() {
        val wrongPage = adapter.route(
            message("filters=nonexpired"), Uri.parse(XuntaPadesTriPhaseAdapter.INITIATOR_ORIGIN), true, 17,
            XuntaPadesTriPhaseAdapter.PUBLIC_START_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.UNOBSERVED_CONTRACT, wrongPage.code)
        val wrongOrigin = adapter.route(
            message("filters=nonexpired"), Uri.parse("https://evil.example"), true, 17,
            XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, wrongOrigin.code)
        val wrongProperties = adapter.route(
            message("filters=nonexpired;foo=bar"), Uri.parse(XuntaPadesTriPhaseAdapter.INITIATOR_ORIGIN), true, 17,
            XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.INVALID_REQUEST, wrongProperties.code)
    }

    private fun message(properties: String): String = JSONObject()
        .put("type", "MINIAPPLET_SELECT_CERTIFICATE")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("extraProperties", properties)
        .toString()

    private companion object {
        const val DOCUMENT_ID = "11111111-1111-4111-8111-111111111111"
        const val REQUEST_ID = "22222222-2222-4222-8222-222222222222"
    }
}
