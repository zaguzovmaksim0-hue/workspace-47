package dev.junta.firmamobile.browser

import android.webkit.ClientCertRequest
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.freshSyntheticIdentity
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import javax.security.auth.x500.X500Principal
import org.junit.Assert.*
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
class InteractiveClientAuthControllerTest {
    @Test fun unconfiguredServerRequiresAnExplicitDecisionOnTheActualCallback() {
        val f = Fixture(); f.offer()
        assertEquals("auth.new-provider.example", f.prompt!!.server)
        assertEquals("https://portal.example", f.prompt!!.pageOrigin)
        assertTrue(f.prompt!!.canConfirm)
        assertEquals(0, f.request.proceeds)
        assertTrue(f.controller.confirm(f.prompt!!.token))
        assertEquals(1, f.request.proceeds)
        assertEquals(0, f.request.ignores)
        assertNotNull(f.request.chain)
        assertEquals(0, f.synthetic.encodedReads.get())
        assertNull(f.prompt)
    }

    @Test fun unlockingKeepsCallbackButDoesNotAuthorizeDisclosure() {
        val f = Fixture(); f.identity = null; f.offer()
        val lockedToken = f.prompt!!.token
        assertEquals(InteractiveClientAuthProblem.NO_CERTIFICATE, f.prompt!!.problem)
        assertFalse(f.controller.confirm(lockedToken))
        assertEquals(0, f.request.proceeds)
        f.identity = f.synthetic.identity; f.controller.refreshIdentity()
        assertTrue(f.prompt!!.canConfirm)
        assertNotEquals(lockedToken, f.prompt!!.token)
        assertFalse(f.controller.confirm(lockedToken))
        assertTrue(f.controller.confirm(f.prompt!!.token))
        assertEquals(1, f.request.proceeds)
    }

    @Test fun certificateReplacementCannotUseTheOldConfirmationEvenWithTheSameSubject() {
        val f = Fixture(); f.offer(); val oldToken = f.prompt!!.token
        f.identity = freshSyntheticIdentity()
        assertFalse(f.controller.confirm(oldToken))
        assertEquals(0, f.request.proceeds)
        assertNotEquals(oldToken, f.prompt!!.token)
        assertTrue(f.controller.confirm(f.prompt!!.token))
    }

