package dev.junta.firmamobile.security

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationSanitizedLoggerFactoryTest {
    @Test
    fun qaModeMirrorsSanitizedRecordsToPrivateFileAndDiagnosticSink() {
        val directory = Files.createTempDirectory("jfm-qa-logger-factory").toFile()
        val mirrored = mutableListOf<String>()
        val logger = ApplicationSanitizedLoggerFactory.create(
            filesDirectory = directory,
            qaEnabled = true,
            diagnosticMirror = SanitizedLogSink(mirrored::add),
        )

        logger.recordPortalCallback("CALLBACK_RETURNED", "ws072.juntadeandalucia.es")

        val file = directory.resolve(QaDiagnosticFileSink.FILE_NAME)
        assertTrue(file.isFile)
        assertTrue(file.readText().contains("event=PORTAL_CALLBACK"))
        assertTrue(mirrored.single().contains("stage=CALLBACK_RETURNED"))
    }

    @Test
    fun nonQaModeDoesNotCreateOrMirrorPersistentDiagnostics() {
        val directory = Files.createTempDirectory("jfm-release-logger-factory").toFile()
        val mirrored = mutableListOf<String>()
        val logger = ApplicationSanitizedLoggerFactory.create(
            filesDirectory = directory,
            qaEnabled = false,
            diagnosticMirror = SanitizedLogSink(mirrored::add),
        )

        logger.recordBrowserEvent(DiagnosticEventCode.NETWORK_ERROR, "ws072.juntadeandalucia.es")

        assertFalse(directory.resolve(QaDiagnosticFileSink.FILE_NAME).exists())
        assertTrue(mirrored.isEmpty())
        assertTrue(logger.exportText().contains("event=NETWORK_ERROR"))
    }
}
