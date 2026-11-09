package dev.junta.firmamobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningUiState
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class SignConfirmationOutcome {
    CONFIRMED, NO_DIALOG, NO_BUTTON, DISABLED, AMBIGUOUS, NO_ACTION, UI_QUERY_FAILED,
    OWNER_CHANGED, REQUEST_CHANGED, REQUEST_EXPIRED, IDLE, FAILED, CALLBACK_NOT_ACCEPTED, STATE_UNCHANGED,
}

internal data class SignConfirmationResult(
    val outcome: SignConfirmationOutcome,
    val dialogObserved: Boolean,
    val failureCode: SigningErrorCode?,
    val dialogs: Int,
    val buttons: Int,
    val enabled: Int,
    val stateCode: String,
) {
    val confirmed: Boolean get() = outcome == SignConfirmationOutcome.CONFIRMED
    val infrastructureCode: String get() =
        "SIGN_CONFIRM_${outcome.name}_D${dialogs.coerceAtMost(9)}_B${buttons.coerceAtMost(9)}_" +
            "E${enabled.coerceAtMost(9)}_$stateCode"
}

/** Reads native consent semantics without Compose test synchronization or WebView content. */
@OptIn(ExperimentalComposeUiApi::class)
internal object RealE2eSigningConfirmation {
    fun click(
        activity: Activity,
        requestId: UUID,
        state: () -> SigningUiState,
        ownsRequest: () -> Boolean,
        timeoutMillis: Long = 30_000L,
        advanceFrame: () -> Unit = {},
        armConsent: () -> Unit = {},
    ): SignConfirmationResult {
        var armed = false
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var last = Attempt(SignConfirmationResult(SignConfirmationOutcome.NO_DIALOG, false, null, 0, 0, 0, "I"))
        while (SystemClock.elapsedRealtime() < deadline) {
            val previous = last
            val next = try {
                onSigningMainUntil(deadline) { canAct ->
                    inspect(activity, requestId, state, ownsRequest, previous, canAct, advanceFrame)
                }
            } catch (_: Exception) {
                null
            }
            // A probe cut off by the deadline is a timeout: report the last observed state, not a query failure.
            val timedOut = SystemClock.elapsedRealtime() >= deadline
            if (timedOut && (next == null || next.result.outcome == SignConfirmationOutcome.UI_QUERY_FAILED)) break
            if (next == null) return last.result.copy(outcome = SignConfirmationOutcome.UI_QUERY_FAILED)
            last = next
            if (last.terminal) return last.result
            // Consent arms on the frame clock; advancing it from the main-thread probe never reaches the delay.
            if (!armed && last.result.outcome == SignConfirmationOutcome.DISABLED) {
                armed = true
                runCatching(armConsent)
            }
            SystemClock.sleep(minOf(50L, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)))
        }
        return last.result
    }

    private data class Attempt(
        val result: SignConfirmationResult,
        val callbackAccepted: Boolean = false,
        val terminal: Boolean = false,
    )

    private fun inspect(
        activity: Activity,
        requestId: UUID,
        state: () -> SigningUiState,
        ownsRequest: () -> Boolean,
        previous: Attempt,
        canAct: () -> Boolean,
        advanceFrame: () -> Unit,
    ): Attempt {
        var observed = previous.result.dialogObserved
        var dialogs = 0
        var buttons = 0
        var enabled = 0
        var stateCode = "I"

        fun finish(
            outcome: SignConfirmationOutcome,
            terminal: Boolean = true,
            accepted: Boolean = previous.callbackAccepted,
            failureCode: SigningErrorCode? = null,
        ) = Attempt(
            SignConfirmationResult(outcome, observed, failureCode, dialogs, buttons, enabled, stateCode),
            accepted,
            terminal,
        )

        if (!canAct()) return finish(SignConfirmationOutcome.UI_QUERY_FAILED)
        if (activity.isFinishing || activity.isDestroyed || !ownsRequest()) {
            return finish(SignConfirmationOutcome.OWNER_CHANGED)
        }
        // One test frame per real-time paced poll preserves consent arming without waiting for global idle.
        advanceFrame()
        if (!canAct()) return finish(SignConfirmationOutcome.UI_QUERY_FAILED)
        val current = state()
        stateCode = current.confirmationStateCode()
        if (current.requestIdOrNull() != requestId) {
            return finish(if (current is SigningUiState.Idle) SignConfirmationOutcome.IDLE
                else SignConfirmationOutcome.REQUEST_CHANGED)
        }
        // A failure after accepted consent belongs to the terminal-state observer, not to the confirmation probe.
        if (current is SigningUiState.Failed && !previous.callbackAccepted) {
            return finish(
                if (current.code == SigningErrorCode.REQUEST_EXPIRED) SignConfirmationOutcome.REQUEST_EXPIRED
                else SignConfirmationOutcome.FAILED,
                failureCode = current.code,
            )
        }
        if (current !is SigningUiState.AwaitingConfirmation) {
            return finish(if (previous.callbackAccepted) SignConfirmationOutcome.CONFIRMED
                else SignConfirmationOutcome.CALLBACK_NOT_ACCEPTED)
        }
        if (previous.callbackAccepted) return finish(SignConfirmationOutcome.STATE_UNCHANGED, terminal = false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return finish(SignConfirmationOutcome.UI_QUERY_FAILED)
        val dialogNodes = WindowInspector.getGlobalWindowViews()
            .filter { it.isAttachedToWindow && it.isShown && it.hasWindowFocus() && it.context.belongsTo(activity) }
            .flatMap(::nativeRoots)
            .filter { it.isLifecycleInResumedState && it.view.isAttachedToWindow && it.view.isShown }
            .flatMap { descendants(it.semanticsOwner.unmergedRootSemanticsNode) }
            .filter { it.config.getOrNull(SemanticsProperties.TestTag) == "signing-confirmation" }
        dialogs = dialogNodes.size
        if (dialogs == 0) return finish(SignConfirmationOutcome.NO_DIALOG, terminal = false)
        observed = true
        val buttonNodes = dialogNodes.flatMap(::descendants).filter {
            it.config.getOrNull(SemanticsProperties.TestTag) == "signing-confirmation-confirm"
        }
        buttons = buttonNodes.size
        enabled = buttonNodes.count { !it.config.contains(SemanticsProperties.Disabled) }
        if (dialogs != 1 || buttons != 1) {
            return if (buttons == 0 && dialogs == 1) finish(SignConfirmationOutcome.NO_BUTTON, terminal = false)
                else finish(SignConfirmationOutcome.AMBIGUOUS)
        }
        val button = buttonNodes.single()
        if (button.config.contains(SemanticsProperties.Disabled) || button.boundsInWindow.isEmpty) {
            return finish(SignConfirmationOutcome.DISABLED, terminal = false)
        }
        val action = button.config.getOrNull(SemanticsActions.OnClick)?.action
            ?: return finish(SignConfirmationOutcome.NO_ACTION)
        val awaiting = state() as? SigningUiState.AwaitingConfirmation
        if (!ownsRequest() || awaiting?.requestId != requestId) return finish(SignConfirmationOutcome.OWNER_CHANGED)
        if (!canAct()) return finish(SignConfirmationOutcome.UI_QUERY_FAILED)
        val callbackAccepted = action() == true
        return finish(
            if (callbackAccepted) SignConfirmationOutcome.STATE_UNCHANGED
            else SignConfirmationOutcome.CALLBACK_NOT_ACCEPTED,
            terminal = !callbackAccepted,
            accepted = callbackAccepted,
        )
    }

    private fun nativeRoots(view: View): List<ViewRootForTest> = when (view) {
        is ViewRootForTest -> listOf(view)
        is WebView -> emptyList()
        is ViewGroup -> (0 until view.childCount).flatMap { nativeRoots(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap(::descendants)

    private fun Context.belongsTo(activity: Activity): Boolean {
        var context = this
        while (context is ContextWrapper) {
            if (context === activity) return true
            val base = context.baseContext
            if (base === context) return false
            context = base
        }
        return context === activity
    }
}

internal fun SigningUiState.requestIdOrNull(): UUID? = when (this) {
    SigningUiState.Idle -> null
    is SigningUiState.AwaitingConfirmation -> requestId
    is SigningUiState.Signing -> requestId
    is SigningUiState.ConnectingSecurely -> requestId
    is SigningUiState.Completed -> requestId
    is SigningUiState.Failed -> requestId
}

private fun SigningUiState.confirmationStateCode(): String = when (this) {
    SigningUiState.Idle -> "I"
    is SigningUiState.AwaitingConfirmation -> "A"
    is SigningUiState.Signing -> "S"
    is SigningUiState.ConnectingSecurely -> "C"
    is SigningUiState.Completed -> "T"
    is SigningUiState.Failed -> "F"
}

/** Cancels queued work at the same deadline; a late UI task must never deliver consent. */
internal fun <T> onSigningMainUntil(deadline: Long, block: (() -> Boolean) -> T): T? {
    val active = AtomicBoolean(true)
    val completed = CountDownLatch(1)
    val result = AtomicReference<Result<T>?>(null)
    val handler = Handler(Looper.getMainLooper())
    val canAct = { active.get() && SystemClock.elapsedRealtime() < deadline }
    val task = Runnable {
        try {
            if (canAct()) result.set(runCatching { block(canAct) })
        } finally {
            completed.countDown()
        }
    }
    if (!handler.post(task)) return null
    return try {
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        if (!completed.await(remaining, TimeUnit.MILLISECONDS)) null else result.get()?.getOrThrow()
    } finally {
        active.set(false)
        handler.removeCallbacks(task)
    }
}
