package dev.junta.firmamobile.afirma

import android.net.Uri
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.SiteProfile
import java.util.Locale

class AfirmaUriParser {
    fun parse(rawUri: String, origin: TrustedOrigin): AfirmaParseResult {
        val profile = BuiltInSiteProfiles.releaseRegistry.resolve(origin)?.profile
        if (profile == null || Capability.AFIRMA_URI !in profile.capabilities) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.UNTRUSTED_ORIGIN)
        }
        if (rawUri.length > MAX_URI_CHARS) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.URI_TOO_LARGE)
        }

        val uri = try {
            Uri.parse(rawUri)
        } catch (_: Exception) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        }
        if (!uri.scheme.equals(AFIRMA_SCHEME, ignoreCase = true) ||
            uri.isOpaque ||
            uri.encodedUserInfo != null ||
            uri.port != -1 ||
            uri.fragment != null
        ) {
            return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        }
        val operationName = uri.host?.lowercase(Locale.ROOT)
            ?: return AfirmaParseResult.Failure(AfirmaParseErrorCode.INVALID_URI)
        val operation = AfirmaOperation.entries.firstOrNull { it.wireName == operationName }
            ?: return AfirmaParseResult.Failure(AfirmaParseErrorCode.UNSUPPORTED_OPERATION)

        val parameters = when (val parsed = parseParameters(uri.encodedQuery)) {
            is ParameterParseResult.Success -> parsed.parameters
            is ParameterParseResult.Failure -> return AfirmaParseResult.Failure(parsed.code)
        }
        CRITICAL_PARAMETERS.forEach { criticalName ->
            if (parameters[criticalName].orEmpty().size > 1) {
                return AfirmaParseResult.Failure(
                    AfirmaParseErrorCode.DUPLICATE_CRITICAL_PARAMETER,
                )
            }
        }
        if (operation == AfirmaOperation.SIGN &&
            REQUIRED_SIGN_PARAMETERS.any { parameters[it]?.singleOrNull()?.decodedValue.isNullOrBlank() }
        ) {
            return AfirmaParseResult.Failure(
                AfirmaParseErrorCode.MISSING_REQUIRED_PARAMETER,
            )
        }
        CALLBACK_PARAMETERS.forEach { callbackName ->
            parameters[callbackName].orEmpty().forEach { parameter ->
                if (!isSafeCallbackUrl(parameter.decodedValue, profile)) {
                    return AfirmaParseResult.Failure(
                        AfirmaParseErrorCode.UNSAFE_CALLBACK_URL,
                    )
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
            val name = try {
                Uri.decode(encodedName).lowercase(Locale.ROOT)
            } catch (_: Exception) {
                return ParameterParseResult.Failure(AfirmaParseErrorCode.MALFORMED_ENCODING)
            }
            if (!PARAMETER_NAME.matches(name)) {
                return ParameterParseResult.Failure(AfirmaParseErrorCode.INVALID_PARAMETER)
            }
            val decodedValue = try {
                Uri.decode(encodedValue)
            } catch (_: Exception) {
                return ParameterParseResult.Failure(AfirmaParseErrorCode.MALFORMED_ENCODING)
            }
            parameters.getOrPut(name) { mutableListOf() } += AfirmaParameter(
                encodedValue = encodedValue,
                decodedValue = decodedValue,
            )
        }
        return ParameterParseResult.Success(
            parameters.mapValues { (_, values) -> values.toList() },
        )
    }

    private fun isSafeCallbackUrl(rawUrl: String, profile: SiteProfile): Boolean {
        val uri = try {
            Uri.parse(rawUrl)
        } catch (_: Exception) {
            return false
        }
        if (uri.fragment != null) return false
        val callbackOrigin = JuntaOriginPolicy.originFor(uri) ?: return false
        val resolved = BuiltInSiteProfiles.releaseRegistry.resolve(callbackOrigin) ?: return false
        return resolved.profile.profileId == profile.profileId
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
        data class Success(
            val parameters: Map<String, List<AfirmaParameter>>,
        ) : ParameterParseResult

        data class Failure(val code: AfirmaParseErrorCode) : ParameterParseResult
    }

    companion object {
        const val MAX_URI_CHARS = 1024 * 1024
        private const val MAX_PARAMETERS = 64
        private const val AFIRMA_SCHEME = "afirma"
        private val PARAMETER_NAME = Regex("[a-z][a-z0-9_-]{0,63}")
        private val REQUIRED_SIGN_PARAMETERS = setOf("algorithm", "format")
        private val CALLBACK_PARAMETERS = setOf("serverurl", "rtservlet", "stservlet")
        private val CRITICAL_PARAMETERS = setOf(
            "dat",
            "algorithm",
            "format",
            "properties",
            "serverurl",
            "rtservlet",
            "stservlet",
            "id",
            "key",
            "fileid",
            "cop",
            "deskey",
            "cipherkey",
        )
    }
}
