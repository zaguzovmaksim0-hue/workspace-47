package dev.junta.firmamobile.browser

import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

internal enum class WebAuthnEngineState {
    UNSUPPORTED,
    ENABLED_PROVIDER_AUTHORIZATION_UNKNOWN,
    CONFIGURATION_FAILED
}

internal object NativeWebAuthnSupport {
    fun configure(settings: WebSettings): WebAuthnEngineState = try {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebAuthnEngineState.UNSUPPORTED
        } else {
            evaluateWebAuthnConfiguration(featureSupported = true) {
                WebSettingsCompat.setWebAuthenticationSupport(
                    settings,
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
                )
                WebSettingsCompat.getWebAuthenticationSupport(settings)
            }
        }
    } catch (_: RuntimeException) {
        // Also protect the browser against failures during feature detection.
        WebAuthnEngineState.CONFIGURATION_FAILED
    }
}

internal fun evaluateWebAuthnConfiguration(
    featureSupported: Boolean,
    configure: () -> Int
): WebAuthnEngineState {
    if (!featureSupported) return WebAuthnEngineState.UNSUPPORTED

    return try {
        if (configure() == 2) {
            // Native engine configuration does not establish provider authorization.
            WebAuthnEngineState.ENABLED_PROVIDER_AUTHORIZATION_UNKNOWN
        } else {
            WebAuthnEngineState.CONFIGURATION_FAILED
        }
    } catch (_: RuntimeException) {
        WebAuthnEngineState.CONFIGURATION_FAILED
    }
}
