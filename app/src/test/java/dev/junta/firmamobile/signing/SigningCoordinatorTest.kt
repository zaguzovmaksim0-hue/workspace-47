package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.SensitiveCertificateFingerprintObserver
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningCoordinatorTest {
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val identity = syntheticIdentity()
    private val session = CertificateSession(clock).apply { unlock(identity) }
    private val adapter = RecordingAdapter()
    private val engine = RecordingEngine()
    private val expiryScheduler = ControlledExpiryScheduler()
    private var activeOrigin: TrustedOrigin? = PORTAL_ORIGIN
    private val coordinator = SigningCoordinator(
        certificateSession = session,
        adapter = adapter,
        localSignatureEngine = engine,
        currentOrigin = { activeOrigin },
        clock = clock,
        expiryScheduler = expiryScheduler,
    )

    @Test
    fun preparePublishesOnlySafeConfirmationAndDoesNotStartSigning() {
        val reply = RecordingReply(REQUEST_ID)

        val result = coordinator.prepare(request(), reply)

        assertEquals(SigningPreparationResult.Ready(REQUEST_ID), result)
        val state = coordinator.state.value as SigningUiState.AwaitingConfirmation
        assertEquals(REQUEST_ID, state.requestId)
        assertEquals("www.juntadeandalucia.es", state.siteHost)
        assertEquals("Junta de Andalucía", state.profileName)
        assertEquals("EXPERIMENTAL", state.supportLevel)
        assertEquals("Autenticación con certificado", state.safeDescription)
        assertEquals("CAdES", state.format)
        assertEquals("SHA256withRSA", state.algorithm)
        assertEquals(identity.summary.ownerName, state.certificateOwner)
        assertFalse(state.requiresLegacySha1Warning)
        assertTrue(adapter.events.isEmpty())
        assertTrue(engine.events.isEmpty())
        assertTrue(reply.events.isEmpty())
        assertFalse(state.toString().contains(PAYLOAD.decodeToString()))
    }

    @Test
    fun explicitConfirmRunsPreSignLocalSignPostSignAndOneShotDeliveryInOrder() = runTest {
        val reply = RecordingReply(REQUEST_ID)
        coordinator.prepare(request(), reply)

        val result = coordinator.confirm(REQUEST_ID)

        assertEquals(SigningExecutionResult.Delivered(REQUEST_ID), result)
        assertEquals(listOf("pre", "post"), adapter.events)
        assertEquals(listOf("local"), engine.events)
        assertEquals(listOf("success"), reply.events)
        assertArrayEquals(FINAL_SIGNATURE, reply.deliveredSignature)
        assertEquals(SigningUiState.Completed(REQUEST_ID), coordinator.state.value)

        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.INVALID_REQUEST),
            coordinator.confirm(REQUEST_ID),
        )
        assertEquals(listOf("success"), reply.events)
    }

    @Test
    fun changedOriginAfterConfirmationFailsBeforeNetworkOrPrivateKeyUse() = runTest {
        val reply = RecordingReply(REQUEST_ID)
        coordinator.prepare(request(), reply)
        activeOrigin = TrustedOrigin("https", "sede.juntadeandalucia.es", 443)

        val result = coordinator.confirm(REQUEST_ID)

        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.ORIGIN_NOT_ALLOWED),
            result,
        )
        assertTrue(adapter.events.isEmpty())
        assertTrue(engine.events.isEmpty())
        assertEquals(listOf("failure:ORIGIN_NOT_ALLOWED"), reply.events)
    }

    @Test
    fun changedOrLockedCertificateAfterConfirmationFailsBeforeNetwork() = runTest {
        val reply = RecordingReply(REQUEST_ID)
        coordinator.prepare(request(), reply)
        session.unlock(freshSyntheticIdentity())

        val result = coordinator.confirm(REQUEST_ID)

        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.CERTIFICATE_LOCKED),
            result,
        )
        assertTrue(adapter.events.isEmpty())
        assertTrue(engine.events.isEmpty())
        assertEquals(listOf("failure:CERTIFICATE_LOCKED"), reply.events)
    }

    @Test
    fun userCancelConsumesPendingRequestAndNeverSigns() = runTest {
        val reply = RecordingReply(REQUEST_ID)
        coordinator.prepare(request(), reply)

        assertTrue(coordinator.cancel(SigningCancelReason.USER, REQUEST_ID))

        assertEquals(SigningUiState.Idle, coordinator.state.value)
        assertEquals(listOf("failure:USER_CANCELLED"), reply.events)
        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.INVALID_REQUEST),
            coordinator.confirm(REQUEST_ID),
        )
        assertTrue(adapter.events.isEmpty())
        assertTrue(engine.events.isEmpty())
    }

    @Test
    fun pendingConfirmationExpiresAndClearsSensitiveStateWithoutUserAction() {
        val mutableClock = MutableClock(NOW)
        val storedCopyClears = mutableListOf<Boolean>()
        val certificateFingerprintClears = mutableListOf<Boolean>()
        val localSession = CertificateSession(
            clock = mutableClock,
            fingerprintObserver = SensitiveCertificateFingerprintObserver(
                certificateFingerprintClears::add,
            ),
        ).apply { unlock(identity) }
        val localScheduler = ControlledExpiryScheduler()
        val localCoordinator = SigningCoordinator(
            certificateSession = localSession,
            adapter = RecordingAdapter(),
            localSignatureEngine = RecordingEngine(),
            currentOrigin = { PORTAL_ORIGIN },
            clock = mutableClock,
            pendingStore = PendingSignRequestStore(
                clock = mutableClock,
                observer = SensitiveSigningCopyObserver(storedCopyClears::add),
            ),
            expiryScheduler = localScheduler,
        )
        val reply = RecordingReply(REQUEST_ID)
        localCoordinator.prepare(request(), reply)

        mutableClock.advance(Duration.ofMinutes(2))
        localScheduler.runPending()

        assertEquals(
            SigningUiState.Failed(REQUEST_ID, SigningErrorCode.REQUEST_EXPIRED),
            localCoordinator.state.value,
        )
        assertEquals(listOf("failure:REQUEST_EXPIRED"), reply.events)
        assertEquals(listOf(true, true), storedCopyClears)
        assertEquals(listOf(true), certificateFingerprintClears)
        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.INVALID_REQUEST),
            runBlocking { localCoordinator.confirm(REQUEST_ID) },
        )
        assertEquals(listOf("failure:REQUEST_EXPIRED"), reply.events)
    }

    @Test
    fun sha1RequestRequiresAVisibleLegacyWarning() {
        val reply = RecordingReply(REQUEST_ID)

        coordinator.prepare(
            request(algorithm = SigningAlgorithm.SHA1_WITH_RSA),
            reply,
        )

        val state = coordinator.state.value as SigningUiState.AwaitingConfirmation
        assertTrue(state.requiresLegacySha1Warning)
        assertEquals("SHA1withRSA", state.algorithm)
    }

    @Test
    fun cancellationThatStartsBeforeDeliveryAlwaysBeatsConcurrentSuccess() {
        val originProvider = BlockingFinalOriginProvider(PORTAL_ORIGIN)
        val localSession = CertificateSession(clock).apply { unlock(identity) }
        val localEngine = RecordingEngine()
        val localCoordinator = SigningCoordinator(
            certificateSession = localSession,
            adapter = RecordingAdapter(),
            localSignatureEngine = localEngine,
            currentOrigin = originProvider::current,
            clock = clock,
            expiryScheduler = ControlledExpiryScheduler(),
        )
        val reply = BlockingFailureReply(REQUEST_ID)
        localCoordinator.prepare(request(), reply)
        val result = AtomicReference<SigningExecutionResult>()
        val confirmThread = thread(start = true) {
            result.set(runBlocking { localCoordinator.confirm(REQUEST_ID) })
        }

        assertTrue(originProvider.finalCheckEntered.await(5, TimeUnit.SECONDS))
        val cancelThread = thread(start = true) {
            localCoordinator.cancel(SigningCancelReason.USER, REQUEST_ID)
        }
        assertTrue(reply.failureEntered.await(5, TimeUnit.SECONDS))
        originProvider.releaseFinalCheck.countDown()
        confirmThread.join(5_000)
        reply.releaseFailure.countDown()
        cancelThread.join(5_000)

        assertEquals(listOf("failure:USER_CANCELLED"), reply.events)
        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.USER_CANCELLED),
            result.get(),
        )
        assertTrue(localEngine.events.isEmpty())
    }

    @Test
    fun requestExpiryDuringPreSignClosesPreSignWithoutUsingPrivateKey() = runTest {
        val mutableClock = MutableClock(NOW)
        val localSession = CertificateSession(mutableClock).apply { unlock(identity) }
        val preSignState = TrackingPreSignState()
        val localAdapter = MutatingAdapter(
            preSignState = preSignState,
            onPrepare = { mutableClock.advance(Duration.ofMinutes(2)) },
        )
        val localEngine = RecordingEngine()
        val localCoordinator = SigningCoordinator(
            certificateSession = localSession,
            adapter = localAdapter,
            localSignatureEngine = localEngine,
            currentOrigin = { PORTAL_ORIGIN },
            clock = mutableClock,
            expiryScheduler = ControlledExpiryScheduler(),
        )
        val reply = RecordingReply(REQUEST_ID)
        localCoordinator.prepare(request(), reply)

        val result = localCoordinator.confirm(REQUEST_ID)

        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.REQUEST_EXPIRED),
            result,
        )
        assertTrue(localEngine.events.isEmpty())
        assertTrue(preSignState.closed.get())
        assertEquals(listOf("failure:REQUEST_EXPIRED"), reply.events)
    }

    @Test
    fun certificateLockDuringPreSignClosesPreSignWithoutUsingPrivateKey() = runTest {
        val localSession = CertificateSession(clock).apply { unlock(identity) }
        val preSignState = TrackingPreSignState()
        val localAdapter = MutatingAdapter(
            preSignState = preSignState,
            onPrepare = localSession::lock,
        )
        val localEngine = RecordingEngine()
        val localCoordinator = SigningCoordinator(
            certificateSession = localSession,
            adapter = localAdapter,
            localSignatureEngine = localEngine,
            currentOrigin = { PORTAL_ORIGIN },
            clock = clock,
            expiryScheduler = ControlledExpiryScheduler(),
        )
        val reply = RecordingReply(REQUEST_ID)
        localCoordinator.prepare(request(), reply)

        val result = localCoordinator.confirm(REQUEST_ID)

        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.CERTIFICATE_LOCKED),
            result,
        )
        assertTrue(localEngine.events.isEmpty())
        assertTrue(preSignState.closed.get())
        assertEquals(listOf("failure:CERTIFICATE_LOCKED"), reply.events)
    }

    @Test
    fun originChangeDuringPreSignClosesPreSignWithoutUsingPrivateKey() = runTest {
        var origin: TrustedOrigin? = PORTAL_ORIGIN
        val preSignState = TrackingPreSignState()
        val localAdapter = MutatingAdapter(
            preSignState = preSignState,
            onPrepare = {
                origin = TrustedOrigin("https", "sede.juntadeandalucia.es", 443)
            },
        )
        val localEngine = RecordingEngine()
        val localCoordinator = SigningCoordinator(
            certificateSession = CertificateSession(clock).apply { unlock(identity) },
            adapter = localAdapter,
            localSignatureEngine = localEngine,
            currentOrigin = { origin },
            clock = clock,
            expiryScheduler = ControlledExpiryScheduler(),
        )
        val reply = RecordingReply(REQUEST_ID)
        localCoordinator.prepare(request(), reply)

        val result = localCoordinator.confirm(REQUEST_ID)

        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.NAVIGATION_CHANGED),
            result,
        )
        assertTrue(localEngine.events.isEmpty())
        assertTrue(preSignState.closed.get())
        assertEquals(listOf("failure:NAVIGATION_CHANGED"), reply.events)
    }

    @Test
    fun requestExpiryDuringPostSignClosesResultWithoutDeliveringSuccess() = runTest {
        val mutableClock = MutableClock(NOW)
        val finalSignatureCleared = AtomicBoolean(false)
        val localAdapter = MutatingAdapter(
            preSignState = TrackingPreSignState(),
            onComplete = { mutableClock.advance(Duration.ofMinutes(2)) },
            finalSignatureCleared = finalSignatureCleared,
        )
        val localEngine = RecordingEngine()
        val localCoordinator = SigningCoordinator(
            certificateSession = CertificateSession(mutableClock).apply { unlock(identity) },
            adapter = localAdapter,
            localSignatureEngine = localEngine,
            currentOrigin = { PORTAL_ORIGIN },
            clock = mutableClock,
            expiryScheduler = ControlledExpiryScheduler(),
        )
        val reply = RecordingReply(REQUEST_ID)
        localCoordinator.prepare(request(), reply)

        val result = localCoordinator.confirm(REQUEST_ID)

        assertEquals(
            SigningExecutionResult.Failed(SigningErrorCode.REQUEST_EXPIRED),
            result,
        )
        assertEquals(listOf("local"), localEngine.events)
        assertTrue(finalSignatureCleared.get())
        assertEquals(listOf("failure:REQUEST_EXPIRED"), reply.events)
    }

    private fun request(
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
    ) = NormalizedSignRequest(
        requestId = REQUEST_ID,
        protocolId = JuntaTriPhaseAdapter.ID,
        context = SigningContext(
            profileId = "junta-andalucia",
            profileVersion = 1,
            origin = PORTAL_ORIGIN,
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
            observedAt = NOW,
        ),
        algorithm = algorithm,
        format = SigningFormat.CADES,
        safeDescription = "Autenticación con certificado",
        payload = PAYLOAD.copyOf(),
    )

    private class RecordingAdapter : SigningProtocolAdapter {
        override val id = JuntaTriPhaseAdapter.ID
        val events = mutableListOf<String>()

        override suspend fun prepare(
            request: NormalizedSignRequest,
            certificateChain: List<X509Certificate>,
        ): ProtocolPrepareResult {
            events += "pre"
            return ProtocolPrepareResult.Success(
                PreSignResult(request, PRE_SIGN_INPUT.copyOf(), TestPreSignState()),
            )
        }

        override suspend fun complete(
            request: NormalizedSignRequest,
            preSign: PreSignResult,
            localSignature: LocalSignature,
        ): ProtocolCompletionResult {
            events += "post"
            checkNotNull(preSign.consumeState(request)).close()
            localSignature.close()
            preSign.close()
            return ProtocolCompletionResult.Success(LocalSignature(FINAL_SIGNATURE.copyOf()))
        }
    }

    private class RecordingEngine : LocalSignatureEngine {
        val events = mutableListOf<String>()

        override fun sign(
            input: ByteArray,
            identity: UnlockedIdentity,
            algorithm: SigningAlgorithm,
        ): LocalSignatureResult {
            events += "local"
            assertArrayEquals(PRE_SIGN_INPUT, input)
            return LocalSignatureResult.Success(LocalSignature(LOCAL_SIGNATURE.copyOf()))
        }
    }

    private class TestPreSignState : PreSignState {
        override fun close() = Unit
    }

    private class TrackingPreSignState : PreSignState {
        val closed = AtomicBoolean(false)

        override fun close() {
            closed.set(true)
        }
    }

    private class MutatingAdapter(
        private val preSignState: TrackingPreSignState,
        private val onPrepare: () -> Unit = {},
        private val onComplete: () -> Unit = {},
        private val finalSignatureCleared: AtomicBoolean = AtomicBoolean(false),
    ) : SigningProtocolAdapter {
        override val id = JuntaTriPhaseAdapter.ID

        override suspend fun prepare(
            request: NormalizedSignRequest,
            certificateChain: List<X509Certificate>,
        ): ProtocolPrepareResult {
            onPrepare()
            return ProtocolPrepareResult.Success(
                PreSignResult(request, PRE_SIGN_INPUT.copyOf(), preSignState),
            )
        }

        override suspend fun complete(
            request: NormalizedSignRequest,
            preSign: PreSignResult,
            localSignature: LocalSignature,
        ): ProtocolCompletionResult {
            onComplete()
            checkNotNull(preSign.consumeState(request)).close()
            return ProtocolCompletionResult.Success(
                LocalSignature(
                    FINAL_SIGNATURE.copyOf(),
                    SensitiveSignatureCopyObserver(finalSignatureCleared::set),
                ),
            )
        }
    }

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = Clock.fixed(current, zone)

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private class ControlledExpiryScheduler : SigningExpiryScheduler {
        private var task: ScheduledTask? = null

        override fun schedule(
            delay: Duration,
            action: () -> Unit,
        ): SigningExpiryHandle {
            check(task == null)
            check(!delay.isNegative && !delay.isZero)
            val scheduled = ScheduledTask(action)
            task = scheduled
            return SigningExpiryHandle {
                scheduled.cancelled.set(true)
                if (task === scheduled) task = null
            }
        }

        fun runPending() {
            val scheduled = checkNotNull(task)
            task = null
            if (!scheduled.cancelled.get()) scheduled.action()
        }

        private class ScheduledTask(
            val action: () -> Unit,
            val cancelled: AtomicBoolean = AtomicBoolean(false),
        )
    }

    private class RecordingReply(
        override val requestId: UUID,
    ) : SigningReplySink {
        private val terminal = AtomicBoolean(false)
        val events = mutableListOf<String>()
        var deliveredSignature: ByteArray? = null

        override fun success(signature: LocalSignature, certificateDer: ByteArray): Boolean {
            if (!terminal.compareAndSet(false, true)) {
                signature.close()
                certificateDer.fill(0)
                return false
            }
            deliveredSignature = signature.use { owned -> owned.withBytes(ByteArray::copyOf) }
            certificateDer.fill(0)
            events += "success"
            return true
        }

        override fun failure(code: SigningErrorCode): Boolean {
            if (!terminal.compareAndSet(false, true)) return false
            events += "failure:${code.name}"
            return true
        }

        override fun abandon(): Boolean = terminal.compareAndSet(false, true)
    }

    private class BlockingFinalOriginProvider(
        private val origin: TrustedOrigin,
    ) {
        private val calls = AtomicInteger()
        val finalCheckEntered = CountDownLatch(1)
        val releaseFinalCheck = CountDownLatch(1)

        fun current(): TrustedOrigin {
            if (calls.incrementAndGet() == FINAL_ORIGIN_CHECK_CALL) {
                finalCheckEntered.countDown()
                check(releaseFinalCheck.await(5, TimeUnit.SECONDS))
            }
            return origin
        }

        private companion object {
            const val FINAL_ORIGIN_CHECK_CALL = 4
        }
    }

    private class BlockingFailureReply(
        override val requestId: UUID,
    ) : SigningReplySink {
        private val terminal = AtomicBoolean(false)
        val failureEntered = CountDownLatch(1)
        val releaseFailure = CountDownLatch(1)
        val events = mutableListOf<String>()

        override fun success(signature: LocalSignature, certificateDer: ByteArray): Boolean {
            val won = terminal.compareAndSet(false, true)
            signature.close()
            certificateDer.fill(0)
            if (won) synchronized(events) { events += "success" }
            return won
        }

        override fun failure(code: SigningErrorCode): Boolean {
            failureEntered.countDown()
            check(releaseFailure.await(5, TimeUnit.SECONDS))
            val won = terminal.compareAndSet(false, true)
            if (won) synchronized(events) { events += "failure:${code.name}" }
            return won
        }

        override fun abandon(): Boolean = terminal.compareAndSet(false, true)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val PORTAL_ORIGIN = TrustedOrigin("https", "www.juntadeandalucia.es", 443)
        val PAYLOAD = "synthetic-coordinator-payload".encodeToByteArray()
        val PRE_SIGN_INPUT = "synthetic-pre-sign".encodeToByteArray()
        val LOCAL_SIGNATURE = byteArrayOf(1, 2, 3)
        val FINAL_SIGNATURE = byteArrayOf(4, 5, 6)
    }
}
