package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.browser.WebAuthnEngineState

/** Read-only explanations; opening or closing this dialog never reloads the page
 * or grants certificate/signing authority. Consent remains in its original UI. */
@Composable
internal fun BrowserSiteInformationDialog(
    profileName: String,
    host: String,
    trustLabel: String,
    publicBrowsing: Boolean,
    webAuthnState: WebAuthnEngineState?,
    onOpenInBrowser: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("browser-site-information"),
        title = { Text(stringResource(R.string.browser_site_information)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrowserServiceIdentity(profileName, host, trustLabel)
                if (publicBrowsing) PublicBrowsingNotice()
                webAuthnState?.let { WebAuthnStatusButton(it, onOpenInBrowser) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("browser-site-information-close")) {
                Text(stringResource(R.string.webauthn_close))
            }
        },
    )
}
