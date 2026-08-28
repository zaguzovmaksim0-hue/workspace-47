package dev.junta.firmamobile.profile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal fun strictClientAuthHttpsUri(raw: String): URI? = runCatching {
    require(raw.length <= CLIENT_AUTH_MAX_URL_CHARS && raw.none(Char::isISOControl))
    val uri = URI(raw)
    require(!uri.isOpaque && uri.scheme == "https" && uri.host != null && uri.userInfo == null)
    require(uri.port == -1 || uri.port in 1..65_535)
    ExactOrigin.parse("https://${uri.host}")
    uri
}.getOrNull()

internal fun ClientAuthPolicy.matchesSourceUrl(uri: URI): Boolean {
    if (uri.rawFragment != null) return false
    if (sourceFixedQueryParameters.isEmpty() && sourceRequiredEphemeralQueryParameters.isEmpty()) {
        return sourceBase64UrlConstraints.isEmpty() && uri in sourceUrls
    }
    val effectivePort = if (uri.port == -1) 443 else uri.port
    val baseMatches = sourceUrls.any { base ->
        val basePort = if (base.port == -1) 443 else base.port
        uri.scheme == "https" && uri.host == base.host && effectivePort == basePort &&
            uri.rawPath == base.rawPath && base.rawQuery == null && base.rawFragment == null
    }
    if (!baseMatches) return false
    val expectedNames = sourceFixedQueryParameters.keys + sourceRequiredEphemeralQueryParameters
    if (expectedNames.isEmpty()) return uri.rawQuery == null && sourceBase64UrlConstraints.isEmpty()
    val parameters = parseClientAuthQuery(uri.rawQuery ?: return false) ?: return false
    if (parameters.keys != expectedNames) return false
    if (sourceFixedQueryParameters.any { (name, value) -> parameters[name] != value }) return false
    if (!sourceRequiredEphemeralQueryParameters.all { name -> validClientAuthEphemeral(name, parameters[name]) }) {
        return false
    }
    return sourceBase64UrlConstraints.all { (parameter, constraint) ->
        val encoded = parameters[parameter] ?: return@all false
        val decoded = decodeStrictBase64Utf8(encoded) ?: return@all false
        val decodedUri = strictClientAuthHttpsUri(decoded) ?: return@all false
        decodedUri.matchesClientAuthConstraint(constraint)
    }
}

internal fun ClientAuthPolicy.matchesRequestUrl(uri: URI): Boolean {
    if (uri.rawPath != requestPath || uri.rawFragment != null) return false
    val effectivePort = if (uri.port == -1) 443 else uri.port
    if (effectivePort != requestPort) return false
    val origin = runCatching { ExactOrigin.parse("https://${uri.host}") }.getOrNull() ?: return false
    if (origin !in requestOrigins) return false
    val expectedNames = fixedQueryParameters.keys + requiredEphemeralQueryParameters
    if (expectedNames.isEmpty()) return uri.rawQuery == null
    val parameters = parseClientAuthQuery(uri.rawQuery ?: return false) ?: return false
    if (parameters.keys != expectedNames) return false
    if (fixedQueryParameters.any { (name, expectedValue) ->
            val actualValue = parameters[name] ?: return false
            !equivalentClientAuthFixedParameter(name, actualValue, expectedValue)
        }
    ) return false
    return requiredEphemeralQueryParameters.all { name -> validClientAuthEphemeral(name, parameters[name]) }
}

internal fun ClientAuthPolicy.matchesReturnUrl(uri: URI): Boolean =
    returnUrlConstraints.any { constraint -> uri.matchesClientAuthConstraint(constraint) }

internal fun ClientAuthPolicy.matchesInPlaceGetTransition(current: URI, target: URI): Boolean =
    transitionMode == ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE &&
        requestMethod == HttpMethod.GET &&
        matchesSourceUrl(current) &&
        matchesRequestUrl(target)

