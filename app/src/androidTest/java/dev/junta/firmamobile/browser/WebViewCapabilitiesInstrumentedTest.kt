package dev.junta.firmamobile.browser

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewCapabilitiesInstrumentedTest {
    @Test
    fun recordsOnlyProviderMetadataAndFeatureBooleansWithoutLaunchingUi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val capabilities = WebViewProfileCapabilities.current(
            ApplicationProvider.getApplicationContext(),
        )
        val status = Bundle().apply {
            putString("webview_provider_package", capabilities.providerPackage)
            putString("webview_provider_version", capabilities.providerVersion)
            putString("webview_multi_profile", capabilities.multiProfile.toString())
            putString("webview_get_cookie_info", capabilities.getCookieInfo.toString())
            putString("webview_message_listener", capabilities.webMessageListener.toString())
            putString("webview_document_start_script", capabilities.documentStartScript.toString())
        }

        instrumentation.sendStatus(0, status)

        assertTrue(capabilities.providerPackage.isNotBlank())
        assertTrue(capabilities.providerVersion.isNotBlank())
        status.keySet().forEach { key ->
            val value = status.getString(key).orEmpty()
            assertFalse(value.contains("http", ignoreCase = true))
            assertFalse(value.contains("cookie=", ignoreCase = true))
        }
    }
}
