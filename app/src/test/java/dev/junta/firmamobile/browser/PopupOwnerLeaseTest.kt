package dev.junta.firmamobile.browser

import org.junit.Assert.*
import org.junit.Test

class PopupOwnerLeaseTest {
    @Test fun oneWindowReservesExactlyOneSlot() {
        val f = Fixture(); val ticket = f.reserve()
        assertTrue(f.lease.hasWindow); assertTrue(f.lease.isCurrent(ticket))
        assertNull(f.lease.reserve(f.owner, f.epoch))
        assertTrue(f.lease.release(ticket)); assertFalse(f.lease.hasWindow)
    }
    @Test fun oldCloseCannotReleaseANewWindow() {
        val f = Fixture(); val old = f.reserve(); f.lease.release(old); val fresh = f.reserve()
        assertNotEquals(old, fresh); assertFalse(f.lease.release(old))
        assertTrue(f.lease.isCurrent(fresh)); assertFalse(f.lease.markAttached(old))
    }
    @Test fun changingOwnerOrNavigationInvalidatesOldRequests() {
        for (ownerChange in listOf(true, false)) {
            val f = Fixture(); val token = f.reserve()
            if (ownerChange) f.current = Any() else f.epoch++
            assertFalse(f.lease.isCurrent(token)); assertFalse(f.lease.markAttached(token))
        }
    }
    @Test fun attachmentIsOneTimeButIsNotAWindowLifetimeLimit() {
        val f = Fixture(); val token = f.reserve()
        assertTrue(f.lease.markAttached(token)); assertFalse(f.lease.markAttached(token))
        f.now += 600_000_000_000L
        assertTrue(f.lease.isCurrent(token)); f.epoch++
        assertFalse(f.lease.isCurrent(token))
    }
    @Test fun aLateOrClockReversedUnattachedWindowCannotBeBound() {
        for (elapsed in listOf(15_000_000_000L, -1L)) {
            val f = Fixture(); val token = f.reserve(); f.now += elapsed
            assertFalse(f.lease.isCurrent(token)); assertFalse(f.lease.markAttached(token))
            assertTrue(f.lease.hasWindow)
            assertNull(f.lease.reserve(f.owner, f.epoch))
            assertTrue(f.lease.release(token))
        }
    }
    @Test fun invalidationClearsTheSlotWithoutReusingTheTicket() {
        val f = Fixture(); val old = f.reserve(); f.lease.invalidate()
        assertFalse(f.lease.hasWindow); assertFalse(f.lease.isCurrent(old))
        val fresh = f.reserve(); assertNotEquals(old, fresh)
    }
    @Test fun failedPredicatesDoNotGrantWindows() {
        val lease = PopupOwnerLease<Any>({ _, _ -> error("synthetic ownership") })
        assertNull(lease.reserve(Any(), 0)); assertFalse(lease.hasWindow)
        val clock = PopupOwnerLease<Any>({ _, _ -> true }, { error("synthetic clock") })
        assertNull(clock.reserve(Any(), 0)); assertFalse(clock.hasWindow)
    }
    @Test fun aValidUnattachedWindowStillHasItsOriginalDeadline() {
        val f = Fixture(); val ticket = f.reserve(); f.now += 14_999_999_999L
        assertTrue(f.lease.isCurrent(ticket)); f.now++
        assertFalse(f.lease.isCurrent(ticket))
    }
    @Test fun scriptedWindowCannotAttachBeforeExplicitConfirmation() {
        val f = Fixture(); val token = checkNotNull(f.lease.reserve(f.owner, f.epoch, requiresConfirmation = true))
        assertTrue(f.lease.isCurrent(token)); assertFalse(f.lease.canAttach(token))
        assertFalse(f.lease.markAttached(token))
        assertTrue(f.lease.confirm(token)); assertTrue(f.lease.canAttach(token))
        assertFalse(f.lease.confirm(token)); assertTrue(f.lease.markAttached(token))
        assertFalse(f.lease.canAttach(token))
    }
    @Test fun confirmationCannotReviveAnExpiredOrReplacedOwner() {
        for (change in listOf<Fixture.() -> Unit>(
            { current = Any() }, { epoch++ }, { now += 15_000_000_000L }, { now-- },
        )) {
            val f = Fixture(); val token = checkNotNull(f.lease.reserve(f.owner, f.epoch, requiresConfirmation = true))
            f.change(); assertFalse(f.lease.confirm(token)); assertFalse(f.lease.canAttach(token))
        }
    }
    @Test fun confirmingDoesNotExtendTheOriginalAttachmentDeadline() {
        val f = Fixture(); val token = checkNotNull(f.lease.reserve(f.owner, f.epoch, requiresConfirmation = true))
        f.now += 14_000_000_000L; assertTrue(f.lease.confirm(token))
        f.now += 1_000_000_000L; assertFalse(f.lease.canAttach(token)); assertFalse(f.lease.markAttached(token))
    }
    @Test fun staleConfirmationCannotApproveANewerScriptedWindow() {
        val f = Fixture(); val old = checkNotNull(f.lease.reserve(f.owner, f.epoch, requiresConfirmation = true))
        f.lease.release(old)
        val fresh = checkNotNull(f.lease.reserve(f.owner, f.epoch, requiresConfirmation = true))
        assertFalse(f.lease.confirm(old)); assertFalse(f.lease.canAttach(fresh))
        assertTrue(f.lease.confirm(fresh))
    }
    private class Fixture {
        val owner = Any(); var current = owner; var epoch = 1L; var now = 100L
        val lease = PopupOwnerLease<Any>({ candidate, generation -> candidate === current && generation == epoch }, { now })
        fun reserve() = checkNotNull(lease.reserve(owner, epoch))
    }
}
