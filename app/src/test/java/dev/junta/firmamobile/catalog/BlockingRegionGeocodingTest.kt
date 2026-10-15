package dev.junta.firmamobile.catalog

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingRegionGeocodingTest {
    @Test(timeout = 10_000)
    fun timeoutReturnsWhileLegacyGeocoderIsStillBlocked() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val request = async {
            withTimeoutOrNull(1_000) {
                blockingRegionGeocoding {
                    started.countDown()
                    try {
                        release.await(3, TimeUnit.SECONDS)
                        listOf("Andalucía")
                    } finally {
                        finished.countDown()
                    }
                }
            }
        }
        try {
            withContext(Dispatchers.IO) { assertTrue(started.await(2, TimeUnit.SECONDS)) }
            assertNull(request.await())
            assertEquals(1L, finished.count)
        } finally {
            release.countDown()
            withContext(Dispatchers.IO) { assertTrue(finished.await(2, TimeUnit.SECONDS)) }
        }
    }

    @Test(timeout = 10_000)
    fun cancellationReturnsWithoutWaitingForLegacyGeocoder() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val request = async {
            blockingRegionGeocoding {
                started.countDown()
                try {
                    release.await(3, TimeUnit.SECONDS)
                } finally {
                    finished.countDown()
                }
            }
        }
        try {
            withContext(Dispatchers.IO) { assertTrue(started.await(2, TimeUnit.SECONDS)) }
            request.cancelAndJoin()
            assertEquals(1L, finished.count)
        } finally {
            release.countDown()
            withContext(Dispatchers.IO) { assertTrue(finished.await(2, TimeUnit.SECONDS)) }
        }
    }

    @Test
    fun repeatedRequestsDoNotStartMoreBlockingCallsWhileGeocoderIsStuck() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = AtomicBoolean(false)
        val first = async {
            blockingRegionGeocoding {
                started.countDown()
                release.await(3, TimeUnit.SECONDS)
                "first"
            }
        }
        try {
            withContext(Dispatchers.IO) { assertTrue(started.await(2, TimeUnit.SECONDS)) }
            assertNull(withTimeoutOrNull(100) {
                blockingRegionGeocoding {
                    secondStarted.set(true)
                    "second"
                }
            })
        } finally {
            release.countDown()
            assertEquals("first", first.await())
        }
        assertFalse(secondStarted.get())
    }

    @Test
    fun completedGeocoderReturnsAddresses() = runBlocking {
        assertEquals(listOf("Andalucía"), blockingRegionGeocoding { listOf("Andalucía") })
    }

    @Test
    fun geocoderFailureReachesCaller() = runBlocking {
        val failure = IOException("Synthetic geocoder failure")
        val result = runCatching { blockingRegionGeocoding<List<String>> { throw failure } }
        // Coroutine stack-trace recovery may hand back a copy; the original stays the cause.
        val thrown = result.exceptionOrNull()
        assertTrue(thrown is IOException)
        assertEquals("Synthetic geocoder failure", thrown?.message)
    }
}
