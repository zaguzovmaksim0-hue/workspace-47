package dev.junta.firmamobile.network

import java.net.URI

enum class NetworkUrlError {
    MALFORMED,
    TOO_LONG,
    ORIGIN_NOT_ALLOWED,
    PATH_NOT_ALLOWED,
    URL_METADATA_NOT_ALLOWED,
}

@JvmInline
value class ValidatedNetworkUrl internal constructor(val uri: URI)

sealed interface NetworkUrlValidation {
    data class Allowed(val url: ValidatedNetworkUrl) : NetworkUrlValidation

    data class Blocked(val error: NetworkUrlError) : NetworkUrlValidation
}

class SafeNetworkUrlPolicy {
    fun validateEndpoint(rawUrl: String): NetworkUrlValidation {
        if (rawUrl.length > MAX_ENDPOINT_CHARS) {
            return NetworkUrlValidation.Blocked(NetworkUrlError.TOO_LONG)
        }
        if (rawUrl.any { it.isISOControl() }) {
            return NetworkUrlValidation.Blocked(NetworkUrlError.MALFORMED)
        }
        val parsed = try {
            URI(rawUrl)
        } catch (_: Exception) {
            return NetworkUrlValidation.Blocked(NetworkUrlError.MALFORMED)
        }
        return validate(parsed)
    }

    fun validateRequest(uri: URI): NetworkUrlValidation = validate(uri)

    private fun validate(uri: URI): NetworkUrlValidation {
        if (uri.isOpaque || uri.scheme != HTTPS || uri.host != JUNTA_HOST ||
            (uri.port != -1 && uri.port != HTTPS_PORT)
        ) {
            return NetworkUrlValidation.Blocked(NetworkUrlError.ORIGIN_NOT_ALLOWED)
        }
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            return NetworkUrlValidation.Blocked(NetworkUrlError.URL_METADATA_NOT_ALLOWED)
        }
        if (uri.rawPath != JUNTA_PATH) {
            return NetworkUrlValidation.Blocked(NetworkUrlError.PATH_NOT_ALLOWED)
        }
        return NetworkUrlValidation.Allowed(ValidatedNetworkUrl(CANONICAL_ENDPOINT))
    }

    companion object {
        const val JUNTA_TRIPHASE_ENDPOINT =
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService"
        private const val HTTPS = "https"
        private const val HTTPS_PORT = 443
        private const val JUNTA_HOST = "ws024.juntadeandalucia.es"
        private const val JUNTA_PATH =
            "/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService"
        private const val MAX_ENDPOINT_CHARS = 2_048
        private val CANONICAL_ENDPOINT = URI(JUNTA_TRIPHASE_ENDPOINT)
    }
}
