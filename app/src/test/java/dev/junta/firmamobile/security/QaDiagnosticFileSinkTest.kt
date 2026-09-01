package dev.junta.firmamobile.security

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaDiagnosticFileSinkTest {
    private val clock = Clock.fixed(
        Instant.parse("2030-01-01T00:00:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun persistsOnlySanitizedNavigationCallbackAndBridgeMetadata() {
        val directory = Files.createTempDirectory("jfm-qa-diagnostic").toFile()
        val file = directory.resolve("qa-navigation.log")
        val logger = SanitizedLogger(
            clock = clock,
            sink = QaDiagnosticFileSink(file),
        )
        val secret = "certificate-signature-secret-canary"

        logger.recordPortalCallback("CALLBACK_RETURNED", "ws072.juntadeandalucia.es")
        logger.recordMiniAppletBridge(
            stage = "REJECTED",
            originHost = "sede.dip-badajoz.es",
            algorithm = "SHA256_WITH_RSA",
            format = "CADES",
            errorCode = "UNSUPPORTED_PROTOCOL",
        )
        logger.recordNavigationEvent(
            code = DiagnosticEventCode.NETWORK_REQUEST,
            rawUrl = "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs?firmaB64=$secret#fragment",
            isMainFrame = true,
            method = "POST",
        )

        val persisted = file.readText()
        assertTrue(persisted.contains("event=PORTAL_CALLBACK"))
        assertTrue(persisted.contains("event=MINIAPPLET_BRIDGE"))
        assertTrue(persisted.contains("stage=REJECTED"))
        assertTrue(persisted.contains("error=UNSUPPORTED_PROTOCOL"))
        assertTrue(persisted.contains("stage=CALLBACK_RETURNED"))
        assertTrue(persisted.contains("event=NETWORK_REQUEST"))
        assertTrue(persisted.contains("host=ws072.juntadeandalucia.es"))
        assertTrue(persisted.contains("method=POST"))
        for (forbidden in listOf(secret, "firmaB64", "fragment", "?", "certificate", "signature")) {
            assertFalse("forbidden=$forbidden persisted=$persisted", persisted.contains(forbidden))
        }
    }

    @Test
    fun resetsStaleRunAndRemainsBoundedWithCompleteLatestRecord() {
        val directory = Files.createTempDirectory("jfm-qa-diagnostic-bounded").toFile()
        val file = directory.resolve("qa-navigation.log")
        file.writeText("stale-secret-canary\n")
        val sink = QaDiagnosticFileSink(file = file, maxBytes = 256)

        repeat(20) { index ->
            sink.emit("timestamp=2030-01-01T00:00:00Z event=PAGE_FINISHED host=ws072.juntadeandalucia.es marker=$index")
        }

        val persisted = file.readText()
        assertFalse(persisted.contains("stale-secret-canary"))
        assertTrue(file.length() <= 256)
        assertTrue(persisted.endsWith("marker=19\n"))
        assertTrue(persisted.lineSequence().filter(String::isNotBlank).all { line ->
            line.startsWith("timestamp=") && '\r' !in line && '\n' !in line
        })
    }

    @Test
    fun rejectsNonBrowserDiagnosticsEvenWhenTheyAreAlreadySanitized() {
        val directory = Files.createTempDirectory("jfm-qa-diagnostic-allowlist").toFile()
        val file = directory.resolve("qa-navigation.log")
        val sink = QaDiagnosticFileSink(file = file, maxBytes = 512)

        sink.emit(
            "timestamp=2030-01-01T00:00:00Z event=AFIRMA_REQUEST_OBSERVED " +
                "parameter.dat.0.length=123 parameter.dat.0.sha256_8=deadbeef",
        )
        sink.emit("event=TUNNEL_ROUTE route=DIRECT stage=DIRECT_SUCCEEDED")

        assertEquals("", file.readText())
    }

    @Test
    fun rejectsMultilineOrOversizedRecordsWithoutChangingFile() {
        val directory = Files.createTempDirectory("jfm-qa-diagnostic-reject").toFile()
        val file = directory.resolve("qa-navigation.log")
        val sink = QaDiagnosticFileSink(file = file, maxBytes = 512)
        sink.emit("timestamp=2030-01-01T00:00:00Z event=PAGE_STARTED host=ws072.juntadeandalucia.es")
        val before = file.readText()

        sink.emit("safe-prefix\nsecret-canary")
        sink.emit("x".repeat(QaDiagnosticFileSink.MAX_RECORD_BYTES + 1))

        assertEquals(before, file.readText())
    }
}
