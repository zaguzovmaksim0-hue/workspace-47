package dev.junta.firmamobile.ui.offlineverification

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.JuntaFieldShape
import dev.junta.firmamobile.verification.OfflineDocumentVerification

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
internal fun OfflineVerificationContent(
    state: OfflineVerificationState,
    controller: OfflineVerificationController,
    onPickCms: () -> Unit,
    onPickOriginal: () -> Unit,
) {
    val hasResult = state.document != null || state.outcome != null
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.offline_verification_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        OfflineVerificationInputs(state, onPickCms, onPickOriginal, controller::clearOriginal)
        if (state.busy) {
            Text(
                stringResource(R.string.offline_verification_progress),
                modifier = Modifier.testTag("offline-verification-progress").semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }
        OfflineVerificationResult(state)
        // A result is only meaningful together with what the local check does not prove.
        if (hasResult) OfflineVerificationNote(stringResource(R.string.signature_verification_scope))
        if (state.hasSelection || state.busy || state.picking != null) {
            TextButton(onClick = controller::cancel, modifier = Modifier.testTag("offline-verification-cancel")) {
                Text(stringResource(R.string.offline_verification_cancel))
            }
        }
        OfflineVerificationDetails(showScope = !hasResult)
    }
}

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
private fun OfflineVerificationDetails(showScope: Boolean) {
    var visible by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { visible = !visible },
        modifier = Modifier.testTag("offline-verification-details"),
    ) {
        Text(
            stringResource(
                if (visible) R.string.more_information_hide else R.string.more_information_show,
            ),
        )
    }
    if (visible) {
        if (showScope) OfflineVerificationNote(stringResource(R.string.signature_verification_scope))
        OfflineVerificationNote(stringResource(R.string.signature_verification_limits))
        OfflineVerificationNote(stringResource(R.string.offline_verification_original_hint))
        OfflineVerificationNote(stringResource(R.string.offline_verification_cancellation_note))
    }
}

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
private fun OfflineVerificationNote(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
private fun OfflineVerificationInputs(
    state: OfflineVerificationState,
    onPickCms: () -> Unit,
    onPickOriginal: () -> Unit,
    onClearOriginal: () -> Unit,
) {
    val cmsLabel = if (state.cmsSelected) {
        R.string.offline_verification_cms_selected
    } else {
        R.string.offline_verification_cms_missing
    }
    OfflineFileChoice(
        action = stringResource(R.string.signature_verification_pick),
        status = stringResource(cmsLabel),
        selected = state.cmsSelected,
        enabled = state.picking == null,
        onClick = onPickCms,
        testTag = "offline-verification-pick-cms",
    )
    val originalLabel = if (state.originalSelected) {
        R.string.offline_verification_original_selected
    } else {
        R.string.offline_verification_original_missing
    }
    OfflineFileChoice(
        action = stringResource(R.string.offline_verification_pick_original),
        status = stringResource(originalLabel),
        selected = state.originalSelected,
        enabled = state.picking == null,
        onClick = onPickOriginal,
        testTag = "offline-verification-pick-original",
    )
    if (state.originalSelected) {
        TextButton(
            onClick = onClearOriginal,
            enabled = state.picking == null,
            modifier = Modifier.testTag("offline-verification-clear-original"),
        ) { Text(stringResource(R.string.offline_verification_clear_original)) }
    }
}

@Suppress("LongParameterList", "FunctionNaming")
@Composable
private fun OfflineFileChoice(
    action: String,
    status: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = JuntaFieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(testTag),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_document),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = action, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_ui_check_circle),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text = status, color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
private fun OfflineVerificationResult(state: OfflineVerificationState) {
    state.document?.let { document ->
        offlineDocumentResult(document)
        return
    }
    val outcome = state.outcome ?: return
    val verified = outcome == OfflineVerificationOutcome.VERIFIED
    OfflineResultSurface(positive = verified) {
        Text(
            text = stringResource(offlineOutcomeLabel(outcome)),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("offline-verification-result").semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (verified) {
            Text(
                stringResource(R.string.offline_verification_signer_count, state.verifiedSigners),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
private fun OfflineResultSurface(positive: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val container: Color
    val onContainer: Color
    if (positive) {
        container = MaterialTheme.colorScheme.primaryContainer
        onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        container = MaterialTheme.colorScheme.errorContainer
        onContainer = MaterialTheme.colorScheme.onErrorContainer
    }
    CompositionLocalProvider(LocalContentColor provides onContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, JuntaFieldShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@StringRes
private fun offlineOutcomeLabel(outcome: OfflineVerificationOutcome): Int = when (outcome) {
    OfflineVerificationOutcome.VERIFIED -> R.string.offline_verification_verified
    OfflineVerificationOutcome.INVALID -> R.string.signature_verification_invalid
    OfflineVerificationOutcome.UNSUPPORTED -> R.string.signature_verification_unsupported
    OfflineVerificationOutcome.LIMIT_EXCEEDED -> R.string.signature_verification_limit_exceeded
    OfflineVerificationOutcome.DETACHED_CONTENT_REQUIRED -> R.string.offline_verification_detached_required
    OfflineVerificationOutcome.READ_FAILED -> R.string.offline_verification_read_failed
}

@Composable
private fun offlineDocumentResult(document: OfflineDocumentVerification) {
    val positive = document.signatures.isNotEmpty() && !document.unsignedChanges &&
        document.signatures.all { it.verified }
    Column(modifier = Modifier.testTag("offline-verification-document-result").semantics {
        liveRegion = LiveRegionMode.Polite
    }) {
        OfflineResultSurface(positive = positive) {
            if (document.signatures.isEmpty()) Text(stringResource(R.string.signature_verification_no_signatures))
            if (document.unsignedChanges) Text(
                stringResource(R.string.signature_verification_unsigned_changes),
                fontWeight = FontWeight.Medium,
            )
            document.signatures.forEachIndexed { index, signature ->
                val result = if (signature.verified) {
                    R.string.signature_verification_valid
                } else {
                    R.string.signature_verification_invalid
                }
                Text(
                    stringResource(R.string.signature_verification_signer, index + 1, signature.signer),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.signature_verification_date,
                        signature.claimedTime ?: stringResource(R.string.signature_verification_unknown_time)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(result), style = MaterialTheme.typography.bodySmall)
                if (signature.laterChanges) {
                    Text(
                        stringResource(R.string.signature_verification_later_changes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
