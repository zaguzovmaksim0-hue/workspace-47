package dev.junta.firmamobile.certificate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class CertificateReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in ACTIONS) return
        val reminders = CertificateExpiryReminders(context.applicationContext)
        if (!reminders.isEnabled()) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MILLIS) { reminders.refresh(deliver = action == ACTION_REMIND) }
            } finally {
                result.finish()
            }
        }
    }

    internal companion object {
        const val ACTION_REMIND = "dev.junta.firmamobile.CERTIFICATE_EXPIRY_REMINDER"
        private const val RECEIVER_TIMEOUT_MILLIS = 8_000L
        private val ACTIONS = setOf(
            ACTION_REMIND,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
