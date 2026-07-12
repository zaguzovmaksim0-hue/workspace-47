package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.security.SanitizedLogger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ProtocolObservationRecorderTest {
    private val logger = SanitizedLogger(
        Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
    )
    private val recorder = ProtocolObservationRecorder(logger)

    @Test
    fun recordsOnlyClosedSafeFields() {
        recorder.record(
            SafeProtocolObservation(
                call = ObservedMiniAppletCall.SIGN,
                originHost = "www.juntadeandalucia.es",
                algorithm = "SHA1withRSA",
                format = "CAdES",
                argumentLengths = listOf(128, 11, 5, 32, 0, 0),
                branch = ObservedRuntimeBranch.AFIRMA,
            ),
        )

        val output = recorder.exportText()
        assertTrue(output.contains("event=MINIAPPLET_OBSERVED"))
        assertTrue(output.contains("call=SIGN"))
        assertTrue(output.contains("branch=AFIRMA"))
        assertTrue(output.contains("algorithm=SHA1withRSA"))
        assertTrue(output.contains("argument.0.length=128"))
        assertFalse(output.contains(RAW_DAT_CANARY))
    }

    @Test
    fun rejectsUntrustedSubframeOversizedAndUnknownJsonFields() {
        val valid = """{
            "type":"MINIAPPLET_OBSERVATION",
            "requestId":"123e4567-e89b-12d3-a456-426614174000",
            "call":"SIGN",
            "algorithm":"SHA256withRSA",
            "format":"CAdES",
            "argumentLengths":[16,13,5]
        }""".trimIndent()

        assertFalse(
            recorder.recordMessage(
                valid,
                Uri.parse("https://evil.example"),
                isMainFrame = true,
            ),
        )
        assertFalse(
            recorder.recordMessage(
                valid,
                TRUSTED_ORIGIN,
                isMainFrame = false,
            ),
        )
        assertFalse(
            recorder.recordMessage(
                valid.dropLast(1) + ",\"dat\":\"$RAW_DAT_CANARY\"}",
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertFalse(
            recorder.recordMessage(
                valid.replace("[16,13,5]", "[1048577]"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        assertFalse(recorder.exportText().contains(RAW_DAT_CANARY))
    }

    private companion object {
        const val RAW_DAT_CANARY = "raw-document-must-never-be-recorded"
        val TRUSTED_ORIGIN: Uri = Uri.parse("https://www.juntadeandalucia.es")
    }
}
