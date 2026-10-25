package dev.junta.firmamobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R

@Composable
internal fun LegalDocument.localizedTitle(): String {
    val resource = when (asset) {
        "legal/project-license.txt" -> R.string.legal_title_project_license
        "legal/project-notice.txt" -> R.string.legal_title_project_notice
        "legal/components.txt" -> R.string.legal_title_components
        "legal/upstream/Apache-2.0.txt" -> R.string.legal_title_apache
        "legal/upstream/bebas-neue-OFL.txt" -> R.string.legal_title_bebas_neue
        "legal/upstream/liberation-LICENSE.txt" -> R.string.legal_title_liberation
        "legal/upstream/pdfbox-LICENSE.txt" -> R.string.legal_title_pdfbox_license
        "legal/upstream/pdfbox-NOTICE.txt" -> R.string.legal_title_pdfbox_notice
        "legal/upstream/slf4j-LICENSE.txt" -> R.string.legal_title_slf4j
        "legal/upstream/stax2-BSD-2-Clause.txt" -> R.string.legal_title_stax2
        "legal/upstream/google-sdk-terms.txt" -> R.string.legal_title_google_sdk
        else -> return title
    }
    return stringResource(resource)
}
