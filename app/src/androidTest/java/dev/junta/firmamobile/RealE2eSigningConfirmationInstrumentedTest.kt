package dev.junta.firmamobile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.ui.SigningConfirmationDialog
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No Compose test rule: the consent helper must tolerate a continuously recomposing background. */
@RunWith(AndroidJUnit4::class)
class RealE2eSigningConfirmationInstrumentedTest {
    @Test
    fun nativeConsentTransitionsOnceWhileBackgroundKeepsRecomposing() {
        val (result, calls) = runCase("confirm")
        assertTrue(result.dialogObserved)
        assertTrue(result.confirmed)
        assertEquals(1, calls)
    }

    @Test
    fun closedFailuresNeverConfirmAnotherOrUnarmedRequest() {
        for ((case, expected) in listOf(
            "no-dialog" to SignConfirmationOutcome.NO_DIALOG,
            "disabled" to SignConfirmationOutcome.DISABLED,
            "duplicate" to SignConfirmationOutcome.AMBIGUOUS,
            "no-action" to SignConfirmationOutcome.NO_ACTION,
            "false-callback" to SignConfirmationOutcome.CALLBACK_NOT_ACCEPTED,
            "no-transition" to SignConfirmationOutcome.STATE_UNCHANGED,
            "owner-changed" to SignConfirmationOutcome.OWNER_CHANGED,
            "ui-query-failed" to SignConfirmationOutcome.UI_QUERY_FAILED,
            "request-changed" to SignConfirmationOutcome.REQUEST_CHANGED,
            "expired" to SignConfirmationOutcome.REQUEST_EXPIRED,
            "idle" to SignConfirmationOutcome.IDLE,
            "covered" to SignConfirmationOutcome.NO_DIALOG,
        )) {
            val (result, calls) = runCase(case)
            assertEquals(case, expected, result.outcome)
            assertFalse(case, result.confirmed)
            assertEquals(case, if (case in setOf("false-callback", "no-transition")) 1 else 0, calls)
            assertTrue(result.infrastructureCode.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")))
            if (case == "expired") assertEquals(SigningErrorCode.REQUEST_EXPIRED, result.failureCode)
        }
    }

    private fun runCase(case: String): Pair<SignConfirmationResult, Int> {
        val requestId = UUID.randomUUID()
        val awaiting = SigningUiState.AwaitingConfirmation(
            requestId, "sede.dip-badajoz.es", "Synthetic login", CompatibilityStatus.VERIFIED_CONTRACT,
            "Synthetic authentication challenge", "CAdES", "SHA256withRSA", "Synthetic identity", false,
        )
        val state = AtomicReference<SigningUiState>(awaiting)
        val ownsRequest = AtomicBoolean(true)
        val renderedFrames = AtomicInteger()
        val calls = AtomicInteger()
        val composed = CountDownLatch(1)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var activity: ComponentActivity
            scenario.onActivity {
                activity = it
                it.setContent {
                    JuntaFirmaTheme {
                        var frames by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                withFrameNanos { }
                                frames = renderedFrames.incrementAndGet()
                            }
                        }
                        Text("$frames")
                        TextButton(onClick = { calls.incrementAndGet() }) { Text("Firmar") }
                        if (case != "no-dialog") {
                            if (case in setOf("disabled", "duplicate", "no-action", "false-callback")) {
                                AlertDialog(
                                    modifier = Modifier.testTag("signing-confirmation"),
                                    onDismissRequest = {},
                                    confirmButton = {
                                        Column {
                                            repeat(if (case == "duplicate") 2 else 1) {
                                                Box(Modifier.size(64.dp)
                                                    .testTag("signing-confirmation-confirm").semantics {
                                                        if (case == "disabled") disabled()
                                                        if (case != "no-action") onClick {
                                                            calls.incrementAndGet()
                                                            false
                                                        }
                                                    })
                                            }
                                        }
                                    },
                                )
                            } else {
                                SigningConfirmationDialog(awaiting, onConfirm = {
                                    calls.incrementAndGet()
                                    if (case != "no-transition") state.set(SigningUiState.Signing(requestId))
                                }, onCancel = {})
                            }
                        }
                        SideEffect { composed.countDown() }
                    }
                }
            }
            assertTrue(composed.await(5, TimeUnit.SECONDS))
            // Changed before probing: a timed post raced the frame-clocked consent arming on slow emulators.
            when (case) {
                "expired" -> state.set(SigningUiState.Failed(requestId, SigningErrorCode.REQUEST_EXPIRED))
                "idle" -> state.set(SigningUiState.Idle)
                "request-changed" -> state.set(awaiting.copy(requestId = UUID.randomUUID()))
                "owner-changed" -> ownsRequest.set(false)
            }
            var covering: android.app.AlertDialog? = null
            if (case == "covered") {
                scenario.onActivity {
                    covering = android.app.AlertDialog.Builder(it).setMessage("Synthetic blocking modal").show()
                }
                // Let the window manager transfer focus before probing the obscured consent.
                android.os.SystemClock.sleep(300)
            }
            val result = try {
                RealE2eSigningConfirmation.click(
                    activity = activity,
                    requestId = requestId,
                    state = state::get,
                    ownsRequest = {
                        if (case == "ui-query-failed") error("synthetic-private-diagnostic")
                        ownsRequest.get()
                    },
                    timeoutMillis = if (case == "confirm") 5_000 else 1_500,
                )
            } finally {
                scenario.onActivity { covering?.dismiss() }
            }
            if (case == "confirm") assertTrue(renderedFrames.get() > 2)
            return result to calls.get()
        }
    }
}
