package dev.junta.firmamobile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.browser.WebViewStateHolder
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.signing.CoroutineSigningExpiryScheduler
import dev.junta.firmamobile.signing.JcaLocalSignatureEngine
import dev.junta.firmamobile.signing.JuntaTriPhaseAdapter
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningCoordinator
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.BrowserScreen
import dev.junta.firmamobile.ui.CertificateUiState
import dev.junta.firmamobile.ui.CertificateViewModel
import dev.junta.firmamobile.ui.SensitiveWindowProtection
import dev.junta.firmamobile.ui.WindowSecureFlagPolicy
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var webViewStateHolder: WebViewStateHolder
    private var currentWebView: WebView? = null
    private var showBrowser by mutableStateOf(false)
    private lateinit var signingCoordinator: SigningCoordinator
    private val signingJobs = SigningJobRegistry()

    private val certificateViewModel: CertificateViewModel by viewModels {
        val app = application as JuntaFirmaApplication
        CertificateViewModel.Factory(app.certificateGateway, app.certificateSession)
    }

    private val certificatePicker = registerForActivityResult(
        OpenableDocumentContract,
    ) { uri ->
        uri?.let(certificateViewModel::onCertificateSelected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webViewStateHolder = WebViewStateHolder(savedInstanceState)
        val app = application as JuntaFirmaApplication
        signingCoordinator = SigningCoordinator(
            certificateSession = app.certificateSession,
            adapter = JuntaTriPhaseAdapter(),
            localSignatureEngine = JcaLocalSignatureEngine(),
            currentOrigin = {
                currentWebView?.url?.let { url ->
                    runCatching { JuntaOriginPolicy.originFor(Uri.parse(url)) }.getOrNull()
                }
            },
            expiryScheduler = CoroutineSigningExpiryScheduler(lifecycleScope),
        )
        enableEdgeToEdge()
        setContent {
            val certificateState = certificateViewModel.state.collectAsStateWithLifecycle()
            val signingState = signingCoordinator.state.collectAsStateWithLifecycle()
            val updateSecureWindow = remember(window) {
                { sensitive: Boolean ->
                    WindowSecureFlagPolicy.apply(window, sensitive)
                }
            }
            SensitiveWindowProtection(
                enabled = certificateState.value is CertificateUiState.Locked,
                updateSecure = updateSecureWindow,
            )
            LaunchedEffect(certificateState.value) {
                if (certificateState.value !is CertificateUiState.Unlocked) {
                    showBrowser = false
                }
            }
            JuntaFirmaTheme {
                val unlocked = certificateState.value as? CertificateUiState.Unlocked
                if (showBrowser && unlocked != null) {
                    val app = application as JuntaFirmaApplication
                    BrowserScreen(
                        certificateState = unlocked,
                        stateHolder = webViewStateHolder,
                        logger = app.sanitizedLogger,
                        signingState = signingState.value,
                        onMiniAppletRequest = ::prepareMiniAppletSigning,
                        onMiniAppletCancel = { requestId ->
                            cancelSigning(SigningCancelReason.JAVASCRIPT, requestId)
                        },
                        onConfirmSigning = ::confirmSigning,
                        onCancelSigning = ::cancelSigning,
                        onDismissSigningState = signingCoordinator::dismissTerminalState,
                        onExitBrowser = {
                            cancelSigning(SigningCancelReason.NAVIGATION)
                            showBrowser = false
                        },
                        onOpenExternal = { uri ->
                            cancelSigning(SigningCancelReason.NAVIGATION)
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (_: ActivityNotFoundException) {
                                // The validated URL stays closed if no browser can handle it.
                            }
                        },
                        onChangeCertificate = {
                            cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                            showBrowser = false
                            launchCertificatePicker()
                        },
                        onLockCertificate = {
                            cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                            showBrowser = false
                            certificateViewModel.lock()
                        },
                        onClearSession = {
                            cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                            showBrowser = false
                            certificateViewModel.lock()
                        },
                        onWebViewChanged = { currentWebView = it },
                    )
                } else {
                    AppRoot(
                        state = certificateState.value,
                        onSelectCertificate = ::launchCertificatePicker,
                        onUnlock = certificateViewModel::unlock,
                        onLock = certificateViewModel::lock,
                        onForget = certificateViewModel::forget,
                        onContinue = { showBrowser = true },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        cancelSigning(SigningCancelReason.BACKGROUND)
        currentWebView?.let { webViewStateHolder.save(it, outState) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        cancelSigning(SigningCancelReason.BACKGROUND)
        certificateViewModel.onAppBackgrounded()
        super.onStop()
    }

    override fun onLowMemory() {
        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
        certificateViewModel.onMemoryPressure()
        super.onLowMemory()
    }

    private fun launchCertificatePicker() {
        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
        certificateViewModel.prepareForCertificateSelection()
        certificatePicker.launch(PKCS12_MIME_TYPES)
    }

    override fun onDestroy() {
        cancelSigning(SigningCancelReason.BACKGROUND)
        signingCoordinator.close()
        super.onDestroy()
    }

    private fun prepareMiniAppletSigning(
        request: MiniAppletBridgeRequest,
        reply: SigningReplySink,
    ) {
        signingCoordinator.prepare(request.normalized, reply)
    }

    private fun confirmSigning(requestId: UUID) {
        val awaiting = signingCoordinator.state.value as? SigningUiState.AwaitingConfirmation
        if (awaiting?.requestId != requestId) return
        val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            signingCoordinator.confirm(requestId)
        }
        if (signingJobs.register(requestId, job)) {
            job.start()
        } else {
            job.cancel()
        }
    }

    private fun cancelSigning(
        reason: SigningCancelReason,
        requestId: UUID? = null,
    ) {
        val accepted = signingCoordinator.cancel(reason, requestId)
        signingJobs.takeForCancellation(requestId, accepted)?.cancel()
    }

    companion object {
        private object OpenableDocumentContract : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent =
                super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
        }

        private val PKCS12_MIME_TYPES = arrayOf(
            CertificateRepository.MIME_X_PKCS12,
            CertificateRepository.MIME_PKCS12,
            CertificateRepository.MIME_OCTET_STREAM,
        )
    }
}
