package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.StoredCertificateEntry
import dev.junta.firmamobile.certificate.StoredKeystoreLevel
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk

private const val USE_TINT_ALPHA = 0.12f
private const val SELECTED_TINT_ALPHA = 0.16f

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
    Column(
        modifier = Modifier.fillMaxWidth().testTag("saved-certificates"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (saved.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.saved_certificates_title),
                    modifier = Modifier.weight(1f),
                    color = JuntaInk,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                JuntaCountPill(text = saved.size.toString())
            }
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
        // With no certificate open, the panel's own primary button already picks a file.
        if (state !is CertificateUiState.NoCertificate) {
            JuntaOutlinedAction(
                text = stringResource(R.string.saved_certificate_add),
                onClick = actions.onAdd,
                modifier = Modifier.padding(top = 4.dp).testTag("add-saved-certificate"),
                iconRes = R.drawable.ic_ui_add,
            )
        }
    }
}

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, JuntaFieldShape)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
            .testTag("saved-certificate-${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Monogram, holder name and, for device keys, where the key is protected. */
@Composable
private fun RowScope.savedCertificateIdentity(entry: StoredCertificateEntry, name: String) {
    JuntaMonogram(name = name, size = 40.dp)
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

/** "Use" capsule; once the entry is active it turns into a disabled mint "Selected" badge. */
@Composable
private fun savedCertificateUseButton(selected: Boolean, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = !selected,
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = USE_TINT_ALPHA),
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = JuntaTone.MINT.accent.copy(alpha = SELECTED_TINT_ALPHA),
            disabledContentColor = JuntaInk,
        ),
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_check_circle),
                contentDescription = null,
                tint = JuntaTone.MINT.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        val label = if (selected) R.string.saved_certificate_selected else R.string.saved_certificate_use
        Text(stringResource(label), fontWeight = FontWeight.SemiBold)
    }
}

private fun CertificateUiState.selectedUri(): Uri? = when (this) {
    CertificateUiState.LoadingReference, is CertificateUiState.NoCertificate -> null
    is CertificateUiState.Locked -> reference.uri
    is CertificateUiState.Unlocking -> reference.uri
    is CertificateUiState.Unlocked -> reference.uri
}
