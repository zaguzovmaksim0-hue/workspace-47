package dev.junta.firmamobile.ui

import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserLoadingVisibilityTest {
    @Test
    fun clearingKeepsIndicatorVisibleForEveryLoadingCombination() {
        assertLoadingCombinations(ClientCertPreferenceBarrierState.CLEARING, listOf(true, true, true, true))
    }

    @Test
    fun idleOnlyShowsIndicatorWhilePreparingOrLoading() {
        assertLoadingCombinations(ClientCertPreferenceBarrierState.IDLE, listOf(false, true, true, true))
    }

    @Test
    fun readyOnlyShowsIndicatorWhilePreparingOrLoading() {
        assertLoadingCombinations(ClientCertPreferenceBarrierState.READY, listOf(false, true, true, true))
    }

    @Test
    fun failureAloneDoesNotShowIndicator() {
        assertLoadingCombinations(ClientCertPreferenceBarrierState.FAILED, listOf(false, true, true, true))
    }

    private fun assertLoadingCombinations(
        state: ClientCertPreferenceBarrierState,
        expected: List<Boolean>,
    ) {
        val combinations = listOf(false to false, false to true, true to false, true to true)
        combinations.forEachIndexed { index, (preparing, loading) ->
            assertEquals(
                "$state: preparing=$preparing, loading=$loading",
                expected[index],
                browserLoadingVisible(state, preparing, loading),
            )
        }
    }
}
