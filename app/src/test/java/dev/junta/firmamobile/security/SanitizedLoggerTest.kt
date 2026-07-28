package dev.junta.firmamobile.security

import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.afirma.AfirmaParameter
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.network.ProfileHttpFailure
import dev.junta.firmamobile.network.ProfileHttpFailurePhase
import dev.junta.firmamobile.network.ProfileHttpRoute
import dev.junta.firmamobile.network.TunnelRouteDurationBucket
import dev.junta.firmamobile.network.TunnelRouteEvent
import dev.junta.firmamobile.network.TunnelRouteStage
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizedLoggerTest {
    private val fixedClockText = "2030-01-01T00:00:00Z"
    private val clock = Clock.fixed(
        Instant.parse("2030-01-01T00:00:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun protocolRecordContainsOnlySafeMetadataLengthsAndShortHashes() {
        val logger = SanitizedLogger(clock = clock)
        val secretCanary = "document-secret-canary"
        val request = AfirmaRequest(
            rawUri = "afirma://sign?dat=$secretCanary&algorithm=SHA256withRSA&format=CAdES",
            origin = TrustedOrigin("https", "www.juntadeandalucia.es", 443),
            operation = AfirmaOperation.SIGN,
            parameters = mapOf(
                "algorithm" to listOf(AfirmaParameter("SHA256withRSA", "SHA256withRSA")),
                "format" to listOf(AfirmaParameter("CAdES", "CAdES")),
                "dat" to listOf(AfirmaParameter(secretCanary, secretCanary)),
            ),
        )

        logger.recordAfirmaRequest(request)
        val exported = logger.exportText()

        assertTrue(exported.contains("event=AFIRMA_REQUEST_OBSERVED"))
        assertTrue(exported.contains("origin=www.juntadeandalucia.es"))
        assertTrue(exported.contains("operation=sign"))
        assertTrue(exported.contains("algorithm=SHA256withRSA"))
        assertTrue(exported.contains("format=CAdES"))
        assertTrue(exported.contains("parameter.dat.0.length=${secretCanary.length}"))
        assertTrue(Regex("parameter\\.dat\\.0\\.sha256_8=[0-9a-f]{8}").containsMatchIn(exported))
        assertFalse(exported.contains(secretCanary))
        assertFalse(exported.contains("afirma://"))
        assertFalse(exported.contains("dat="))
    }

    @Test
    fun browserEventsSanitizeHostAndRingBufferIsBounded() {
        val logger = SanitizedLogger(clock = clock, capacity = 2)

        logger.recordBrowserEvent(DiagnosticEventCode.EXTERNAL_NAVIGATION, "one.example")
        logger.recordBrowserEvent(
            DiagnosticEventCode.PLAY_STORE_FALLBACK_INTERCEPTED,
            "play.google.com\nsecret=canary",
        )
        logger.recordBrowserEvent(DiagnosticEventCode.SSL_ERROR_CANCELLED, "three.example")

        val records = logger.snapshot()
        assertEquals(2, records.size)
        assertFalse(records.joinToString().contains("one.example"))
        assertFalse(records.joinToString().contains("secret=canary"))
        assertTrue(records.first().contains("host=invalid"))
        assertTrue(records.last().contains("event=SSL_ERROR_CANCELLED"))
    }

    @Test
    fun tunnelRecordContainsOnlyClosedTokensAndCoarseDuration() {
        val logger = SanitizedLogger(clock = clock)
        val uuid = "123e4567-e89b-42d3-a456-426614174000"
        val token = "qa-secret-token-canary"
        val relay = "relay.private.example"
        logger.recordTunnelRouteEvent(
            TunnelRouteEvent(
                route = ProfileHttpRoute.SECURE_TUNNEL,
                stage = TunnelRouteStage.TUNNEL_FAILED,
                phase = ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES,
                resultCode = ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE,
                durationBucket = TunnelRouteDurationBucket.ONE_TO_THREE_SECONDS,
            ),
        )

        val exported = logger.exportText()
        assertTrue(exported.contains("event=TUNNEL_ROUTE"))
        assertTrue(exported.contains("route=SECURE_TUNNEL"))
        assertTrue(exported.contains("stage=TUNNEL_FAILED"))
        assertTrue(exported.contains("phase=TCP_BEFORE_HTTP_BYTES"))
        assertTrue(exported.contains("result=TUNNEL_CONNECT_UNAVAILABLE"))
        assertTrue(exported.contains("duration_bucket=ONE_TO_THREE_SECONDS"))
        for (forbidden in listOf(
            uuid, token, relay, "sha256_8", ".length=", "bytes=", "size=",
            "Authorization", "Bearer", "Exception", "https://", fixedClockText,
        )) {
            assertFalse("forbidden=$forbidden record=$exported", exported.contains(forbidden))
        }
        assertEquals(1, logger.snapshot().size)
    }

    @Test
    fun clearRemovesAllInMemoryDiagnostics() {
        val logger = SanitizedLogger(clock = clock)
        logger.recordBrowserEvent(DiagnosticEventCode.NETWORK_ERROR, "www.juntadeandalucia.es")

        logger.clear()

        assertTrue(logger.snapshot().isEmpty())
        assertEquals("", logger.exportText())
    }
}
