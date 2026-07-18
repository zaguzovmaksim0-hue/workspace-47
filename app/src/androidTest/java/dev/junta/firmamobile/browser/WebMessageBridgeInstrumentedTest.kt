package dev.junta.firmamobile.browser

import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.signing.LocalSignature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
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

    @Test
    fun trustedMiniAppletSignReturnsOneNativeResultWithoutCallingOriginalMethod() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val completed = CountDownLatch(1)
        val nativeCalls = AtomicInteger()
        lateinit var webView: TrustedJuntaWebView
        lateinit var attachment: WebMessageBridgeAttachment

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = TrustedJuntaWebView(context).apply {
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String) {
                        if (title == "PASS") completed.countDown()
                    }
                }
            }
            attachment = WebMessageBridge(
                logger = SanitizedLogger(),
                onAfirmaRequest = {},
                onMiniAppletRequest = { request, reply ->
                    nativeCalls.incrementAndGet()
                    request.normalized.close()
                    reply.success(
                        signature = LocalSignature(byteArrayOf(1, 2, 3)),
                        certificateDer = byteArrayOf(4, 5, 6),
                    )
                },
                miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
                currentOrigin = {
                    TrustedOrigin("https", "www.juntadeandalucia.es", 443)
                },
            ).attach(webView)
            webView.loadDataWithBaseURL(
                "https://www.juntadeandalucia.es/",
                SYNTHETIC_MINIAPPLET_PAGE,
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue("MiniApplet callback did not complete", completed.await(15, TimeUnit.SECONDS))
        assertEquals(1, nativeCalls.get())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            attachment.close()
            webView.destroy()
        }
    }

    @Test
    fun trustedRedSaraAutoScriptAssignmentIsInterceptedWithExactXadesTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val completed = CountDownLatch(1)
        val nativeCalls = AtomicInteger()
        lateinit var webView: TrustedJuntaWebView
        lateinit var attachment: WebMessageBridgeAttachment

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = TrustedJuntaWebView(context).apply {
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String) {
                        if (title == "PASS") completed.countDown()
                    }
                }
            }
            attachment = WebMessageBridge(
                logger = SanitizedLogger(),
                onAfirmaRequest = {},
                onMiniAppletRequest = { request, reply ->
                    nativeCalls.incrementAndGet()
                    assertEquals("reg-age-redsara", request.normalized.context.profileId)
                    request.normalized.close()
                    reply.success(
                        signature = LocalSignature(byteArrayOf(1, 2, 3)),
                        certificateDer = byteArrayOf(4, 5, 6),
                    )
                },
                miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
                currentOrigin = { TrustedOrigin("https", "reg.redsara.es", 443) },
            ).attach(webView)
            webView.loadDataWithBaseURL(
                "https://reg.redsara.es/",
                SYNTHETIC_AUTOSCRIPT_PAGE,
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue("AutoScript callback did not complete", completed.await(15, TimeUnit.SECONDS))
        assertEquals(1, nativeCalls.get())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            attachment.close()
            webView.destroy()
        }
    }

    private companion object {
        const val SYNTHETIC_MINIAPPLET_PAGE = """
            <!doctype html><html><head><title>START</title><script>
            let originalCalls = 0;
            window.MiniApplet = {
              sign: function() { originalCalls += 1; document.title = 'ORIGINAL'; }
            };
            window.addEventListener('DOMContentLoaded', function() {
              window.MiniApplet.sign(
                btoa('synthetic-data'),
                'SHA1withRSA',
                'CAdES',
                'serverUrl=https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService\nmode=explicit',
                function(signatureB64, certificateB64) {
                  document.title = signatureB64 === 'AQID' &&
                    certificateB64 === 'BAUG' && originalCalls === 0 ? 'PASS' : 'FAIL';
                },
                function() { document.title = 'ERROR'; }
              );
            });
            </script></head><body>synthetic</body></html>
        """

        const val SYNTHETIC_AUTOSCRIPT_PAGE = """
            <!doctype html><html><head><title>START</title><script>
            let originalCalls = 0;
            window.AutoScript = {
              sign: function() { originalCalls += 1; document.title = 'ORIGINAL'; }
            };
            window.addEventListener('DOMContentLoaded', function() {
              window.AutoScript.sign(
                btoa('<resumen><dato>synthetic</dato></resumen>'),
                'SHA512withRSA',
                'XAdES Detached',
                null,
                function(signatureB64, certificateB64) {
                  document.title = signatureB64 === 'AQID' &&
                    certificateB64 === 'BAUG' && originalCalls === 0 ? 'PASS' : 'FAIL';
                },
                function() { document.title = 'ERROR'; }
              );
            });
            </script></head><body>synthetic</body></html>
        """
    }
}
