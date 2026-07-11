package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AppRoot(
    state: CertificateUiState = CertificateUiState.NoCertificate(),
    onSelectCertificate: () -> Unit = {},
    onUnlock: (CharArray) -> Unit = {},
    onLock: () -> Unit = {},
    onForget: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.unofficial_disclosure),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.certificate_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.certificate_copy),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.privacy_copy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))

            when (state) {
                CertificateUiState.LoadingReference -> LoadingCertificate()
                is CertificateUiState.NoCertificate -> NoCertificate(
                    state = state,
                    onSelectCertificate = onSelectCertificate,
                )
                is CertificateUiState.Locked -> LockedCertificate(
                    state = state,
                    onUnlock = onUnlock,
                    onSelectCertificate = onSelectCertificate,
                    onForget = onForget,
                )
                is CertificateUiState.Unlocking -> UnlockingCertificate(state)
                is CertificateUiState.Unlocked -> UnlockedCertificate(
                    state = state,
                    onContinue = onContinue,
                    onSelectCertificate = onSelectCertificate,
                    onLock = onLock,
                    onForget = onForget,
                )
            }
        }
    }
}

@Composable
private fun LoadingCertificate() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(text = stringResource(R.string.certificate_loading))
    }
}

@Composable
private fun NoCertificate(
    state: CertificateUiState.NoCertificate,
    onSelectCertificate: () -> Unit,
) {
    state.error?.let { CertificateError(it) }
    Button(
        onClick = onSelectCertificate,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.select_certificate))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockedCertificate(
    state: CertificateUiState.Locked,
    onUnlock: (CharArray) -> Unit,
    onSelectCertificate: () -> Unit,
    onForget: () -> Unit,
) {
    Text(
        text = stringResource(
            R.string.certificate_selected,
            state.reference.displayName,
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    state.summary?.let {
        Spacer(modifier = Modifier.height(12.dp))
        CertificateSummaryDetails(it)
    }
    state.error?.let {
        Spacer(modifier = Modifier.height(12.dp))
        CertificateError(it)
    }
    Spacer(modifier = Modifier.height(16.dp))

    val passwordState = remember(state.reference.uri) { TextFieldState() }
    DisposableEffect(passwordState) {
        onDispose { passwordState.clearText() }
    }
    val passwordLabel = stringResource(R.string.certificate_password)
    OutlinedSecureTextField(
        state = passwordState,
        label = { Text(passwordLabel) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = passwordLabel },
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = {
            val password = CharArray(passwordState.text.length) { index ->
                passwordState.text[index]
            }
            passwordState.clearText()
            onUnlock(password)
        },
        enabled = passwordState.text.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.unlock_certificate))
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onSelectCertificate,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.choose_another_certificate))
    }
    OutlinedButton(
        onClick = onForget,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.forget_certificate))
    }
}

@Composable
private fun UnlockingCertificate(state: CertificateUiState.Unlocking) {
    Text(
        text = stringResource(R.string.certificate_selected, state.reference.displayName),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(text = stringResource(R.string.certificate_unlocking))
    }
}

@Composable
private fun UnlockedCertificate(
    state: CertificateUiState.Unlocked,
    onContinue: () -> Unit,
    onSelectCertificate: () -> Unit,
    onLock: () -> Unit,
    onForget: () -> Unit,
) {
    Text(
        text = stringResource(R.string.certificate_found),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(12.dp))
    CertificateSummaryDetails(state.summary)
    Spacer(modifier = Modifier.height(20.dp))
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.continue_to_portal))
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onSelectCertificate,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.choose_another_certificate))
    }
    OutlinedButton(
        onClick = onLock,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.lock_certificate))
    }
    OutlinedButton(
        onClick = onForget,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.forget_certificate))
    }
}

@Composable
private fun CertificateSummaryDetails(summary: CertificateSummary) {
    Text(text = stringResource(R.string.certificate_owner, summary.ownerName))
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = stringResource(R.string.certificate_issuer, summary.issuerName))
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = stringResource(R.string.certificate_valid_from, summary.validFrom.toDisplayDate()))
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = stringResource(R.string.certificate_valid_until, summary.validUntil.toDisplayDate()))
}

@Composable
private fun CertificateError(error: CertificateUiError) {
    Text(
        text = error.message(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun CertificateUiError.message(): String = stringResource(
    when (this) {
        CertificateUiError.PASSWORD_INVALID_OR_FILE -> R.string.error_password_or_file
        CertificateUiError.FILE_TOO_LARGE -> R.string.error_file_too_large
        CertificateUiError.PRIVATE_KEY_MISSING -> R.string.error_private_key_missing
        CertificateUiError.MULTIPLE_PRIVATE_KEYS -> R.string.error_multiple_private_keys
        CertificateUiError.CERTIFICATE_NOT_X509 -> R.string.error_certificate_not_x509
        CertificateUiError.UNSUPPORTED_KEY_TYPE -> R.string.error_unsupported_key_type
        CertificateUiError.CERTIFICATE_EXPIRED -> R.string.error_certificate_expired
        CertificateUiError.CERTIFICATE_NOT_YET_VALID -> R.string.error_certificate_not_yet_valid
        CertificateUiError.KEY_USAGE_NOT_PERMITTED -> R.string.error_key_usage
        CertificateUiError.KEY_CERTIFICATE_MISMATCH -> R.string.error_key_mismatch
        CertificateUiError.UNSUPPORTED_FILE -> R.string.error_unsupported_file
        CertificateUiError.DOCUMENT_UNAVAILABLE -> R.string.error_document_unavailable
        CertificateUiError.PERMISSION_DENIED -> R.string.error_permission_denied
        CertificateUiError.STORAGE_FAILURE -> R.string.error_storage
        CertificateUiError.CERTIFICATE_NOT_SELECTED -> R.string.error_not_selected
        CertificateUiError.CERTIFICATE_NOT_USABLE -> R.string.error_not_usable
    },
)

private fun Instant.toDisplayDate(): String = DATE_FORMATTER.format(this)

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    .withZone(ZoneId.systemDefault())
