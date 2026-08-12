package dev.junta.firmamobile.certificate

import dev.junta.firmamobile.security.MonotonicSecurityTime
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

internal data class CertificateUnlockLease(
    val expiresAt: Instant,
    internal val observedAtMonotonicNanos: Long,
    internal val lifetimeNanos: Long,
) {
    init {
        require(lifetimeNanos > 0L)
    }

    internal fun isExpiredOrInvalid(nowNanos: Long): Boolean =
        MonotonicSecurityTime.isExpiredOrInvalid(
            observedAtMonotonicNanos,
            lifetimeNanos,
            nowNanos,
        )

    internal fun remaining(nowNanos: Long): Duration = MonotonicSecurityTime.remaining(
        observedAtMonotonicNanos,
        lifetimeNanos,
        nowNanos,
    )
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
    private val unlockDuration: Duration = Duration.ofHours(24),
    private val fingerprintObserver: SensitiveCertificateFingerprintObserver =
        SensitiveCertificateFingerprintObserver {},
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) {
    private var unlockedIdentity: UnlockedIdentity? = null
    private var lastSummary: CertificateSummary? = null
    private var unlockLease: CertificateUnlockLease? = null

    init {
        require(!unlockDuration.isNegative && !unlockDuration.isZero)
        MonotonicSecurityTime.durationNanos(unlockDuration)
    }

    @Synchronized
    fun unlock(identity: UnlockedIdentity) {
        val now = clock.instant()
        val lease = createUnlockLease(
            expiresAt = now.plus(unlockDuration),
            lifetime = unlockDuration,
            now = now,
        )
        unlock(identity, lease, now)
    }

    @Synchronized
    fun unlock(identity: UnlockedIdentity, expiresAt: Instant) {
        val now = clock.instant()
        val lifetime = runCatching { Duration.between(now, expiresAt) }
            .getOrElse { throw IllegalArgumentException("Invalid certificate unlock expiry", it) }
        val lease = createUnlockLease(expiresAt, lifetime, now)
        unlock(identity, lease, now)
    }

    @Synchronized
    internal fun createUnlockLease(
        expiresAt: Instant,
        lifetime: Duration,
    ): CertificateUnlockLease = createUnlockLease(expiresAt, lifetime, clock.instant())

    private fun createUnlockLease(
        expiresAt: Instant,
        lifetime: Duration,
        now: Instant,
    ): CertificateUnlockLease {
        require(expiresAt.isAfter(now))
        require(!expiresAt.isAfter(now.plus(unlockDuration)))
        require(!lifetime.isNegative && !lifetime.isZero && lifetime <= unlockDuration)
        return CertificateUnlockLease(
            expiresAt = expiresAt,
            observedAtMonotonicNanos = monotonicNanos(),
            lifetimeNanos = MonotonicSecurityTime.durationNanos(lifetime),
        )
    }

    @Synchronized
    internal fun unlock(identity: UnlockedIdentity, lease: CertificateUnlockLease) {
        unlock(identity, lease, clock.instant())
    }

    private fun unlock(
        identity: UnlockedIdentity,
        lease: CertificateUnlockLease,
        now: Instant,
    ) {
        require(lease.expiresAt.isAfter(now))
        require(lease.lifetimeNanos <= MonotonicSecurityTime.durationNanos(unlockDuration))
        val currentMonotonic = monotonicNanos()
        require(!lease.isExpiredOrInvalid(currentMonotonic))
        unlockedIdentity = identity
        lastSummary = identity.summary
        unlockLease = lease
    }

    @Synchronized
    fun lock() {
        unlockedIdentity = null
        unlockLease = null
    }

    @Synchronized
    fun forget() {
        unlockedIdentity = null
        lastSummary = null
        unlockLease = null
    }

    @Synchronized
    fun onAppBackgrounded() {
        expireIfNeeded()
    }

    @Synchronized
    fun onMemoryPressure() = lock()

    @Synchronized
    fun state(): CertificateSessionState {
        expireIfNeeded()
        val identity = unlockedIdentity
        val lease = unlockLease
        return when {
            identity != null && lease != null -> CertificateSessionState.Unlocked(
                identity.summary,
                lease.expiresAt,
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
        val lease = unlockLease ?: return
        val civilNow = clock.instant()
        val monotonicNow = runCatching(monotonicNanos).getOrNull()
        if (!civilNow.isBefore(lease.expiresAt) ||
            monotonicNow == null ||
            lease.isExpiredOrInvalid(monotonicNow)
        ) {
            lock()
        }
    }

    private companion object {
        const val SHA_256 = "SHA-256"
    }
}
