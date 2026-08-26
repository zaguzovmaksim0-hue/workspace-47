package dev.junta.firmamobile.smoke

import dev.junta.firmamobile.diagnostics.RuntimeDiagnosticEvent
import dev.junta.firmamobile.diagnostics.SigningDiagnosticState
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

internal enum class CatalogSmokeEventCode {
    WEBVIEW_ATTACHED,
    WEBVIEW_DESTROYED,
    NAVIGATION_STARTED,
    NAVIGATION_CHANGED,
    NAVIGATION_BLOCKED,
    BROWSER_ERROR,
    RENDER_PROCESS_GONE,
    CLIENT_CERT_REQUEST,
    CLIENT_CERT_ACCEPTED,
    CLIENT_AUTH_CONFIRMATION_REQUIRED,
    CERTIFICATE_SELECTION_REQUIRED,
    AFIRMA_REQUEST,
    AUTOFIRMA_INTENT,
    PORTAL_CALLBACK,
    SIGNING_AWAITING_CONFIRMATION,
    SIGNING_CONNECTING,
    SIGNING,
    SIGNING_COMPLETED,
    SIGNING_FAILED,
}

internal data class CatalogSmokeRuntimeEvent(
    val sequence: Long,
    val code: CatalogSmokeEventCode,
    val navigationEpoch: Long,
    val host: String? = null,
    val path: String? = null,
    val detail: String? = null,
)

internal data class CatalogSmokeRuntimeSnapshot(
    val runId: String,
    val profileId: ProfileId,
    val browserSessionBound: Boolean,
    val webViewActive: Boolean,
    val navigationEpoch: Long,
    val currentHost: String?,
    val currentPath: String?,
    val currentUrlAllowed: Boolean,
    val clientCertRequestObserved: Boolean,
    val clientCertAcceptedObserved: Boolean,
    val clientAuthConfirmationRequired: Boolean,
    val certificateSelectionRequired: Boolean,
    val afirmaRequestObserved: Boolean,
    val autofirmaIntentObserved: Boolean,
    val signingConfirmationRequired: Boolean,
    val signingStartedObserved: Boolean,
    val signingCompletedObserved: Boolean,
    val signingFailedObserved: Boolean,
    val portalCallbackObserved: Boolean,
    val renderProcessGone: Boolean,
    val failureCode: String?,
    val events: List<CatalogSmokeRuntimeEvent>,
)

/**
 * Bounded QA runtime journal. A run is bound to the next fresh BrowserScreen UUID, then every
 * event must carry the same UUID and profile. This prevents a delayed event from an old WebView
 * from proving a new run even when navigation epochs restart at zero for a recreated screen.
 */
internal class CatalogSmokeRuntime {
    private var lastObservedBrowserSessionId: UUID? = null
    private var active: ActiveRun? = null

    @Synchronized
    fun beginRun(runId: String, profileId: ProfileId) {
        active = ActiveRun(
            runId = runId,
            profileId = profileId,
            previousBrowserSessionId = lastObservedBrowserSessionId,
        )
    }

    @Synchronized
    fun snapshot(runId: String, profileId: ProfileId): CatalogSmokeRuntimeSnapshot? =
        active?.takeIf { it.runId == runId && it.profileId == profileId }?.snapshot()

