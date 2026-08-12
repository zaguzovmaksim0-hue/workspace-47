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
            object : SanitizedLogSink {
                override fun emit(record: String) {
                    runCatching { fileSink.emit(record) }
                    runCatching { diagnosticMirror.emit(record) }
                }

                override fun clear() {
                    runCatching { fileSink.clear() }
                    runCatching { diagnosticMirror.clear() }
                }
            }
        } else {
            SanitizedLogSink {}
        }
        return SanitizedLogger(sink = sink)
    }
}
