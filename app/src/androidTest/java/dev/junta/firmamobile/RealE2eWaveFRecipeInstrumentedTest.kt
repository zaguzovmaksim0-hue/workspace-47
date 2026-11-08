package dev.junta.firmamobile

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/** Local documents only: every resource is intercepted and no certificate or network is used. */
@RunWith(AndroidJUnit4::class)
class RealE2eWaveFRecipeInstrumentedTest {
    @Test
    fun dgojLoginSubmitsRequireExactFormsAndReadyCsrf() {
        listOf(false, true).forEach { second ->
            val source = if (second) "$DGOJ_PLOGIN;DGOJ_ID=synthetic-1" else DGOJ_START
            val script = dgojLoginSubmitScript(second)
            val html = dgojDocument(second)
            val formName = if (second) "lanzarPeticionClave" else "plogin"
            val inputId = if (second) "lanzarPeticionClave__" else "plogin_login_texto_iniciar_sesion"
            val control = Regex("""<input id="$inputId"[^>]*>""").find(html)!!.value
            val csrf = """<input type="hidden" name="sedeCsrf" value="synthetic-csrf">"""
            checkFixture(source, html, "", script, true)
            val rejected = listOf(
                html.replace("id=\"$formName\"", "id=\"wrong\""),
                html.replace("name=\"$formName\"", "name=\"wrong\""),
                html + """<form id="$formName" name="other"></form>""",
                html + """<form id="other" name="$formName"></form>""",
                html + """<div id="$inputId"></div>""",
                html.replace(control, """<button id="$inputId" type="submit"></button>"""),
                html.replace(control, control.replace("submit", "button")),
                html.replace(control, control.replace("name=", "name=\"wrong\" ignored=")),
                html.replace(control, control.replace("value=", "value=\"Cl@ve\" ignored=")),
                html.replace(control, control.replace("id=", "id=\"neighbor-only\" ignored=")),
                html.replace(control, control.replace("<input", "<input disabled")),
                html.replace(control, control.replace("<input", "<input style='display:none'")),
                html.replace(control, control.replace("<input", "<input style='visibility:hidden'")),
                html.replace(control, control.replace("<input", "<input onclick=''")),
                html.replace(control, control.replace("<input", "<input form='iniciar_sesion_form'")),
                html.replace(control, control.replace("<input", "<input form='outside'")) +
                    "<form id='outside'></form>",
                html.replace(control, "<fieldset disabled>$control</fieldset>"),
                html.replace("method=\"post\"", "method=\"get\""),
                html.replace("<form id=", "<form target='_self' id="),
                html.replace("<form id=", "<form target='_top' id="),
                html.replace("<form id=", "<form target='_parent' id="),
                html.replace("<form id=", "<form target='_blank' id="),
                "<base target='_blank'>$html",
                html.replace(control, control.replace("<input", "<input formaction=''")),
                html.replace(control, control.replace("<input", "<input formmethod=''")),
                html.replace(control, control.replace("<input", "<input formtarget=''")),
                html.replace(csrf, ""),
                html.replace(csrf, "") + "<form>$csrf</form>",
                html.replace(csrf, csrf.replace("synthetic-csrf", "")),
                html.replace(csrf, csrf.replace("<input", "<input disabled")),
                html.replace(csrf, csrf.replace("hidden", "text")),
                html.replace(csrf, "<select name='sedeCsrf'></select>"),
                html.replace(csrf, csrf + csrf),
                html.replace("action=\"https://sede.", "action=\"https://foreign."),
            )
            rejected.forEach { checkFixture(source, it, "", script, false) }
            checkFixture(source, html.replace(csrf, ""), "", script, true) { view ->
                assertEquals("0", evaluateFixture(view, script))
                evaluateFixture(view, """
                    const csrf = document.createElement('input'); csrf.type = 'hidden'; csrf.name = 'sedeCsrf';
                    document.getElementById('$formName').appendChild(csrf);
                """.trimIndent())
                assertEquals("0", evaluateFixture(view, script))
                evaluateFixture(view, "document.querySelector('[name=sedeCsrf]').value = 'synthetic-ready'")
            }
            val frame = "<iframe src='$source'></iframe>"
            checkFixture(source, frame, html,
                "document.querySelector('iframe').contentWindow.eval(${JSONObject.quote(script)})", false)
        }
    }

