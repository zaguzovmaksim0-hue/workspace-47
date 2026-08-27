package dev.junta.firmamobile.smoke

import dev.junta.firmamobile.catalog.PortalCatalogItem
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.profile.ProfileId

internal enum class CatalogSmokeOperation { OPEN, INSPECT, ACTIVATE }

internal enum class CatalogSmokeResultCode {
    INVALID_REQUEST,
    UNKNOWN_PORTAL,
    UNKNOWN_PROFILE,
    AMBIGUOUS_PROFILE,
    CATALOG_ONLY,
    PROFILE_DISABLED,
    PROFILE_RESOLVED,
    OPEN_REQUESTED,
    WEBVIEW_ACTIVE,
    WEBVIEW_NOT_ACTIVE,
    PUBLIC_ENTRY_ACTIVATION_REQUESTED,
    PUBLIC_ENTRY_ACTIVATION_UNAVAILABLE,
    RUN_NOT_ACTIVE,
}

internal data class CatalogSmokeRequest(
    val runId: String?,
    val portalId: String? = null,
    val operation: String?,
    val profileId: String? = null,
)

internal data class CatalogSmokeOutcome(
    val runId: String?,
    val portalId: PortalId?,
    val profileId: ProfileId?,
    val adapterId: String?,
    val entryUrl: String?,
    val supportStatus: String?,
    val result: CatalogSmokeResultCode,
    val runtime: CatalogSmokeRuntimeSnapshot? = null,
)

internal class CatalogSmokeController(
    private val repository: PortalCatalogRepository,
    private val certificateUnlocked: () -> Boolean,
    private val openProfile: (PortalLaunchTarget) -> Unit,
    private val activeWebViewMatches: (ProfileId) -> Boolean,
    private val activatePublicEntry: (ProfileId) -> Boolean,
    private val adapterIdForProfile: (ProfileId) -> String?,
    private val runtime: CatalogSmokeRuntime,
) {
    fun execute(request: CatalogSmokeRequest): CatalogSmokeOutcome {
        val runId = request.runId?.takeIf(RUN_ID::matches)
            ?: return invalid(request.runId)
        val operation = request.operation
            ?.uppercase()
            ?.let { raw -> runCatching { CatalogSmokeOperation.valueOf(raw) }.getOrNull() }
            ?: return invalid(runId)
        val selected = resolve(request, runId) ?: return invalid(runId)
        if (selected is Resolution.Failure) return selected.outcome
        val portal = (selected as Resolution.Success).portal
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

        if (operation == CatalogSmokeOperation.INSPECT || operation == CatalogSmokeOperation.ACTIVATE) {
            val snapshot = runtime.snapshot(runId, profileId)
                ?: return base.copy(result = CatalogSmokeResultCode.RUN_NOT_ACTIVE)
            // activeWebViewMatches re-validates the live WebView URL against the exact profile.
            // currentUrlAllowed is diagnostic evidence from the event journal, not a second authority.
            val active = activeWebViewMatches(profileId) &&
                snapshot.browserSessionBound && snapshot.webViewActive
            if (!active) {
                return base.copy(
                    result = CatalogSmokeResultCode.WEBVIEW_NOT_ACTIVE,
                    runtime = snapshot,
                )
            }
            if (operation == CatalogSmokeOperation.ACTIVATE) {
                return base.copy(
                    result = if (activatePublicEntry(profileId)) {
                        CatalogSmokeResultCode.PUBLIC_ENTRY_ACTIVATION_REQUESTED
                    } else {
                        CatalogSmokeResultCode.PUBLIC_ENTRY_ACTIVATION_UNAVAILABLE
                    },
                    runtime = snapshot,
                )
            }
            return base.copy(result = CatalogSmokeResultCode.WEBVIEW_ACTIVE, runtime = snapshot)
        }

        val target = repository.resolveLaunch(portal)
            ?: return base.copy(result = CatalogSmokeResultCode.PROFILE_DISABLED)
        if (!certificateUnlocked()) {
            return base.copy(result = CatalogSmokeResultCode.PROFILE_RESOLVED)
        }
        runtime.beginRun(runId, profileId)
        openProfile(target)
        return base.copy(result = CatalogSmokeResultCode.OPEN_REQUESTED)
    }

    private fun resolve(request: CatalogSmokeRequest, runId: String): Resolution? {
        val hasPortalId = !request.portalId.isNullOrBlank()
        val hasProfileId = !request.profileId.isNullOrBlank()
        if (hasPortalId == hasProfileId) return null

        return if (hasPortalId) {
            val portalId = runCatching { PortalId(requireNotNull(request.portalId)) }.getOrNull()
                ?: return null
            val portal = repository.portals().singleOrNull { it.portalId == portalId }
                ?: return Resolution.Failure(
                    CatalogSmokeOutcome(
                        runId = runId,
                        portalId = portalId,
                        profileId = null,
                        adapterId = null,
                        entryUrl = null,
                        supportStatus = null,
                        result = CatalogSmokeResultCode.UNKNOWN_PORTAL,
                    ),
                )
            Resolution.Success(portal)
        } else {
            val profileId = runCatching { ProfileId(requireNotNull(request.profileId)) }.getOrNull()
                ?: return null
            val matches = repository.portals().filter { it.profileId == profileId }
            when (matches.size) {
                0 -> Resolution.Failure(
                    CatalogSmokeOutcome(
                        runId = runId,
                        portalId = null,
                        profileId = profileId,
                        adapterId = null,
                        entryUrl = null,
                        supportStatus = null,
                        result = CatalogSmokeResultCode.UNKNOWN_PROFILE,
                    ),
                )
                1 -> Resolution.Success(matches.single())
                else -> Resolution.Failure(
                    CatalogSmokeOutcome(
                        runId = runId,
                        portalId = null,
                        profileId = profileId,
                        adapterId = null,
                        entryUrl = null,
                        supportStatus = null,
                        result = CatalogSmokeResultCode.AMBIGUOUS_PROFILE,
                    ),
                )
            }
        }
    }

    private fun invalid(runId: String?) = CatalogSmokeOutcome(
        runId = runId?.takeIf(RUN_ID::matches),
        portalId = null,
        profileId = null,
        adapterId = null,
        entryUrl = null,
        supportStatus = null,
        result = CatalogSmokeResultCode.INVALID_REQUEST,
    )

    private sealed interface Resolution {
        data class Success(val portal: PortalCatalogItem) : Resolution
        data class Failure(val outcome: CatalogSmokeOutcome) : Resolution
    }

    private companion object {
        val RUN_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}
