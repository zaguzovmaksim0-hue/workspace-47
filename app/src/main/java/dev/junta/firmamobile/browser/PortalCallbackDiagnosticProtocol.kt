package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.TrustMode
import java.util.UUID
import org.json.JSONObject

enum class PortalCallbackStage {
    RESULT_RECEIVED,
    RESULT_IGNORED,
    CALLBACK_STARTED,
    CALLBACK_RETURNED,
    CALLBACK_THROWN,
}

sealed interface PortalCallbackDiagnosticParseResult {
    data object NotApplicable : PortalCallbackDiagnosticParseResult

    data class Accepted(val stage: PortalCallbackStage) : PortalCallbackDiagnosticParseResult

    data object Rejected : PortalCallbackDiagnosticParseResult
}

object PortalCallbackDiagnosticProtocol {
    fun parse(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        expectedProfileId: ProfileId,
        registry: SiteProfileRegistry,
        enabled: Boolean,
    ): PortalCallbackDiagnosticParseResult {
        if (!enabled || rawMessage.length > MAX_MESSAGE_CHARS) {
            return PortalCallbackDiagnosticParseResult.NotApplicable
        }
        val json = try {
            JSONObject(rawMessage)
        } catch (_: Exception) {
            return PortalCallbackDiagnosticParseResult.NotApplicable
        }
        if (json.optString(TYPE_FIELD) != MESSAGE_TYPE) {
            return PortalCallbackDiagnosticParseResult.NotApplicable
        }
        if (!isMainFrame || json.keys().asSequence().toSet() != REQUIRED_KEYS) {
            return PortalCallbackDiagnosticParseResult.Rejected
        }
        if (strictVersionFourUuid(json.optString(DOCUMENT_ID_FIELD)) == null ||
            strictVersionFourUuid(json.optString(REQUEST_ID_FIELD)) == null
        ) {
            return PortalCallbackDiagnosticParseResult.Rejected
        }
        val stage = runCatching {
            PortalCallbackStage.valueOf(json.optString(STAGE_FIELD))
        }.getOrNull() ?: return PortalCallbackDiagnosticParseResult.Rejected
        val resolution = registry.resolve(sourceOrigin)
            ?: return PortalCallbackDiagnosticParseResult.Rejected
        if (resolution.profile.profileId != expectedProfileId ||
            resolution.trustMode != TrustMode.TRUSTED_SIGNING
        ) {
            return PortalCallbackDiagnosticParseResult.Rejected
        }
        return PortalCallbackDiagnosticParseResult.Accepted(stage)
    }

    private fun strictVersionFourUuid(raw: String): UUID? {
        if (!CANONICAL_VERSION_FOUR_UUID.matches(raw)) return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    private const val MAX_MESSAGE_CHARS = 512
    private const val MESSAGE_TYPE = "QA_PORTAL_DIAGNOSTIC"
    private const val TYPE_FIELD = "type"
    private const val DOCUMENT_ID_FIELD = "documentId"
    private const val REQUEST_ID_FIELD = "requestId"
    private const val STAGE_FIELD = "stage"
    private val REQUIRED_KEYS = setOf(
        TYPE_FIELD,
        DOCUMENT_ID_FIELD,
        REQUEST_ID_FIELD,
        STAGE_FIELD,
    )
    private val CANONICAL_VERSION_FOUR_UUID = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
    )
}