    @Synchronized
    fun observe(event: RuntimeDiagnosticEvent) {
        if (event is RuntimeDiagnosticEvent.WebViewState && event.active) {
            lastObservedBrowserSessionId = event.browserSessionId
        }
        val run = active ?: return
        if (event.profileId != run.profileId) return

        if (run.browserSessionId == null) {
            if (event !is RuntimeDiagnosticEvent.WebViewState || !event.active) return
            if (event.browserSessionId == run.previousBrowserSessionId) return
            run.browserSessionId = event.browserSessionId
        }
        if (event.browserSessionId != run.browserSessionId) return
        if (event.navigationEpoch < run.navigationEpoch) return
        run.navigationEpoch = event.navigationEpoch

        when (event) {
            is RuntimeDiagnosticEvent.WebViewState -> {
                run.webViewActive = event.active
                run.add(if (event.active) CatalogSmokeEventCode.WEBVIEW_ATTACHED else CatalogSmokeEventCode.WEBVIEW_DESTROYED)
            }
            is RuntimeDiagnosticEvent.NavigationStarted -> {
                val safe = safeUrl(event.url, run.profileId)
                run.currentHost = safe.host
                run.currentPath = safe.path
                run.currentUrlAllowed = safe.allowed
                run.add(CatalogSmokeEventCode.NAVIGATION_STARTED, safe.host, safe.path)
            }
            is RuntimeDiagnosticEvent.NavigationChanged -> {
                val safe = safeUrl(event.url, run.profileId)
                run.currentHost = safe.host
                run.currentPath = safe.path
                run.currentUrlAllowed = safe.allowed
                run.add(CatalogSmokeEventCode.NAVIGATION_CHANGED, safe.host, safe.path)
            }
            is RuntimeDiagnosticEvent.NavigationBlocked -> {
                run.failureCode = event.reason.name
                run.add(CatalogSmokeEventCode.NAVIGATION_BLOCKED, detail = event.reason.name)
            }
            is RuntimeDiagnosticEvent.BrowserError -> {
                run.failureCode = event.error.name
                run.add(CatalogSmokeEventCode.BROWSER_ERROR, detail = event.error.name)
            }
            is RuntimeDiagnosticEvent.RenderProcessGone -> {
                run.renderProcessGone = true
                run.failureCode = "RENDER_PROCESS_GONE"
                run.add(CatalogSmokeEventCode.RENDER_PROCESS_GONE)
            }
            is RuntimeDiagnosticEvent.ClientCertRequestObserved -> {
                run.clientCertRequestObserved = true
                run.add(
                    CatalogSmokeEventCode.CLIENT_CERT_REQUEST,
                    host = safeHost(event.host),
                    detail = event.port.takeIf { it in 1..65_535 }?.toString(),
                )
            }
            is RuntimeDiagnosticEvent.ClientCertRequestAccepted -> {
                run.clientCertAcceptedObserved = true
                run.add(
                    CatalogSmokeEventCode.CLIENT_CERT_ACCEPTED,
                    host = safeHost(event.host),
                    detail = event.port.takeIf { it in 1..65_535 }?.toString(),
                )
            }
            is RuntimeDiagnosticEvent.ClientAuthConfirmationRequired -> {
                run.clientAuthConfirmationRequired = true
                run.add(
                    CatalogSmokeEventCode.CLIENT_AUTH_CONFIRMATION_REQUIRED,
                    host = safeHost(event.host),
                )
            }
            is RuntimeDiagnosticEvent.CertificateSelectionRequired -> {
                run.certificateSelectionRequired = true
                run.add(
                    CatalogSmokeEventCode.CERTIFICATE_SELECTION_REQUIRED,
                    host = safeHost(event.host),
                )
            }
            is RuntimeDiagnosticEvent.AfirmaRequestObserved -> {
                run.afirmaRequestObserved = true
                run.add(CatalogSmokeEventCode.AFIRMA_REQUEST, host = safeHost(event.host))
            }
            is RuntimeDiagnosticEvent.AutoFirmaIntentObserved -> {
                run.autofirmaIntentObserved = true
                run.add(CatalogSmokeEventCode.AUTOFIRMA_INTENT)
            }
            is RuntimeDiagnosticEvent.PortalCallbackObserved -> {
                run.portalCallbackObserved = true
                run.add(
                    CatalogSmokeEventCode.PORTAL_CALLBACK,
                    host = safeHost(event.host),
                    detail = safeToken(event.stage),
                )
            }
            is RuntimeDiagnosticEvent.SigningStateObserved -> when (event.state) {
                SigningDiagnosticState.IDLE -> Unit
                SigningDiagnosticState.AWAITING_CONFIRMATION -> {
                    run.signingConfirmationRequired = true
                    run.add(CatalogSmokeEventCode.SIGNING_AWAITING_CONFIRMATION)
                }
                SigningDiagnosticState.CONNECTING_SECURELY -> {
                    run.signingStartedObserved = true
                    run.add(CatalogSmokeEventCode.SIGNING_CONNECTING)
                }
                SigningDiagnosticState.SIGNING -> {
                    run.signingStartedObserved = true
                    run.add(CatalogSmokeEventCode.SIGNING)
                }
                SigningDiagnosticState.COMPLETED -> {
                    run.signingCompletedObserved = true
                    run.add(CatalogSmokeEventCode.SIGNING_COMPLETED)
                }
                SigningDiagnosticState.FAILED -> {
                    run.signingFailedObserved = true
                    run.failureCode = event.error?.name ?: "SIGNING_FAILED"
                    run.add(CatalogSmokeEventCode.SIGNING_FAILED, detail = event.error?.name)
                }
            }
        }
    }

