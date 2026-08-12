package dev.junta.firmamobile.browser

import android.content.Context
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

data class WebViewProfileCapabilities(
    val providerPackage: String,
    val providerVersion: String,
    val multiProfile: Boolean,
    val getCookieInfo: Boolean,
    val webMessageListener: Boolean,
    val documentStartScript: Boolean,
) {
    companion object {
        fun current(context: Context): WebViewProfileCapabilities {
            val packageInfo = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
            return detect(
                providerPackage = packageInfo?.packageName,
                providerVersion = packageInfo?.versionName,
                isFeatureSupported = WebViewFeature::isFeatureSupported,
            )
        }

        internal fun detect(
            providerPackage: String?,
            providerVersion: String?,
            isFeatureSupported: (String) -> Boolean,
        ): WebViewProfileCapabilities = WebViewProfileCapabilities(
            providerPackage = providerPackage?.takeIf(String::isNotBlank) ?: UNAVAILABLE,
            providerVersion = providerVersion?.takeIf(String::isNotBlank) ?: UNAVAILABLE,
            multiProfile = isSupported(WebViewFeature.MULTI_PROFILE, isFeatureSupported),
            getCookieInfo = isSupported(WebViewFeature.GET_COOKIE_INFO, isFeatureSupported),
            webMessageListener = isSupported(WebViewFeature.WEB_MESSAGE_LISTENER, isFeatureSupported),
            documentStartScript = isSupported(WebViewFeature.DOCUMENT_START_SCRIPT, isFeatureSupported),
        )

        private fun isSupported(feature: String, probe: (String) -> Boolean): Boolean =
            runCatching { probe(feature) }.getOrDefault(false)

        private const val UNAVAILABLE = "unavailable"
    }
}
