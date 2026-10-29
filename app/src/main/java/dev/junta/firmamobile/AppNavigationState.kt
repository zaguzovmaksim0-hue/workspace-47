package dev.junta.firmamobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object AppNavigationRoutes {
    const val CERTIFICATE = "certificate"
    const val CATALOG = "catalog"
    const val SESSION_ARG = "sessionId"
    const val BROWSER = "browser/{$SESSION_ARG}"

    fun browser(sessionId: String): String = "browser/$sessionId"
}

internal sealed interface AppDestination {
    data object Certificate : AppDestination

    data object Catalog : AppDestination

    data class Browser(
        val profileId: ProfileId?,
        val entryUrl: URI,
    ) : AppDestination
}

internal data class AppBrowserSession(
    val profileId: ProfileId?,
    val entryUrl: URI,
)

/**
 * Activity-host-owned in-memory registry for opaque Browser route ids. It never stores or restores
 * URIs, credentials, requests or WebView state: after host loss every id becomes unresolvable.
 */
internal class AppBrowserSessionRegistry {
    private val sessions = ConcurrentHashMap<String, AppBrowserSession>()

    fun create(profileId: ProfileId?, entryUrl: URI): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = AppBrowserSession(profileId, entryUrl)
        return sessionId
    }

    fun resolve(sessionId: String): AppBrowserSession? =
        sessions[sessionId]?.let(::withResolvedProfile)

    private fun withResolvedProfile(session: AppBrowserSession): AppBrowserSession? =
        when (session.profileId) {
            null -> session
            else -> session.profileId?.let(::resolveProfileOrNull)?.let { session }
        }

    private fun resolveProfileOrNull(profileId: ProfileId) =
        runCatching { BuiltInSiteProfiles.runtimeRegistry.profile(profileId) }.getOrNull()

    fun invalidate(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun clearAll() {
        sessions.clear()
    }
}

/**
 * Root destination and certificate overlay of one Activity host. Deliberately not a retained ViewModel:
 * a recreated host must start on Certificate with the panel closed, never restore a browser.
 * Callers run their side effects (signing cancellation, WebView release) before the command.
 */
internal class AppNavigationState(
    private val browserSessions: AppBrowserSessionRegistry = AppBrowserSessionRegistry(),
) {
    var destination by mutableStateOf<AppDestination>(AppDestination.Certificate)
        private set
    var certificatePanelVisible by mutableStateOf(false)
        private set
    var activeBrowserSessionId by mutableStateOf<String?>(null)
        private set

    fun showCertificate() {
        activeBrowserSessionId = null
        destination = AppDestination.Certificate
    }

    fun showCatalog() {
        activeBrowserSessionId = null
        destination = AppDestination.Catalog
    }

    fun openBrowser(profileId: ProfileId?, entryUrl: URI): String {
        val sessionId = browserSessions.create(profileId, entryUrl)
        activeBrowserSessionId = sessionId
        destination = AppDestination.Browser(profileId, entryUrl)
        return sessionId
    }

    fun resolveBrowserSession(sessionId: String): AppBrowserSession? =
        browserSessions.resolve(sessionId)

    fun invalidateBrowserSession(sessionId: String) {
        browserSessions.invalidate(sessionId)
    }

    fun clearBrowserSessions() {
        browserSessions.clearAll()
    }

    fun showCertificatePanel() {
        certificatePanelVisible = true
    }

    fun hideCertificatePanel() {
        certificatePanelVisible = false
    }
}
