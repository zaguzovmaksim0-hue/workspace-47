package dev.junta.firmamobile

import android.net.Uri
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
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

    private fun waitForText(text: String) {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForConfirmation(scenario: ActivityScenario<MainActivity>) {
        var lastSafeTitle: String? = null
        var lastSafeOrigin: String? = null
        var activityTracksWebView = false
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
                }
                dialogVisible || lastSafeTitle in TERMINAL_TITLES
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            fail(
                "Native confirmation missing; title=$lastSafeTitle " +
                    "origin=$lastSafeOrigin activityTracksWebView=$activityTracksWebView",
            )
        }
        if (rule.onAllNodesWithText("Solicitud de firma").fetchSemanticsNodes().isEmpty()) {
            fail(
                "Native confirmation missing; title=$lastSafeTitle " +
                    "origin=$lastSafeOrigin activityTracksWebView=$activityTracksWebView",
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
        const val TRUSTED_BASE_URL = "https://www.juntadeandalucia.es/"
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
            window.MiniApplet = {
              sign: function() { originalCalls += 1; document.title = 'ORIGINAL'; }
            };
            window.addEventListener('DOMContentLoaded', function() {
              window.MiniApplet.sign(
                btoa('synthetic-data'),
                'SHA1withRSA',
                'CAdES',
                'serverUrl=https://ws024.juntadeandalucia.es/afirma-server-triphase-signer/SignatureService\nmode=explicit',
                function() { document.title = 'UNEXPECTED_SUCCESS'; },
                function(code) {
                  document.title = originalCalls === 0 ? code : 'ORIGINAL';
                }
              );
            });
            </script></head><body>synthetic</body></html>
        """
    }
}
