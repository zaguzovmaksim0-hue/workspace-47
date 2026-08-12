package dev.junta.firmamobile.afirma

import dev.junta.firmamobile.network.TrustedOrigin
import java.util.Locale

enum class AfirmaOperation(val wireName: String) {
    SIGN("sign"),
    SELECT_CERTIFICATE("selectcert"),
    WEBSOCKET("websocket"),
}

data class AfirmaParameter(
    val encodedValue: String,
    val decodedValue: String,
)

data class AfirmaRequest(
    val rawUri: String,
    val origin: TrustedOrigin,
    val operation: AfirmaOperation,
    val parameters: Map<String, List<AfirmaParameter>>,
) {
    fun singleValue(name: String): String? = parameters[
        name.lowercase(Locale.ROOT),
    ]?.singleOrNull()?.decodedValue
}

enum class AfirmaParseErrorCode {
    UNTRUSTED_ORIGIN,
    URI_TOO_LARGE,
    INVALID_URI,
    MALFORMED_ENCODING,
    TOO_MANY_PARAMETERS,
    INVALID_PARAMETER,
    UNSUPPORTED_OPERATION,
    DUPLICATE_CRITICAL_PARAMETER,
    MISSING_REQUIRED_PARAMETER,
    UNSAFE_CALLBACK_URL,
}

sealed interface AfirmaParseResult {
    data class Success(val request: AfirmaRequest) : AfirmaParseResult

    data class Failure(val code: AfirmaParseErrorCode) : AfirmaParseResult
}
