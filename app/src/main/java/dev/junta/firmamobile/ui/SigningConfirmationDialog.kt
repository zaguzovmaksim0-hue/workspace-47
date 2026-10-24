package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.junta.firmamobile.R
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.signing.SigningUiState

@Composable
internal fun SigningConfirmationDialog(
    state: SigningUiState.AwaitingConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val confirmArmed = rememberConsentArmed(state.requestId)
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.signing_confirmation_title)) },
        text = {
            FilterObscuredTouches()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.signing_site, state.siteHost))
                Text(stringResource(R.string.signing_profile, state.profileName))
                Text(
                    stringResource(
                        R.string.signing_support_level,
                        stringResource(compatibilityStatusLabelRes(state.compatibilityStatus)),
                    ),
                )
                if (state.compatibilityStatus != CompatibilityStatus.VERIFIED_E2E) {
                    Text(stringResource(R.string.compatibility_unverified_operation))
                }
                Text(stringResource(R.string.signing_operation, state.safeDescription))
                Text(stringResource(R.string.signing_certificate, state.certificateOwner))
                Text(stringResource(R.string.signing_format, state.format))
                Text(stringResource(R.string.signing_algorithm, state.algorithm))
                if (state.requiresLegacySha1Warning) {
                    Text(
                        text = stringResource(R.string.signing_sha1_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.batchDocumentReview == null) {
                    SigningDocumentConsent(state.documentConsent, showPdfPreview = state.format == "PAdES")
                }
                BatchSigningDocuments(state)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmArmed) {
                Text(stringResource(R.string.signing_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention, as in other UI components.
@Composable
private fun BatchSigningDocuments(state: SigningUiState.AwaitingConfirmation) {
    val review = state.batchDocumentReview ?: return
    if (state.format == "PAdES") pdfPreview(null)
    var expanded by remember(state.requestId) { mutableStateOf(false) }
    Text(
        text = pluralStringResource(
            R.plurals.signing_batch_document_count,
            review.documentLabels.size,
            review.documentLabels.size,
        ),
        modifier = Modifier.testTag("signing-batch-documents-count"),
    )
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.testTag("signing-batch-documents-toggle"),
    ) {
        Text(
            stringResource(
                if (expanded) R.string.signing_batch_documents_hide
                else R.string.signing_batch_documents_show,
            ),
        )
    }
    if (expanded) {
        Column(
            modifier = Modifier.testTag("signing-batch-documents"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.signing_batch_documents_explanation))
            review.documentLabels.forEachIndexed { index, label ->
                Text(
                    text = if (label.isEmpty()) {
                        stringResource(R.string.signing_batch_document_number, index + 1)
                    } else {
                        stringResource(R.string.signing_batch_document_identifier, index + 1, label)
                    },
                    modifier = Modifier.testTag("signing-batch-document-$index"),
                )
            }
        }
    }
}
