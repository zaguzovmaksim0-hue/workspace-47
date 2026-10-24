package dev.junta.firmamobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.junta.firmamobile.ui.theme.JuntaTeal
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaHairline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentProblem
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.afirma.servlet.NativeBatchConsentReview
import dev.junta.firmamobile.afirma.servlet.AfirmaResultSummary

@Composable
internal fun NativeAfirmaConsentDialog(
    prompt: AfirmaConsentPrompt,
    onConfirm: () -> Unit,
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var detailsExpanded by remember(prompt.token) { mutableStateOf(false) }
    var previewIndex by remember(prompt.token) { mutableStateOf(prompt.pdfPreviews.keys.firstOrNull()) }
    val confirmArmed = rememberConsentArmed(prompt.token to prompt.canConfirm)
    val finished = prompt.phase == AfirmaConsentPhase.FINISHED
    val review = prompt.phase == AfirmaConsentPhase.REVIEW
    val cancelling = prompt.phase == AfirmaConsentPhase.CANCELLING
    AlertDialog(
        onDismissRequest = if (finished || review) onDismiss else onCancel,
        modifier = Modifier.testTag("native-afirma-consent"),
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        shape = JuntaPanelShape,
        containerColor = JuntaPaperElevated,
        tonalElevation = 0.dp,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.native_afirma_title),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(when {
                    finished -> R.string.native_consent_result_title
                    !review -> R.string.native_consent_progress_title
                    prompt.details.operation == "selectcert" -> R.string.native_afirma_send_certificate
                    else -> R.string.native_consent_review_title
                }), style = MaterialTheme.typography.headlineMedium, color = JuntaTeal)
            }
        },
        text = {
            FilterObscuredTouches()
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when (prompt.details.operation) {
                    "selectcert" -> R.string.native_afirma_selectcert
                    "cosign" -> when {
                        prompt.details.format?.startsWith("PDF") == true && !prompt.details.delegatedSigning -> R.string.native_afirma_pdf_cosign
                        prompt.details.format?.startsWith("CAdES") == true && !prompt.details.delegatedSigning -> R.string.native_cades_cosign_action
                        else -> R.string.native_multiphase_cosign
                    }
                    "countersign" -> R.string.native_multiphase_countersign
                    "batch" -> R.string.native_multiphase_batch
                    else -> R.string.native_afirma_sign
                }))
                Text(stringResource(R.string.compatibility_operation_notice))
                Surface(
                    shape = JuntaPanelShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, JuntaHairline),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConsentIdentityField(stringResource(R.string.native_consent_page_label), prompt.details.sourceOrigin)
                        ConsentIdentityField(stringResource(R.string.native_consent_recipient_label), prompt.details.destination)
                        prompt.certificateOwner?.let {
                            ConsentIdentityField(stringResource(R.string.native_consent_certificate_label), it)
                        }
                    }
                }
                if (prompt.details.requiresExactCertificate) {
                    Text(stringResource(R.string.native_afirma_exact_certificate),
                        Modifier.testTag("native-afirma-exact-certificate"))
                }
                prompt.details.serviceDestinations.forEach { endpoint -> Text(stringResource(R.string.native_multiphase_service, endpoint)) }
                prompt.details.batchItems?.let {
                    Text(stringResource(R.string.native_multiphase_count, it), Modifier.testTag("native-batch-count"))
                }
                if (review) prompt.details.batchReview?.let { nativeBatchDocuments(it, prompt.token) }
                if (prompt.details.delegatedSigning) Text(stringResource(R.string.native_multiphase_delegated))
                prompt.resultSummary?.let { summary ->
                    val text = when (summary) {
                        AfirmaResultSummary.ServiceResult -> stringResource(R.string.native_multiphase_result)
                        is AfirmaResultSummary.Batch -> stringResource(R.string.native_multiphase_batch_result,
                            summary.documents, summary.positive, summary.documents - summary.positive)
                    }
                    Text(text, Modifier.testTag("native-multiphase-results"))
                }
                if (finished) prompt.batchReceipt?.let { NativeBatchResultDetails(it) }
                if (prompt.details.format?.startsWith("PDF") == true) Text(stringResource(R.string.native_afirma_pdf_prior_signatures))
                if (review && (prompt.details.format?.contains("PAdES", ignoreCase = true) == true ||
                        prompt.pdfPreviews.isNotEmpty())) {
                    if (prompt.details.batchItems != null) {
                        prompt.pdfPreviews.keys.forEach { index ->
                            TextButton(onClick = { previewIndex = index }) {
                                Text(stringResource(R.string.signing_batch_document_number, index + 1))
                            }
                        }
                        previewIndex?.let { Text(stringResource(R.string.signing_batch_document_number, it + 1)) }
                    }
                    key(prompt.token, previewIndex) { pdfPreview(previewIndex?.let(prompt.pdfPreviews::get)) }
                }
                if (!prompt.details.delegatedSigning &&
                    (prompt.details.operation == "cosign" && prompt.details.format?.startsWith("CAdES") == true ||
                        prompt.details.operation == "batch" && prompt.details.format?.contains("CAdES co-sign") == true)) {
                    Text(stringResource(R.string.native_cades_cosign_notice), Modifier.testTag("native-cades-cosign-notice"))
                }
                prompt.details.signaturePolicySummary?.let { policy ->
                    Text(stringResource(R.string.native_cades_policy_notice, policy), Modifier.testTag("native-cades-policy-notice"))
                }
                if (prompt.details.signaturePolicyItems > 0) {
                    Text(stringResource(R.string.native_cades_policy_batch, prompt.details.signaturePolicyItems), Modifier.testTag("native-cades-policy-batch"))
                }
                if (prompt.details.providedDigestAlgorithm != null) {
                    Text(stringResource(R.string.native_cades_provided_hash_notice, prompt.details.providedDigestAlgorithm),
                        Modifier.testTag("native-cades-provided-hash-notice"))
                }
                if (prompt.details.providedDigestItems > 0) {
                    Text(stringResource(R.string.native_cades_provided_hash_batch, prompt.details.providedDigestItems),
                        Modifier.testTag("native-cades-provided-hash-batch"))
                }
                if (review) Text(stringResource(R.string.native_afirma_cancel_notice))
                if (!review && !finished) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("native-afirma-progress"))
                }
                when (prompt.phase) {
                    AfirmaConsentPhase.WORKING -> Text(stringResource(R.string.native_afirma_working))
                    AfirmaConsentPhase.SENDING -> Text(stringResource(R.string.native_afirma_sending))
                    AfirmaConsentPhase.CANCELLING -> {
                        Text(stringResource(R.string.native_afirma_cancel_progress))
                        Text(stringResource(R.string.native_afirma_cancel_stop_warning))
                    }
                    else -> Unit
                }
                when (prompt.problem) {
                    AfirmaConsentProblem.LOCKED -> Text(stringResource(R.string.native_afirma_locked))
                    AfirmaConsentProblem.INCOMPATIBLE -> Text(stringResource(R.string.native_afirma_incompatible))
                    AfirmaConsentProblem.ACKNOWLEDGED -> Text(stringResource(R.string.native_afirma_stored))
                    AfirmaConsentProblem.UNCERTAIN -> Text(stringResource(R.string.native_afirma_uncertain))
                    AfirmaConsentProblem.NOT_SENT -> Text(stringResource(R.string.native_afirma_not_sent))
                    AfirmaConsentProblem.REJECTED -> Text(stringResource(R.string.native_afirma_rejected))
                    AfirmaConsentProblem.CANCELLED -> Text(stringResource(R.string.native_afirma_cancelled))
                    AfirmaConsentProblem.EXPIRED -> Text(stringResource(R.string.native_afirma_expired))
                    AfirmaConsentProblem.FAILED -> Text(stringResource(R.string.native_afirma_failed))
                    AfirmaConsentProblem.CANCEL_ACKNOWLEDGED -> Text(stringResource(R.string.native_afirma_cancel_acknowledged))
                    AfirmaConsentProblem.CANCEL_NOT_SENT -> Text(stringResource(R.string.native_afirma_cancel_not_sent))
                    AfirmaConsentProblem.CANCEL_REJECTED -> Text(stringResource(R.string.native_afirma_cancel_rejected))
                    AfirmaConsentProblem.CANCEL_UNCERTAIN -> Text(stringResource(R.string.native_afirma_cancel_uncertain))
                    null -> if (review) Text(stringResource(R.string.native_afirma_review_notice))
                }
                val detailsState = stringResource(if (detailsExpanded) R.string.native_consent_expanded else R.string.native_consent_collapsed)
                TextButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("native-afirma-details-toggle")
                        .semantics { stateDescription = detailsState },
                ) {
                    Text(stringResource(if (detailsExpanded) R.string.native_consent_hide_details else R.string.native_consent_show_details))
                }
                if (detailsExpanded) {
                    Column(Modifier.fillMaxWidth().testTag("native-afirma-details"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        prompt.details.format?.let { Text(stringResource(R.string.native_afirma_format, it)) }
                        prompt.details.algorithm?.let { Text(stringResource(R.string.native_afirma_algorithm, it)) }
                        if (prompt.details.operation != "selectcert") {
                            Text(stringResource(when {
                                prompt.details.batchItems != null -> R.string.native_batch_descriptor_size
                                prompt.details.providedDigestAlgorithm == null -> R.string.native_afirma_size
                                else -> R.string.native_cades_provided_hash_size
                            }, prompt.details.payloadBytes))
                            prompt.details.payloadSha256?.let { Text(stringResource(when {
                                prompt.details.batchItems != null -> R.string.native_batch_descriptor_digest
                                prompt.details.providedDigestAlgorithm == null -> R.string.native_afirma_digest
                                else -> R.string.native_cades_received_hash_fingerprint
                            }, it)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                finished -> ConsentPrimaryButton(
                    text = stringResource(R.string.native_afirma_close),
                    onClick = onDismiss, modifier = Modifier.testTag("native-afirma-close"),
                )
                review && prompt.canConfirm -> ConsentPrimaryButton(
                    text = stringResource(if (prompt.details.operation == "selectcert") R.string.native_afirma_send_certificate else R.string.native_afirma_sign_and_send),
                    onClick = onConfirm, modifier = Modifier.testTag("native-afirma-confirm"),
                    enabled = confirmArmed,
                )
                review -> ConsentPrimaryButton(
                    text = stringResource(R.string.interactive_tls_unlock),
                    onClick = onUnlock, modifier = Modifier.testTag("native-afirma-unlock"),
                )
            }
        },
        dismissButton = {
            if (!finished) TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(if (cancelling) "native-afirma-stop-cancel" else "native-afirma-cancel"),
            ) {
                Text(stringResource(when {
                    review -> R.string.native_afirma_cancel_notify
                    cancelling -> R.string.native_afirma_cancel_stop
                    else -> R.string.cancel
                }))
            }
        },
    )
}

@Composable
private fun ConsentIdentityField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConsentPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = JuntaPanelShape) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun nativeBatchDocuments(review: NativeBatchConsentReview, token: java.util.UUID) {
    var expanded by remember(token) { mutableStateOf(false) }
    val state = stringResource(
        if (expanded) R.string.native_consent_expanded else R.string.native_consent_collapsed,
    )
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("native-batch-documents-toggle").semantics { stateDescription = state },
    ) {
        Text(stringResource(
            if (expanded) R.string.signing_batch_documents_hide else R.string.signing_batch_documents_show,
        ))
    }
    if (expanded) {
        Column(Modifier.fillMaxWidth().testTag("native-batch-documents"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.signing_batch_documents_explanation))
            Text(stringResource(R.string.native_batch_evidence_notice))
            review.documents.forEachIndexed { index, document ->
                Text(stringResource(if (document.identifier.isEmpty()) R.string.signing_batch_document_number
                    else R.string.signing_batch_document_identifier, index + 1, document.identifier),
                    Modifier.testTag("native-batch-document-$index"))
                val evidence = document.evidence
                if (evidence == null) {
                    Text(stringResource(R.string.native_batch_evidence_unavailable),
                        Modifier.testTag("native-batch-document-unavailable-$index"))
                } else {
                    Text(stringResource(R.string.native_batch_document_bytes, evidence.bytes),
                        Modifier.testTag("native-batch-document-bytes-$index"))
                    Text(stringResource(R.string.native_batch_document_sha256, evidence.sha256),
                        Modifier.testTag("native-batch-document-sha256-$index"))
                }
            }
        }
    }
}
