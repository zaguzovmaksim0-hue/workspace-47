package dev.junta.firmamobile.ui.offlineverification

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.JuntaTone
import dev.junta.firmamobile.ui.JuntaToolTile

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
internal fun OfflineVerificationEntry(onClick: () -> Unit) {
    JuntaToolTile(
        iconRes = R.drawable.ic_ui_verify,
        title = stringResource(R.string.offline_verification_entry),
        onClick = onClick,
        tone = JuntaTone.MINT,
        modifier = Modifier.testTag("open-offline-verification"),
    )
}
