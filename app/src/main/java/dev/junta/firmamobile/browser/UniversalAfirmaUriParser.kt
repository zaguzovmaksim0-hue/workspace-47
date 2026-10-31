package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.afirma.AfirmaParameter
import dev.junta.firmamobile.afirma.AfirmaParseErrorCode
import dev.junta.firmamobile.afirma.AfirmaParseResult
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.network.TrustedOrigin
import java.util.Locale

class UniversalAfirmaUriParser {
    fun parse(rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        if (rawUri.length > MAX_URI_CHARS) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.URI_TOO_LARGE)
        }
        val uri = runCatching { Uri.parse(rawUri) }.getOrElse {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        }
        if (hasUnsupportedUriShape(uri)) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        }
        val operation = resolveSupportedOperation(uri)
            ?: return AfirmaParseResult.Failure(AfirmaParseErrorCode.UNSUPPORTED_OPERATION)
        val parameters = when (val parsed = parseParameters(uri.encodedQuery)) {
            is ParameterParseResult.Success -> parsed.parameters
            is ParameterParseResult.Failure -> return AfirmaParseResult.Failure(parsed.code)
        }
        validateParameters(operation, parameters)?.let { code ->
            return AfirmaParseResult.Failure(code)
        }
        validateCallbacks(parameters, origin)?.let { code ->
            return AfirmaParseResult.Failure(code)
        }
        return AfirmaParseResult.Success(
            AfirmaRequest(rawUri, origin, operation, parameters),
        )
    }

    private fun hasUnsupportedUriShape(uri: Uri): Boolean {
        val unsupportedScheme = !uri.scheme.equals(AFIRMA_SCHEME, ignoreCase = true)
        val unsupportedPort = uri.port != -1
        return unsupportedScheme || uri.isOpaque ||
            uri.encodedUserInfo != null || unsupportedPort || uri.fragment != null
    }

    private fun resolveSupportedOperation(uri: Uri): AfirmaOperation? {
        val operation = AfirmaOperation.entries.firstOrNull {
            it.wireName.equals(uri.host, ignoreCase = true)
        }
        return operation?.takeUnless { it == AfirmaOperation.WEBSOCKET }
    }

    private fun validateParameters(
        operation: AfirmaOperation,
        parameters: Map<String, List<AfirmaParameter>>,
    ): AfirmaParseErrorCode? {
        val duplicate = CRITICAL_PARAMETERS.firstOrNull { name ->
            parameters[name].orEmpty().size > 1
        }
        if (duplicate != null) {
            return AfirmaParseErrorCode.DUPLICATE_CRITICAL_PARAMETER
        }
        if (operation != AfirmaOperation.SIGN) return null
        val missing = REQUIRED_SIGN_PARAMETERS.any { name ->
            parameters[name]?.singleOrNull()?.decodedValue.isNullOrBlank()
        }
        return if (missing) AfirmaParseErrorCode.MISSING_REQUIRED_PARAMETER else null
    }

    private fun validateCallbacks(
        parameters: Map<String, List<AfirmaParameter>>,
        origin: TrustedOrigin,
    ): AfirmaParseErrorCode? {
        val unsafeCallback = CALLBACK_PARAMETERS.any { name ->
            parameters[name].orEmpty().any { parameter ->
                !isSameOriginHttpsCallback(parameter.decodedValue, origin)
            }
        }
        return if (unsafeCallback) AfirmaParseErrorCode.UNSAFE_CALLBACK_URL else null
    }

    private fun parseParameters(encodedQuery: String?): ParameterParseResult {
        if (encodedQuery.isNullOrEmpty()) return ParameterParseResult.Success(emptyMap())
        val segments = encodedQuery.split('&')
        if (segments.size > MAX_PARAMETERS) {
            return ParameterParseResult.Failure(AfirmaParseErrorCode.TOO_MANY_PARAMETERS)
        }
        val parameters = linkedMapOf<String, MutableList<AfirmaParameter>>()
        for (segment in segments) {
            val parameter = parseParameter(segment) ?: return ParameterParseResult.Failure(
                AfirmaParseErrorCode.INVALID_PARAMETER,
            )
            parameters.getOrPut(parameter.first) { mutableListOf() } += parameter.second
        }
        return ParameterParseResult.Success(parameters.mapValues { it.value.toList() })
    }

    private fun parseParameter(segment: String): Pair<String, AfirmaParameter>? {
        if (segment.isEmpty()) return null
        val separator = segment.indexOf('=')
        val encodedName = if (separator == -1) segment else segment.substring(0, separator)
        val encodedValue = if (separator == -1) "" else segment.substring(separator + 1)
        if (!hasValidPercentEncoding(encodedName) || !hasValidPercentEncoding(encodedValue)) {
            return null
        }
        val name = runCatching {
            Uri.decode(encodedName).lowercase(Locale.ROOT)
        }.getOrNull() ?: return null
        if (!PARAMETER_NAME.matches(name)) return null
        val value = runCatching { Uri.decode(encodedValue) }.getOrNull() ?: return null
        return name to AfirmaParameter(encodedValue = encodedValue, decodedValue = value)
    }

    private fun isSameOriginHttpsCallback(rawUrl: String, origin: TrustedOrigin): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        if (hasUnsupportedCallbackShape(uri)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val port = runCatching { uri.port.takeIf { it != -1 } ?: 443 }.getOrNull() ?: return false
        return host == origin.host && port == origin.port
    }

    private fun hasUnsupportedCallbackShape(uri: Uri): Boolean {
        val unsupportedScheme = !uri.scheme.equals("https", ignoreCase = true)
        return uri.isOpaque || uri.encodedUserInfo != null ||
            uri.fragment != null || unsupportedScheme
    }

    private fun hasValidPercentEncoding(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            if (value[index] != '%') {
                index += 1
                continue
            }
            if (!hasHexDigitsAt(value, index + 1)) return false
            index += 3
        }
        return true
    }

    private fun hasHexDigitsAt(value: String, startIndex: Int): Boolean {
        if (startIndex + 1 >= value.length) return false
        return value[startIndex].digitToIntOrNull(16) != null &&
            value[startIndex + 1].digitToIntOrNull(16) != null
    }

    private sealed interface ParameterParseResult {
        data class Success(val parameters: Map<String, List<AfirmaParameter>>) : ParameterParseResult
        data class Failure(val code: AfirmaParseErrorCode) : ParameterParseResult
    }

    private companion object {
        const val MAX_URI_CHARS = 1024 * 1024
        const val MAX_PARAMETERS = 64
        const val AFIRMA_SCHEME = "afirma"
        val PARAMETER_NAME = Regex("[a-z][a-z0-9_-]{0,63}")
        val REQUIRED_SIGN_PARAMETERS = setOf("algorithm", "format")
        val CALLBACK_PARAMETERS = setOf("serverurl", "rtservlet", "stservlet")
        val CRITICAL_PARAMETERS = setOf(
            "dat", "algorithm", "format", "properties", "serverurl", "rtservlet",
            "stservlet", "id", "key", "fileid", "cop", "deskey", "cipherkey",
        )
    }
}
