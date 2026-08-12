package dev.junta.firmamobile.browser

import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.SigningContext
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Instant
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MiniAppletReplyRegistryTest {
    @Test
    fun abandonAllDoesNotCancelAConcurrentSuccessThatAlreadyWonTerminalState() {
        val origin = TrustedOrigin("https", "www.juntadeandalucia.es", 443)
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { 1L },
            currentOrigin = { origin },
        )
        val requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val postStarted = CountDownLatch(1)
        val releasePost = CountDownLatch(1)
        val channel = checkNotNull(
            registry.create(requestId, context(origin)) {
                postStarted.countDown()
                releasePost.await(5, TimeUnit.SECONDS)
            },
        )
        val successThread = thread(start = true) {
            channel.success(LocalSignature(byteArrayOf(1)), byteArrayOf(2))
        }

        assertTrue(postStarted.await(5, TimeUnit.SECONDS))
        assertTrue(registry.abandonAll().isEmpty())

        releasePost.countDown()
        successThread.join(5_000)
    }

    @Test
    fun concurrentSuccessAndFailureProduceExactlyOneTerminalDelivery() {
        val origin = TrustedOrigin("https", "www.juntadeandalucia.es", 443)
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { 1L },
            currentOrigin = { origin },
        )
        val posted = mutableListOf<String>()
        val channel = checkNotNull(
            registry.create(UUID.randomUUID(), context(origin), posted::add),
        )
        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val success = AtomicReference<Boolean>()
        val failure = AtomicReference<Boolean>()
        val successThread = thread(start = true) {
            ready.countDown()
            start.await(5, TimeUnit.SECONDS)
            success.set(channel.success(LocalSignature(byteArrayOf(1)), byteArrayOf(2)))
        }
        val failureThread = thread(start = true) {
            ready.countDown()
            start.await(5, TimeUnit.SECONDS)
            failure.set(
                channel.failure(dev.junta.firmamobile.signing.SigningErrorCode.USER_CANCELLED),
            )
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        successThread.join(5_000)
        failureThread.join(5_000)

        assertEquals(1, listOf(success.get(), failure.get()).count { it == true })
        assertEquals(1, posted.size)
        assertTrue(registry.abandonAll().isEmpty())
    }

    @Test
    fun epochChangeMakesAReplyTerminalWithoutDelivery() {
        val trusted = TrustedOrigin("https", "reg.redsara.es", 443)
        val origin: TrustedOrigin? = trusted
        var epoch = 7L
        val posted = mutableListOf<String>()
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { epoch },
            currentOrigin = { origin },
        )
        val requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val channel = checkNotNull(registry.create(requestId, context(trusted, epoch), posted::add))

        epoch++
        assertTrue(!channel.success(LocalSignature(byteArrayOf(1)), byteArrayOf(2)))
        assertTrue(posted.isEmpty())
        assertTrue(registry.abandonAll().isEmpty())

    }

    @Test
    fun originChangeMakesAReplyTerminalWithoutDelivery() {
        val trusted = TrustedOrigin("https", "reg.redsara.es", 443)
        var origin: TrustedOrigin? = trusted
        val posted = mutableListOf<String>()
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { 7L },
            currentOrigin = { origin },
        )
        val requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val channel = checkNotNull(registry.create(requestId, context(trusted, 7L), posted::add))

        origin = TrustedOrigin("https", "www.juntadeandalucia.es", 443)
        assertTrue(!channel.success(LocalSignature(byteArrayOf(1)), byteArrayOf(2)))
        assertTrue(posted.isEmpty())
        assertTrue(registry.abandonAll().isEmpty())
    }

    @Test
    fun requestIdCannotBeReplayedAfterTerminalDelivery() {
        val origin = TrustedOrigin("https", "reg.redsara.es", 443)
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { 7L },
            currentOrigin = { origin },
        )
        val requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val posted = mutableListOf<String>()
        val first = checkNotNull(registry.create(requestId, context(origin, 7L), posted::add))

        assertTrue(first.failure(dev.junta.firmamobile.signing.SigningErrorCode.USER_CANCELLED))
        assertNull(registry.create(requestId, context(origin, 7L), posted::add))
    }

    @Test
    fun expiredCallbackCannotDeliver() {
        val origin = TrustedOrigin("https", "reg.redsara.es", 443)
        val monotonic = MutableMonotonicClock(100L)
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { 7L },
            currentOrigin = { origin },
            monotonicNanos = monotonic::nowNanos,
        )
        val requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val posted = mutableListOf<String>()
        val channel = checkNotNull(registry.create(requestId, context(origin, 7L), posted::add))

        monotonic.advance(Duration.ofMinutes(2))
        assertTrue(!channel.failure(dev.junta.firmamobile.signing.SigningErrorCode.REQUEST_EXPIRED))
        assertTrue(posted.isEmpty())
    }


    @Test
    fun replyReplayLedgerPrunesByMonotonicTtlAndRecoversCapacity() {
        val origin = TrustedOrigin("https", "reg.redsara.es", 443)
        val monotonic = MutableMonotonicClock(1_000L)
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { 7L },
            currentOrigin = { origin },
            monotonicNanos = monotonic::nowNanos,
            replayRetention = Duration.ofMinutes(5),
            maxReplayEntries = 2,
        )
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val thirdId = UUID.randomUUID()
        val posted = mutableListOf<String>()

        fun terminate(id: UUID) {
            val channel = checkNotNull(registry.create(id, context(origin, 7L), posted::add))
            assertTrue(channel.failure(dev.junta.firmamobile.signing.SigningErrorCode.USER_CANCELLED))
        }

        terminate(firstId)
        terminate(secondId)
        assertNull(registry.create(thirdId, context(origin, 7L), posted::add))

        monotonic.advance(Duration.ofMinutes(5))
        terminate(thirdId)
        terminate(firstId)
    }

    private fun context(
        origin: TrustedOrigin,
        epoch: Long = 1L,
    ) = SigningContext(
        profileId = if (origin.host == "reg.redsara.es") "reg-age-redsara" else "junta-andalucia",
        profileVersion = 1,
        origin = origin,
        navigationId = NavigationId("navigation-1"),
        navigationEpoch = epoch,
        observedAt = Instant.parse("2030-01-01T00:00:00Z"),
    )

    private class MutableMonotonicClock(
        private var currentNanos: Long,
    ) {
        fun nowNanos(): Long = currentNanos
        fun advance(duration: Duration) {
            currentNanos += duration.toNanos()
        }
    }
}
