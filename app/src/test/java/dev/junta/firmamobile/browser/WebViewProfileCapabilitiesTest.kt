package dev.junta.firmamobile.browser

import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewProfileCapabilitiesTest {
    @Test
    fun detectorReportsProviderAndEachFeatureWithoutInferringTrust() {
        val supported = setOf(
            WebViewFeature.MULTI_PROFILE,
            WebViewFeature.WEB_MESSAGE_LISTENER,
        )

        val capabilities = WebViewProfileCapabilities.detect(
            providerPackage = "com.example.webview",
            providerVersion = "148.0.1",
            isFeatureSupported = supported::contains,
        )

        assertEquals("com.example.webview", capabilities.providerPackage)
        assertEquals("148.0.1", capabilities.providerVersion)
        assertTrue(capabilities.multiProfile)
        assertFalse(capabilities.getCookieInfo)
        assertTrue(capabilities.webMessageListener)
        assertFalse(capabilities.documentStartScript)
    }

    @Test
    fun missingProviderMetadataAndFeatureProbeFailuresStayConservative() {
        val capabilities = WebViewProfileCapabilities.detect(
            providerPackage = null,
            providerVersion = null,
            isFeatureSupported = { feature ->
                if (feature == WebViewFeature.GET_COOKIE_INFO) error("provider unavailable")
                false
            },
        )

        assertEquals("unavailable", capabilities.providerPackage)
        assertEquals("unavailable", capabilities.providerVersion)
        assertFalse(capabilities.multiProfile)
        assertFalse(capabilities.getCookieInfo)
        assertFalse(capabilities.webMessageListener)
        assertFalse(capabilities.documentStartScript)
    }
}
