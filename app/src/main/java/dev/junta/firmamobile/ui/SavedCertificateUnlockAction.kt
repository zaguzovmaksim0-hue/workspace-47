package dev.junta.firmamobile.ui

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaMutedInk

// Compose entry points use PascalCase.
@Suppress("FunctionNaming")
@Composable
internal fun SavedCertificateUnlockAction(onRestore: (() -> Unit)?, enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (onRestore != null) {
            Spacer(modifier = Modifier.height(8.dp))
            JuntaOutlinedAction(
                text = stringResource(R.string.certificate_restore_saved),
                onClick = onRestore,
                enabled = enabled,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.certificate_auth_help),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.certificate_auth_unsupported),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
