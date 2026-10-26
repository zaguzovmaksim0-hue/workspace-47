package dev.junta.firmamobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentDetails
import dev.junta.firmamobile.afirma.servlet.NativeMultiPhaseFormat
import dev.junta.firmamobile.certificate.CertificateDisplayName
import dev.junta.firmamobile.certificate.CertificateDisplayNamePolicy
import dev.junta.firmamobile.signing.SigningOperationDescription

@Composable
internal fun certificateDisplayNameText(name: String): String =
    when (val value = CertificateDisplayNamePolicy.presentation(name)) {
        CertificateDisplayName.Default -> stringResource(R.string.certificate_default_name)
        is CertificateDisplayName.Named -> value.value
    }

@Composable
internal fun signingOperationText(value: SigningOperationDescription): String = when (value) {
    is SigningOperationDescription.Literal -> value.value
    is SigningOperationDescription.Batch -> pluralStringResource(
        R.plurals.signing_batch_operation_description, value.documentCount, value.documentCount,
    )
}

@Composable
internal fun nativeFormatText(details: AfirmaConsentDetails): String? = when (details.multiPhaseFormat) {
    null -> details.format
    NativeMultiPhaseFormat.TRIPHASE -> stringResource(R.string.native_format_triphase, details.format.orEmpty())
    NativeMultiPhaseFormat.LOCAL_BATCH -> stringResource(R.string.native_format_batch_local)
    NativeMultiPhaseFormat.REMOTE_BATCH -> stringResource(R.string.native_format_batch_remote)
    NativeMultiPhaseFormat.LOCAL_CADES_COSIGN_BATCH -> stringResource(R.string.native_format_batch_local_cades_cosign)
}
