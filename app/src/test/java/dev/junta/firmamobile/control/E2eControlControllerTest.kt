package dev.junta.firmamobile.control

import android.net.Uri
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.smoke.CatalogSmokeOutcome
import dev.junta.firmamobile.smoke.CatalogSmokeResultCode
import dev.junta.firmamobile.smoke.CatalogSmokeRuntimeSnapshot
import dev.junta.firmamobile.ui.CertificateUiState
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class E2eControlControllerTest {
    private val reference = StoredCertificateReference(
        uri = Uri.parse("content://com.android.externalstorage.documents/document/fixture"),
        displayName = "fixture.p12",
        mimeType = "application/pkcs12",
        size = 128L,
        summary = null,
    )
    private val summary = CertificateSummary(
        ownerName = "Synthetic Owner",
        issuerName = "Synthetic Issuer",
        validFrom = Instant.parse("2026-01-01T00:00:00Z"),
        validUntil = Instant.parse("2027-01-01T00:00:00Z"),
    )

    @Test
    fun `certificate select accepts only exact opaque staging handle`() = runTest {
        val state = MutableStateFlow<CertificateUiState>(CertificateUiState.NoCertificate())
        var selectedUri: Uri? = null
        val controller = controller(
            state = state,
            select = { uri ->
                selectedUri = uri
                state.value = CertificateUiState.Locked(reference, null, null)
            },
        )
        val handle = "0123456789abcdef0123456789abcdef"
        val valid = E2eControlRequest(
            runId = "select-run",
            command = "CERT_SELECT",
            certificateHandle = handle,
        )
        val outcome = controller.execute(valid)

        assertEquals("CERTIFICATE_SELECTED", outcome.result)
        assertTrue(outcome.success)
        assertEquals("dev.junta.firmamobile.e2e.certificate", selectedUri?.authority)
        assertEquals(listOf(handle), selectedUri?.pathSegments)
        assertFalse(outcome.toJson().contains(handle))

        state.value = CertificateUiState.NoCertificate()
        assertEquals(
            "INVALID_REQUEST",
            controller.execute(valid.copy(certificateHandle = "../outside")).result,
        )
        assertEquals(
            "INVALID_REQUEST",
            controller.execute(valid.copy(certificateHandle = "a".repeat(31))).result,
        )
    }

    @Test
    fun `unlock consumes opaque handle and never exports secret material`() = runTest {
        val state = MutableStateFlow<CertificateUiState>(CertificateUiState.Locked(reference, null, null))
        var consumedHandle: String? = null
        val controller = controller(
            state = state,
            consumeSecret = { handle ->
                consumedHandle = handle
                E2eSecretReadResult.Success(OwnedE2eSecret("synthetic-password".toCharArray()))
            },
            unlock = { password ->
                assertEquals("synthetic-password", password.concatToString())
                password.fill('\u0000')
                state.value = CertificateUiState.Unlocked(reference, summary)
            },
        )
        val handle = "0123456789abcdef0123456789abcdef"
        val outcome = controller.execute(
            E2eControlRequest("unlock-run", "CERT_UNLOCK", secretHandle = handle),
        )

        assertEquals(handle, consumedHandle)
        assertEquals("CERTIFICATE_UNLOCKED", outcome.result)
        assertTrue(outcome.success)
        val json = outcome.toJson()
        assertFalse(json.contains(handle))
        assertFalse(json.contains("synthetic-password"))
        assertFalse(json.contains("password", ignoreCase = true))
        assertFalse(json.contains("fixture.p12"))
    }

    @Test
    fun `sign confirm is bound to exact active portal run`() = runTest {
        val state = MutableStateFlow<CertificateUiState>(CertificateUiState.Unlocked(reference, summary))
        var confirmed = 0
        var activeRun = true
        val awaiting = SigningUiState.AwaitingConfirmation(
            requestId = UUID.randomUUID(),
            siteHost = "example.test",
            profileName = "Fixture",
            supportLevel = "IMPLEMENTED_NOT_E2E",
            safeDescription = "Synthetic signing fixture",
            format = "XADES",
            algorithm = "SHA512withRSA",
            certificateOwner = "Synthetic Owner",
            requiresLegacySha1Warning = false,
        )
        val controller = controller(
            state = state,
            portal = { request ->
                assertEquals("INSPECT", request.operation)
                portalOutcome(
                    request.runId,
                    if (activeRun) CatalogSmokeResultCode.WEBVIEW_ACTIVE else CatalogSmokeResultCode.RUN_NOT_ACTIVE,
                    runtime = if (activeRun) {
                        activeRuntime(request.runId, signingConfirmationRequired = true)
                    } else {
                        null
                    },
                )
            },
            signing = { awaiting },
            confirm = { confirmed++; true },
        )
        val request = E2eControlRequest(
            runId = "sign-run",
            command = "SIGN_CONFIRM",
            portalId = "fixture-portal",
        )

        assertEquals("SIGNING_CONFIRM_REQUESTED", controller.execute(request).result)
        assertEquals(1, confirmed)

        activeRun = false
        assertEquals("SIGNING_CONTEXT_NOT_ACTIVE", controller.execute(request).result)
        assertEquals(1, confirmed)

        assertEquals(
            "INVALID_REQUEST",
            controller.execute(request.copy(portalId = null)).result,
        )
    }

    @Test
    fun `client auth confirmation requires bound runtime evidence and current browser callback`() = runTest {
        val state = MutableStateFlow<CertificateUiState>(CertificateUiState.Unlocked(reference, summary))
        var confirmed = 0
        var evidence = true
        val controller = controller(
            state = state,
            portal = { request ->
                portalOutcome(
                    request.runId,
                    CatalogSmokeResultCode.WEBVIEW_ACTIVE,
                    runtime = activeRuntime(
                        request.runId,
                        clientAuthConfirmationRequired = evidence,
                    ),
                )
            },
            confirmClientAuth = { profileId ->
                assertEquals(ProfileId("fixture-profile"), profileId)
                confirmed++
                true
            },
        )
        val request = E2eControlRequest(
            runId = "client-auth-run",
            command = "CLIENT_AUTH_CONFIRM",
            profileId = "fixture-profile",
        )

        assertEquals("CLIENT_AUTH_CONFIRM_REQUESTED", controller.execute(request).result)
        assertEquals(1, confirmed)

        evidence = false
        assertEquals("BROWSER_CONFIRMATION_NOT_PENDING", controller.execute(request).result)
        assertEquals(1, confirmed)
    }

    @Test
    fun `portal certificate confirmation requires exact pending runtime evidence`() = runTest {
        val state = MutableStateFlow<CertificateUiState>(CertificateUiState.Unlocked(reference, summary))
        var confirmed = 0
        var evidence = true
        val controller = controller(
            state = state,
            portal = { request ->
                portalOutcome(
                    request.runId,
                    CatalogSmokeResultCode.WEBVIEW_ACTIVE,
                    runtime = activeRuntime(
                        request.runId,
                        certificateSelectionRequired = evidence,
                    ),
                )
            },
            confirmPortalCertificate = { profileId ->
                assertEquals(ProfileId("fixture-profile"), profileId)
                confirmed++
                true
            },
        )
        val request = E2eControlRequest(
            runId = "portal-cert-run",
            command = "PORTAL_CERT_CONFIRM",
            portalId = "fixture-portal",
        )

        assertEquals("PORTAL_CERTIFICATE_CONFIRM_REQUESTED", controller.execute(request).result)
        assertEquals(1, confirmed)

        evidence = false
        assertEquals("BROWSER_CONFIRMATION_NOT_PENDING", controller.execute(request).result)
        assertEquals(1, confirmed)
    }

    @Test
    fun `portal close delegates only bounded identifiers and reports closed`() = runTest {
        val state = MutableStateFlow<CertificateUiState>(CertificateUiState.Unlocked(reference, summary))
        val operations = mutableListOf<String?>()
        val controller = controller(
            state = state,
            portal = { request ->
                operations += request.operation
                portalOutcome(request.runId, CatalogSmokeResultCode.PORTAL_CLOSED)
            },
        )
        val result = controller.execute(
            E2eControlRequest(
                runId = "close-run",
                command = "PORTAL_CLOSE",
                profileId = "fixture-profile",
            ),
        )
        assertEquals("PORTAL_CLOSED", result.result)
        assertTrue(result.success)
        assertEquals(listOf("CLOSE"), operations)
    }

    private fun controller(
        state: MutableStateFlow<CertificateUiState>,
        select: (Uri) -> Unit = {},
        unlock: (CharArray) -> Unit = { it.fill('\u0000') },
        consumeSecret: suspend (String) -> E2eSecretReadResult = { E2eSecretReadResult.Missing },
        portal: (dev.junta.firmamobile.smoke.CatalogSmokeRequest) -> CatalogSmokeOutcome = {
            portalOutcome(it.runId, CatalogSmokeResultCode.RUN_NOT_ACTIVE)
        },
        signing: () -> SigningUiState = { SigningUiState.Idle },
        confirmClientAuth: (ProfileId) -> Boolean = { false },
        cancelClientAuth: (ProfileId) -> Boolean = { false },
        confirmPortalCertificate: (ProfileId) -> Boolean = { false },
        cancelPortalCertificate: (ProfileId) -> Boolean = { false },
        confirm: () -> Boolean = { false },
        cancel: () -> Boolean = { false },
        dismiss: () -> Boolean = { false },
    ) = E2eControlController(
        certificateState = state,
        selectCertificate = select,
        unlockCertificate = unlock,
        lockCertificate = {
            state.value = CertificateUiState.Locked(reference, state.value.summaryOrNull(), null)
        },
        forgetCertificate = { state.value = CertificateUiState.NoCertificate() },
        consumeSecret = consumeSecret,
        executePortal = portal,
        signingState = signing,
        confirmClientAuth = confirmClientAuth,
        cancelClientAuth = cancelClientAuth,
        confirmPortalCertificate = confirmPortalCertificate,
        cancelPortalCertificate = cancelPortalCertificate,
        confirmCurrentSigning = confirm,
        cancelCurrentSigning = cancel,
        dismissCurrentSigning = dismiss,
    )

    private fun CertificateUiState.summaryOrNull() = when (this) {
        is CertificateUiState.Unlocked -> summary
        is CertificateUiState.Locked -> summary
        is CertificateUiState.Unlocking -> summary
        CertificateUiState.LoadingReference,
        is CertificateUiState.NoCertificate,
        -> null
    }

    private fun portalOutcome(
        runId: String?,
        result: CatalogSmokeResultCode,
        runtime: CatalogSmokeRuntimeSnapshot? = null,
    ) = CatalogSmokeOutcome(
        runId = runId,
        portalId = PortalId("fixture-portal"),
        profileId = ProfileId("fixture-profile"),
        adapterId = "fixture-adapter",
        entryUrl = "https://example.test/",
        supportStatus = "IMPLEMENTED_NOT_E2E",
        result = result,
        runtime = runtime,
    )

    private fun activeRuntime(
        runId: String?,
        clientAuthConfirmationRequired: Boolean = false,
        certificateSelectionRequired: Boolean = false,
        signingConfirmationRequired: Boolean = false,
        signingStartedObserved: Boolean = false,
        signingCompletedObserved: Boolean = false,
        signingFailedObserved: Boolean = false,
    ) = CatalogSmokeRuntimeSnapshot(
        runId = requireNotNull(runId),
        profileId = ProfileId("fixture-profile"),
        browserSessionBound = true,
        webViewActive = true,
        navigationEpoch = 1L,
        currentHost = "example.test",
        currentPathLength = 1,
        currentPathSha256_8 = "deadbeef",
        currentUrlAllowed = true,
        clientCertRequestObserved = false,
        clientCertAcceptedObserved = false,
        clientAuthConfirmationRequired = clientAuthConfirmationRequired,
        certificateSelectionRequired = certificateSelectionRequired,
        afirmaRequestObserved = false,
        autofirmaIntentObserved = false,
        signingConfirmationRequired = signingConfirmationRequired,
        signingStartedObserved = signingStartedObserved,
        signingCompletedObserved = signingCompletedObserved,
        signingFailedObserved = signingFailedObserved,
        portalCallbackObserved = false,
        renderProcessGone = false,
        failureCode = null,
        events = emptyList(),
    )
}
