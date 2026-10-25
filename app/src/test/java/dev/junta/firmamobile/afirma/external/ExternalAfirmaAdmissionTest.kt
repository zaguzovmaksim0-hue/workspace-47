package dev.junta.firmamobile.afirma.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAfirmaAdmissionTest {
    @Test fun envelopeAcceptsOnlyViewOfTheAfirmaScheme() {
        val view = ExternalAfirmaEnvelope.ACTION_VIEW
        assertEquals("afirma://sign?op=sign", ExternalAfirmaEnvelope.accept(view, "afirma://sign?op=sign"))
        assertEquals("AFIRMA://sign", ExternalAfirmaEnvelope.accept(view, "AFIRMA://sign"))
        assertNull(ExternalAfirmaEnvelope.accept("android.intent.action.SEND", "afirma://sign"))
        assertNull(ExternalAfirmaEnvelope.accept(null, "afirma://sign"))
        assertNull(ExternalAfirmaEnvelope.accept(view, null))
        assertNull(ExternalAfirmaEnvelope.accept(view, "https://example.test/afirma://sign"))
        assertNull(ExternalAfirmaEnvelope.accept(view, "afirm"))
        assertNull(ExternalAfirmaEnvelope.accept(view, "afirma:" + "x".repeat(1_048_570)))
    }

    @Test fun inboxDeliversOnceAndNeverReplacesAFreshDelivery() {
        var now = 0L
        val inbox = ExternalAfirmaInbox(ttlNanos = 10, nowNanos = { now })
        assertSame(ExternalAfirmaDelivery.None, inbox.take())
        assertTrue(inbox.put("afirma://first"))
        assertFalse(inbox.put("afirma://second"))
        now = 9
        assertEquals("afirma://first", (inbox.take() as ExternalAfirmaDelivery.Fresh).rawUri)
        assertSame(ExternalAfirmaDelivery.None, inbox.take())
        assertFalse(inbox.hasPending())
    }

    @Test fun staleOrClockRollbackDeliveryIsReportedButNeverExecuted() {
        var now = 100L
        val inbox = ExternalAfirmaInbox(ttlNanos = 10, nowNanos = { now })
        assertTrue(inbox.put("afirma://old"))
        now = 110
        assertTrue(inbox.put("afirma://replacement"))
        now = 120
        assertSame(ExternalAfirmaDelivery.Stale, inbox.take())
        assertTrue(inbox.put("afirma://rollback"))
        now = 50
        assertSame(ExternalAfirmaDelivery.Stale, inbox.take())
        assertEquals("Fresh", ExternalAfirmaDelivery.Fresh("afirma://secret").toString())
    }

    @Test fun portalAndExternalOwnersExcludeEachOther() {
        val admission = SigningAdmission()
        var busy = true
        val screen = Any()
        val provider = { busy }
        admission.registerPortal(screen, provider)
        val first = Any()
        assertFalse(admission.tryAcquireExternal(first))
        busy = false
        assertTrue(admission.tryAcquireExternal(first))
        assertTrue(admission.externalActive)
        assertFalse(admission.tryAcquireExternal(Any()))
        admission.releaseExternal(Any())
        assertTrue(admission.externalActive)
        admission.releaseExternal(first)
        assertFalse(admission.externalActive)
    }

    @Test fun throwingOrStaleProvidersFailClosedAndOnlyTheirOwnerUnregisters() {
        val admission = SigningAdmission()
        val owner = Any()
        val throwing: () -> Boolean = { error("unavailable") }
        admission.registerPortal(owner, throwing)
        assertTrue(admission.portalBusy())
        val replacement: () -> Boolean = { false }
        admission.registerPortal(owner, replacement)
        admission.unregisterPortal(owner, throwing)
        assertFalse(admission.portalBusy())
        admission.registerPortal(Any()) { true }
        assertTrue(admission.portalBusy())
        admission.unregisterPortal(owner, replacement)
        assertTrue(admission.portalBusy())
    }
}
