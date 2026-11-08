package dev.junta.firmamobile

import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningUiState
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun recipeSessionRewritingChangesOnlyThePath() {
        val expected = "https://login.example/login/"
        assertEquals(expected, recipeUrlWithoutSession("$expected;jsessionid=SYNTHETIC"))
        assertEquals(expected, recipeUrlWithoutSession("https://login.example/login;JSESSIONID=SYNTHETIC/"))
        listOf(
            "https://login.example;jsessionid=.evil.test/login/",
            "$expected?state=;jsessionid=SYNTHETIC",
            "$expected#;jsessionid=SYNTHETIC",
        ).forEach { url -> assertEquals(url, recipeUrlWithoutSession(url)) }
    }

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
                    applyPortalRecipe(scenario, portalId, profile.profileId.value)
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
            rule.onAllNodesWithText("Servicios públicos").fetchSemanticsNodes().isNotEmpty()
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
        profileId: String,
    ) {
        // REC and all its AGE aliases: login only, the XAdES signature of the registry summary is the filing.
        if (profileId == REG_AGE_PROFILE_ID) {
            clickRecClaveLogin(scenario)
            clickClaveAfirmaProvider(scenario)
            return
        }
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
            BADAJOZ_PORTAL_ID -> {
                waitForBadajozEntryPageReady(scenario)
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
                clickClaveAfirmaProvider(scenario)
            }
            MALAGA_PORTAL_ID -> runMalagaClaveCertificateRecipe(scenario)
            // Gestiona redirects the instancia general to its identification page; Cl@ve is the only provider.
            in GESTIONA_CLAVE_ORIGINS -> {
                clickGestionaClaveLogin(scenario, GESTIONA_CLAVE_ORIGINS.getValue(portalId))
                clickClaveAfirmaProvider(scenario)
            }
            in GESTIONA_IDENTIFICATE_ORIGINS -> {
                val origin = GESTIONA_IDENTIFICATE_ORIGINS.getValue(portalId)
                clickGestionaIdentificate(scenario, origin)
                clickGestionaClaveLogin(scenario, origin)
                clickClaveAfirmaProvider(scenario)
            }
            ISFAS_PORTAL_ID -> {
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = ISFAS_HOME_URL,
                    expectedElementId = null,
                    expectedLabel = "Identificarse",
                    expectedAriaLabel = null,
                    expectedOnClick = "identificarse();",
                    expectedFormName = "menuPrincipal",
                    expectedFormAction = ISFAS_WELCOME_URL,
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = ISFAS_IDENTIFY_URL,
                    expectedLabel = "Cl@ve",
                    expectedHref = "#",
                    expectedOnClick = ISFAS_CLAVE_ONCLICK,
                    expectedCssClass = "imagenIdentificarse",
                    expectedFormName = "formWelcome",
                    expectedFormAction = ISFAS_IDENTIFY_URL,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            CNMV_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CNMV_CARPETA_URL,
                    expectedLabel = "",
                    expectedHref = CNMV_CARPETA_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = CNMV_CLAVE_URL,
                    expectedElementId = CNMV_CLAVE_ID,
                    expectedLabel = "",
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                    expectedType = "image",
                    expectedTag = "input",
                    expectedSrc = CNMV_CLAVE_IMAGE_SRC,
                    expectedFormName = "formData",
                    expectedFormAction = CNMV_CLAVE_FORM_ACTION,
                    requireNoOnClick = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            CSD_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CSD_OFICINA_URL,
                    expectedLabel = "",
                    expectedHref = CSD_OFICINA_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = CSD_CLAVE_URL,
                    expectedElementId = CSD_CLAVE_ID,
                    expectedLabel = "Validarse",
                    expectedAriaLabel = "Validarse (Acceso con Plataforma Cl@ve)",
                    expectedOnClick = "javascript:preshow();",
                    waitForExpectedUrl = true,
                    expectedType = "submit",
                    expectedTag = "input",
                    expectedFormName = "aspnetForm",
                    expectedFormAction = CSD_CLAVE_FORM_ACTION,
                )
                clickClaveAfirmaProvider(scenario)
            }
            POLICIA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = POLICIA_CLAVE_URL,
                    expectedLabel = "Solicitud genérica",
                    expectedHref = POLICIA_CLAVE_HREF,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            CATASTRO_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CATASTRO_CLAVE_0_URL,
                    expectedLabel = "Ir al formulario",
                    expectedHref = CATASTRO_CLAVE_0_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CATASTRO_CLAVE_1_URL,
                    expectedLabel = "Cl@ve: PIN24, Cl@ve permanente,...",
                    expectedHref = CATASTRO_CLAVE_1_HREF,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            MINECO_PORTAL_ID -> {
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = MINECO_CLAVE_URL,
                    expectedElementId = MINECO_CLAVE_ID,
                    expectedLabel = "",
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                    expectedType = "image",
                    expectedTag = "input",
                    expectedSrc = MINECO_CLAVE_IMAGE_SRC,
                    expectedFormName = "aspnetForm",
                    expectedFormAction = MINECO_CLAVE_FORM_ACTION,
                    requireNoOnClick = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            TRANSPARENCIA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = TRANSPARENCIA_CLAVE_URL,
                    expectedLabel = "Acceder al procedimiento",
                    expectedHref = TRANSPARENCIA_CLAVE_HREF,
                    waitForExpectedUrl = true,
                    shadowHostTag = "dnt-button",
                )
                clickClaveAfirmaProvider(scenario)
            }
            RED_ES_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = RED_ES_CLAVE_0_URL,
                    expectedLabel = "Mis trámites",
                    expectedHref = RED_ES_CLAVE_0_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = RED_ES_CLAVE_1_URL,
                    expectedLabel = "",
                    expectedHref = RED_ES_CLAVE_1_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = RED_ES_CLAVE_URL,
                    expectedElementId = null,
                    expectedLabel = "Acceder",
                    expectedAriaLabel = null,
                    expectedOnClick = "mostrarEspera();accesoClave();",
                    waitForExpectedUrl = true,
                    expectedType = "button",
                )
                clickClaveAfirmaProvider(scenario)
            }
            ALMERIA_PORTAL_ID -> {
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = ALMERIA_CLAVE_URL,
                    expectedElementId = ALMERIA_CLAVE_ID,
                    expectedLabel = "Acceso Cl@ve",
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                    expectedType = "submit",
                    expectedFormName = "formularioExternoClave2",
                    expectedFormAction = ALMERIA_CLAVE_FORM_ACTION,
                    requireNoOnClick = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            CORDOBA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CORDOBA_CLAVE_URL,
                    expectedLabel = "Ir al trámite con Cl@ve",
                    expectedHref = CORDOBA_CLAVE_HREF,
                    waitForExpectedUrl = true,
                    expectedFormName = "formClave",
                    expectedFormAction = "https://apis.dipucordoba.es/apiclave/api/sessions",
                )
                clickClaveAfirmaProvider(scenario)
            }
            PALENCIA_PORTAL_ID -> {
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = PALENCIA_CLAVE_URL,
                    expectedElementId = null,
                    expectedLabel = "",
                    expectedAriaLabel = null,
                    expectedOnClick = PALENCIA_CLAVE_ONCLICK,
                    waitForExpectedUrl = true,
                    expectedType = null,
                    expectedTag = "img",
                    expectedSrc = PALENCIA_CLAVE_IMAGE_SRC,
                    matchPalenciaSessionPath = true,
                    expectedFormName = "formClave",
                    expectedFormAction = PALENCIA_CLAVE_FORM_ACTION,
                )
                clickClaveAfirmaProvider(scenario)
            }
            EDUCATION_PORTAL_ID -> {
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = EDUCATION_CLAVE_URL,
                    expectedElementId = EDUCATION_CLAVE_ID,
                    expectedLabel = "Acceder",
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                    expectedType = "submit",
                    expectedTag = "input",
                    expectedFormName = "tipoIdentificacionForm",
                    expectedFormAction = EDUCATION_CLAVE_FORM_ACTION,
                    requireNoOnClick = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            PONTEVEDRA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = PONTEVEDRA_ENTRY_URL,
                    expectedLabel = "En liña",
                    expectedHref = PONTEVEDRA_LOGIN_HREF,
                    expectedCssClass = "rightformbutton",
                    requireVisible = true,
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = PONTEVEDRA_LOGIN_URL,
                    expectedLabel = "Certificado dixital ou DNI electrónico",
                    expectedHref = "#",
                    expectedOnClick = PONTEVEDRA_CLAVE_ONCLICK,
                    expectedFormName = "formclave",
                    expectedFormAction = PONTEVEDRA_FORM_ACTION,
                    requireFormContainment = true,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            EXTREMADURA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = EXTREMADURA_ANTERIOR_CLAVE_0_URL,
                    expectedLabel = "Miespacio",
                    expectedHref = EXTREMADURA_LOGIN_URL,
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = EXTREMADURA_LOGIN_URL,
                    expectedElementId = "link-clave",
                    expectedLabel = "Acceso mediante el sistema CLAVE",
                    expectedHref = EXTREMADURA_CLAVE_HREF,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            EXTREMADURA_ANTERIOR_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = EXTREMADURA_ANTERIOR_CLAVE_0_URL,
                    expectedLabel = "Anotaciones de registro Consulta tus anotaciones de registro",
                    expectedHref = EXTREMADURA_ANTERIOR_CLAVE_0_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = EXTREMADURA_ANTERIOR_CLAVE_1_URL,
                    expectedLabel = "Acceso mediante el sistema CLAVE",
                    expectedHref = EXTREMADURA_ANTERIOR_CLAVE_1_HREF,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            // Both catalog entries launch the same reviewed Registrar.do profile start.
            FUERTEVENTURA_INSTITUCIONAL_PORTAL_ID, FUERTEVENTURA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = FUERTEVENTURA_ENTRY_URL,
                    expectedLabel = "",
                    expectedHref = "#",
                    expectedOnClick = "submit();",
                    expectedFormName = "claveForm",
                    expectedFormAction = CLAVE_SERVICE_PROVIDER_URL,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }

            JUSTICIA_PORTAL_ID -> {
                clickClaveAfirmaProvider(scenario)
            }
            MADRID_SEDE_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = MADRID_SEDE_APPLICATION_URL,
                    expectedLabel = "Solicitud o renovación digital de Tarjeta Azul",
                    expectedHref = MADRID_SEDE_APPLICATION_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = MADRID_SEDE_CERT_URL,
                    expectedLabel = "DNIe / Certificado",
                    expectedHref = MADRID_SEDE_CERT_HREF,
                    waitForExpectedUrl = true,
                    matchUrlWithoutQuery = true,
                )
            }
            MJUSTICIA_PORTAL_ID -> {
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = MJUSTICIA_CLAVE_URL,
                    expectedElementId = MJUSTICIA_CLAVE_ID,
                    expectedLabel = "Acceso con cl@ve",
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                    expectedType = "submit",
                    expectedFormName = "formClave",
                    expectedFormAction = MJUSTICIA_CLAVE_FORM_ACTION,
                    requireNoOnClick = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            // Cáceres: "Acceder" on the procedure -> login page -> Cl@ve logo (image-only link) -> AFIRMA.
            CACERES_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CACERES_START_URL,
                    expectedLabel = CACERES_ACCESS_LABEL,
                    expectedHref = CACERES_ACCESS_HREF,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CACERES_LOGIN_URL,
                    expectedLabel = "",
                    expectedHref = CACERES_CLAVE_HREF,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            // *.sede.gob.es: shadow-DOM header "Iniciar sesión" -> optional /login page -> claveproxy -> Cl@ve.
            in SEDE_GOB_CLAVE_HOSTS -> {
                val host = SEDE_GOB_CLAVE_HOSTS.getValue(portalId)
                val home = "https://$host/"
                val claveproxy = "/claveproxy/clave/authenticate?returnUrl=https%3A%2F%2F$host%2F"
                val login = "/login?returnUrl=https%3A%2F%2F$host%2F"
                val loginPage = portalId in SEDE_GOB_LOGIN_PAGE_PORTALS
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = home,
                    expectedLabel = SEDE_GOB_LOGIN_LABEL,
                    expectedHref = if (loginPage) login else claveproxy,
                    waitForExpectedUrl = true,
                    shadowHostTag = "dnt-header-item",
                )
                if (loginPage) {
                    clickExactLabeledAnchor(
                        scenario = scenario,
                        expectedCurrentUrl = "https://$host$login",
                        expectedLabel = "",
                        expectedHref = claveproxy,
                        waitForExpectedUrl = true,
                        shadowHostTag = "dnt-link",
                    )
                }
                clickClaveAfirmaProvider(scenario)
            }
            // gsede (opencms) sedes: "Identificarse" -> autenticacion.jsp -> loginClave() posts to Cl@ve.
            in GSEDE_CLAVE_PORTALS -> {
                val gsede = GSEDE_CLAVE_PORTALS.getValue(portalId)
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = gsede.homeUrl,
                    expectedLabel = GSEDE_IDENTIFY_LABEL,
                    expectedHref = GSEDE_AUTHENTICATION_PATH,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = gsede.origin + GSEDE_AUTHENTICATION_PATH,
                    expectedElementId = null,
                    expectedLabel = gsede.claveLabel,
                    expectedAriaLabel = null,
                    expectedOnClick = gsede.claveOnClick,
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            AESA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = AESA_HOME_URL,
                    expectedLabel = "Trámite online",
                    expectedHref = "/oficina/tramites/altaSolicitud.do?id=4&codArea=SOLGEN",
                    expectedType = "button",
                    expectedAriaLabel = "Iniciar el trámite electrónico",
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = AESA_LOGIN_URL,
                    expectedElementId = "botonAccederClave",
                    expectedName = "autenticarComprobacion",
                    expectedLabel = "Acceder",
                    expectedAriaLabel = null,
                    expectedOnClick = "galleta(); muestraCargando(); accesoClave();",
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            DGSFP_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = DGSFP_HOME_URL,
                    expectedLabel = "",
                    expectedHref = DGSFP_LOGIN_URL,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = DGSFP_LOGIN_URL,
                    expectedElementId = "imgClave",
                    expectedLabel = "",
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    expectedType = null,
                    expectedTag = "img",
                    expectedSrc = "/Style Library/DGSFP.SedeElectronica/Imagenes/clave.png",
                    expectedParentAnchorClass = "text-center",
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            // CSN: the hidden login-menu link starts Cl@ve with AFIRMA preselected; no provider choice follows.
            BOE_PORTAL_ID -> clickBoeCertificateAnchor(scenario)
            CMT_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CMT_ENTRY_URL,
                    expectedLabel = "Consulta de sus trámites",
                    expectedHref = "https://sede.cmt.gob.es/carpetaciudadana.aspx",
                )
                clickSedipualbaServerCertificateOptionOnLoginPage(
                    scenario, CMT_LOGIN_URL, SEDIPUALBA_SSL_OPTION_LABEL_ES,
                )
            }
            FONDOS_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = FONDOS_LOGIN_URL,
                    expectedLabel = "Acceso con Cl@ve",
                    expectedHref = "javascript:__doPostBack('ImageButtonClave2','')",
                    expectedOnClick = "VerCargando();",
                    expectedFormName = "servForm",
                    expectedFormAction = FONDOS_LOGIN_URL,
                )
                clickClaveAfirmaProvider(scenario)
            }
            IMSERSO_PORTAL_ID -> clickClaveAfirmaProvider(scenario)
            INE_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = INE_ENTRY_URL,
                    expectedLabel = "Más trámites",
                    expectedHref = "#accFig1254735165102",
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = INE_ENTRY_URL + "#accFig1254735165102",
                    expectedLabel = "Consulta del estado de tramitación",
                    expectedHref = "/tramite/estado_tramitacion/",
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = "https://sede.ine.gob.es/tramite/estado_tramitacion/",
                    expectedLabel = "Acceso al trámite",
                    expectedHref = "/sigem-consulta-expediente/index.jsp?lang=es_ES",
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = "https://sede.ine.gob.es/sigem-consulta-expediente/index.jsp?lang=es_ES",
                    expectedElementId = null,
                    expectedLabel = "Autenticarse en Cl@ve",
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    expectedTag = "input",
                    expectedType = "submit",
                    waitForExpectedUrl = true,
                )
                clickClaveAfirmaProvider(scenario)
            }
            SEPES_PORTAL_ID -> {
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = SEPES_LOGIN_URL,
                    expectedElementId = "ctl00_ContentFormulario_controlAutenticacion_Sign",
                    expectedLabel = "Continuar",
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    expectedTag = "input",
                    expectedType = "submit",
                    requireNoOnClick = true,
                    expectedFormName = "aspnetForm",
                    expectedFormAction = SEPES_LOGIN_URL,
                )
                clickClaveAfirmaProvider(scenario)
            }
            TEA_PORTAL_ID -> clickExactButton(
                scenario = scenario,
                expectedCurrentUrl = TEA_SELECTOR_URL,
                expectedElementId = null,
                expectedLabel = "Certificado o DNI electrónico",
                expectedAriaLabel = null,
                expectedOnClick = null,
                waitForExpectedUrl = true,
                expectedType = "button",
                expectedTag = "button",
                requireNoOnClick = true,
            )
            CSN_PORTAL_ID -> clickExactLabeledAnchor(
                scenario = scenario,
                expectedCurrentUrl = CSN_HOME_URL,
                expectedLabel = CSN_CERT_LABEL,
                expectedHref = CSN_CERT_HREF,
                waitForExpectedUrl = true,
            )
            // MUGEJU, AIREF and Cádiz auto-post their SAML request to Cl@ve; only the certificate IdP choice remains.
            // AIREF: login only, the MiniApplet signature is the filing.
            MUGEJU_PORTAL_ID, AIREF_PORTAL_ID, CADIZ_PORTAL_ID -> clickClaveAfirmaProvider(scenario)
            // JCCM: login only, the AutoScript signature of the Solicitud Genérica is the filing.
            JCCM_PORTAL_ID -> {
                clickExactAuthButton(
                    scenario = scenario,
                    expectedCurrentUrl = JCCM_ENTRY_URL,
                    elementId = JCCM_CLAVE_BUTTON_ID,
                    expectedValue = JCCM_CLAVE_BUTTON_VALUE,
                    expectedOnClick = JCCM_CLAVE_BUTTON_ONCLICK,
                )
                // sso.jccm.es runs ALTCHA proof-of-work before Cl@ve: ~20 s on a phone, >45 s on CI.
                clickClaveAfirmaProvider(scenario, timeoutMillis = JCCM_ALTCHA_TIMEOUT_MILLIS)
            }
            // Certificate logins that sign a reviewed auth challenge (SAFE_AUTH_SIGN_PROFILES): one exact click each.
            DGT_PORTAL_ID -> clickExactButton(
                scenario = scenario,
                expectedCurrentUrl = DGT_VERIFY_URL,
                expectedElementId = DGT_VERIFY_BUTTON_ID,
                expectedLabel = DGT_VERIFY_BUTTON_LABEL,
                expectedAriaLabel = null,
                expectedOnClick = DGT_VERIFY_BUTTON_ONCLICK,
                expectedType = null,
            )
            SIRAW_PORTAL_ID -> clickExactAuthButton(
                scenario = scenario,
                expectedCurrentUrl = SIRAW_LOGIN_URL,
                elementId = SIRAW_CERT_BUTTON_ID,
                expectedValue = SIRAW_CERT_BUTTON_VALUE,
                expectedOnClick = SIRAW_CERT_BUTTON_ONCLICK,
            )
            TRANSPORTES_PORTAL_ID -> clickExactButton(
                scenario = scenario,
                expectedCurrentUrl = TRANSPORTES_AUTH_URL,
                expectedElementId = TRANSPORTES_SIGN_BUTTON_ID,
                expectedLabel = TRANSPORTES_SIGN_BUTTON_LABEL,
                expectedAriaLabel = null,
                expectedOnClick = TRANSPORTES_SIGN_BUTTON_ONCLICK,
                waitForTransportesSignReady = true,
                waitForExpectedUrl = true,
                expectedType = "submit",
                expectedTag = "input",
            )
            LUGO_PORTAL_ID -> clickExactButton(
                scenario = scenario,
                expectedCurrentUrl = LUGO_AUTH_URL,
                expectedElementId = LUGO_AUTH_BUTTON_ID,
                expectedLabel = LUGO_CERT_BUTTON_LABEL,
                expectedAriaLabel = null,
                expectedOnClick = LUGO_CERT_BUTTON_ONCLICK,
                waitForLugoSignReady = true,
            )
            AEMET_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = AEMET_HOME_URL,
                    expectedLabel = "Identificarse",
                    expectedHref = "selectLogin",
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = AEMET_LOGIN_MENU_URL,
                    expectedLabel = "Acceder por dni-e o certificado digital",
                    expectedHref = "./sso",
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = AEMET_LOGIN_URL,
                    expectedElementId = "buttonSign",
                    expectedLabel = "Entrar",
                    expectedAriaLabel = null,
                    expectedOnClick = "envia();",
                    expectedTag = "input",
                    expectedFormName = "signBean",
                    expectedFormAction = AEMET_LOGIN_ACTION_URL,
                    expectedHandlerName = "envia",
                    waitForAemetSignHook = true,
                    requireVisible = true,
                    waitForExpectedUrl = true,
                )
            }
            CANARIAS_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CANARIAS_HOME_URL,
                    expectedLabel = "Gestión datos personales",
                    expectedHref = CANARIAS_PERSONAL_DATA_URL,
                    waitForExpectedUrl = true,
                )
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = CANARIAS_LOGIN_MENU_URL,
                    expectedLabel = "Certificado Digital / DNI-E",
                    expectedHref = "identificacion",
                    expectedTitle = "Certificado Digital / DNI-E",
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = CANARIAS_LOGIN_URL,
                    expectedElementId = null,
                    expectedLabel = "Validarme",
                    expectedAriaLabel = null,
                    expectedOnClick = "firmar(formData);",
                    expectedFormName = "formData",
                    expectedFormAction = CANARIAS_LOGIN_ACTION_URL,
                    matchCanariasLoginAction = true,
                    expectedHandlerName = "firmar",
                    requireVisible = true,
                    waitForExpectedUrl = true,
                )
            }
            // Soria: wait for the late script before clicking the certificate control.
            SORIA_PORTAL_ID -> clickExactButton(
                scenario = scenario,
                expectedCurrentUrl = SORIA_SOURCE_URL,
                expectedElementId = SORIA_CERT_BUTTON_ID,
                expectedLabel = SORIA_CERT_BUTTON_LABEL,
                expectedAriaLabel = null,
                expectedOnClick = SORIA_CERT_BUTTON_ONCLICK,
                expectedHandlerName = "pulsarCertificado",
            )
            // La Rioja: the trámite redirects to CAS with per-session param/TARGET; its certificate "Conectar".
            LA_RIOJA_PORTAL_ID -> clickExactButton(
                scenario = scenario,
                expectedCurrentUrl = LA_RIOJA_CAS_LOGIN_URL,
                expectedElementId = LA_RIOJA_CERT_BUTTON_ID,
                expectedLabel = LA_RIOJA_CERT_BUTTON_LABEL,
                expectedAriaLabel = null,
                expectedOnClick = LA_RIOJA_CERT_BUTTON_ONCLICK,
                waitForExpectedUrl = true,
                matchUrlWithoutQuery = true,
            )
            // Menorca: "Les meves dades" -> Carpeta Ciutadana login (return URL in query) -> "Certificat electrònic" submit.
            // "Tramitar" redirects to a login URL carrying raw ^ and ¿ characters, which the app rejects as INVALID_URL.
            MENORCA_PORTAL_INSTITUCIONAL_ID, MENORCA_SEDE_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = MENORCA_START_URL,
                    expectedLabel = MENORCA_PERSONAL_DATA_LABEL,
                    expectedHref = MENORCA_PERSONAL_DATA_HREF,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = MENORCA_LOGIN_URL,
                    expectedElementId = MENORCA_CERT_SUBMIT_ID,
                    expectedLabel = MENORCA_CERT_SUBMIT_LABEL,
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                    expectedType = "submit",
                    matchUrlWithoutQuery = true,
                    expectedTag = "input",
                )
            }
            // A Coruña: portada -> sede -> Solicitude Xeral -> tramitador -> Cl@ve, each hop an exact link.
            CORUNA_PORTAL_ID -> {
                CORUNA_LINK_HOPS.forEach { hop ->
                    clickExactLabeledAnchor(
                        scenario = scenario,
                        expectedCurrentUrl = hop.pageUrl,
                        expectedLabel = hop.label,
                        expectedHref = hop.href,
                        waitForExpectedUrl = true,
                    )
                }
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = CORUNA_TRAMITADOR_URL,
                    expectedElementId = CORUNA_CLAVE_BUTTON_ID,
                    expectedLabel = CORUNA_CLAVE_BUTTON_LABEL,
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                    expectedType = null,
                )
                clickClaveAfirmaProvider(scenario)
            }
            // CAIB sistramit redirects straight to Cl@ve. Login only: the PortaFIB signature is the filing.
            CAIB_SEU_PORTAL_ID, CAIB_REGISTRE_PORTAL_ID -> clickClaveAfirmaProvider(scenario)
            // Tenerife: login only, the AutoScript signature is the filing.
            TENERIFE_SEDE_PORTAL_ID, TENERIFE_INSTITUCIONAL_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = TENERIFE_HOME_URL,
                    expectedLabel = TENERIFE_LOGIN_LABEL,
                    expectedHref = TENERIFE_LOGIN_HREF,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = TENERIFE_ACCESS_URL,
                    expectedElementId = null,
                    expectedLabel = TENERIFE_CLAVE_BUTTON_LABEL,
                    expectedAriaLabel = null,
                    expectedOnClick = null,
                    waitForExpectedUrl = true,
                    expectedType = "submit",
                )
                clickClaveAfirmaProvider(scenario)
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
                expectedHandlerName = UNIZAR_AUTH_HANDLER,
            )
            SEDIPUALBA_ALBACETE_PORTAL_ID -> clickSedipualbaServerCertificateOption(
                scenario = scenario,
                expectedEntryUrl = SEDIPUALBA_ALBACETE_ENTRY_URL,
                expectedLoginUrl = SEDIPUALBA_ALBACETE_LOGIN_URL,
                loginLabel = SEDIPUALBA_LOGIN_LABEL_ES,
                optionLabel = SEDIPUALBA_SSL_OPTION_LABEL_ES,
            )
            SEDIPUALBA_CUENCA_PORTAL_ID -> clickSedipualbaServerCertificateOption(
                scenario = scenario,
                expectedEntryUrl = SEDIPUALBA_CUENCA_ENTRY_URL,
                expectedLoginUrl = SEDIPUALBA_CUENCA_LOGIN_URL,
                loginLabel = SEDIPUALBA_LOGIN_LABEL_ES,
                optionLabel = SEDIPUALBA_SSL_OPTION_LABEL_ES,
            )
            SEDIPUALBA_LEON_PORTAL_ID -> clickSedipualbaServerCertificateOption(
                scenario = scenario,
                expectedEntryUrl = SEDIPUALBA_LEON_ENTRY_URL,
                expectedLoginUrl = SEDIPUALBA_LEON_LOGIN_URL,
                loginLabel = SEDIPUALBA_LOGIN_LABEL_ES,
                optionLabel = SEDIPUALBA_SSL_OPTION_LABEL_ES,
            )
            SEDIPUALBA_MALLORCA_PORTAL_ID, MALLORCA_INSTITUCIONAL_PORTAL_ID -> clickSedipualbaServerCertificateOption(
                scenario = scenario,
                expectedEntryUrl = SEDIPUALBA_MALLORCA_ENTRY_URL,
                expectedLoginUrl = SEDIPUALBA_MALLORCA_LOGIN_URL,
                loginLabel = SEDIPUALBA_LOGIN_LABEL_CA,
                optionLabel = SEDIPUALBA_SSL_OPTION_LABEL_CA,
            )
            SANIDAD_PORTAL_ID, JAEN_PORTAL_ID -> {
                val hops = if (portalId == SANIDAD_PORTAL_ID) SANIDAD_LINK_HOPS else JAEN_LINK_HOPS
                hops.forEach { hop ->
                    clickExactLabeledAnchor(
                        scenario = scenario,
                        expectedCurrentUrl = hop.pageUrl,
                        expectedLabel = hop.label,
                        expectedHref = hop.href,
                        waitForExpectedUrl = true,
                    )
                }
            }
            ARAGON_TRAMITES_PORTAL_ID -> clickAragonIframeCertificateLogin(scenario)
            MADRID_CUENTA_DIGITAL_PORTAL_ID -> clickMadridCuentaDigitalCertificateLogin(scenario)
            HUELVA_PORTAL_ID -> {
                clickExactLabeledAnchor(
                    scenario = scenario,
                    expectedCurrentUrl = HUELVA_INDEX_URL,
                    expectedLabel = "Identificarse",
                    expectedHref = GSEDE_AUTHENTICATION_PATH,
                    waitForExpectedUrl = true,
                )
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = HUELVA_AUTH_URL,
                    expectedElementId = null,
                    expectedLabel = "Acceder",
                    expectedAriaLabel = null,
                    expectedOnClick = "login();",
                    waitForExpectedUrl = true,
                )
            }
            VALLADOLID_PORTAL_ID -> clickExactLabeledAnchor(
                scenario = scenario,
                expectedCurrentUrl = VALLADOLID_LOGIN_URL,
                expectedLabel = "ACCESO CON CERTIFICADO DIGITAL",
                expectedHref = "/c/portal/cert-login",
                waitForExpectedUrl = true,
            )
            GVA_PORTAL_ID -> clickExactLabeledAnchor(
                scenario = scenario,
                expectedCurrentUrl = GVA_LOGIN_URL,
                expectedLabel = "Acceder con certificado",
                expectedHref = "javascript:;",
                waitForExpectedUrl = true,
            )
            SEGURIDAD_SOCIAL_PORTAL_ID, IMPORTASS_PORTAL_ID -> {
                if (portalId == SEGURIDAD_SOCIAL_PORTAL_ID) {
                    clickExactButton(
                        scenario = scenario,
                        expectedCurrentUrl = SEGURIDAD_SOCIAL_ENTRY_URL,
                        expectedElementId = null,
                        expectedLabel = "Acceder",
                        expectedAriaLabel = null,
                        expectedOnClick = "doLogin();",
                        expectedType = null,
                        waitForExpectedUrl = true,
                    )
                }
                // The portal performs its own SAML auto-posts; only the exact IdP choice is clicked.
                clickExactButton(
                    scenario = scenario,
                    expectedCurrentUrl = SEGURIDAD_SOCIAL_IDP_URL,
                    expectedElementId = "IPCEIdP",
                    expectedLabel = "DNIe o certificado",
                    expectedAriaLabel = "Acceder a DNIe o certificado",
                    expectedOnClick = null,
                    expectedType = "submit",
                    waitForExpectedUrl = true,
                )
            }
            else -> Unit
        }
    }

    private fun clickAragonIframeCertificateLogin(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedEntry = JSONObject.quote(ARAGON_TRAMITES_ENTRY_URL)
        val quotedFrameUrl = JSONObject.quote(ARAGON_IDENTIFICATION_FRAME_URL)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("currentWebView").apply { isAccessible = true }
                val webView = field.get(activity) as? WebView
                if (webView == null) {
                    failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      if (location.href !== $quotedEntry) return 0;
                      const frames = document.querySelectorAll('iframe#mfe_iframe');
                      if (frames.length === 0) return 0;
                      if (frames.length !== 1) return 2;
                      let doc;
                      try { doc = frames[0].contentDocument; } catch (e) { return 2; }
                      if (!doc || doc.location.href === 'about:blank') return 0;
                      if (doc.location.href !== $quotedFrameUrl || doc.location.origin !== location.origin) return 2;
                      const radios = doc.querySelectorAll('input#identificacion-2[name="identificacion"]' +
                        '[type="radio"][value="certificado"]');
                      const labels = doc.querySelectorAll('label[for="identificacion-2"]');
                      const buttons = Array.from(doc.querySelectorAll('button')).filter(button =>
                        (button.innerText || '').trim().replace(/\s+/g, ' ') === 'Acceder' &&
                        button.getAttribute('onclick') === 'identifyByMethod(applicationId, urlRedirect)');
                      if (radios.length === 0 || labels.length === 0 || buttons.length === 0) return 0;
                      if (radios.length !== 1 || labels.length !== 1 || buttons.length !== 1) return 2;
                      if ((labels[0].textContent || '').trim() !== 'Con certificado electrónico') return 2;
                      if (radios[0].disabled || buttons[0].disabled) return 0;
                      radios[0].click();
                      if (!radios[0].checked) return 2;
                      buttons[0].click();
                      return 1;
                    })()
                    """.trimIndent(),
                ) { code ->
                    clicked = code == "1"
                    if (code == "2") failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        if (hasObservedTerminalNavigationFailure()) return
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickMadridCuentaDigitalCertificateLogin(scenario: ActivityScenario<MainActivity>) {
        val quotedSource = JSONObject.quote(MADRID_CUENTA_DIGITAL_LOGIN_URL)
        val controls = listOf(
            "ui-button" to "Acceder a Cuenta Digital",
            "a.idps__list--link[role=\"link\"][tabindex=\"0\"]" to "Certificado Digital",
        )
        for ((selector, label) in controls) {
            val quotedSelector = JSONObject.quote(selector)
            val quotedLabel = JSONObject.quote(label)
            val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
            var clicked = false
            while (!clicked && SystemClock.elapsedRealtime() < deadline) {
                if (hasObservedTerminalNavigationFailure()) return
                val inspected = CountDownLatch(1)
                var failure: String? = null
                scenario.onActivity { activity ->
                    val field = MainActivity::class.java.getDeclaredField("currentWebView")
                        .apply { isAccessible = true }
                    val webView = field.get(activity) as? WebView
                    if (webView == null) {
                        failure = "REAL_E2E_RECIPE_WEBVIEW_MISSING"
                        inspected.countDown()
                        return@onActivity
                    }
                    webView.evaluateJavascript(
                        """
                        (() => {
                          if (location.href !== $quotedSource && location.href + '/' !== $quotedSource) return 0;
                          const elements = Array.from(document.querySelectorAll($quotedSelector)).filter(element => {
                            if (element.localName === 'ui-button') {
                              return element.xRole === 'link' && element.type === 'link' &&
                                element.text === $quotedLabel;
                            }
                            const texts = element.querySelectorAll('ui-text');
                            return texts.length === 1 && texts[0].text === $quotedLabel;
                          });
                          if (elements.length === 0) return 0;
                          if (elements.length !== 1) return 2;
                          elements[0].click();
                          return 1;
                        })()
                        """.trimIndent(),
                    ) { code ->
                        clicked = code == "1"
                        if (code == "2") failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"
                        inspected.countDown()
                    }
                }
                check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
                check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
                if (!clicked) SystemClock.sleep(RECIPE_POLL_MILLIS)
            }
            if (hasObservedTerminalNavigationFailure()) return
            check(clicked) { "REAL_E2E_RECIPE_TARGET_TIMEOUT" }
        }
    }

    private fun waitForBadajozEntryPageReady(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline =
            SystemClock.elapsedRealtime() + BADAJOZ_PAGE_READY_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var ready = false
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
                if (currentUrl == null) {
                    inspected.countDown()
                    return@onActivity
                }
                if (!recipeUrlMatches(currentUrl, BADAJOZ_ENTRY_URL)) {
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    "document.readyState === 'complete'",
                ) { readyState ->
                    ready = readyState == "true"
                    inspected.countDown()
                }
            }
            check(inspected.await(5, TimeUnit.SECONDS)) { "REAL_E2E_RECIPE_INSPECT_TIMEOUT" }
            check(failure == null) { failure ?: "REAL_E2E_RECIPE_FAILED" }
            if (ready) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        error("REAL_E2E_RECIPE_PAGE_READY_TIMEOUT")
    }

    private fun runMalagaClaveCertificateRecipe(
        scenario: ActivityScenario<MainActivity>,
    ) {
        clickMalagaClaveEntry(scenario)
        clickClaveAfirmaProvider(scenario)
    }

    private fun clickMalagaClaveEntry(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedExpectedAction = JSONObject.quote(MALAGA_LOCAL_CLAVE_URL)
        val quotedExpectedValue = JSONObject.quote(MALAGA_CLAVE_SUBMIT_VALUE)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasObservedTerminalNavigationFailure()) return
            val inspected = CountDownLatch(1)
            var failure: String? = null
            var clicked = false
            var waitingForSource = false
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
                if (currentUrl != null && !recipeUrlMatches(currentUrl, MALAGA_ENTRY_URL)) {
                    if (recipeUrlMatches(currentUrl, MALAGA_LOCAL_CLAVE_URL) ||
                        recipeUrlMatches(currentUrl, CLAVE_SERVICE_PROVIDER_URL)
                    ) {
                        clicked = true
                    } else {
                        waitingForSource = true
                    }
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const forms = Array.from(document.querySelectorAll('form#claveFrm'));
                      if (forms.length === 0) return 0;
                      if (forms.length !== 1) return 2;
                      const form = forms[0];
                      if ((form.getAttribute('method') || '').toLowerCase() !== 'post' ||
                          form.action !== $quotedExpectedAction) return 2;
                      const tokenInputs = form.querySelectorAll('input[type="hidden"][name="tkn"]');
                      if (tokenInputs.length !== 1 ||
                          !tokenInputs[0].value || tokenInputs[0].value.length > 16384) return 2;
                      const submits = Array.from(form.querySelectorAll('input[type="submit"]'));
                      if (submits.length !== 1 || submits[0].value !== $quotedExpectedValue) return 2;
                      submits[0].click();
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
            if (waitingForSource) SystemClock.sleep(RECIPE_POLL_MILLIS)
            else SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickClaveAfirmaProvider(
        scenario: ActivityScenario<MainActivity>,
        timeoutMillis: Long = UI_TIMEOUT_MILLIS,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        val quotedServiceProvider = JSONObject.quote(CLAVE_SERVICE_PROVIDER_URL)
        val quotedServiceRedirect = JSONObject.quote(CLAVE_SERVICE_REDIRECT_URL)
        val quotedAfirmaOnClick = JSONObject.quote(CLAVE_AFIRMA_ONCLICK)
        val quotedAfirmaImage = JSONObject.quote(CLAVE_AFIRMA_IMAGE_SRC)
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
                if (currentUrl == null || !recipeUrlMatches(currentUrl, CLAVE_SERVICE_PROVIDER_URL)) {
                    waitingForExpectedUrl = true
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      // WebView.url switches before the document does; the Kotlin URL check rejects a real mismatch.
                      if (location.href !== $quotedServiceProvider) return 0;
                      // A slow Cl@ve page exposes the form before its selectedIdP script; wait instead of failing.
                      if (document.readyState !== 'complete') return 0;
                      const forms = Array.from(document.querySelectorAll('form[name="idpRedirect"]'));
                      if (forms.length !== 1) return forms.length === 0 ? 0 : 2;
                      const form = forms[0];
                      if ((form.getAttribute('method') || '').toLowerCase() !== 'post' ||
                          form.action !== $quotedServiceRedirect) return 2;
                      const saml = form.querySelectorAll('input[type="hidden"][name="SAMLRequest"]');
                      const relay = form.querySelectorAll('input[type="hidden"][name="RelayState"]');
                      const selected = form.querySelectorAll('input[type="hidden"][name="SelectedIdP"]');
                      // Some Cl@ve service providers (Sevilla-dip, Tenerife) send no RelayState.
                      if (saml.length !== 1 || relay.length > 1 || selected.length !== 1 ||
                          saml[0].value.length < 512 || saml[0].value.length > 32768 ||
                          (relay.length === 1 &&
                            (relay[0].value.length < 1 || relay[0].value.length > 512)) ||
                          selected[0].value !== '') return 2;
                      if (typeof window.selectedIdP !== 'function') return 2;
                      const controls = Array.from(document.querySelectorAll('[onclick]')).filter(
                        element => element.getAttribute('onclick') === $quotedAfirmaOnClick
                      );
                      const images = Array.from(document.querySelectorAll('img')).filter(
                        image => image.getAttribute('src') === $quotedAfirmaImage
                      );
                      if (controls.length !== 1 || images.length !== 1) return 2;
                      const controlCard = controls[0].closest('article');
                      const imageCard = images[0].closest('article');
                      if (!controlCard || controlCard !== imageCard) return 2;
                      controls[0].click();
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

    private fun clickStaCertificateLogin(
        scenario: ActivityScenario<MainActivity>,
        expectedEntryUrl: String,
        expectedIdentifyHref: String,
        expectedLoginUrl: String,
    ) {
        clickExactStaResponsiveIdentifyAnchor(
            scenario = scenario,
            expectedCurrentUrl = expectedEntryUrl,
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

    private fun clickBoeCertificateAnchor(scenario: ActivityScenario<MainActivity>) {
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
                if (webView.url != BOE_ENTRY_URL) {
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(boeCertificateAnchorScript()) { recipeCode ->
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
        if (hasObservedTerminalNavigationFailure()) return
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    // login.aspx opens the options page in a same-origin iframe; #optSsl then navigates window.top.
    private fun clickSedipualbaServerCertificateOption(
        scenario: ActivityScenario<MainActivity>,
        expectedEntryUrl: String,
        expectedLoginUrl: String,
        loginLabel: String,
        optionLabel: String,
    ) {
        clickExactLabeledAnchor(
            scenario = scenario,
            expectedCurrentUrl = expectedEntryUrl,
            expectedLabel = loginLabel,
            expectedHref = expectedLoginUrl,
        )
        clickSedipualbaServerCertificateOptionOnLoginPage(scenario, expectedLoginUrl, optionLabel)
    }

    private fun clickSedipualbaServerCertificateOptionOnLoginPage(
        scenario: ActivityScenario<MainActivity>,
        expectedLoginUrl: String,
        optionLabel: String,
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
                if (webView.url != expectedLoginUrl) {
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    sedipualbaServerCertificateOptionScript(expectedLoginUrl, optionLabel),
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
        if (hasObservedTerminalNavigationFailure()) return
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickExactStaResponsiveIdentifyAnchor(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        expectedHref: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedExpectedLabel = JSONObject.quote(STA_IDENTIFY_LABEL)
        val quotedExpectedHref = JSONObject.quote(expectedHref)
        val quotedExpectedOnClick = JSONObject.quote(STA_IDENTIFY_ONCLICK)
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
                if (currentUrl != null && !recipeUrlMatches(currentUrl, expectedCurrentUrl)) {
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      const elements = Array.from(document.querySelectorAll('a')).filter(element => {
                        const labels = element.querySelectorAll(':scope > span.a-text');
                        if (labels.length !== 1) return false;
                        const label = (labels[0].textContent || '').trim().replace(/\s+/g, ' ');
                        return label === $quotedExpectedLabel &&
                          element.getAttribute('href') === $quotedExpectedHref &&
                          element.getAttribute('onclick') === $quotedExpectedOnClick &&
                          element.classList.contains('responsive') &&
                          element.classList.contains('ui-link') &&
                          element.classList.contains('tamano-defecto') &&
                          element.classList.contains('iconed');
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

    private fun clickGestionaClaveLogin(
        scenario: ActivityScenario<MainActivity>,
        expectedOrigin: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        // The identification page carries an ephemeral, server-generated ?x= state.
        val identificationPrefix = "$expectedOrigin/?x="
        val quotedOrigin = JSONObject.quote(expectedOrigin)
        val quotedServiceProvider = JSONObject.quote(CLAVE_SERVICE_PROVIDER_URL)
        val quotedLabel = JSONObject.quote(GESTIONA_CLAVE_LABEL)
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
                if (currentUrl == null || !currentUrl.startsWith(identificationPrefix)) {
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      // WebView.url switches before the document does; the Kotlin URL check rejects a real mismatch.
                      if (location.origin !== $quotedOrigin || location.pathname !== '/') return 0;
                      const forms = Array.from(document.querySelectorAll('form[name="claveForm"]'));
                      if (forms.length === 0) return 0;
                      if (forms.length !== 1) return 2;
                      const form = forms[0];
                      if ((form.getAttribute('method') || '').toLowerCase() !== 'post' ||
                          form.action !== $quotedServiceProvider) return 2;
                      const saml = form.querySelectorAll('input[type="hidden"][name="SAMLRequest"]');
                      if (saml.length !== 1 || saml[0].value.length < 512 || saml[0].value.length > 32768) return 2;
                      const links = Array.from(form.querySelectorAll('a')).filter(link =>
                        link.getAttribute('onclick') === 'submit();' &&
                          (link.innerText || '').trim().replace(/\s+/g, ' ') === $quotedLabel
                      );
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

    private fun clickGestionaIdentificate(
        scenario: ActivityScenario<MainActivity>,
        expectedOrigin: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val expectedUrl = "$expectedOrigin/info.0"
        val quotedExpectedUrl = JSONObject.quote(expectedUrl)
        val quotedLabel = JSONObject.quote("Identifícate")
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
                if (webView.url != expectedUrl) {
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      if (location.href !== $quotedExpectedUrl) return 0;
                      const elements = Array.from(document.querySelectorAll('a')).filter(element =>
                        (element.innerText || '').trim().replace(/\s+/g, ' ') === $quotedLabel &&
                          /^\?x=[^:/\\?#&\s]+$/.test(element.getAttribute('href') || '') &&
                          new URL(element.href).origin === new URL($quotedExpectedUrl).origin
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
            if (clicked) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        val terminalFailureDeadline = SystemClock.elapsedRealtime() + RECIPE_TERMINAL_GRACE_MILLIS
        while (SystemClock.elapsedRealtime() < terminalFailureDeadline) {
            if (hasObservedTerminalNavigationFailure()) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        if (hasObservedTerminalNavigationFailure()) return
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun clickExactLabeledAnchor(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        expectedLabel: String,
        expectedHref: String,
        waitForExpectedUrl: Boolean = false,
        // Web-component sites (*.sede.gob.es) render the anchor inside an open shadow root of this host tag.
        shadowHostTag: String? = null,
        matchUrlWithoutQuery: Boolean = false,
        expectedOnClick: String? = null,
        expectedFormName: String? = null,
        expectedFormAction: String? = null,
        expectedCssClass: String? = null,
        expectedElementId: String? = null,
        requireVisible: Boolean = false,
        requireFormContainment: Boolean = false,
        expectedType: String? = null,
        expectedAriaLabel: String? = null,
        expectedTitle: String? = null,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedAnchorType = expectedType?.let { JSONObject.quote(it) } ?: "null"
        val quotedAnchorAriaLabel = expectedAriaLabel?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedTitle = expectedTitle?.let { JSONObject.quote(it) } ?: "null"
        val quotedShadowHost = JSONObject.quote(shadowHostTag.orEmpty())
        val quotedExpectedLabel = JSONObject.quote(expectedLabel)
        val quotedExpectedHref = JSONObject.quote(expectedHref)
        val quotedExpectedOnClick = expectedOnClick?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedUrl = JSONObject.quote(expectedCurrentUrl)
        val quotedCssClass = expectedCssClass?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedElementId = expectedElementId?.let { JSONObject.quote(it) } ?: "null"
        require((expectedFormName == null) == (expectedFormAction == null))
        require(!requireFormContainment || expectedFormName != null)
        val quotedFormName = expectedFormName?.let { JSONObject.quote(it) } ?: "null"
        val quotedFormAction = expectedFormAction?.let { JSONObject.quote(it) } ?: "null"
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
                // Tomcat rewrites this login URL on the first visit before the session cookie exists.
                val withoutSession = currentUrl?.let(::recipeUrlWithoutSession)
                val comparedUrl = if (matchUrlWithoutQuery) withoutSession?.substringBefore('?') else withoutSession
                if (comparedUrl != null && !recipeUrlMatches(comparedUrl, expectedCurrentUrl)) {
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
                      const withoutSessionId = value => {
                        const url = new URL(value);
                        url.pathname = url.pathname.replace(/;jsessionid=[^/;]+/gi, '');
                        return url.href;
                      };
                      const withoutSession = withoutSessionId(location.href);
                      const documentUrl = $matchUrlWithoutQuery ? withoutSession.split('?')[0] : withoutSession;
                      if (documentUrl !== $quotedExpectedUrl &&
                          !(!$quotedExpectedUrl.endsWith('/') && documentUrl === $quotedExpectedUrl + '/') &&
                          !($quotedExpectedUrl.endsWith('/') &&
                            documentUrl === $quotedExpectedUrl.slice(0, -1))) return 0;
                      const host = $quotedShadowHost;
                      // Header items nest another component (dnt-link) whose own shadow root holds the anchor.
                      const deep = root => Array.from(root.querySelectorAll('*'))
                        .flatMap(el => el.shadowRoot ? [el.shadowRoot, ...deep(el.shadowRoot)] : []);
                      const roots = host === '' ? [document] : Array.from(document.querySelectorAll(host))
                        .flatMap(h => h.shadowRoot ? [h.shadowRoot, ...deep(h.shadowRoot)] : []);
                      const anchors = roots.flatMap(root => Array.from(root.querySelectorAll('a')));
                      const elements = anchors.filter(element => {
                        if ($quotedExpectedElementId !== null && element.id !== $quotedExpectedElementId) return false;
                        if ($quotedCssClass !== null && !element.classList.contains($quotedCssClass)) return false;
                        if ($requireVisible && element.getClientRects().length === 0) return false;
                        const label = (element.innerText || '').trim().replace(/\s+/g, ' ');
                        // A first visit without cookies gets ;jsessionid=... rewritten into every href (CSN).
                        const href = (element.getAttribute('href') || '').replace(/;jsessionid=[^?#]*/i, '');
                        return label === $quotedExpectedLabel && href === $quotedExpectedHref &&
                          ($quotedExpectedTitle === null || element.getAttribute('title') === $quotedExpectedTitle) &&
                          ($quotedAnchorType === null || element.getAttribute('type') === $quotedAnchorType) &&
                          ($quotedAnchorAriaLabel === null ||
                            element.getAttribute('aria-label') === $quotedAnchorAriaLabel) &&
                          ($quotedExpectedOnClick === null ||
                            element.getAttribute('onclick') === $quotedExpectedOnClick);
                      });
                      if (elements.length === 0) return 0;
                      if (elements.length !== 1) return 2;
                      if ($quotedFormName !== null) {
                        const forms = Array.from(document.forms).filter(form =>
                          form.name === $quotedFormName || form.id === $quotedFormName);
                        if (forms.length !== 1 ||
                            withoutSessionId(forms[0].action) !== $quotedFormAction ||
                            (forms[0].getAttribute('method') || '').toLowerCase() !== 'post') return 2;
                        if ($requireFormContainment && !forms[0].contains(elements[0])) return 2;
                        if (!['', '_self', '_parent'].includes(
                            (forms[0].getAttribute('target') || '').toLowerCase())) return 2;
                      }
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
        expectedHandlerName: String,
    ) {
        // Unizar can expose a transitional DOM before the final certificate button is ready.
        // The current page labels the button through its tooltip title and binds the reviewed
        // handler from script instead of an inline onclick, so wait for that handler too.
        require(JS_IDENTIFIER.matches(expectedHandlerName))
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
                      const label = element.getAttribute('aria-label') ?? element.getAttribute('title');
                      const inlineOnClick = element.getAttribute('onclick');
                      if (element.id !== $quotedElementId ||
                          element.getAttribute('type') !== 'button' ||
                          label !== $quotedExpectedLabel ||
                          element.querySelector('img')?.getAttribute('alt') !== $quotedExpectedImageAlt ||
                          (inlineOnClick !== null && inlineOnClick !== $quotedExpectedOnClick)) return 2;
                      const handlerReady = typeof $expectedHandlerName === 'function' && (
                        inlineOnClick !== null ||
                        document.readyState === 'complete' ||
                        (window.jQuery?._data?.(element, 'events')?.click?.length ?? 0) > 0
                      );
                      if (!handlerReady) return 0;
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

    internal fun clickExactButton(
        scenario: ActivityScenario<MainActivity>,
        expectedCurrentUrl: String,
        expectedElementId: String?,
        expectedLabel: String?,
        expectedAriaLabel: String?,
        expectedOnClick: String?,
        waitForExpectedUrl: Boolean = false,
        waitForBadajozSignHook: Boolean = false,
        // null: the button carries no type attribute at all.
        expectedType: String? = "button",
        // Pages whose URL carries per-session query values (CAS tickets, return URLs) match on origin + path.
        matchUrlWithoutQuery: Boolean = false,
        // "input": a submit input whose value is its label.
        expectedTag: String = "button",
        expectedSrc: String? = null,
        expectedFormName: String? = null,
        expectedFormAction: String? = null,
        requireNoOnClick: Boolean = false,
        expectedParentAnchorClass: String? = null,
        expectedHandlerName: String? = null,
        requireVisible: Boolean = false,
        waitForAemetSignHook: Boolean = false,
        expectedName: String? = null,
        matchCanariasLoginAction: Boolean = false,
        waitForTransportesSignReady: Boolean = false,
        waitForLugoSignReady: Boolean = false,
        matchPalenciaSessionPath: Boolean = false,
    ) {
        require(expectedLabel != null || expectedAriaLabel != null)
        require(expectedTag in setOf("button", "input", "img"))
        require(expectedHandlerName == null || JS_IDENTIFIER.matches(expectedHandlerName))
        require(expectedParentAnchorClass == null || expectedTag == "img")
        require(!waitForTransportesSignReady || expectedCurrentUrl == TRANSPORTES_AUTH_URL)
        require(!waitForLugoSignReady || expectedCurrentUrl == LUGO_AUTH_URL)
        require(!matchPalenciaSessionPath || expectedCurrentUrl == PALENCIA_CLAVE_URL &&
            expectedTag == "img" && expectedSrc == PALENCIA_CLAVE_IMAGE_SRC &&
            expectedFormName == "formClave" && expectedFormAction == PALENCIA_CLAVE_FORM_ACTION)
        val quotedHandlerName = expectedHandlerName?.let { JSONObject.quote(it) } ?: "null"
        val quotedParentAnchorClass = expectedParentAnchorClass?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedName = expectedName?.let { JSONObject.quote(it) } ?: "null"
        require(!matchCanariasLoginAction || expectedCurrentUrl == CANARIAS_LOGIN_URL &&
            expectedFormName == "formData" && expectedFormAction == CANARIAS_LOGIN_ACTION_URL)
        val quotedExpectedTag = JSONObject.quote(expectedTag)
        val quotedExpectedType = expectedType?.let { JSONObject.quote(it) } ?: "null"
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedExpectedElementId = expectedElementId?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedLabel = expectedLabel?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedAriaLabel = expectedAriaLabel?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedOnClick = expectedOnClick?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedSrc = expectedSrc?.let { JSONObject.quote(it) } ?: "null"
        val quotedExpectedUrl = JSONObject.quote(expectedCurrentUrl)
        require((expectedFormName == null) == (expectedFormAction == null))
        val quotedFormName = expectedFormName?.let { JSONObject.quote(it) } ?: "null"
        val quotedFormAction = expectedFormAction?.let { JSONObject.quote(it) } ?: "null"
        val quotedWaitForBadajozSignHook = waitForBadajozSignHook.toString()
        var lastReadyState = "unobserved"
        var handlerReady = false
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
                val withoutSession = currentUrl?.let(::recipeUrlWithoutSession)
                val comparedUrl = if (matchUrlWithoutQuery) withoutSession?.substringBefore('?') else withoutSession
                if (comparedUrl != null && !recipeUrlMatches(comparedUrl, expectedCurrentUrl)) {
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
                      const withoutSessionId = value => {
                        const url = new URL(value);
                        url.pathname = url.pathname.replace(/;jsessionid=[^/;]+/gi, '');
                        return url.href;
                      };
                      const withoutSession = withoutSessionId(location.href);
                      const documentUrl = $matchUrlWithoutQuery ? withoutSession.split('?')[0] : withoutSession;
                      if (documentUrl !== $quotedExpectedUrl &&
                          !(!$quotedExpectedUrl.endsWith('/') && documentUrl === $quotedExpectedUrl + '/') &&
                          !($quotedExpectedUrl.endsWith('/') &&
                            documentUrl === $quotedExpectedUrl.slice(0, -1))) return 0;
                      const expectedId = $quotedExpectedElementId;
                      const expectedLabel = $quotedExpectedLabel;
                      const expectedAriaLabel = $quotedExpectedAriaLabel;
                      const expectedOnClick = $quotedExpectedOnClick;
                      const waitForBadajozSignHook = $quotedWaitForBadajozSignHook;
                      const expectedTag = $quotedExpectedTag;
                      const elements = Array.from(document.querySelectorAll(expectedTag)).filter(element => {
                        if (element.getAttribute('type') !== $quotedExpectedType) return false;
                        if (expectedId !== null && element.id !== expectedId) return false;
                        if ($quotedExpectedName !== null &&
                            element.getAttribute('name') !== $quotedExpectedName) return false;
                        if ($quotedExpectedSrc !== null &&
                            (element.getAttribute('src') || '').replace(/;jsessionid=[^/;?#]+/gi, '') !== $quotedExpectedSrc) return false;
                        if ($requireNoOnClick && element.getAttribute('onclick') !== null) return false;
                        const rawLabel = expectedTag === 'input' ? element.value : element.innerText;
                        const label = (rawLabel || '').trim().replace(/\s+/g, ' ');
                        if (expectedLabel !== null && label !== expectedLabel) return false;
                        if (expectedAriaLabel !== null &&
                            element.getAttribute('aria-label') !== expectedAriaLabel) return false;
                        return expectedOnClick === null ||
                          element.getAttribute('onclick') === expectedOnClick;
                      });
                      if (elements.length === 0) return 0;
                      if (elements.length !== 1) return 2;
                      if ($quotedParentAnchorClass !== null) {
                        const anchor = elements[0].parentElement;
                        if (anchor?.tagName !== 'A' || anchor.className !== $quotedParentAnchorClass ||
                            anchor.getAttribute('target') !== '_self') return 2;
                      }
                      if ($quotedHandlerName !== null || $waitForTransportesSignReady || $waitForLugoSignReady) {
                        const handlerReady =
                          ($quotedHandlerName === null || typeof window[$quotedHandlerName] === 'function') &&
                          (!$waitForTransportesSignReady || (
                            typeof window.Sign_firmar === 'function' && typeof window.SignParams === 'function' &&
                            typeof window.SignResult === 'function' && typeof window.doSign === 'function' &&
                            typeof window.MiniApplet?.sign === 'function' &&
                            typeof window.MiniApplet?.isAndroid === 'function' &&
                            typeof window.MiniApplet?.isIOS === 'function'
                          )) && (!$waitForLugoSignReady || (
                            typeof window.waiting === 'function' && typeof window.chargeClient === 'function' &&
                            typeof window.authenticate === 'function' &&
                            typeof window.AutoScript?.signBatch === 'function'
                          ));
                        if (document.readyState !== 'complete' || !handlerReady) {
                          return {readyState: document.readyState, handlerReady, clicked: false};
                        }
                      }
                      if ($requireVisible && (elements[0].disabled || elements[0].getClientRects().length === 0 ||
                          getComputedStyle(elements[0]).visibility !== 'visible')) return 0;
                      if ($waitForAemetSignHook && window.MiniApplet?.sign?.__jfmSignHook !== true) return 0;
                      if ($quotedFormName !== null) {
                        const forms = Array.from(document.forms).filter(form =>
                          form.name === $quotedFormName || form.id === $quotedFormName);
                        if (forms.length !== 1) return 2;
                        let actionMatches = withoutSessionId(forms[0].action) === $quotedFormAction;
                        if ($matchCanariasLoginAction) {
                          // sfest.base.js caches the library used by firmar -> getScript().sign in Script.
                          const script = window.Script;
                          if (typeof script?.sign !== 'function' || script.sign.__jfmSignHook !== true) return 0;
                          const action = new URL(forms[0].action);
                          const params = action.searchParams;
                          actionMatches = action.origin + action.pathname === $quotedFormAction &&
                            action.hash === '' && action.username === '' && action.password === '' &&
                            Array.from(params.keys()).length === 6 &&
                            params.get('i3l') === 'es_ES' && params.get('i3pn') === 'identificacion' &&
                            params.get('i3sc') === '' && params.get('i3pt') === 'S' &&
                            params.get('i3d') === 'e8e67340-afe4-11e6-9a06-1eb47e039c2b' &&
                            Boolean(params.get('i3csrf')) && window.formData === forms[0];
                        }
                        if (!actionMatches ||
                            (forms[0].getAttribute('method') || '').toLowerCase() !== 'post' ||
                            (expectedTag !== 'img' && elements[0].form !== forms[0])) return 2;
                        if ($matchPalenciaSessionPath && !forms[0].contains(elements[0])) return 2;
                        if (!['', '_self', '_parent'].includes(
                            (forms[0].getAttribute('target') || '').toLowerCase())) return 2;
                        if (['formaction', 'formmethod', 'formtarget'].some(
                            name => elements[0].getAttribute(name) !== null)) return 2;
                      }
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
                        else -> if ((expectedHandlerName != null || waitForTransportesSignReady ||
                                waitForLugoSignReady) && recipeCode.startsWith("{")) {
                            val state = JSONObject(recipeCode)
                            lastReadyState = state.optString("readyState").takeIf {
                                it in setOf("loading", "interactive", "complete")
                            } ?: "unknown"
                            handlerReady = state.optBoolean("handlerReady")
                        }
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
            if (clicked) {
                if (expectedHandlerName != null || waitForTransportesSignReady || waitForLugoSignReady) {
                    println("REAL_E2E_RECIPE_READINESS readyState=complete handlerReady=true clicked=true")
                }
                return
            }
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        val terminalFailureDeadline =
            SystemClock.elapsedRealtime() + RECIPE_TERMINAL_GRACE_MILLIS
        while (SystemClock.elapsedRealtime() < terminalFailureDeadline) {
            if (hasObservedTerminalNavigationFailure()) return
            SystemClock.sleep(RECIPE_POLL_MILLIS)
        }
        if (hasObservedTerminalNavigationFailure()) return
        if (expectedHandlerName != null || waitForTransportesSignReady || waitForLugoSignReady) {
            println("REAL_E2E_RECIPE_READINESS readyState=$lastReadyState handlerReady=$handlerReady clicked=false")
        }
        error("REAL_E2E_RECIPE_TARGET_TIMEOUT")
    }

    private fun recipeUrlWithoutSession(value: String): String {
        val url = Uri.parse(value)
        return url.buildUpon()
            .encodedPath(url.encodedPath?.replace(Regex(";jsessionid=[^/;]+", RegexOption.IGNORE_CASE), ""))
            .build().toString()
    }

    private fun recipeUrlMatches(actualUrl: String, expectedUrl: String): Boolean =
        actualUrl == expectedUrl ||
            (expectedUrl.endsWith("/") && actualUrl == expectedUrl.dropLast(1)) ||
            (!expectedUrl.endsWith("/") && actualUrl == "$expectedUrl/")


    // The REC header "Iniciar sesión" item requests a SAML login over XHR and posts its own Cl@ve form, so the
    // recipe only clicks it and lets clickClaveAfirmaProvider wait for the ServiceProvider page.
    private fun clickRecClaveLogin(
        scenario: ActivityScenario<MainActivity>,
    ) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val quotedLabel = JSONObject.quote(REC_LOGIN_LABEL)
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
                if (currentUrl != null && currentUrl != REC_HOME_URL) {
                    failure = "REAL_E2E_RECIPE_SOURCE_MISMATCH"
                    inspected.countDown()
                    return@onActivity
                }
                webView.evaluateJavascript(
                    """
                    (() => {
                      // WebView.url switches before the document does; the Kotlin URL check rejects a real mismatch.
                      if (location.href !== ${JSONObject.quote(REC_HOME_URL)}) return 0;
                      if (document.readyState !== 'complete') return 0;
                      const items = Array.from(document.querySelectorAll('dnt-header-item')).filter(element =>
                        element.getAttribute('text') === $quotedLabel
                      );
                      if (items.length !== 1) return items.length === 0 ? 0 : 2;
                      if (items[0].getAttribute('icon') !== 'Login' || items[0].getAttribute('href') !== '#' ||
                          items[0].closest('dnt-modal')) return 2;
                      if (!items[0].classList.contains('hydrated')) return 0;
                      items[0].click();
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
                record.contains("event=BLANK_DOCUMENT") ||
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
        var signingCoordinator = if (safeAuthSigning) {
            signingCoordinatorForScenario()
        } else {
            null
        }
        var signingCoordinatorRefreshed = false

        while (
            SystemClock.elapsedRealtime() < deadline ||
                postSignObservationDeadline?.let { SystemClock.elapsedRealtime() < it } == true
        ) {
            val records = diagnosticRecords()
            updateRecordObservations(records, result)
            postSignTracker?.let { updatePostSignObservations(records, it, result) }
            updateCurrentHostFromRecords(records, result)

            // The WebView bridge can finish prepare while Compose is still replacing the
            // browser subtree. Refresh the coordinator once after the sanitized preparation
            // marker so a recreated Activity cannot leave the probe watching a stale instance.
            if (safeAuthSigning && !signingCoordinatorRefreshed && records.any {
                    it.contains("event=MINIAPPLET_BRIDGE") &&
                        it.contains("stage=PREPARE_READY")
                }
            ) {
                signingCoordinator = signingCoordinatorForScenario()
                signingCoordinatorRefreshed = true
            }

            if (result.hasTerminalSecurityFailure()) {
                result.classification = ProbeClassification.FAIL_SECURITY_OR_NETWORK
                return
            }

            if (Capability.CLIENT_TLS_AUTH in profileCapabilities &&
                (taggedNodeVisible(CLIENT_AUTH_DIALOG_TAG) || taggedNodeVisible(INTERACTIVE_CLIENT_AUTH_DIALOG_TAG))
            ) {
                result.clientAuthObserved = true
                result.level = maxOf(result.level, 2)
                val host = allowedClientAuthHosts.firstOrNull { allowedHost ->
                    clientAuthDialogTextVisible("Dominio: $allowedHost")
                }
                if (host == null) {
                    // Either the in-place prompt names a host outside the profile, or the app fell
                    // back to its interactive prompt for a server the profile did not authorize.
                    result.unexpectedClientAuthHost = true
                    if (!clickTaggedNode(CLIENT_AUTH_CANCEL_TAG)) {
                        clickTaggedNode(INTERACTIVE_CLIENT_AUTH_CANCEL_TAG)
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
                rule.waitUntil(timeoutMillis = 5_000) {
                    rule.onAllNodes(hasTestTag(CLIENT_AUTH_CONTINUE_TAG) and isEnabled(), useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                clickTaggedNode(CLIENT_AUTH_CONTINUE_TAG)
            }

            if (Capability.SELECT_CERTIFICATE in profileCapabilities && !certificateSelectionHandled &&
                rule.onAllNodesWithText("Compartir certificado").fetchSemanticsNodes().isNotEmpty()
            ) {
                result.certificateSelectionObserved = true
                result.level = maxOf(result.level, 3)
                rule.waitUntil(timeoutMillis = 5_000) {
                    rule.onAllNodes(
                        hasText("Compartir", substring = false, ignoreCase = false) and isEnabled(),
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText("Compartir").performClick()
                result.publicCertificateShared = true
                certificateSelectionHandled = true
                result.level = maxOf(result.level, 4)
            }

            val awaitingSigning = if (safeAuthSigning && !signingHandled) {
                signingCoordinator?.state as? SigningUiState.AwaitingConfirmation
            } else {
                null
            }
            val signingConfirmationVisible = if (safeAuthSigning) {
                awaitingSigning != null
            } else {
                rule.onAllNodesWithText("Solicitud de firma").fetchSemanticsNodes().isNotEmpty()
            }
            if (Capability.SIGN in profileCapabilities && !signingHandled &&
                signingConfirmationVisible
            ) {
                if (!safeAuthSigning) {
                    result.signingConfirmationObserved = true
                    result.level = maxOf(result.level, 3)
                }
                if (!deepEnabled && profileId == AEMET_PROFILE_ID) {
                    // AEMET's error callback submits its login form, so stop without delivering a callback.
                    abandonAemetLoginAtBoundary()
                    result.signingCancelledAtBoundary = true
                    classify(profileCapabilities, result)
                    return
                }
                if (deepEnabled && profileId in SAFE_AUTH_SIGN_PROFILES) {
                    val preSignRecords = diagnosticRecords()
                    val signingEvidenceTracker = SigningEvidenceTracker(preSignRecords)
                    val owner = requireNotNull(signingCoordinator)
                    val requestId = requireNotNull(awaitingSigning).requestId
                    val confirmation = clickSigningConfirmation(owner, profileId, requestId)
                    result.signingConfirmationObserved = confirmation.dialogObserved
                    result.signingConfirmed = confirmation.confirmed
                    if (confirmation.dialogObserved) result.level = maxOf(result.level, 3)
                    if (!confirmation.confirmed) {
                        result.signingFailureCode = confirmation.failureCode?.name
                        result.infrastructureError = confirmation.infrastructureCode
                        result.classification = ProbeClassification.INFRASTRUCTURE_ERROR
                        return
                    }
                    val completedRecords = waitForSigningTerminalState(
                        signingCoordinator = owner,
                        profileId = profileId,
                        requestId = requestId,
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

    private fun abandonAemetLoginAtBoundary() {
        val coordinator = signingCoordinatorForScenario()
        val deadline = SystemClock.elapsedRealtime() + SIGNING_OWNER_TIMEOUT_MILLIS
        val abandoned = onSigningMainUntil(deadline) {
            check(coordinator.isCurrent(AEMET_PROFILE_ID)) { "REAL_E2E_SIGN_CONFIRM_OWNER_CHANGED" }
            val pending = coordinator.state as? SigningUiState.AwaitingConfirmation
                ?: error("REAL_E2E_SIGNING_REQUEST_MISSING")
            coordinator.orchestrator.cancelSigning(SigningCancelReason.BACKGROUND, pending.requestId)
            coordinator.state is SigningUiState.Idle
        }
        check(abandoned == true) { "REAL_E2E_SIGNING_ABANDON_FAILED" }
    }

    private fun waitForSigningTerminalState(
        signingCoordinator: SigningProbeOwner,
        profileId: String,
        requestId: UUID,
        tracker: SigningEvidenceTracker,
        result: ProbeResult,
    ): List<String>? {
        val deadline = SystemClock.elapsedRealtime() + SIGNING_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            updateSigningEvidence(diagnosticRecords(), tracker, result)
            val current = onSigningMainUntil(deadline) {
                check(signingCoordinator.isCurrent(profileId)) { "REAL_E2E_SIGN_CONFIRM_OWNER_CHANGED" }
                signingCoordinator.state
            } ?: error("REAL_E2E_SIGN_CONFIRM_UI_QUERY_FAILED")
            check(current.requestIdOrNull() == requestId) {
                if (current is SigningUiState.Idle) "REAL_E2E_SIGN_CONFIRM_IDLE"
                else "REAL_E2E_SIGN_CONFIRM_REQUEST_CHANGED"
            }
            when (val observed = current) {
                is SigningUiState.Completed -> {
                    result.signatureCompleted = true
                    result.level = maxOf(result.level, 4)
                    return diagnosticRecords()
                }
                is SigningUiState.Failed -> {
                    result.signingFailureCode = observed.code.name
                    return null
                }
                is SigningUiState.AwaitingConfirmation -> error("REAL_E2E_SIGN_CONFIRM_STATE_UNCHANGED")
                else -> Unit
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        result.signingFailureCode = "TIMEOUT"
        return null
    }

    private fun signingCoordinatorForScenario(): SigningProbeOwner {
        // The Activity installs one owner for ordinary and batch login requests after content initialization.
        val deadline = SystemClock.elapsedRealtime() + SIGNING_OWNER_TIMEOUT_MILLIS
        while (true) {
            val coordinator = onSigningMainUntil(deadline) {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull() ?: return@onSigningMainUntil null
                val orchestrator = MainActivity::class.java.getDeclaredField("signing")
                    .apply { isAccessible = true }
                    .get(activity) as? SigningOrchestrator ?: return@onSigningMainUntil null
                SigningProbeOwner(activity, orchestrator)
            }
            coordinator?.let { return it }
            check(SystemClock.elapsedRealtime() < deadline) { "REAL_E2E_SIGNING_OWNER_MISSING" }
            SystemClock.sleep(POLL_MILLIS)
        }
    }

    private class SigningProbeOwner(val activity: MainActivity, val orchestrator: SigningOrchestrator) {
        val state: SigningUiState get() =
            orchestrator.visibleState(orchestrator.ordinaryState.value, orchestrator.batchState.value)

        fun isCurrent(profileId: String): Boolean {
            val navigation = MainActivity::class.java.getDeclaredField("navigation").apply { isAccessible = true }
                .get(activity) as AppNavigationState
            val currentProfile = (navigation.destination as? AppDestination.Browser)?.profileId?.value
            return activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                currentProfile == profileId &&
                MainActivity::class.java.getDeclaredField("signing").apply { isAccessible = true }
                    .get(activity) === orchestrator
        }
    }

    // The browser chrome reuses "Acceso con certificado" as its trust label on client-auth
    // origins, and Material3 dialogs do not always expose an IsDialog ancestor on the emulator.
    // Scope client-auth lookups to the dialogs' test tags instead of their visible text.
    private fun taggedNodeVisible(tag: String): Boolean =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun clientAuthDialogTextVisible(text: String): Boolean {
        val matcher = hasText(text, substring = false, ignoreCase = false) and
            hasAnyAncestor(hasTestTag(CLIENT_AUTH_DIALOG_TAG))
        return rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodes(matcher, useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()
    }

    private fun clickTaggedNode(tag: String): Boolean {
        val nodes = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
        if (nodes.fetchSemanticsNodes().isEmpty()) return false
        nodes[0].performClick()
        return true
    }

    private fun clickSigningConfirmation(
        owner: SigningProbeOwner,
        profileId: String,
        requestId: UUID,
    ): SignConfirmationResult {
        return RealE2eSigningConfirmation.click(
            activity = owner.activity,
            requestId = requestId,
            state = { owner.state },
            ownsRequest = { owner.isCurrent(profileId) },
            timeoutMillis = UI_TIMEOUT_MILLIS,
            advanceFrame = { rule.mainClock.advanceTimeByFrame() },
        )
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
        result.networkError = result.networkError || records.any {
            it.contains("event=NETWORK_ERROR") || it.contains("event=BLANK_DOCUMENT")
        }
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
                (result.pageStarted || result.pageFinished) &&
                // Everything before the consequential step must be proven, including the certificate login.
                (Capability.CLIENT_TLS_AUTH !in capabilities || result.clientCertProceeded)
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
        val fileRecords = if (file.isFile) {
            file.readLines(StandardCharsets.US_ASCII)
        } else {
            emptyList()
        }
        // The file sink is a cross-process artifact. During a live probe its truncating rewrite can
        // briefly expose an incomplete file; the in-process sanitized snapshot is ordered and uses
        // the same allowlisted records. Prefer it when available and retain the file as a fallback
        // for a recreated process.
        val loggerRecords = application().sanitizedLogger.snapshot()
        return loggerRecords.ifEmpty { fileRecords }
    }

    private fun waitUntil(timeoutMillis: Long, predicate: () -> Boolean) {
        rule.waitUntil(timeoutMillis = timeoutMillis, condition = predicate)
    }

    private fun safeInfrastructureCode(throwable: Throwable, stage: ProbeStage): String =
        safeInfrastructureCodeFor(unwrapMainThreadFailure(throwable), stage)

    // ActivityScenario.onActivity and other main-thread hops rethrow the original failure inside
    // a bare RuntimeException; report the original type so the sanitized code stays diagnostic.
    private fun unwrapMainThreadFailure(throwable: Throwable): Throwable {
        var current = throwable
        var depth = 0
        while (current.javaClass == RuntimeException::class.java && depth < MAX_FAILURE_UNWRAP_DEPTH) {
            val cause = current.cause ?: break
            if (cause === current) break
            current = cause
            depth += 1
        }
        return current
    }

    private fun safeInfrastructureCodeFor(throwable: Throwable, stage: ProbeStage): String = when (throwable.message) {
        "REAL_E2E_CERTIFICATE_MISSING" -> "CERTIFICATE_MISSING"
        "REAL_E2E_SIGNING_OWNER_MISSING" -> "SIGNING_OWNER_MISSING"
        "REAL_E2E_PASSWORD_MISSING" -> "PASSWORD_MISSING"
        "REAL_E2E_RECIPE_WEBVIEW_MISSING" -> "RECIPE_WEBVIEW_MISSING"
        "REAL_E2E_RECIPE_SOURCE_MISMATCH" -> "RECIPE_SOURCE_MISMATCH"
        "REAL_E2E_RECIPE_TARGET_MISMATCH" -> "RECIPE_TARGET_MISMATCH"
        "REAL_E2E_RECIPE_INSPECT_TIMEOUT" -> "RECIPE_INSPECT_TIMEOUT"
        "REAL_E2E_RECIPE_TARGET_TIMEOUT" -> "RECIPE_TARGET_TIMEOUT"
        "REAL_E2E_RECIPE_PAGE_READY_TIMEOUT" -> "RECIPE_PAGE_READY_TIMEOUT"
        else -> {
            val message = throwable.message.orEmpty()
            if (message.startsWith(SIGN_CONFIRM_ERROR_PREFIX)) {
                message.removePrefix("REAL_E2E_").takeIf { SAFE_ERROR_TOKEN.matches(it) }?.let { return it }
            }
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

    private data class RecipeLinkHop(val pageUrl: String, val label: String, val href: String)

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
        const val BOE_PORTAL_ID = "age-agencia-estatal-del-boletin-oficial-del-estado-boe"
        const val BOE_ENTRY_URL = "https://extranet.boe.es/anuncios/"
        const val CMT_PORTAL_ID = "age-comisionado-para-el-mercado-de-tabacos-cmt"
        const val CMT_ENTRY_URL = "https://sede.cmt.gob.es/"
        const val CMT_LOGIN_URL = "https://sede.cmt.gob.es/carpetaciudadana/login.aspx?returnUrl=" +
            "https%3a%2f%2fsede.cmt.gob.es%2fcarpetaciudadana%2fcarpeta_ciudadana.aspx"
        const val FONDOS_PORTAL_ID = "age-direccion-general-de-fondos-europeos"
        const val FONDOS_LOGIN_URL =
            "https://webpub2.igae.hacienda.gob.es/accesoremoto/frmExecAPP.aspx?code=SIRIOWS&ta=ANO"
        const val IMSERSO_PORTAL_ID = "age-instituto-de-mayores-y-servicios-sociales-imserso"
        const val INE_PORTAL_ID = "age-instituto-nacional-de-estadistica-ine"
        const val INE_ENTRY_URL = "https://sede.ine.gob.es/es/index.htm"
        const val SEPES_PORTAL_ID = "age-entidad-publica-empresarial-de-suelo-sepes"
        const val SEPES_LOGIN_URL = "https://sede.transportes.gob.es/MFOM.Sisgest/autenticacion.aspx"
        const val TEA_PORTAL_ID = "age-sede-electronica-de-los-tribunales-economico-administrativos-tea"
        const val TEA_SELECTOR_URL =
            "https://sede.tea.hacienda.gob.es/static_files/common/html/selector_acceso/" +
                "SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0&aut=CPE"
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
            // Owner decision 2026-11-06: prove the Cl@ve certificate login only, never file.
            "tenerife-sede-electronica",
            "caib-portafib",
            // STA batch signature registers the instancia: prove the certificate login only, never file.
            "diputacion-burgos-portal",
            "diputacion-huesca-portal",
            // Cl@ve login before a MiniApplet/AutoScript signature that files: prove the login only, never file.
            "airef-instancia-general",
            "jccm-registro-generico",
            "policia-solicitud-generica",
            "ministerio-economia-instancia-generica",
            "age-portal-de-la-transparencia",
            "extremadura-tramites",
            "fuerteventura-sede-electronica",
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
        const val STA_IDENTIFY_ONCLICK =
            "if (jQuery(this).hasClass('disabled')) {return false;} else {;}"
        const val STA_CERTIFICATE_LINK_ID = "link-certificado"
        const val STA_CERTIFICATE_LINK_HREF =
            "/sta/CarpetaPrivate/Certificate?APP_CODE=STA&PAGE_CODE=HOME"
        const val SEDIPUALBA_ALBACETE_PORTAL_ID = "diputacion-albacete-portal"
        const val SEDIPUALBA_ALBACETE_ENTRY_URL =
            "https://sede.dipualba.es/carpetaciudadana/tramite.aspx?idtramite=567"
        const val SEDIPUALBA_ALBACETE_LOGIN_URL =
            "https://sede.dipualba.es/carpetaciudadana/login.aspx?returnUrl=" +
                "https%3a%2f%2fsede.dipualba.es%2fcarpetaciudadana%2ftramite.aspx%3fidtramite%3d567"
        const val SEDIPUALBA_CUENCA_PORTAL_ID = "diputacion-cuenca-portal"
        const val SEDIPUALBA_CUENCA_ENTRY_URL =
            "https://sede.dipucuenca.es/carpetaciudadana/tramite.aspx?idtramite=12074"
        const val SEDIPUALBA_CUENCA_LOGIN_URL =
            "https://sede.dipucuenca.es/carpetaciudadana/login.aspx?returnUrl=" +
                "https%3a%2f%2fsede.dipucuenca.es%2fcarpetaciudadana%2ftramite.aspx%3fidtramite%3d12074"
        const val SEDIPUALBA_LEON_PORTAL_ID = "diputacion-leon-sede"
        const val SEDIPUALBA_LEON_ENTRY_URL =
            "https://sede.dipuleon.es/carpetaciudadana/tramite.aspx?idtramite=20270"
        const val SEDIPUALBA_LEON_LOGIN_URL =
            "https://sede.dipuleon.es/carpetaciudadana/login.aspx?returnUrl=" +
                "https%3a%2f%2fsede.dipuleon.es%2fcarpetaciudadana%2ftramite.aspx%3fidtramite%3d20270"
        const val SANIDAD_PORTAL_ID = "age-ministerio-de-sanidad"
        const val ARAGON_TRAMITES_PORTAL_ID = "aragon-tramites-sede"
        const val MADRID_CUENTA_DIGITAL_PORTAL_ID = "comunidad-madrid-cuenta-digital-carne-joven"
        const val HUELVA_PORTAL_ID = "diputacion-huelva-portal"
        const val JAEN_PORTAL_ID = "diputacion-jaen-sede"
        const val VALLADOLID_PORTAL_ID = "diputacion-valladolid-sede"
        const val GVA_PORTAL_ID = "gva-sede"
        const val MALLORCA_INSTITUCIONAL_PORTAL_ID = "mallorca-portal-institucional"
        const val SEGURIDAD_SOCIAL_PORTAL_ID = "seguridad-social-sede"
        const val IMPORTASS_PORTAL_ID = "tgss-importass"
        const val HUELVA_INDEX_URL = "https://sede.diphuelva.es/opencms/sede/elements/secciones/index"
        const val HUELVA_AUTH_URL =
            "https://sede.diphuelva.es/opencms/system/modules/gsede/elements/secciones/autenticacion/" +
            "autenticacion.jsp"
        const val VALLADOLID_LOGIN_URL = "https://www.sede.diputaciondevalladolid.es/tgauth/login"
        const val GVA_LOGIN_URL = "https://www.tramita.gva.es/ctt-att-atr/login.html"
        const val SEGURIDAD_SOCIAL_ENTRY_URL =
            "https://sede.seg-social.gob.es/wps/portal/sede/sede/Inicio/RegistroElectronicoApod/NREASS_3?" +
            "changeLanguage=es"
        const val SEGURIDAD_SOCIAL_IDP_URL = "https://idp.seg-social.es/PGIS/Login"
        const val ARAGON_TRAMITES_ENTRY_URL = "https://aplicaciones.aragon.es/tramitar/solicitud-general/identificacion"
        const val ARAGON_IDENTIFICATION_FRAME_URL =
            "https://aplicaciones.aragon.es/mfe_core/rest/identification/TTO/" +
            "aHR0cHM6Ly9hcGxpY2FjaW9uZXMuYXJhZ29uLmVzL3RyYW1pdGFyL3NvbGljaXR1ZC1nZW5lcmFsL2lkZW50aWZpY2FjaW9uL2lk" +
            "ZW50aWZpY2Fkbw=="
        const val MADRID_CUENTA_DIGITAL_LOGIN_URL = "https://digital.comunidad.madrid/login/"
        val SANIDAD_LINK_HOPS = listOf(
            RecipeLinkHop(
                "https://sede.mscbs.gob.es/",
                "Registro electrónico",
                "/registroElectronico/home.htm",
            ),
            RecipeLinkHop(
                "https://sede.mscbs.gob.es/registroElectronico/home.htm",
                "índice de formularios",
                "https://sede.mscbs.gob.es/registroElectronico/formularios.htm",
            ),
            RecipeLinkHop(
                "https://sede.mscbs.gob.es/registroElectronico/formularios.htm",
                "Mejora de la Atencion Temprana",
                "#TRAM_TEMPRANAATENCION",
            ),
            RecipeLinkHop(
                "https://sede.mscbs.gob.es/registroElectronico/formularios.htm",
                "Certificado digital",
                "https://sede.mscbs.gob.es/SIGEM_AutenticacionWeb/validacionCertificado.do?" +
                "REDIRECCION=RegistroTelematico&tramiteId=TRAM_TEMPRANAATENCION&ENTIDAD_ID=000&LANG=es&COUNTRY=ES",
            ),
        )
        val JAEN_LINK_HOPS = listOf(
            RecipeLinkHop(
                "https://sede.dipujaen.es/SolicitudGenerica",
                "Acceder",
                "/IniciarSesion",
            ),
            RecipeLinkHop(
                "https://sede.dipujaen.es/IniciarSesion",
                "Acceder con certificado digital",
                "/IniciarSesion/Certificado",
            ),
        )

        const val SEDIPUALBA_MALLORCA_PORTAL_ID = "mallorca-sede-electronica"
        const val SEDIPUALBA_MALLORCA_ENTRY_URL =
            "https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082"
        const val SEDIPUALBA_MALLORCA_LOGIN_URL =
            "https://cim.secimallorca.net/carpetaciudadana/login.aspx?returnUrl=" +
                "https%3a%2f%2fcim.secimallorca.net%2fsegex%2ftramite.aspx%3fidtramite%3d12082"
        const val SEDIPUALBA_LOGIN_LABEL_ES = "Iniciar sesi\u00f3n"
        const val SEDIPUALBA_LOGIN_LABEL_CA = "Iniciar sessi\u00f3"
        const val SEDIPUALBA_SSL_OPTION_LABEL_ES =
            "Identificarse con certificado digital a trav\u00e9s de nuestro servidor"
        const val SEDIPUALBA_SSL_OPTION_LABEL_CA =
            "Identificar-se amb certificat digital a trav\u00e9s del nostre servidor"
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
                "SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FBUGC-JDIT%2FMdcAcceso&aut=CPE"
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
        const val MALAGA_PORTAL_ID = "diputacion-malaga-sede"
        const val MUGEJU_PORTAL_ID = "age-mutualidad-general-judicial-mugeju"
        const val MALAGA_ENTRY_URL =
            "https://sede.malaga.es/instancia-general/nueva-instancia-general/"
        const val MALAGA_LOCAL_CLAVE_URL = "https://clave.malaga.es/clave.php"
        const val MALAGA_CLAVE_SUBMIT_VALUE = "Acceder con cl@ve"
        const val GESTIONA_CLAVE_LABEL = "Acceso con sistema Cl@ve"
        const val SEDE_GOB_LOGIN_LABEL = "Iniciar sesión"
        val SEDE_GOB_CLAVE_HOSTS = mapOf(
            "age-enaire-entidad-publica-empresarial" to "enaire.sede.gob.es",
            "age-instituto-de-astrofisica-de-canarias-iac" to "iac.sede.gob.es",
            "age-instituto-de-contabilidad-y-auditoria-de-cuentas-icac" to "icac.sede.gob.es",
        )
        // IAC and ICAC open a /login page first; ENAIRE goes straight to claveproxy.
        val SEDE_GOB_LOGIN_PAGE_PORTALS = setOf(
            "age-instituto-de-astrofisica-de-canarias-iac",
            "age-instituto-de-contabilidad-y-auditoria-de-cuentas-icac",
        )
        class GsedeClave(val origin: String, val homeUrl: String, val claveLabel: String, val claveOnClick: String)
        const val GSEDE_IDENTIFY_LABEL = "Identificarse"
        const val GSEDE_AUTHENTICATION_PATH =
            "/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp"
        val GSEDE_CLAVE_PORTALS = mapOf(
            "diputacion-granada-portal" to GsedeClave(
                origin = "https://sede.dipgra.es",
                homeUrl = "https://sede.dipgra.es/opencms/system/modules/sede/elements/secciones/index",
                // CSS text-transform: uppercase applies to innerText.
                claveLabel = "ACCEDER",
                claveOnClick = "loginClave();",
            ),
            // ICO's Cl@ve button is an image; two buttons share id autButton, the onclick tells them apart.
            "age-instituto-de-credito-oficial-ico" to GsedeClave(
                origin = "https://sedeico.gob.es",
                homeUrl = "https://sedeico.gob.es/opencms/system/modules/sede/index",
                claveLabel = "",
                claveOnClick = "waiting(); loginClave();",
            ),
        )
        const val CNMV_PORTAL_ID = "age-comision-nacional-del-mercado-de-valores-cnmv"
        const val CNMV_CLAVE_FORM_ACTION =
            "https://sede.cnmv.gob.es/sedecnmv/IdenCiudadana/LoginCarpCiudadana.aspx"
        const val CNMV_CARPETA_URL = "https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx"
        const val CNMV_CARPETA_HREF = "IdenCiudadana/LoginCarpCiudadana.aspx"
        const val CNMV_CLAVE_URL = "https://sede.cnmv.gob.es/sedecnmv/IdenCiudadana/LoginCarpCiudadana.aspx"
        const val CNMV_CLAVE_ID = "btn_Clave"
        const val CNMV_CLAVE_IMAGE_SRC = "../imagenes/clave.png"
        const val CSD_PORTAL_ID = "age-consejo-superior-de-deportes"
        const val CSD_CLAVE_FORM_ACTION =
            "https://sede.csd.gob.es/oficinavirtual/Default.aspx"
        const val CSD_OFICINA_URL = "https://sede.csd.gob.es/"
        const val CSD_OFICINA_HREF = "https://sede.csd.gob.es/oficinavirtual/Default.aspx"
        const val CSD_CLAVE_URL = "https://sede.csd.gob.es/oficinavirtual/Default.aspx"
        const val CSD_CLAVE_ID = "ctl00_ContentPlaceHolder1_BtnClave"
        const val POLICIA_PORTAL_ID = "age-cuerpo-nacional-de-policia"
        const val POLICIA_CLAVE_URL = "https://sede.policia.gob.es/portalCiudadano/_es/index.php"
        const val POLICIA_CLAVE_HREF = "/sede/pages/procedimientos/solicitudGenerica.xhtml"
        const val CATASTRO_PORTAL_ID = "age-direccion-general-del-catastro"
        const val CATASTRO_CLAVE_0_URL = "https://www.sedecatastro.gob.es/Accesos/SECAccProcedimientos.aspx?Dest=22"
        const val CATASTRO_CLAVE_0_HREF = "JavaScript:ElegirAcceso2('22');"
        const val CATASTRO_CLAVE_1_URL = "https://www.sedecatastro.gob.es/Accesos/SECAccDNI.aspx?Dest=22"
        const val CATASTRO_CLAVE_1_HREF = "../Accesos/SECAccPIN.aspx?Dest=22&del=&mun=&final=&RefC=&texp=REGI"
        const val MINECO_PORTAL_ID = "age-ministerio-de-economia-comercio-y-empresa"
        const val MINECO_CLAVE_FORM_ACTION =
            "https://serviciosede.mineco.gob.es/FB/Home.aspx?control=161_IG"
        const val MINECO_CLAVE_URL = "https://serviciosede.mineco.gob.es/FB/Home.aspx?control=161_IG"
        const val MINECO_CLAVE_ID = "ctl00_ctl00_MainContentPlaceHolder_ucIdentificacion_btnIdentificacion"
        const val MINECO_CLAVE_IMAGE_SRC = "images/clave.jpg"
        const val TRANSPARENCIA_PORTAL_ID = "age-portal-de-la-transparencia"
        const val TRANSPARENCIA_CLAVE_URL =
            "https://transparencia.sede.gob.es/procedimiento/portada" +
                "?idProc=133628&idAmb=101524"
        const val TRANSPARENCIA_CLAVE_HREF =
            "/claveproxy/clave/authenticate" +
                "?returnUrl=https%3A%2F%2Ftransparencia.sede.gob.es%2Fprocedimiento%2Fformulario" +
                    "%3FidProc%3D133628%26idAmb%3D101524"
        const val RED_ES_PORTAL_ID = "age-red-es"
        const val RED_ES_CLAVE_0_URL = "https://sede.red.gob.es/"
        const val RED_ES_CLAVE_0_HREF = "/es/carpeta-del-ciudadano/mis-tramites"
        const val RED_ES_CLAVE_1_URL = "https://sede.red.gob.es/es/carpeta-del-ciudadano/mis-tramites"
        const val RED_ES_CLAVE_1_HREF = "https://sede.red.gob.es/oficina/solicitudes/solicitudes.do"
        const val RED_ES_CLAVE_URL = "https://sede.red.gob.es/oficina/solicitudes/solicitudes.do"
        const val ALMERIA_PORTAL_ID = "diputacion-almeria-portal"
        const val ALMERIA_CLAVE_FORM_ACTION =
            "https://ov.dipalme.org/SP2/TiWorksRequest"
        const val ALMERIA_CLAVE_URL =
            "https://ov.dipalme.org/TiProceeding/ciudadano" +
                "?entrada=ciudadano&idLogica=accesoDirecto&idExpediente=800210_SolicitudGeneral&idEntidad=400000"
        const val ALMERIA_CLAVE_ID = "firmar"
        const val CORDOBA_PORTAL_ID = "diputacion-cordoba-portal"
        const val CORDOBA_CLAVE_URL =
            "https://sede.dipucordoba.es/diputacion/tramites/procedimiento/8876/" +
                "solicitud-generica"
        const val CORDOBA_CLAVE_HREF = ""
        const val PALENCIA_PORTAL_ID = "diputacion-palencia-sede"
        const val PALENCIA_CLAVE_FORM_ACTION =
            "https://sede.diputaciondepalencia.es/SPProxy2/IndexPage"
        const val PALENCIA_CLAVE_URL = "https://sede.diputaciondepalencia.es/opensiac/certlogin/enter.action"
        const val PALENCIA_CLAVE_IMAGE_SRC = "/opensiac/images/clave_w_32.jpg"
        const val PALENCIA_CLAVE_ONCLICK = "document.formClave.submit()"
        const val EDUCATION_PORTAL_ID = "educacion-convocatoria-46"
        const val EDUCATION_CLAVE_FORM_ACTION =
            "https://sede.educacion.gob.es/sede/login/loginClaveEduToken.jjsp"
        const val EDUCATION_CLAVE_URL =
            "https://sede.educacion.gob.es/sede/login/loginConv.jjsp" +
                "?iA=no&idConvocatoria=46"
        const val EDUCATION_CLAVE_ID = "login_texto_entrar"
        const val PONTEVEDRA_PORTAL_ID = "diputacion-pontevedra-sede"
        const val PONTEVEDRA_ENTRY_URL = "https://sede.depo.gal/web/public/catalog-detail/50709505"
        const val PONTEVEDRA_LOGIN_HREF =
            "/web/private/registry/external-registryinput-new.xhtml" +
                "?idbduacproceduretype=50709505&ispresential=0"
        const val PONTEVEDRA_LOGIN_URL = "https://sede.depo.gal" + PONTEVEDRA_LOGIN_HREF
        const val PONTEVEDRA_FORM_ACTION = "https://sede.depo.gal/web/public/login/login.xhtml"
        // shortcut: generated JSF id may change, revalidate the login DOM when Pontevedra changes the control.
        const val PONTEVEDRA_CLAVE_ONCLICK =
            "mojarra.jsfcljs(document.getElementById('formclave')," +
                "{'formclave:j_idt231':'formclave:j_idt231'},'');return false"
        const val EXTREMADURA_PORTAL_ID = "extremadura-tramites"
        const val EXTREMADURA_LOGIN_URL =
            "https://tramites.juntaex.es/sta/CarpetaPrivate/Login?APP_CODE=STA&PAGE_CODE=HOME"
        const val EXTREMADURA_CLAVE_HREF = "/sta/CarpetaPrivate/Clave2Request?APP_CODE=STA&PAGE_CODE=HOME"
        const val EXTREMADURA_ANTERIOR_PORTAL_ID = "extremadura-sede-anterior"
        const val EXTREMADURA_ANTERIOR_CLAVE_0_URL =
            "https://tramites.juntaex.es/sta/CarpetaPublic/?APP_CODE=STA&PAGE_CODE=PTS2_HOME"
        const val EXTREMADURA_ANTERIOR_CLAVE_0_HREF =
            "/sta/CarpetaPrivate/Login?APP_CODE=STA&PAGE_CODE=ANOTACIONES"
        const val EXTREMADURA_ANTERIOR_CLAVE_1_URL =
            "https://tramites.juntaex.es/sta/CarpetaPrivate/Login" +
                "?APP_CODE=STA&PAGE_CODE=ANOTACIONES"
        const val EXTREMADURA_ANTERIOR_CLAVE_1_HREF =
            "/sta/CarpetaPrivate/Clave2Request" +
                "?APP_CODE=STA&PAGE_CODE=ANOTACIONES"
        const val FUERTEVENTURA_PORTAL_ID = "fuerteventura-sede-electronica"
        const val FUERTEVENTURA_INSTITUCIONAL_PORTAL_ID = "fuerteventura-portal-institucional"
        const val FUERTEVENTURA_ENTRY_URL = "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=comenzar&tipoReg=1"
        const val JUSTICIA_PORTAL_ID = "justicia-sede-judicial"
        const val MADRID_SEDE_PORTAL_ID = "madrid-sede"
        const val MADRID_SEDE_APPLICATION_URL =
            "https://sede.madrid.es/portal/site/tramites/" +
                "menuitem.62876cb64654a55e2dbd7003a8a409a0/" +
                    "?vgnextchannel=23a99c5ffb020310VgnVCM100000171f5a0aRCRD" +
                    "&vgnextoid=ac464e85763fd310VgnVCM1000000b205a0aRCRD"
        const val MADRID_SEDE_APPLICATION_HREF = "https://servcla.madrid.es/TAZUL_FTWEBINTER/"
        const val MADRID_SEDE_CERT_URL = "https://cas.madrid.es/authenticationendpoint/login.do"
        const val MADRID_SEDE_CERT_HREF = "#"
        const val MJUSTICIA_PORTAL_ID = "mjusticia-sede"
        const val MJUSTICIA_CLAVE_FORM_ACTION =
            "https://sede2.mjusticia.gob.es/clave/iraclave"
        const val MJUSTICIA_CLAVE_URL = "https://sede2.mjusticia.gob.es/login/index/idp/75"
        const val MJUSTICIA_CLAVE_ID = "submitBtnClave"
        const val CADIZ_PORTAL_ID = "diputacion-cadiz-portal"
        const val CACERES_PORTAL_ID = "diputacion-caceres-portal"
        const val CACERES_START_URL = "https://sede.dip-caceres.es/carpetaCiudadano/fichaprocedimiento.do?idproc=341"
        const val CACERES_ACCESS_LABEL = "Acceder"
        const val CACERES_ACCESS_HREF = "./LoginLDAPFrontAction.do?red=MzQxI2V4cGdlbiNJTlNUQU5DSUEgR0VORVJBTCBOT1JNQUxJWkFEQQ=="
        const val CACERES_LOGIN_URL = "https://sede.dip-caceres.es/carpetaCiudadano/" +
            "LoginLDAPFrontAction.do?red=MzQxI2V4cGdlbiNJTlNUQU5DSUEgR0VORVJBTCBOT1JNQUxJWkFEQQ=="
        const val CACERES_CLAVE_HREF = "LoginClave2Front.do"
        const val CSN_PORTAL_ID = "age-consejo-de-seguridad-nuclear-csn"
        const val CSN_HOME_URL = "https://sede.csn.gob.es/Sede20/home.html"
        const val CSN_CERT_LABEL = "ACCESO CON CERTIFICADO DIGITAL O DNI-E"
        // The site emits the empty port verbatim; the anchor href is matched as written.
        const val CSN_CERT_HREF = "https://sede.csn.gob.es:/Sede20/clave/peticioncert"
        val GESTIONA_CLAVE_ORIGINS = mapOf(
            "diputacion-avila-portal" to "https://diputacionavila.sedelectronica.es",
            "diputacion-alicante-portal" to "https://diputacionalicante.sedelectronica.es",
            "diputacion-castellon-portal" to "https://dipcas.sedelectronica.es",
            "lanzarote-portal-institucional" to "https://lanzaroteylagraciosa.sedelectronica.es",
            "diputacion-teruel-sede" to "https://dpteruel.sedelectronica.es",
            "diputacion-guadalajara-sede" to "https://dguadalajara.sedelectronica.es",
            "age-consejo-de-transparencia-y-buen-gobierno-ctbg" to "https://sede.consejodetransparencia.gob.es",
            "diputacion-segovia-sede" to "https://sede.dipsegovia.es",
            "el-hierro-portal-institucional" to "https://elhierro.sedelectronica.es",
            "la-gomera-portal-institucional" to "https://lagomera.sedelectronica.es",
        )
        val GESTIONA_IDENTIFICATE_ORIGINS = mapOf(
            "diputacion-zamora-sede" to "https://diputaciondezamora.sedelectronica.es",
            "diputacion-zaragoza-sede" to "https://dpz.sedelectronica.es",
            "el-hierro-sede-electronica" to "https://elhierro.sedelectronica.es",
            "la-gomera-sede-electronica" to "https://lagomera.sedelectronica.es",
            "lanzarote-sede-electronica" to "https://lanzaroteylagraciosa.sedelectronica.es",
        )
        const val ISFAS_PORTAL_ID = "age-instituto-social-de-las-fuerzas-armadas-isfas"
        const val ISFAS_HOME_URL = "https://sede.isfas.gob.es/sede-web/"
        const val ISFAS_WELCOME_URL = "https://sede.isfas.gob.es/sede-web/welcome"
        const val ISFAS_IDENTIFY_URL = "https://sede.isfas.gob.es/sede-web/pages/autenticacion/identificarse.xhtml"
        // shortcut: j_idt85 is a generated JSF id; re-probe if the exact Cl@ve control changes.
        const val ISFAS_CLAVE_ONCLICK = "mojarra.jsfcljs(document.getElementById('formWelcome')," +
            "{'j_idt85':'j_idt85'},'');return false"
        const val CAIB_SEU_PORTAL_ID = "caib-seu-electronica"
        const val CAIB_REGISTRE_PORTAL_ID = "caib-registre-electronic"
        const val REG_AGE_PROFILE_ID = "reg-age-redsara"
        const val REC_HOME_URL = "https://reg.redsara.es/es/"
        const val REC_LOGIN_LABEL = "Iniciar sesión"
        const val TENERIFE_SEDE_PORTAL_ID = "tenerife-sede-electronica"
        const val TENERIFE_INSTITUCIONAL_PORTAL_ID = "tenerife-portal-institucional"
        const val TENERIFE_HOME_URL = "https://sede.tenerife.es/es"
        const val TENERIFE_LOGIN_LABEL = "Acceder"
        const val TENERIFE_LOGIN_HREF = "/es/personal/vista-general"
        const val TENERIFE_ACCESS_URL = "https://sede.tenerife.es/es/acceso?returnBack=personal%2Fvista-general"
        const val TENERIFE_CLAVE_BUTTON_LABEL = "Identificarme"
        const val AIREF_PORTAL_ID = "age-autoridad-independiente-de-responsabilidad-fiscal-airef"
        const val JCCM_PORTAL_ID = "castilla-la-mancha-sede"
        const val JCCM_ENTRY_URL =
            "https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ"
        const val JCCM_CLAVE_BUTTON_ID = "boton_presentar_solicitud_clave"
        const val JCCM_CLAVE_BUTTON_VALUE = "Presentar Solicitud"
        const val JCCM_CLAVE_BUTTON_ONCLICK = "cargar_documentos_clave(); return false;"
        const val DGT_PORTAL_ID = "dgt-sede"
        const val DGT_VERIFY_URL = "https://sede.dgt.gob.es/es/otros-tramites/" +
            "verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/"
        const val DGT_VERIFY_BUTTON_ID = "botonFirmar"
        const val DGT_VERIFY_BUTTON_LABEL = "Verificar mi equipo"
        const val DGT_VERIFY_BUTTON_ONCLICK = "cargarAF()"
        const val SIRAW_PORTAL_ID = "aragon-siraw"
        const val SIRAW_LOGIN_URL = "https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw"
        const val SIRAW_CERT_BUTTON_ID = "certificado"
        const val SIRAW_CERT_BUTTON_VALUE = "Certificado"
        const val SIRAW_CERT_BUTTON_ONCLICK = "waitMask(true, 'Procesando acceso, espere por favor, gracias...');"
        const val TRANSPORTES_PORTAL_ID = "age-ministerio-de-transportes-y-movilidad-sostenible"
        const val TRANSPORTES_AUTH_URL = "https://sede.transportes.gob.es/MFOM.genericprocedure.web/Autenticacion.aspx"
        const val TRANSPORTES_SIGN_BUTTON_ID = "ctl00_ContentFormulario_controlAutenticacion_Sign_signButton"
        const val TRANSPORTES_SIGN_BUTTON_LABEL = "Continuar"
        const val TRANSPORTES_SIGN_BUTTON_ONCLICK = "Sign_firmar();return false;"
        const val LUGO_PORTAL_ID = "diputacion-lugo-sede"
        const val LUGO_AUTH_URL = "https://sede.deputacionlugo.org/opencms/system/modules/gsede/elements/" +
            "secciones/autenticacion/autenticacion.jsp"
        const val LUGO_AUTH_BUTTON_ID = "autButton"
        const val LUGO_CERT_BUTTON_LABEL = "Acceder"
        const val LUGO_CERT_BUTTON_ONCLICK = "waiting(); chargeClient();"
        const val SORIA_PORTAL_ID = "diputacion-soria-sede"
        const val SORIA_SOURCE_URL = "https://portaltramitador.dipsoria.es/web/inicioWebc.do?opcion=cargar&" +
            "redirige=L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%3D&entidad=SORIA&idioma=1"
        const val SORIA_CERT_BUTTON_ID = "b_certificado"
        const val SORIA_CERT_BUTTON_LABEL = "ACCEDER"
        const val SORIA_CERT_BUTTON_ONCLICK = "pulsarCertificado();"
        const val AEMET_PORTAL_ID = "age-agencia-estatal-de-meteorologia-aemet"
        const val AEMET_PROFILE_ID = "aemet-public-solicitud-navigation"
        const val AEMET_BASE_URL = "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones"
        const val AEMET_HOME_URL = "$AEMET_BASE_URL/home"
        const val AEMET_LOGIN_MENU_URL = "$AEMET_BASE_URL/selectLogin"
        const val AEMET_LOGIN_URL = "$AEMET_BASE_URL/sso"
        const val AEMET_LOGIN_ACTION_URL = "$AEMET_BASE_URL/ssoLogin"
        const val CANARIAS_PORTAL_ID = "canarias-sede"
        const val CANARIAS_MOBILE_BASE = "https://sede.gobiernodecanarias.org/sede/movil"
        const val CANARIAS_HOME_URL = "$CANARIAS_MOBILE_BASE/la_sede"
        const val CANARIAS_PERSONAL_DATA_URL = "$CANARIAS_MOBILE_BASE/menu_portada_movil/area_personal/datos_personales"
        const val CANARIAS_LOGIN_MENU_URL = "$CANARIAS_MOBILE_BASE/identificacionmenu"
        const val CANARIAS_LOGIN_URL = "$CANARIAS_MOBILE_BASE/identificacion"
        const val CANARIAS_LOGIN_ACTION_URL = "https://sede.gobiernodecanarias.org/-/action/" +
            "c002ea10-8036-11df-9987-52c39c0cc1c2/e8e67340-afe4-11e6-9a06-1eb47e039c2b/es_ES/" +
            "a15be610-b249-11e6-9a06-11991fa5bb48/accionProcesaFormularioLogin"
        const val AESA_PORTAL_ID = "age-agencia-estatal-de-seguridad-aerea-aesa"
        const val AESA_HOME_URL = "https://sede.seguridadaerea.gob.es/sede-aesa/" +
            "catalogo-de-procedimientos/solicitud-general"
        const val AESA_LOGIN_URL =
            "https://sede.seguridadaerea.gob.es/oficina/tramites/altaSolicitud.do?id=4&codArea=SOLGEN"
        const val DGSFP_PORTAL_ID = "age-direccion-general-de-seguros-y-fondos-de-pensiones"
        const val DGSFP_HOME_URL = "https://www.sededgsfp.gob.es/es/Paginas/inicio.aspx"
        const val DGSFP_LOGIN_URL = "https://www.sededgsfp.gob.es/es/paginas/login.aspx"
        const val LA_RIOJA_PORTAL_ID = "la-rioja-oficina-electronica"
        const val LA_RIOJA_CAS_LOGIN_URL = "https://ias1.larioja.org/casLR/login"
        const val LA_RIOJA_CERT_BUTTON_ID = "boton_certificado"
        const val LA_RIOJA_CERT_BUTTON_LABEL = "Conectar"
        const val LA_RIOJA_CERT_BUTTON_ONCLICK = "loginClientCertSSL('https://ias1.larioja.org/clientcertSSL/login')"
        const val MENORCA_PORTAL_INSTITUCIONAL_ID = "menorca-portal-institucional"
        const val MENORCA_SEDE_ID = "menorca-sede-electronica"
        const val MENORCA_START_URL =
            "https://www.carpetaciutadana.org/cime/gesserveis/Gestion.aspx?IDGESTION=990100262"
        const val MENORCA_PERSONAL_DATA_LABEL = "Les meves dades"
        const val MENORCA_PERSONAL_DATA_HREF = "/cime/InfoPersonal.aspx"
        const val MENORCA_LOGIN_URL = "https://www.carpetaciutadana.org/cime/Login/Login.aspx"
        const val MENORCA_CERT_SUBMIT_ID = "ctl00_Content1_Button1"
        const val MENORCA_CERT_SUBMIT_LABEL = "Certificat electrònic"
        const val CORUNA_PORTAL_ID = "diputacion-a-coruna-portal"
        const val CORUNA_TRAMITADOR_URL =
            "https://sede.dacoruna.gal/tramitador/entrada?idLogica=accesoDirecto&entrada=ciudadano" +
                "&idEntidad=diputacion&idExpediente=X004&fkIdioma=GL"
        private val CORUNA_LINK_HOPS = listOf(
            RecipeLinkHop("https://www.dacoruna.gal/portada", "Sede electrónica", "https://sede.dacoruna.gal/gl/"),
            // sede.dacoruna.gal/gl/ redirects to /sxc/gl/.
            RecipeLinkHop(
                "https://sede.dacoruna.gal/sxc/gl/",
                "SOLICITUDE XERAL",
                "/sxc/gl/procedimientosytramites/index.html#solicitud_general",
            ),
            RecipeLinkHop(
                "https://sede.dacoruna.gal/sxc/gl/procedimientosytramites/index.html#solicitud_general",
                "Solicitude Xeral",
                "/sxc/gl/procedimientosytramites/tramites/SolicitudGeneral_N",
            ),
            RecipeLinkHop(
                "https://sede.dacoruna.gal/sxc/gl/procedimientosytramites/tramites/SolicitudGeneral_N",
                "INICIAR TRÁMITE (On Line)",
                CORUNA_TRAMITADOR_URL,
            ),
        )
        const val CORUNA_CLAVE_BUTTON_ID = "acceso"
        const val CORUNA_CLAVE_BUTTON_LABEL = "Entrar con Cl@ve"
        const val CLAVE_SERVICE_PROVIDER_URL =
            "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val CLAVE_SERVICE_REDIRECT_URL =
            "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val CLAVE_AFIRMA_ONCLICK =
            "JAVASCRIPT:selectedIdP('AFIRMA');idpRedirect.submit();"
        const val CLAVE_AFIRMA_IMAGE_SRC = "ImageRetrieve?id=IDP_AFIRMA"
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
        const val UNIZAR_AUTH_ELEMENT_ID = "acEntrar"
        const val UNIZAR_AUTH_LABEL =
            "Pulse para ejecutar Autofirma e identificarse con certificado."
        const val UNIZAR_AUTH_IMAGE_ALT = "certificado login"
        const val UNIZAR_AUTH_ONCLICK = "lanza();"
        const val UNIZAR_AUTH_HANDLER = "lanza"
        val REAL_CERT_URI: Uri = Uri.parse("content://dev.junta.firmamobile.real-e2e/identity.p12")
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val JCCM_ALTCHA_TIMEOUT_MILLIS = 150_000L
        const val CLIENT_AUTH_DIALOG_TAG = "client-auth-confirmation"
        const val CLIENT_AUTH_CONTINUE_TAG = "client-auth-confirmation-continue"
        const val CLIENT_AUTH_CANCEL_TAG = "client-auth-confirmation-cancel"
        const val INTERACTIVE_CLIENT_AUTH_DIALOG_TAG = "interactive-client-auth"
        const val INTERACTIVE_CLIENT_AUTH_CANCEL_TAG = "interactive-client-auth-cancel"
        const val BADAJOZ_PAGE_READY_TIMEOUT_MILLIS = 60_000L
        const val PORTAL_TIMEOUT_MILLIS = 75_000L
        const val SIGNING_OWNER_TIMEOUT_MILLIS = 10_000L
        const val MAX_FAILURE_UNWRAP_DEPTH = 4
        const val SIGNING_TIMEOUT_MILLIS = 90_000L
        const val POST_SIGN_TIMEOUT_MILLIS = 30_000L
        const val RECIPE_TERMINAL_GRACE_MILLIS = 5_000L
        const val POLL_MILLIS = 300L
        const val RECIPE_POLL_MILLIS = 200L
        const val MAX_PASSWORD_BYTES = 8_192
        const val MAX_CLIENT_AUTH_CONFIRMATIONS = 8
        val PORTAL_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,95}")
        val SAFE_ERROR_TOKEN = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
        const val SIGN_CONFIRM_ERROR_PREFIX = "REAL_E2E_SIGN_CONFIRM_"
        val JS_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
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
            "aemet-public-solicitud-navigation",
            "transportes-qys-cert-login",
            "mites-certificate-login",
            "diputacion-lleida-sede",
            "diputacion-badajoz-portal",
        )
    }
}

internal fun boeCertificateAnchorScript(): String = """
    (() => {
      if (document.location.href !== 'https://extranet.boe.es/anuncios/' ||
          document.readyState !== 'complete') return 0;
      const href = 'https://extranet.boe.es/id/check-cert.php?referer=https://extranet.boe.es/anuncios/';
      const anchors = Array.from(document.querySelectorAll('a')).filter(a => a.getAttribute('href') === href);
      if (anchors.length === 0) return 0;
      if (anchors.length !== 1) return 2;
      const anchor = anchors[0];
      if ((anchor.textContent || '').trim().replace(/\s+/g, ' ') !== '' ||
          anchor.getAttribute('onclick') !== null ||
          !['', '_self'].includes((anchor.getAttribute('target') || '').toLowerCase())) return 2;
      const images = anchor.querySelectorAll('img');
      if (images.length !== 1 || images[0].getAttribute('name') !== 'certificado' ||
          images[0].getAttribute('alt') !== 'Identificación con certificado electrónico' ||
          images[0].getAttribute('src') !== '/imagenes/certificado_txt.png') return 2;
      anchor.click();
      return 1;
    })()
""".trimIndent()

internal fun sedipualbaServerCertificateOptionScript(expectedLoginUrl: String, optionLabel: String): String = """
    (() => {
      if (document.location.href !== ${JSONObject.quote(expectedLoginUrl)} ||
          document.readyState !== 'complete') return 0;
      const frames = document.querySelectorAll('[id="ventanaModal"]');
      if (frames.length === 0) return 0;
      if (frames.length !== 1 || frames[0].tagName !== 'IFRAME') return 2;
      let doc;
      try { doc = frames[0].contentDocument; } catch (e) { return 2; }
      if (!doc || !doc.location || doc.readyState !== 'complete') return 0;
      if (doc.location.origin !== location.origin ||
          doc.location.pathname !== '/segex/identificacion_opciones.aspx') return 0;
      const options = doc.querySelectorAll('[id="optSsl"]');
      if (options.length === 0) return 0;
      if (options.length !== 1 || options[0].tagName !== 'TBODY') return 2;
      const option = options[0];
      if (!['', '_self'].includes((option.getAttribute('target') || '').toLowerCase())) return 2;
      const images = option.querySelectorAll('img');
      const titles = option.querySelectorAll('.titulo-opcion span');
      if (images.length !== 1 || titles.length !== 1) return 2;
      const title = (titles[0].textContent || '').trim().replace(/\s+/g, ' ');
      const label = ${JSONObject.quote(optionLabel)};
      if (images[0].getAttribute('alt') !== label || title !== label) return 2;
      option.click();
      return 1;
    })()
""".trimIndent()
