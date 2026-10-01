package dev.junta.firmamobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.ClientCertRequest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.certificate.CertificateDocumentAccess
import dev.junta.firmamobile.certificate.CertificateDocumentMetadata
import dev.junta.firmamobile.certificate.CertificateReferenceStore
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.signing.SigningCoordinator
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SigningConfirmationInstrumentedTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun trustedMiniAppletRequestWaitsForNativeConfirmationAndCancelReturnsClosedError() {
        val uri = Uri.parse("content://dev.junta.firmamobile.tests/signing-identity.p12")
        val bytes = syntheticPkcs12()
        try {
            val reference = StoredCertificateReference(
                uri = uri,
                displayName = "synthetic-identity.p12",
                mimeType = CertificateRepository.MIME_X_PKCS12,
                size = bytes.size.toLong(),
                summary = null,
            )
            val repository = CertificateRepository(
                documentAccess = SyntheticDocumentAccess(uri, bytes),
                referenceStore = MemoryReferenceStore(reference),
                loader = Pkcs12Loader(),
            )

            TestCertificateDependencies.install(repository, CertificateSession()).use {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    rule.onNodeWithContentDescription("Contraseña del certificado")
                        .performScrollTo()
                        .performTextInput(TEST_PASSPHRASE)
                    rule.onNodeWithText("Desbloquear certificado")
                        .performScrollTo()
                        .performClick()
                    waitForText("Certificado encontrado")
                    rule.onNodeWithText("Continuar").performScrollTo().performClick()
                    waitForText("SERVICIOS PÚBLICOS")
                    openOvorionPortal()

                    waitForWebView(scenario)
                    scenario.onActivity { activity ->
                        checkNotNull(findWebView(activity.window.decorView)).apply {
                            stopLoading()
                            loadDataWithBaseURL(
                                TRUSTED_BASE_URL,
                                SYNTHETIC_MINIAPPLET_PAGE,
                                "text/html",
                                "UTF-8",
                                TRUSTED_BASE_URL,
                            )
                        }
                    }

                    waitForWebViewTitle(scenario, SIGN_READY_TITLE)
                    invokeSyntheticSign(scenario)
                    waitForConfirmation(scenario)
                    rule.onNodeWithText("Solicitud de firma").assertIsDisplayed()
                    rule.onNodeWithText("Sitio: www.juntadeandalucia.es").assertIsDisplayed()
                    rule.onNodeWithText("Firmar").assertIsDisplayed()
                    rule.onNodeWithText("Cancelar").performClick()

                    rule.waitUntil(timeoutMillis = 15_000) {
                        var title: String? = null
                        scenario.onActivity { activity ->
                            title = findWebView(activity.window.decorView)?.title
                        }
                        title == "USER_CANCELLED"
                    }
                    scenario.onActivity { activity ->
                        assertEquals(
                            "USER_CANCELLED",
                            findWebView(activity.window.decorView)?.title,
                        )
                    }
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    @Test
    fun unknownTlsRequestCanUnlockWithoutReplacingTheBrowserOrAutoApproving() {
        val uri = Uri.parse("content://dev.junta.firmamobile.tests/interactive-tls-identity.p12")
        val bytes = syntheticPkcs12()
        try {
            val repository = CertificateRepository(
                documentAccess = SyntheticDocumentAccess(uri, bytes),
                referenceStore = MemoryReferenceStore(StoredCertificateReference(
                    uri = uri, displayName = "synthetic-identity.p12",
                    mimeType = CertificateRepository.MIME_X_PKCS12,
                    size = bytes.size.toLong(), summary = null,
                )),
                loader = Pkcs12Loader(),
            )
            TestCertificateDependencies.install(repository, CertificateSession()).use {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitForText("Explorar sedes sin desbloquear el certificado")
                    rule.onNodeWithText("Explorar sedes sin desbloquear el certificado")
                        .performScrollTo().performClick()
                    waitForText("SERVICIOS PÚBLICOS")
                    // The QA smoke OPEN command deliberately requires an
                    // unlocked identity. Exercise the real catalog UI here.
                    rule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
                    rule.onNodeWithText("Buscar organismo o servicio").performTextReplacement("Ovorion")
                    // Catalog search combines DataStore preferences with input
                    // asynchronously. Await the resolved state, not only the
                    // editable text; opening an empty transitional list races it.
                    var observedSearch = ""
                    var resolvedItems = 0
                    try {
                        rule.waitUntil(timeoutMillis = 15_000) {
                            var ready = false
                            scenario.onActivity { activity ->
                                val state = androidx.lifecycle.ViewModelProvider(activity)
                                    .get(dev.junta.firmamobile.catalog.PortalCatalogViewModel::class.java).state.value
                                observedSearch = state.searchText
                                resolvedItems = state.sections.sumOf { it.items.size }
                                ready = state.searchText == "Ovorion" && state.sections.any { section ->
                                    section.items.any { it.portalId.value == OVORION_PORTAL_ID }
                                }
                            }
                            ready
                        }
                    } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
                        fail("Synthetic catalog query not resolved: query=$observedSearch itemCount=$resolvedItems")
                    }
                    rule.waitForIdle()
                    // Hide the search IME before scrolling; text-node index 0
                    // was unstable while insets and merged semantics changed.
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    rule.waitForIdle()
                    val openTag = "catalog-open-$OVORION_PORTAL_ID"
                    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(openTag))
                    rule.waitUntil(timeoutMillis = 10_000) {
                        rule.onAllNodesWithTag(openTag).fetchSemanticsNodes().size == 1
                    }
                    rule.onNodeWithTag(openTag).assertIsDisplayed().performClick()
                    waitForWebView(scenario)
                    var original: WebView? = null
                    scenario.onActivity { activity ->
                        original = checkNotNull(findWebView(activity.window.decorView)).apply {
                            stopLoading()
                            loadDataWithBaseURL(
                                "https://portal.synthetic.example/start",
                                "<html><head><title>TLS_FIXTURE_READY</title></head><body><input value=retained-draft></body></html>",
                                "text/html", "UTF-8", "https://portal.synthetic.example/start",
                            )
                        }
                    }
                    waitForWebViewTitle(scenario, "TLS_FIXTURE_READY")
                    var proceeded = 0
                    var ignored = 0
                    val request = object : ClientCertRequest() {
                        override fun getHost() = "auth.synthetic.example"
                        override fun getPort() = 443
                        override fun getKeyTypes() = arrayOf("RSA")
                        override fun getPrincipals(): Array<java.security.Principal> = emptyArray()
                        override fun proceed(key: java.security.PrivateKey, chain: Array<java.security.cert.X509Certificate>) { proceeded++ }
                        override fun ignore() { ignored++ }
                        override fun cancel() { error("No sticky cancellation in this flow") }
                    }
                    scenario.onActivity {
                        val view = checkNotNull(original)
                        view.webViewClient.onReceivedClientCertRequest(view, request)
                    }
                    rule.onNodeWithTag("interactive-client-auth-unlock").assertIsDisplayed().performClick()
                    rule.onNodeWithContentDescription("Contraseña del certificado")
                        .performScrollTo().performTextInput(TEST_PASSPHRASE)
                    rule.onNodeWithText("Desbloquear certificado").performScrollTo().performClick()
                    waitForText("Certificado encontrado")
                    scenario.onActivity { activity ->
                        assertTrue(original === findWebView(activity.window.decorView))
                        assertEquals("TLS_FIXTURE_READY", original?.title)
                        assertEquals(0, proceeded)
                        assertEquals(0, ignored)
                    }
                    rule.onNodeWithText("Continuar").performScrollTo().performClick()
                    rule.onNodeWithTag("interactive-client-auth-confirm").assertIsDisplayed().performClick()
                    scenario.onActivity { activity ->
                        assertEquals(1, proceeded)
                        assertEquals(0, ignored)
                        assertTrue(original === findWebView(activity.window.decorView))
                        assertEquals("TLS_FIXTURE_READY", original?.title)
                    }
                }
            }
        } finally { bytes.fill(0) }
    }

    @Test
    fun inlineNativeAfirmaRequestUnlocksInTheRealBrowserAndBackgroundCancelsWithoutSending() {
        val uri = Uri.parse("content://dev.junta.firmamobile.tests/native-afirma-identity.p12")
        val bytes = syntheticPkcs12()
        try {
            val repository = CertificateRepository(
                documentAccess = SyntheticDocumentAccess(uri, bytes),
                referenceStore = MemoryReferenceStore(StoredCertificateReference(
                    uri = uri, displayName = "synthetic-identity.p12",
                    mimeType = CertificateRepository.MIME_X_PKCS12,
                    size = bytes.size.toLong(), summary = null,
                )),
                loader = Pkcs12Loader(),
            )
            TestCertificateDependencies.install(repository, CertificateSession()).use {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitForText("Explorar sedes sin desbloquear el certificado")
                    rule.onNodeWithText("Explorar sedes sin desbloquear el certificado")
                        .performScrollTo().performClick()
                    waitForText("SERVICIOS PÚBLICOS")
                    // The QA smoke OPEN command deliberately requires an
                    // unlocked identity. Exercise the real catalog UI here.
                    rule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
                    rule.onNodeWithText("Buscar organismo o servicio").performTextReplacement("Ovorion")
                    // Catalog search combines DataStore preferences with input
                    // asynchronously. Await the resolved state, not only the
                    // editable text; opening an empty transitional list races it.
                    var observedSearch = ""
                    var resolvedItems = 0
                    try {
                        rule.waitUntil(timeoutMillis = 15_000) {
                            var ready = false
                            scenario.onActivity { activity ->
                                val state = androidx.lifecycle.ViewModelProvider(activity)
                                    .get(dev.junta.firmamobile.catalog.PortalCatalogViewModel::class.java).state.value
                                observedSearch = state.searchText
                                resolvedItems = state.sections.sumOf { it.items.size }
                                ready = state.searchText == "Ovorion" && state.sections.any { section ->
                                    section.items.any { it.portalId.value == OVORION_PORTAL_ID }
                                }
                            }
                            ready
                        }
                    } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
                        fail("Synthetic catalog query not resolved: query=$observedSearch itemCount=$resolvedItems")
                    }
                    rule.waitForIdle()
                    // Hide the search IME before scrolling; text-node index 0
                    // was unstable while insets and merged semantics changed.
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    rule.waitForIdle()
                    val openTag = "catalog-open-$OVORION_PORTAL_ID"
                    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(openTag))
                    rule.waitUntil(timeoutMillis = 10_000) {
                        rule.onAllNodesWithTag(openTag).fetchSemanticsNodes().size == 1
                    }
                    rule.onNodeWithTag(openTag).assertIsDisplayed().performClick()
                    waitForWebView(scenario)
                    var original: WebView? = null
                    scenario.onActivity { activity ->
                        original = checkNotNull(findWebView(activity.window.decorView)).apply {
                            stopLoading()
                            loadDataWithBaseURL(
                                "https://portal.synthetic.example/start",
                                "<html><head><title>NATIVE_AFIRMA_READY</title></head><body><input value=retained-draft></body></html>",
                                "text/html", "UTF-8", "https://portal.synthetic.example/start",
                            )
                        }
                    }
                    waitForWebViewTitle(scenario, "NATIVE_AFIRMA_READY")
                    val invocation = Uri.parse(
                        "afirma://sign?id=Synthetic-123&stservlet=https%3A%2F%2Fstore.synthetic.example%2Fput" +
                            "&format=CAdES&algorithm=SHA256withRSA&dat=c3ludGhldGlj",
                    )
                    val request = object : android.webkit.WebResourceRequest {
                        override fun getUrl() = invocation
                        override fun isForMainFrame() = true
                        override fun isRedirect() = false
                        override fun hasGesture() = true
                        override fun getMethod() = "GET"
                        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
                    }
                    scenario.onActivity {
                        val view = checkNotNull(original)
                        assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request))
                    }
                    rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed().performClick()
                    rule.onNodeWithContentDescription("Contraseña del certificado")
                        .performScrollTo().performTextInput(TEST_PASSPHRASE)
                    rule.onNodeWithText("Desbloquear certificado").performScrollTo().performClick()
                    waitForText("Certificado encontrado")
                    scenario.onActivity { activity ->
                        assertTrue(original === findWebView(activity.window.decorView))
                        assertEquals("NATIVE_AFIRMA_READY", original?.title)
                    }
                    rule.onNodeWithText("Continuar").performScrollTo().performClick()
                    rule.onNodeWithTag("native-afirma-confirm").assertIsDisplayed()
                    // Explicit REVIEW cancellation now notifies storage. This
                    // lifecycle test intentionally exercises the purely local
                    // path: no CANCEL POST, no signing, no remote endpoint.
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                    rule.waitForIdle()
                    rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
                    rule.onNodeWithTag("native-afirma-close").assertIsDisplayed().performClick()
                    rule.onNodeWithTag("native-afirma-consent").assertDoesNotExist()
                    scenario.onActivity { activity ->
                        assertTrue(original === findWebView(activity.window.decorView))
                        assertEquals("NATIVE_AFIRMA_READY", original?.title)
                    }
                }
            }
        } finally { bytes.fill(0) }
    }

    @Test
    fun composeBrowserUsesMatchParentLayoutAndCompositesWebViewPixels() {
        val uri = Uri.parse("content://dev.junta.firmamobile.tests/rendering-identity.p12")
        val bytes = syntheticPkcs12()
        try {
            val reference = StoredCertificateReference(
                uri = uri,
                displayName = "synthetic-rendering-identity.p12",
                mimeType = CertificateRepository.MIME_X_PKCS12,
                size = bytes.size.toLong(),
                summary = null,
            )
            val repository = CertificateRepository(
                documentAccess = SyntheticDocumentAccess(uri, bytes),
                referenceStore = MemoryReferenceStore(reference),
                loader = Pkcs12Loader(),
            )

            TestCertificateDependencies.install(repository, CertificateSession()).use {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    rule.onNodeWithContentDescription("Contraseña del certificado")
                        .performScrollTo()
                        .performTextInput(TEST_PASSPHRASE)
                    rule.onNodeWithText("Desbloquear certificado")
                        .performScrollTo()
                        .performClick()
                    waitForText("Certificado encontrado")
                    rule.onNodeWithText("Continuar").performScrollTo().performClick()
                    waitForText("SERVICIOS PÚBLICOS")
                    openOvorionPortal()
                    waitForWebView(scenario)

                    scenario.onActivity { activity ->
                        checkNotNull(findWebView(activity.window.decorView)).apply {
                            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, layoutParams.width)
                            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, layoutParams.height)
                            stopLoading()
                            loadDataWithBaseURL(
                                TRUSTED_BASE_URL,
                                SYNTHETIC_RENDER_PAGE,
                                "text/html",
                                "UTF-8",
                                TRUSTED_BASE_URL,
                            )
                        }
                    }
                    waitForWebViewTitle(scenario, RENDER_READY_TITLE)

                    val visualStateLatch = CountDownLatch(1)
                    scenario.onActivity { activity ->
                        checkNotNull(findWebView(activity.window.decorView))
                            .postVisualStateCallback(
                                1L,
                                object : WebView.VisualStateCallback() {
                                    override fun onComplete(requestId: Long) {
                                        visualStateLatch.countDown()
                                    }
                                },
                            )
                    }
                    assertTrue(
                        "Synthetic WebView visual-state callback timed out",
                        visualStateLatch.await(5, TimeUnit.SECONDS),
                    )
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

                    var internalPixel = Color.WHITE
                    var screenX = 0
                    var screenY = 0
                    scenario.onActivity { activity ->
                        val webView = checkNotNull(findWebView(activity.window.decorView))
                        val bitmap = Bitmap.createBitmap(
                            webView.width,
                            webView.height,
                            Bitmap.Config.ARGB_8888,
                        )
                        try {
                            webView.draw(Canvas(bitmap))
                            internalPixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                        } finally {
                            bitmap.recycle()
                        }
                        val location = IntArray(2)
                        webView.getLocationOnScreen(location)
                        screenX = location[0] + webView.width / 2
                        screenY = location[1] + webView.height / 2
                    }
                    var windowPixel = Color.WHITE
                    rule.waitUntil(timeoutMillis = 5_000) {
                        val screen = InstrumentationRegistry.getInstrumentation()
                            .uiAutomation
                            .takeScreenshot()
                        try {
                            windowPixel = screen.getPixel(screenX, screenY)
                            windowPixel.isNear(RENDER_COLOR)
                        } finally {
                            screen.recycle()
                        }
                    }

                    assertTrue(
                        "Expected the synthetic WebView color in its draw pass and window; " +
                            "internal=${internalPixel.toColorHex()} " +
                            "window=${windowPixel.toColorHex()}",
                        internalPixel.isNear(RENDER_COLOR) && windowPixel.isNear(RENDER_COLOR),
                    )
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun openOvorionPortal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = listOf(
            "am", "broadcast", "--user", "0",
            "-a", "dev.junta.firmamobile.action.CATALOG_SMOKE",
            "-p", "dev.junta.firmamobile",
            "--es", "runId", "instrumentation-ovorion",
            "--es", "portalId", OVORION_PORTAL_ID,
            "--es", "operation", "OPEN",
        ).joinToString(" ")
        val output = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
        assertTrue("Catalog smoke OPEN failed: $output", output.contains("OPEN_REQUESTED"))
    }

    private fun waitForText(text: String) {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun invokeSyntheticSign(scenario: ActivityScenario<MainActivity>) {
        val latch = CountDownLatch(1)
        var result: String? = null
        scenario.onActivity { activity ->
            checkNotNull(findWebView(activity.window.decorView)).evaluateJavascript(
                """
                (() => {
                  if (window.__jfmAfirmaShimInstalled !== true) return 'MISSING_SHIM';
                  if (!window.__jfmProbeDocumentId) return 'MISSING_DOCUMENT_ID';
                  if (typeof window.__jfmRunSyntheticSign !== 'function') return 'MISSING_RUNNER';
                  if (window.MiniApplet.sign === window.__jfmOriginalSign) return 'MISSING_WRAPPER';
                  window.__jfmRunSyntheticSign();
                  return 'INVOKED';
                })()
                """.trimIndent(),
            ) { value ->
                result = value
                latch.countDown()
            }
        }
        assertTrue("Synthetic sign invocation callback timed out", latch.await(5, TimeUnit.SECONDS))
        assertEquals("\"INVOKED\"", result)
    }

    private fun waitForConfirmation(scenario: ActivityScenario<MainActivity>) {
        var lastSafeTitle: String? = null
        var lastSafeOrigin: String? = null
        var activityTracksWebView = false
        var signingState = "unknown"
        try {
            rule.waitUntil(timeoutMillis = 15_000) {
                val dialogVisible = rule.onAllNodesWithText("Solicitud de firma")
                    .fetchSemanticsNodes().isNotEmpty()
                scenario.onActivity { activity ->
                    val webView = findWebView(activity.window.decorView)
                    lastSafeTitle = webView?.title
                    lastSafeOrigin = webView?.url?.let(::safeOrigin)
                    val field = MainActivity::class.java.getDeclaredField("currentWebView")
                        .apply { isAccessible = true }
                    activityTracksWebView = field.get(activity) === webView
                    val coordinatorField = MainActivity::class.java.getDeclaredField("signingCoordinator")
                        .apply { isAccessible = true }
                    signingState = (coordinatorField.get(activity) as SigningCoordinator)
                        .state.value.javaClass.simpleName
                }
                dialogVisible || lastSafeTitle in TERMINAL_TITLES
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            fail(
                "Native confirmation missing; title=$lastSafeTitle " +
                    "origin=$lastSafeOrigin activityTracksWebView=$activityTracksWebView " +
                    "signingState=$signingState",
            )
        }
        if (rule.onAllNodesWithText("Solicitud de firma").fetchSemanticsNodes().isEmpty()) {
            fail(
                "Native confirmation missing; title=$lastSafeTitle " +
                    "origin=$lastSafeOrigin activityTracksWebView=$activityTracksWebView " +
                    "signingState=$signingState",
            )
        }
    }

    private fun safeOrigin(rawUrl: String): String {
        val uri = Uri.parse(rawUrl)
        val port = uri.port.takeIf { it != -1 }
        return buildString {
            append(uri.scheme ?: "none")
            append("://")
            append(uri.host ?: "none")
            if (port != null) append(":$port")
        }
    }

    private fun waitForWebView(scenario: ActivityScenario<MainActivity>) {
        rule.waitUntil(timeoutMillis = 15_000) {
            var found = false
            scenario.onActivity { activity ->
                found = findWebView(activity.window.decorView) != null
            }
            found
        }
    }

    private fun waitForWebViewTitle(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ) {
        rule.waitUntil(timeoutMillis = 15_000) {
            var title: String? = null
            scenario.onActivity { activity ->
                title = findWebView(activity.window.decorView)?.title
            }
            title == expected
        }
    }

    private fun Int.isNear(expected: Int, tolerance: Int = 16): Boolean =
        kotlin.math.abs(Color.red(this) - Color.red(expected)) <= tolerance &&
            kotlin.math.abs(Color.green(this) - Color.green(expected)) <= tolerance &&
            kotlin.math.abs(Color.blue(this) - Color.blue(expected)) <= tolerance

    private fun Int.toColorHex(): String = String.format("#%08X", this)

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun syntheticPkcs12(): ByteArray {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val base64 = testContext.assets.open("synthetic-identity.p12.b64")
            .bufferedReader()
            .use { it.readText() }
        return Base64.decode(base64, Base64.DEFAULT)
    }

    private class SyntheticDocumentAccess(
        private val expectedUri: Uri,
        private val bytes: ByteArray,
    ) : CertificateDocumentAccess {
        override fun queryMetadata(uri: Uri) = CertificateDocumentMetadata(
            displayName = "synthetic-identity.p12",
            mimeType = CertificateRepository.MIME_X_PKCS12,
            size = bytes.size.toLong(),
        )

        override fun takePersistableReadPermission(uri: Uri) = Unit

        override fun releasePersistableReadPermission(uri: Uri) = Unit

        override fun open(uri: Uri): InputStream {
            check(uri == expectedUri)
            return ByteArrayInputStream(bytes)
        }
    }

    private class MemoryReferenceStore(
        private var reference: StoredCertificateReference?,
    ) : CertificateReferenceStore {
        override suspend fun read(): StoredCertificateReference? = reference

        override suspend fun write(reference: StoredCertificateReference) {
            this.reference = reference
        }

        override suspend fun clear() {
            reference = null
        }
    }

    private companion object {
        const val TEST_PASSPHRASE = "test-password-123"
        const val OVORION_PORTAL_ID = "junta-andalucia-ovorion"
        const val TRUSTED_BASE_URL =
            "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"
        const val SIGN_READY_TITLE = "SIGN_READY"
        const val RENDER_READY_TITLE = "RENDER_READY"
        val RENDER_COLOR: Int = Color.rgb(0, 94, 73)
        val TERMINAL_TITLES = setOf(
            "ORIGINAL",
            "INVALID_REQUEST",
            "UNSUPPORTED_PROTOCOL",
            "PROFILE_NOT_ACTIVE",
            "ORIGIN_NOT_ALLOWED",
            "CERTIFICATE_LOCKED",
            "PROTOCOL_FAILED",
        )
        const val SYNTHETIC_MINIAPPLET_PAGE = """
            <!doctype html><html><head><title>START</title><script>
            let originalCalls = 0;
            window.__jfmOriginalSign = function() { originalCalls += 1; document.title = 'ORIGINAL'; };
            window.MiniApplet = { sign: window.__jfmOriginalSign };
            window.__jfmRunSyntheticSign = function() {
              document.title = 'INVOKED';
              window.MiniApplet.sign(
                btoa('synthetic-data'),
                'SHA1withRSA',
                'CAdES',
                'serverUrl=https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService\nfilters=keyusage.digitalsignature:true;nonexpired:',
                function() { document.title = 'UNEXPECTED_SUCCESS'; },
                function(code) {
                  document.title = originalCalls === 0 ? code : 'ORIGINAL';
                }
              );
            };
            window.addEventListener('DOMContentLoaded', function() { document.title = 'SIGN_READY'; });
            </script></head><body>synthetic</body></html>
        """
        const val SYNTHETIC_RENDER_PAGE = """
            <!doctype html><html><head><title>START</title>
            <style>
              html, body { margin: 0; width: 100%; height: 100%; background: #005e49; }
            </style></head><body>
            <script>document.title = 'RENDER_READY';</script>
            </body></html>
        """
    }
}