    private fun safeUrl(raw: String, profileId: ProfileId): SafeUrl {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return SafeUrl(null, null, false)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return SafeUrl(null, null, false)
        }
        val host = safeHost(uri.host)
        val path = (uri.rawPath.takeUnless { it.isNullOrBlank() } ?: "/")
            .take(MAX_PATH_CHARS)
        val allowed = runCatching {
            val registry = BuiltInSiteProfiles.runtimeRegistry
            registry.resolveForProfile(profileId, uri)?.profile?.profileId == profileId ||
                registry.resolveRedirect(profileId, uri)?.profile?.profileId == profileId
        }.getOrDefault(false)
        return SafeUrl(host, path, allowed)
    }

    private fun safeHost(raw: String): String? = raw.lowercase(Locale.ROOT)
        .takeIf { it.length <= 253 && HOST.matches(it) }

    private fun safeToken(raw: String): String? = raw.takeIf { TOKEN.matches(it) }

    private data class SafeUrl(val host: String?, val path: String?, val allowed: Boolean)

    private class ActiveRun(
        val runId: String,
        val profileId: ProfileId,
        val previousBrowserSessionId: UUID?,
    ) {
        var browserSessionId: UUID? = null
        var webViewActive = false
        var navigationEpoch = 0L
        var currentHost: String? = null
        var currentPath: String? = null
        var currentUrlAllowed = false
        var clientCertRequestObserved = false
        var clientCertAcceptedObserved = false
        var clientAuthConfirmationRequired = false
        var certificateSelectionRequired = false
        var afirmaRequestObserved = false
        var autofirmaIntentObserved = false
        var signingConfirmationRequired = false
        var signingStartedObserved = false
        var signingCompletedObserved = false
        var signingFailedObserved = false
        var portalCallbackObserved = false
        var renderProcessGone = false
        var failureCode: String? = null
        private var sequence = 0L
        private val events = ArrayDeque<CatalogSmokeRuntimeEvent>(MAX_EVENTS)

        fun add(
            code: CatalogSmokeEventCode,
            host: String? = null,
            path: String? = null,
            detail: String? = null,
        ) {
            sequence++
            while (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(
                CatalogSmokeRuntimeEvent(
                    sequence = sequence,
                    code = code,
                    navigationEpoch = navigationEpoch,
                    host = host,
                    path = path,
                    detail = detail,
                ),
            )
        }

        fun snapshot() = CatalogSmokeRuntimeSnapshot(
            runId = runId,
            profileId = profileId,
            browserSessionBound = browserSessionId != null,
            webViewActive = webViewActive,
            navigationEpoch = navigationEpoch,
            currentHost = currentHost,
            currentPath = currentPath,
            currentUrlAllowed = currentUrlAllowed,
            clientCertRequestObserved = clientCertRequestObserved,
            clientCertAcceptedObserved = clientCertAcceptedObserved,
            clientAuthConfirmationRequired = clientAuthConfirmationRequired,
            certificateSelectionRequired = certificateSelectionRequired,
            afirmaRequestObserved = afirmaRequestObserved,
            autofirmaIntentObserved = autofirmaIntentObserved,
            signingConfirmationRequired = signingConfirmationRequired,
            signingStartedObserved = signingStartedObserved,
            signingCompletedObserved = signingCompletedObserved,
            signingFailedObserved = signingFailedObserved,
            portalCallbackObserved = portalCallbackObserved,
            renderProcessGone = renderProcessGone,
            failureCode = failureCode,
            events = events.toList(),
        )
    }

    private companion object {
        const val MAX_EVENTS = 64
        const val MAX_PATH_CHARS = 256
        val HOST = Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")
        val TOKEN = Regex("[A-Za-z0-9._+\\-]{1,64}")
    }
}
