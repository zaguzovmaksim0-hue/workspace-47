package dev.junta.firmamobile.ui.offlineverification

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
internal fun OfflineVerificationEntry(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("open-offline-verification")) {
        Text(stringResource(R.string.offline_verification_entry))
    }
}