internal fun ClientAuthPolicy.hasLinkedEphemeralParameters(source: URI, target: URI): Boolean {
    if (linkedEphemeralQueryParameters.isEmpty() && linkedEphemeralQueryParameterMappings.isEmpty()) return true
    val sourceParameters = parseClientAuthQuery(source.rawQuery ?: return false) ?: return false
    val targetParameters = parseClientAuthQuery(target.rawQuery ?: return false) ?: return false
    if (linkedEphemeralQueryParameters.any { name -> sourceParameters[name] != targetParameters[name] }) return false
    return linkedEphemeralQueryParameterMappings.all { (sourceName, targetName) ->
        sourceParameters[sourceName] == targetParameters[targetName]
    }
}

private fun URI.matchesClientAuthConstraint(constraint: ClientAuthUrlConstraint): Boolean {
    if (isOpaque || scheme != "https" || host == null || userInfo != null || rawFragment != null) return false
    val effectivePort = if (port == -1) 443 else port
    if (effectivePort != constraint.origin.port) return false
    val origin = runCatching { ExactOrigin.parse("https://$host") }.getOrNull() ?: return false
    if (origin != constraint.origin || rawPath != constraint.path) return false
    val expectedNames = constraint.fixedQueryParameters.keys + constraint.requiredEphemeralQueryParameters
    if (expectedNames.isEmpty()) return rawQuery == null
    val parameters = parseClientAuthQuery(rawQuery ?: return false) ?: return false
    if (parameters.keys != expectedNames) return false
    if (constraint.fixedQueryParameters.any { (name, value) -> parameters[name] != value }) return false
    return constraint.requiredEphemeralQueryParameters.all { name -> validClientAuthEphemeral(name, parameters[name]) }
}

private fun equivalentClientAuthFixedParameter(name: String, actual: String, expected: String): Boolean {
    if (actual == expected) return true
    if (name != "comeBackURL") return false
    val actualDecoded = decodeStrictBase64(actual) ?: return false
    val expectedDecoded = decodeStrictBase64(expected) ?: return false
    return actualDecoded.contentEquals(expectedDecoded)
}

private fun validClientAuthEphemeral(name: String, value: String?): Boolean =
    value != null && value.isNotEmpty() && value.length <= CLIENT_AUTH_MAX_EPHEMERAL_CHARS &&
        value.none(Char::isISOControl) &&
        (name != CLIENT_AUTH_LEON_IDTOKEN_PARAMETER || CLIENT_AUTH_LEON_IDTOKEN.matches(value))

private fun parseClientAuthQuery(rawQuery: String): Map<String, String>? = runCatching {
    if (rawQuery.isEmpty() || rawQuery.length > CLIENT_AUTH_MAX_QUERY_CHARS) return null
    val result = linkedMapOf<String, String>()
    rawQuery.split('&').forEach { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0) return null
        val name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8.name())
        val value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8.name())
        if (!CLIENT_AUTH_PARAMETER_NAME.matches(name) || result.put(name, value) != null) return null
    }
    result
}.getOrNull()

private fun decodeStrictBase64Utf8(input: String): String? {
    val bytes = decodeStrictBase64(input) ?: return null
    val decoded = bytes.toString(StandardCharsets.UTF_8)
    return decoded.takeIf { decoded.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes) }
}

private fun decodeStrictBase64(input: String): ByteArray? {
    if (input.any(Char::isWhitespace) || input.length % 4 == 1) return null
    val normalized = input.replace('-', '+').replace('_', '/')
    val padded = when (normalized.length % 4) {
        2 -> "$normalized=="
        3 -> "$normalized="
        else -> normalized
    }
    return runCatching { java.util.Base64.getDecoder().decode(padded) }.getOrNull()
}

internal const val CLIENT_AUTH_MAX_URL_CHARS = 8_192
internal const val CLIENT_AUTH_MAX_QUERY_CHARS = 4_096
internal const val CLIENT_AUTH_MAX_EPHEMERAL_CHARS = 1_024
internal val CLIENT_AUTH_PARAMETER_NAME = Regex("[A-Za-z][A-Za-z0-9_.]{0,63}")
private const val CLIENT_AUTH_LEON_IDTOKEN_PARAMETER = "idtoken"
private val CLIENT_AUTH_LEON_IDTOKEN = Regex("[0-9]{8}-[A-Za-z0-9_-]{20,64}")
