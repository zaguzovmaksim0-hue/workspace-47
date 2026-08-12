package dev.junta.firmamobile.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.CountDownLatch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileHttpCallPhaseTrackerTest {
    private val call = OkHttpClient().newCall(
        Request.Builder().url("https://ws024.juntadeandalucia.es/").build(),
    )
    private val address = InetSocketAddress("217.12.21.226", 443)

    @Test
    fun requestHeadersStartConservativelyClosesTheFallbackWindow() {
        val tracker = ProfileHttpCallPhaseTracker()
        tracker.connectStart(call, address, Proxy.NO_PROXY)
        tracker.secureConnectStart(call)
        assertTrue(tracker.failure(ProfileHttpFailure.NETWORK_ERROR).safeForRouteFallback)

        tracker.requestHeadersStart(call)

        val failure = tracker.failure(ProfileHttpFailure.NETWORK_ERROR)
        assertEquals(ProfileHttpFailurePhase.HTTP_WRITE_STARTED, failure.phase)
        assertTrue(failure.httpWriteStarted)
        assertFalse(failure.safeForRouteFallback)
    }

    @Test
    fun aRaceCanOnlyBecomeUnsafeNeverFallbackSafe() {
        val cancellation = ProfileHttpCancellation()
        val tracker = ProfileHttpCallPhaseTracker()
        assertTrue(cancellation.beginAttempt(tracker))
        tracker.requestHeadersStart(call)
        cancellation.cancel()
        assertFalse(cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR).safeForRouteFallback)
        assertFalse(cancellation.beginAttempt(ProfileHttpCallPhaseTracker()))
    }

    @Test
    fun safeFirstAttemptCanRebindAndSnapshotsFollowTheSecondTracker() {
        val cancellation = ProfileHttpCancellation()
        val firstTracker = ProfileHttpCallPhaseTracker()
        assertTrue(cancellation.beginAttempt(firstTracker))
        firstTracker.connectStart(call, address, Proxy.NO_PROXY)
        assertTrue(cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR).safeForRouteFallback)

        val secondTracker = ProfileHttpCallPhaseTracker()
        assertTrue(cancellation.beginAttempt(secondTracker))
        secondTracker.requestHeadersStart(call)

        val failure = cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR)
        assertEquals(ProfileHttpFailurePhase.HTTP_WRITE_STARTED, failure.phase)
        assertFalse(failure.safeForRouteFallback)
    }

    @Test
    fun cancellationDuringDnsTcpOrTlsPreservesTheExactPreHeaderPhase() {
        val dns = ProfileHttpCancellation()
        assertTrue(dns.beginAttempt(ProfileHttpCallPhaseTracker()))
        dns.cancel()
        assertEquals(
            ProfileHttpFailurePhase.DNS_BEFORE_CONNECT,
            dns.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR).phase,
        )

        val tcpTracker = ProfileHttpCallPhaseTracker()
        val tcp = ProfileHttpCancellation()
        assertTrue(tcp.beginAttempt(tcpTracker))
        tcpTracker.connectStart(call, address, Proxy.NO_PROXY)
        tcp.cancel()
        assertEquals(
            ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES,
            tcp.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR).phase,
        )

        val tlsTracker = ProfileHttpCallPhaseTracker()
        val tls = ProfileHttpCancellation()
        assertTrue(tls.beginAttempt(tlsTracker))
        tlsTracker.connectStart(call, address, Proxy.NO_PROXY)
        tlsTracker.secureConnectStart(call)
        tls.cancel()
        assertEquals(
            ProfileHttpFailurePhase.TLS_BEFORE_HTTP_BYTES,
            tls.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR).phase,
        )
    }

    @Test
    fun cancellationAfterRequestHeadersStartIsNeverFallbackSafe() {
        val cancellation = ProfileHttpCancellation()
        val tracker = ProfileHttpCallPhaseTracker()
        assertTrue(cancellation.beginAttempt(tracker))
        tracker.requestHeadersStart(call)
        cancellation.cancel()

        val failure = cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR)
        assertEquals(ProfileHttpFailurePhase.HTTP_WRITE_STARTED, failure.phase)
        assertFalse(failure.safeForRouteFallback)
    }

    @Test
    fun concurrentTimeoutAndRequestHeaderRaceCanNeverReturnASafePhase() {
        repeat(100) {
            val cancellation = ProfileHttpCancellation()
            val tracker = ProfileHttpCallPhaseTracker()
            assertTrue(cancellation.beginAttempt(tracker))
            val start = CountDownLatch(1)
            val headers = Thread {
                start.await()
                tracker.requestHeadersStart(call)
            }
            val timeout = Thread {
                start.await()
                cancellation.cancel()
            }
            headers.start()
            timeout.start()
            start.countDown()
            headers.join()
            timeout.join()

            assertFalse(cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR).safeForRouteFallback)
        }
    }
}
