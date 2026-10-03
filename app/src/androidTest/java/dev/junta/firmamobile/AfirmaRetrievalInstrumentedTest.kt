package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.afirma.servlet.*
import dev.junta.firmamobile.ui.AfirmaRetrievalDialog
import dev.junta.firmamobile.ui.NativeAfirmaConsentDialog
import java.net.URLEncoder
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SAX and Compose; synthetic encrypted configuration transport.
 * No government host, stored user identity, document signature or upload. */
@RunWith(AndroidJUnit4::class)
class AfirmaRetrievalInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun androidSaxReadsTheOfficialParameterShapeWithoutDoubleFormDecoding() {
        val xml = "<?xml version='1.0'?><sign><e k='appname' v='A+B%2BC%252B'/><e k='id' v='Fixture-123'/></sign>"
        val value = AfirmaConfigurationXml.parse(xml.toByteArray())
        assertEquals(AfirmaServletOperation.SIGN, value.operation)
        assertEquals("A B+C%2B", value.values["appname"])
        assertEquals("Fixture-123", value.values["id"])
    }

    @Test fun androidXmlRejectsDtdAndExternalResourceDefinitions() {
        for (xml in listOf("<!DOCTYPE sign SYSTEM 'https://unused.synthetic.example/xxe'><sign/>",
            "<!DOCTYPE sign [<!ENTITY x SYSTEM 'file:///unused-fixture'>]><sign><e k='id' v='&x;'/></sign>")) {
            try { AfirmaConfigurationXml.parse(xml.toByteArray()); fail("External definition must fail") }
            catch (_: Exception) { }
        }
    }

    @Test fun decryptedDescriptorOpensFreshSigningReviewWithoutUsingACertificateOrSigning() {
        val ready = CompletableDeferred<Unit>()
        var downloads = 0; var consentCount = 0; var unlocks = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val scope = rememberCoroutineScope()
                val owner = remember { Any() }
                val loaderPrompt = remember { mutableStateOf<AfirmaRetrievalPrompt?>(null) }
                val signPrompt = remember { mutableStateOf<AfirmaConsentPrompt?>(null) }
                val consent = remember {
                    AfirmaConsentController<Any>(scope, { o, e -> o === owner && e == 1L },
                        identityProvider = { null }, canRespond = { true }, onPrompt = { signPrompt.value = it })
                }
                val loader = remember {
                    AfirmaRetrievalController<Any>(scope,
                        resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, id ->
                            downloads++; assertEquals("Config-123", id); ready.await()
                            AfirmaRetrievedBytes(AfirmaIntermediateCipher.encode(xml(), "12345678").toByteArray())
                        }),
                        isCurrent = { o, e -> o === owner && e == 1L }, canRespond = { true },
                        onPrompt = { loaderPrompt.value = it }, onPrepared = { o, e, operation ->
                            consentCount++; assertEquals(4, operation.details.payloadBytes)
                            consent.offer(o, e, operation)
                        },
                    )
                }
                LaunchedEffect(loader) { loader.offer(owner, 1, request()) }
                DisposableEffect(loader, consent) { onDispose { loader.close(); consent.close() } }
                loaderPrompt.value?.let { p -> AfirmaRetrievalDialog(p, { loader.cancel(p.token) }, { loader.dismiss(p.token) }) }
                signPrompt.value?.let { p -> NativeAfirmaConsentDialog(p,
                    onConfirm = { fail("No unlocked certificate or consent in this fixture") }, onUnlock = { unlocks++ },
                    onCancel = { consent.cancel(p.token) }, onDismiss = { consent.dismiss(p.token) }) }
            } }
            rule.onNodeWithTag("afirma-retrieval-progress").assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.runOnIdle { assertEquals(1, downloads); assertEquals(0, consentCount); ready.complete(Unit) }
            rule.waitUntil(timeoutMillis = 15_000) { consentCount == 1 }
            rule.onNodeWithTag("afirma-retrieval-dialog").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed().performClick()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.runOnIdle { assertEquals(1, downloads); assertEquals(1, consentCount); assertEquals(1, unlocks) }
            rule.onNodeWithTag("native-afirma-cancel").performClick()
            rule.onNodeWithTag("native-afirma-close").performClick()
        }
    }

    @Test fun canceledLoadingOffersCloseNotRetryAndNeverHandoffsToSigning() {
        val ready = CompletableDeferred<Unit>()
        var downloads = 0; var handoffs = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val scope = rememberCoroutineScope(); val owner = remember { Any() }
                val prompt = remember { mutableStateOf<AfirmaRetrievalPrompt?>(null) }
                val loader = remember {
                    AfirmaRetrievalController<Any>(scope,
                        resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> downloads++; ready.await(); AfirmaRetrievedBytes(xml()) }),
                        isCurrent = { o, e -> o === owner && e == 1L }, canRespond = { true }, onPrompt = { prompt.value = it },
                        onPrepared = { _, _, op -> handoffs++; op.close(); false },
                    )
                }
                LaunchedEffect(loader) { loader.offer(owner, 1, request()) }
                DisposableEffect(loader) { onDispose { loader.close() } }
                prompt.value?.let { p -> AfirmaRetrievalDialog(p, { loader.cancel(p.token) }, { loader.dismiss(p.token) }) }
            } }
            rule.onNodeWithTag("afirma-retrieval-cancel").assertIsDisplayed().performClick()
            rule.onNodeWithTag("afirma-retrieval-progress").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.runOnIdle { ready.complete(Unit) }
            rule.waitForIdle()
            rule.onNodeWithTag("afirma-retrieval-close").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(1, downloads); assertEquals(0, handoffs) }
        }
    }

    private fun request() = (AfirmaServletInvocationParser.parse(
        "afirma://sign?fileid=Config-123&rid=Response-123&key=12345678&rtservlet=https%3A%2F%2Fretrieve.synthetic.example%2Fget&stservlet=https%3A%2F%2Fstore.synthetic.example%2Fput",
        "https://page.synthetic.example/start",
    ) as AfirmaServletParseResult.Deferred).invocation
    private fun xml(): ByteArray = ("<sign>" + mapOf("id" to "Response-123", "stservlet" to "https://store.synthetic.example/put",
        "key" to "87654321", "format" to "CAdES", "algorithm" to "SHA256withRSA", "dat" to "dGVzdA==").entries.joinToString("") {
            "<e k='${it.key}' v='${URLEncoder.encode(it.value, "UTF-8")}'/>"
        } + "</sign>").toByteArray()
}
