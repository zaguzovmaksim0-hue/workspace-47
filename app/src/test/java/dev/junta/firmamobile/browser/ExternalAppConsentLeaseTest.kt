package dev.junta.firmamobile.browser

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ExternalAppConsentLeaseTest {
    @Test fun oneClickCanAuthorizeOnlyOneLaunch() {
        val f = Fixture(); val token = f.reserve()
        assertTrue(f.lease.hasPending); assertNull(f.lease.reserve(f.owner, f.epoch))
        assertTrue(f.lease.consume(token)); assertFalse(f.lease.consume(token)); assertFalse(f.lease.hasPending)
    }
    @Test fun staleTokenCannotConsumeANewerRequest() {
        val f = Fixture(); val old = f.reserve(); f.lease.invalidate(); val next = f.reserve()
        assertFalse(f.lease.consume(old)); assertTrue(f.lease.isValid(next)); assertTrue(f.lease.consume(next))
    }
    @Test fun ownerAndDocumentGenerationArePartOfTheConsent() {
        for (replace in listOf(true, false)) {
            val f = Fixture(); val token = f.reserve()
            if (replace) f.current = Any() else f.epoch++
            assertFalse(f.lease.isValid(token)); assertFalse(f.lease.consume(token)); assertFalse(f.lease.hasPending)
        }
    }
    @Test fun expiryAndClockRollbackDoNotPermitALateLaunch() {
        for (delta in listOf(120_000_000_000L, -1L)) {
            val f = Fixture(); val token = f.reserve(); f.now += delta
            assertFalse(f.lease.consume(token)); assertFalse(f.lease.hasPending)
        }
        val f = Fixture(); val token = f.reserve(); f.now += 119_999_999_999L
        assertTrue(f.lease.consume(token))
    }
    @Test fun foregroundIsRequiredAtOfferAndConfirmation() {
        val f = Fixture(); f.foreground = false; assertNull(f.lease.reserve(f.owner, f.epoch))
        f.foreground = true; val token = f.reserve(); f.foreground = false; assertFalse(f.lease.consume(token))
    }
    @Test fun failingOwnerOrClockChecksNeverGrantPermission() {
        assertNull(ExternalAppConsentLease<Any>({ _, _ -> error("owner") }, { true }).reserve(Any(), 0))
        assertNull(ExternalAppConsentLease<Any>({ _, _ -> true }, { true }, { error("clock") }).reserve(Any(), 0))
        val f = Fixture(); val token = f.reserve(); f.throwNow = true
        assertFalse(f.lease.consume(token)); assertFalse(f.lease.hasPending)
    }
    @Test fun anObserverCannotReplaceTheRequestDuringConsumption() {
        val owner = Any(); var action: () -> Unit = {}; lateinit var lease: ExternalAppConsentLease<Any>
        lease = ExternalAppConsentLease({ _, _ -> action(); true }, { true }, { 0L })
        val old = checkNotNull(lease.reserve(owner, 1)); var newer: UUID? = null
        action = { action = {}; lease.invalidate(); newer = lease.reserve(owner, 2) }
        assertFalse(lease.consume(old)); assertNotNull(newer); assertTrue(lease.isValid(newer!!))
    }
    @Test fun recursiveReserveDoesNotCreateTwoTickets() {
        val owner = Any(); lateinit var lease: ExternalAppConsentLease<Any>; var nested: UUID? = null
        lease = ExternalAppConsentLease({ _, _ -> nested = lease.reserve(owner, 1); true }, { true }, { 0L })
        assertNotNull(lease.reserve(owner, 1)); assertNull(nested); assertTrue(lease.hasPending)
    }
    private class Fixture {
        val owner = Any(); var current = owner; var epoch = 1L; var now = 100L; var foreground = true; var throwNow = false
        val lease = ExternalAppConsentLease<Any>({ candidate, generation -> candidate === current && generation == epoch },
            { foreground }, { check(!throwNow); now })
        fun reserve() = checkNotNull(lease.reserve(owner, epoch))
    }
}
