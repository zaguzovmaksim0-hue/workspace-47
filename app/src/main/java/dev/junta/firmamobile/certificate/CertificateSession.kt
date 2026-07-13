package dev.junta.firmamobile.certificate

import java.io.Closeable
import java.security.MessageDigest
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

internal fun interface SensitiveCertificateFingerprintObserver {
    fun onCleared(allZero: Boolean)
}

internal class CertificateSigningSnapshot(
    val summary: CertificateSummary,
    fingerprint: ByteArray,
    private val observer: SensitiveCertificateFingerprintObserver =
        SensitiveCertificateFingerprintObserver {},
) : Closeable {
    private var ownedFingerprint: ByteArray? = fingerprint

    @Synchronized
    internal fun <T> withFingerprint(block: (ByteArray) -> T): T =
        block(checkNotNull(ownedFingerprint) { "Certificate snapshot is closed" })

    @Synchronized
    override fun close() {
        val fingerprint = ownedFingerprint ?: return
        fingerprint.fill(0)
        observer.onCleared(fingerprint.all { it == 0.toByte() })
        ownedFingerprint = null
    }
}

class CertificateSession internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val unlockDuration: Duration = Duration.ofMinutes(10),
    private val fingerprintObserver: SensitiveCertificateFingerprintObserver =
        SensitiveCertificateFingerprintObserver {},
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

    @Synchronized
    internal fun signingSnapshot(): CertificateSigningSnapshot? {
        expireIfNeeded()
        val identity = unlockedIdentity ?: return null
        val encoded = try {
            identity.certificate.encoded
        } catch (_: Exception) {
            return null
        }
        val fingerprint = try {
            MessageDigest.getInstance(SHA_256).digest(encoded)
        } finally {
            encoded.fill(0)
        }
        return CertificateSigningSnapshot(
            summary = identity.summary,
            fingerprint = fingerprint,
            observer = fingerprintObserver,
        )
    }

    @Synchronized
    internal fun identityForSigning(
        expected: CertificateSigningSnapshot,
    ): UnlockedIdentity? {
        expireIfNeeded()
        val identity = unlockedIdentity ?: return null
        val encoded = try {
            identity.certificate.encoded
        } catch (_: Exception) {
            return null
        }
        val currentFingerprint = try {
            MessageDigest.getInstance(SHA_256).digest(encoded)
        } finally {
            encoded.fill(0)
        }
        return try {
            val matches = try {
                expected.withFingerprint { expectedFingerprint ->
                    MessageDigest.isEqual(expectedFingerprint, currentFingerprint)
                }
            } catch (_: IllegalStateException) {
                false
            }
            identity.takeIf { matches }
        } finally {
            currentFingerprint.fill(0)
        }
    }

    private fun expireIfNeeded() {
        val expiry = expiresAt ?: return
        if (clock.instant().isAfter(expiry) || clock.instant() == expiry) {
            lock()
        }
    }

    private companion object {
        const val SHA_256 = "SHA-256"
    }
}
