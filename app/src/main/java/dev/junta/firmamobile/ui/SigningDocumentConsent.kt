package dev.junta.firmamobile.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R
import dev.junta.firmamobile.signing.DocumentConsentEvidence

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun SigningDocumentConsent(evidence: DocumentConsentEvidence, showPdfPreview: Boolean) {
    if (showPdfPreview) pdfPreview(evidence.pdfPreview)
    val document = evidence.actualDocument
    val digest = document?.sha256ForDisplay()
    if (document == null || digest == null) {
        Text(
            text = stringResource(R.string.signing_document_metadata_unavailable),
            modifier = Modifier.testTag("signing-document-unavailable"),
        )
        return
    }
    // None of these portal contracts supplies an authoritative filename. Do not use an ID,
    // URL, operation description, or PDF title as a filename, nor parse untrusted labels here.
    Text(
        text = stringResource(R.string.signing_document_name_unavailable),
        modifier = Modifier.testTag("signing-document-name"),
    )
    Text(
        text = stringResource(R.string.signing_document_bytes, document.byteCount),
        modifier = Modifier.testTag("signing-document-bytes"),
    )
    Text(
        text = stringResource(R.string.signing_document_sha256, digest),
        modifier = Modifier.testTag("signing-document-sha256"),
    )
}
