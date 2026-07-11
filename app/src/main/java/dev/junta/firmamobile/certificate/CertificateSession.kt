package dev.junta.firmamobile.certificate

import java.time.Clock
import java.time.Duration
import java.time.Instant

sealed interface CertificateSessionState {
    data object Empty : CertificateSessionState

    data class Locked(val summary: CertificateSummary) : CertificateSessionState

    data class Unlocked(
        val summary: CertificateSummary,
        val expiresAt: Instant,
    ) : CertificateSessionState
}

class CertificateSession(
    private val clock: Clock = Clock.systemUTC(),
    private val unlockDuration: Duration = Duration.ofMinutes(10),
) {
    private var unlockedIdentity: UnlockedIdentity? = null
    private var lastSummary: CertificateSummary? = null
    private var expiresAt: Instant? = null

    init {
        require(!unlockDuration.isNegative && !unlockDuration.isZero)
    }

    @Synchronized
    fun unlock(identity: UnlockedIdentity) {
        unlockedIdentity = identity
        lastSummary = identity.summary
        expiresAt = clock.instant().plus(unlockDuration)
    }

    @Synchronized
    fun lock() {
        unlockedIdentity = null
        expiresAt = null
    }

    @Synchronized
    fun forget() {
        unlockedIdentity = null
        lastSummary = null
        expiresAt = null
    }

    @Synchronized
    fun onAppBackgrounded() = lock()

    @Synchronized
    fun onMemoryPressure() = lock()

    @Synchronized
    fun state(): CertificateSessionState {
        expireIfNeeded()
        val identity = unlockedIdentity
        val expiry = expiresAt
        return when {
            identity != null && expiry != null -> CertificateSessionState.Unlocked(
                identity.summary,
                expiry,
            )
            lastSummary != null -> CertificateSessionState.Locked(checkNotNull(lastSummary))
            else -> CertificateSessionState.Empty
        }
    }

    @Synchronized
    internal fun identityForSigning(): UnlockedIdentity? {
        expireIfNeeded()
        return unlockedIdentity
    }

    private fun expireIfNeeded() {
        val expiry = expiresAt ?: return
        if (clock.instant().isAfter(expiry) || clock.instant() == expiry) {
            lock()
        }
    }
}
