package dev.junta.firmamobile.security

import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.afirma.AfirmaParameter
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizedLoggerTest {
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
    fun clearRemovesAllInMemoryDiagnostics() {
        val logger = SanitizedLogger(clock = clock)
        logger.recordBrowserEvent(DiagnosticEventCode.NETWORK_ERROR, "www.juntadeandalucia.es")

        logger.clear()

        assertTrue(logger.snapshot().isEmpty())
        assertEquals("", logger.exportText())
    }
}
