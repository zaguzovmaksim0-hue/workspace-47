package dev.junta.firmamobile.certificate

import java.time.Duration

private const val SHORT_UNLOCK_MINUTES = 15L
private const val LONG_UNLOCK_HOURS = 24L

/** A per-unlock choice, never a sliding expiration or automatic renewal. */
enum class CertificateUnlockRetention(val duration: Duration) {
    FIFTEEN_MINUTES(Duration.ofMinutes(SHORT_UNLOCK_MINUTES)),
    ONE_HOUR(Duration.ofHours(1)),
    ONE_DAY(Duration.ofHours(LONG_UNLOCK_HOURS)),
}
