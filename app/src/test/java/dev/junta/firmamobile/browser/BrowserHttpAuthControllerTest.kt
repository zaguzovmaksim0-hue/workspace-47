package dev.junta.firmamobile.browser

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class BrowserHttpAuthControllerTest {
    @Test fun onlyExplicitConfirmationAnswersTheOriginalRequest() {
        val f = Fixture(); assertTrue(f.offer()); assertEquals(0, f.reply.proceeds)
        val token = f.prompt!!.token
        assertTrue(f.controller.confirm(token, " user ", ""))
        assertEquals(" user " to "", f.reply.value)
        assertEquals(1, f.reply.proceeds); assertEquals(0, f.reply.cancels)
        assertFalse(f.controller.confirm(token, "user", "secret")); assertNull(f.prompt)
    }
    @Test fun duplicateRequestDoesNotCancelAndSecondRequestCannotReplaceTheFirst() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token
        assertTrue(f.offer()); assertEquals(token, f.prompt!!.token)
        val another = Reply(); assertFalse(f.offer(another)); assertEquals(1, another.cancels)
        assertEquals(0, f.reply.cancels); f.controller.close(); assertEquals(1, f.reply.cancels)
    }
    @Test fun oldTokenCannotAffectNewRequest() {
        val f = Fixture(); f.offer(); val old = f.prompt!!.token; f.controller.cancel(old)
        val another = Reply(); f.offer(another); val newer = f.prompt!!.token
        f.controller.cancel(old); assertFalse(f.controller.confirm(old, "user", "password"))
        assertEquals(newer, f.prompt!!.token); assertEquals(0, another.cancels)
    }
    @Test fun ownerAndEpochChangesExpireConsent() {
        for (changeOwner in listOf(true, false)) {
            val f = Fixture(); f.offer(); val token = f.prompt!!.token
            if (changeOwner) f.active = Any() else f.epoch++
            assertFalse(f.controller.confirm(token, "user", "password"))
            assertEquals(0, f.reply.proceeds); assertEquals(1, f.reply.cancels)
        }
    }
    @Test fun expiryAndClockRollbackCancelExactlyOnce() {
        for (elapsed in listOf(120_000_000_000L, -1L)) {
            val f = Fixture(); f.offer(); f.now += elapsed
            f.controller.tick(); f.controller.invalidate(); f.controller.close()
            assertEquals(1, f.reply.cancels); assertNull(f.prompt)
        }
    }
    @Test fun backgroundAndClosedControllerNeverSendCredentials() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; f.foreground = false
        assertFalse(f.controller.confirm(token, "user", "password")); assertEquals(1, f.reply.cancels)
        f.foreground = true; f.controller.close(); val next = Reply()
        assertFalse(f.offer(next)); assertEquals(1, next.cancels)
    }
    @Test fun invalidInputDoesNotTruncateOrSubmitAndCanBeCorrected() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token
        for ((user, secret) in listOf("" to "p", "x".repeat(1025) to "p", "u" to "x".repeat(4097), "u\r" to "p", "u" to "p\n", "u\u0000" to "p")) {
            assertFalse(f.controller.confirm(token, user, secret)); assertEquals(token, f.prompt!!.token)
        }
        assertEquals(0, f.reply.proceeds)
        assertTrue(f.controller.confirm(token, "u".repeat(1024), "p".repeat(4096)))
    }
    @Test fun aThrownProceedNeverCausesASecondTerminalAnswer() {
        val f = Fixture(); f.reply.throwOnProceed = true; f.offer()
        assertFalse(f.controller.confirm(f.prompt!!.token, "user", "password"))
        assertEquals(1, f.reply.proceeds); assertEquals(0, f.reply.cancels)
        f.controller.close(); assertEquals(0, f.reply.cancels)
    }
    @Test fun reentrantDismissalAndObserverFailureReleaseRequests() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token
        f.observer = { if (it == null) f.active = Any() }
        assertFalse(f.controller.confirm(token, "user", "password"))
        assertEquals(1, f.reply.cancels); assertEquals(0, f.reply.proceeds)
        val g = Fixture(); g.observer = { throw IllegalStateException("synthetic observer") }
        assertFalse(g.offer()); assertEquals(1, g.reply.cancels); assertFalse(g.controller.hasPending)
    }
    @Test fun aNewRequestDuringDismissalDoesNotReceiveOldCredentials() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; val next = Reply()
        f.observer = { if (it == null) { f.observer = {}; f.offer(next) } }
        assertFalse(f.controller.confirm(token, "user", "password"))
        assertEquals(0, f.reply.proceeds); assertEquals(1, f.reply.cancels)
        assertEquals(0, next.proceeds); assertEquals(0, next.cancels); assertTrue(f.controller.hasPending)
        f.controller.close()
    }

    private class Fixture {
        val owner = Any(); var active = owner; var epoch = 1L; var now = 100L; var foreground = true
        var prompt: BrowserHttpAuthPrompt? = null; var observer: (BrowserHttpAuthPrompt?) -> Unit = {}
        val reply = Reply()
        val controller = BrowserHttpAuthController<Any>(
            { view, generation -> view === active && generation == epoch }, { foreground },
            { prompt = it; observer(it) }, { now },
        )
        fun offer(value: Reply = reply) = controller.offer(owner, epoch, "https://login.example/", "login.example", "Test", value)
    }
    private class Reply : HttpAuthReply {
        var proceeds = 0; var cancels = 0; var throwOnProceed = false
        var value: Pair<String, String>? = null
        override fun proceed(username: String, password: String) { proceeds++; value = username to password; check(!throwOnProceed) }
        override fun cancel() { cancels++ }
    }
}
