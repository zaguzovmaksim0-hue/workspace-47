package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.StoredCertificateEntry

/** Catalogue actions for the certificate panel; absent where the panel has no catalogue. */
internal class SavedCertificatesActions(
    val state: SavedCertificatesUiState,
    val onAdd: () -> Unit,
    val onSwitch: (StoredCertificateEntry) -> Unit,
    val onRemove: (StoredCertificateEntry) -> Unit,
)

internal val LocalSavedCertificates = staticCompositionLocalOf<SavedCertificatesActions?> { null }

/** Saved file entries other than the selected one; key-backed entries are managed by the keystore catalogue. */
@Composable
internal fun savedCertificatesSection(state: CertificateUiState) {
    val actions = LocalSavedCertificates.current ?: return
    val activeUri = state.selectedUri()
    val saved = actions.state.entries.filter {
        it.signingKey == null && it.reference != null && it.reference.uri != activeUri
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("saved-certificates")) {
        if (saved.isNotEmpty()) {
            Text(stringResource(R.string.saved_certificates_title), style = MaterialTheme.typography.titleSmall)
        }
        saved.forEach { savedCertificateRow(it, actions) }
        actions.state.error?.let {
            Text(stringResource(R.string.saved_certificate_error), color = MaterialTheme.colorScheme.error)
        }
        TextButton(
            onClick = actions.onAdd,
            modifier = Modifier.fillMaxWidth().testTag("add-saved-certificate"),
        ) { Text(stringResource(R.string.saved_certificate_add)) }
    }
}

@Composable
private fun savedCertificateRow(entry: StoredCertificateEntry, actions: SavedCertificatesActions) {
    val name = entry.reference?.summary?.ownerName ?: entry.reference?.displayName.orEmpty()
    val use = stringResource(R.string.saved_certificate_use_named, name)
    val remove = stringResource(R.string.saved_certificate_remove_named, name)
    Row(
        modifier = Modifier.fillMaxWidth().testTag("saved-certificate-${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(name, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { actions.onSwitch(entry) },
            modifier = Modifier.semantics { contentDescription = use },
        ) { Text(stringResource(R.string.saved_certificate_use)) }
        TextButton(
            onClick = { actions.onRemove(entry) },
            modifier = Modifier.semantics { contentDescription = remove },
        ) { Text(stringResource(R.string.saved_certificate_remove)) }
    }
}

private fun CertificateUiState.selectedUri(): Uri? = when (this) {
    CertificateUiState.LoadingReference, is CertificateUiState.NoCertificate -> null
    is CertificateUiState.Locked -> reference.uri
    is CertificateUiState.Unlocking -> reference.uri
    is CertificateUiState.Unlocked -> reference.uri
}
