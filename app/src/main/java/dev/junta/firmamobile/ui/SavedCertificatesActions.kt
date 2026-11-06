package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.StoredCertificateEntry
import dev.junta.firmamobile.certificate.StoredKeystoreLevel
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk

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
            FirmaSectionRule(
                label = stringResource(R.string.saved_certificates_title),
                count = saved.size.toString(),
                modifier = Modifier.padding(top = 22.dp, bottom = 2.dp),
            )
        }
        saved.forEach { entry -> key(entry.id) { savedCertificateRow(entry, actions) } }
        actions.state.error?.let {
            val message = when (it) {
                CertificateUiError.CATALOG_FULL -> R.string.error_catalog_full
                CertificateUiError.ALREADY_SAVED -> R.string.error_already_saved
                else -> R.string.saved_certificate_error
            }
            Text(
                stringResource(message),
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (actions.state.keyDeletionPending) {
            Text(
                stringResource(R.string.keystore_key_deletion_pending),
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // With no certificate open, the panel's own main key already picks a file.
        if (state !is CertificateUiState.NoCertificate) {
            JuntaOutlinedAction(
                text = stringResource(R.string.saved_certificate_add),
                onClick = actions.onAdd,
                modifier = Modifier.padding(top = 14.dp).testTag("add-saved-certificate"),
                iconRes = R.drawable.ic_ui_add,
            )
        }
    }
}

/** One saved certificate: its badge and name, the "use" key and a remove control, over a hairline. */
@Composable
private fun savedCertificateRow(entry: StoredCertificateEntry, actions: SavedCertificatesActions) {
    val name = certificateDisplayNameText(
        entry.reference?.summary?.ownerName ?: entry.signingKey?.displayName
            ?: entry.reference?.displayName.orEmpty(),
    )
    var confirmDeletion by remember(entry) { mutableStateOf(false) }
    if (confirmDeletion) {
        savedCertificateKeyDeletionDialog(
            onConfirm = { confirmDeletion = false; actions.onRemove(entry) },
            onDismiss = { confirmDeletion = false },
        )
    }
    val use = stringResource(R.string.saved_certificate_use_named, name)
    val remove = stringResource(R.string.saved_certificate_remove_named, name)
    val selected = entry == actions.state.activeKey
    Column(modifier = Modifier.fillMaxWidth().testTag("saved-certificate-${entry.id}")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            savedCertificateIdentity(entry = entry, name = name)
            savedCertificateUseButton(selected = selected, description = use) { actions.onSwitch(entry) }
            IconButton(
                onClick = {
                    if (entry.signingKey != null) confirmDeletion = true else actions.onRemove(entry)
                },
                modifier = Modifier.semantics { contentDescription = remove },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ui_delete),
                    contentDescription = null,
                    tint = JuntaMutedInk,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        FirmaDivider()
    }
}

/** Graphite badge, holder name and, for device keys, where the key is protected. */
@Composable
private fun RowScope.savedCertificateIdentity(entry: StoredCertificateEntry, name: String) {
    JuntaMonogram(name = name, size = 36.dp)
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = name,
            color = JuntaInk,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.signingKey?.let { key ->
            val level = if (key.level == StoredKeystoreLevel.STRONGBOX) {
                R.string.keystore_key_strongbox
            } else R.string.keystore_key_tee
            Text(stringResource(level), color = JuntaMutedInk, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Light "Use" key; the active entry stays pressed in, with its LED lit and "Selected" printed. */
@Composable
private fun savedCertificateUseButton(selected: Boolean, description: String, onClick: () -> Unit) {
    FirmaCompactKey(
        onClick = onClick,
        style = FirmaKeyStyle.LIGHT,
        shape = CircleShape,
        faceModifier = Modifier.heightIn(min = 36.dp),
        enabled = !selected,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                FirmaLed(state = FirmaLedState.ON, size = 7.dp, animate = false)
                Spacer(modifier = Modifier.width(7.dp))
            }
            val label = if (selected) R.string.saved_certificate_selected else R.string.saved_certificate_use
            Text(
                stringResource(label),
                color = if (selected) JuntaInk else Firma.keyLightInk,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

private fun CertificateUiState.selectedUri(): Uri? = when (this) {
    CertificateUiState.LoadingReference, is CertificateUiState.NoCertificate -> null
    is CertificateUiState.Locked -> reference.uri
    is CertificateUiState.Unlocking -> reference.uri
    is CertificateUiState.Unlocked -> reference.uri
}
