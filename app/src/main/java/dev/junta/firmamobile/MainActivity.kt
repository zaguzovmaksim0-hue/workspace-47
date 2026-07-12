package dev.junta.firmamobile

import android.content.ActivityNotFoundException
import android.content.Intent
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
import dev.junta.firmamobile.browser.WebViewStateHolder
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.BrowserScreen
import dev.junta.firmamobile.ui.CertificateUiState
import dev.junta.firmamobile.ui.CertificateViewModel
import dev.junta.firmamobile.ui.SensitiveWindowProtection
import dev.junta.firmamobile.ui.WindowSecureFlagPolicy
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme

class MainActivity : ComponentActivity() {
    private lateinit var webViewStateHolder: WebViewStateHolder
    private var currentWebView: WebView? = null
    private var showBrowser by mutableStateOf(false)

    private val certificateViewModel: CertificateViewModel by viewModels {
        val app = application as JuntaFirmaApplication
        CertificateViewModel.Factory(app.certificateGateway, app.certificateSession)
    }

    private val certificatePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(certificateViewModel::onCertificateSelected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webViewStateHolder = WebViewStateHolder(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val certificateState = certificateViewModel.state.collectAsStateWithLifecycle()
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
                        onExitBrowser = { showBrowser = false },
                        onOpenExternal = { uri ->
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (_: ActivityNotFoundException) {
                                // The validated URL stays closed if no browser can handle it.
                            }
                        },
                        onChangeCertificate = {
                            showBrowser = false
                            launchCertificatePicker()
                        },
                        onLockCertificate = {
                            showBrowser = false
                            certificateViewModel.lock()
                        },
                        onClearSession = {
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
        currentWebView?.let { webViewStateHolder.save(it, outState) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        certificateViewModel.onAppBackgrounded()
        super.onStop()
    }

    override fun onLowMemory() {
        certificateViewModel.onMemoryPressure()
        super.onLowMemory()
    }

    private fun launchCertificatePicker() {
        certificateViewModel.prepareForCertificateSelection()
        certificatePicker.launch(PKCS12_MIME_TYPES)
    }

    companion object {
        private val PKCS12_MIME_TYPES = arrayOf(
            CertificateRepository.MIME_X_PKCS12,
            CertificateRepository.MIME_PKCS12,
            CertificateRepository.MIME_OCTET_STREAM,
        )
    }
}
