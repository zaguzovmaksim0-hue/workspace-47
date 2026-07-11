package dev.junta.firmamobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.CertificateViewModel
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme

class MainActivity : ComponentActivity() {
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
        enableEdgeToEdge()
        setContent {
            val certificateState = certificateViewModel.state.collectAsStateWithLifecycle()
            JuntaFirmaTheme {
                AppRoot(
                    state = certificateState.value,
                    onSelectCertificate = {
                        certificateViewModel.prepareForCertificateSelection()
                        certificatePicker.launch(PKCS12_MIME_TYPES)
                    },
                    onUnlock = certificateViewModel::unlock,
                    onLock = certificateViewModel::lock,
                    onForget = certificateViewModel::forget,
                )
            }
        }
    }

    override fun onStop() {
        certificateViewModel.onAppBackgrounded()
        super.onStop()
    }

    override fun onLowMemory() {
        certificateViewModel.onMemoryPressure()
        super.onLowMemory()
    }

    companion object {
        private val PKCS12_MIME_TYPES = arrayOf(
            CertificateRepository.MIME_X_PKCS12,
            CertificateRepository.MIME_PKCS12,
            CertificateRepository.MIME_OCTET_STREAM,
        )
    }
}
