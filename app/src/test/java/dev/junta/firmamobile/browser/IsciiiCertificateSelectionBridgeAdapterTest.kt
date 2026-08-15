package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
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
class IsciiiCertificateSelectionBridgeAdapterTest {
    private val adapter = IsciiiCertificateSelectionBridgeAdapter(
        profileRegistry = BuiltInSiteProfiles.qaRegistry,
        activeProfileId = { ProfileId(PROFILE_ID) },
    )

    @Test
    fun exactPublicIsciiiSelectCertificateCallNormalizesWithoutNetworkExecution() {
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
        assertEquals("sede.isciii.gob.es", result.request.context.origin.host)
        assertEquals(DOCUMENT_ID, result.request.context.navigationId.value)
        assertEquals(17, result.request.context.navigationEpoch)
        assertEquals(SAFE_DESCRIPTION, result.request.safeDescription)
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
            currentPageUrl = "https://sede.isciii.gob.es/",
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.UNOBSERVED_CONTRACT, wrongPage.code)

        val wrongParameters = adapter.route(
            rawMessage = message("serverUrl=https://attacker.example/SignatureService"),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 17,
            currentPageUrl = START_URL,
        ) as CertificateSelectionBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.INVALID_REQUEST, wrongParameters.code)
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

    private fun message(properties: String): String = JSONObject()
        .put("type", "MINIAPPLET_SELECT_CERTIFICATE")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("extraProperties", properties)
        .toString()

    private companion object {
        const val PROFILE_ID = "isciii-certificate-selection"
        const val ORIGIN = "https://sede.isciii.gob.es"
        const val START_URL =
            "https://sede.isciii.gob.es/cargaApplet.jsp?accion=generico&recurso.opcion=null"
        const val DOCUMENT_ID = "11111111-1111-4111-8111-111111111111"
        const val REQUEST_ID = "22222222-2222-4222-8222-222222222222"
        const val SAFE_DESCRIPTION = "Compartir certificado con la Sede electrónica del ISCIII"
        const val EXTRA_PROPERTIES =
            "serverUrl=http://dtomcat7.isciiides.es:8080/afirma-server-triphase-signer/SignatureService"
    }
}
