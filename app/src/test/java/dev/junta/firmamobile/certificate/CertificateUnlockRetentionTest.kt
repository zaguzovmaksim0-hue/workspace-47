package dev.junta.firmamobile.certificate

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateUnlockRetentionTest {
    @Test
    fun retentionChoicesHaveExactlyTheAdvertisedDurations() {
        assertEquals(Duration.ofMinutes(15), CertificateUnlockRetention.FIFTEEN_MINUTES.duration)
        assertEquals(Duration.ofHours(1), CertificateUnlockRetention.ONE_HOUR.duration)
        assertEquals(Duration.ofHours(24), CertificateUnlockRetention.ONE_DAY.duration)
        assertEquals(3, CertificateUnlockRetention.entries.size)
    }
}
