package dev.junta.firmamobile.security

import java.io.File

internal object ApplicationSanitizedLoggerFactory {
    fun create(
        filesDirectory: File,
        qaEnabled: Boolean,
        diagnosticMirror: SanitizedLogSink,
    ): SanitizedLogger {
        val sink = if (qaEnabled) {
            val fileSink = QaDiagnosticFileSink(
                file = filesDirectory.resolve(QaDiagnosticFileSink.FILE_NAME),
            )
            SanitizedLogSink { record ->
                runCatching { fileSink.emit(record) }
                runCatching { diagnosticMirror.emit(record) }
            }
        } else {
            SanitizedLogSink {}
        }
        return SanitizedLogger(sink = sink)
    }
}
