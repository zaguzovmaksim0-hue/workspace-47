package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.afirma.AfirmaParameter
import dev.junta.firmamobile.afirma.AfirmaParseErrorCode
import dev.junta.firmamobile.afirma.AfirmaParseResult
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.afirma.servlet.AfirmaEndpointPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import java.util.Locale

class UniversalAfirmaUriParser {
    fun parse(rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        if (rawUri.length > MAX_URI_CHARS) return AfirmaParseResult.Failure(AfirmaParseErrorCode.URI_TOO_LARGE)
        return parseUri(rawUri, origin)
    }

    private fun parseUri(rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
            ?: return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        if (hasUnsupportedUriShape(uri)) return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        return parseOperation(uri, rawUri, origin)
    }

    private fun parseOperation(uri: Uri, rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        val operation = resolveSupportedOperation(uri)
            ?: return AfirmaParseResult.Failure(AfirmaParseErrorCode.UNSUPPORTED_OPERATION)
        return when (val parsed = parseParameters(uri.encodedQuery)) {
            is ParameterParseResult.Success -> parseValidatedOperation(operation, rawUri, parsed.parameters, origin)
            is ParameterParseResult.Failure -> AfirmaParseResult.Failure(parsed.code)
        }
    }

    private fun parseValidatedOperation(
        operation: AfirmaOperation,
        rawUri: String,
        parameters: Map<String, List<AfirmaParameter>>,
        origin: TrustedOrigin,
    ): AfirmaParseResult {
        val error = validateParameters(operation, parameters) ?: validateCallbacks(parameters)
        return error?.let { AfirmaParseResult.Failure(it) }
            ?: AfirmaParseResult.Success(AfirmaRequest(rawUri, origin, operation, parameters))
    }

    private fun hasUnsupportedUriShape(uri: Uri): Boolean {
        val unsupportedScheme = !uri.scheme.equals(AFIRMA_SCHEME, ignoreCase = true)
        return unsupportedScheme || uri.isOpaque ||
            uri.encodedUserInfo != null || uri.port != -1 || uri.fragment != null
    }

    private fun resolveSupportedOperation(uri: Uri): AfirmaOperation? {
        val operation = AfirmaOperation.entries.firstOrNull {
            it.wireName.equals(uri.host, ignoreCase = true)
        }
        return operation?.takeUnless { it == AfirmaOperation.WEBSOCKET }
    }

    private fun validateParameters(
        operation: AfirmaOperation,
        rawUri: String,
        parameters: Map<String, List<AfirmaParameter>>,
    ): AfirmaParseErrorCode? = when {
        CRITICAL_PARAMETERS.any { parameters[it].orEmpty().size > 1 } ->
            AfirmaParseErrorCode.DUPLICATE_CRITICAL_PARAMETER
        operation != AfirmaOperation.SIGN -> null
        REQUIRED_SIGN_PARAMETERS.any { parameters[it]?.singleOrNull()?.decodedValue.isNullOrBlank() } ->
            AfirmaParseErrorCode.MISSING_REQUIRED_PARAMETER
        else -> null
    }

    private fun validateCallbacks(
        parameters: Map<String, List<AfirmaParameter>>,
    ): AfirmaParseErrorCode? {
        val unsafeCallback = CALLBACK_PARAMETERS.any { name ->
            parameters[name].orEmpty().any { parameter ->
                !isStrictPublicHttpsEndpoint(parameter.decodedValue)
            }
        }
        return if (unsafeCallback) AfirmaParseErrorCode.UNSAFE_CALLBACK_URL else null
    }

    private fun parseParameters(encodedQuery: String?): ParameterParseResult =
        if (encodedQuery.isNullOrEmpty()) {
            ParameterParseResult.Success(emptyMap())
        } else {
            parseSegments(encodedQuery.split('&'))
        }

    private fun parseSegments(segments: List<String>): ParameterParseResult {
        if (segments.size > MAX_PARAMETERS) {
            return ParameterParseResult.Failure(AfirmaParseErrorCode.TOO_MANY_PARAMETERS)
        }
        val parameters = linkedMapOf<String, MutableList<AfirmaParameter>>()
        for (segment in segments) {
            val parameter = parseParameter(segment)
                ?: return ParameterParseResult.Failure(AfirmaParseErrorCode.INVALID_PARAMETER)
            parameters.getOrPut(parameter.first) { mutableListOf() } += parameter.second
        }
        return ParameterParseResult.Success(parameters.mapValues { it.value.toList() })
    }

    private fun parseParameter(segment: String): Pair<String, AfirmaParameter>? {
        val separator = segment.indexOf('=')
        val encodedName = if (separator == -1) segment else segment.substring(0, separator)
        val encodedValue = if (separator == -1) "" else segment.substring(separator + 1)
        val name = encodedName.takeIf { hasValidPercentEncoding(it) }
            ?.let { runCatching { Uri.decode(it).lowercase(Locale.ROOT) }.getOrNull() }
        val value = encodedValue.takeIf { hasValidPercentEncoding(it) }
            ?.let { runCatching { Uri.decode(it) }.getOrNull() }
        return if (name != null && value != null && PARAMETER_NAME.matches(name)) {
            name to AfirmaParameter(encodedValue = encodedValue, decodedValue = value)
        } else {
            null
        }
    }

    // A portal page and its standard AutoFirma storage/retrieval services may
    // use different public HTTPS origins (for example, IFEF). Admission is
    // protocol-shaped, not same-origin; the native parser and explicit consent
    // still validate the complete request and display the upload destination.
    private fun isStrictPublicHttpsEndpoint(rawUrl: String): Boolean {
        val uri = runCatching { java.net.URI(rawUrl) }.getOrNull() ?: return false
        return AfirmaEndpointPolicy.accepts(uri)
    }

    private fun hasValidPercentEncoding(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            if (value[index] != '%') {
                index += SINGLE_CHARACTER_STEP
                continue
            }
            if (!hasHexDigitsAt(value, index + 1)) return false
            index += PERCENT_SEQUENCE_LENGTH
        }
        return true
    }

    private fun hasHexDigitsAt(value: String, startIndex: Int): Boolean {
        if (startIndex + 1 >= value.length) return false
        return value[startIndex].digitToIntOrNull(HEX_RADIX) != null &&
            value[startIndex + 1].digitToIntOrNull(HEX_RADIX) != null
    }

    private sealed interface ParameterParseResult {
        data class Success(val parameters: Map<String, List<AfirmaParameter>>) : ParameterParseResult
        data class Failure(val code: AfirmaParseErrorCode) : ParameterParseResult
    }

    private companion object {
        const val MAX_URI_CHARS = 1024 * 1024
        const val MAX_PARAMETERS = 64
        const val AFIRMA_SCHEME = "afirma"
        const val SINGLE_CHARACTER_STEP = 1
        const val PERCENT_SEQUENCE_LENGTH = 3
        const val HEX_RADIX = 16
        val PARAMETER_NAME = Regex("[a-z][a-z0-9_-]{0,63}")
        val REQUIRED_SIGN_PARAMETERS = setOf("algorithm", "format")
        val CALLBACK_PARAMETERS = setOf("serverurl", "rtservlet", "stservlet")
        val CRITICAL_PARAMETERS = setOf(
            "dat", "algorithm", "format", "properties", "serverurl", "rtservlet",
            "stservlet", "id", "key", "fileid", "cop", "deskey", "cipherkey",
        )
    }
}
