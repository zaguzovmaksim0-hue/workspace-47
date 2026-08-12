package dev.junta.firmamobile.browser

import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.LocalSignature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
                profileId = JUNTA_PROFILE,
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
                profileId = JUNTA_PROFILE,
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
                activeProfileId = { JUNTA_PROFILE },
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
    fun identicalInFlightOfvirtualSignIsCoalescedWithoutPortalErrorCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nativeRequestReceived = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val nativeCalls = AtomicInteger()
        val finalTitle = AtomicReference<String>()
        lateinit var pendingRequest: MiniAppletBridgeRequest
        lateinit var pendingReply: MiniAppletReplyChannel
        lateinit var webView: TrustedJuntaWebView
        lateinit var attachment: WebMessageBridgeAttachment

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = TrustedJuntaWebView(context).apply {
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String) {
                        if (title == "PASS" || title == "DUPLICATE_ERROR") {
                            finalTitle.set(title)
                            completed.countDown()
                        }
                    }
                }
            }
            attachment = WebMessageBridge(
                profileId = OFVIRTUAL_PROFILE,
                logger = SanitizedLogger(),
                onAfirmaRequest = {},
                onMiniAppletRequest = { request, reply ->
                    nativeCalls.incrementAndGet()
                    pendingRequest = request
                    pendingReply = reply
                    nativeRequestReceived.countDown()
                },
                activeProfileId = { OFVIRTUAL_PROFILE },
                miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
                currentOrigin = {
                    TrustedOrigin("https", "ws072.juntadeandalucia.es", 443)
                },
            ).attach(webView)
            webView.loadDataWithBaseURL(
                "https://ws072.juntadeandalucia.es/",
                SYNTHETIC_DUPLICATE_OFVIRTUAL_PAGE,
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue(
            "The first MiniApplet request was not delivered",
            nativeRequestReceived.await(15, TimeUnit.SECONDS),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pendingRequest.normalized.close()
            pendingReply.success(
                signature = LocalSignature(byteArrayOf(1, 2, 3)),
                certificateDer = byteArrayOf(4, 5, 6),
            )
        }

        assertTrue("The portal callback did not complete", completed.await(15, TimeUnit.SECONDS))
        assertEquals("PASS", finalTitle.get())
        assertEquals(1, nativeCalls.get())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            attachment.close()
            webView.destroy()
        }
    }

    @Test
    fun conflictingInFlightOfvirtualSignStillFailsClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nativeRequestReceived = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val nativeCalls = AtomicInteger()
        val finalTitle = AtomicReference<String>()
        lateinit var pendingRequest: MiniAppletBridgeRequest
        lateinit var pendingReply: MiniAppletReplyChannel
        lateinit var webView: TrustedJuntaWebView
        lateinit var attachment: WebMessageBridgeAttachment

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = TrustedJuntaWebView(context).apply {
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String) {
                        if (title == "CONFLICT_REJECTED") {
                            finalTitle.set(title)
                            completed.countDown()
                        }
                    }
                }
            }
            attachment = WebMessageBridge(
                profileId = OFVIRTUAL_PROFILE,
                logger = SanitizedLogger(),
                onAfirmaRequest = {},
                onMiniAppletRequest = { request, reply ->
                    nativeCalls.incrementAndGet()
                    pendingRequest = request
                    pendingReply = reply
                    nativeRequestReceived.countDown()
                },
                activeProfileId = { OFVIRTUAL_PROFILE },
                miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
                currentOrigin = {
                    TrustedOrigin("https", "ws072.juntadeandalucia.es", 443)
                },
            ).attach(webView)
            webView.loadDataWithBaseURL(
                "https://ws072.juntadeandalucia.es/",
                SYNTHETIC_CONFLICTING_OFVIRTUAL_PAGE,
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue(
            "The first MiniApplet request was not delivered",
            nativeRequestReceived.await(15, TimeUnit.SECONDS),
        )
        assertTrue("The conflicting request was not rejected", completed.await(15, TimeUnit.SECONDS))
        assertEquals("CONFLICT_REJECTED", finalTitle.get())
        assertEquals(1, nativeCalls.get())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pendingRequest.normalized.close()
            pendingReply.abandon()
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
                profileId = RED_SARA_PROFILE,
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
                activeProfileId = { RED_SARA_PROFILE },
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

    @Test
    fun trustedUnizarAutoScriptAssignmentIsInterceptedWithExactLegacyChallengeTuple() {
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
                profileId = UNIZAR_PROFILE,
                logger = SanitizedLogger(),
                onAfirmaRequest = {},
                onMiniAppletRequest = { request, reply ->
                    nativeCalls.incrementAndGet()
                    assertEquals("unizar-tramitador", request.normalized.context.profileId)
                    request.normalized.close()
                    reply.success(
                        signature = LocalSignature(byteArrayOf(1, 2, 3)),
                        certificateDer = byteArrayOf(4, 5, 6),
                    )
                },
                activeProfileId = { UNIZAR_PROFILE },
                miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
                currentOrigin = { TrustedOrigin("https", "tramita.unizar.es", 443) },
            ).attach(webView)
            webView.loadDataWithBaseURL(
                "https://tramita.unizar.es/",
                SYNTHETIC_UNIZAR_AUTOSCRIPT_PAGE,
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue("UniZAR AutoScript callback did not complete", completed.await(15, TimeUnit.SECONDS))
        assertEquals(1, nativeCalls.get())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            attachment.close()
            webView.destroy()
        }
    }

    @Test
    fun foreignSigningOriginCannotUseAJuntaScopedBridge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val completed = CountDownLatch(1)
        val nativeCalls = AtomicInteger()
        lateinit var webView: TrustedJuntaWebView
        lateinit var attachment: WebMessageBridgeAttachment

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = TrustedJuntaWebView(context).apply {
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String) {
                        if (title == "ORIGINAL") completed.countDown()
                    }
                }
            }
            attachment = WebMessageBridge(
                profileId = JUNTA_PROFILE,
                logger = SanitizedLogger(),
                onAfirmaRequest = {},
                onMiniAppletRequest = { request, _ ->
                    nativeCalls.incrementAndGet()
                    request.normalized.close()
                },
                activeProfileId = { JUNTA_PROFILE },
                miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
                currentOrigin = {
                    TrustedOrigin("https", "www.juntadeandalucia.es", 443)
                },
            ).attach(webView)
            webView.loadDataWithBaseURL(
                "https://reg.redsara.es/",
                SYNTHETIC_AUTOSCRIPT_PAGE,
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue("Foreign origin executed the native bridge", completed.await(15, TimeUnit.SECONDS))
        assertEquals(0, nativeCalls.get())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            attachment.close()
            webView.destroy()
        }
    }

    private companion object {
        val JUNTA_PROFILE = ProfileId("junta-andalucia")
        val OFVIRTUAL_PROFILE = ProfileId("junta-ofvirtual")
        val RED_SARA_PROFILE = ProfileId("reg-age-redsara")
        val UNIZAR_PROFILE = ProfileId("unizar-tramitador")

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
                'serverUrl=https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService\nfilters=keyusage.digitalsignature:true;nonexpired:',
                function(signatureB64, certificateB64) {
                  document.title = signatureB64 === 'AQID' &&
                    certificateB64 === 'BAUG' && originalCalls === 0 ? 'PASS' : 'FAIL';
                },
                function() { document.title = 'ERROR'; }
              );
            });
            </script></head><body>synthetic</body></html>
        """

        const val SYNTHETIC_DUPLICATE_OFVIRTUAL_PAGE = """
            <!doctype html><html><head><title>START</title><script>
            window.MiniApplet = { sign: function() { document.title = 'ORIGINAL'; } };
            let errorCalls = 0;
            function onSuccess(signatureB64, certificateB64) {
              document.title = signatureB64 === 'AQID' && certificateB64 === 'BAUG' &&
                errorCalls === 0 ? 'PASS' : 'DUPLICATE_ERROR';
            }
            function onError() { errorCalls += 1; }
            window.addEventListener('DOMContentLoaded', function() {
              const args = [
                btoa('-8867827660538134267'),
                'SHA1withRSA',
                'CAdES',
                'filters=keyusage.digitalsignature:true;nonexpired:\n' +
                  'serverUrl=https://ws024.juntadeandalucia.es/' +
                  'afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService',
                onSuccess,
                onError
              ];
              window.MiniApplet.sign(...args);
              window.MiniApplet.sign(...args);
            });
            </script></head><body>synthetic duplicate</body></html>
        """

        const val SYNTHETIC_CONFLICTING_OFVIRTUAL_PAGE = """
            <!doctype html><html><head><title>START</title><script>
            window.MiniApplet = { sign: function() { document.title = 'ORIGINAL'; } };
            function onSuccess() { document.title = 'UNEXPECTED_SUCCESS'; }
            function onError(errorCode) {
              document.title = errorCode === 'PROTOCOL_FAILED' ?
                'CONFLICT_REJECTED' : 'WRONG_ERROR';
            }
            window.addEventListener('DOMContentLoaded', function() {
              const common = [
                'SHA1withRSA',
                'CAdES',
                'filters=keyusage.digitalsignature:true;nonexpired:\n' +
                  'serverUrl=https://ws024.juntadeandalucia.es/' +
                  'afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService',
                onSuccess,
                onError
              ];
              window.MiniApplet.sign(btoa('-8867827660538134267'), ...common);
              window.MiniApplet.sign(btoa('-8867827660538134268'), ...common);
            });
            </script></head><body>synthetic conflict</body></html>
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

        const val SYNTHETIC_UNIZAR_AUTOSCRIPT_PAGE = """
            <!doctype html><html><head><title>START</title><script>
            let originalCalls = 0;
            window.AutoScript = {
              sign: function() { originalCalls += 1; document.title = 'ORIGINAL'; }
            };
            window.addEventListener('DOMContentLoaded', function() {
              window.AutoScript.sign(
                btoa('12345678901234567890'),
                'SHA1withRSA',
                'CAdES',
                'precalculatedHashAlgorithm=SHA1\nserverUrl=https://tramita.unizar.es/afirma-server-triphase-signer-2.7.3/SignatureService',
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
