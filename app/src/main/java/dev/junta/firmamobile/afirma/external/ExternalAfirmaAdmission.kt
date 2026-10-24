package dev.junta.firmamobile.afirma.external

/** Only the action and data string of an incoming VIEW are read; extras, clip data and selectors never are. */
internal object ExternalAfirmaEnvelope {
    const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val SCHEME = "afirma:"
    private const val MAX_URI_CHARS = 1_048_576

    fun accept(action: String?, data: String?): String? = data?.takeIf {
        action == ACTION_VIEW && it.length <= MAX_URI_CHARS && it.regionMatches(0, SCHEME, 0, SCHEME.length, true)
    }
}

internal sealed interface ExternalAfirmaDelivery {
    data object None : ExternalAfirmaDelivery
    data object Stale : ExternalAfirmaDelivery
    class Fresh(val rawUri: String) : ExternalAfirmaDelivery {
        override fun toString(): String = "Fresh"
    }
}

/** One in-process slot between the exported entry and MainActivity. A delivery is taken once or goes stale,
 * so an old request never surfaces on a later ordinary app launch. */
internal class ExternalAfirmaInbox(
    private val ttlNanos: Long,
    private val nowNanos: () -> Long,
) {
    private var rawUri: String? = null
    private var receivedAt = 0L

    /** A second delivery never replaces a fresh pending one. */
    @Synchronized fun put(uri: String): Boolean {
        if (rawUri != null && fresh()) return false
        rawUri = uri
        receivedAt = nowNanos()
        return true
    }

    @Synchronized fun take(): ExternalAfirmaDelivery {
        val uri = rawUri ?: return ExternalAfirmaDelivery.None
        val delivery = if (fresh()) ExternalAfirmaDelivery.Fresh(uri) else ExternalAfirmaDelivery.Stale
        rawUri = null
        return delivery
    }

    @Synchronized fun hasPending(): Boolean = rawUri != null

    private fun fresh(): Boolean = (nowNanos() - receivedAt).let { it in 0 until ttlNanos }
}

/** Process-wide owner of the active signing operation. Portal screens and an external delivery exclude
 * each other; a busy portal provider that throws counts as busy. Main thread only. */
internal class SigningAdmission {
    private val portals = LinkedHashMap<Any, () -> Boolean>()
    private var externalOwner: Any? = null

    val externalActive: Boolean get() = externalOwner != null

    fun registerPortal(owner: Any, busy: () -> Boolean) { portals[owner] = busy }

    fun unregisterPortal(owner: Any, busy: () -> Boolean) {
        if (portals[owner] === busy) portals.remove(owner)
    }

    fun portalBusy(): Boolean = portals.values.any { runCatching(it).getOrDefault(true) }

    fun tryAcquireExternal(owner: Any): Boolean {
        if (externalOwner != null || portalBusy()) return false
        externalOwner = owner
        return true
    }

    fun releaseExternal(owner: Any) { if (externalOwner === owner) externalOwner = null }
}
