package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.AemetCertificateLoginCadesAdapter
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class AemetMiniAppletBridgeTest {
    @Test
    fun normalizesOnlyAemetAutoNullToLocalDetachedCades() {
        for (nonce in listOf("0", "1", "9999", "10000")) {
            val accepted = route(message(nonce)) as MiniAppletBridgeRouteResult.Accepted
            accepted.request.normalized.use { request ->
                assertEquals(AemetCertificateLoginCadesAdapter.ID, request.protocolId)
                assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
                assertEquals("CADES", request.format.name)
                assertEquals(PAGE, request.context.pageUrl)
                request.withPayload { payload ->
                    MiniAppletPayloadCodec.withDecoded(payload) { bytes, properties ->
                        assertArrayEquals(nonce.encodeToByteArray(), bytes)
                        assertEquals("", properties)
                    }
                }
            }
        }
    }

    @Test
    fun rejectsUnreviewedWireTuplesAndNoncanonicalNonces() {
        for (nonce in listOf("", "00", "01", "10001", "123456", "-1", "+1", "1.0", " 1", "1\n",
            "%PDF-1.7", "<xml/>", "https://example.org/")) {
            assertRejected(message(nonce))
        }
        for ((key, value) in listOf(
            "dataB64" to "MQ", "dataB64" to "MR==", "algorithm" to "SHA256withRSA",
            "format" to "CAdES", "format" to "auto", "extraProperties" to "", "extraProperties" to "null",
        )) assertRejected(message("1").put(key, value))
        assertRejected(message("1").apply { remove("extraProperties") })
        assertRejected(message("1").put("extra", true))
    }

    @Test
    fun rejectsForeignSourcesSubframesInactiveProfilesAndNeighboringPages() {
        for (page in listOf(null, "$PAGE/", "$PAGE?x=1", "$PAGE#x", "$ORIGIN/AEMET/es/other")) {
            assertTrue(route(message("1"), page = page) is MiniAppletBridgeRouteResult.Rejected)
        }
        assertTrue(route(message("1"), origin = "https://foreign.example") is MiniAppletBridgeRouteResult.Rejected)
        assertTrue(route(message("1"), mainFrame = false) is MiniAppletBridgeRouteResult.Rejected)
        assertTrue(route(message("1"), active = null) is MiniAppletBridgeRouteResult.Rejected)
        assertTrue(route(message("1"), active = ProfileId("canarias-sede")) is MiniAppletBridgeRouteResult.Rejected)
    }

    private fun assertRejected(message: JSONObject) {
        assertTrue(message.toString(), route(message) is MiniAppletBridgeRouteResult.Rejected)
    }

    @Test
    fun localCallbackCannotBeReplayedOrDeliveredAfterNavigation() {
        for (change in listOf("none", "page", "epoch", "origin")) {
            val request = (route(message("1")) as MiniAppletBridgeRouteResult.Accepted).request.normalized
            request.use {
                var epoch = request.context.navigationEpoch
                var page: String? = PAGE
                var origin = request.context.origin
                val replies = mutableListOf<String>()
                val registry = MiniAppletReplyRegistry(
                    currentNavigationEpoch = { epoch },
                    currentOrigin = { origin },
                    currentPageUrl = { page },
                )
                val channel = checkNotNull(registry.create(request.requestId, request.context, replies::add))
                when (change) {
                    "page" -> page = "$PAGE?other=1"
                    "epoch" -> epoch++
                    "origin" -> origin = origin.copy(host = "foreign.example")
                }
                if (change == "none") {
                    assertTrue(channel.failure(SigningErrorCode.USER_CANCELLED))
                    assertEquals(1, replies.size)
                } else {
                    assertFalse(channel.failure(SigningErrorCode.USER_CANCELLED))
                    assertTrue(replies.isEmpty())
                }
                assertNull(registry.create(request.requestId, request.context, replies::add))
            }
        }
    }

    @Test
    fun releaseRegistryDoesNotExposeTheLogin() {
        val result = ProfileMiniAppletBridgeAdapter(
            profileRegistry = BuiltInSiteProfiles.releaseRegistry,
            activeProfileId = { ProfileId(AemetCertificateLoginCadesAdapter.PROFILE_ID) },
        ).route(message("1").toString(), Uri.parse(ORIGIN), true, 9, PAGE)
        assertTrue(result is MiniAppletBridgeRouteResult.Rejected)
    }

    private fun route(
        message: JSONObject,
        page: String? = PAGE,
        origin: String = ORIGIN,
        mainFrame: Boolean = true,
        active: ProfileId? = ProfileId(AemetCertificateLoginCadesAdapter.PROFILE_ID),
        epoch: Long = 9,
    ) = MiniAppletBridgeAdapter(activeProfileId = { active }).route(
        rawMessage = message.toString(),
        sourceOrigin = Uri.parse(origin),
        isMainFrame = mainFrame,
        navigationEpoch = epoch,
        currentPageUrl = page,
    )

    private fun message(nonce: String): JSONObject = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", "123e4567-e89b-42d3-a456-426614174001")
        .put("requestId", "123e4567-e89b-42d3-a456-426614174000")
        .put("dataB64", Base64.getEncoder().encodeToString(nonce.encodeToByteArray()))
        .put("algorithm", "SHA1withRSA")
        .put("format", "AUTO")
        .put("extraProperties", JSONObject.NULL)

    private companion object {
        const val ORIGIN = AemetCertificateLoginCadesAdapter.INITIATOR_ORIGIN
        const val PAGE = AemetCertificateLoginCadesAdapter.SIGNING_PAGE_URL
    }
}