    @Test
    fun dgojPloginSessionComparisonIsBounded() {
        assertTrue(dgojLoginSourceMatches(DGOJ_START, false))
        assertFalse(dgojLoginSourceMatches(null, false))
        assertFalse(dgojLoginSourceMatches(null, true))
        val firstSources = listOf(
            DGOJ_START.replace("iA=no", "iA=si"), DGOJ_START.replace("limpiarBusqueda=S", "limpiarBusqueda=N"),
            DGOJ_START.replace("iA=no&", ""), DGOJ_START.replace("&limpiarBusqueda=S", ""),
            "$DGOJ_START&iA=no", "$DGOJ_START&limpiarBusqueda=S", "$DGOJ_START&extra=1", "$DGOJ_START#x",
            "$DGOJ_START;DGOJ_ID=a",
        )
        firstSources.forEach { source ->
            assertFalse(dgojLoginSourceMatches(source, false))
            checkFixture(source, dgojDocument(false), "", dgojLoginSubmitScript(false), false)
        }
        val invalid = listOf(
            DGOJ_PLOGIN, "$DGOJ_PLOGIN;DGOJ_ID=", "$DGOJ_PLOGIN;DGOJ_ID=${"a".repeat(257)}",
            DGOJ_PLOGIN.replace("https://", "http://"), DGOJ_PLOGIN.replace("https://", "https://user@"),
            DGOJ_PLOGIN.replace(".gob.es", ".gob.es:443"), DGOJ_PLOGIN.replace(".gob.es", ".gob.es:444"),
            DGOJ_PLOGIN.replace(".gob.es", ".gob.es.evil.example"), DGOJ_PLOGIN.replace(".gob.es", ".gob.es."),
            DGOJ_PLOGIN.replace("plogin", "other"), "$DGOJ_PLOGIN/", "$DGOJ_PLOGIN/other",
            DGOJ_PLOGIN.replace("/login/", "/login/../login/"), DGOJ_PLOGIN.replace("/login/", "//login/"),
            "$DGOJ_PLOGIN;jsessionid=a", "$DGOJ_PLOGIN;dgoj_id=a",
        ) + listOf("a;DGOJ_ID=b", "a;x=y", "a?x=1", "a#x", "a/b", "a\\b", "a b", "a\n", "a\r", "a\t",
            "a\u0000", "é", "%41", "a%2Fb", "a%3Bb").map { "$DGOJ_PLOGIN;DGOJ_ID=$it" }
        invalid.forEach { value ->
            assertFalse(dgojLoginSourceMatches(value, true))
            val changed = dgojDocument(false).replace("$DGOJ_PLOGIN;DGOJ_ID=synthetic-1", value)
            checkFixture(DGOJ_START, changed, "", dgojLoginSubmitScript(false), false)
        }
        listOf("a", "a".repeat(256), "AZaz09._~-").forEach { value ->
            val source = "$DGOJ_PLOGIN;DGOJ_ID=$value"
            assertTrue(dgojLoginSourceMatches(source, true))
            checkFixture(source, dgojDocument(true), "", dgojLoginSubmitScript(true), true)
            val changed = dgojDocument(false).replace("$DGOJ_PLOGIN;DGOJ_ID=synthetic-1", source)
            checkFixture(DGOJ_START, changed, "", dgojLoginSubmitScript(false), true)
        }
        val claveAction = "https://sede.ordenacionjuego.gob.es/tramite/clave/lanzarPeticionClave.jjsp"
        listOf(DGOJ_START, claveAction, "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect").forEach {
            assertFalse(dgojLoginSourceMatches("$it;DGOJ_ID=a", true))
        }
        checkFixture("$DGOJ_PLOGIN;DGOJ_ID=synthetic-1",
            dgojDocument(true).replace(claveAction, "$claveAction;DGOJ_ID=a"), "", dgojLoginSubmitScript(true), false)
    }

