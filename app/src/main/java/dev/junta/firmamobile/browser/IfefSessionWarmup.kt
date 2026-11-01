package dev.junta.firmamobile.browser

import java.net.URI

/**
 * Decides whether a cold IFEF deep link needs an entity-session warm-up.
 * The policy is deliberately narrow: all other URLs retain current behavior.
 */
object IfefSessionWarmup {
    const val IFEF_HOST = "sedeelectronica.ifef.es"
    const val WARMUP_URL = "https://sedeelectronica.ifef.es/"

    private const val PORTAL_PREFIX = "/portalCiudadano/"
    private const val ENTIDADES_PATH = "/portalCiudadano/portal/entidades.do"

    fun shouldWarmUp(targetUrl: String, alreadyWarmed: Boolean): Boolean {
        val uri = runCatching { URI(targetUrl) }.getOrNull()
        val hasEntityContext = uri?.rawQuery
            ?.split('&')
            ?.any { parameter ->
                parameter.substringBefore('=').equals("ent_id", ignoreCase = true)
            } == true

        return !alreadyWarmed &&
            uri != null &&
            uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(IFEF_HOST, ignoreCase = true) &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.path?.startsWith(PORTAL_PREFIX) == true &&
            uri.path != PORTAL_PREFIX &&
            uri.path != ENTIDADES_PATH &&
            !hasEntityContext
    }

    internal fun isWarmupChainUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(IFEF_HOST, ignoreCase = true) &&
            (uri.path.isNullOrEmpty() || uri.path == "/" || uri.path == ENTIDADES_PATH)
    }
}

/**
 * Coordinates one optional IFEF warm-up per browser session.
 *
 * The controller owns every navigation performed for the warm-up flow. The
 * root page may redirect to `entidades.do`; both starts advance the browser
 * epoch, so completion is identified by the pending warm-up state and the
 * resulting IFEF URL rather than by one fixed epoch. Timeout fails open by
 * loading the original target once, unless the warm-up was cancelled first.
 */
class IfefSessionWarmupController(
    private val scheduleTimeout: (() -> Unit) -> Unit,
    private val loadTarget: (String) -> Unit,
) {
    private var pendingTarget: String? = null

    var warmed: Boolean = false
        private set

    @Suppress("UnusedParameter")
    fun startIfEligible(targetUrl: String, navigationEpoch: Long): Boolean {
        val isEligible = !warmed &&
            pendingTarget == null &&
            IfefSessionWarmup.shouldWarmUp(targetUrl, alreadyWarmed = warmed)

        if (isEligible) {
            pendingTarget = targetUrl
            scheduleTimeout { onTimeout() }
            loadTarget(IfefSessionWarmup.WARMUP_URL)
        }
        return isEligible
    }

    @Suppress("UnusedParameter")
    fun onNavigationStarted(startedUrl: String, navigationEpoch: Long) {
        if (pendingTarget != null &&
            !IfefSessionWarmup.isWarmupChainUrl(startedUrl)
        ) {
            clear()
        }
    }

    @Suppress("UnusedParameter")
    fun onPageFinished(finishedUrl: String, navigationEpoch: Long) {
        val target = pendingTarget ?: return
        if (!IfefSessionWarmup.isWarmupChainUrl(finishedUrl)) return

        warmed = true
        clear()
        loadTarget(target)
    }

    private fun onTimeout() {
        val target = pendingTarget ?: return
        warmed = true
        clear()
        loadTarget(target)
    }

    private fun clear() {
        pendingTarget = null
    }
}
