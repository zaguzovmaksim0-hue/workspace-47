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
}

/**
 * Coordinates one optional IFEF warm-up per browser session. It fails open:
 * timeout or a changed navigation epoch loads the original target directly.
 */
class IfefSessionWarmupController(
    private val scheduleTimeout: (() -> Unit) -> Unit,
    private val loadTarget: (String) -> Unit,
) {
    private var pendingTarget: String? = null
    private var pendingEpoch: Long = -1L

    var warmed: Boolean = false
        private set

    fun startIfEligible(targetUrl: String, navigationEpoch: Long): String? {
        val warmupUrl = IfefSessionWarmup.WARMUP_URL
        val isEligible = !warmed &&
            pendingTarget == null &&
            IfefSessionWarmup.shouldWarmUp(targetUrl, alreadyWarmed = warmed)

        if (isEligible) {
            pendingTarget = targetUrl
            pendingEpoch = navigationEpoch
            scheduleTimeout { onTimeout(navigationEpoch) }
            loadTarget(warmupUrl)
        }
        return warmupUrl.takeIf { isEligible }
    }

    fun onPageFinished(finishedUrl: String, navigationEpoch: Long): String? {
        val target = pendingTarget
        val isWarmupCompletion = target != null &&
            navigationEpoch == pendingEpoch &&
            finishedUrl == IfefSessionWarmup.WARMUP_URL

        if (isWarmupCompletion) {
            warmed = true
            pendingTarget = null
            loadTarget(target)
        }
        return target.takeIf { isWarmupCompletion }
    }

    fun onNavigationStarted(startedUrl: String, navigationEpoch: Long) {
        val uri = runCatching { URI(startedUrl) }.getOrNull()
        val isSameHttpsHost = uri != null &&
            uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(IfefSessionWarmup.IFEF_HOST, ignoreCase = true)

        if (pendingTarget != null &&
            (navigationEpoch != pendingEpoch || !isSameHttpsHost)
        ) {
            clear()
        }
    }

    private fun onTimeout(navigationEpoch: Long) {
        val target = pendingTarget
        if (target != null && navigationEpoch == pendingEpoch) {
            warmed = true
            pendingTarget = null
            loadTarget(target)
        }
    }

    private fun clear() {
        pendingTarget = null
        pendingEpoch = -1L
    }
}
