package dev.junta.firmamobile.ui

import android.os.Build
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R

// Compose entry points use PascalCase.
@Suppress("FunctionNaming")
@Composable
internal fun SavedCertificateUnlockAction(onRestore: (() -> Unit)?, enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Text(stringResource(R.string.certificate_auth_help))
        if (onRestore != null) OutlinedButton(onClick = onRestore, enabled = enabled) {
            Text(stringResource(R.string.certificate_restore_saved))
        }
    } else {
        Text(stringResource(R.string.certificate_auth_unsupported))
    }
}
