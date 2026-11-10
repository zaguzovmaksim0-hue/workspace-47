package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.afirma.servlet.NativeAfirmaLimits
import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.afirma.AfirmaParameter
import dev.junta.firmamobile.afirma.AfirmaParseErrorCode
import dev.junta.firmamobile.afirma.AfirmaParseResult
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.afirma.servlet.AfirmaEndpointPolicy
import dev.junta.firmamobile.network.TrustedOrigin

private const val PERCENT_SINGLE_CHARACTER_STEP = 1
private const val PERCENT_SEQUENCE_LENGTH = 3
private const val PERCENT_HEX_RADIX = 16
private val PARAMETER_NAME = Regex("[a-z][a-z0-9_-]{0,63}")
private const val RETRIEVAL_HTTPS_PORT = 443
private val RETRIEVAL_HTTPS_PORTS = setOf(-1, RETRIEVAL_HTTPS_PORT)
private const val OFFICIAL_AFIRMA_RETRIEVAL_HOST = "afirma.redsara.es"

// A portal page may use a separate public HTTPS AutoFirma storage/retrieval
// service. Syntax-level admission is deliberately generic, but remains
// fail-closed: AfirmaEndpointPolicy rejects non-HTTPS, malformed, local and
// non-public-shaped endpoints. The transport independently validates resolved
// DNS addresses, disables redirects/retries, and explicit consent displays the
// destination before any upload.
private fun isAllowedUniversalEndpoint(rawUrl: String): Boolean {
    val uri = runCatching { java.net.URI(rawUrl) }.getOrNull()
    return uri != null && AfirmaEndpointPolicy.accepts(uri)
}

private fun hasValidPercentEncoding(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        if (value[index] != '%') {
            index += PERCENT_SINGLE_CHARACTER_STEP
            continue
        }
        if (!hasHexDigitsAt(value, index + 1)) return false
        index += PERCENT_SEQUENCE_LENGTH
    }
    return true
}

private fun hasSafeRetrievalTransport(endpoint: java.net.URI): Boolean =
    endpoint.scheme.equals("https", true) &&
        endpoint.rawUserInfo == null &&
        endpoint.rawFragment == null

private fun hasAllowedRetrievalPort(endpoint: java.net.URI): Boolean =
    endpoint.port in RETRIEVAL_HTTPS_PORTS

private fun isAllowedRetrievalHost(endpoint: java.net.URI, origin: TrustedOrigin): Boolean {
    val host = endpoint.host?.lowercase(java.util.Locale.ROOT) ?: return false
    return host == origin.host.lowercase(java.util.Locale.ROOT) ||
        host == OFFICIAL_AFIRMA_RETRIEVAL_HOST
}

private fun parseParameter(segment: String): Pair<String, AfirmaParameter>? {
    val separator = segment.indexOf('=')
    val encodedName = if (separator == -1) segment else segment.substring(0, separator)
    val encodedValue = if (separator == -1) "" else segment.substring(separator + 1)
    val name = encodedName.takeIf { hasValidPercentEncoding(it) }
        ?.let { runCatching { Uri.decode(it).lowercase(java.util.Locale.ROOT) }.getOrNull() }
    val value = encodedValue.takeIf { hasValidPercentEncoding(it) }
        ?.let { runCatching { Uri.decode(it) }.getOrNull() }
    return if (name != null && value != null && PARAMETER_NAME.matches(name)) {
        name to AfirmaParameter(encodedValue = encodedValue, decodedValue = value)
    } else {
        null
    }
}

private fun hasHexDigitsAt(value: String, startIndex: Int): Boolean {
    if (startIndex + 1 >= value.length) return false
    return value[startIndex].digitToIntOrNull(PERCENT_HEX_RADIX) != null &&
        value[startIndex + 1].digitToIntOrNull(PERCENT_HEX_RADIX) != null
}

class UniversalAfirmaUriParser {
    fun parse(rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        if (rawUri.length > MAX_URI_CHARS) return AfirmaParseResult.Failure(AfirmaParseErrorCode.URI_TOO_LARGE)
        return parseUri(rawUri, origin)
    }

