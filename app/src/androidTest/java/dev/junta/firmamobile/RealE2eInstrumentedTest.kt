package dev.junta.firmamobile

import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
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

        require(explicitlyEnabled) { "REAL_E2E_NOT_ENABLED" }
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
        try {
            require(certificateFile.isFile) { "REAL_E2E_CERTIFICATE_MISSING" }
            require(passwordFile.isFile) { "REAL_E2E_PASSWORD_MISSING" }

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
                    enterCatalog(result)
                    openPortal(portalId, result)
                    waitForWebView(scenario, result)
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
            result.infrastructureError = safeInfrastructureCode(throwable)
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
        val output = shell(
            listOf(
                "am", "broadcast", "--user", "0",
                "-a", "dev.junta.firmamobile.action.CATALOG_SMOKE",
                "-p", PACKAGE_NAME,
                "--es", "runId", "real-e2e-${portalId.takeLast(20)}",
                "--es", "portalId", portalId,
                "--es", "operation", "OPEN",
            ).joinToString(" "),
        )
        if (!output.contains("OPEN_REQUESTED")) error("Protected catalog OPEN was not accepted")
        result.openRequested = true
        result.level = maxOf(result.level, 1)
    }

    private fun waitForWebView(
        scenario: ActivityScenario<MainActivity>,
        result: ProbeResult,
    ) {
        waitUntil(UI_TIMEOUT_MILLIS) {
            var webView: WebView? = null
            scenario.onActivity { activity -> webView = findWebView(activity.window.decorView) }
            webView?.let { current ->
                result.currentHost = current.url?.let(Uri::parse)?.host
                true
            } == true
        }
        result.webViewActive = true
        result.level = maxOf(result.level, 1)
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

        while (SystemClock.elapsedRealtime() < deadline) {
            val records = diagnosticRecords()
            updateRecordObservations(records, result)
            updateCurrentWebView(scenario, result)

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

            if (Capability.SIGN in profileCapabilities && !signingHandled &&
                rule.onAllNodesWithText("Solicitud de firma").fetchSemanticsNodes().isNotEmpty()
            ) {
                result.signingConfirmationObserved = true
                result.level = maxOf(result.level, 3)
                if (deepEnabled && profileId in SAFE_AUTH_SIGN_PROFILES) {
                    rule.onNodeWithText("Firmar").performClick()
                    result.signingConfirmed = true
                    waitForSigningTerminalState(scenario, result)
                } else {
                    rule.onNodeWithText("Cancelar").performClick()
                    result.signingCancelledAtBoundary = true
                }
                signingHandled = true
            }

            if (isObservationComplete(profileCapabilities, result)) {
                classify(profileCapabilities, result)
                return
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            SystemClock.sleep(POLL_MILLIS)
        }

        classify(profileCapabilities, result)
    }

    private fun waitForSigningTerminalState(
        scenario: ActivityScenario<MainActivity>,
        result: ProbeResult,
    ) {
        val deadline = SystemClock.elapsedRealtime() + SIGNING_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var state: SigningUiState? = null
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("signingCoordinator")
                    .apply { isAccessible = true }
                state = (field.get(activity) as SigningCoordinator).state.value
            }
            when (val current = state) {
                is SigningUiState.Completed -> {
                    result.signatureCompleted = true
                    result.level = maxOf(result.level, 4)
                    return
                }
                is SigningUiState.Failed -> {
                    result.signingFailureCode = current.code.name
                    return
                }
                else -> Unit
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        result.signingFailureCode = "TIMEOUT"
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

        result.portalAuthSuccess = result.portalAuthSuccess || records.any {
            it.contains("stage=vea-auth-success") ||
                it.contains("stage=auth-success") ||
                it.contains("stage=authentication-success")
        }
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

    private fun updateCurrentWebView(
        scenario: ActivityScenario<MainActivity>,
        result: ProbeResult,
    ) {
        scenario.onActivity { activity ->
            findWebView(activity.window.decorView)?.url?.let(Uri::parse)?.host?.let { host ->
                result.currentHost = host
            }
        }
    }

    private fun isObservationComplete(
        capabilities: Set<Capability>,
        result: ProbeResult,
    ): Boolean {
        if (Capability.CLIENT_TLS_AUTH in capabilities && !result.clientCertProceeded) return false
        if (Capability.SELECT_CERTIFICATE in capabilities && !result.publicCertificateShared) return false
        if (Capability.SIGN in capabilities &&
            !result.signingConfirmationObserved && !result.signatureCompleted
        ) return false
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
            result.classification = when {
                result.portalAuthSuccess -> ProbeClassification.PASS_PORTAL_AUTH
                result.signatureCompleted -> ProbeClassification.PASS_CRYPTO_CALLBACK
                result.clientCertProceeded -> ProbeClassification.PASS_CLIENT_TLS
                else -> ProbeClassification.PASS_MECHANISM_BOUNDARY
            }
        } else {
            result.classification = ProbeClassification.RECIPE_REQUIRED
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
        val cookies = CountDownLatch(1)
        CookieManager.getInstance().removeAllCookies { cookies.countDown() }
        assertTrue("Cookie reset timed out", cookies.await(5, TimeUnit.SECONDS))
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        val clientCert = CountDownLatch(1)
        WebView.clearClientCertPreferences { clientCert.countDown() }
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

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun waitUntil(timeoutMillis: Long, predicate: () -> Boolean) {
        rule.waitUntil(timeoutMillis = timeoutMillis, condition = predicate)
    }

    private fun safeInfrastructureCode(throwable: Throwable): String = when (throwable.message) {
        "REAL_E2E_CERTIFICATE_MISSING" -> "CERTIFICATE_MISSING"
        "REAL_E2E_PASSWORD_MISSING" -> "PASSWORD_MISSING"
        else -> throwable.javaClass.simpleName.takeIf { SAFE_ERROR_TOKEN.matches(it) }
            ?: "UNKNOWN_ERROR"
    }

    private fun writeResult(result: ProbeResult) {
        val directory = File(application().filesDir, REAL_E2E_DIR).apply { mkdirs() }
        val output = File(directory, RESULT_FILE)
        val json = JSONObject()
            .put("schemaVersion", 1)
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

    private enum class ProbeClassification {
        PENDING,
        PASS_BROWSE,
        PASS_MECHANISM_BOUNDARY,
        PASS_CLIENT_TLS,
        PASS_CRYPTO_CALLBACK,
        PASS_PORTAL_AUTH,
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
        const val REAL_E2E_DIR = "real-e2e"
        const val CERTIFICATE_FILE = "identity.p12"
        const val PASSWORD_FILE = "password"
        const val RESULT_FILE = "result.json"
        const val PACKAGE_NAME = "dev.junta.firmamobile"
        val REAL_CERT_URI: Uri = Uri.parse("content://dev.junta.firmamobile.real-e2e/identity.p12")
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val PORTAL_TIMEOUT_MILLIS = 75_000L
        const val SIGNING_TIMEOUT_MILLIS = 90_000L
        const val POLL_MILLIS = 300L
        const val MAX_PASSWORD_BYTES = 8_192
        const val MAX_CLIENT_AUTH_CONFIRMATIONS = 8
        val PORTAL_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,95}")
        val SAFE_ERROR_TOKEN = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
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
