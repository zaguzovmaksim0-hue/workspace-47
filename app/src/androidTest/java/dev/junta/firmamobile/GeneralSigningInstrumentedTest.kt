package dev.junta.firmamobile

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.afirma.servlet.*
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.ui.BrowserScreen
import java.math.BigInteger
import java.net.URI
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android XML canonicalization and browser request routing, using only
 * generated identities/local .example fixtures; no real credential or server. */
@RunWith(AndroidJUnit4::class)
class GeneralSigningInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val logger = SanitizedLogger()

    @Test fun fourXadesPackagesSignAndVerifyOnAndroidWithoutPrivateKeyEncoding() {
        val now = Instant.now(); val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=Synthetic XAdES test")
        val cert = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger.valueOf(992), Date.from(now.minusSeconds(600)), Date.from(now.plusSeconds(3600)), name, pair.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider()).build(pair.private)))
        val source = pair.private as RSAPrivateKey; val reads = AtomicInteger()
        val key = object : RSAPrivateKey {
            override fun getAlgorithm() = "RSA"
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray { reads.incrementAndGet(); error("No key encoding") }
            override fun getPrivateExponent() = source.privateExponent
            override fun getModulus() = source.modulus
        }
        val identity = UnlockedIdentity(key, cert, listOf(cert), CertificateSummary("Synthetic", "Synthetic", now.minusSeconds(600), now.plusSeconds(3600)))
        val engine = NativeXadesEngine(Clock.fixed(now, ZoneOffset.UTC))
        for (kind in NativeXadesPackaging.entries) {
            val payload = if (kind == NativeXadesPackaging.ENVELOPED) "<root><value>one</value></root>".toByteArray() else byteArrayOf(0,1,2,127,-1)
            val options = NativeXadesOptions(kind, "application/octet-stream", null)
            val result = engine.sign(payload, identity, SigningAlgorithm.SHA256_WITH_RSA, options)
            assertTrue("Actual Android $kind: $result", result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.use { it.withBytes { encoded ->
                assertTrue(engine.verify(encoded, payload, cert, SigningAlgorithm.SHA256_WITH_RSA, options))
                assertFalse(engine.verify(encoded, "changed".toByteArray(), cert, SigningAlgorithm.SHA256_WITH_RSA, options))
            } }
        }
        assertEquals(0, reads.get())
    }

    @Test fun unprofiledPageOffersLocalXadesWithoutAutomaticallyUsingACertificate() {
        withBrowser { scenario, view ->
            offer(scenario, view, "sign", base("XAdES", "synthetic".toByteArray()))
            rule.onNodeWithText("XAdES", substring = true).assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            closeLocally(scenario)
        }
    }

    @Test fun unprofiledBatchDisclosesEachServiceAndDelegatedPreDataBeforeUnlock() {
        withBrowser { scenario, view ->
            val batch = """{"algorithm":"SHA256withRSA","format":"XAdES","singlesigns":[{"id":"one","datareference":"YWJj"},{"id":"two","datareference":"ZGVm"}]}""".toByteArray()
            val fields = mapOf("id" to "BrowserBatch123", "stservlet" to "https://storage.synthetic.example/put", "dat" to b64(batch), "jsonbatch" to "true",
                "batchpresignerurl" to "https://signer.synthetic.example/pre", "batchpostsignerurl" to "https://signer.synthetic.example/post")
            offer(scenario, view, "batch", fields)
            rule.onNodeWithText("Servicio de firma: https://signer.synthetic.example/pre").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Servicio de firma: https://signer.synthetic.example/post").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Documentos en el lote: 2").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Firma delegada:", substring = true).performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            closeLocally(scenario)
        }
    }

    @Test fun unprofiledTriphaseCountersignRemainsAnExplicitDelegatedOperation() {
        withBrowser { scenario, view ->
            offer(scenario, view, "countersign", base("XAdEStri", "service-document-reference".toByteArray()) + ("serverurl" to "https://signer.synthetic.example/tri"))
            rule.onNodeWithText("Servicio de firma: https://signer.synthetic.example/tri").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Contrafirma mediante el servicio").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            closeLocally(scenario)
        }
    }

    private fun withBrowser(test: (ActivityScenario<MainActivity>, WebView) -> Unit) {
        val current = AtomicReference<WebView?>(); val ready = AtomicBoolean(false); val intercepted = AtomicBoolean(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = activity.application as JuntaFirmaApplication
                activity.setContent {
                    BrowserScreen(profileId = null, entryUrl = URI("https://multiphase.synthetic.example/"), certificateState = null,
                        logger = logger, signingState = SigningUiState.Idle,
                        onMiniAppletRequest = { _, _ -> error("No profile bridge") }, onMiniAppletCancel = {}, onConfirmSigning = {},
                        onCancelSigning = { _, _ -> }, onDismissSigningState = {}, onExitBrowser = {}, onOpenExternal = {}, onOpenOfficialAutoFirma = {},
                        onChangeCertificate = {}, onLockCertificate = {}, onClearSession = {}, clientCertificateIdentityProvider = { null },
                        clientCertPreferenceCoordinator = app.clientCertPreferenceCoordinator, onWebViewChanged = { current.set(it) })
                }
            }
            rule.waitUntil(timeoutMillis = 15_000) { current.get() != null }
            val view = checkNotNull(current.get()); val url = "https://multiphase.synthetic.example/local"
            scenario.onActivity {
                view.stopLoading()
                view.webViewClient = PublicBrowserInstrumentedTest.LocalResponseClient(view.webViewClient, url, ready, intercepted,
                    "<html><head><title>MULTIPHASE_READY</title></head><body>Local request fixture</body></html>")
                view.loadUrl(url)
            }
            rule.waitUntil(timeoutMillis = 15_000) {
                var shown = false
                scenario.onActivity { shown = current.get() === view && view.url == url && view.title == "MULTIPHASE_READY" && view.progress == 100 && view.hasWindowFocus() }
                shown && ready.get() && intercepted.get()
            }
            test(scenario, view)
        }
    }
    private fun offer(scenario: ActivityScenario<MainActivity>, view: WebView, op: String, fields: Map<String, String>) {
        val query = fields.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8").replace("+", "%20") }
        val uri = Uri.parse("afirma://$op?$query")
        scenario.onActivity { assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, object : WebResourceRequest {
            override fun getUrl() = uri
            override fun isForMainFrame() = true
            override fun isRedirect() = false
            override fun hasGesture() = true
            override fun getMethod() = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        })) }
        rule.waitUntil(timeoutMillis = 5_000) { runCatching { rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed() }.isSuccess }
    }
    private fun closeLocally(scenario: ActivityScenario<MainActivity>) {
        // Cancel through lifecycle, not the server-notifying cancellation button.
        scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle(); rule.onNodeWithTag("native-afirma-close").performClick()
    }
    private fun base(format: String, data: ByteArray) = mapOf("id" to "GeneralBrowser123", "stservlet" to "https://storage.synthetic.example/put", "format" to format, "algorithm" to "SHA256withRSA", "dat" to b64(data))
    private fun b64(data: ByteArray) = Base64.getUrlEncoder().encodeToString(data)
}
