package dev.junta.firmamobile.certificate

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.junta.firmamobile.MainActivity
import dev.junta.firmamobile.R
import java.time.Instant

/** A single replaceable, inexact, non-wakeup alarm. No certificate data enters its intent. */
internal class CertificateReminderNotifications(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.certificate_reminder_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            },
        )
    }

    fun available(): Boolean = hasPermission() && notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(at: Instant) {
        alarms.setWindow(
            AlarmManager.RTC,
            at.toEpochMilli(),
            DELIVERY_WINDOW_MILLIS,
            checkNotNull(alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT)),
        )
    }

    fun cancelAlarm() {
        alarmIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    fun clearNotification() = notifications.cancel(NOTIFICATION_ID)

    fun show(daysRemaining: Int): Boolean {
        // Keep the platform permission check at the actual notify call for Android lint, too.
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED) ||
            !available()
        ) return false
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_certificate_reminder)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.certificate_reminder_days,
                    daysRemaining,
                    daysRemaining,
                ),
            )
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MILLIS)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun alarmIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CertificateReminderReceiver::class.java).setAction(CertificateReminderReceiver.ACTION_REMIND),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "certificate_expiry_reminders"
        const val NOTIFICATION_ID = 4704
        private const val NOTIFICATION_TIMEOUT_MILLIS = 24 * 60 * 60 * 1000L
        private const val DELIVERY_WINDOW_MILLIS = 6 * 60 * 60 * 1000L
    }
}