    private fun parseUri(rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
        val isInvalidUri = uri == null || hasUnsupportedUriShape(uri)
        return if (isInvalidUri) {
            AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        } else {
            parseOperation(checkNotNull(uri), rawUri, origin)
        }
    }

    private fun parseOperation(uri: Uri, rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        val operation = resolveSupportedOperation(uri)
            ?: return AfirmaParseResult.Failure(AfirmaParseErrorCode.UNSUPPORTED_OPERATION)
        return when (val parsed = parseParameters(uri.encodedQuery)) {
            is ParameterParseResult.Success ->
                parseValidatedOperation(operation, rawUri, parsed.parameters, origin)
            is ParameterParseResult.Failure -> AfirmaParseResult.Failure(parsed.code)
        }
    }

    private fun parseValidatedOperation(
        operation: AfirmaOperation,
        rawUri: String,
        parameters: Map<String, List<AfirmaParameter>>,
        origin: TrustedOrigin,
    ): AfirmaParseResult {
        val error = validateParameters(operation, parameters) ?:
            validateCallbacks(parameters, origin)
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
        origin: TrustedOrigin,
    ): AfirmaParseErrorCode? {
        val unsafeCallback = CALLBACK_PARAMETERS.any { name ->
            parameters[name].orEmpty().any { parameter ->
                val endpoint = runCatching { java.net.URI(parameter.decodedValue) }.getOrNull()
                !isAllowedUniversalEndpoint(parameter.decodedValue) ||
                    (name == RETRIEVAL_CALLBACK_PARAMETER && !isAllowedRetrievalEndpoint(endpoint, origin))
            }
        }
        return if (unsafeCallback) AfirmaParseErrorCode.UNSAFE_CALLBACK_URL else null
    }

    // Retrieval is the only callback that can cause AutoFirma to fetch and
    // execute subsequent protocol configuration. Keep it bound to the visible
    // page origin, except for the official public AutoFirma retrieval service.
    private fun isAllowedRetrievalEndpoint(endpoint: java.net.URI?, origin: TrustedOrigin): Boolean {
        if (endpoint == null) return false
        return hasSafeRetrievalTransport(endpoint) &&
            hasAllowedRetrievalPort(endpoint) &&
            isAllowedRetrievalHost(endpoint, origin)
    }

    private fun parseParameters(encodedQuery: String?): ParameterParseResult = when {
        encodedQuery.isNullOrEmpty() -> ParameterParseResult.Success(emptyMap())
        encodedQuery.split('&').size > MAX_PARAMETERS ->
            ParameterParseResult.Failure(AfirmaParseErrorCode.TOO_MANY_PARAMETERS)
        else -> {
            val parsedParameters = encodedQuery.split('&').map(::parseParameter)
            if (parsedParameters.all { it != null }) {
                val parameters = parsedParameters.filterNotNull()
                    .groupBy({ it.first }, { it.second })
                ParameterParseResult.Success(parameters)
            } else {
                ParameterParseResult.Failure(AfirmaParseErrorCode.INVALID_PARAMETER)
            }
        }
    }

    private sealed interface ParameterParseResult {
        data class Success(val parameters: Map<String, List<AfirmaParameter>>) : ParameterParseResult
        data class Failure(val code: AfirmaParseErrorCode) : ParameterParseResult
    }

    private companion object {
        const val MAX_URI_CHARS = NativeAfirmaLimits.URI_CHARS
        const val MAX_PARAMETERS = 64
        const val AFIRMA_SCHEME = "afirma"
        val REQUIRED_SIGN_PARAMETERS = setOf("algorithm", "format")
        val CALLBACK_PARAMETERS = setOf("serverurl", "rtservlet", "stservlet")
        const val RETRIEVAL_CALLBACK_PARAMETER = "rtservlet"
        val CRITICAL_PARAMETERS = setOf(
            "dat", "algorithm", "format", "properties", "serverurl", "rtservlet",
            "stservlet", "id", "key", "fileid", "cop", "deskey", "cipherkey",
        )
    }
}
