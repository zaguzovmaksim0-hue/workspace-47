package dev.junta.firmamobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserOwnedResourceLeaseTest {
    @Test
    fun replacementClosesSupersededResourceAndStaleOwnerCannotCloseCurrent() {
        val firstOwner = Any()
        val secondOwner = Any()
        val firstResource = RecordingCloseable()
        val secondResource = RecordingCloseable()
        val lease = BrowserOwnedResourceLease<Any, RecordingCloseable>()

        lease.bind(firstOwner, firstResource)
        lease.bind(secondOwner, secondResource)

        assertEquals(1, firstResource.closeCount)
        assertFalse(lease.release(firstOwner))
        assertEquals(0, secondResource.closeCount)
        assertTrue(lease.current() === secondResource)

        assertTrue(lease.release(secondOwner))
        assertEquals(1, secondResource.closeCount)
        assertNull(lease.current())
        assertFalse(lease.release(secondOwner))
        assertEquals(1, secondResource.closeCount)
    }

    private class RecordingCloseable : AutoCloseable {
        var closeCount = 0
            private set

        override fun close() {
            closeCount++
        }
    }
}
