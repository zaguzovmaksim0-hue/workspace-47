package dev.junta.firmamobile.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientCertPreferenceCoordinatorTest {
    private var nowMillis = 10_000L
    private val clearer = FakeClearer()
    private val scheduler = FakeScheduler { nowMillis }
    private val coordinator = ClientCertPreferenceCoordinator(
        clearer = clearer,
        scheduler = scheduler,
        barrier = ClientCertPreferenceBarrier(monotonicMillis = { nowMillis }),
    )

    @Test
    fun callbackIsRequiredBeforeGlobalStateReturnsToIdle() {
        val results = mutableListOf<ClientCertPreferenceClearResult>()

        coordinator.requestClear { _, result -> results += result }

        assertEquals(ClientCertPreferenceBarrierState.CLEARING, coordinator.state.value)
        assertTrue(results.isEmpty())
        clearer.complete(0)
        assertEquals(listOf(ClientCertPreferenceClearResult.CLEARED), results)
        assertEquals(ClientCertPreferenceBarrierState.IDLE, coordinator.state.value)
        assertEquals(0, scheduler.activeTasks())
    }

    @Test
    fun timeoutLeavesProcessBlockedAndLateCallbackCannotUnblockIt() {
        val results = mutableListOf<ClientCertPreferenceClearResult>()
        coordinator.requestClear { _, result -> results += result }

        scheduler.advanceBy(ClientCertPreferenceBarrier.CLEAR_TIMEOUT.toMillis())

        assertEquals(listOf(ClientCertPreferenceClearResult.FAILED), results)
        assertEquals(ClientCertPreferenceBarrierState.FAILED, coordinator.state.value)
        clearer.complete(0)
        assertEquals(listOf(ClientCertPreferenceClearResult.FAILED), results)
        assertEquals(ClientCertPreferenceBarrierState.FAILED, coordinator.state.value)
    }

    @Test
    fun successfulRetryClearsAPreviousFailureButOldCallbackStaysStale() {
        val results = mutableListOf<String>()
        coordinator.requestClear { _, result -> results += "old:$result" }
        scheduler.advanceBy(ClientCertPreferenceBarrier.CLEAR_TIMEOUT.toMillis())

        coordinator.requestClear { _, result -> results += "new:$result" }
        assertEquals(ClientCertPreferenceBarrierState.CLEARING, coordinator.state.value)
        clearer.complete(0)
        assertEquals(listOf("old:FAILED"), results)
        assertEquals(ClientCertPreferenceBarrierState.CLEARING, coordinator.state.value)

        clearer.complete(1)
        assertEquals(listOf("old:FAILED", "new:CLEARED"), results)
        assertEquals(ClientCertPreferenceBarrierState.IDLE, coordinator.state.value)
    }

    @Test
    fun cancellingUiCallbackDoesNotCancelTheProcessWideClear() {
        val results = mutableListOf<ClientCertPreferenceClearResult>()
        val request = coordinator.requestClear { _, result -> results += result }

        coordinator.cancelCallback(request)
        clearer.complete(0)

        assertTrue(results.isEmpty())
        assertEquals(ClientCertPreferenceBarrierState.IDLE, coordinator.state.value)
    }

    @Test
    fun newerRequestSupersedesOlderGenerationAndOnlyNewListenerCompletes() {
        val results = mutableListOf<String>()
        coordinator.requestClear { _, result -> results += "old:$result" }
        coordinator.requestClear { _, result -> results += "new:$result" }

        clearer.complete(0)
        assertTrue(results.isEmpty())
        assertEquals(ClientCertPreferenceBarrierState.CLEARING, coordinator.state.value)

        clearer.complete(1)
        assertEquals(listOf("new:CLEARED"), results)
        assertEquals(ClientCertPreferenceBarrierState.IDLE, coordinator.state.value)
    }

    @Test
    fun clearerExceptionFailsClosedAndCanBeRetried() {
        val results = mutableListOf<ClientCertPreferenceClearResult>()
        clearer.throwOnNext = true

        coordinator.requestClear { _, result -> results += result }

        assertEquals(listOf(ClientCertPreferenceClearResult.FAILED), results)
        assertEquals(ClientCertPreferenceBarrierState.FAILED, coordinator.state.value)
        coordinator.requestClear { _, result -> results += result }
        clearer.complete(0)
        assertEquals(
            listOf(ClientCertPreferenceClearResult.FAILED, ClientCertPreferenceClearResult.CLEARED),
            results,
        )
        assertEquals(ClientCertPreferenceBarrierState.IDLE, coordinator.state.value)
    }

    @Test
    fun synchronousPlatformCallbackIsHandledWithoutLeavingATimeoutBehind() {
        val synchronous = ClientCertPreferenceClearer { callback -> callback() }
        val local = ClientCertPreferenceCoordinator(
            clearer = synchronous,
            scheduler = scheduler,
            barrier = ClientCertPreferenceBarrier(monotonicMillis = { nowMillis }),
        )
        val results = mutableListOf<ClientCertPreferenceClearResult>()

        local.requestClear { _, result -> results += result }

        assertEquals(listOf(ClientCertPreferenceClearResult.CLEARED), results)
        assertEquals(ClientCertPreferenceBarrierState.IDLE, local.state.value)
        assertEquals(0, scheduler.activeTasks())
    }

    private class FakeClearer : ClientCertPreferenceClearer {
        val callbacks = mutableListOf<() -> Unit>()
        var throwOnNext = false

        override fun clear(onCleared: () -> Unit) {
            if (throwOnNext) {
                throwOnNext = false
                error("clear unavailable")
            }
            callbacks += onCleared
        }

        fun complete(index: Int) = callbacks[index].invoke()
    }

    private data class ScheduledTask(
        val dueAt: Long,
        val action: () -> Unit,
        var cancelled: Boolean = false,
    )

    private inner class FakeScheduler(
        private val now: () -> Long,
    ) : ClientCertPreferenceTimeoutScheduler {
        private val tasks = mutableListOf<ScheduledTask>()

        override fun schedule(delayMillis: Long, action: () -> Unit): ClientCertPreferenceTimeoutHandle {
            val task = ScheduledTask(now() + delayMillis, action)
            tasks += task
            return ClientCertPreferenceTimeoutHandle { task.cancelled = true }
        }

        fun advanceBy(durationMillis: Long) {
            val target = now() + durationMillis
            while (true) {
                val next = tasks
                    .filter { !it.cancelled && it.dueAt <= target }
                    .minByOrNull(ScheduledTask::dueAt)
                    ?: break
                nowMillis = next.dueAt
                next.cancelled = true
                next.action()
            }
            nowMillis = target
        }

        fun activeTasks(): Int = tasks.count { !it.cancelled }
    }
}
