package dev.junta.firmamobile.afirma.servlet

import java.time.Duration
import org.junit.Assert.*
import org.junit.Test

class AfirmaFallbackControllerTest {
    @Test fun unsupportedAndInvalidRequestsCannotOverlapOrChangeTheOldButton() {
        val f = Fixture(); assertTrue(f.offer("afirma://sign?id=A")); val first = f.prompt!!
        assertFalse(f.offer(null)); assertFalse(f.offer("afirma://sign?id=B"))
        assertEquals(first, f.prompt); assertFalse(f.prompt!!.invalid)
        assertEquals("afirma://sign?id=A", f.controller.consume(first.token))
        assertNull(f.prompt); assertNull(f.controller.consume(first.token))
    }
    @Test fun invalidRequestNeverHasAnOfficialAppPayload() {
        val f = Fixture(); f.offer(null)
        assertTrue(f.prompt!!.invalid); assertNull(f.controller.consume(f.prompt!!.token))
    }
    @Test fun aCapturedButtonAfterNavigationCannotOpenItsOldRequest() {
        val f = Fixture(); f.offer("afirma://sign?id=A"); val token = f.prompt!!.token
        f.epoch++; f.controller.invalidate()
        assertNull(f.controller.consume(token))
    }
    @Test fun anotherViewCannotInheritFallbackEvenWithTheSameEpoch() {
        val f = Fixture(); f.offer("afirma://sign?id=A"); val token = f.prompt!!.token
        f.currentOwner = Any(); assertNull(f.controller.consume(token))
    }
    @Test fun deadlineAndClockRollbackRemoveExpiredFallback() {
        for (expired in listOf(true, false)) {
            val f = Fixture(); f.offer("afirma://sign?id=A"); val token = f.prompt!!.token
            f.now = if (expired) f.now + Duration.ofMinutes(5).toNanos() else f.now - 1
            f.controller.tick(); assertNull(f.prompt); assertNull(f.controller.consume(token))
        }
    }
    @Test fun changingOwnerDuringPromptDismissalCannotRaceTheExternalLaunch() {
        val f = Fixture(); f.offer("afirma://sign?id=A"); val token = f.prompt!!.token
        f.observer = { if (it == null) f.epoch++ }
        assertNull(f.controller.consume(token))
    }
    @Test fun oldDismissDoesNotDeleteANewerFallback() {
        val f = Fixture(); f.offer("afirma://sign?id=A"); val old = f.prompt!!.token
        f.controller.dismiss(old); f.offer("afirma://sign?id=B"); val latest = f.prompt!!.token
        f.controller.dismiss(old); assertEquals(latest, f.prompt!!.token)
        assertEquals("afirma://sign?id=B", f.controller.consume(latest))
    }
    @Test fun visibleReasonIsOnlyAClosedTechnicalCodeNeverRequestText() {
        val cases = listOf(
            "missing_storage_url" to "missing_storage_url",
            "certificate_filter_not_implemented" to "certificate_filter_not_implemented",
            "afirma://sign?id=Synthetic" to null, "Missing_Storage" to null, "missing storage" to null,
            "" to null, "a".repeat(65) to null, null to null,
        )
        for ((reason, expected) in cases) {
            val f = Fixture(); assertTrue(f.controller.offer(f.owner, f.epoch, null, reason))
            assertEquals(reason.toString(), expected, f.prompt!!.reason)
        }
        val f = Fixture(); f.controller.offer(f.owner, f.epoch, "afirma://sign?id=A", "a".repeat(64))
        assertEquals("a".repeat(64), f.prompt!!.reason)
    }
    @Test fun inactiveOrStaleDocumentsCannotConsumeAnOfficialAppPayload() {
        val stale: List<Fixture.() -> Unit> = listOf(
            { epoch++ }, { currentOwner = Any() }, { foreground = false },
            { now += Duration.ofMinutes(5).toNanos() }, { now -= 1 }, { observer = { if (it == null) epoch++ } },
        )
        for (change in stale) {
            val f = Fixture(); f.offer("afirma://sign?id=A"); val token = f.prompt!!.token; f.change()
            assertNull(f.controller.consume(token)); assertNull(f.prompt)
        }
    }
    @Test fun capturedFallbackButtonCannotConsumeANewerRequest() {
        val f = Fixture(); f.offer("afirma://sign?id=A"); val old = f.prompt!!.token; f.controller.dismiss(old)
        f.offer("afirma://sign?id=B"); val latest = f.prompt!!.token
        assertNull(f.controller.consume(old)); assertEquals(latest, f.prompt!!.token)
        assertEquals("afirma://sign?id=B", f.controller.consume(latest))
    }
    private class Fixture {
        val owner = Any(); var currentOwner = owner; var epoch = 1L; var now = 100L; var foreground = true
        var prompt: AfirmaFallbackPrompt? = null; var observer: (AfirmaFallbackPrompt?) -> Unit = {}
        val controller = AfirmaFallbackController<Any>(
            isCurrent = { owner, epoch -> owner === currentOwner && epoch == this.epoch },
            canRespond = { foreground }, onPrompt = { prompt = it; observer(it) }, monotonicNanos = { now },
        )
        fun offer(raw: String?) = controller.offer(owner, epoch, raw)
    }
}
