package dev.junta.firmamobile.browser

import android.content.Context
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowJsResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class JuntaWebChromeClientTest {
    @Test
    fun alertIsHandledWithoutPlatformDefaultDialog() {
        withWebView { webView ->
            val result = newJsResult()

            assertTrue(
                JuntaWebChromeClient().onJsAlert(
                    webView,
                    "https://example.invalid/",
                    "secret",
                    result,
                ),
            )
        }
    }

    @Test
    fun beforeUnloadIsHandledWithoutPlatformDefaultDialog() {
        withWebView { webView ->
            val result = newJsResult()

            assertTrue(
                JuntaWebChromeClient().onJsBeforeUnload(
                    webView,
                    "https://example.invalid/",
                    "secret",
                    result,
                ),
            )
        }
    }

    @Test
    fun confirmIsHandledAndDenied() {
        withWebView { webView ->
            val result = newJsResult()

            assertTrue(
                JuntaWebChromeClient().onJsConfirm(
                    webView,
                    "https://example.invalid/",
                    "secret",
                    result,
                ),
            )
            assertTrue(Shadow.extract<ShadowJsResult>(result).wasCancelled())
        }
    }

    @Test
    fun promptIsHandledAndDenied() {
        withWebView { webView ->
            val result = newJsPromptResult()

            assertTrue(
                JuntaWebChromeClient().onJsPrompt(
                    webView,
                    "https://example.invalid/",
                    "secret",
                    "default",
                    result,
                ),
            )
            assertTrue(Shadow.extract<ShadowJsResult>(result).wasCancelled())
        }
    }

    private fun withWebView(block: (WebView) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = WebView(context)
        try {
            block(webView)
        } finally {
            webView.destroy()
        }
    }

    private fun newJsResult(): JsResult = newResult(JsResult::class.java)

    private fun newJsPromptResult(): JsPromptResult = newResult(JsPromptResult::class.java)

    private fun <T : JsResult> newResult(type: Class<T>): T {
        val receiverType = Class.forName("android.webkit.JsResult\$ResultReceiver")
        val receiver = Proxy.newProxyInstance(
            receiverType.classLoader,
            arrayOf(receiverType),
        ) { _, _, _ -> null }
        return type.getDeclaredConstructor(receiverType).apply { isAccessible = true }
            .newInstance(receiver)
    }
}
