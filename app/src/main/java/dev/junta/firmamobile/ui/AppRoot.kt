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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.ui.history.verificationJournalHome
import dev.junta.firmamobile.certificate.CertificateUnlockRetention
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
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit = { _, _ -> },
    onRestoreSavedUnlock: (() -> Unit)? = null,
    onLock: () -> Unit = {},
    onForget: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    var legalInformationVisible by remember { mutableStateOf(false) }
    if (legalInformationVisible) LegalInformationDialog(onDismiss = { legalInformationVisible = false })
    val verificationJournal = remember { InMemoryOperationJournal() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JuntaPaper),
    ) {
        Image(
            painter = painterResource(R.drawable.jfm_home_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag("jfm-home-background"),
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
                onRestoreSavedUnlock = onRestoreSavedUnlock,
                onLock = onLock,
                onForget = onForget,
                onContinue = onContinue,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CertificateStateBanner(state)
            localFileSigningEntry(state is CertificateUiState.Unlocked)
            if (state !is CertificateUiState.Unlocked) {
                androidx.compose.material3.TextButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().testTag("browse-without-certificate"),
                ) {
                    Text(stringResource(R.string.browse_without_certificate))
                }
            }
            certificateReminderSettings()
            androidx.compose.material3.TextButton(
                onClick = { legalInformationVisible = true },
                modifier = Modifier.fillMaxWidth().testTag("open-legal-information"),
            ) {
                Text(stringResource(R.string.legal_information))
            }
            verificationJournalHome(verificationJournal)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CertificatePanel(
    state: CertificateUiState,
    onSelectCertificate: () -> Unit,
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit,
    onRestoreSavedUnlock: (() -> Unit)?,
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
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state is CertificateUiState.Unlocked) {
                var expanded by remember(state.reference.uri) { mutableStateOf(false) }
                Box {
                    androidx.compose.material3.IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_browser_more),
                            contentDescription = stringResource(R.string.certificate_options), tint = JuntaTeal)
                    }
                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.lock_certificate)) },
                            onClick = { expanded = false; onLock() },
                        )
                    }
                }
            }
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
            is CertificateUiState.Locked -> if (state.choices.isNotEmpty()) {
                certificateEntryChooser(state)
            } else {
                LockedCertificate(
                    state = state,
                    onUnlock = onUnlock,
                    onRestoreSavedUnlock = onRestoreSavedUnlock,
                    onSelectCertificate = onSelectCertificate,
                    onForget = onForget,
                )
            }
            is CertificateUiState.Unlocking -> UnlockingCertificate(state)
            is CertificateUiState.Unlocked -> UnlockedCertificate(
                state = state,
                onContinue = onContinue,
                onSelectCertificate = onSelectCertificate,
                onForget = onForget,
            )
        }
        savedCertificatesSection(state)
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
    LocalSavedCertificates.current?.state?.activeKey?.signingKey?.let { selected ->
        Text(
            stringResource(R.string.certificate_selected, selected.displayName),
            color = JuntaInk,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("selected-keystore-certificate"),
        )
        Text(stringResource(R.string.saved_keystore_locked), color = JuntaMutedInk)
        Spacer(modifier = Modifier.height(8.dp))
    }
    JuntaPrimaryButton(
        text = stringResource(R.string.select_certificate),
        onClick = onSelectCertificate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockedCertificate(
    state: CertificateUiState.Locked,
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit,
    onRestoreSavedUnlock: (() -> Unit)?,
    onSelectCertificate: () -> Unit,
    onForget: () -> Unit,
) {
    LockedCertificateHeading(state)
    var retention by remember(state.reference.uri) { mutableStateOf(CertificateUnlockRetention.FIFTEEN_MINUTES) }
    CertificateUnlockRetentionSelector(retention, !state.revocationPending) { retention = it }
    SavedCertificateUnlockAction(onRestoreSavedUnlock, !state.revocationPending)
    val passwordState = remember(state.reference.uri) { TextFieldState() }
    DisposableEffect(passwordState) {
        onDispose { passwordState.clearText() }
    }
    if (state.revocationPending) {
        Text(stringResource(R.string.certificate_revoking), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(modifier = Modifier.height(8.dp))
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
            onUnlock(password, retention)
        },
        enabled = passwordState.text.isNotEmpty() && !state.revocationPending,
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

// Compose UI entry points use PascalCase by convention.
@Suppress("FunctionNaming")
@Composable
private fun LockedCertificateHeading(state: CertificateUiState.Locked) {
    Text(
        text = stringResource(
            R.string.certificate_selected,
            certificateDisplayNameText(state.reference.displayName),
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
}

@Composable
private fun UnlockingCertificate(state: CertificateUiState.Unlocking) {
    Text(
        text = stringResource(R.string.certificate_selected, certificateDisplayNameText(state.reference.displayName)),
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
        text = stringResource(R.string.forget_certificate),
        onClick = onForget,
    )
}

@Composable
private fun CertificateSummaryDetails(summary: CertificateSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = stringResource(R.string.certificate_owner, certificateDisplayNameText(summary.ownerName)),
            color = JuntaInk,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.certificate_issuer, certificateDisplayNameText(summary.issuerName)),
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
        CertificateExpirationNotice(validUntil = summary.validUntil)
    }
}

@Composable
private fun CertificateStateBanner(state: CertificateUiState) {
    val selectedKey = LocalSavedCertificates.current?.state?.activeKey != null
    val titleRes: Int
    val copyRes: Int
    when (state) {
        CertificateUiState.LoadingReference -> {
            titleRes = R.string.certificate_status_loading_title
            copyRes = R.string.certificate_status_loading_copy
        }
        is CertificateUiState.NoCertificate -> {
            titleRes = if (selectedKey) R.string.certificate_status_locked_title
                else R.string.certificate_status_ready_title
            copyRes = if (selectedKey) R.string.saved_keystore_locked else R.string.certificate_status_ready_copy
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
        CertificateUiError.INVALID_PASSWORD -> R.string.error_invalid_password
        CertificateUiError.INVALID_PKCS12_FILE -> R.string.error_invalid_pkcs12_file
        CertificateUiError.UNSUPPORTED_PKCS12_ENCRYPTION -> R.string.error_unsupported_pkcs12_encryption
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
        CertificateUiError.CATALOG_FULL -> R.string.error_catalog_full
        CertificateUiError.ALREADY_SAVED -> R.string.error_already_saved
        CertificateUiError.STORAGE_FAILURE -> R.string.error_storage
        CertificateUiError.CERTIFICATE_NOT_SELECTED -> R.string.error_not_selected
        CertificateUiError.CERTIFICATE_NOT_USABLE -> R.string.error_not_usable
    },
)

private fun Instant.toDisplayDate(): String = DATE_FORMATTER.format(this)

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    .withZone(ZoneId.systemDefault())
