package dev.junta.firmamobile.browser

import dev.junta.firmamobile.signing.LocalSignature
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertTrue
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
        val registry = MiniAppletReplyRegistry()
        val requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val postStarted = CountDownLatch(1)
        val releasePost = CountDownLatch(1)
        val channel = checkNotNull(
            registry.create(requestId, NavigationId("navigation-1")) {
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
}
