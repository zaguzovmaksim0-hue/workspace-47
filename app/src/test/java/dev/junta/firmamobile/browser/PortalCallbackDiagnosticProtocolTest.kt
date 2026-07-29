package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
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
class PortalCallbackDiagnosticProtocolTest {
    private val profileId = ProfileId("junta-ofvirtual")

    @Test
    fun acceptsOnlyClosedQaCallbackStagesFromTheActiveMainFrameOrigin() {
        val result = PortalCallbackDiagnosticProtocol.parse(
            rawMessage = message("CALLBACK_STARTED"),
            sourceOrigin = Uri.parse("https://ws072.juntadeandalucia.es"),
            isMainFrame = true,
            expectedProfileId = profileId,
            registry = BuiltInSiteProfiles.qaRegistry,
            enabled = true,
        )

        assertEquals(
            PortalCallbackDiagnosticParseResult.Accepted(PortalCallbackStage.CALLBACK_STARTED),
            result,
        )
    }

    @Test
    fun rejectsSubframesForeignOriginsUnknownStagesAndExtraFields() {
        val foreign = PortalCallbackDiagnosticProtocol.parse(
            message("CALLBACK_RETURNED"),
            Uri.parse("https://example.org"),
            true,
            profileId,
            BuiltInSiteProfiles.qaRegistry,
            true,
        )
        val subframe = PortalCallbackDiagnosticProtocol.parse(
            message("CALLBACK_RETURNED"),
            Uri.parse("https://ws072.juntadeandalucia.es"),
            false,
            profileId,
            BuiltInSiteProfiles.qaRegistry,
            true,
        )
        val unknown = PortalCallbackDiagnosticProtocol.parse(
            message("CERTIFICATE_CANARY"),
            Uri.parse("https://ws072.juntadeandalucia.es"),
            true,
            profileId,
            BuiltInSiteProfiles.qaRegistry,
            true,
        )
        val extra = PortalCallbackDiagnosticProtocol.parse(
            message("CALLBACK_THROWN").dropLast(1) + ",\"certificate\":\"secret-canary\"}",
            Uri.parse("https://ws072.juntadeandalucia.es"),
            true,
            profileId,
            BuiltInSiteProfiles.qaRegistry,
            true,
        )

        listOf(foreign, subframe, unknown, extra).forEach {
            assertTrue(it is PortalCallbackDiagnosticParseResult.Rejected)
        }
    }

    @Test
    fun disabledOrDifferentMessageTypeIsNotApplicable() {
        val disabled = PortalCallbackDiagnosticProtocol.parse(
            message("RESULT_RECEIVED"),
            Uri.parse("https://ws072.juntadeandalucia.es"),
            true,
            profileId,
            BuiltInSiteProfiles.qaRegistry,
            false,
        )
        val other = PortalCallbackDiagnosticProtocol.parse(
            """{"type":"MINIAPPLET_CANCEL"}""",
            Uri.parse("https://ws072.juntadeandalucia.es"),
            true,
            profileId,
            BuiltInSiteProfiles.qaRegistry,
            true,
        )

        assertTrue(disabled is PortalCallbackDiagnosticParseResult.NotApplicable)
        assertTrue(other is PortalCallbackDiagnosticParseResult.NotApplicable)
    }

    private fun message(stage: String) =
        """{"type":"QA_PORTAL_DIAGNOSTIC","documentId":"123e4567-e89b-42d3-a456-426614174000","requestId":"123e4567-e89b-42d3-a456-426614174001","stage":"$stage"}"""
}
