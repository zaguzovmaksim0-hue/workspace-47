package dev.junta.firmamobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaTeal
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JuntaPaper),
    ) {
        Image(
            painter = painterResource(R.drawable.jfm_home_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .testTag("jfm-home-background"),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            JuntaBrandHeader()
            Spacer(modifier = Modifier.height(12.dp))
            CertificatePanel(
                state = state,
                onSelectCertificate = onSelectCertificate,
                onUnlock = onUnlock,
                onLock = onLock,
                onForget = onForget,
                onContinue = onContinue,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CertificateStateBanner(state)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CertificatePanel(
    state: CertificateUiState,
    onSelectCertificate: () -> Unit,
    onUnlock: (CharArray) -> Unit,
    onLock: () -> Unit,
    onForget: () -> Unit,
    onContinue: () -> Unit,
) {
    JuntaElevatedPanel {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_jfm_document),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(width = 42.dp, height = 48.dp),
            )
            Text(
                text = stringResource(R.string.certificate_title),
                color = JuntaTeal,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.padding(start = 56.dp)) {
            Text(
                text = stringResource(R.string.certificate_copy),
                color = JuntaInk,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.privacy_copy),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = JuntaHairline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

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

@Composable
private fun LoadingCertificate() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = JuntaTeal,
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
        Text(
            text = stringResource(R.string.certificate_loading),
            color = JuntaInk,
        )
    }
}

@Composable
private fun NoCertificate(
    state: CertificateUiState.NoCertificate,
    onSelectCertificate: () -> Unit,
) {
    state.error?.let { CertificateError(it) }
    JuntaPrimaryButton(
        text = stringResource(R.string.select_certificate),
        onClick = onSelectCertificate,
    )
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
        color = JuntaInk,
        style = MaterialTheme.typography.titleMedium,
    )
    state.summary?.let {
        Spacer(modifier = Modifier.height(8.dp))
        CertificateSummaryDetails(it)
    }
    state.error?.let {
        Spacer(modifier = Modifier.height(12.dp))
        CertificateError(it)
    }
    Spacer(modifier = Modifier.height(10.dp))

    val passwordState = remember(state.reference.uri) { TextFieldState() }
    DisposableEffect(passwordState) {
        onDispose { passwordState.clearText() }
    }
    val passwordLabel = stringResource(R.string.certificate_password)
    OutlinedSecureTextField(
        state = passwordState,
        label = { Text(passwordLabel) },
        shape = JuntaPanelShape,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = passwordLabel },
    )
    Spacer(modifier = Modifier.height(8.dp))
    JuntaPrimaryButton(
        text = stringResource(R.string.unlock_certificate),
        onClick = {
            val password = CharArray(passwordState.text.length) { index ->
                passwordState.text[index]
            }
            passwordState.clearText()
            onUnlock(password)
        },
        enabled = passwordState.text.isNotEmpty(),
    )
    Spacer(modifier = Modifier.height(6.dp))
    JuntaOutlinedAction(
        text = stringResource(R.string.choose_another_certificate),
        onClick = onSelectCertificate,
    )
    Spacer(modifier = Modifier.height(6.dp))
    JuntaOutlinedAction(
        text = stringResource(R.string.forget_certificate),
        onClick = onForget,
    )
}

@Composable
private fun UnlockingCertificate(state: CertificateUiState.Unlocking) {
    Text(
        text = stringResource(R.string.certificate_selected, state.reference.displayName),
        color = JuntaInk,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = JuntaTeal,
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
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
        color = JuntaTeal,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    CertificateSummaryDetails(state.summary)
    Spacer(modifier = Modifier.height(12.dp))
    JuntaPrimaryButton(
        text = stringResource(R.string.continue_to_portal),
        onClick = onContinue,
    )
    Spacer(modifier = Modifier.height(6.dp))
    JuntaOutlinedAction(
        text = stringResource(R.string.choose_another_certificate),
        onClick = onSelectCertificate,
    )
    Spacer(modifier = Modifier.height(6.dp))
    JuntaOutlinedAction(
        text = stringResource(R.string.lock_certificate),
        onClick = onLock,
    )
    Spacer(modifier = Modifier.height(6.dp))
    JuntaOutlinedAction(
        text = stringResource(R.string.forget_certificate),
        onClick = onForget,
    )
}

@Composable
private fun CertificateSummaryDetails(summary: CertificateSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = stringResource(R.string.certificate_owner, summary.ownerName),
            color = JuntaInk,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.certificate_issuer, summary.issuerName),
            color = JuntaInk,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.certificate_validity,
                summary.validFrom.toDisplayDate(),
                summary.validUntil.toDisplayDate(),
            ),
            color = JuntaInk,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CertificateStateBanner(state: CertificateUiState) {
    val titleRes: Int
    val copyRes: Int
    when (state) {
        CertificateUiState.LoadingReference -> {
            titleRes = R.string.certificate_status_loading_title
            copyRes = R.string.certificate_status_loading_copy
        }
        is CertificateUiState.NoCertificate -> {
            titleRes = R.string.certificate_status_ready_title
            copyRes = R.string.certificate_status_ready_copy
        }
        is CertificateUiState.Locked -> {
            titleRes = R.string.certificate_status_locked_title
            copyRes = R.string.certificate_status_locked_copy
        }
        is CertificateUiState.Unlocking -> {
            titleRes = R.string.certificate_status_unlocking_title
            copyRes = R.string.certificate_status_unlocking_copy
        }
        is CertificateUiState.Unlocked -> {
            titleRes = R.string.certificate_status_unlocked_title
            copyRes = R.string.certificate_status_unlocked_copy
        }
    }
    JuntaStatusBanner(
        title = stringResource(titleRes),
        copy = stringResource(copyRes),
    )
}

@Composable
private fun CertificateError(error: CertificateUiError) {
    Text(
        text = error.message(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
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
