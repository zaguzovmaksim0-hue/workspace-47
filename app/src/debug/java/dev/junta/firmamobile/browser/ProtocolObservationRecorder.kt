package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.security.SanitizedLogger
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class ObservedMiniAppletCall {
    LOAD,
    SIGN,
}

enum class ObservedRuntimeBranch {
    AFIRMA,
    INTENT,
    WEBSOCKET,
    DIRECT_NETWORK,
    NONE,
}

data class SafeProtocolObservation(
    val call: ObservedMiniAppletCall,
    val originHost: String,
    val algorithm: String?,
    val format: String?,
    val argumentLengths: List<Int>,
    val branch: ObservedRuntimeBranch,
)

fun interface ProtocolObservationSink {
    fun record(observation: SafeProtocolObservation)
}

class ProtocolObservationRecorder(
    private val logger: SanitizedLogger,
    private val onUpdated: (String) -> Unit = {},
) : ProtocolObservationSink {
    private var latestCall: SafeProtocolObservation? = null

    @Synchronized
    override fun record(observation: SafeProtocolObservation) {
        if (!isValid(observation)) return
        latestCall = observation
        logger.recordMiniAppletObservation(
            call = observation.call.name,
            originHost = observation.originHost,
            algorithm = observation.algorithm,
            format = observation.format,
            argumentLengths = observation.argumentLengths,
            branch = observation.branch.name,
        )
        onUpdated(logger.exportText())
    }

    @Synchronized
    fun recordBranch(branch: ObservedRuntimeBranch, originHost: String): Boolean {
        if (branch == ObservedRuntimeBranch.NONE) return false
        val observation = latestCall?.copy(
            originHost = originHost,
            branch = branch,
        ) ?: SafeProtocolObservation(
            call = ObservedMiniAppletCall.LOAD,
            originHost = originHost,
            algorithm = null,
            format = null,
            argumentLengths = emptyList(),
            branch = branch,
        )
        if (!isValid(observation)) return false
        record(observation)
        return true
    }

    @Synchronized
    fun recordMessage(rawMessage: String, sourceOrigin: Uri, isMainFrame: Boolean): Boolean {
        if (!isMainFrame || rawMessage.length > MAX_MESSAGE_CHARS) return false
        val origin = JuntaOriginPolicy.originFor(sourceOrigin) ?: return false
        val json = try {
            JSONObject(rawMessage)
        } catch (_: Exception) {
            return false
        }
        if (!hasCanonicalRequestId(json)) return false
        return when (json.optString(TYPE_FIELD)) {
            TYPE_MINIAPPLET_OBSERVATION -> {
                if (json.keySet() !in MINIAPPLET_ALLOWED_KEY_SETS) return false
                val call = json.stringEnum<ObservedMiniAppletCall>(CALL_FIELD) ?: return false
                val algorithm = json.optionalSafeToken(ALGORITHM_FIELD) ?: if (
                    json.has(ALGORITHM_FIELD) && !json.isNull(ALGORITHM_FIELD)
                ) {
                    return false
                } else {
                    null
                }
                val format = json.optionalSafeToken(FORMAT_FIELD) ?: if (
                    json.has(FORMAT_FIELD) && !json.isNull(FORMAT_FIELD)
                ) {
                    return false
                } else {
                    null
                }
                val lengths = json.safeArgumentLengths() ?: return false
                record(
                    SafeProtocolObservation(
                        call = call,
                        originHost = origin.host,
                        algorithm = algorithm,
                        format = format,
                        argumentLengths = lengths,
                        branch = ObservedRuntimeBranch.NONE,
                    ),
                )
                true
            }
            TYPE_RUNTIME_BRANCH_OBSERVATION -> {
                if (json.keySet() != BRANCH_ALLOWED_KEYS) return false
                val branch = json.stringEnum<ObservedRuntimeBranch>(BRANCH_FIELD)
                    ?.takeUnless { it == ObservedRuntimeBranch.NONE }
                    ?: return false
                recordBranch(branch, origin.host)
            }
            else -> false
        }
    }

    @Synchronized
    fun exportText(): String = logger.exportText()

    private fun isValid(observation: SafeProtocolObservation): Boolean =
        JuntaOriginPolicy.allowedHosts.contains(observation.originHost) &&
            observation.argumentLengths.size <= MAX_ARGUMENTS &&
            observation.argumentLengths.all { it in 0..MAX_ARGUMENT_LENGTH } &&
            observation.algorithm.isSafeOptionalToken() &&
            observation.format.isSafeOptionalToken()

    private fun JSONObject.safeArgumentLengths(): List<Int>? {
        val values = opt(ARGUMENT_LENGTHS_FIELD) as? JSONArray ?: return null
        if (values.length() > MAX_ARGUMENTS) return null
        return buildList(values.length()) {
            repeat(values.length()) { index ->
                val number = values.opt(index) as? Number ?: return null
                val value = number.toLong()
                if (value !in 0L..MAX_ARGUMENT_LENGTH.toLong()) return null
                add(value.toInt())
            }
        }
    }

    private fun JSONObject.optionalSafeToken(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name) as? String ?: return null
        return value.takeIf(SAFE_TOKEN::matches)
    }

    private fun hasCanonicalRequestId(json: JSONObject): Boolean {
        val raw = json.opt(REQUEST_ID_FIELD) as? String ?: return false
        val parsed = try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return parsed.toString().equals(raw, ignoreCase = true)
    }

    private inline fun <reified T : Enum<T>> JSONObject.stringEnum(name: String): T? {
        val value = opt(name) as? String ?: return null
        return enumValues<T>().firstOrNull { it.name == value }
    }

    private fun JSONObject.keySet(): Set<String> = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }

    private fun String?.isSafeOptionalToken(): Boolean = this == null || SAFE_TOKEN.matches(this)

    private companion object {
        const val MAX_MESSAGE_CHARS = 4096
        const val MAX_ARGUMENTS = 32
        const val MAX_ARGUMENT_LENGTH = 1_048_576
        const val TYPE_FIELD = "type"
        const val REQUEST_ID_FIELD = "requestId"
        const val CALL_FIELD = "call"
        const val ALGORITHM_FIELD = "algorithm"
        const val FORMAT_FIELD = "format"
        const val ARGUMENT_LENGTHS_FIELD = "argumentLengths"
        const val BRANCH_FIELD = "branch"
        const val TYPE_MINIAPPLET_OBSERVATION = "MINIAPPLET_OBSERVATION"
        const val TYPE_RUNTIME_BRANCH_OBSERVATION = "RUNTIME_BRANCH_OBSERVATION"
        val SAFE_TOKEN = Regex("[A-Za-z0-9._+\\-]{1,64}")
        val MINIAPPLET_REQUIRED_KEYS = setOf(
            TYPE_FIELD,
            REQUEST_ID_FIELD,
            CALL_FIELD,
            ARGUMENT_LENGTHS_FIELD,
        )
        val MINIAPPLET_ALLOWED_KEY_SETS = setOf(
            MINIAPPLET_REQUIRED_KEYS,
            MINIAPPLET_REQUIRED_KEYS + ALGORITHM_FIELD,
            MINIAPPLET_REQUIRED_KEYS + FORMAT_FIELD,
            MINIAPPLET_REQUIRED_KEYS + setOf(ALGORITHM_FIELD, FORMAT_FIELD),
        )
        val BRANCH_ALLOWED_KEYS = setOf(TYPE_FIELD, REQUEST_ID_FIELD, BRANCH_FIELD)
    }
}
