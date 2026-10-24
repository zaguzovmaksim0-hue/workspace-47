package dev.junta.firmamobile.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateExpiryReminders
import dev.junta.firmamobile.certificate.CertificateReminderNotifications
import kotlinx.coroutines.launch

@Composable
internal fun certificateReminderSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember(context) { ReminderSettingsState(context) }
    var visible by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { settings.save(granted, denied = !granted) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { settings.refresh() } }
    TextButton(
        onClick = { visible = true },
        modifier = Modifier.fillMaxWidth().testTag("open-certificate-reminder-settings"),
    ) { Text(stringResource(R.string.certificate_reminder_settings)) }
    if (visible) certificateReminderSettingsDialog(
        state = settings.state,
        onToggle = { enabled ->
            // The only permission-launch path is this explicit switch action, never composition/startup.
            if (enabled && settings.needsPermission()) {
                settings.waitingForPermission()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scope.launch { settings.save(enabled) }
            }
        },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onDismiss = { visible = false },
    )
}

internal data class CertificateReminderSettingsUiState(
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val blocked: Boolean = false,
    val failed: Boolean = false,
)

@Composable
internal fun certificateReminderSettingsDialog(
    state: CertificateReminderSettingsUiState,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val label = stringResource(R.string.certificate_reminder_toggle)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.certificate_reminder_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.certificate_reminder_copy))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onToggle,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("certificate-reminder-toggle")
                            .semantics { contentDescription = label },
                    )
                }
                if (state.blocked) {
                    Text(stringResource(R.string.certificate_reminder_blocked))
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.certificate_reminder_system_settings))
                    }
                }
                if (state.failed) Text(
                    stringResource(R.string.certificate_reminder_save_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.certificate_reminder_done)) }
        },
    )
}

private class ReminderSettingsState(context: Context) {
    private var permissionDenied = false
    private val reminders = CertificateExpiryReminders(context.applicationContext)
    private val notifications = CertificateReminderNotifications(context.applicationContext)
    var state by mutableStateOf(CertificateReminderSettingsUiState(enabled = reminders.isEnabled()))
        private set

    fun needsPermission(): Boolean {
        notifications.ensureChannel()
        return !notifications.hasPermission()
    }

    fun waitingForPermission() {
        state = state.copy(busy = true)
    }

    suspend fun save(enabled: Boolean, denied: Boolean = false) {
        state = state.copy(busy = true)
        permissionDenied = denied
        val saved = reminders.setEnabled(enabled)
        state = CertificateReminderSettingsUiState(
            enabled = reminders.isEnabled(),
            blocked = denied || (reminders.isEnabled() && !notifications.available()),
            failed = !saved,
        )
    }

    suspend fun refresh() {
        reminders.refresh()
        state = state.copy(
            enabled = reminders.isEnabled(),
            blocked = permissionDenied || (reminders.isEnabled() && !notifications.available()),
        )
    }
}
