package dev.junta.firmamobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI

internal sealed interface AppDestination {
    data object Certificate : AppDestination

    data object Catalog : AppDestination

    data class Browser(
        val profileId: ProfileId?,
        val entryUrl: URI,
    ) : AppDestination
}

/**
 * Root destination and certificate overlay of one Activity host. Deliberately not a retained ViewModel:
 * a recreated host must start on Certificate with the panel closed, never restore a browser.
 * Callers run their side effects (signing cancellation, WebView release) before the command.
 */
internal class AppNavigationState {
    var destination by mutableStateOf<AppDestination>(AppDestination.Certificate)
        private set
    var certificatePanelVisible by mutableStateOf(false)
        private set

    fun showCertificate() {
        destination = AppDestination.Certificate
    }

    fun showCatalog() {
        destination = AppDestination.Catalog
    }

    fun openBrowser(profileId: ProfileId?, entryUrl: URI) {
        destination = AppDestination.Browser(profileId, entryUrl)
    }

    fun showCertificatePanel() {
        certificatePanelVisible = true
    }

    fun hideCertificatePanel() {
        certificatePanelVisible = false
    }
}
