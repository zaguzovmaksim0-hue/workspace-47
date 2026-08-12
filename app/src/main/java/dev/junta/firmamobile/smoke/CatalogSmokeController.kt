package dev.junta.firmamobile.smoke

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.profile.ProfileId

internal enum class CatalogSmokeOperation { OPEN, INSPECT }

internal enum class CatalogSmokeResultCode {
    INVALID_REQUEST,
    UNKNOWN_PORTAL,
    CATALOG_ONLY,
    PROFILE_DISABLED,
    PROFILE_RESOLVED,
    OPEN_REQUESTED,
    WEBVIEW_ACTIVE,
    WEBVIEW_NOT_ACTIVE,
}

internal data class CatalogSmokeRequest(
    val runId: String?,
    val portalId: String?,
    val operation: String?,
)

internal data class CatalogSmokeOutcome(
    val runId: String?,
    val portalId: PortalId?,
    val profileId: ProfileId?,
    val adapterId: String?,
    val entryUrl: String?,
    val supportStatus: String?,
    val result: CatalogSmokeResultCode,
)

internal class CatalogSmokeController(
    private val repository: PortalCatalogRepository,
    private val certificateUnlocked: () -> Boolean,
    private val openProfile: (PortalLaunchTarget) -> Unit,
    private val activeWebViewMatches: (ProfileId) -> Boolean,
    private val adapterIdForProfile: (ProfileId) -> String?,
) {
    fun execute(request: CatalogSmokeRequest): CatalogSmokeOutcome {
        val runId = request.runId?.takeIf(RUN_ID::matches)
            ?: return invalid(request.runId)
        val portalId = request.portalId?.let { raw -> runCatching { PortalId(raw) }.getOrNull() }
            ?: return invalid(runId)
        val operation = request.operation
            ?.uppercase()
            ?.let { raw -> runCatching { CatalogSmokeOperation.valueOf(raw) }.getOrNull() }
            ?: return invalid(runId, portalId)
        val portal = repository.portals().singleOrNull { it.portalId == portalId }
            ?: return CatalogSmokeOutcome(
                runId = runId,
                portalId = portalId,
                profileId = null,
                adapterId = null,
                entryUrl = null,
                supportStatus = null,
                result = CatalogSmokeResultCode.UNKNOWN_PORTAL,
            )
        val base = CatalogSmokeOutcome(
            runId = runId,
            portalId = portal.portalId,
            profileId = portal.profileId,
            adapterId = portal.profileId?.let(adapterIdForProfile),
            entryUrl = portal.entryUrl.toASCIIString(),
            supportStatus = portal.supportStatus.name,
            result = CatalogSmokeResultCode.CATALOG_ONLY,
        )
        val profileId = portal.profileId ?: return base

        if (operation == CatalogSmokeOperation.INSPECT) {
            return base.copy(
                result = if (activeWebViewMatches(profileId)) {
                    CatalogSmokeResultCode.WEBVIEW_ACTIVE
                } else {
                    CatalogSmokeResultCode.WEBVIEW_NOT_ACTIVE
                },
            )
        }

        val target = repository.resolveLaunch(portal)
            ?: return base.copy(result = CatalogSmokeResultCode.PROFILE_DISABLED)
        if (!certificateUnlocked()) {
            return base.copy(result = CatalogSmokeResultCode.PROFILE_RESOLVED)
        }
        openProfile(target)
        return base.copy(result = CatalogSmokeResultCode.OPEN_REQUESTED)
    }

    private fun invalid(runId: String?, portalId: PortalId? = null) = CatalogSmokeOutcome(
        runId = runId?.takeIf(RUN_ID::matches),
        portalId = portalId,
        profileId = null,
        adapterId = null,
        entryUrl = null,
        supportStatus = null,
        result = CatalogSmokeResultCode.INVALID_REQUEST,
    )

    private companion object {
        val RUN_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}
