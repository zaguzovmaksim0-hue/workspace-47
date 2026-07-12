package dev.junta.firmamobile.security

import dev.junta.firmamobile.afirma.AfirmaRequest
import java.security.MessageDigest
import java.time.Clock
import java.util.ArrayDeque
import java.util.Locale

enum class DiagnosticEventCode {
    EXTERNAL_NAVIGATION,
    PLAY_STORE_FALLBACK_INTERCEPTED,
    NAVIGATION_BLOCKED,
    WEB_MESSAGE_REJECTED,
    WEB_MESSAGE_FEATURE_UNAVAILABLE,
    DOCUMENT_START_SCRIPT_UNAVAILABLE,
    SSL_ERROR_CANCELLED,
    SAFE_BROWSING_BLOCKED,
    NETWORK_ERROR,
    AFIRMA_REQUEST_OBSERVED,
}

class SanitizedLogger(
    private val clock: Clock = Clock.systemUTC(),
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val records = ArrayDeque<String>(capacity)

    init {
        require(capacity in 1..MAX_CAPACITY)
    }

    @Synchronized
    fun recordAfirmaRequest(request: AfirmaRequest) {
        val fields = mutableListOf(
            "timestamp=${clock.instant()}",
            "event=${DiagnosticEventCode.AFIRMA_REQUEST_OBSERVED.name}",
            "origin=${safeHost(request.origin.host)}",
            "operation=${request.operation.wireName}",
            "algorithm=${safeToken(request.singleValue("algorithm"))}",
            "format=${safeToken(request.singleValue("format"))}",
        )
        request.parameters.toSortedMap().forEach { (rawName, values) ->
            val name = safeParameterName(rawName)
            values.forEachIndexed { index, parameter ->
                fields += "parameter.$name.$index.length=${parameter.decodedValue.length}"
                fields += "parameter.$name.$index.sha256_8=${sha256Prefix(parameter.decodedValue)}"
            }
        }
        append(fields.joinToString(separator = " "))
    }

    @Synchronized
    fun recordBrowserEvent(code: DiagnosticEventCode, host: String? = null) {
        val record = buildString {
            append("timestamp=")
            append(clock.instant())
            append(" event=")
            append(code.name)
            if (host != null) {
                append(" host=")
                append(safeHost(host))
            }
        }
        append(record)
    }

    @Synchronized
    fun snapshot(): List<String> = records.toList()

    @Synchronized
    fun exportText(): String = records.joinToString(separator = "\n")

    @Synchronized
    fun clear() {
        records.clear()
    }

    private fun append(record: String) {
        while (records.size >= capacity) records.removeFirst()
        records.addLast(record)
    }

    private fun safeHost(host: String): String {
        val normalized = host.lowercase(Locale.ROOT)
        return normalized.takeIf(HOST_PATTERN::matches) ?: INVALID_VALUE
    }

    private fun safeToken(value: String?): String =
        value?.takeIf(TOKEN_PATTERN::matches) ?: INVALID_VALUE

    private fun safeParameterName(value: String): String =
        value.takeIf(PARAMETER_NAME_PATTERN::matches) ?: INVALID_VALUE

    private fun sha256Prefix(value: String): String {
        val bytes = value.encodeToByteArray()
        val digest = try {
            MessageDigest.getInstance("SHA-256").digest(bytes)
        } finally {
            bytes.fill(0)
        }
        return try {
            buildString(SHA256_PREFIX_BYTES * 2) {
                repeat(SHA256_PREFIX_BYTES) { index ->
                    append(HEX[(digest[index].toInt() ushr 4) and 0x0f])
                    append(HEX[digest[index].toInt() and 0x0f])
                }
            }
        } finally {
            digest.fill(0)
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
        const val MAX_CAPACITY = 1000
        const val SHA256_PREFIX_BYTES = 4
        const val INVALID_VALUE = "invalid"
        const val HEX = "0123456789abcdef"
        val HOST_PATTERN = Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")
        val TOKEN_PATTERN = Regex("[A-Za-z0-9._+\\-]{1,64}")
        val PARAMETER_NAME_PATTERN = Regex("[a-z][a-z0-9_-]{0,63}")
    }
}
