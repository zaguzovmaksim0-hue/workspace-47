package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.afirma.AfirmaParameter
import dev.junta.firmamobile.afirma.AfirmaParseErrorCode
import dev.junta.firmamobile.afirma.AfirmaParseResult
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.network.TrustedOrigin
import java.util.Locale

/**
 * Strict parser for standard AutoFirma requests from an unprofiled public HTTPS page.
 * Callback endpoints must belong to the exact visible page origin; no broad host trust
 * and no WebSocket/desktop legacy transport is admitted.
 */
internal class UniversalAfirmaUriParser {
    fun parse(rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        if (rawUri.length > MAX_URI_CHARS) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.URI_TOO_LARGE)
        }
        val uri = runCatching { Uri.parse(rawUri) }.getOrElse {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        }
        if (!uri.scheme.equals(AFIRMA_SCHEME, ignoreCase = true) || uri.isOpaque ||
            uri.encodedUserInfo != null || uri.port != -1 || uri.fragment != null
        ) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        }
        val operation = AfirmaOperation.entries.firstOrNull {
            it.wireName.equals(uri.host, ignoreCase = true)
        } ?: return AfirmaParseResult.Failure(AfirmaParseErrorCode.UNSUPPORTED_OPERATION)
        if (operation == AfirmaOperation.WEBSOCKET) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.UNSUPPORTED_OPERATION)
        }
        val parameters = when (val parsed = parseParameters(uri.encodedQuery)) {
            is ParameterParseResult.Success -> parsed.parameters
            is ParameterParseResult.Failure -> return AfirmaParseResult.Failure(parsed.code)
        }
        CRITICAL_PARAMETERS.forEach { name ->
            if (parameters[name].orEmpty().size > 1) {
                return AfirmaParseResult.Failure(
                    AfirmaParseErrorCode.DUPLICATE_CRITICAL_PARAMETER,
                )
            }
        }
        if (operation == AfirmaOperation.SIGN &&
            REQUIRED_SIGN_PARAMETERS.any {
                parameters[it]?.singleOrNull()?.decodedValue.isNullOrBlank()
            }
        ) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.MISSING_REQUIRED_PARAMETER)
        }
        CALLBACK_PARAMETERS.forEach { name ->
            parameters[name].orEmpty().forEach { parameter ->
                if (!isSameOriginHttpsCallback(parameter.decodedValue, origin)) {
                    return AfirmaParseResult.Failure(AfirmaParseErrorCode.UNSAFE_CALLBACK_URL)
                }
            }
        }
        return AfirmaParseResult.Success(
            AfirmaRequest(
                rawUri = rawUri,
                origin = origin,
                operation = operation,
                parameters = parameters,
            ),
        )
    }

    private fun parseParameters(encodedQuery: String?): ParameterParseResult {
        if (encodedQuery.isNullOrEmpty()) return ParameterParseResult.Success(emptyMap())
        val segments = encodedQuery.split('&')
        if (segments.size > MAX_PARAMETERS) {
            return ParameterParseResult.Failure(AfirmaParseErrorCode.TOO_MANY_PARAMETERS)
        }
        val parameters = linkedMapOf<String, MutableList<AfirmaParameter>>()
        for (segment in segments) {
            if (segment.isEmpty()) {
                return ParameterParseResult.Failure(AfirmaParseErrorCode.INVALID_PARAMETER)
            }
            val separator = segment.indexOf('=')
            val encodedName = if (separator == -1) segment else segment.substring(0, separator)
            val encodedValue = if (separator == -1) "" else segment.substring(separator + 1)
            if (!hasValidPercentEncoding(encodedName) || !hasValidPercentEncoding(encodedValue)) {
                return ParameterParseResult.Failure(AfirmaParseErrorCode.MALFORMED_ENCODING)
            }
            val name = runCatching {
                Uri.decode(encodedName).lowercase(Locale.ROOT)
            }.getOrElse {
                return ParameterParseResult.Failure(AfirmaParseErrorCode.MALFORMED_ENCODING)
            }
            if (!PARAMETER_NAME.matches(name)) {
                return ParameterParseResult.Failure(AfirmaParseErrorCode.INVALID_PARAMETER)
            }
            val value = runCatching { Uri.decode(encodedValue) }.getOrElse {
                return ParameterParseResult.Failure(AfirmaParseErrorCode.MALFORMED_ENCODING)
            }
            parameters.getOrPut(name) { mutableListOf() } +=
                AfirmaParameter(encodedValue = encodedValue, decodedValue = value)
        }
        return ParameterParseResult.Success(parameters.mapValues { it.value.toList() })
    }

    private fun isSameOriginHttpsCallback(rawUrl: String, origin: TrustedOrigin): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        if (uri.isOpaque || uri.encodedUserInfo != null || uri.fragment != null ||
            !uri.scheme.equals("https", ignoreCase = true)
        ) {
            return false
        }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val port = runCatching { uri.port.takeIf { it != -1 } ?: 443 }.getOrNull() ?: return false
        return host == origin.host && port == origin.port
    }

    private fun hasValidPercentEncoding(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                if (index + 2 >= value.length ||
                    value[index + 1].digitToIntOrNull(16) == null ||
                    value[index + 2].digitToIntOrNull(16) == null
                ) {
                    return false
                }
                index += 3
            } else {
                index += 1
            }
        }
        return true
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
