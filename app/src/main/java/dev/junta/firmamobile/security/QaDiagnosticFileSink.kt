package dev.junta.firmamobile.security

import java.io.File
import java.nio.charset.StandardCharsets

internal class QaDiagnosticFileSink(
    private val file: File,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) : SanitizedLogSink {
    init {
        require(maxBytes in MIN_MAX_BYTES..MAX_MAX_BYTES)
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { }
        }
    }

    @Synchronized
    override fun emit(record: String) {
        if (!isSafeRecord(record)) return
        val recordBytes = record.toByteArray(StandardCharsets.US_ASCII)
        if (recordBytes.size + NEWLINE_BYTES.size > maxBytes) {
            recordBytes.fill(0)
            return
        }

        runCatching {
            val records = if (file.isFile) {
                file.readLines(StandardCharsets.US_ASCII)
                    .filter(::isSafeRecord)
                    .toMutableList()
            } else {
                mutableListOf()
            }
            records += record
            while (serializedSize(records) > maxBytes && records.isNotEmpty()) {
                records.removeAt(0)
            }
            val output = buildString {
                records.forEach { line ->
                    append(line)
                    append('\n')
                }
            }.toByteArray(StandardCharsets.US_ASCII)
            file.outputStream().use { stream ->
                stream.write(output)
                stream.flush()
            }
            output.fill(0)
        }
        recordBytes.fill(0)
    }

    private fun isSafeRecord(record: String): Boolean {
        if (record.isEmpty() || record.length > MAX_RECORD_BYTES ||
            (!record.startsWith("timestamp=") && !record.startsWith("event=")) ||
            record.any { character -> character.code !in ASCII_SPACE..ASCII_TILDE }
        ) {
            return false
        }
        val event = EVENT_PATTERN.find(record)?.groupValues?.getOrNull(1) ?: return false
        return event in ALLOWED_EVENTS
    }

    private fun serializedSize(records: List<String>): Int =
        records.sumOf { record -> record.toByteArray(StandardCharsets.US_ASCII).size + NEWLINE_BYTES.size }

    companion object {
        const val FILE_NAME = "qa-navigation.log"
        const val MAX_RECORD_BYTES = 4_096
        const val DEFAULT_MAX_BYTES = 65_536
        private const val MIN_MAX_BYTES = 128
        private const val MAX_MAX_BYTES = 1_048_576
        private const val ASCII_SPACE = 0x20
        private const val ASCII_TILDE = 0x7e
        private val NEWLINE_BYTES = byteArrayOf('\n'.code.toByte())
        private val EVENT_PATTERN = Regex("(?:^| )event=([A-Z_]+)(?: |$)")
        private val ALLOWED_EVENTS = setOf(
            DiagnosticEventCode.EXTERNAL_NAVIGATION.name,
            DiagnosticEventCode.PLAY_STORE_FALLBACK_INTERCEPTED.name,
            DiagnosticEventCode.NAVIGATION_BLOCKED.name,
            DiagnosticEventCode.WEB_MESSAGE_REJECTED.name,
            DiagnosticEventCode.WEB_MESSAGE_FEATURE_UNAVAILABLE.name,
            DiagnosticEventCode.DOCUMENT_START_SCRIPT_UNAVAILABLE.name,
            DiagnosticEventCode.SSL_ERROR_CANCELLED.name,
            DiagnosticEventCode.SAFE_BROWSING_BLOCKED.name,
            DiagnosticEventCode.NETWORK_ERROR.name,
            DiagnosticEventCode.NAVIGATION_ALLOWED.name,
            DiagnosticEventCode.NETWORK_REQUEST.name,
            DiagnosticEventCode.PAGE_STARTED.name,
            DiagnosticEventCode.PAGE_FINISHED.name,
            DiagnosticEventCode.PORTAL_CALLBACK.name,
        )
    }
}
