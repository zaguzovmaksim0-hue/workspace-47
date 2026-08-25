package dev.junta.firmamobile.browser

import android.content.Intent
import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaParseResult
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.afirma.AfirmaUriParser
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import java.util.Locale

enum class NavigationBlockReason {
    INVALID_URL,
    UNTRUSTED_AFIRMA_ORIGIN,
    UNTRUSTED_EXTERNAL_NAVIGATION,
    INVALID_AFIRMA_URI,
    PLAY_STORE_FALLBACK,
    UNSUPPORTED_SCHEME,
    UNSUPPORTED_EXTERNAL_INTENT,
    CROSS_PROFILE_NAVIGATION,
    INSECURE_HTTP,
}

sealed interface NavigationDecision {
    data object AllowInWebView : NavigationDecision

    data class OpenExternal(val uri: Uri) : NavigationDecision

    data class OpenOfficialAutoFirma(val uri: Uri) : NavigationDecision

    data class UpgradeToHttps(val uri: Uri) : NavigationDecision

    data class HandleAfirma(val request: AfirmaRequest) : NavigationDecision

    data class Block(val reason: NavigationBlockReason) : NavigationDecision
}

class JuntaNavigationPolicy(
    private val selectedProfileId: ProfileId,
    private val registry: SiteProfileRegistry = BuiltInSiteProfiles.runtimeRegistry,
    private val afirmaUriParser: AfirmaUriParser = AfirmaUriParser(),
) {
    init {
        require(registry.profile(selectedProfileId) != null) {
            "Selected navigation profile is not active: ${selectedProfileId.value}"
        }
    }

    fun decide(targetUrl: String, currentPageUrl: String?): NavigationDecision {
        if (targetUrl.length > AfirmaUriParser.MAX_URI_CHARS) {
            return NavigationDecision.Block(NavigationBlockReason.INVALID_URL)
        }
        val target = try {
            Uri.parse(targetUrl)
        } catch (_: Exception) {
            return NavigationDecision.Block(NavigationBlockReason.INVALID_URL)
        }
        return when (target.scheme?.lowercase(Locale.ROOT)) {
            "https" -> decideHttpsUrl(target, targetUrl)
            "http" -> decideLegacyHttpUpgrade(target, currentPageUrl)
            "afirma" -> if (selectedProfileId == SEGURIDAD_SOCIAL_AUTOFIRMA_PROFILE_ID) {
                decideOfficialAutoFirmaUri(targetUrl, currentPageUrl)
            } else {
                decideAfirma(targetUrl, currentPageUrl)
            }
            "intent" -> if (selectedProfileId == SEGURIDAD_SOCIAL_AUTOFIRMA_PROFILE_ID) {
                decideOfficialAutoFirmaIntent(targetUrl, currentPageUrl)
            } else {
                decideIntent(targetUrl, currentPageUrl)
            }
            "market" -> if (isAutoFirmaPlayStoreUrl(target, targetUrl)) {
                NavigationDecision.Block(NavigationBlockReason.PLAY_STORE_FALLBACK)
            } else {
                NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME)
            }
            else -> NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME)
        }
    }

    private fun decideHttpsUrl(target: Uri, rawUrl: String): NavigationDecision {
        if (isAutoFirmaPlayStoreUrl(target, rawUrl)) {
            return NavigationDecision.Block(NavigationBlockReason.PLAY_STORE_FALLBACK)
        }
        if (JuntaOriginPolicy.isAllowed(target, selectedProfileId)) {
            return NavigationDecision.AllowInWebView
        }
        val otherProfile = registry.resolve(target)?.profile?.profileId
        if (otherProfile != null && otherProfile != selectedProfileId) {
            return NavigationDecision.Block(NavigationBlockReason.CROSS_PROFILE_NAVIGATION)
        }
        if (isClientAuthRequestOrigin(target)) {
            return NavigationDecision.Block(NavigationBlockReason.CROSS_PROFILE_NAVIGATION)
        }
        return if (isSafeExternalHttpsUrl(target)) {
            NavigationDecision.OpenExternal(target)
        } else {
            NavigationDecision.Block(NavigationBlockReason.INVALID_URL)
        }
    }

    private fun isClientAuthRequestOrigin(target: Uri): Boolean {
        val profile = registry.profile(selectedProfileId) ?: return false
        val requestOrigins = profile.clientAuthPolicy?.requestOrigins ?: return false
        if (requestOrigins.isEmpty()) return false
        val targetOrigin = exactOriginOf(target) ?: return false
        return targetOrigin in requestOrigins
    }

    private fun exactOriginOf(target: Uri): ExactOrigin? {
        if (target.isOpaque || !target.scheme.equals("https", ignoreCase = true)) return null
        if (target.encodedUserInfo != null) return null
        val rawHost = target.host ?: return null
        if (rawHost.isBlank()) return null
        val port = try {
            target.port.takeIf { it != -1 } ?: 443
        } catch (_: Exception) {
            return null
        }
        if (port != 443) return null
        val canonicalHost = try {
            java.net.IDN.toASCII(rawHost, java.net.IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: Exception) {
            return null
        }
        return runCatching { ExactOrigin.parse("https://$canonicalHost") }.getOrNull()
    }

    private fun decideLegacyHttpUpgrade(
        target: Uri,
        currentPageUrl: String?,
    ): NavigationDecision {
        val blocked = NavigationDecision.Block(NavigationBlockReason.INSECURE_HTTP)
        if (selectedProfileId != OFVIRTUAL_PROFILE_ID || target.isOpaque ||
            target.encodedUserInfo != null || target.port !in setOf(-1, 80) ||
            !target.host.equals(OFVIRTUAL_HOST, ignoreCase = true) ||
            !isExactOfvirtualPath(target)
        ) {
            return blocked
        }
        val current = currentPageUrl?.let { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()
        } ?: return blocked
        if (current.isOpaque || !current.scheme.equals("https", ignoreCase = true) ||
            current.encodedUserInfo != null || current.port !in setOf(-1, 443) ||
            !current.host.equals(OFVIRTUAL_HOST, ignoreCase = true) ||
            !isExactOfvirtualPath(current)
        ) {
            return blocked
        }
        val upgraded = target.buildUpon()
            .scheme("https")
            .encodedAuthority(OFVIRTUAL_HOST)
            .build()
        return if (JuntaOriginPolicy.isAllowed(upgraded, selectedProfileId)) {
            NavigationDecision.UpgradeToHttps(upgraded)
        } else {
            blocked
        }
    }

    private fun isExactOfvirtualPath(uri: Uri): Boolean {
        val encodedPath = uri.encodedPath ?: return false
        val decodedPath = uri.path ?: return false
        if (!encodedPath.startsWith(OFVIRTUAL_PATH_PREFIX) ||
            !decodedPath.startsWith(OFVIRTUAL_PATH_PREFIX) ||
            encodedPath.contains('\\') || decodedPath.contains('\\') ||
            encodedPath.contains("%2f", ignoreCase = true) ||
            encodedPath.contains("%5c", ignoreCase = true)
        ) {
            return false
        }
        return decodedPath.split('/').none { segment -> segment == "." || segment == ".." }
    }

    private fun decideAfirma(rawUrl: String, currentPageUrl: String?): NavigationDecision {
        val origin = currentPageUrl?.let { current ->
            try {
                JuntaOriginPolicy.signingOriginFor(Uri.parse(current), selectedProfileId)
            } catch (_: Exception) {
                null
            }
        } ?: return NavigationDecision.Block(
            NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN,
        )
        return when (val result = afirmaUriParser.parse(rawUrl, origin)) {
            is AfirmaParseResult.Success -> NavigationDecision.HandleAfirma(result.request)
            is AfirmaParseResult.Failure -> NavigationDecision.Block(
                NavigationBlockReason.INVALID_AFIRMA_URI,
            )
        }
    }

    private fun decideOfficialAutoFirmaIntent(
        rawUrl: String,
        currentPageUrl: String?,
    ): NavigationDecision {
        if (!isSeguridadSocialAutoFirmaSource(currentPageUrl)) {
            return NavigationDecision.Block(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN)
        }
        val intent = try {
            Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            return NavigationDecision.Block(NavigationBlockReason.INVALID_URL)
        }
        if (!intent.`package`.equals(AUTOFIRMA_PACKAGE, ignoreCase = false) ||
            intent.component != null || intent.selector != null ||
            intent.getStringExtra(BROWSER_FALLBACK_URL) != null
        ) {
            return NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_EXTERNAL_INTENT)
        }
        val data = intent.dataString
            ?: return NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI)
        return decideOfficialAutoFirmaUri(data, currentPageUrl)
    }

    private fun decideOfficialAutoFirmaUri(
        rawUrl: String,
        currentPageUrl: String?,
    ): NavigationDecision {
        if (!isSeguridadSocialAutoFirmaSource(currentPageUrl)) {
            return NavigationDecision.Block(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN)
        }
        if (rawUrl.length > AfirmaUriParser.MAX_URI_CHARS) {
            return NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI)
        }
        val uri = try {
            Uri.parse(rawUrl)
        } catch (_: Exception) {
            return NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI)
        }
        if (!uri.scheme.equals("afirma", ignoreCase = true) ||
            !uri.host.equals("sign", ignoreCase = true) ||
            uri.isOpaque || uri.encodedUserInfo != null || uri.port != -1 || uri.fragment != null ||
            uri.encodedQuery.isNullOrBlank() ||
            !hasSafeOfficialAutoFirmaQuery(uri.encodedQuery.orEmpty())
        ) {
            return NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI)
        }
        return NavigationDecision.OpenOfficialAutoFirma(uri)
    }

    private fun hasSafeOfficialAutoFirmaQuery(encodedQuery: String): Boolean {
        val segments = encodedQuery.split('&')
        if (segments.isEmpty() || segments.size > 64) return false
        val values = linkedMapOf<String, MutableList<String>>()
        for (segment in segments) {
            if (segment.isEmpty()) return false
            val separator = segment.indexOf('=')
            val encodedName = if (separator == -1) segment else segment.substring(0, separator)
            val encodedValue = if (separator == -1) "" else segment.substring(separator + 1)
            if (!hasValidPercentEncoding(encodedName) || !hasValidPercentEncoding(encodedValue)) return false
            val name = runCatching { Uri.decode(encodedName).lowercase(Locale.ROOT) }.getOrNull() ?: return false
            if (!AFIRMA_PARAMETER_NAME.matches(name) || name !in AFIRMA_ALLOWED_PARAMETERS) return false
            val value = runCatching { Uri.decode(encodedValue) }.getOrNull() ?: return false
            values.getOrPut(name) { mutableListOf() } += value
        }
        AFIRMA_SINGLE_VALUE_PARAMETERS.forEach { name ->
            if (values[name].orEmpty().size > 1) return false
        }
        val algorithm = values["algorithm"]?.singleOrNull()
        val format = values["format"]?.singleOrNull()
        if (algorithm.isNullOrBlank() || format.isNullOrBlank()) return false
        val hasInlineData = !values["dat"]?.singleOrNull().isNullOrBlank()
        val hasServerData = !values["fileid"]?.singleOrNull().isNullOrBlank()
        if (hasInlineData == hasServerData) return false
        AFIRMA_HTTPS_URL_PARAMETERS.forEach { name ->
            values[name]?.singleOrNull()?.let { value ->
                if (!isStrictExternalHttpsUrl(value)) return false
            }
        }
        return true
    }

    private fun isStrictExternalHttpsUrl(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        return !uri.isOpaque &&
            uri.scheme.equals("https", ignoreCase = true) &&
            uri.encodedUserInfo == null &&
            !uri.host.isNullOrBlank() &&
            uri.port in setOf(-1, 443) &&
            uri.fragment == null
    }

    private fun hasValidPercentEncoding(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                if (index + 2 >= value.length ||
                    value[index + 1].digitToIntOrNull(16) == null ||
                    value[index + 2].digitToIntOrNull(16) == null
                ) return false
                index += 3
            } else {
                index++
            }
        }
        return true
    }

    private fun isSeguridadSocialAutoFirmaSource(rawUrl: String?): Boolean {
        val uri = rawUrl?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        val encodedPath = uri.encodedPath.orEmpty()
        if (uri.isOpaque || !uri.scheme.equals("https", ignoreCase = true) ||
            uri.encodedUserInfo != null || uri.port !in setOf(-1, 443) ||
            !uri.host.equals(SEGURIDAD_SOCIAL_HOST, ignoreCase = true) ||
            !encodedPath.startsWith(SEGURIDAD_SOCIAL_MYPORTAL_PATH) ||
            encodedPath.contains('\\') ||
            encodedPath.contains("%2f", ignoreCase = true) ||
            encodedPath.contains("%5c", ignoreCase = true) ||
            uri.queryParameterNames != SEGURIDAD_SOCIAL_QUERY_NAMES
        ) return false
        return exactSingleQueryValue(uri, "A") == "" &&
            exactSingleQueryValue(uri, "N3") == "" &&
            exactSingleQueryValue(uri, "idApp") == SEGURIDAD_SOCIAL_ID_APP &&
            exactSingleQueryValue(uri, "idContenido") == SEGURIDAD_SOCIAL_ID_CONTENIDO &&
            exactSingleQueryValue(uri, "idPagina") == SEGURIDAD_SOCIAL_ID_PAGINA
    }

    private fun exactSingleQueryValue(uri: Uri, name: String): String? = try {
        uri.getQueryParameters(name).singleOrNull()
    } catch (_: Exception) {
        null
    }

    private fun decideIntent(rawUrl: String, currentPageUrl: String?): NavigationDecision {
        val trustedOrigin = currentPageUrl?.let { current ->
            try {
                JuntaOriginPolicy.signingOriginFor(Uri.parse(current), selectedProfileId)
            } catch (_: Exception) {
                null
            }
        } ?: return NavigationDecision.Block(
            NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN,
        )
        val intent = try {
            Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            return NavigationDecision.Block(NavigationBlockReason.INVALID_URL)
        }
        val fallback = intent.getStringExtra(BROWSER_FALLBACK_URL)
        val candidates = listOfNotNull(intent.dataString, fallback)
        candidates.firstOrNull { candidate ->
            candidate.startsWith("afirma:", ignoreCase = true)
        }?.let { afirmaUrl ->
            return when (val result = afirmaUriParser.parse(afirmaUrl, trustedOrigin)) {
                is AfirmaParseResult.Success -> NavigationDecision.HandleAfirma(result.request)
                is AfirmaParseResult.Failure -> NavigationDecision.Block(
                    NavigationBlockReason.INVALID_AFIRMA_URI,
                )
            }
        }

        if (intent.`package`.equals(AUTOFIRMA_PACKAGE, ignoreCase = true) ||
            candidates.any { candidate ->
                val candidateUri = try {
                    Uri.parse(candidate)
                } catch (_: Exception) {
                    null
                }
                candidateUri != null && isAutoFirmaPlayStoreUrl(candidateUri, candidate)
            } ||
            rawUrl.contains(AUTOFIRMA_PACKAGE, ignoreCase = true)
        ) {
            return NavigationDecision.Block(NavigationBlockReason.PLAY_STORE_FALLBACK)
        }

        fallback?.let { fallbackUrl ->
            val fallbackUri = try {
                Uri.parse(fallbackUrl)
            } catch (_: Exception) {
                null
            }
            if (fallbackUri != null && isSafeExternalHttpsUrl(fallbackUri)) {
                return NavigationDecision.OpenExternal(fallbackUri)
            }
        }
        return NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_EXTERNAL_INTENT)
    }

    private fun isSafeExternalHttpsUrl(uri: Uri): Boolean =
        !uri.isOpaque &&
            uri.encodedUserInfo == null &&
            !uri.host.isNullOrBlank() &&
            uri.scheme.equals("https", ignoreCase = true) &&
            uri.port in setOf(-1, 443)

    private fun isAutoFirmaPlayStoreUrl(uri: Uri, rawUrl: String): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        val storeLocation = scheme == "market" ||
            host == "play.google.com" ||
            host == "market.android.com"
        if (!storeLocation) return false
        val packageId = try {
            uri.getQueryParameter("id")
        } catch (_: Exception) {
            null
        }
        return packageId.equals(AUTOFIRMA_PACKAGE, ignoreCase = true) ||
            rawUrl.contains(AUTOFIRMA_PACKAGE, ignoreCase = true) ||
            rawUrl.contains("autofirma", ignoreCase = true)
    }

    companion object {
        internal const val AUTOFIRMA_PACKAGE = "es.gob.afirma"
        private val SEGURIDAD_SOCIAL_AUTOFIRMA_PROFILE_ID = ProfileId("seguridad-social-sede-autofirma")
        private const val SEGURIDAD_SOCIAL_HOST = "sede.seg-social.gob.es"
        private const val SEGURIDAD_SOCIAL_MYPORTAL_PATH = "/wps/myportal/sede/"
        private const val SEGURIDAD_SOCIAL_ID_APP = "826"
        private const val SEGURIDAD_SOCIAL_ID_CONTENIDO = "a061f401-c3ed-426e-9428-82bd9198c223"
        private const val SEGURIDAD_SOCIAL_ID_PAGINA = "com.ss.sede.RegistroElectronicoDeApoderamiento"
        private val SEGURIDAD_SOCIAL_QUERY_NAMES = setOf("A", "N3", "idApp", "idContenido", "idPagina")
        private val AFIRMA_PARAMETER_NAME = Regex("[a-z][a-z0-9_-]{0,63}")
        private val AFIRMA_ALLOWED_PARAMETERS = setOf(
            "op", "algorithm", "format", "dat", "fileid", "id", "key", "properties",
            "stservlet", "rtservlet", "serverurl", "ver", "sticky", "resetsticky", "filename",
            "cop", "deskey", "cipherkey",
        )
        private val AFIRMA_SINGLE_VALUE_PARAMETERS = AFIRMA_ALLOWED_PARAMETERS
        private val AFIRMA_HTTPS_URL_PARAMETERS = setOf("stservlet", "rtservlet", "serverurl")
        private const val BROWSER_FALLBACK_URL = "browser_fallback_url"
        private const val OFVIRTUAL_HOST = "ws072.juntadeandalucia.es"
        private const val OFVIRTUAL_PATH_PREFIX = "/ofvirtual/"
        private val OFVIRTUAL_PROFILE_ID = ProfileId("junta-ofvirtual")
    }
}
