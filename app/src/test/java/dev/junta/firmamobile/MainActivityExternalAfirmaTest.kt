package dev.junta.firmamobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.afirma.external.ExternalAfirmaDelivery
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Real MainActivity admission of a parked external afirma delivery (ADR 0006). No identity is unlocked, so
 * the consent stays in LOCKED review and nothing can be signed or sent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-rES-w400dp-h1200dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MainActivityExternalAfirmaTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val app get() = ApplicationProvider.getApplicationContext<JuntaFirmaApplication>()

    @Before fun grantOverlayProtection() {
        // Robolectric does not grant this manifest-declared normal permission automatically.
        shadowOf(app).grantPermissions(Manifest.permission.HIDE_OVERLAY_WINDOWS)
    }

    @After fun close() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test fun coldStartShowsTheExternalConsentOnceAndOwnsSigning() {
        assertTrue(app.externalAfirmaInbox.put(SELECT))
        launch()
        awaitConsent()
        rule.onNodeWithTag("external-afirma-warning").assertExists()
        rule.onNodeWithText(app.getString(R.string.native_afirma_locked)).assertExists()
        assertTrue(app.signingAdmission.externalActive)
        assertFalse(app.signingAdmission.tryAcquireExternal(Any()))
        assertSame(ExternalAfirmaDelivery.None, app.externalAfirmaInbox.take())
    }

    @Test fun warmDeliveryIsAdmittedOnReturnAndCancelSendsNothingThenReleases() {
        launch()
        deliverWarm(SELECT)
        awaitConsent()
        rule.onNodeWithTag("native-afirma-cancel").performClick()
        rule.onNodeWithText(app.getString(R.string.native_afirma_cancelled)).assertExists()
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        assertTrue(app.signingAdmission.externalActive)
        rule.onNodeWithTag("native-afirma-close").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("native-afirma-consent").assertDoesNotExist()
        assertFalse(app.signingAdmission.externalActive)
    }

    @Test fun anExplicitIntentToMainActivityNeverCarriesAnAfirmaRequest() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SELECT)).setClass(app, MainActivity::class.java)
        launch(intent)
        rule.onNodeWithTag("native-afirma-consent").assertDoesNotExist()
        assertFalse(app.signingAdmission.externalActive)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test fun aBusyPortalRejectsTheDeliveryAndKeepsItsOperation() {
        launch()
        val portal = Any()
        val busy = { true }
        app.signingAdmission.registerPortal(portal, busy)
        deliverWarm(SELECT)
        assertEquals(app.getString(R.string.external_afirma_busy), ShadowToast.getTextOfLatestToast())
        rule.onNodeWithTag("native-afirma-consent").assertDoesNotExist()
        assertFalse(app.signingAdmission.externalActive)
        assertTrue(app.signingAdmission.portalBusy())
        assertSame(ExternalAfirmaDelivery.None, app.externalAfirmaInbox.take())
        app.signingAdmission.unregisterPortal(portal, busy)
    }

    @Test fun aPendingExternalRequestRefusesASecondDeliveryWithoutReplacingIt() {
        launch()
        deliverWarm(SELECT)
        awaitConsent()
        deliverWarm(SELECT.replace("fixture", "second"))
        assertEquals(app.getString(R.string.external_afirma_busy), ShadowToast.getTextOfLatestToast())
        assertEquals(1, rule.onAllNodesWithTag("native-afirma-consent").fetchSemanticsNodes().size)
        assertTrue(app.signingAdmission.externalActive)
    }

    @Test fun leavingTheAppBeforeConfirmingCancelsTheRequest() {
        launch()
        deliverWarm(SELECT)
        awaitConsent()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        focus()
        rule.onNodeWithText(app.getString(R.string.native_afirma_cancelled)).assertExists()
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-unlock").assertDoesNotExist()
    }

    @Test fun anUnparseableDeliveryIsRejectedAndReleasesAdmission() {
        launch()
        deliverWarm("afirma://unknown-operation?id=x")
        assertEquals(app.getString(R.string.external_afirma_rejected), ShadowToast.getTextOfLatestToast())
        rule.onNodeWithTag("native-afirma-consent").assertDoesNotExist()
        assertFalse(app.signingAdmission.externalActive)
    }

    @Test fun aStaleDeliveryOnAnOrdinaryLaunchOnlyReportsExpiry() {
        assertTrue(app.externalAfirmaInbox.put(SELECT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        launch()
        rule.waitUntil(20_000) {
            ShadowToast.getTextOfLatestToast() == app.getString(R.string.external_afirma_expired)
        }
        rule.onNodeWithTag("native-afirma-consent").assertDoesNotExist()
        assertFalse(app.signingAdmission.externalActive)
    }

    private fun launch(intent: Intent? = null) {
        scenario = intent?.let { ActivityScenario.launch<MainActivity>(it) }
            ?: ActivityScenario.launch(MainActivity::class.java)
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("browse-without-certificate").fetchSemanticsNodes().size == 1
        }
        focus()
    }

    /** What the trampoline does: park the URI, then the existing task pauses and resumes. */
    private fun deliverWarm(uri: String) {
        assertTrue(app.externalAfirmaInbox.put(uri))
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        focus()
    }

    private fun awaitConsent() {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("native-afirma-consent").fetchSemanticsNodes().size == 1
        }
    }

    // Robolectric never gives a window focus by itself; drive the platform callback the real way.
    private fun focus() {
        scenario.onActivity { activity ->
            val root = activity.window.decorView.parent
            runCatching {
                ReflectionHelpers.callInstanceMethod<Any>(root, "windowFocusChanged",
                    ClassParameter.from(Boolean::class.javaPrimitiveType, true))
            }.recoverCatching {
                ReflectionHelpers.callInstanceMethod<Any>(root, "windowFocusChanged",
                    ClassParameter.from(Boolean::class.javaPrimitiveType, true),
                    ClassParameter.from(Boolean::class.javaPrimitiveType, true))
            }.getOrThrow()
        }
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
    }

    private companion object {
        const val SELECT = "afirma://selectcert?id=fixture&stservlet=https%3A%2F%2Fstorage.example%2Fstore"
    }
}
