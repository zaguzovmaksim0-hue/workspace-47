package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSignRequestStoreTest {
    private val clock = MutableClock(Instant.parse("2030-01-01T00:00:00Z"))
    private val monotonic = MutableMonotonicClock(1_000_000_000L)
    private val clearedCopies = mutableListOf<Boolean>()
    private val store = PendingSignRequestStore(
        clock = clock,
        lifetime = Duration.ofMinutes(2),
        observer = SensitiveSigningCopyObserver(clearedCopies::add),
        monotonicNanos = monotonic::nowNanos,
    )

    @Test
    fun requestIsSingleUseAndBoundToOriginProfileNavigationAndPayload() {
        val request = request()
        val pending = store.put(request)

        assertEquals(pending, store.peek())
        val changedNavigation = validation(request).copy(
            navigationId = NavigationId("navigation-2"),
        )
        assertEquals(
            ConsumeError.NAVIGATION_CHANGED,
            (store.consume(changedNavigation) as PendingConsumeResult.Rejected).error,
        )
        assertNull(store.peek())
        assertTrue(clearedCopies.isNotEmpty())
        assertTrue(clearedCopies.all { it })
    }

    @Test
    fun matchingRequestIsAcceptedOnceAndStoredCopiesAreCleared() {
        val request = request()
        store.put(request)

        val accepted = store.consume(validation(request)) as PendingConsumeResult.Accepted
        assertEquals(request.requestId, accepted.request.requestId)
        accepted.request.withPayload { payload ->
            assertArrayEquals(PAYLOAD, payload)
        }
        assertNull(store.peek())
        assertTrue(clearedCopies.all { it })

        val replay = store.consume(validation(request)) as PendingConsumeResult.Rejected
        assertEquals(ConsumeError.ALREADY_CONSUMED, replay.error)
        assertThrows(IllegalArgumentException::class.java) { store.put(request) }
    }

    @Test
    fun sourceAndAcceptedPayloadOwnersClearAndRejectAccessAfterClose() {
        val request = request(UUID.randomUUID())

        store.put(request)

        assertThrows(IllegalStateException::class.java) {
            request.withPayload { error("source payload remained accessible") }
        }
        val accepted = store.consume(validation(request)) as PendingConsumeResult.Accepted
        accepted.request.use { ownedRequest ->
            ownedRequest.withPayload { payload ->
                assertArrayEquals(PAYLOAD, payload)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            accepted.request.withPayload { error("accepted payload remained accessible") }
        }
        assertTrue(clearedCopies.isNotEmpty())
        assertTrue(clearedCopies.all { it })
    }

    @Test
    fun expiryAndEveryContextMismatchFailClosedAndClearState() {
        val mismatches = listOf<Pair<(PendingValidationContext) -> PendingValidationContext, ConsumeError>>(
            ({ context: PendingValidationContext -> context.copy(profileId = "other-profile") }) to
                ConsumeError.PROFILE_CHANGED,
            ({ context: PendingValidationContext -> context.copy(profileVersion = 2) }) to
                ConsumeError.PROFILE_CHANGED,
            ({ context: PendingValidationContext ->
                context.copy(origin = TrustedOrigin("https", "sede.juntadeandalucia.es", 443))
            }) to
                ConsumeError.ORIGIN_CHANGED,
            ({ context: PendingValidationContext ->
                context.copy(navigationId = NavigationId("other-navigation"))
            }) to
                ConsumeError.NAVIGATION_CHANGED,
            ({ context: PendingValidationContext ->
                context.copy(payloadFingerprint = fingerprint("changed".encodeToByteArray()))
            }) to
                ConsumeError.PAYLOAD_CHANGED,
        )
        mismatches.forEach { (change, expected) ->
            val localStore = PendingSignRequestStore(
                clock = clock,
                monotonicNanos = monotonic::nowNanos,
            )
            val request = request(UUID.randomUUID())
            localStore.put(request)
            val result = localStore.consume(change(validation(request)))
                as PendingConsumeResult.Rejected
            assertEquals(expected, result.error)
            assertNull(localStore.peek())
        }

        val expiringStore = PendingSignRequestStore(
            clock = clock,
            lifetime = Duration.ofMinutes(2),
            monotonicNanos = monotonic::nowNanos,
        )
        val expiringRequest = request(UUID.randomUUID())
        expiringStore.put(expiringRequest)
        monotonic.advance(Duration.ofMinutes(2))
        val expired = expiringStore.consume(validation(expiringRequest))
            as PendingConsumeResult.Rejected
        assertEquals(ConsumeError.REQUEST_EXPIRED, expired.error)
        assertNull(expiringStore.peek())
    }


    @Test
    fun civilClockJumpsDoNotAlterSecurityLifetimeButMonotonicBoundaryDoes() {
        val first = request(UUID.randomUUID())
        store.put(first)

        clock.advance(Duration.ofDays(30))
        val acceptedAfterCivilJump = store.consume(validation(first))
            as PendingConsumeResult.Accepted
        acceptedAfterCivilJump.request.close()

        val second = request(UUID.randomUUID())
        store.put(second)
        clock.rewind(Duration.ofDays(60))
        monotonic.advance(Duration.ofMinutes(2))

        val expired = store.consume(validation(second)) as PendingConsumeResult.Rejected
        assertEquals(ConsumeError.REQUEST_EXPIRED, expired.error)
    }

    @Test
    fun replayLedgerExpiresAndCapacityRecoversInsteadOfPermanentDenialOfService() {
        val localMonotonic = MutableMonotonicClock(50L)
        val localStore = PendingSignRequestStore(
            clock = clock,
            lifetime = Duration.ofMinutes(2),
            monotonicNanos = localMonotonic::nowNanos,
            replayRetention = Duration.ofMinutes(5),
            maxReplayEntries = 2,
        )
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val thirdId = UUID.randomUUID()

        fun consume(id: UUID) {
            val candidate = request(id, observedAtMonotonicNanos = localMonotonic.nowNanos())
            localStore.put(candidate)
            val accepted = localStore.consume(validation(candidate)) as PendingConsumeResult.Accepted
            accepted.request.close()
        }

        consume(firstId)
        consume(secondId)
        assertThrows(IllegalStateException::class.java) {
            localStore.put(request(thirdId, observedAtMonotonicNanos = localMonotonic.nowNanos()))
        }

        localMonotonic.advance(Duration.ofMinutes(5))
        consume(thirdId)
        consume(firstId)
    }

    @Test
    fun replacementAndExplicitClearZeroOwnedCopies() {
        store.put(request(UUID.randomUUID()))
        store.put(request(UUID.randomUUID()))
        store.clear(ConsumeError.PROFILE_CHANGED)

        assertNull(store.peek())
        assertTrue(clearedCopies.size >= 4)
        assertTrue(clearedCopies.all { it })
    }

    @Test
    fun oversizedPayloadIsRejectedBeforeRetention() {
        val oversized = request(
            payload = ByteArray(PendingSignRequestStore.MAX_PAYLOAD_BYTES + 1),
        )

        assertThrows(IllegalArgumentException::class.java) { store.put(oversized) }
        assertNull(store.peek())
    }

    private fun request(
        requestId: UUID = REQUEST_ID,
        payload: ByteArray = PAYLOAD.copyOf(),
        observedAtMonotonicNanos: Long = monotonic.nowNanos(),
    ) = NormalizedSignRequest(
        requestId = requestId,
        protocolId = SigningProtocolId("junta-miniapplet-cades-v1"),
        context = SigningContext(
            profileId = "junta-andalucia",
            profileVersion = 1,
            origin = ORIGIN,
            navigationId = NavigationId("navigation-1"),
            observedAt = clock.instant(),
        ),
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format = SigningFormat.CADES,
        safeDescription = "Solicitud de acceso",
        payload = payload,
        observedAtMonotonicNanos = observedAtMonotonicNanos,
    )

    private fun validation(request: NormalizedSignRequest) = PendingValidationContext(
        requestId = request.requestId,
        profileId = request.context.profileId,
        profileVersion = request.context.profileVersion,
        origin = request.context.origin,
        navigationId = request.context.navigationId,
        payloadFingerprint = fingerprint(PAYLOAD),
    )

    private fun fingerprint(payload: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(payload)

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }

        fun rewind(duration: Duration) {
            current = current.minus(duration)
        }
    }

    private class MutableMonotonicClock(
        private var currentNanos: Long,
    ) {
        fun nowNanos(): Long = currentNanos

        fun advance(duration: Duration) {
            currentNanos += duration.toNanos()
        }
    }

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val ORIGIN = TrustedOrigin("https", "www.juntadeandalucia.es", 443)
        val PAYLOAD = "synthetic-signing-payload".encodeToByteArray()
    }
}
