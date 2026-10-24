package dev.junta.firmamobile.certificate

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Local presentation state only; never reads a PKCS#12 or unlocks a certificate. */
internal class CertificateExpiryReminders(
    context: Context,
    private val references: CertificateReferenceStore = PreferencesCertificateReferenceStore(
        context.applicationContext.certificateReferenceDataStore,
    ),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val notifications = CertificateReminderNotifications(context.applicationContext)

    fun isEnabled(): Boolean = preferences.getBoolean(ENABLED, false)

    suspend fun setEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved = preferences.edit().putBoolean(ENABLED, enabled).commit()
            if (saved) {
                refreshSafely(deliver = false)
            } else {
                preferences.edit().putBoolean(ENABLED, false).apply()
                cancel()
            }
            saved
        }
    }

    suspend fun refresh(deliver: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock { refreshSafely(deliver) }
    }

    // Serialize reference writes/deletion with delivery, not just their asynchronous UI effects.
    suspend fun changeReference(change: suspend () -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            change()
            refreshSafely(deliver = false)
        }
    }

    private suspend fun refreshSafely(deliver: Boolean) {
        try {
            reconcile(deliver)
        } catch (_: IOException) {
            cancel() // A metadata/storage failure must not notify or fail certificate selection.
        } catch (_: SecurityException) {
            cancel() // Permission may be revoked between checking and notifying.
        }
    }

    private suspend fun reconcile(deliver: Boolean) {
        val reference = references.read()
        val validUntil = reference?.summary?.validUntil
        val key = reference?.takeIf { validUntil != null }?.let(::referenceKey)
        if (preferences.getString(REFERENCE_KEY, null) != key) {
            persist(preferences.edit().putString(REFERENCE_KEY, key).remove(DELIVERED))
            cancel()
        }
        if (!isEnabled() || validUntil == null) {
            cancel()
            return
        }
        notifications.ensureChannel()
        if (!notifications.available()) {
            cancel()
            return
        }
        val now = clock.instant()
        val zone = zoneId()
        var delivered = preferences.getStringSet(DELIVERED, emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }.toSet()
        val next = CertificateExpiryReminderPolicy.next(validUntil, now, zone, delivered)
        if (deliver && next != null && !now.isBefore(next.at)) {
            // Persist before notify: repeated broadcasts/process recreation cannot duplicate a window.
            delivered = delivered + next.thresholdDays
            persist(preferences.edit().putStringSet(DELIVERED, delivered.map { it.toString() }.toSet()))
            val days = CertificateExpirationPolicy.warning(validUntil, now, zone)?.daysRemaining ?: 0L
            notifications.show(days.toInt())
        }
        scheduleNext(validUntil, now, zone, delivered)
    }

    private fun scheduleNext(validUntil: Instant, now: Instant, zone: ZoneId, delivered: Set<Int>) {
        val next = CertificateExpiryReminderPolicy.next(validUntil, now, zone, delivered)
        if (next == null) notifications.cancelAlarm() else notifications.schedule(next.at)
    }

    private fun cancel() {
        notifications.cancelAlarm()
        notifications.clearNotification()
    }

    private fun persist(editor: SharedPreferences.Editor) {
        if (!editor.commit()) throw IOException("Reminder state could not be persisted")
    }

    private fun referenceKey(reference: StoredCertificateReference): String {
        val input = "${reference.uri}\u0000${reference.summary?.validUntil}".toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(input))
    }

    private companion object {
        // All instances, including a receiver racing a certificate replacement, share this lock.
        val mutex = Mutex()
        const val PREFERENCES = "certificate_expiry_reminders"
        const val ENABLED = "enabled"
        const val REFERENCE_KEY = "reference_key"
        const val DELIVERED = "delivered"
    }
}
