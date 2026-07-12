package dev.junta.firmamobile.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.security.SanitizedLogger
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebMessageBridgeInstrumentedTest {
    @Test
    fun attachesOnlyTheOriginScopedWebKitBridgeAndDocumentStartScript() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = TrustedJuntaWebView(context)
            val attachment = WebMessageBridge(
                logger = SanitizedLogger(),
                onAfirmaRequest = {},
            ).attach(webView)

            assertTrue(attachment.listenerAttached)
            assertTrue(attachment.documentStartScriptAttached)

            attachment.close()
            webView.destroy()
        }
    }
}
