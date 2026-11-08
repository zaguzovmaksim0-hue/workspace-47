package dev.junta.firmamobile

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Local documents only: every resource is intercepted and no certificate or network is used. */
@RunWith(AndroidJUnit4::class)
class RealE2eWaveFRecipeInstrumentedTest {
    @Test
    fun boeCertificateAnchorRequiresUniquePinnedImage() {
        val anchor = """
            <a href="https://extranet.boe.es/id/check-cert.php?referer=https://extranet.boe.es/anuncios/">
              <img name="certificado" alt="Identificación con certificado electrónico"
                src="/imagenes/certificado_txt.png">
            </a>
        """.trimIndent()
        val rejected = listOf(
            anchor + anchor,
            anchor.replace("electrónico", "incorrecto"),
            anchor.replace("<a ", "<a target='_blank' "),
            anchor.replace("<a ", "<a onclick='window.fixtureClicks++' "),
            anchor.replace("certificado_txt.png", "otro.png"),
            anchor.replace("<img ", "<img src='extra.png'><img "),
            anchor.replace("</a>", "Cl@ve</a>"),
        )
        (listOf(anchor) + rejected).forEachIndexed { index, content ->
            val html = """
                <html><body>$content<script>
                window.fixtureClicks = 0;
                document.querySelectorAll('a').forEach(a => a.addEventListener('click', e => {
                  window.fixtureClicks++; e.preventDefault();
                }));
                </script></body></html>
            """.trimIndent()
            checkFixture("https://extranet.boe.es/anuncios/", html, "", boeCertificateAnchorScript(), index == 0)
        }
        checkFixture("https://extranet.boe.es/other/", anchor, "", boeCertificateAnchorScript(), false)
    }

    @Test
    fun cmtOptionRequiresSameOriginFrame() {
        val source = "https://sede.cmt.gob.es/carpetaciudadana/login.aspx?returnUrl=" +
            "https%3a%2f%2fsede.cmt.gob.es%2fcarpetaciudadana%2fcarpeta_ciudadana.aspx"
        val label = "Identificarse con certificado digital a través de nuestro servidor"
        val option = """
            <table><tbody id="optSsl" onclick="parent.fixtureClicks++"><tr><td>
              <img alt="$label"><div class="titulo-opcion"><span>$label</span></div>
            </td></tr></tbody></table>
        """.trimIndent()
        val path = "/segex/identificacion_opciones.aspx"
        val frame = "<iframe id='ventanaModal' src='https://sede.cmt.gob.es$path'></iframe>"
        val script = sedipualbaServerCertificateOptionScript(source, label)
        val cases = listOf(
            Triple(frame, option, true),
            Triple(frame + frame, option, false),
            Triple(frame, option + option, false),
            Triple(frame, option.replace("<img ", "<img alt='extra'><img "), false),
            Triple(frame, option.replace("alt=\"$label\"", "alt='wrong'"), false),
            Triple(frame, option.replace("<tbody ", "<tbody target='_blank' "), false),
            Triple(frame.replace("sede.cmt.gob.es", "foreign.example"), option, false),
            Triple(frame.replace(path, "/other.aspx"), option, false),
        )
        cases.forEach { (frames, options, accepted) ->
            checkFixture(source, "<script>window.fixtureClicks=0</script>$frames", options, script, accepted)
        }
        checkFixture(source.substringBefore('?'), frame, option, script, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun checkFixture(base: String, html: String, frameHtml: String, script: String, accepted: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        WebResourceResponse(
                            "text/html", "UTF-8", ByteArrayInputStream(frameHtml.toByteArray(Charsets.UTF_8)),
                        )
                    override fun onPageFinished(view: WebView, url: String) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(base, html, "text/html", "UTF-8", null)
            }
        }
        try {
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            val inspected = CountDownLatch(1)
            var code: String? = null
            var clicks: String? = null
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(script) { result ->
                    code = result
                    webView.evaluateJavascript("window.fixtureClicks || 0") {
                        clicks = it
                        inspected.countDown()
                    }
                }
            }
            assertTrue(inspected.await(5, TimeUnit.SECONDS))
            if (accepted) assertEquals("1", code) else assertTrue("Unexpected click: $code", code != "1")
            assertEquals(if (accepted) "1" else "0", clicks)
        } finally {
            instrumentation.runOnMainSync { webView.destroy() }
        }
    }
}
