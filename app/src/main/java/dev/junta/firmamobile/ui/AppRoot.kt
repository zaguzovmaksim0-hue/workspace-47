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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            JuntaBrandHeader()
            Spacer(modifier = Modifier.height(20.dp))
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
            if (state !is CertificateUiState.Unlocked) {
                Spacer(modifier = Modifier.height(12.dp))
                JuntaOutlinedAction(
                    text = stringResource(R.string.browse_without_certificate),
                    onClick = onContinue,
                    modifier = Modifier.testTag("browse-without-certificate"),
                    iconRes = R.drawable.ic_ui_globe,
                )
            }
            HomeToolSections(
                unlocked = state is CertificateUiState.Unlocked,
                journal = verificationJournal,
                onOpenLegalInformation = { legalInformationVisible = true },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Grouped secondary destinations: tools first, then settings and legal information. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun HomeToolSections(
    unlocked: Boolean,
    journal: InMemoryOperationJournal,
    onOpenLegalInformation: () -> Unit,
) {
    JuntaSectionLabel(stringResource(R.string.home_tools_title))
    JuntaListPanel {
        localFileSigningEntry(unlocked)
        JuntaListDivider()
        verificationJournalHome(journal)
    }
    JuntaSectionLabel(stringResource(R.string.home_settings_title))
    JuntaListPanel {
        certificateReminderSettings()
        JuntaListDivider()
        JuntaToolRow(
            iconRes = R.drawable.ic_ui_info,
            title = stringResource(R.string.legal_information),
            onClick = onOpenLegalInformation,
            modifier = Modifier.testTag("open-legal-information"),
        )
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
        CertificatePanelHeader(state = state, onLock = onLock)
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = JuntaHairline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

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

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificatePanelHeader(state: CertificateUiState, onLock: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_certificate),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.certificate_title),
                color = JuntaInk,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (state is CertificateUiState.NoCertificate) {
                Text(
                    text = stringResource(R.string.certificate_copy),
                    color = JuntaMutedInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state is CertificateUiState.Unlocked) CertificateOptionsMenu(state, onLock)
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ui_lock),
            contentDescription = null,
            tint = JuntaMutedInk,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.privacy_copy),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateOptionsMenu(state: CertificateUiState.Unlocked, onLock: () -> Unit) {
    var expanded by remember(state.reference.uri) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_browser_more),
                contentDescription = stringResource(R.string.certificate_options),
                tint = JuntaMutedInk,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = JuntaFieldShape,
            containerColor = JuntaPaperElevated,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.lock_certificate)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_ui_lock), contentDescription = null)
                },
                onClick = { expanded = false; onLock() },
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
    LocalSavedCertificates.current?.state?.activeKey?.signingKey?.let { selected ->
        Text(
            stringResource(R.string.certificate_selected, selected.displayName),
            color = JuntaInk,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("selected-keystore-certificate"),
        )
        Text(
            stringResource(R.string.saved_keystore_locked),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
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
    val passwordState = remember(state.reference.uri) { TextFieldState() }
    DisposableEffect(passwordState) {
        onDispose { passwordState.clearText() }
    }
    if (state.revocationPending) {
        Text(
            stringResource(R.string.certificate_revoking),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    val passwordLabel = stringResource(R.string.certificate_password)
    OutlinedSecureTextField(
        state = passwordState,
        label = { Text(passwordLabel) },
        shape = JuntaFieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = JuntaTeal,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = passwordLabel },
    )
    Spacer(modifier = Modifier.height(10.dp))
    CertificateUnlockRetentionSelector(retention, !state.revocationPending) { retention = it }
    Spacer(modifier = Modifier.height(12.dp))
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
    SavedCertificateUnlockAction(onRestoreSavedUnlock, !state.revocationPending)
    Spacer(modifier = Modifier.height(12.dp))
    CertificateSecondaryActions(onSelectCertificate = onSelectCertificate, onForget = onForget)
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
    Spacer(modifier = Modifier.height(14.dp))
}

/** Change or remove the current file; removal is styled as the quieter, destructive choice. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateSecondaryActions(onSelectCertificate: () -> Unit, onForget: () -> Unit) {
    JuntaOutlinedAction(
        text = stringResource(R.string.choose_another_certificate),
        onClick = onSelectCertificate,
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextButton(
        onClick = onForget,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(
            text = stringResource(R.string.forget_certificate),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun UnlockingCertificate(state: CertificateUiState.Unlocking) {
    Text(
        text = stringResource(R.string.certificate_selected, certificateDisplayNameText(state.reference.displayName)),
        color = JuntaInk,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = JuntaTeal,
            modifier = Modifier.size(24.dp),
            strokeWidth = 3.dp,
        )
        Text(text = stringResource(R.string.certificate_unlocking), color = JuntaMutedInk)
    }
}

@Composable
private fun UnlockedCertificate(
    state: CertificateUiState.Unlocked,
    onContinue: () -> Unit,
    onSelectCertificate: () -> Unit,
    onForget: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ui_check_circle),
            contentDescription = null,
            tint = JuntaTeal,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(R.string.certificate_found),
            color = JuntaTeal,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    CertificateSummaryDetails(state.summary)
    Spacer(modifier = Modifier.height(16.dp))
    JuntaPrimaryButton(
        text = stringResource(R.string.continue_to_portal),
        onClick = onContinue,
    )
    Spacer(modifier = Modifier.height(8.dp))
    CertificateSecondaryActions(onSelectCertificate = onSelectCertificate, onForget = onForget)
}

@Composable
private fun CertificateSummaryDetails(summary: CertificateSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
    }
    CertificateExpirationNotice(
        validUntil = summary.validUntil,
        modifier = Modifier.padding(top = 8.dp),
    )
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
