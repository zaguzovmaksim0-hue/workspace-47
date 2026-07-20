package dev.junta.firmamobile.browser

import android.content.Intent
import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaParseResult
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.afirma.AfirmaUriParser
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import java.util.Locale

enum class NavigationBlockReason {
    INVALID_URL,
    UNTRUSTED_AFIRMA_ORIGIN,
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
            "http" -> NavigationDecision.Block(NavigationBlockReason.INSECURE_HTTP)
            "afirma" -> decideAfirma(targetUrl, currentPageUrl)
            "intent" -> decideIntent(targetUrl, currentPageUrl)
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
        return if (isSafeExternalHttpsUrl(target)) {
            NavigationDecision.OpenExternal(target)
        } else {
            NavigationDecision.Block(NavigationBlockReason.INVALID_URL)
        }
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

    private companion object {
        const val AUTOFIRMA_PACKAGE = "es.gob.afirma"
        const val BROWSER_FALLBACK_URL = "browser_fallback_url"
    }
}
