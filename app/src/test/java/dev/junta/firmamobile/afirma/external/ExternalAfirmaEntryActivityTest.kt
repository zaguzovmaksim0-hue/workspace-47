package dev.junta.firmamobile.afirma.external

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.JuntaFirmaApplication
import dev.junta.firmamobile.MainActivity
import dev.junta.firmamobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-rES")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ExternalAfirmaEntryActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<JuntaFirmaApplication>()

    @Test fun viewParksTheUriAndOpensTheAppLikeItsLauncherWithoutForwardingIt() {
        val activity = Robolectric.buildActivity(ExternalAfirmaEntryActivity::class.java, view(URI)).create().get()
        assertTrue(activity.isFinishing)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(Intent.ACTION_MAIN, started.action)
        assertNull(started.data)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(URI, (app.externalAfirmaInbox.take() as ExternalAfirmaDelivery.Fresh).rawUri)
    }

    @Test fun otherActionsSchemesAndRestoredInstancesHaveNoEffect() {
        val other = listOf(
            Intent(Intent.ACTION_SEND, Uri.parse(URI)),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.test/afirma")),
            Intent(Intent.ACTION_VIEW),
        )
        other.forEach { intent ->
            val activity = Robolectric.buildActivity(ExternalAfirmaEntryActivity::class.java, intent).create().get()
            assertTrue(activity.isFinishing)
            assertNull(shadowOf(activity).nextStartedActivity)
        }
        val restored = Robolectric.buildActivity(ExternalAfirmaEntryActivity::class.java, view(URI))
            .create(Bundle()).get()
        assertNull(shadowOf(restored).nextStartedActivity)
        assertSame(ExternalAfirmaDelivery.None, app.externalAfirmaInbox.take())
    }

    @Test fun aSecondDeliveryIsRefusedAndNeverReplacesThePendingOne() {
        assertTrue(app.externalAfirmaInbox.put(URI))
        val activity = Robolectric.buildActivity(ExternalAfirmaEntryActivity::class.java, view("$URI-other"))
            .create().get()
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(app.getString(R.string.external_afirma_busy), ShadowToast.getTextOfLatestToast())
        assertEquals(URI, (app.externalAfirmaInbox.take() as ExternalAfirmaDelivery.Fresh).rawUri)
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .setClass(ApplicationProvider.getApplicationContext(), ExternalAfirmaEntryActivity::class.java)

    private companion object {
        const val URI = "afirma://selectcert?id=fixture&stservlet=https%3A%2F%2Fstorage.example%2Fstore"
    }
}
