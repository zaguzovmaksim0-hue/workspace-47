package dev.junta.firmamobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDataClearCompletionLeaseTest {
    @Test
    fun currentRequestCanBeConsumedOnlyOnce() {
        val owner = Any()
        val lease = BrowserDataClearCompletionLease<Any>()
        val request = lease.begin(owner)

        assertEquals(owner, request.owner)
        assertTrue(lease.consume(request))
        assertFalse(lease.consume(request))
    }

    @Test
    fun laterRequestSupersedesEarlierRequest() {
        val lease = BrowserDataClearCompletionLease<String>()
        val first = lease.begin("first")
        val second = lease.begin("second")

        assertFalse(lease.consume(first))
        assertTrue(lease.consume(second))
    }

    @Test
    fun invalidationRejectsOutstandingRequest() {
        val lease = BrowserDataClearCompletionLease<String>()
        val request = lease.begin("profile-a")

        lease.invalidate()

        assertFalse(lease.consume(request))
    }
}