    @Test fun lockBetweenDialogAndClickNeverSendsTheKey() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; f.identity = null
        assertFalse(f.controller.confirm(token)); assertEquals(0, f.request.proceeds)
        assertEquals(InteractiveClientAuthProblem.NO_CERTIFICATE, f.prompt!!.problem)
    }

    @Test fun cancellationUsesNonStickyIgnoreExactlyOnce() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token
        f.controller.cancel(token); f.controller.cancel(token); f.controller.cancelPending()
        assertFalse(f.controller.confirm(token))
        assertEquals(1, f.request.ignores); assertEquals(0, f.request.cancels); assertEquals(0, f.request.proceeds)
    }

    @Test fun duplicateClickAndRepeatedOfferOfTheSameCallbackDoNotDoubleRespond() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; f.offer()
        assertEquals(token, f.prompt!!.token); assertEquals(0, f.request.ignores)
        assertTrue(f.controller.confirm(token)); assertFalse(f.controller.confirm(token))
        assertEquals(1, f.request.proceeds); assertEquals(0, f.request.ignores)
    }

    @Test fun aSecondConcurrentRequestCannotReplaceTheFirstConsent() {
        val f = Fixture(); f.offer(); val first = f.prompt!!.token
        val second = RecordingRequest(); f.offer(second)
        assertEquals(1, second.ignores); assertEquals(first, f.prompt!!.token)
        assertTrue(f.controller.confirm(first)); assertEquals(0, second.proceeds)
    }

    @Test fun navigationMakesTheOutstandingDecisionStale() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; f.epoch++
        assertFalse(f.controller.confirm(token)); assertEquals(1, f.request.ignores); assertEquals(0, f.request.proceeds)
    }

    @Test fun rendererReplacementNeverReceivesAnOldCallback() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; f.activeOwner = Any()
        assertFalse(f.controller.confirm(token)); assertEquals(1, f.request.ignores)
    }

    @Test fun callbackForAnInactiveViewIsIgnoredImmediately() {
        val f = Fixture(); f.activeOwner = Any(); f.offer()
        assertNull(f.prompt); assertEquals(1, f.request.ignores)
    }

    @Test fun backgroundedUiCannotApprove() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; f.foreground = false
        assertFalse(f.controller.confirm(token)); assertEquals(0, f.request.proceeds)
        f.controller.onBackground(false)
        assertEquals(1, f.request.ignores)
    }

    @Test fun knownExternalReturnPreservesCallbackButNeverAutoApproves() {
        val f = Fixture(); f.identity = null; f.offer()
        f.foreground = false; f.controller.onBackground(true)
        assertEquals(0, f.request.ignores)
        f.identity = f.synthetic.identity; f.foreground = true; f.controller.onForeground(true)
        assertTrue(f.prompt!!.canConfirm); assertEquals(0, f.request.proceeds)
        assertTrue(f.controller.confirm(f.prompt!!.token))
    }

    @Test fun expiredExternalReturnCannotRestoreAStaleChallenge() {
        val f = Fixture(); f.offer(); f.controller.onBackground(true)
        f.controller.onForeground(false)
        assertNull(f.prompt); assertEquals(1, f.request.ignores); assertEquals(0, f.request.proceeds)
    }

    @Test fun unlockAndExternalReturnDoNotExtendTheFiveMinuteDeadline() {
        val f = Fixture(); f.identity = null; f.offer()
        f.controller.onBackground(true); f.now += Duration.ofMinutes(5).toNanos()
        f.identity = f.synthetic.identity; f.controller.onForeground(true)
        assertNull(f.prompt); assertEquals(1, f.request.ignores)
    }

    @Test fun clockRollbackFailsClosed() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; f.now--
        assertFalse(f.controller.confirm(token)); assertEquals(1, f.request.ignores)
    }

    @Test fun timerReleasesTheCallbackWithoutNeedingAnotherClick() {
        val f = Fixture(); f.offer(); f.scheduler.run(0)
        assertNull(f.prompt); assertEquals(1, f.request.ignores)
        assertEquals(listOf(InteractiveClientAuthProblem.EXPIRED), f.problems)
        f.controller.cancelPending(); assertEquals(1, f.request.ignores)
    }

    @Test fun staleTimeoutCannotCancelANewerRequest() {
        val f = Fixture(); f.offer(); f.controller.cancelPending()
        val second = RecordingRequest(); f.offer(second); val token = f.prompt!!.token
        f.scheduler.run(0, evenIfCanceled = true)
        assertEquals(token, f.prompt!!.token); assertEquals(0, second.ignores)
        assertTrue(f.controller.confirm(token))
    }

    @Test fun missingTimeoutSchedulerRefusesToHoldAnUnboundedCallback() {
        val f = Fixture(); f.scheduler.fail = true; f.offer()
        assertNull(f.prompt); assertEquals(1, f.request.ignores)
    }

    @Test fun requestedHostAndPortAreRecheckedAtConfirmation() {
        for (mutateHost in listOf(true, false)) {
            val f = Fixture(); f.offer(); val token = f.prompt!!.token
            if (mutateHost) f.request.hostname = "swapped.example" else f.request.serverPort = 8443
            assertFalse(f.controller.confirm(token)); assertEquals(0, f.request.proceeds); assertEquals(1, f.request.ignores)
        }
    }

    @Test fun uppercaseHostIsNormalizedAndNondefaultTlsPortIsShown() {
        val f = Fixture(); f.request.hostname = "AUTH.NEW-PROVIDER.EXAMPLE"; f.request.serverPort = 8443; f.offer()
        assertEquals("auth.new-provider.example:8443", f.prompt!!.server)
        assertTrue(f.controller.confirm(f.prompt!!.token))
    }

    @Test fun malformedHostsCannotBecomeConsentLabels() {
        val f = Fixture()
        for (host in listOf("user@auth.example", "auth.example/path", "auth.example\n", "localhost", "a.localhost", "a.local", "127.0.0.1", "[::1]", "*.example", "example.", "аuth.example")) {
            val request = RecordingRequest().apply { hostname = host }
            f.offer(request); assertEquals(host, 1, request.ignores); assertNull(f.prompt)
        }
    }

    @Test fun malformedPageContextOrTlsPortIsRejectedBeforePrompting() {
        val f = Fixture()
        for (url in listOf("http://portal.example", "javascript:alert(1)", "https://user:secret@portal.example", "https://localhost", "https://127.0.0.1")) {
            val request = RecordingRequest()
            f.controller.offer(f.owner, f.epoch, url, request)
            assertEquals(1, request.ignores); assertNull(f.prompt)
        }
        for (port in listOf(-1, 0, 65536)) {
            val request = RecordingRequest().apply { serverPort = port }; f.offer(request)
            assertEquals(1, request.ignores)
        }
    }

    @Test fun incompatibleKeyTypeIsVisibleAndCannotBeConfirmed() {
        val f = Fixture(); f.request.types = arrayOf("EC"); f.offer()
        assertEquals(InteractiveClientAuthProblem.INCOMPATIBLE_CERTIFICATE, f.prompt!!.problem)
        assertFalse(f.controller.confirm(f.prompt!!.token)); assertEquals(0, f.request.proceeds)
    }

    @Test fun mismatchedIssuerIsRejectedButAnEmptyIssuerListMeansAnyIssuer() {
        val f = Fixture(); f.request.issuers = arrayOf(X500Principal("CN=Wrong issuer")); f.offer()
        assertFalse(f.prompt!!.canConfirm)
        f.request.issuers = emptyArray(); f.controller.refreshIdentity()
        assertTrue(f.prompt!!.canConfirm); assertTrue(f.controller.confirm(f.prompt!!.token))
    }

    @Test fun requestProceedExceptionDoesNotCauseADoubleTerminalReply() {
        val f = Fixture(); f.request.throwOnProceed = true; f.offer()
        assertFalse(f.controller.confirm(f.prompt!!.token))
        assertEquals(1, f.request.proceeds); assertEquals(0, f.request.ignores); assertEquals(1, f.clears)
    }

    @Test fun positiveCacheIsClearedOnLockWithoutDeletingTheBrowser() {
        val f = Fixture(); f.offer(); f.controller.confirm(f.prompt!!.token)
        f.identity = null; f.controller.refreshIdentity(); f.controller.refreshIdentity()
        assertEquals(1, f.clears); assertSame(f.owner, f.activeOwner)
    }

    @Test fun unexpectedBackgroundClearsThePositiveChoiceOnlyOnce() {
        val f = Fixture(); f.offer(); f.controller.confirm(f.prompt!!.token)
        f.controller.onBackground(false); f.controller.onBackground(false); f.controller.close()
        assertEquals(1, f.clears)
    }

    @Test fun expectedExternalReturnKeepsUnchangedPositiveChoiceWithinItsOwnDeadline() {
        val f = Fixture(); f.offer(); f.controller.confirm(f.prompt!!.token)
        f.controller.onBackground(true); f.controller.onForeground(true)
        assertEquals(0, f.clears)
        f.scheduler.run(1); assertEquals(1, f.clears)
    }

    @Test fun certificateChangeOnReturnRevokesOldPositiveChoice() {
        val f = Fixture(); f.offer(); f.controller.confirm(f.prompt!!.token)
        f.controller.onBackground(true); f.identity = freshSyntheticIdentity(); f.controller.onForeground(true)
        assertEquals(1, f.clears)
    }

    @Test fun closedControllerCannotAcceptANewCallback() {
        val f = Fixture(); f.controller.close(); f.offer()
        assertNull(f.prompt); assertEquals(1, f.request.ignores)
    }

    @Test fun platformPreferenceBarrierPreventsDisclosure() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token; f.barrierReady = false
        assertFalse(f.controller.confirm(token)); assertEquals(0, f.request.proceeds)
        f.barrierReady = true; assertTrue(f.controller.confirm(token))
    }

    @Test fun additionalServersDoNotExtendTheEarliestCachedChoiceLifetime() {
        val f = Fixture(); f.offer(); f.controller.confirm(f.prompt!!.token)
        val second = RecordingRequest().apply { hostname = "second.example" }
        f.offer(second); f.controller.confirm(f.prompt!!.token)
        f.scheduler.run(1)
        assertEquals(1, f.clears)
    }

    @Test fun aCanceledOldCacheTimerCannotRevokeANewerChoice() {
        val f = Fixture(); f.offer(); f.controller.confirm(f.prompt!!.token)
        f.controller.revokeCachedChoice()
        val second = RecordingRequest(); f.offer(second); f.controller.confirm(f.prompt!!.token)
        f.scheduler.run(1, evenIfCanceled = true)
        assertEquals(1, f.clears)
    }

    @Test fun onlyOwnedTlsEndpointFailureReleasesPendingCallback() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token
        f.controller.onServerTlsError(f.owner, f.epoch, "https://unrelated.example/resource")
        f.controller.onServerTlsError(f.owner, f.epoch, null)
        f.controller.onServerTlsError(f.owner, f.epoch, "https://auth.new-provider.example:8443/")
        assertEquals(token, f.prompt!!.token); assertEquals(0, f.request.ignores)
        f.controller.onServerTlsError(f.owner, f.epoch, "https://auth.new-provider.example/path")
        assertNull(f.prompt); assertEquals(1, f.request.ignores)
    }

    @Test fun staleOwnerOrEpochTlsErrorCannotCancelCurrentCallback() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token
        f.controller.onServerTlsError(Any(), f.epoch, "https://auth.new-provider.example/")
        f.controller.onServerTlsError(f.owner, f.epoch - 1, "https://auth.new-provider.example/")
        assertEquals(token, f.prompt!!.token); assertEquals(0, f.request.ignores)
    }

    @Test fun malformedTlsErrorUrlCannotBeUsedAsCallbackOwnership() {
        val f = Fixture(); f.offer(); val token = f.prompt!!.token
        for (url in listOf("http://auth.new-provider.example", "https://user@auth.new-provider.example", "not a url")) {
            f.controller.onServerTlsError(f.owner, f.epoch, url)
        }
        assertEquals(token, f.prompt!!.token); assertEquals(0, f.request.ignores)
    }

    private class Fixture {
        val synthetic = nonExportableSyntheticIdentity()
        var identity: UnlockedIdentity? = synthetic.identity
        val owner = Any()
        var activeOwner = owner
        var epoch = 7L
        var now = 100L
        var foreground = true
        var barrierReady = true
        var clears = 0
        var prompt: InteractiveClientAuthPrompt? = null
        val problems = mutableListOf<InteractiveClientAuthProblem>()
        val request = RecordingRequest()
        val scheduler = FakeScheduler()
        val controller = InteractiveClientAuthController<Any>(
            isCurrent = { view, expectedEpoch -> view === activeOwner && expectedEpoch == epoch },
            identityProvider = { identity }, canRespond = { foreground && barrierReady },
            clearClientCertPreferences = { clears++ }, onPrompt = { prompt = it }, onProblem = { problems += it },
            scheduler = scheduler,
            clock = Clock.fixed(synthetic.identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC),
            monotonicNanos = { now },
        )
        fun offer(value: RecordingRequest = request) =
            controller.offer(owner, epoch, "https://portal.example/path?private-state=not-displayed#section", value)
    }

    private class FakeScheduler : ClientCertPreferenceTimeoutScheduler {
        data class Entry(val action: () -> Unit, var canceled: Boolean = false)
        val entries = mutableListOf<Entry>()
        var fail = false
        override fun schedule(delayMillis: Long, action: () -> Unit): ClientCertPreferenceTimeoutHandle {
            check(!fail)
            val entry = Entry(action); entries += entry
            return ClientCertPreferenceTimeoutHandle { entry.canceled = true }
        }
        fun run(index: Int, evenIfCanceled: Boolean = false) {
            val entry = entries[index]
            if (!entry.canceled || evenIfCanceled) entry.action()
        }
    }

    private class RecordingRequest : ClientCertRequest() {
        var hostname = "auth.new-provider.example"
        var serverPort = 443
        var types = arrayOf("RSA")
        var issuers = emptyArray<Principal>()
        var ignores = 0; var proceeds = 0; var cancels = 0
        var chain: Array<X509Certificate>? = null
        var throwOnProceed = false
        override fun getHost(): String = hostname
        override fun getPort(): Int = serverPort
        override fun getKeyTypes(): Array<String> = types
        override fun getPrincipals(): Array<Principal> = issuers
        override fun proceed(privateKey: PrivateKey, chain: Array<X509Certificate>) {
            proceeds++; this.chain = chain; check(!throwOnProceed)
        }
        override fun ignore() { ignores++ }
        override fun cancel() { cancels++ }
    }
}
