package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.ProfileHttpCancellation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BatchPhaseExecutorTest {
    @Test fun blockingPhaseRunsOffCallerAndReturnsToOwner() = runBlocking {
        val caller = Thread.currentThread()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val cancellation = ProfileHttpCancellation()
            val result = BatchPhaseExecutor(dispatcher).run(cancellation, discard = { _: String -> error("Unexpected discard") }) {
                assertNotSame(caller, Thread.currentThread())
                "done"
            }
            assertEquals("done", result)
            assertSame(caller, Thread.currentThread())
            assertFalse(cancellation.isCancelled())
        }
    }

    @Test fun cancellationStopsHttpAndDiscardsLateResultBeforeReturning() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val cancellation = ProfileHttpCancellation()
            val started = CountDownLatch(1)
            val stop = CountDownLatch(1)
            val discarded = AtomicBoolean(false)
            val job = launch {
                BatchPhaseExecutor(dispatcher).run(cancellation, discard = { _: String -> discarded.set(true) }) {
                    cancellation.register { stop.countDown() }.use {
                        started.countDown()
                        check(stop.await(5, TimeUnit.SECONDS))
                        "late"
                    }
                }
                fail("Cancelled result delivered")
            }
            // Yield the caller so its child can dispatch the phase.
            kotlinx.coroutines.yield()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(cancellation.isCancelled())
            assertTrue(discarded.get())
        }
    }
}
