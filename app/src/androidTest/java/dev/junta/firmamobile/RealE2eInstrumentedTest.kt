package dev.junta.firmamobile

import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.catalog.PublicPortalCatalogParser
import dev.junta.firmamobile.certificate.CachedCertificateUnlock
import dev.junta.firmamobile.certificate.CertificateDocumentAccess
import dev.junta.firmamobile.certificate.CertificateDocumentMetadata
import dev.junta.firmamobile.certificate.CertificateReferenceStore
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.signing.SigningCoordinator
import dev.junta.firmamobile.signing.SigningUiState
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Manual-only, credential-backed portal probe. The protected workflow invokes this class once per
 * catalog portal and stages the PKCS#12 only inside app-private storage on the disposable emulator.
 *
 * The generic probe may authenticate with TLS and may share the public certificate. It reaches but
 * cancels signing confirmation unless the profile is in the explicit safe authentication-signing
 * allowlist. It never files, registers, pays, submits, modifies an administrative record, or clicks
 * a generic DOM control whose semantics are not part of a reviewed profile recipe.
 */
@RunWith(AndroidJUnit4::class)
class RealE2eInstrumentedTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun probeOneCatalogPortalWithRealCertificate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val explicitlyEnabled = arguments.getString(REAL_E2E_ARGUMENT) == "true"
        val portalId = arguments.getString(PORTAL_ID_ARGUMENT).orEmpty()
        val deepEnabled = arguments.getString(DEEP_ARGUMENT) == "true"
        val targetContext = instrumentation.targetContext
        val fixtureDir = File(targetContext.filesDir, REAL_E2E_DIR)
        val certificateFile = File(fixtureDir, CERTIFICATE_FILE)
        val passwordFile = File(fixtureDir, PASSWORD_FILE)

        assumeTrue("REAL_E2E requires explicit opt-in", explicitlyEnabled)
        require(PORTAL_ID_PATTERN.matches(portalId)) { "REAL_E2E_INVALID_PORTAL_ID" }

        val catalog = targetContext.resources.openRawResource(R.raw.public_portal_catalog_v1)
            .bufferedReader(StandardCharsets.UTF_8)
            .use { PublicPortalCatalogParser.parse(it.readText()) }
        val entry = catalog.entries.singleOrNull { it.portalId.value == portalId }
            ?: error("Unknown catalog portal id")
        val profileId = requireNotNull(entry.profileId).value
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId.value == profileId }
        val capabilities = profile.capabilities.map { it.name }.sorted()
        val result = ProbeResult(
            portalId = portalId,
            profileId = profileId,
            capabilities = capabilities,
            expectedStartHost = profile.startUrl.host,
        )

        var password: CharArray? = null
        var unlockCache: RealE2eUnlockCache? = null
        var probeStage = ProbeStage.INIT
        try {
            require(certificateFile.isFile) { "REAL_E2E_CERTIFICATE_MISSING" }
            require(passwordFile.isFile) { "REAL_E2E_PASSWORD_MISSING" }

            probeStage = ProbeStage.RESET_BROWSER
            resetBrowserState()
            application().sanitizedLogger.clear()

            val loadedPassword = readPassword(passwordFile)
            password = loadedPassword
            val session = CertificateSession(monotonicNanos = SystemClock::elapsedRealtimeNanos)
            val reference = StoredCertificateReference(
                uri = REAL_CERT_URI,
                displayName = "real-e2e-identity.p12",
                mimeType = CertificateRepository.MIME_X_PKCS12,
                size = certificateFile.length(),
                summary = null,
            )
            val repository = CertificateRepository(
                documentAccess = FileCertificateDocumentAccess(certificateFile),
                referenceStore = MemoryReferenceStore(reference),
                loader = Pkcs12Loader(),
            )
            unlockCache = RealE2eUnlockCache(loadedPassword, session)

            TestCertificateDependencies.install(
                gateway = repository,
                session = session,
                unlockCache = unlockCache,
            ).use {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    probeStage = ProbeStage.ENTER_CATALOG
                    enterCatalog(result)
                    probeStage = ProbeStage.OPEN_PORTAL
                    openPortal(portalId, result)
                    probeStage = ProbeStage.WAIT_FOR_WEBVIEW
                    waitForWebView(portalId, result)
                    probeStage = ProbeStage.APPLY_RECIPE
                    applyPortalRecipe(scenario, portalId)
                    probeStage = ProbeStage.OBSERVE_PORTAL
                    observePortal(
                        scenario = scenario,
                        profileCapabilities = profile.capabilities,
                        allowedClientAuthHosts = allowedClientAuthHosts(profile),
                        deepEnabled = deepEnabled,
                        profileId = profileId,
                        result = result,
                    )
                }
            }
        } catch (throwable: Throwable) {
            result.infrastructureError = safeInfrastructureCode(throwable, probeStage)
            result.classification = ProbeClassification.INFRASTRUCTURE_ERROR
        } finally {
            unlockCache?.close()
            password?.fill('\u0000')
            writeResult(result)
        }
    }

    private fun enterCatalog(result: ProbeResult) {
        waitUntil(UI_TIMEOUT_MILLIS) {
            rule.onAllNodesWithText("Continuar").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Continuar").performScrollTo().performClick()
        waitUntil(UI_TIMEOUT_MILLIS) {
            rule.onAllNodesWithText("SERVICIOS PÚBLICOS").fetchSemanticsNodes().isNotEmpty()
        }
        result.certificateUnlocked = true
    }

    private fun openPortal(portalId: String, result: ProbeResult) {
        val output = catalogSmoke(portalId, "OPEN")
        if (!output.contains("OPEN_REQUESTED")) error("Protected catalog OPEN was not accepted")
        result.openRequested = true
        result.level = maxOf(result.level, 1)
    }

    private fun waitForWebView(
        portalId: String,
        result: ProbeResult,
    ) {
        waitUntil(UI_TIMEOUT_MILLIS) {
            catalogSmoke(portalId, "INSPECT").contains("WEBVIEW_ACTIVE")
        }
        result.webViewActive = true
        updateCurrentHostFromRecords(diagnosticRecords(), result)
        result.level = maxOf(result.level, 1)
    }

    private fun catalogSmoke(portalId: String, operation: String): String =
        shell(
            listOf(
                "am", "broadcast", "--user", "0",
                "-a", "dev.junta.firmamobile.action.CATALOG_SMOKE",
                "-p", PACKAGE_NAME,
                "--es", "runId", "real-e2e-${operation.lowercase()}-${portalId.takeLast(20)}",
                "--es", "portalId", portalId,
                "--es", "operation", operation,
            ).joinToString(" "),
        )

    private fun applyPortalRecipe(
        scenario: ActivityScenario<MainActivity>,
        portalId: String,
    ) {
        when (portalId) {
            CARNE_JOVEN_PORTAL_ID -> clickExactAnchor(
                scenario = scenario,
                expectedCurrentUrl = CARNE_JOVEN_ENTRY_URL,
                elementId = CARNE_JOVEN_AUTH_LINK_ID,
                expectedHref = CARNE_JOVEN_AUTH_HREF,
            )
            OVORION_PORTAL_ID -> clickExactAuthButton(
                scenario = scenario,
                expectedCurrentUrl = OVORION_ENTRY_URL,
                elementId = OVORION_AUTH_BUTTON_ID,
                expectedValue = OVORION_AUTH_BUTTON_VALUE,
                expectedOnClick = OVORION_AUTH_BUTTON_ONCLICK,
            )
            OFVIRTUAL_PORTAL_ID -> clickExactAuthButton(
                scenario = scenario,
                expectedCurrentUrl = OFVIRTUAL_ENTRY_URL,
                elementId = OFVIRTUAL_AUTH_BUTTON_ID,
                expectedValue = OFVIRTUAL_AUTH_BUTTON_VALUE,
                expectedOnClick = OFVIRTUAL_AUTH_BUTTON_ONCLICK,
            )
            BURGOS_PORTAL_ID -> clickStaCertificateLogin(
                scenario = scenario,
                expectedEntryUrl = BURGOS_ENTRY_URL,
                expectedIdentifyHref = BURGOS_IDENTIFY_HREF,
                expectedLoginUrl = BURGOS_LOGIN_URL,
            )
            HUESCA_PORTAL_ID -> clickStaCertificateLogin(
                scenario = scenario,
                expectedEntryUrl = HUESCA_ENTRY_URL,
                expectedIdentifyHref = HUESCA_IDENTIFY_HREF,
                expectedLoginUrl = HUESCA_LOGIN_URL,
            )
            EIVISSA_INSTITUTIONAL_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = EIVISSA_INSTITUTIONAL_ENTRY_URL,
                    expectedLabel = EIVISSA_SEDE_LABEL,
                    expectedHref = EIVISSA_SEDE_HOME_ES_URL,
                )
                clickStaCertificateLogin(
                    scenario = scenario,
                    expectedEntryUrl = EIVISSA_SEDE_HOME_ES_URL,
                    expectedIdentifyLabel = EIVISSA_IDENTIFY_LABEL,
                    expectedIdentifyHref = EIVISSA_IDENTIFY_HREF,
                    expectedLoginUrl = EIVISSA_LOGIN_URL,
                )
            }
            EIVISSA_SEDE_PORTAL_ID -> clickStaCertificateLogin(
                scenario = scenario,
                expectedEntryUrl = EIVISSA_SEDE_HOME_URL,
                expectedIdentifyLabel = EIVISSA_IDENTIFY_LABEL,
                expectedIdentifyHref = EIVISSA_IDENTIFY_HREF,
                expectedLoginUrl = EIVISSA_LOGIN_URL,
            )
            ALBACETE_PORTAL_ID -> runSedipualbaClientTlsRecipe(
                scenario = scenario,
                expectedEntryUrl = ALBACETE_ENTRY_URL,
                expectedLoginLabel = SEDIPUALBA_LOGIN_LABEL_ES,
                expectedLoginHref = ALBACETE_LOGIN_URL,
                expectedSourceHost = ALBACETE_SOURCE_HOST,
                expectedIdioma = SEDIPUALBA_IDIOMA_ES,
                expectedCertificateAlt = SEDIPUALBA_CERTIFICATE_ALT_ES,
            )
            LEON_PORTAL_ID -> runSedipualbaClientTlsRecipe(
                scenario = scenario,
                expectedEntryUrl = LEON_ENTRY_URL,
                expectedLoginLabel = SEDIPUALBA_LOGIN_LABEL_ES,
                expectedLoginHref = LEON_LOGIN_URL,
                expectedSourceHost = LEON_SOURCE_HOST,
                expectedIdioma = SEDIPUALBA_IDIOMA_ES,
                expectedCertificateAlt = SEDIPUALBA_CERTIFICATE_ALT_ES,
            )
            MALLORCA_INSTITUTIONAL_PORTAL_ID, MALLORCA_SEDE_PORTAL_ID ->
                runSedipualbaClientTlsRecipe(
                    scenario = scenario,
                    expectedEntryUrl = MALLORCA_ENTRY_URL,
                    expectedLoginLabel = SEDIPUALBA_LOGIN_LABEL_CA,
                    expectedLoginHref = MALLORCA_LOGIN_URL,
                    expectedSourceHost = MALLORCA_SOURCE_HOST,
                    expectedIdioma = SEDIPUALBA_IDIOMA_CA,
                    expectedCertificateAlt = SEDIPUALBA_CERTIFICATE_ALT_CA,
                )
            MENORCA_INSTITUTIONAL_PORTAL_ID, MENORCA_SEDE_PORTAL_ID ->
                runMenorcaClientTlsRecipe(scenario)
            LA_RIOJA_PORTAL_ID -> clickLaRiojaCertificateLogin(scenario)
            NAVARRA_PORTAL_ID -> runNavarraClientTlsRecipe(scenario)
            ASTURIAS_PORTAL_ID -> runAsturiasClaveCertificateRecipe(scenario)
            CATALUNYA_PETICIO_PORTAL_ID -> runCatalunyaPeticioClaveRecipe(scenario)
            OURENSE_PORTAL_ID -> runOurenseClaveCertificateRecipe(scenario)
            TEA_PORTAL_ID -> clickExactLabeledAnchor(
                scenario = scenario,
                expectedCurrentUrl = TEA_ENTRY_URL,
                expectedLabel = TEA_AUTH_LABEL,
                expectedHref = TEA_AUTH_HREF,
            )
            SANIDAD_PORTAL_ID -> runSanidadClientTlsRecipe(scenario)
            VEA_PORTAL_ID -> runVeaCertificateAuthRecipe(scenario)
            TGSS_PORTAL_ID -> clickTgssIpceAuth(scenario)
            CORUNA_PORTAL_ID -> runCorunaClaveCertificateRecipe(scenario)
            MELILLA_PORTAL_ID -> clickMelillaAutofirmaEntry(scenario)
            MUGEJU_PORTAL_ID, DIPUTACION_SEVILLA_PORTAL_ID -> clickClaveAfirmaProvider(scenario)
            VALLADOLID_PORTAL_ID -> clickExactLabeledAnchor(
                scenario = scenario,
                expectedCurrentUrl = VALLADOLID_ENTRY_URL,
                expectedLabel = VALLADOLID_CERT_LABEL,
                expectedHref = VALLADOLID_CERT_HREF,
            )
            SORIA_PORTAL_ID -> clickExactButton(
                scenario = scenario,
                expectedCurrentUrl = SORIA_ENTRY_URL,
                expectedElementId = SORIA_CERT_BUTTON_ID,
                expectedLabel = SORIA_CERT_BUTTON_LABEL,
                expectedAriaLabel = null,
                expectedOnClick = SORIA_CERT_BUTTON_ONCLICK,
            )
            JAEN_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = JAEN_ENTRY_URL,
                    expectedLabel = JAEN_LOGIN_LABEL,
                    expectedHref = JAEN_LOGIN_HREF,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = JAEN_LOGIN_URL,
                    expectedLabel = JAEN_CERT_LABEL,
                    expectedHref = JAEN_CERT_HREF,
                    waitForExpectedUrl = true,
                )
            }
            BADAJOZ_PORTAL_ID -> {
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = BADAJOZ_ENTRY_URL,
                    expectedElementId = null,
                    expectedLabel = BADAJOZ_CONTINUE_LABEL,
                    expectedAriaLabel = null,
                    expectedOnClick = BADAJOZ_CONTINUE_ONCLICK,
                )
                clickExactAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = BADAJOZ_LOGIN_PAGE_URL,
                    elementId = BADAJOZ_LOGIN_LINK_ID,
                    expectedHref = BADAJOZ_LOGIN_LINK_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = BADAJOZ_LOGIN_PAGE_URL,
                    expectedElementId = BADAJOZ_CERT_BUTTON_ID,
                    expectedLabel = BADAJOZ_CERT_BUTTON_LABEL,
                    expectedAriaLabel = null,
                    expectedOnClick = BADAJOZ_CERT_BUTTON_ONCLICK,
                    waitForBadajozSignHook = true,
                )
            }
            AEAT_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = AEAT_ENTRY_URL,
                    expectedLabel = AEAT_CENSAL_DATA_LABEL,
                    expectedHref = AEAT_CENSAL_DATA_HREF,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = AEAT_CERTIFICATE_PAGE_URL,
                    expectedElementId = null,
                    expectedLabel = AEAT_CERTIFICATE_BUTTON_LABEL,
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                )
            }
            LLEIDA_PORTAL_ID -> {
                clickExactAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = LLEIDA_LOGIN_PAGE_URL,
                    elementId = LLEIDA_LOGIN_LINK_ID,
                    expectedHref = LLEIDA_LOGIN_LINK_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = LLEIDA_LOGIN_PAGE_URL,
                    expectedElementId = LLEIDA_CERT_BUTTON_ID,
                    expectedLabel = null,
                    expectedAriaLabel = LLEIDA_CERT_BUTTON_ARIA_LABEL,
                    expectedOnClick = LLEIDA_CERT_BUTTON_ONCLICK,
                )
            }
            DIPUTACION_SEVILLA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = DIPUTACION_SEVILLA_INDEX_URL,
                    expectedLabel = DIPUTACION_SEVILLA_AUTH_LABEL,
                    expectedHref = DIPUTACION_SEVILLA_AUTH_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = DIPUTACION_SEVILLA_AUTH_URL,
                    expectedElementId = null,
                    expectedLabel = DIPUTACION_SEVILLA_AUTH_BUTTON_LABEL,
                    expectedAriaLabel = null,
                    expectedOnClick = DIPUTACION_SEVILLA_AUTH_BUTTON_ONCLICK,
                    waitForExpectedUrl = true,
                )
            }
            SEVILLA_PORTAL_ID -> clickExactContainedAnchor(
                scenario = scenario,
                expectedCurrentUrl = SEVILLA_ENTRY_URL,
                expectedContainerId = SEVILLA_AUTH_CONTAINER_ID,
                expectedLabel = SEVILLA_AUTH_LABEL,
                expectedHref = SEVILLA_AUTH_HREF,
                expectedOnClick = SEVILLA_AUTH_ONCLICK,
            )
            UNIZAR_PORTAL_ID -> clickExactContainedButton(
                scenario = scenario,
                expectedCurrentUrl = UNIZAR_ENTRY_URL,
                expectedContainerId = UNIZAR_AUTH_CONTAINER_ID,
                expectedElementId = UNIZAR_AUTH_ELEMENT_ID,
                expectedLabel = UNIZAR_AUTH_LABEL,
                expectedImageAlt = UNIZAR_AUTH_IMAGE_ALT,
                expectedOnClick = UNIZAR_AUTH_ONCLICK,
            )
            else -> Unit
        }
    }

    private fun clickStaCertificateLogin(
        scenario: ActivityScenario<MainActivity>,
        expectedEntryUrl: String,
        expectedIdentifyLabel: String = STA_IDENTIFY_LABEL,
        expectedIdentifyHref: String,
        expectedLoginUrl: String,
    ) {
        clickExactLabeledAnchor(
            scenario = scenario,
            expectedCurrentUrl = expectedEntryUrl,
            expectedLabel = expectedIdentifyLabel,
            expectedHref = expectedIdentifyHref,
        )
        clickExactAnchor(
            scenario = scenario,
            expectedCurrentUrl = expectedLoginUrl,
            elementId = STA_CERTIFICATE_LINK_ID,
            expectedHref = STA_CERTIFICATE_LINK_HREF,
            waitForExpectedUrl = true,
        )
    }

    private fun runSedipualbaClientTlsRecipe(
        scenario: ActivityScenario<MainActivity>,
        expectedEntryUrl: String,
        expectedLoginLabel: String,
        expectedLoginHref: String,
        expectedSourceHost: String,
        expectedIdioma: String,
        expectedCertificateAlt: String,
    ) {
        clickExactLabeledAnchor(
            scenario = scenario,
            expectedCurrentUrl = expectedEntryUrl,
            expectedLabel = expectedLoginLabel,
            expectedHref = expectedLoginHref,
        )
        clickSedipualbaSslOptionInAuthFrame(
            scenario = scenario,
            expectedLoginUrl = expectedLoginHref,
            expectedSourceHost = expectedSourceHost,
            expectedIdioma = expectedIdioma,
            expectedCertificateAlt = expectedCertificateAlt,
        )
    }

    private fun clickSedipualbaSslOptionInAuthFrame(
        scenario: ActivityScenario<MainActivity>,
        expectedLoginUrl: String,
        expectedSourceHost: String,
        expectedIdioma: String,
        expectedCertificateAlt: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedExpectedSourceOrigin = JSONObject.quote("https://$expectedSourceHost")
        val quotedExpectedIdioma = JSONObject.quote(expectedIdioma)
        val quotedCertificateAlt = JSONObject.quote(expectedCertificateAlt)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForExpectedUrl = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || !recipeUrlMatches(currentUrl, expectedLoginUrl)) {
                    waitingForExpectedUrl = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const expectedOrigin = $quotedExpectedSourceOrigin;
                      const expectedIdioma = $quotedExpectedIdioma;
                      const frames = Array.from(document.querySelectorAll('iframe')).filter(frame => {
                        try {
                          const url = new URL(frame.contentWindow.location.href);
                          const keys = Array.from(url.searchParams.keys());
                          const idToken = url.searchParams.get('idtoken') || '';
                          return url.origin === expectedOrigin &&
                            url.pathname === '/segex/identificacion_opciones.aspx' &&
                            keys.length === 2 &&
                            keys.includes('idtoken') && keys.includes('idioma') &&
                            url.searchParams.get('idioma') === expectedIdioma &&
                            /^[A-Za-z0-9_-]{16,128}$/.test(idToken);
                        } catch (_) {
                          return false;
                        }
                      });
                      if (frames.length === 0) return 0;
                      if (frames.length !== 1) return 2;
                      const doc = frames[0].contentDocument;
                      if (!doc) return 0;
                      const option = doc.getElementById('optSsl');
                      if (!option) return 0;
                      if (option.tagName !== 'TBODY') return 2;
                      const images = Array.from(option.querySelectorAll('img'));
                      if (images.length !== 1) return 2;
                      const image = images[0];
                      let imageUrl;
                      try { imageUrl = new URL(image.getAttribute('src'), frames[0].contentWindow.location.href); }
                      catch (_) { return 2; }
                      if (!imageUrl.pathname.endsWith('/imgs/identificacion/certificado.svg') ||
                          image.getAttribute('alt') !== $quotedCertificateAlt) return 2;
                      option.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForExpectedUrl || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickMelillaAutofirmaEntry(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedFormId = JSONObject.quote(MELILLA_FORM_ID)
        val quotedFormName = JSONObject.quote(MELILLA_FORM_NAME)
        val quotedInitialAction = JSONObject.quote(MELILLA_INITIAL_FORM_ACTION)
        val quotedAuthBase = JSONObject.quote(MELILLA_AUTH_BASE)
        val quotedProcedureId = JSONObject.quote(MELILLA_PROCEDURE_ID)
        val quotedAutofirmaOnClick = JSONObject.quote(MELILLA_AUTOFIRMA_ONCLICK)
        val quotedAutofirmaLabel = JSONObject.quote(MELILLA_AUTOFIRMA_LABEL)
        val expectedHidden = JSONObject(MELILLA_HIDDEN_FIELDS).toString()
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForEntry = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || currentUrl != MELILLA_ENTRY_URL) {
                    waitingForEntry = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const form = document.getElementById($quotedFormId);
                      if (!form) return 0;
                      if (form.tagName !== 'FORM' ||
                          form.getAttribute('name') !== $quotedFormName ||
                          form.getAttribute('method')?.toLowerCase() !== 'post' ||
                          new URL(form.getAttribute('action'), window.location.href).href !== $quotedInitialAction) return 2;
                      if (typeof window.catser !== 'object' || window.catser === null ||
                          window.catser.urlauth !== $quotedAuthBase ||
                          window.catser.dboid !== $quotedProcedureId ||
                          typeof submitFormulario !== 'function') return 2;
                      const expectedHidden = $expectedHidden;
                      const hiddenInputs = Array.from(form.querySelectorAll('input[type="hidden"]'));
                      const expectedNames = Object.keys(expectedHidden);
                      if (hiddenInputs.length !== expectedNames.length) return 2;
                      for (const name of expectedNames) {
                        const matches = hiddenInputs.filter(input => input.getAttribute('name') === name);
                        if (matches.length !== 1 || matches[0].value !== expectedHidden[name]) return 2;
                      }
                      const links = Array.from(form.querySelectorAll('a')).filter(link =>
                        link.getAttribute('onclick') === $quotedAutofirmaOnClick &&
                        link.getAttribute('href') === 'javascript:;'
                      );
                      if (links.length === 0) return 0;
                      if (links.length !== 1) return 2;
                      const link = links[0];
                      const label = (link.innerText || '').trim().replace(/\s+/g, ' ');
                      if (label !== $quotedAutofirmaLabel ||
                          !link.classList.contains('acceso-title') ||
                          !link.classList.contains('acceso-mark') ||
                          !link.classList.contains('autofirma') ||
                          !link.classList.contains('main-element')) return 2;
                      link.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForEntry || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun runCorunaClaveCertificateRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickCorunaClaveAuth(scenario)
        clickClaveAfirmaProvider(scenario)
    }

    private fun clickCorunaClaveAuth(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedFormId = JSONObject.quote(CORUNA_CLAVE_FORM_ID)
        val quotedFormName = JSONObject.quote(CORUNA_CLAVE_FORM_NAME)
        val quotedFormAction = JSONObject.quote(CORUNA_CLAVE_FORM_ACTION)
        val quotedButtonId = JSONObject.quote(CORUNA_CLAVE_BUTTON_ID)
        val quotedButtonName = JSONObject.quote(CORUNA_CLAVE_BUTTON_NAME)
        val quotedButtonLabel = JSONObject.quote(CORUNA_CLAVE_BUTTON_LABEL)
        val expectedHidden = JSONObject(CORUNA_CLAVE_HIDDEN_FIELDS).toString()
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForEntry = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || currentUrl != CORUNA_X004_URL) {
                    waitingForEntry = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const form = document.getElementById($quotedFormId);
                      if (!form) return 0;
                      if (form.tagName !== 'FORM' ||
                          form.getAttribute('name') !== $quotedFormName ||
                          form.getAttribute('method')?.toLowerCase() !== 'post' ||
                          new URL(form.getAttribute('action'), window.location.href).href !== $quotedFormAction) return 2;
                      const expectedHidden = $expectedHidden;
                      const hiddenInputs = Array.from(form.querySelectorAll('input[type="hidden"]'));
                      const expectedNames = Object.keys(expectedHidden);
                      if (hiddenInputs.length !== expectedNames.length) return 2;
                      for (const name of expectedNames) {
                        const matches = hiddenInputs.filter(input => input.getAttribute('name') === name);
                        if (matches.length !== 1 || matches[0].value !== expectedHidden[name]) return 2;
                      }
                      const buttons = Array.from(form.querySelectorAll('button')).filter(button =>
                        button.getAttribute('id') === $quotedButtonId &&
                        button.getAttribute('name') === $quotedButtonName
                      );
                      if (buttons.length === 0) return 0;
                      if (buttons.length !== 1) return 2;
                      const button = buttons[0];
                      const label = (button.innerText || '').trim().replace(/\s+/g, ' ');
                      if (button.type !== 'submit' ||
                          button.getAttribute('onclick') !== null ||
                          button.getAttribute('formaction') !== null ||
                          label !== $quotedButtonLabel ||
                          !button.classList.contains('btn') ||
                          !button.classList.contains('btn-sm') ||
                          !button.classList.contains('boton')) return 2;
                      button.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForEntry || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun runAsturiasClaveCertificateRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickAsturiasClaveAuth(scenario)
        clickClaveAfirmaProvider(scenario)
    }

    private fun runOurenseClaveCertificateRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickExactLabeledAnchor(
            scenario = scenario,
            expectedCurrentUrl = OURENSE_ENTRY_URL,
            expectedLabel = OURENSE_IDENTIFY_LABEL,
            expectedHref = OURENSE_IDENTIFY_HREF,
        )
        clickClaveAfirmaProvider(scenario)
    }

    private fun clickClaveAfirmaProvider(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedAction = JSONObject.quote(CLAVE_REDIRECT_ACTION)
        val quotedOnClick = JSONObject.quote(CLAVE_AFIRMA_ONCLICK)
        val quotedImageSrc = JSONObject.quote(CLAVE_AFIRMA_IMAGE_SRC)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForProvider = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                val uri = currentUrl?.let(Uri::parse)
                if (uri == null ||
                    uri.scheme != "https" ||
                    uri.host != CLAVE_PROVIDER_HOST ||
                    uri.path != CLAVE_PROVIDER_PATH ||
                    uri.queryParameterNames.isNotEmpty()
                ) {
                    waitingForProvider = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const form = Array.from(document.forms).find(candidate =>
                        candidate.getAttribute('name') === 'idpRedirect' &&
                        candidate.getAttribute('method')?.toLowerCase() === 'post' &&
                        new URL(candidate.getAttribute('action'), window.location.href).href === $quotedAction
                      );
                      if (!form) return 0;
                      const saml = form.querySelector('input[name="SAMLRequest"][type="hidden"]');
                      const relay = form.querySelector('input[name="RelayState"][type="hidden"]');
                      const selected = form.querySelector('input[name="SelectedIdP"][type="hidden"]');
                      if (!saml || saml.value.length < 512 ||
                          !relay || relay.value.length < 1 || relay.value.length > 512 ||
                          !selected || selected.value !== '') return 2;
                      const buttons = Array.from(form.querySelectorAll('button')).filter(button =>
                        button.getAttribute('onclick') === $quotedOnClick &&
                        button.classList.contains('idp-button')
                      );
                      if (buttons.length === 0) return 0;
                      if (buttons.length !== 1) return 2;
                      const button = buttons[0];
                      const article = button.closest('article.idp-card2');
                      if (!article) return 2;
                      const images = Array.from(article.querySelectorAll('img.spLogo')).filter(image =>
                        image.getAttribute('src') === $quotedImageSrc
                      );
                      if (images.length !== 1 || typeof selectedIdP !== 'function') return 2;
                      button.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForProvider || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickTgssIpceAuth(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedFormAction = JSONObject.quote(TGSS_IPCE_FORM_ACTION)
        val quotedSelectedAction = JSONObject.quote(TGSS_IPCE_FORM_ACTION_SELECTED)
        val quotedButtonId = JSONObject.quote(TGSS_IPCE_BUTTON_ID)
        val quotedButtonLabel = JSONObject.quote(TGSS_IPCE_BUTTON_LABEL)
        val quotedButtonAria = JSONObject.quote(TGSS_IPCE_BUTTON_ARIA)
        val quotedImageAlt = JSONObject.quote(TGSS_IPCE_IMAGE_ALT)
        val quotedImagePath = JSONObject.quote(TGSS_IPCE_IMAGE_PATH)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForIdp = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                val uri = currentUrl?.let(Uri::parse)
                if (uri == null || uri.scheme != "https" || uri.host != TGSS_IDP_HOST || uri.path != TGSS_IDP_PATH) {
                    waitingForIdp = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const form = Array.from(document.forms).find(candidate =>
                        candidate.getAttribute('name') === 'redirectForm' &&
                        candidate.getAttribute('method')?.toLowerCase() === 'post' &&
                        new URL(candidate.getAttribute('action'), window.location.href).href === $quotedFormAction
                      );
                      if (!form) return 0;
                      const button = document.getElementById($quotedButtonId);
                      if (!button || !form.contains(button)) return 0;
                      const label = (button.innerText || '').trim().replace(/\s+/g, ' ');
                      if (button.tagName !== 'BUTTON' ||
                          button.getAttribute('type') !== 'submit' ||
                          button.getAttribute('aria-label') !== $quotedButtonAria ||
                          label !== $quotedButtonLabel ||
                          new URL(button.getAttribute('formaction'), window.location.href).href !== $quotedSelectedAction)
                        return 2;
                      const images = Array.from(button.querySelectorAll('img')).filter(image => {
                        let target;
                        try { target = new URL(image.getAttribute('src'), window.location.href); }
                        catch (_) { return false; }
                        return target.origin === 'https://idp.seg-social.es' &&
                          target.pathname === $quotedImagePath &&
                          image.getAttribute('alt') === $quotedImageAlt;
                      });
                      if (images.length !== 1) return 2;
                      button.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForIdp || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun runVeaCertificateAuthRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickVeaAuthButton(
            scenario = scenario,
            expectedLabel = VEA_START_LABEL,
            requireAuthModal = false,
        )
        clickVeaAuthButton(
            scenario = scenario,
            expectedLabel = VEA_CERT_LABEL,
            requireAuthModal = true,
        )
    }

    private fun clickVeaAuthButton(
        scenario: ActivityScenario<MainActivity>,
        expectedLabel: String,
        requireAuthModal: Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedLabel = JSONObject.quote(expectedLabel)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForButton = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || !recipeUrlMatches(currentUrl, VEA_ENTRY_URL)) {
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const buttons = Array.from(document.querySelectorAll('button')).filter(button => {
                        const label = (button.innerText || '').trim().replace(/\s+/g, ' ');
                        return label === $quotedLabel;
                      });
                      if (buttons.length === 0) return 0;
                      if (buttons.length !== 1) return 2;
                      const button = buttons[0];
                      if (button.disabled || button.getAttribute('aria-disabled') === 'true') return 2;
                      const modal = button.closest('app-modal-1');
                      if (${if (requireAuthModal) "!modal" else "modal"}) return 2;
                      button.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                        else -> waitingForButton = true
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForButton || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun runSanidadClientTlsRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickExactLabeledAnchor(
            scenario = scenario,
            expectedCurrentUrl = SANIDAD_ENTRY_URL,
            expectedLabel = SANIDAD_REGISTRY_LABEL,
            expectedHref = SANIDAD_REGISTRY_HREF,
        )
        clickExactLabeledAnchor(
            scenario = scenario,
            expectedCurrentUrl = SANIDAD_REGISTRY_URL,
            expectedLabel = SANIDAD_FORMS_LABEL,
            expectedHref = SANIDAD_FORMS_URL,
            waitForExpectedUrl = true,
        )
        clickSanidadTardesCertificate(scenario)
    }

    private fun clickSanidadTardesCertificate(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedTarget = JSONObject.quote(SANIDAD_TARDES_CERT_URL)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForPanel = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || !recipeUrlMatches(currentUrl, SANIDAD_FORMS_URL)) {
                    waitingForPanel = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const panel = document.getElementById('TRAM_TARDESCONPLAN');
                      if (!panel) return 0;
                      if (!panel.classList.contains('panel')) return 2;
                      const title = document.getElementById('idTRAM_TARDESCONPLAN');
                      if (!title || !panel.contains(title)) return 2;
                      const titleText = (title.innerText || '').trim().replace(/\\s+/g, ' ');
                      if (!titleText.startsWith('Tardes con Plan')) return 2;
                      const links = Array.from(panel.querySelectorAll('li.concertificado a')).filter(link => {
                        const label = (link.innerText || '').trim().replace(/\\s+/g, ' ');
                        let target;
                        try { target = new URL(link.getAttribute('href'), window.location.href).href; }
                        catch (_) { return false; }
                        return label === 'Certificado digital' && target === $quotedTarget;
                      });
                      if (links.length === 0) return 0;
                      if (links.length !== 1) return 2;
                      links[0].click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForPanel || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun runCatalunyaPeticioClaveRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickExactLabeledAnchor(
            scenario = scenario,
            expectedCurrentUrl = CATALUNYA_PETICIO_ENTRY_URL,
            expectedLabel = CATALUNYA_SIGNED_START_LABEL,
            expectedHref = CATALUNYA_SIGNED_START_URL,
        )
        clickCatalunyaAccedeix(scenario)
        clickCatalunyaClave(scenario)
        clickClaveAfirmaProvider(scenario)
    }

    private fun clickCatalunyaAccedeix(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForPage = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || !recipeUrlMatches(currentUrl, CATALUNYA_SIGNED_START_URL)) {
                    waitingForPage = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const elements = Array.from(document.querySelectorAll('input')).filter(element =>
                        element.getAttribute('type') === 'button' &&
                        element.value === 'Accedeix' &&
                        element.getAttribute('onclick') ===
                          "location.href='/gsitgf/AppJava/traint/renderitzaruploadSecure.do?reqCode=autenticarFormulariHtml&authMFA=false'" &&
                        element.classList.contains('btn') &&
                        element.classList.contains('btn-form')
                      );
                      if (elements.length === 0) return 0;
                      if (elements.length !== 1) return 2;
                      elements[0].click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForPage || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickCatalunyaClave(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForAoc = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || !catalunyaAocUrlMatches(currentUrl)) {
                    waitingForAoc = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const form = document.getElementById('login-form');
                      if (!form || form.tagName !== 'FORM' ||
                          form.getAttribute('action') !== '/o/oauth2/login' ||
                          (form.getAttribute('method') || '').toLowerCase() !== 'post') return 2;
                      const authMethod = document.getElementById('authMethod');
                      if (!authMethod || authMethod.tagName !== 'INPUT' ||
                          authMethod.getAttribute('type') !== 'hidden' ||
                          authMethod.getAttribute('name') !== 'authMethod') return 2;
                      const element = document.getElementById('btnContinuaClave');
                      if (!element) return 0;
                      const label = (element.innerText || '').trim().replace(/\s+/g, ' ');
                      if (element.tagName !== 'BUTTON' ||
                          label !== 'Cl@ve PIN24, Ciutadans UE ...' ||
                          element.getAttribute('onclick') !== "submitLoginForm('clave')" ||
                          !element.classList.contains('btn-clave') ||
                          element.classList.contains('g-recaptcha')) return 2;
                      element.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForAoc || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun catalunyaAocUrlMatches(actualUrl: String): Boolean {
        val uri = Uri.parse(actualUrl)
        return uri.scheme == "https" &&
            uri.host == CATALUNYA_AOC_HOST &&
            uri.path == CATALUNYA_AOC_PATH &&
            uri.queryParameterNames == CATALUNYA_AOC_QUERY_KEYS &&
            uri.getQueryParameter("lang") == "ca" &&
            uri.getQueryParameter("scope") == "autenticacio_usuari" &&
            uri.getQueryParameter("state") == "state" &&
            uri.getQueryParameter("redirect_uri") == CATALUNYA_AOC_REDIRECT_URI &&
            uri.getQueryParameter("response_type") == "code" &&
            uri.getQueryParameter("client_id") == "gsit.gencat.cat" &&
            uri.getQueryParameter("approval_prompt") == "auto"
    }

    private fun runNavarraClientTlsRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickExactLabeledAnchor(
            scenario = scenario,
            expectedCurrentUrl = NAVARRA_ENTRY_URL,
            expectedLabel = NAVARRA_TRAMITAR_LABEL,
            expectedHref = NAVARRA_RGE_URL,
        )
        clickNavarraCertificateLogin(scenario)
    }

    private fun clickNavarraCertificateLogin(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForRouter = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                val returnUrl = currentUrl?.let(::navarraRouterReturnUrl)
                if (returnUrl == null) {
                    waitingForRouter = true
                    inspected.countDown()
                    return@onActivity
                }
                val quotedReturnUrl = JSONObject.quote(returnUrl)
                webView.evaluateJavascript(
                    """
                    (() => {
                      const expectedReturnUrl = $quotedReturnUrl;
                      const elements = Array.from(document.querySelectorAll('a')).filter(element => {
                        const label = (element.innerText || '').trim().replace(/\s+/g, ' ');
                        if (label !== 'Certificado Digital o DNIe') return false;
                        let target;
                        try { target = new URL(element.getAttribute('href'), window.location.href); }
                        catch (_) { return false; }
                        const keys = Array.from(target.searchParams.keys());
                        return target.origin === 'https://ateka.navarra.es' &&
                          target.pathname === '/ateka/Certificate/login' &&
                          keys.length === 1 && keys[0] === 'returnUrl' &&
                          target.searchParams.get('returnUrl') === expectedReturnUrl &&
                          element.classList.contains('btn') &&
                          element.classList.contains('btn-default') &&
                          element.classList.contains('btn-login');
                      });
                      if (elements.length === 0) return 0;
                      if (elements.length !== 1) return 2;
                      elements[0].click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForRouter || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun navarraRouterReturnUrl(actualUrl: String): String? {
        val uri = Uri.parse(actualUrl)
        if (uri.scheme != "https" ||
            uri.host != NAVARRA_ATEKA_HOST ||
            uri.path != NAVARRA_ROUTER_PATH ||
            uri.queryParameterNames != setOf("ReturnUrl")
        ) return null
        val returnUrl = uri.getQueryParameter("ReturnUrl") ?: return null
        if (returnUrl.length !in 80..4096 || !returnUrl.startsWith(NAVARRA_CALLBACK_PATH)) return null
        val callback = Uri.parse("https://$NAVARRA_ATEKA_HOST$returnUrl")
        val state = callback.getQueryParameter("state") ?: return null
        val nonce = callback.getQueryParameter("nonce") ?: return null
        val challenge = callback.getQueryParameter("code_challenge") ?: return null
        return if (
            callback.path == NAVARRA_CALLBACK_PATH &&
            callback.getQueryParameter("client_id") == "rge" &&
            callback.getQueryParameter("redirect_uri") == NAVARRA_RGE_URL &&
            callback.getQueryParameter("response_type") == "code id_token" &&
            callback.getQueryParameter("response_mode") == "form_post" &&
            callback.getQueryParameter("code_challenge_method") == "S256" &&
            callback.getQueryParameter("ui_locales") == "es" &&
            state.length in 16..2048 &&
            nonce.length in 16..512 &&
            NAVARRA_CODE_CHALLENGE_PATTERN.matches(challenge)
        ) returnUrl else null
    }

    private fun clickAsturiasClaveAuth(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || !recipeUrlMatches(currentUrl, ASTURIAS_ENTRY_URL)) {
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const form = document.getElementById('sytInitForm');
                      if (!form || form.tagName !== 'FORM') return 0;
                      const action = new URL(form.getAttribute('action'), window.location.href);
                      if (action.href !== 'https://tramita.asturias.es/sta/Relec/STARhssoManager' ||
                          (form.getAttribute('method') || '').toLowerCase() !== 'post' ||
                          form.getAttribute('target') !== '_blank') return 2;
                      const expected = {
                        PAGE_CODE: 'CATALOGO',
                        APP_CODE: 'STA',
                        ROOTID: '2',
                        HFC: 'HEADER#FOOTER',
                        dboidSolicitud: '6269000102616541907573',
                        autoFirma: 'false',
                        url: 'Relec/STARhssoManager',
                        fire: 'true',
                        urlBack: '/-/dboid-6269000102616541907573?redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites'
                      };
                      for (const [name, value] of Object.entries(expected)) {
                        const inputs = Array.from(form.querySelectorAll('input')).filter(input => input.name === name);
                        if (inputs.length !== 1 || inputs[0].type !== 'hidden' || inputs[0].value !== value) return 2;
                      }
                      const buttons = Array.from(form.querySelectorAll('button')).filter(button => {
                        const label = (button.innerText || '').trim().replace(/\s+/g, ' ');
                        return button.getAttribute('type') === 'button' &&
                          label === 'Con sistema Clave' &&
                          button.getAttribute('onclick') === 'javascript:sendFormCustom(false);';
                      });
                      if (buttons.length === 0) return 0;
                      if (buttons.length !== 1) return 2;
                      buttons[0].click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickLaRiojaCertificateLogin(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForLogin = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || !laRiojaSourceUrlMatches(currentUrl)) {
                    waitingForLogin = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const element = document.getElementById('boton_certificado');
                      if (!element) return 0;
                      const label = (element.innerText || '').trim().replace(/\s+/g, ' ');
                      if (element.tagName !== 'BUTTON' ||
                          element.getAttribute('type') !== 'button' ||
                          label !== 'Conectar' ||
                          element.getAttribute('onclick') !==
                            "loginClientCertSSL('https://ias1.larioja.org/clientcertSSL/login')" ||
                          element.className !== 'btn btn-success') return 2;
                      element.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForLogin || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun laRiojaSourceUrlMatches(actualUrl: String): Boolean {
        val uri = Uri.parse(actualUrl)
        if (uri.scheme != "https" ||
            uri.host != LA_RIOJA_HOST ||
            uri.path != LA_RIOJA_SOURCE_PATH ||
            uri.queryParameterNames != LA_RIOJA_SOURCE_QUERY_KEYS ||
            uri.getQueryParameter("inst") != "G" ||
            uri.getQueryParameter("apli") != "OFIVIR" ||
            uri.getQueryParameter("nodo") != "CIUDANO"
        ) return false
        val param = uri.getQueryParameter("param") ?: return false
        if (!LA_RIOJA_PARAM_PATTERN.matches(param)) return false
        val target = Uri.parse(uri.getQueryParameter("TARGET") ?: return false)
        val uuid = target.getQueryParameter("uuidep") ?: return false
        return target.scheme == "https" &&
            target.host == LA_RIOJA_HOST &&
            target.path == LA_RIOJA_TARGET_PATH &&
            target.queryParameterNames == setOf("act_codi", "uuidep") &&
            target.getQueryParameter("act_codi") == "24697" &&
            LA_RIOJA_UUID_PATTERN.matches(uuid)
    }

    private fun runMenorcaClientTlsRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickExactAnchor(
            scenario = scenario,
            expectedCurrentUrl = MENORCA_ENTRY_URL,
            elementId = MENORCA_START_LINK_ID,
            expectedHref = MENORCA_START_LINK_HREF,
        )
        clickMenorcaCertificateSubmit(scenario)
    }

    private fun clickMenorcaCertificateSubmit(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForLogin = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl == null || !menorcaLoginUrlMatches(currentUrl)) {
                    waitingForLogin = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const element = document.getElementById('ctl00_Content1_Button1');
                      if (!element) return 0;
                      if (element.tagName !== 'INPUT' ||
                          element.getAttribute('type') !== 'submit' ||
                          element.getAttribute('name') !== 'ctl00${'$'}Content1${'$'}Button1' ||
                          element.value !== 'Certificat electrònic' ||
                          element.className !== 'boton') return 2;
                      element.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForLogin || !clicked) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            return
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun menorcaLoginUrlMatches(actualUrl: String): Boolean {
        val uri = Uri.parse(actualUrl)
        val linkedUrl = uri.getQueryParameter("URL") ?: return false
        return uri.scheme == "https" &&
            uri.host == MENORCA_HOST &&
            uri.path == MENORCA_LOGIN_PATH &&
            uri.queryParameterNames == setOf("URL") &&
            linkedUrl in MENORCA_ALLOWED_LINKED_URLS
    }

    private fun clickExactAnchor(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        elementId: String,
        expectedHref: String,
        waitForExpectedUrl: Boolean = false,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedId = JSONObject.quote(elementId)
        val quotedExpectedHref = JSONObject.quote(expectedHref)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForExpectedUrl = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl != null && currentUrl != expectedCurrentUrl) {
                    if (waitForExpectedUrl) {
                        waitingForExpectedUrl = true
                        inspected.countDown()
                        return@onActivity
                    }
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const element = document.getElementById($quotedId);
                      if (!element) return 0;
                      if (element.getAttribute('href') !== $quotedExpectedHref) return 2;
                      element.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForExpectedUrl) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickExactLabeledAnchor(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        expectedLabel: String,
        expectedHref: String,
        waitForExpectedUrl: Boolean = false,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedExpectedLabel = JSONObject.quote(expectedLabel)
        val quotedExpectedHref = JSONObject.quote(expectedHref)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForExpectedUrl = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl != null && !recipeUrlMatches(currentUrl, expectedCurrentUrl)) {
                    if (waitForExpectedUrl) {
                        waitingForExpectedUrl = true
                    } else {
                        failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    }
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const elements = Array.from(document.querySelectorAll('a')).filter(element => {
                        const label = (element.innerText || '').trim().replace(/\s+/g, ' ');
                        return label === $quotedExpectedLabel &&
                          element.getAttribute('href') === $quotedExpectedHref;
                      });
                      if (elements.length === 0) return 0;
                      if (elements.length !== 1) return 2;
                      elements[0].click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForExpectedUrl) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        val terminalFailureDeadline =
            SystemClock.elapsedRealtime() + RECIPE_TERMINAL_GRACE_MILLIS
        while (SystemClock.elapsedRealtime() < terminalFailureDeadline) {
            if (hasObservedTerminalNavigationFailure()) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        if (hasObservedTerminalNavigationFailure()) return
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickExactContainedAnchor(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        expectedContainerId: String,
        expectedLabel: String,
        expectedHref: String,
        expectedOnClick: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedContainerId = JSONObject.quote(expectedContainerId)
        val quotedExpectedLabel = JSONObject.quote(expectedLabel)
        val quotedExpectedHref = JSONObject.quote(expectedHref)
        val quotedExpectedOnClick = JSONObject.quote(expectedOnClick)
        while (SystemClock.elapsedRealtime() < deadline) {
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl != null && currentUrl != expectedCurrentUrl) {
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const container = document.getElementById($quotedContainerId);
                      if (!container) return 0;
                      const elements = Array.from(container.children)
                        .filter(element => element.tagName === 'A');
                      if (elements.length !== 1) return 2;
                      const element = elements[0];
                      const label = (element.innerText || '').trim().replace(/\s+/g, ' ');
                      if (label !== $quotedExpectedLabel ||
                          element.getAttribute('href') !== $quotedExpectedHref ||
                          element.getAttribute('onclick') !== $quotedExpectedOnClick) return 2;
                      const preventDefault = event => event.preventDefault();
                      element.addEventListener('click', preventDefault, { once: true });
                      element.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickExactContainedButton(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        expectedContainerId: String,
        expectedElementId: String,
        expectedLabel: String,
        expectedImageAlt: String,
        expectedOnClick: String,
    ) {
        // Unizar can expose a transitional DOM before the final certificate button is ready.
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedContainerId = JSONObject.quote(expectedContainerId)
        val quotedElementId = JSONObject.quote(expectedElementId)
        val quotedExpectedLabel = JSONObject.quote(expectedLabel)
        val quotedExpectedImageAlt = JSONObject.quote(expectedImageAlt)
        val quotedExpectedOnClick = JSONObject.quote(expectedOnClick)
        var targetMismatchObserved = false
        while (SystemClock.elapsedRealtime() < deadline) {
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl != null && currentUrl != expectedCurrentUrl) {
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const container = document.getElementById($quotedContainerId);
                      if (!container) return 0;
                      const elements = Array.from(container.querySelectorAll('button'));
                      if (elements.length !== 1) return 2;
                      const element = elements[0];
                      if (element.id !== $quotedElementId ||
                          element.getAttribute('type') !== 'button' ||
                          element.getAttribute('aria-label') !== $quotedExpectedLabel ||
                          element.querySelector('img')?.getAttribute('alt') !== $quotedExpectedImageAlt ||
                          element.getAttribute('onclick') !== $quotedExpectedOnClick) return 2;
                      element.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> targetMismatchObserved = true
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        error(
            if (targetMismatchObserved) {
                "REAL_E2E_RECIPE_TARGET_MISMATCH"
            } else {
                "REAL_E2E_RECIPE_TARGET_TIMEOUT"
            },
        )
    }

    private fun clickExactButton(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        expectedElementId: String?,
        expectedLabel: String?,
        expectedAriaLabel: String?,
        expectedOnClick: String?,
        waitForExpectedUrl: Boolean = false,
        waitForBadajozSignHook: Boolean = false,
    ) {
        require(expectedLabel != null || expectedAriaLabel != null)
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedExpectedElementId = expectedElementId?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedLabel = expectedLabel?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedAriaLabel = expectedAriaLabel?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedOnClick = expectedOnClick?.let { JSONObject.quote(it) } ?: "null"
        val quotedWaitForBadajozSignHook = waitForBadajozSignHook.toString()
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForExpectedUrl = false
            var waitingForBadajozSignHook = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl != null && !recipeUrlMatches(currentUrl, expectedCurrentUrl)) {
                    if (waitForExpectedUrl) {
                        waitingForExpectedUrl = true
                    } else {
                        failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    }
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const expectedId = $quotedExpectedElementId;
                      const expectedLabel = $quotedExpectedLabel;
                      const expectedAriaLabel = $quotedExpectedAriaLabel;
                      const expectedOnClick = $quotedExpectedOnClick;
                      const waitForBadajozSignHook = $quotedWaitForBadajozSignHook;
                      const elements = Array.from(document.querySelectorAll('button')).filter(element => {
                        if (element.getAttribute('type') !== 'button') return false;
                        if (expectedId !== null && element.id !== expectedId) return false;
                        const label = (element.innerText || '').trim().replace(/\s+/g, ' ');
                        if (expectedLabel !== null && label !== expectedLabel) return false;
                        if (expectedAriaLabel !== null &&
                            element.getAttribute('aria-label') !== expectedAriaLabel) return false;
                        return expectedOnClick === null ||
                          element.getAttribute('onclick') === expectedOnClick;
                      });
                      if (elements.length === 0) return 0;
                      if (elements.length !== 1) return 2;
                      if (waitForBadajozSignHook && (
                          window.__jfmBadajozSignHookReady !== true ||
                          typeof window.MiniApplet?.sign !== 'function'
                      )) return 3;
                      elements[0].click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                        "3" -> waitingForBadajozSignHook = true
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (waitingForExpectedUrl) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            if (waitingForBadajozSignHook) {
                SystemClock.sleep(RECIPE_POLL_MILLIS)
                continue
            }
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        val terminalFailureDeadline =
            SystemClock.elapsedRealtime() + RECIPE_TERMINAL_GRACE_MILLIS
        while (SystemClock.elapsedRealtime() < terminalFailureDeadline) {
            if (hasObservedTerminalNavigationFailure()) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        if (hasObservedTerminalNavigationFailure()) return
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun recipeUrlMatches(actualUrl: String, expectedUrl: String): Boolean =
        actualUrl == expectedUrl ||
            (expectedUrl.endsWith("/") && actualUrl == expectedUrl.dropLast(1)) ||
            (!expectedUrl.endsWith("/") && actualUrl == "$expectedUrl/")

    private fun clickExactAuthButton(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        elementId: String,
        expectedValue: String,
        expectedOnClick: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedId = JSONObject.quote(elementId)
        val quotedExpectedValue = JSONObject.quote(expectedValue)
        val quotedExpectedOnClick = JSONObject.quote(expectedOnClick)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView")
                    .apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                val currentUrl = webView.url
                if (currentUrl != null && currentUrl != expectedCurrentUrl) {
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const element = document.getElementById($quotedId);
                      if (!element) return 0;
                      if (element.getAttribute('type') !== 'button') return 2;
                      if (element.value !== $quotedExpectedValue) return 2;
                      if (element.getAttribute('onclick') !== $quotedExpectedOnClick) return 2;
                      element.click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { recipeCode ->
                    when (recipeCode) {
                        "1" -> clicked = true
                        "2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    }
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        val terminalFailureDeadline =
            SystemClock.elapsedRealtime() + RECIPE_TERMINAL_GRACE_MILLIS
        while (SystemClock.elapsedRealtime() < terminalFailureDeadline) {
            if (hasObservedTerminalNavigationFailure()) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        if (hasObservedTerminalNavigationFailure()) return
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun hasObservedTerminalNavigationFailure(): Boolean =
        diagnosticRecords().any { record ->
            record.contains("event=NETWORK_ERROR") ||
                record.contains("event=SSL_ERROR_CANCELLED") ||
                record.contains("event=NAVIGATION_BLOCKED")
        }

    private fun observePortal(
        scenario: ActivityScenario<MainActivity>,
        profileCapabilities: Set<Capability>,
        allowedClientAuthHosts: Set<String>,
        deepEnabled: Boolean,
        profileId: String,
        result: ProbeResult,
    ) {
        val deadline = SystemClock.elapsedRealtime() + PORTAL_TIMEOUT_MILLIS
        var signingHandled = false
        var certificateSelectionHandled = false
        var clientAuthConfirmations = 0
        var postSignObservationDeadline: Long? = null
        var postSignTracker: PostSignTracker? = null
        val safeAuthSigning = deepEnabled && profileId in SAFE_AUTH_SIGN_PROFILES

        while (
            SystemClock.elapsedRealtime() < deadline ||
                postSignObservationDeadline?.let { SystemClock.elapsedRealtime() < it } == true
        ) {
            val records = diagnosticRecords()
            updateRecordObservations(records, result)
            postSignTracker?.let { updatePostSignObservations(records, it, result) }
            updateCurrentHostFromRecords(records, result)

            if (result.hasTerminalSecurityFailure()) {
                result.classification = ProbeClassification.FAIL_SECURITY_OR_NETWORK
                return
            }

            if (Capability.CLIENT_TLS_AUTH in profileCapabilities &&
                rule.onAllNodesWithText("Acceso con certificado").fetchSemanticsNodes().isNotEmpty()
            ) {
                result.clientAuthObserved = true
                result.level = maxOf(result.level, 2)
                val host = allowedClientAuthHosts.firstOrNull { allowedHost ->
                    rule.onAllNodesWithText("Dominio: $allowedHost").fetchSemanticsNodes().isNotEmpty()
                }
                if (host == null) {
                    result.unexpectedClientAuthHost = true
                    rule.onAllNodesWithText("Cancelar").fetchSemanticsNodes().firstOrNull()?.let {
                        rule.onNodeWithText("Cancelar").performClick()
                    }
                    result.classification = ProbeClassification.FAIL_UNEXPECTED_CLIENT_AUTH_HOST
                    return
                }
                if (clientAuthConfirmations >= MAX_CLIENT_AUTH_CONFIRMATIONS) {
                    result.classification = ProbeClassification.FAIL_CLIENT_AUTH_LOOP
                    return
                }
                clientAuthConfirmations++
                result.clientAuthConfirmed = true
                result.level = maxOf(result.level, 3)
                rule.onNodeWithText("Continuar").performClick()
            }

            if (Capability.SELECT_CERTIFICATE in profileCapabilities && !certificateSelectionHandled &&
                rule.onAllNodesWithText("Compartir certificado").fetchSemanticsNodes().isNotEmpty()
            ) {
                result.certificateSelectionObserved = true
                result.level = maxOf(result.level, 3)
                rule.onNodeWithText("Compartir").performClick()
                result.publicCertificateShared = true
                certificateSelectionHandled = true
                result.level = maxOf(result.level, 4)
            }

            val signingConfirmationVisible = if (safeAuthSigning) {
                !signingHandled && currentSigningState(scenario) is SigningUiState.AwaitingConfirmation
            } else {
                rule.onAllNodesWithText("Solicitud de firma").fetchSemanticsNodes().isNotEmpty()
            }
            if (Capability.SIGN in profileCapabilities && !signingHandled &&
                signingConfirmationVisible
            ) {
                result.signingConfirmationObserved = true
                result.level = maxOf(result.level, 3)
                if (deepEnabled && profileId in SAFE_AUTH_SIGN_PROFILES) {
                    val preSignRecords = diagnosticRecords()
                    val signingEvidenceTracker = SigningEvidenceTracker(preSignRecords)
                    check(clickSigningConfirmation()) {
                        "REAL_E2E_SIGN_CONFIRMATION_CLICK_TIMEOUT"
                    }
                    result.signingConfirmed = true
                    val completedRecords = waitForSigningTerminalState(
                        scenario = scenario,
                        tracker = signingEvidenceTracker,
                        result = result,
                    )
                    if (completedRecords != null) {
                        updateSigningEvidence(completedRecords, signingEvidenceTracker, result)
                        val tracker = PostSignTracker(
                            baselineNavigation = latestMainFrameNavigation(completedRecords),
                            previousRecords = completedRecords,
                        )
                        postSignTracker = tracker
                        postSignObservationDeadline =
                            SystemClock.elapsedRealtime() + POST_SIGN_TIMEOUT_MILLIS
                        updatePostSignObservations(
                            diagnosticRecords(),
                            tracker,
                            result,
                        )
                    }
                } else {
                    rule.onNodeWithText("Cancelar").performClick()
                    result.signingCancelledAtBoundary = true
                }
                signingHandled = true
            }

            if (isObservationComplete(
                    capabilities = profileCapabilities,
                    profileId = profileId,
                    result = result,
                    postSignObservationDeadline = postSignObservationDeadline,
                )
            ) {
                classify(profileCapabilities, result)
                return
            }

            if (!safeAuthSigning) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            SystemClock.sleep(POLL_MILLIS)
        }

        classify(profileCapabilities, result)
    }

    private fun waitForSigningTerminalState(
        scenario: ActivityScenario<MainActivity>,
        tracker: SigningEvidenceTracker,
        result: ProbeResult,
    ): List<String>? {
        val deadline = SystemClock.elapsedRealtime() + SIGNING_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            updateSigningEvidence(diagnosticRecords(), tracker, result)
            when (val current = currentSigningState(scenario)) {
                is SigningUiState.Completed -> {
                    result.signatureCompleted = true
                    result.level = maxOf(result.level, 4)
                    return diagnosticRecords()
                }
                is SigningUiState.Failed -> {
                    result.signingFailureCode = current.code.name
                    return null
                }
                else -> Unit
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        result.signingFailureCode = "TIMEOUT"
        return null
    }

    private fun currentSigningState(scenario: ActivityScenario<MainActivity>): SigningUiState? {
        var state: SigningUiState? = null
        scenario.onActivity { activity ->
            val field = MainActivity::class.java.getDeclaredField("signingCoordinator")
                .apply { isAccessible = true }
            state = (field.get(activity) as SigningCoordinator).state.value
        }
        return state
    }

    private fun clickSigningConfirmation(): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = runCatching { automation.rootInActiveWindow }.getOrNull()
            val button = root?.let { findAccessibleTextNode(it, "Firmar") }
            if (button != null && runCatching {
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
            ) {
                return true
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        return false
    }

    private fun findAccessibleTextNode(
        node: AccessibilityNodeInfo,
        expectedText: String,
    ): AccessibilityNodeInfo? {
        if (node.isVisibleToUser && node.text?.toString()?.trim() == expectedText) {
            var candidate: AccessibilityNodeInfo? = node
            repeat(5) {
                val current = candidate ?: return@repeat
                if (current.isVisibleToUser && current.isClickable) {
                    return current
                }
                candidate = current.parent
            }
            return node
        }
        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            findAccessibleTextNode(child, expectedText)?.let { return it }
        }
        return null
    }

    private fun updateSigningEvidence(
        records: List<String>,
        tracker: SigningEvidenceTracker,
        result: ProbeResult,
    ) {
        val newRecords = recordsAddedSince(tracker.previousRecords, records)
        tracker.previousRecords = records
        if (newRecords.any { it.contains("event=PORTAL_CALLBACK") }) {
            result.signingCallbackObserved = true
        }
    }

    private fun updatePostSignObservations(
        records: List<String>,
        tracker: PostSignTracker,
        result: ProbeResult,
    ) {
        val newRecords = recordsAddedSince(tracker.previousRecords, records)
        tracker.previousRecords = records

        if (newRecords.any { it.contains("event=PORTAL_CALLBACK") }) {
            result.postSignCallbackObserved = true
        }
        if (newRecords.any { isPortalAuthSuccessRecord(it) }) {
            result.portalAuthSuccess = true
            result.postSignPortalAuthSuccess = true
            result.authenticatedReturnObserved = true
            result.level = maxOf(result.level, 5)
        }

        val navigations = newRecords.mapNotNull(::parseMainFrameNavigation)
        if (navigations.isEmpty()) return

        result.postSignNavigationObserved = true
        result.postSignPageFinished = result.postSignPageFinished || navigations.any {
            it.event == "PAGE_FINISHED"
        }
        tracker.baselineNavigation?.let { baseline ->
            result.postSignHostChanged = result.postSignHostChanged || navigations.any {
                it.host != baseline.host
            }
            result.postSignPathChanged = result.postSignPathChanged || navigations.any {
                it.pathLength != baseline.pathLength || it.pathSha2568 != baseline.pathSha2568
            }
        }
    }

    private fun isPortalAuthSuccessRecord(record: String): Boolean =
        record.contains("stage=vea-auth-success") ||
            record.contains("stage=auth-success") ||
            record.contains("stage=authentication-success")

    private fun latestMainFrameNavigation(records: List<String>): SanitizedNavigation? =
        records.asReversed().firstNotNullOfOrNull(::parseMainFrameNavigation)

    private fun parseMainFrameNavigation(record: String): SanitizedNavigation? {
        val event = SANITIZED_NAVIGATION_EVENT.find(record)?.groupValues?.getOrNull(1) ?: return null
        if (!SANITIZED_MAIN_FRAME.containsMatchIn(record)) return null
        val host = SANITIZED_HOST.find(record)?.groupValues?.getOrNull(1) ?: return null
        val pathLength = SANITIZED_PATH_LENGTH.find(record)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return null
        val pathSha2568 = SANITIZED_PATH_HASH.find(record)?.groupValues?.getOrNull(1) ?: return null
        return SanitizedNavigation(event, host, pathLength, pathSha2568)
    }

    private fun recordsAddedSince(previous: List<String>, current: List<String>): List<String> {
        if (previous.isEmpty()) return current
        if (current.size >= previous.size && current.take(previous.size) == previous) {
            return current.drop(previous.size)
        }
        val maxOverlap = minOf(previous.size, current.size)
        for (overlap in maxOverlap downTo 1) {
            if (previous.takeLast(overlap) == current.take(overlap)) {
                return current.drop(overlap)
            }
        }
        return emptyList()
    }

    private fun updateRecordObservations(records: List<String>, result: ProbeResult) {
        result.pageStarted = result.pageStarted || records.any { it.contains("event=PAGE_STARTED") }
        result.pageFinished = result.pageFinished || records.any { it.contains("event=PAGE_FINISHED") }
        if (result.pageStarted || result.pageFinished) result.level = maxOf(result.level, 1)

        result.clientCertReceived = result.clientCertReceived || records.any {
            it.contains("stage=client-cert-received")
        }
        result.clientCertProceeded = result.clientCertProceeded || records.any {
            it.contains("stage=client-cert-proceeded")
        }
        if (result.clientCertReceived) result.level = maxOf(result.level, 2)
        if (result.clientCertProceeded) result.level = maxOf(result.level, 4)

        result.portalAuthSuccess = result.portalAuthSuccess || records.any(::isPortalAuthSuccessRecord)
        if (result.portalAuthSuccess) result.level = maxOf(result.level, 5)

        result.clientCertRejected = result.clientCertRejected || records.any {
            it.contains("stage=client-cert-rejected-")
        }
        result.networkError = result.networkError || records.any { it.contains("event=NETWORK_ERROR") }
        result.sslError = result.sslError || records.any { it.contains("event=SSL_ERROR_CANCELLED") }
        result.navigationBlocked = result.navigationBlocked || records.any {
            it.contains("event=NAVIGATION_BLOCKED")
        }
    }

    private fun updateCurrentHostFromRecords(records: List<String>, result: ProbeResult) {
        records.asReversed().firstNotNullOfOrNull { record ->
            SANITIZED_HOST.find(record)?.groupValues?.getOrNull(1)
        }?.let { host ->
            result.currentHost = host
        }
    }

    private fun isObservationComplete(
        capabilities: Set<Capability>,
        profileId: String,
        result: ProbeResult,
        postSignObservationDeadline: Long?,
    ): Boolean {
        if (Capability.CLIENT_TLS_AUTH in capabilities && !result.clientCertProceeded) return false
        if (Capability.SELECT_CERTIFICATE in capabilities && !result.publicCertificateShared) return false
        if (Capability.SIGN in capabilities &&
            !result.signingConfirmationObserved && !result.signatureCompleted
        ) return false
        if (Capability.SIGN in capabilities &&
            profileId in SAFE_AUTH_SIGN_PROFILES &&
            result.signatureCompleted
        ) {
            val deadline = postSignObservationDeadline ?: return false
            if (SystemClock.elapsedRealtime() < deadline) return false
        }
        return result.pageFinished || result.portalAuthSuccess || result.signatureCompleted
    }

    private fun classify(capabilities: Set<Capability>, result: ProbeResult) {
        if (result.classification != ProbeClassification.PENDING) return
        if (result.hasTerminalSecurityFailure()) {
            result.classification = ProbeClassification.FAIL_SECURITY_OR_NETWORK
            return
        }
        val required = mutableListOf<Boolean>()
        if (Capability.CLIENT_TLS_AUTH in capabilities) required += result.clientCertProceeded
        if (Capability.SELECT_CERTIFICATE in capabilities) required += result.publicCertificateShared
        if (Capability.SIGN in capabilities) {
            required += result.signingConfirmationObserved || result.signatureCompleted
        }
        if (required.isEmpty()) {
            result.classification = if (result.webViewActive && (result.pageStarted || result.pageFinished)) {
                ProbeClassification.PASS_BROWSE
            } else {
                ProbeClassification.RECIPE_REQUIRED
            }
        } else if (required.all { it }) {
            val portalAuthAfterRealSign = Capability.SIGN in capabilities &&
                result.signatureCompleted &&
                (result.postSignPortalAuthSuccess || result.authenticatedReturnObserved)
            result.classification = when {
                portalAuthAfterRealSign ||
                    (Capability.SIGN !in capabilities && result.portalAuthSuccess) ->
                    ProbeClassification.PASS_PORTAL_AUTH
                result.signatureCompleted &&
                    (result.signingCallbackObserved ||
                        result.postSignNavigationObserved ||
                        result.postSignCallbackObserved) ->
                    ProbeClassification.PASS_CRYPTO_CALLBACK
                result.signatureCompleted -> ProbeClassification.PASS_REAL_CRYPTO_SIGN
                result.clientCertProceeded -> ProbeClassification.PASS_CLIENT_TLS
                else -> ProbeClassification.PASS_MECHANISM_BOUNDARY
            }
        } else {
            result.classification = if (
                result.profileId in CONSEQ_RECIPE_PROFILES &&
                result.webViewActive &&
                (result.pageStarted || result.pageFinished)
            ) {
                ProbeClassification.BLOCKED_CONSEQUENTIAL_ACTION
            } else {
                ProbeClassification.RECIPE_REQUIRED
            }
        }
    }

    private fun allowedClientAuthHosts(profile: dev.junta.firmamobile.profile.SiteProfile): Set<String> {
        val policy = profile.clientAuthPolicy ?: return emptySet()
        return buildSet {
            policy.requestOrigins.mapTo(this) { it.host }
            policy.requestContinuationUrlConstraints.mapTo(this) { it.origin.host }
        }
    }

    private fun resetBrowserState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cookies = CountDownLatch(1)
        instrumentation.runOnMainSync {
            CookieManager.getInstance().removeAllCookies { cookies.countDown() }
        }
        assertTrue("Cookie reset timed out", cookies.await(5, TimeUnit.SECONDS))

        val clientCert = CountDownLatch(1)
        instrumentation.runOnMainSync {
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            WebView.clearClientCertPreferences { clientCert.countDown() }
        }
        assertTrue("Client-certificate preference reset timed out", clientCert.await(5, TimeUnit.SECONDS))
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }

    private fun diagnosticRecords(): List<String> {
        val file = File(application().filesDir, "qa-navigation.log")
        return if (file.isFile) file.readLines(StandardCharsets.US_ASCII) else emptyList()
    }

    private fun waitUntil(timeoutMillis: Long, predicate: () -> Boolean) {
        rule.waitUntil(timeoutMillis = timeoutMillis, condition = predicate)
    }

    private fun safeInfrastructureCode(throwable: Throwable, stage: ProbeStage): String = when (throwable.message) {
        "REAL_E2E_CERTIFICATE_MISSING" -> "CERTIFICATE_MISSING"
        "REAL_E2E_PASSWORD_MISSING" -> "PASSWORD_MISSING"
        "REAL_E2E_RECIPE_WEBVIEW_MISSING" -> "RECIPE_WEBVIEW_MISSING"
        "REAL_E2E_RECIPE_SOURCE_MISMATCH" -> "RECIPE_SOURCE_MISMATCH"
        "REAL_E2E_RECIPE_TARGET_MISMATCH" -> "RECIPE_TARGET_MISMATCH"
        "REAL_E2E_RECIPE_INSPECT_TIMEOUT" -> "RECIPE_INSPECT_TIMEOUT"
        "REAL_E2E_RECIPE_TARGET_TIMEOUT" -> "RECIPE_TARGET_TIMEOUT"
        else -> {
            val type = throwable.javaClass.simpleName.takeIf { SAFE_ERROR_TOKEN.matches(it) }
                ?: "UNKNOWN_ERROR"
            "${type}_${stage.name}"
        }
    }

    private fun writeResult(result: ProbeResult) {
        val directory = File(application().filesDir, REAL_E2E_DIR).apply { mkdirs() }
        val output = File(directory, RESULT_FILE)
        val json = JSONObject()
            .put("schemaVersion", RESULT_SCHEMA_VERSION)
            .put("portalId", result.portalId)
            .put("profileId", result.profileId)
            .put("classification", result.classification.name)
            .put("level", result.level)
            .put("capabilities", JSONArray(result.capabilities))
            .put("expectedStartHost", result.expectedStartHost)
            .put("currentHost", result.currentHost ?: JSONObject.NULL)
            .put("certificateUnlocked", result.certificateUnlocked)
            .put("openRequested", result.openRequested)
            .put("webViewActive", result.webViewActive)
            .put("pageStarted", result.pageStarted)
            .put("pageFinished", result.pageFinished)
            .put("clientAuthObserved", result.clientAuthObserved)
            .put("clientAuthConfirmed", result.clientAuthConfirmed)
            .put("clientCertReceived", result.clientCertReceived)
            .put("clientCertProceeded", result.clientCertProceeded)
            .put("clientCertRejected", result.clientCertRejected)
            .put("certificateSelectionObserved", result.certificateSelectionObserved)
            .put("publicCertificateShared", result.publicCertificateShared)
            .put("signingConfirmationObserved", result.signingConfirmationObserved)
            .put("signingConfirmed", result.signingConfirmed)
            .put("signingCancelledAtBoundary", result.signingCancelledAtBoundary)
            .put("signatureCompleted", result.signatureCompleted)
            .put("signingCallbackObserved", result.signingCallbackObserved)
            .put("postSignNavigationObserved", result.postSignNavigationObserved)
            .put("postSignPageFinished", result.postSignPageFinished)
            .put("postSignCallbackObserved", result.postSignCallbackObserved)
            .put("postSignHostChanged", result.postSignHostChanged)
            .put("postSignPathChanged", result.postSignPathChanged)
            .put("authenticatedReturnObserved", result.authenticatedReturnObserved)
            .put("postSignPortalAuthSuccess", result.postSignPortalAuthSuccess)
            .put("signingFailureCode", result.signingFailureCode ?: JSONObject.NULL)
            .put("portalAuthSuccess", result.portalAuthSuccess)
            .put("networkError", result.networkError)
            .put("sslError", result.sslError)
            .put("navigationBlocked", result.navigationBlocked)
            .put("unexpectedClientAuthHost", result.unexpectedClientAuthHost)
            .put("infrastructureError", result.infrastructureError ?: JSONObject.NULL)
        output.writeText(json.toString(), StandardCharsets.US_ASCII)
    }

    private fun application(): JuntaFirmaApplication =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JuntaFirmaApplication

    private fun readPassword(file: File): CharArray {
        val bytes = file.readBytes()
        require(bytes.isNotEmpty() && bytes.size <= MAX_PASSWORD_BYTES)
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val decoded = decoder.decode(ByteBuffer.wrap(bytes))
            try {
                CharArray(decoded.remaining()).also(decoded::get)
            } finally {
                if (decoded.hasArray()) decoded.array().fill('\u0000')
            }
        } finally {
            bytes.fill(0)
        }
    }

    private class FileCertificateDocumentAccess(
        private val certificateFile: File,
    ) : CertificateDocumentAccess {
        override fun queryMetadata(uri: Uri) = CertificateDocumentMetadata(
            displayName = "real-e2e-identity.p12",
            mimeType = CertificateRepository.MIME_X_PKCS12,
            size = certificateFile.length(),
        )

        override fun takePersistableReadPermission(uri: Uri) = Unit
        override fun releasePersistableReadPermission(uri: Uri) = Unit
        override fun open(uri: Uri): InputStream {
            check(uri == REAL_CERT_URI)
            return certificateFile.inputStream()
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

    private class RealE2eUnlockCache(
        password: CharArray,
        private val session: CertificateSession,
    ) : CertificateUnlockCache, AutoCloseable {
        private var ownedPassword: CharArray? = password.copyOf()

        override suspend fun store(
            reference: StoredCertificateReference,
            password: CharArray,
            issuedAt: Instant,
            expiresAt: Instant,
            observedAtMonotonicNanos: Long,
        ): Boolean = true

        override suspend fun restore(
            reference: StoredCertificateReference,
            now: Instant,
        ): CachedCertificateUnlock? {
            val copy = synchronized(this) { ownedPassword?.copyOf() } ?: return null
            val lifetime = Duration.ofMinutes(30)
            val expiresAt = now.plus(lifetime)
            return CachedCertificateUnlock(
                password = copy,
                expiresAt = expiresAt,
                lease = session.createUnlockLease(expiresAt, lifetime),
            )
        }

        override fun clear() = Unit

        @Synchronized
        override fun close() {
            ownedPassword?.fill('\u0000')
            ownedPassword = null
        }
    }

    private data class SanitizedNavigation(
        val event: String,
        val host: String,
        val pathLength: Int,
        val pathSha2568: String,
    )

    private class PostSignTracker(
        val baselineNavigation: SanitizedNavigation?,
        var previousRecords: List<String>,
    )

    private class SigningEvidenceTracker(
        var previousRecords: List<String>,
    )

    private data class ProbeResult(
        val portalId: String,
        val profileId: String,
        val capabilities: List<String>,
        val expectedStartHost: String,
        var classification: ProbeClassification = ProbeClassification.PENDING,
        var level: Int = 0,
        var currentHost: String? = null,
        var certificateUnlocked: Boolean = false,
        var openRequested: Boolean = false,
        var webViewActive: Boolean = false,
        var pageStarted: Boolean = false,
        var pageFinished: Boolean = false,
        var clientAuthObserved: Boolean = false,
        var clientAuthConfirmed: Boolean = false,
        var clientCertReceived: Boolean = false,
        var clientCertProceeded: Boolean = false,
        var clientCertRejected: Boolean = false,
        var certificateSelectionObserved: Boolean = false,
        var publicCertificateShared: Boolean = false,
        var signingConfirmationObserved: Boolean = false,
        var signingConfirmed: Boolean = false,
        var signingCancelledAtBoundary: Boolean = false,
        var signatureCompleted: Boolean = false,
        var signingCallbackObserved: Boolean = false,
        var postSignNavigationObserved: Boolean = false,
        var postSignPageFinished: Boolean = false,
        var postSignCallbackObserved: Boolean = false,
        var postSignHostChanged: Boolean = false,
        var postSignPathChanged: Boolean = false,
        var authenticatedReturnObserved: Boolean = false,
        var postSignPortalAuthSuccess: Boolean = false,
        var signingFailureCode: String? = null,
        var portalAuthSuccess: Boolean = false,
        var networkError: Boolean = false,
        var sslError: Boolean = false,
        var navigationBlocked: Boolean = false,
        var unexpectedClientAuthHost: Boolean = false,
        var infrastructureError: String? = null,
    ) {
        fun hasTerminalSecurityFailure(): Boolean =
            clientCertRejected || networkError || sslError || navigationBlocked || unexpectedClientAuthHost
    }

    private enum class ProbeStage {
        INIT,
        RESET_BROWSER,
        ENTER_CATALOG,
        OPEN_PORTAL,
        WAIT_FOR_WEBVIEW,
        APPLY_RECIPE,
        OBSERVE_PORTAL,
    }

    private enum class ProbeClassification {
        PENDING,
        PASS_BROWSE,
        PASS_MECHANISM_BOUNDARY,
        PASS_CLIENT_TLS,
        PASS_REAL_CRYPTO_SIGN,
        PASS_CRYPTO_CALLBACK,
        PASS_PORTAL_AUTH,
        BLOCKED_CONSEQUENTIAL_ACTION,
        RECIPE_REQUIRED,
        FAIL_SECURITY_OR_NETWORK,
        FAIL_UNEXPECTED_CLIENT_AUTH_HOST,
        FAIL_CLIENT_AUTH_LOOP,
        INFRASTRUCTURE_ERROR,
    }

    private companion object {
        const val REAL_E2E_ARGUMENT = "realE2e"
        const val DEEP_ARGUMENT = "realE2eDeep"
        const val PORTAL_ID_ARGUMENT = "portalId"
        const val RESULT_SCHEMA_VERSION = 2
        const val REAL_E2E_DIR = "real-e2e"
        const val CERTIFICATE_FILE = "identity.p12"
        const val PASSWORD_FILE = "password"
        const val RESULT_FILE = "result.json"
        const val PACKAGE_NAME = "dev.junta.firmamobile"
        const val CARNE_JOVEN_PORTAL_ID = "junta-andalucia-carne-joven"
        const val CARNE_JOVEN_ENTRY_URL =
            "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val CARNE_JOVEN_AUTH_LINK_ID = "bot-obtener"
        val CONSEQ_RECIPE_PROFILES = setOf(
            "reg-age-redsara",
        )
        const val CARNE_JOVEN_AUTH_HREF =
            "/carneJoven/servlet/CallAuthenticationServlet"
        const val OVORION_PORTAL_ID = "junta-andalucia-ovorion"
        const val OVORION_ENTRY_URL =
            "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"
        const val OVORION_AUTH_BUTTON_ID = "btnacceso"
        const val OVORION_AUTH_BUTTON_VALUE = "Acceder"
        const val OVORION_AUTH_BUTTON_ONCLICK = "autenticar();"
        const val OFVIRTUAL_PORTAL_ID = "junta-andalucia-ofvirtual"
        const val OFVIRTUAL_ENTRY_URL =
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
        const val OFVIRTUAL_AUTH_BUTTON_ID = "btnacceso"
        const val OFVIRTUAL_AUTH_BUTTON_VALUE = "Acceder"
        const val OFVIRTUAL_AUTH_BUTTON_ONCLICK = "autenticar();"
        const val BURGOS_PORTAL_ID = "diputacion-burgos-portal"
        const val BURGOS_ENTRY_URL =
            "https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?" +
                "APP_CODE=STA&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO"
        const val BURGOS_IDENTIFY_HREF =
            "https://registro.diputaciondeburgos.es/sta/CarpetaPrivate/doEvent?APP_CODE=STA&PAGE_CODE=HOME"
        const val BURGOS_LOGIN_URL = BURGOS_IDENTIFY_HREF
        const val HUESCA_PORTAL_ID = "diputacion-huesca-portal"
        const val HUESCA_ENTRY_URL =
            "https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_HOME"
        const val HUESCA_IDENTIFY_HREF =
            "https://ovc24.dphuesca.es/sta/CarpetaPrivate/Login?APP_CODE=STA&PAGE_CODE=HOME"
        const val HUESCA_LOGIN_URL = HUESCA_IDENTIFY_HREF
        const val STA_IDENTIFY_LABEL = "Identificate"
        const val STA_CERTIFICATE_LINK_ID = "link-certificado"
        const val STA_CERTIFICATE_LINK_HREF =
            "/sta/CarpetaPrivate/Certificate?APP_CODE=STA&PAGE_CODE=HOME"
        const val EIVISSA_INSTITUTIONAL_PORTAL_ID = "eivissa-portal-institucional"
        const val EIVISSA_INSTITUTIONAL_ENTRY_URL = "https://www.conselldeivissa.es/"
        const val EIVISSA_SEDE_PORTAL_ID = "eivissa-sede-electronica"
        const val EIVISSA_SEDE_LABEL = "Seu electrònica"
        const val EIVISSA_SEDE_HOME_ES_URL =
            "https://seu.conselldeivissa.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_HOME&lang=ES"
        const val EIVISSA_SEDE_HOME_URL =
            "https://seu.conselldeivissa.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_HOME"
        const val EIVISSA_IDENTIFY_LABEL = "Identifícate"
        const val EIVISSA_IDENTIFY_HREF =
            "https://seu.conselldeivissa.es/sta/CarpetaPrivate/Login?APP_CODE=STA&PAGE_CODE=HOME"
        const val EIVISSA_LOGIN_URL = EIVISSA_IDENTIFY_HREF
        const val ALBACETE_PORTAL_ID = "diputacion-albacete-portal"
        const val ALBACETE_ENTRY_URL =
            "https://sede.dipualba.es/carpetaciudadana/tramite.aspx?idtramite=567"
        const val ALBACETE_LOGIN_URL =
            "https://sede.dipualba.es/carpetaciudadana/login.aspx?" +
                "returnUrl=https%3a%2f%2fsede.dipualba.es%2fcarpetaciudadana%2ftramite.aspx%3fidtramite%3d567"
        const val ALBACETE_SOURCE_HOST = "sede.dipualba.es"
        const val LEON_PORTAL_ID = "diputacion-leon-sede"
        const val LEON_ENTRY_URL =
            "https://sede.dipuleon.es/carpetaciudadana/tramite.aspx?idtramite=20270"
        const val LEON_LOGIN_URL =
            "https://sede.dipuleon.es/carpetaciudadana/login.aspx?" +
                "returnUrl=https%3a%2f%2fsede.dipuleon.es%2fcarpetaciudadana%2ftramite.aspx%3fidtramite%3d20270"
        const val LEON_SOURCE_HOST = "sede.dipuleon.es"
        const val MALLORCA_INSTITUTIONAL_PORTAL_ID = "mallorca-portal-institucional"
        const val MALLORCA_SEDE_PORTAL_ID = "mallorca-sede-electronica"
        const val MALLORCA_ENTRY_URL =
            "https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082"
        const val MALLORCA_LOGIN_URL =
            "https://cim.secimallorca.net/carpetaciudadana/login.aspx?" +
                "returnUrl=https%3a%2f%2fcim.secimallorca.net%2fsegex%2ftramite.aspx%3fidtramite%3d12082"
        const val MALLORCA_SOURCE_HOST = "cim.secimallorca.net"
        const val SEDIPUALBA_LOGIN_LABEL_ES = "Iniciar sesión"
        const val SEDIPUALBA_LOGIN_LABEL_CA = "Iniciar sessió"
        const val SEDIPUALBA_IDIOMA_ES = "es"
        const val SEDIPUALBA_IDIOMA_CA = "ca"
        const val SEDIPUALBA_SOURCE_PATH = "/segex/identificacion_opciones.aspx"
        const val SEDIPUALBA_CERTIFICATE_ALT_ES =
            "Identificarse con certificado digital a través de nuestro servidor"
        const val SEDIPUALBA_CERTIFICATE_ALT_CA =
            "Identificar-se amb certificat digital a través del nostre servidor"
        val SEDIPUALBA_TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{16,128}")
        const val MELILLA_PORTAL_ID = "melilla-sede"
        const val MELILLA_ENTRY_URL =
            "https://sede.melilla.es/sta/CarpetaPublic/doEvent?" +
                "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999"
        const val MELILLA_FORM_ID = "webAppPageForm"
        const val MELILLA_FORM_NAME = "webAppPageForm"
        const val MELILLA_INITIAL_FORM_ACTION =
            "https://sede.melilla.es/sta/CarpetaPublic/submitAjax.aa"
        const val MELILLA_AUTH_BASE = "https://sede.melilla.es:443/sta"
        const val MELILLA_PROCEDURE_ID = "6269000018479610199999"
        const val MELILLA_AUTOFIRMA_ONCLICK = "submitFormulario(false,true);"
        const val MELILLA_AUTOFIRMA_LABEL = "Con Autofirma"
        val MELILLA_HIDDEN_FIELDS = mapOf(
            "eventScreenId" to "",
            "eventComponent" to "",
            "eventObject" to "",
            "eventAction" to "",
            "eventArguments" to "",
            "PAGE_CODE" to "CATALOGO",
            "APP_CODE" to "STA",
            "PAGE_COMPLETE" to "",
            "ROOTID" to "1",
            "HFC" to "HEADER#FOOTER",
            "SESSION_REQUIRED" to "false",
            "dboidSolicitud" to MELILLA_PROCEDURE_ID,
            "autoFirma" to "false",
            "fire" to "false",
            "url" to "Relec/TramitaForm",
            "urlBack" to " /sta/CarpetaPublic/?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999",
        )
        const val CORUNA_PORTAL_ID = "diputacion-a-coruna-portal"
        const val CORUNA_X004_URL =
            "https://sede.dacoruna.gal/tramitador/entrada?" +
                "idLogica=accesoDirecto&entrada=ciudadano&idEntidad=diputacion&idExpediente=X004&fkIdioma=GL"
        const val CORUNA_CLAVE_FORM_ID = "formularioExternoClave"
        const val CORUNA_CLAVE_FORM_NAME = "formularioExternoClave"
        const val CORUNA_CLAVE_FORM_ACTION = "https://sede.dacoruna.gal/SP2/TiWorksRequest"
        const val CORUNA_CLAVE_BUTTON_ID = "acceso"
        const val CORUNA_CLAVE_BUTTON_NAME = "acceso"
        const val CORUNA_CLAVE_BUTTON_LABEL = "Entrar con Cl@ve"
        val CORUNA_CLAVE_HIDDEN_FIELDS = mapOf(
            "entrada" to "ciudadano",
            "idEntidad" to "diputacion",
            "idioma" to "gl",
            "modo" to "Clave2CiudadanoAuthentication",
            "tipoDeLogado" to "externo",
            "idLogica" to "accesoDirecto",
            "idExpediente" to "X004",
            "detalleExpediente" to "",
            "idExpedienteOrigen" to "",
            "idVersionProceso" to "",
            "idConvocatoria" to "",
        )
        const val MUGEJU_PORTAL_ID = "age-mutualidad-general-judicial-mugeju"
        const val CLAVE_PROVIDER_HOST = "pasarela.clave.gob.es"
        const val CLAVE_PROVIDER_PATH = "/Proxy2/ServiceProvider"
        const val CLAVE_REDIRECT_ACTION =
            "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val CLAVE_AFIRMA_ONCLICK =
            "JAVASCRIPT:selectedIdP('AFIRMA');idpRedirect.submit();"
        const val CLAVE_AFIRMA_IMAGE_SRC = "ImageRetrieve?id=IDP_AFIRMA"
        const val TGSS_PORTAL_ID = "tgss-importass"
        const val TGSS_IDP_HOST = "idp.seg-social.es"
        const val TGSS_IDP_PATH = "/PGIS/Login"
        const val TGSS_IPCE_FORM_ACTION = "https://idp.seg-social.es/PGIS/Login"
        const val TGSS_IPCE_FORM_ACTION_SELECTED =
            "https://idp.seg-social.es/PGIS/Login?seleccion=IPCE"
        const val TGSS_IPCE_BUTTON_ID = "IPCEIdP"
        const val TGSS_IPCE_BUTTON_LABEL = "DNIe o certificado"
        const val TGSS_IPCE_BUTTON_ARIA = "Acceder a DNIe o certificado"
        const val TGSS_IPCE_IMAGE_ALT = "Certificado admitido por la GISS"
        const val TGSS_IPCE_IMAGE_PATH =
            "/PasarelaStaticAuth/images-pasarela/Componentes/Botones/IPCE.svg"
        const val VEA_PORTAL_ID = "junta-andalucia-sede"
        const val VEA_ENTRY_URL =
            "https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA"
        const val VEA_START_LABEL = "INICIAR SOLICITUD"
        const val VEA_CERT_LABEL = "Acceder con certificado electrónico"
        const val SANIDAD_PORTAL_ID = "age-ministerio-de-sanidad"
        const val SANIDAD_ENTRY_URL = "https://sede.mscbs.gob.es/"
        const val SANIDAD_REGISTRY_LABEL = "Registro electrónico"
        const val SANIDAD_REGISTRY_HREF = "/registroElectronico/home.htm"
        const val SANIDAD_REGISTRY_URL =
            "https://sede.mscbs.gob.es/registroElectronico/home.htm"
        const val SANIDAD_FORMS_LABEL = "índice de formularios"
        const val SANIDAD_FORMS_URL =
            "https://sede.mscbs.gob.es/registroElectronico/formularios.htm"
        const val SANIDAD_TARDES_CERT_URL =
            "https://sede.mscbs.gob.es/SIGEM_AutenticacionWeb/validacionCertificado.do?" +
                "REDIRECCION=RegistroTelematico&tramiteId=TRAM_TARDESCONPLAN&" +
                "ENTIDAD_ID=000&LANG=es&COUNTRY=ES"
        const val TEA_PORTAL_ID =
            "age-sede-electronica-de-los-tribunales-economico-administrativos-tea"
        const val TEA_ENTRY_URL = "https://sede.tea.hacienda.gob.es/TEA/alegaciones.html"
        const val TEA_AUTH_LABEL = "Alegaciones"
        const val TEA_AUTH_HREF =
            "https://www1.tea.hacienda.gob.es/wlpl/TEAC-TRAM/SedeTRAM?tram=0"
        const val OURENSE_PORTAL_ID = "diputacion-ourense-sede"
        const val OURENSE_ENTRY_URL =
            "https://sede.depourense.es/sta/CarpetaPublic/doEvent?" +
                "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000946476474507610&lang=ES"
        const val OURENSE_IDENTIFY_LABEL = "Identificate"
        const val OURENSE_IDENTIFY_HREF =
            "https://sede.depourense.es/sta/CarpetaPrivate/Login?APP_CODE=STA&PAGE_CODE=HOME"
        const val CATALUNYA_PETICIO_PORTAL_ID = "catalunya-tramits-peticio-generica"
        const val CATALUNYA_PETICIO_ENTRY_URL =
            "https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?" +
                "category=72461610-a82c-11e3-a972-000c29052e2c"
        const val CATALUNYA_SIGNED_START_LABEL =
            "Inicia . Ves a Presentar amb signatura electrònica"
        const val CATALUNYA_SIGNED_START_URL =
            "https://ovt.gencat.cat/gsitgf/AppJava/traint/renderitzar.do?" +
                "reqCode=inicial&set-locale=ca_ES&idioma=ca_ES&idServei=ING001HTM2&" +
                "urlRetorn=https%3A%2F%2Ftramits.gencat.cat%2Fca%2Ftramits%2Ftramits-temes%2F" +
                "Peticio-generica%3Fcategory%3D72461610-a82c-11e3-a972-000c29052e2c"
        const val CATALUNYA_AOC_HOST = "valid.aoc.cat"
        const val CATALUNYA_AOC_PATH = "/o/oauth2/auth"
        const val CATALUNYA_AOC_REDIRECT_URI =
            "https://ovt.gencat.cat/gsitfc/AppJava/redirectservlet"
        val CATALUNYA_AOC_QUERY_KEYS = setOf(
            "lang", "scope", "state", "redirect_uri", "response_type", "client_id", "approval_prompt",
        )
        const val NAVARRA_PORTAL_ID = "navarra-sede-registro-general"
        const val NAVARRA_ENTRY_URL =
            "https://www.navarra.es/es/tramites/on/-/line/registro-general-electronico"
        const val NAVARRA_TRAMITAR_LABEL = "Tramitar"
        const val NAVARRA_RGE_URL =
            "https://administracionelectronica.navarra.es/RGE2/Default.aspx?idioma=es"
        const val NAVARRA_ATEKA_HOST = "ateka.navarra.es"
        const val NAVARRA_ROUTER_PATH = "/ateka/router"
        const val NAVARRA_CALLBACK_PATH = "/ateka/connect/authorize/callback"
        val NAVARRA_CODE_CHALLENGE_PATTERN = Regex("[A-Za-z0-9_-]{32,128}")
        const val ASTURIAS_PORTAL_ID = "asturias-miprincipado-sede"
        const val ASTURIAS_ENTRY_URL =
            "https://miprincipado.asturias.es/-/dboid-6269000102616541907573?" +
                "redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites"
        const val LA_RIOJA_PORTAL_ID = "la-rioja-oficina-electronica"
        const val LA_RIOJA_HOST = "ias1.larioja.org"
        const val LA_RIOJA_SOURCE_PATH = "/casLR/login"
        const val LA_RIOJA_TARGET_PATH = "/oficinavirtual/presentacion"
        val LA_RIOJA_SOURCE_QUERY_KEYS = setOf("inst", "apli", "nodo", "param", "TARGET")
        val LA_RIOJA_PARAM_PATTERN = Regex("[A-Za-z0-9_-]{16,256}")
        val LA_RIOJA_UUID_PATTERN = Regex("[0-9a-f]{40}")
        const val VALLADOLID_PORTAL_ID = "diputacion-valladolid-sede"
        const val VALLADOLID_ENTRY_URL =
            "https://www.sede.diputaciondevalladolid.es/tgauth/login"
        const val VALLADOLID_CERT_LABEL = "ACCESO CON CERTIFICADO DIGITAL"
        const val VALLADOLID_CERT_HREF = "/c/portal/cert-login"
        const val SORIA_PORTAL_ID = "diputacion-soria-sede"
        const val SORIA_ENTRY_URL =
            "https://portaltramitador.dipsoria.es/web/inicioWebc.do?" +
                "opcion=cargar&redirige=L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%3D&entidad=SORIA&idioma=1"
        const val SORIA_CERT_BUTTON_ID = "b_certificado"
        const val SORIA_CERT_BUTTON_LABEL = "Acceder"
        const val SORIA_CERT_BUTTON_ONCLICK = "pulsarCertificado();"
        const val JAEN_PORTAL_ID = "diputacion-jaen-sede"
        const val JAEN_ENTRY_URL = "https://sede.dipujaen.es/SolicitudGenerica"
        const val JAEN_LOGIN_LABEL = "Acceder"
        const val JAEN_LOGIN_HREF = "/IniciarSesion"
        const val JAEN_LOGIN_URL = "https://sede.dipujaen.es/IniciarSesion"
        const val JAEN_CERT_LABEL = "Acceder con certificado digital"
        const val JAEN_CERT_HREF = "/IniciarSesion/Certificado"
        const val MENORCA_INSTITUTIONAL_PORTAL_ID = "menorca-portal-institucional"
        const val MENORCA_SEDE_PORTAL_ID = "menorca-sede-electronica"
        const val MENORCA_HOST = "www.carpetaciutadana.org"
        const val MENORCA_ENTRY_URL =
            "https://www.carpetaciutadana.org/cime/gesserveis/Gestion.aspx?IDGESTION=990100262"
        const val MENORCA_START_LINK_ID = "ctl00_Content1_HyperLink1"
        const val MENORCA_START_LINK_HREF =
            "https://www.carpetaciutadana.org/cime/solicituds/iniciartramit.aspx?TIPO=REGE&IDIOMA=1"
        const val MENORCA_LOGIN_PATH = "/cime/Login/Login.aspx"
        const val MENORCA_LINKED_URL =
            "https://www.carpetaciutadana.org/cime/solicituds/iniciartramit.aspx?TIPO=REGE^IDIOMA=1"
        const val MENORCA_LINKED_URL_LEGACY_SEPARATOR =
            "https://www.carpetaciutadana.org/cime/solicituds/iniciartramit.aspx¿TIPO=REGE^IDIOMA=1"
        val MENORCA_ALLOWED_LINKED_URLS =
            setOf(MENORCA_LINKED_URL, MENORCA_LINKED_URL_LEGACY_SEPARATOR)
        const val BADAJOZ_PORTAL_ID = "diputacion-badajoz-portal"
        const val BADAJOZ_ENTRY_URL = "https://sede.dip-badajoz.es"
        const val BADAJOZ_LOGIN_PAGE_URL =
            "https://sede.dip-badajoz.es/portal/entidades.do?ent_id=10&idioma=1"
        const val BADAJOZ_CONTINUE_LABEL = "CONTINUAR"
        const val BADAJOZ_CONTINUE_ONCLICK =
            "javascript: document.location.href='/portal/entidades.do?ent_id=10&idioma=1'"
        const val BADAJOZ_LOGIN_LINK_ID = "login"
        const val BADAJOZ_LOGIN_LINK_HREF = "javascript: abrirLogin('');"
        const val BADAJOZ_CERT_BUTTON_ID = "firmar"
        const val BADAJOZ_CERT_BUTTON_LABEL = "Certificado digital"
        const val BADAJOZ_CERT_BUTTON_ONCLICK = "pulsarFirmarIdentificate();"
        const val AEAT_PORTAL_ID = "aeat-sede"
        const val AEAT_ENTRY_URL =
            "https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"
        const val AEAT_CENSAL_DATA_LABEL = "Mis datos censales"
        const val AEAT_CENSAL_DATA_HREF =
            "https://sede.agenciatributaria.gob.es/static_files/common/html/selector_acceso/" +
                "SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FBUGC-JDIT%2FMdcAcceso&aut=CP"
        const val AEAT_CERTIFICATE_PAGE_URL = AEAT_CENSAL_DATA_HREF
        const val AEAT_CERTIFICATE_BUTTON_LABEL = "Certificado o DNI electrónico"
        const val LLEIDA_PORTAL_ID = "diputacion-lleida-sede"
        const val LLEIDA_LOGIN_PAGE_URL =
            "https://seu.diputaciolleida.cat/portal/entidades.do?ent_id=1&idioma=2"
        const val LLEIDA_LOGIN_LINK_ID = "login"
        const val LLEIDA_LOGIN_LINK_HREF = "javascript: abrirLogin('');"
        const val LLEIDA_CERT_BUTTON_ID = "btnValid"
        const val LLEIDA_CERT_BUTTON_ARIA_LABEL = "VALid"
        const val LLEIDA_CERT_BUTTON_ONCLICK = "javascript: pulsarLoginValid();"
        const val DIPUTACION_SEVILLA_PORTAL_ID = "diputacion-sevilla-sede"
        const val DIPUTACION_SEVILLA_INDEX_URL =
            "https://sedeelectronicadipusevilla.es/opencms/system/modules/sede/elements/secciones/index"
        const val DIPUTACION_SEVILLA_AUTH_URL =
            "https://sedeelectronicadipusevilla.es/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp"
        const val DIPUTACION_SEVILLA_AUTH_LABEL = "Identificarse"
        const val DIPUTACION_SEVILLA_AUTH_HREF =
            "/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp"
        const val DIPUTACION_SEVILLA_AUTH_BUTTON_LABEL = "ACCEDER"
        const val DIPUTACION_SEVILLA_AUTH_BUTTON_ONCLICK = "loginClave();"
        const val SEVILLA_PORTAL_ID = "sevilla-sede"
        const val SEVILLA_ENTRY_URL =
            "https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"
        const val SEVILLA_AUTH_CONTAINER_ID = "divBotonCertificado"
        const val SEVILLA_AUTH_LABEL = "Acceder"
        const val SEVILLA_AUTH_HREF = "#"
        const val SEVILLA_AUTH_ONCLICK = "doSign();"
        const val UNIZAR_PORTAL_ID = "unizar-tramitador"
        const val UNIZAR_ENTRY_URL =
            "https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent"
        const val UNIZAR_AUTH_CONTAINER_ID = "capaAccesoCertificado"
        const val UNIZAR_AUTH_ELEMENT_ID = "entrar"
        const val UNIZAR_AUTH_LABEL =
            "Pulse para ejecutar Autofirma e identificarse con certificado."
        const val UNIZAR_AUTH_IMAGE_ALT = "certificado login"
        const val UNIZAR_AUTH_ONCLICK = "lanza();"
        val REAL_CERT_URI: Uri = Uri.parse("content://dev.junta.firmamobile.real-e2e/identity.p12")
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val PORTAL_TIMEOUT_MILLIS = 75_000L
        const val SIGNING_TIMEOUT_MILLIS = 90_000L
        const val POST_SIGN_TIMEOUT_MILLIS = 30_000L
        const val RECIPE_TERMINAL_GRACE_MILLIS = 5_000L
        const val POLL_MILLIS = 300L
        const val RECIPE_POLL_MILLIS = 200L
        const val MAX_PASSWORD_BYTES = 8_192
        const val MAX_CLIENT_AUTH_CONFIRMATIONS = 8
        val PORTAL_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,95}")
        val SAFE_ERROR_TOKEN = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
        val SANITIZED_HOST = Regex("(?:^| )host=([a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?)(?: |$)")
        val SANITIZED_NAVIGATION_EVENT = Regex(
            "(?:^| )event=(NAVIGATION_ALLOWED|PAGE_STARTED|PAGE_FINISHED)(?: |$)",
        )
        val SANITIZED_MAIN_FRAME = Regex("(?:^| )main_frame=true(?: |$)")
        val SANITIZED_PATH_LENGTH = Regex("(?:^| )path_length=([0-9]{1,7})(?: |$)")
        val SANITIZED_PATH_HASH = Regex("(?:^| )path_sha256_8=([0-9a-f]{8})(?: |$)")
        val SAFE_AUTH_SIGN_PROFILES = setOf(
            "junta-andalucia",
            "unizar-tramitador",
            "junta-ofvirtual",
            "aragon-siraw",
            "dgt-verificacion-equipo",
            "ugr-certificado-login",
            "cantabria-rec-cert-login",
            "jccm-certificate-login-probe",
            "sevilla-atse-certificate-login",
            "cdti-certificate-validation",
            "diputacion-lugo-sede",
            "canarias-sede",
            "transportes-qys-cert-login",
            "mites-certificate-login",
            "diputacion-lleida-sede",
            "diputacion-badajoz-portal",
        )
    }
}
