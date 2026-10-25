package dev.junta.firmamobile.certificate

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [31])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CertificateExpiryRemindersTest {
    private lateinit var context: Application
    private lateinit var alarms: AlarmManager
    private lateinit var notifications: NotificationManager
    private lateinit var references: MemoryReferenceStore
    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val expiry = Instant.parse("2026-11-23T12:00:00Z")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("certificate_expiry_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        alarms = context.getSystemService(AlarmManager::class.java)
        notifications = context.getSystemService(NotificationManager::class.java)
        shadowOf(notifications).setNotificationsEnabled(true)
        ShadowAlarmManager.setAutoSchedule(false)
        references = MemoryReferenceStore(reference())
    }

    @Test
    fun defaultIsOffAndRefreshingCannotScheduleOrNotify() = runBlocking {
        val reminders = reminders()
        assertFalse(reminders.isEnabled())
        reminders.refresh(deliver = true)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertNull(notifications.getNotificationChannel(CertificateReminderNotifications.CHANNEL_ID))
    }

    @Test
    fun repeatedSchedulingReplacesOneInexactNonWakeupAlarmWithoutCertificateExtras() = runBlocking {
        val reminders = reminders()
        assertTrue(reminders.setEnabled(true))
        repeat(3) { reminders.refresh() }
        reminders().refresh() // Process recreation must also replace, not append.
        val alarm = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(AlarmManager.RTC, alarm.type)
        assertEquals(Instant.parse("2026-10-24T00:00:00Z").toEpochMilli(), alarm.triggerAtTime)
        assertEquals(21_600_000L, alarm.windowLengthMs)
        assertTrue(alarm.operation!!.isImmutable)
        assertNull(shadowOf(alarm.operation).savedIntent.extras)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun deliveryIsOncePerWindowEvenAfterDismissalAndProcessRecreation() = runBlocking {
        reminders().setEnabled(true)
        reminders().refresh(deliver = true)
        assertEquals("Su certificado caduca en 30 días", title())
        assertEquals(
            Instant.parse("2026-11-16T00:00:00Z").toEpochMilli(),
            shadowOf(alarms).scheduledAlarms.single().triggerAtTime,
        )
        notifications.cancel(CertificateReminderNotifications.NOTIFICATION_ID)
        reminders().refresh(deliver = true)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        val sevenDays = Instant.parse("2026-11-16T12:00:00Z")
        reminders(sevenDays).refresh(deliver = true)
        assertEquals("Su certificado caduca en 7 días", title())
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        notifications.cancel(CertificateReminderNotifications.NOTIFICATION_ID)
        reminders(sevenDays).refresh(deliver = true)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun lateDeliverySkipsTheOlderWindowAndUsesActualDaysWithoutPersonalData() = runBlocking {
        reminders().setEnabled(true)
        reminders(Instant.parse("2026-11-17T12:00:00Z")).refresh(deliver = true)
        val notification = shadowOf(notifications).allNotifications.single()
        assertEquals("Su certificado caduca en 6 días", title())
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertFalse(notification.extras.toString().contains("OWNER_CANARY"))
        assertFalse(notification.extras.toString().contains("SERIAL_CANARY"))
        assertNull(shadowOf(notification.contentIntent).savedIntent.extras)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals(86_400_000L, notification.timeoutAfter)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test
    fun todayExactExpiryAndExpiredCertificatesCannotNotify() = runBlocking {
        for (at in listOf("2026-11-23T00:00:00Z", "2026-11-23T12:00:00Z", "2026-11-24T00:00:00Z")) {
            reminders(Instant.parse(at)).setEnabled(true)
            reminders(Instant.parse(at)).refresh(deliver = true)
            assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
            assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        }
    }

    @Test
    fun replacingAndDeletingTheSavedReferenceCancelStaleAlarmsAndNotifications() = runBlocking {
        val reminders = reminders()
        val store = ReminderCertificateReferenceStore(context, references, reminders)
        reminders.setEnabled(true)
        reminders.refresh(deliver = true)
        store.write(reference("content://synthetic/replacement-SERIAL_CANARY"))
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        reminders.refresh(deliver = true)
        assertEquals("Su certificado caduca en 30 días", title())
        store.clear()
        assertNull(store.read())
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        reminders.refresh(deliver = true)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun selectingAFileWithoutAnUnlockedSummaryCancelsUntilMetadataIsSaved() = runBlocking {
        val reminders = reminders()
        val store = ReminderCertificateReferenceStore(context, references, reminders)
        reminders.setEnabled(true)
        store.write(reference().copy(summary = null))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        reminders.refresh(deliver = true)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        store.write(reference())
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test
    fun disablingCancelsDeliveryAndReenablingDoesNotRepeatADeliveredWindow() = runBlocking {
        val reminders = reminders()
        reminders.setEnabled(true)
        reminders.refresh(deliver = true)
        reminders.setEnabled(false)
        assertFalse(reminders.isEnabled())
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        reminders.refresh(deliver = true)
        reminders.setEnabled(true)
        reminders.refresh(deliver = true)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertEquals(
            Instant.parse("2026-11-16T00:00:00Z").toEpochMilli(),
            shadowOf(alarms).scheduledAlarms.single().triggerAtTime,
        )
    }

    @Test
    fun channelIsQuiet() = runBlocking {
        val reminders = reminders()
        reminders.setEnabled(true)
        val channel = notifications.getNotificationChannel(CertificateReminderNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.shouldVibrate())
        assertNull(channel.sound)
    }

    @Test
    fun anExplicitlyBlockedChannelCannotBeBypassed() = runBlocking {
        notifications.createNotificationChannel(
            NotificationChannel(CertificateReminderNotifications.CHANNEL_ID, "Blocked", NotificationManager.IMPORTANCE_NONE),
        )
        val reminders = reminders()
        reminders.setEnabled(true)
        reminders.refresh(deliver = true)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun missingPostNotificationsPermissionDoesNotScheduleOrConsumeAWindow() = runBlocking {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val reminders = reminders()
        reminders.setEnabled(true)
        reminders.refresh(deliver = true)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        reminders.refresh()
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        reminders.refresh(deliver = true)
        assertEquals("Su certificado caduca en 30 días", title())
    }

    @Test
    fun aRecreatedSchedulerRestoresAnAlarmLostAtRebootWithoutRedelivery() = runBlocking {
        reminders().setEnabled(true)
        reminders().refresh(deliver = true)
        notifications.cancel(CertificateReminderNotifications.NOTIFICATION_ID)
        val pending = shadowOf(alarms).scheduledAlarms.single().operation!!
        alarms.cancel(pending) // Android drops scheduled alarms at reboot.
        reminders().refresh()
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun timezoneChangesAreReadAgainByALongLivedScheduler() = runBlocking {
        var zone: ZoneId = ZoneOffset.UTC
        val reminders = CertificateExpiryReminders(context, references, Clock.fixed(now, ZoneOffset.UTC)) { zone }
        reminders.setEnabled(true)
        zone = ZoneId.of("Europe/Madrid")
        reminders.refresh()
        assertEquals(
            Instant.parse("2026-10-23T22:00:00Z").toEpochMilli(),
            shadowOf(alarms).scheduledAlarms.single().triggerAtTime,
        )
    }

    @Test
    fun unreadableMetadataCancelsInsteadOfNotifyingOrFailingTheReferenceWrite() = runBlocking {
        val reminders = reminders()
        reminders.setEnabled(true)
        references.readFailure = true
        val store = ReminderCertificateReferenceStore(context, references, reminders)
        store.write(reference("content://synthetic/new"))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        references.readFailure = false
        assertEquals(Uri.parse("content://synthetic/new"), store.read()?.uri)
    }

    @Test
    fun anEarlyBroadcastCannotDeliverBeforeTheThresholdDate() = runBlocking {
        val reminders = reminders(Instant.parse("2026-10-23T12:00:00Z"))
        reminders.setEnabled(true)
        reminders.refresh(deliver = true)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test
    fun updatedExpiryOnTheSameSavedUriReplacesTheOldSchedule() = runBlocking {
        val reminders = reminders()
        val store = ReminderCertificateReferenceStore(context, references, reminders)
        reminders.setEnabled(true)
        reminders.refresh(deliver = true)
        val updated = reference().let { it.copy(summary = it.summary!!.copy(validUntil = Instant.parse("2026-12-23T12:00:00Z"))) }
        store.write(updated)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertEquals(
            Instant.parse("2026-11-23T00:00:00Z").toEpochMilli(),
            shadowOf(alarms).scheduledAlarms.single().triggerAtTime,
        )
    }

    @Test
    fun aConsumedUrgentWindowCannotReplayTheOlderReminderAfterAClockChange() = runBlocking {
        val urgent = reminders(Instant.parse("2026-11-16T12:00:00Z"))
        urgent.setEnabled(true)
        urgent.refresh(deliver = true)
        notifications.cancel(CertificateReminderNotifications.NOTIFICATION_ID)
        reminders(Instant.parse("2026-10-01T12:00:00Z")).refresh(deliver = true)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test
    fun remindersFollowOnlyTheActiveCatalogueEntry() = runBlocking {
        val reminders = reminders()
        val store = ReminderCertificateReferenceStore(context, references, reminders)
        reminders.setEnabled(true)
        val first = checkNotNull(store.readCatalog().active)
        val second = store.add(reference("content://synthetic/second").copy(summary = null))
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        assertFalse(store.selectEntry(second.id, second.revision + 1))
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        assertTrue(store.selectEntry(second.id, second.revision))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(store.selectEntry(first.id, first.revision))
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        store.remove(second)
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        // Replacing the active entry reconciles too, as forgetting its source file would.
        val withoutSummary = first.copy(revision = first.revision + 1, reference = first.reference?.copy(summary = null))
        store.replace(first, withoutSummary)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        val restored = first.copy(revision = first.revision + 2)
        store.replace(withoutSummary, restored)
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        store.remove(restored)
        assertNull(store.read())
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        reminders.refresh(deliver = true)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    private fun reminders(at: Instant = now) = CertificateExpiryReminders(
        context, references, Clock.fixed(at, ZoneOffset.UTC), { ZoneOffset.UTC },
    )

    private fun title(): String = shadowOf(notifications).allNotifications.single()
        .extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun reference(uri: String = "content://synthetic/SERIAL_CANARY") = StoredCertificateReference(
        Uri.parse(uri), "synthetic.p12", "application/x-pkcs12", 4096,
        CertificateSummary("OWNER_CANARY", "ISSUER_CANARY", Instant.parse("2025-01-01T00:00:00Z"), expiry),
    )

    /** In-memory catalogue with the same conditional semantics as the DataStore one. */
    private class MemoryReferenceStore(initial: StoredCertificateReference?) : CertificateReferenceCatalogStore {
        var readFailure = false
        private var catalog = StoredCertificateCatalog(emptyList(), null)

        init {
            initial?.let { catalog = entry(it).let { first -> StoredCertificateCatalog(listOf(first), first.id) } }
        }

        override suspend fun read(): StoredCertificateReference? {
            if (readFailure) throw IOException("Synthetic unavailable metadata")
            return catalog.active?.reference
        }
        override suspend fun readCatalog() = catalog
        override suspend fun add(reference: StoredCertificateReference) = entry(reference).also {
            catalog = catalog.copy(entries = catalog.entries + it)
        }
        override suspend fun selectEntry(id: String, revision: Long): Boolean =
            catalog.entries.any { it.id == id && it.revision == revision && it.reference != null }
                .also { if (it) catalog = catalog.copy(activeId = id) }
        override suspend fun replace(expected: StoredCertificateEntry, replacement: StoredCertificateEntry) {
            check(expected in catalog.entries)
            catalog = catalog.copy(entries = catalog.entries.map { if (it == expected) replacement else it })
        }
        override suspend fun remove(expected: StoredCertificateEntry, onlyIfInactive: Boolean) {
            check(expected in catalog.entries && !(onlyIfInactive && catalog.activeId == expected.id))
            catalog = catalog.copy(
                entries = catalog.entries - expected,
                activeId = catalog.activeId.takeUnless { it == expected.id },
            )
        }
        override suspend fun acknowledgeKeyDeletion(alias: String) = Unit
        override suspend fun write(reference: StoredCertificateReference) {
            val active = catalog.active
            catalog = if (active == null) {
                entry(reference).let { StoredCertificateCatalog(catalog.entries + it, it.id) }
            } else {
                catalog.copy(entries = catalog.entries.map { if (it.id == active.id) it.copy(reference = reference) else it })
            }
        }
        override suspend fun clear() {
            catalog = StoredCertificateCatalog(catalog.entries.filterNot { it.id == catalog.activeId }, null)
        }

        private fun entry(reference: StoredCertificateReference) = CertificateReferenceCatalogCodec.entry(reference)
    }
}