    private fun dgojDocument(secondStep: Boolean): String {
        val formName = if (secondStep) "lanzarPeticionClave" else "plogin"
        val inputId = if (secondStep) "lanzarPeticionClave__" else "plogin_login_texto_iniciar_sesion"
        val inputName = if (secondStep) " " else "login.texto.iniciar.sesion"
        val label = if (secondStep) " " else "Iniciar sesión"
        val action = if (secondStep) "https://sede.ordenacionjuego.gob.es/tramite/clave/lanzarPeticionClave.jjsp"
            else "$DGOJ_PLOGIN;DGOJ_ID=synthetic-1"
        return """
            <form id="$formName" name="$formName" method="post" action="$action">
              <input id="$inputId" type="submit" name="$inputName" value="$label">
              <input type="hidden" name="sedeCsrf" value="synthetic-csrf">
            </form>
            <form id="iniciar_sesion_form"><input type="submit" value="Iniciar sesión"></form>
            <form id="inicio" target="_Blank"></form>
            <script>window.fixtureClicks=0;
            document.querySelectorAll('input[type=submit]').forEach(input => input.addEventListener('click', e => {
              window.fixtureClicks++; e.preventDefault();
            }));</script>
        """.trimIndent()
    }

    private fun evaluateFixture(webView: WebView, script: String): String? {
        val inspected = CountDownLatch(1)
        var result: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(script) { result = it; inspected.countDown() }
        }
        assertTrue(inspected.await(5, TimeUnit.SECONDS))
        return result
    }

    @Test
    fun teaCertificateButtonUsesTheExistingExactHelper() {
        val source = "https://sede.tea.hacienda.gob.es/static_files/common/html/selector_acceso/" +
            "SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0&aut=CPE"
        val target = "https://www1.tea.hacienda.gob.es/wlpl/OVCT-CXEW/DialogoRepresentacion" +
            "?ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0"
        val button = """
            <button name="autoriza-C" type="button" data-link="$target">Certificado o DNI electrónico</button>
        """.trimIndent()
        val cases = listOf(
            button, button.replace("type=\"button\"", "type=\"submit\""),
            button.replace("Certificado o DNI electrónico", "Cl@ve"),
            button.replace("<button ", "<button onclick='' "), button + button,
        )
        val recipe = RealE2eInstrumentedTest::class.java.getDeclaredMethod(
            "applyPortalRecipe", ActivityScenario::class.java, String::class.java, String::class.java,
        ).apply { isAccessible = true }
        val currentWebView = MainActivity::class.java.getDeclaredField("currentWebView")
            .apply { isAccessible = true }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            cases.forEachIndexed { index, content ->
                val html = """
                    <html><body>$content<script>
                    window.fixtureClicks = 0;
                    document.querySelectorAll('button').forEach(button => button.addEventListener('click', () => {
                      window.fixtureClicks++; window.fixtureTarget = button.getAttribute('data-link');
                    }));
                    </script></body></html>
                """.trimIndent()
                checkFixture(source, html, "", "window.fixtureClicks === 1 ? 1 : 0", index == 0) { view ->
                    var original: WebView? = null
                    scenario.onActivity { activity ->
                        original = currentWebView.get(activity) as? WebView
                        currentWebView.set(activity, view)
                        (activity.application as JuntaFirmaApplication).sanitizedLogger.clear()
                    }
                    try {
                        val probe = RealE2eInstrumentedTest()
                        if (index == 0) {
                            recipe.invoke(probe, scenario,
                                "age-sede-electronica-de-los-tribunales-economico-administrativos-tea",
                                "tea-alegaciones-certificado")
                        } else {
                            val failure = assertThrows(InvocationTargetException::class.java) {
                                recipe.invoke(probe, scenario,
                                    "age-sede-electronica-de-los-tribunales-economico-administrativos-tea",
                                    "tea-alegaciones-certificado")
                            }
                            assertTrue(failure.cause is IllegalStateException)
                            assertTrue(failure.cause?.message in setOf(
                                "REAL_E2E_RECIPE_TARGET_TIMEOUT", "REAL_E2E_RECIPE_TARGET_MISMATCH",
                            ))
                        }
                    } finally {
                        scenario.onActivity { currentWebView.set(it, original) }
                    }
                }
            }
        }
    }

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
    private fun checkFixture(
        base: String, html: String, frameHtml: String, script: String, accepted: Boolean,
        beforeInspect: (WebView) -> Unit = {},
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                layout(0, 0, 1080, 1920)
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
            beforeInspect(webView)
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

    private companion object {
        const val DGOJ_START =
            "https://sede.ordenacionjuego.gob.es/tramite/login/inicio.jjsp?iA=no&limpiarBusqueda=S"
        const val DGOJ_PLOGIN = "https://sede.ordenacionjuego.gob.es/tramite/login/plogin.jjsp"
    }
}
