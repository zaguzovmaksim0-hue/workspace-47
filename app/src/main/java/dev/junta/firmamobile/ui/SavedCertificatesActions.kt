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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.junta.firmamobile.certificate.StoredKeystoreLevel

/** Catalogue actions for the certificate panel; absent where the panel has no catalogue. */
internal class SavedCertificatesActions(
    val state: SavedCertificatesUiState,
    val onAdd: () -> Unit,
    val onSwitch: (StoredCertificateEntry) -> Unit,
    val onRemove: (StoredCertificateEntry) -> Unit,
)

internal val LocalSavedCertificates = staticCompositionLocalOf<SavedCertificatesActions?> { null }

/** Saved certificates, including key-only records; selecting a row never unlocks its key. */
@Composable
internal fun savedCertificatesSection(state: CertificateUiState) {
    val actions = LocalSavedCertificates.current ?: return
    val activeUri = state.selectedUri()
    val saved = actions.state.entries.filter {
        it.signingKey != null || (it.reference != null && it.reference.uri != activeUri)
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("saved-certificates")) {
        if (saved.isNotEmpty()) {
            Text(stringResource(R.string.saved_certificates_title), style = MaterialTheme.typography.titleSmall)
        }
        saved.forEach { entry -> key(entry.id) { savedCertificateRow(entry, actions) } }
        actions.state.error?.let {
            val message = when (it) {
                CertificateUiError.CATALOG_FULL -> R.string.error_catalog_full
                CertificateUiError.ALREADY_SAVED -> R.string.error_already_saved
                else -> R.string.saved_certificate_error
            }
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
        }
        if (actions.state.keyDeletionPending) {
            Text(stringResource(R.string.keystore_key_deletion_pending), color = MaterialTheme.colorScheme.error)
        }
        TextButton(
            onClick = actions.onAdd,
            modifier = Modifier.fillMaxWidth().testTag("add-saved-certificate"),
        ) { Text(stringResource(R.string.saved_certificate_add)) }
    }
}

@Composable
private fun savedCertificateRow(entry: StoredCertificateEntry, actions: SavedCertificatesActions) {
    val name = entry.reference?.summary?.ownerName ?: entry.signingKey?.displayName
        ?: entry.reference?.displayName.orEmpty()
    var confirmDeletion by remember(entry) { mutableStateOf(false) }
    if (confirmDeletion) {
        savedCertificateKeyDeletionDialog(
            onConfirm = { confirmDeletion = false; actions.onRemove(entry) },
            onDismiss = { confirmDeletion = false },
        )
    }
    val use = stringResource(R.string.saved_certificate_use_named, name)
    val remove = stringResource(R.string.saved_certificate_remove_named, name)
    Row(
        modifier = Modifier.fillMaxWidth().testTag("saved-certificate-${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name)
            entry.signingKey?.let { key ->
                val level = if (key.level == StoredKeystoreLevel.STRONGBOX) {
                    R.string.keystore_key_strongbox
                } else R.string.keystore_key_tee
                Text(stringResource(level), style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(
            onClick = { actions.onSwitch(entry) },
            enabled = entry != actions.state.activeKey,
            modifier = Modifier.semantics { contentDescription = use },
        ) {
            val label = if (entry == actions.state.activeKey) R.string.saved_certificate_selected
                else R.string.saved_certificate_use
            Text(stringResource(label))
        }
        TextButton(
            onClick = {
                if (entry.signingKey != null) confirmDeletion = true else actions.onRemove(entry)
            },
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
