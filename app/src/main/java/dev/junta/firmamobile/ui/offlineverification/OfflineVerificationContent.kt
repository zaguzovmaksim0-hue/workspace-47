package dev.junta.firmamobile.ui.offlineverification

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
internal fun OfflineVerificationContent(
    state: OfflineVerificationState,
    controller: OfflineVerificationController,
    onPickCms: () -> Unit,
    onPickOriginal: () -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.offline_verification_scope))
        Text(stringResource(R.string.offline_verification_limits))
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
        if (state.hasSelection || state.busy || state.picking != null) {
            TextButton(onClick = controller::cancel, modifier = Modifier.testTag("offline-verification-cancel")) {
                Text(stringResource(R.string.offline_verification_cancel))
            }
        }
        Text(stringResource(R.string.offline_verification_cancellation_note))
    }
}

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
private fun OfflineVerificationInputs(
    state: OfflineVerificationState,
    onPickCms: () -> Unit,
    onPickOriginal: () -> Unit,
    onClearOriginal: () -> Unit,
) {
    TextButton(
        onClick = onPickCms,
        enabled = state.picking == null,
        modifier = Modifier.testTag("offline-verification-pick-cms"),
    ) { Text(stringResource(R.string.offline_verification_pick_cms)) }
    val cmsLabel = if (state.cmsSelected) {
        R.string.offline_verification_cms_selected
    } else {
        R.string.offline_verification_cms_missing
    }
    Text(stringResource(cmsLabel))
    Text(stringResource(R.string.offline_verification_original_hint))
    TextButton(
        onClick = onPickOriginal,
        enabled = state.picking == null,
        modifier = Modifier.testTag("offline-verification-pick-original"),
    ) { Text(stringResource(R.string.offline_verification_pick_original)) }
    val originalLabel = if (state.originalSelected) {
        R.string.offline_verification_original_selected
    } else {
        R.string.offline_verification_original_missing
    }
    Text(stringResource(originalLabel))
    if (state.originalSelected) {
        TextButton(
            onClick = onClearOriginal,
            enabled = state.picking == null,
            modifier = Modifier.testTag("offline-verification-clear-original"),
        ) { Text(stringResource(R.string.offline_verification_clear_original)) }
    }
}

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
private fun OfflineVerificationResult(state: OfflineVerificationState) {
    val outcome = state.outcome ?: return
    val verified = outcome == OfflineVerificationOutcome.VERIFIED
    Text(
        text = stringResource(offlineOutcomeLabel(outcome)),
        color = if (verified) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("offline-verification-result").semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (verified) Text(stringResource(R.string.offline_verification_signer_count, state.verifiedSigners))
}

@StringRes
private fun offlineOutcomeLabel(outcome: OfflineVerificationOutcome): Int = when (outcome) {
    OfflineVerificationOutcome.VERIFIED -> R.string.offline_verification_verified
    OfflineVerificationOutcome.INVALID -> R.string.offline_verification_invalid
    OfflineVerificationOutcome.UNSUPPORTED -> R.string.offline_verification_unsupported
    OfflineVerificationOutcome.LIMIT_EXCEEDED -> R.string.offline_verification_limit_exceeded
    OfflineVerificationOutcome.DETACHED_CONTENT_REQUIRED -> R.string.offline_verification_detached_required
    OfflineVerificationOutcome.READ_FAILED -> R.string.offline_verification_read_failed
}
