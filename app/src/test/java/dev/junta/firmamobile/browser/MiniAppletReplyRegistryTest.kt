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
import kotlin.concurrent.thread
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
        val clock = MutableClock(Instant.parse("2030-01-01T00:00:00Z"))
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { 7L },
            currentOrigin = { origin },
            clock = clock,
        )
        val requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val posted = mutableListOf<String>()
        val channel = checkNotNull(registry.create(requestId, context(origin, 7L), posted::add))

        clock.advance(Duration.ofMinutes(2))
        assertTrue(!channel.failure(dev.junta.firmamobile.signing.SigningErrorCode.REQUEST_EXPIRED))
        assertTrue(posted.isEmpty())
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

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
