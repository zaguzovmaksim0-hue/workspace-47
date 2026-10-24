package dev.junta.firmamobile.ui

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import dev.junta.firmamobile.certificate.CertificateExpiryReminders
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CertificateReminderSettingsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun settingIsOffAndNoActionIsSentUntilTheUserToggles() {
        val changes = mutableListOf<Boolean>()
        rule.setContent {
            JuntaFirmaTheme {
                certificateReminderSettingsDialog(CertificateReminderSettingsUiState(), { changes += it }, {}, {})
            }
        }
        rule.onNodeWithTag("certificate-reminder-toggle").assertIsOff()
        assertEquals(emptyList<Boolean>(), changes)
        rule.onNodeWithTag("certificate-reminder-toggle").performClick()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun permissionOrPersistenceInProgressDisablesTheSwitch() {
        rule.setContent {
            JuntaFirmaTheme {
                certificateReminderSettingsDialog(CertificateReminderSettingsUiState(busy = true), {}, {}, {})
            }
        }
        rule.onNodeWithTag("certificate-reminder-toggle").assertIsNotEnabled()
    }

    @Test
    fun blockedNotificationsHaveAnExplicitSystemSettingsAction() {
        var opened = 0
        rule.setContent {
            JuntaFirmaTheme {
                certificateReminderSettingsDialog(
                    CertificateReminderSettingsUiState(blocked = true), {}, { opened++ }, {},
                )
            }
        }
        rule.onNodeWithText("Abrir ajustes de notificaciones").performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(sdk = [33])
    fun api33RequestsPermissionOnlyOnExplicitOptInAndDenialLeavesRemindersOff() {
        val context = RuntimeEnvironment.getApplication<Application>()
        context.getSharedPreferences("certificate_expiry_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val requested = mutableListOf<Any?>()
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    requested += input
                    dispatchResult(requestCode, false)
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                JuntaFirmaTheme { certificateReminderSettings() }
            }
        }
        rule.waitForIdle()
        assertEquals(emptyList<Any?>(), requested)
        rule.onNodeWithTag("open-certificate-reminder-settings").performClick()
        rule.onNodeWithTag("certificate-reminder-toggle").assertIsOff()
        assertEquals(emptyList<Any?>(), requested)
        rule.onNodeWithTag("certificate-reminder-toggle").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requested)
        assertFalse(CertificateExpiryReminders(context).isEnabled())
    }
}
