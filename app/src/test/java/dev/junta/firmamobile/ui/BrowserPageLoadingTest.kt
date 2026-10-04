package dev.junta.firmamobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserPageLoadingTest {
    @Test fun percentageUpdatesCollapseToTwoVisibilityTransitions() {
        val states = (listOf(100) + (0..100).toList()).map(::browserPageLoading)
        assertEquals(2, states.zipWithNext().count { (a, b) -> a != b })
    }
    @Test fun invalidOrCompletedProgressDoesNotLeaveAnIndefiniteSpinner() {
        assertEquals(false, browserPageLoading(-1))
        assertEquals(false, browserPageLoading(100))
        assertEquals(false, browserPageLoading(101))
    }
}
