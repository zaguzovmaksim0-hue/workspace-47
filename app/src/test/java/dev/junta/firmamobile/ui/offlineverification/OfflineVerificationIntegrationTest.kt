package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.verification.OfflineCmsFixtures
import dev.junta.firmamobile.verification.OfflineCmsOptions
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs the actual existing verifier through the UI's real loader/controller path. */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineVerificationIntegrationTest {
    @Test
    fun expiredSelfSignedAttachedSignatureOnlyProducesIntegrityCount() = runTest {
        val cms = OfflineCmsFixtures.signed(byteArrayOf(3), listOf(OfflineCmsFixtures.signer(notAfter = Date(1L))))
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler))
        choose(owner, OfflineInputKind.CMS, cms)
        assertNull(owner.state.value.outcome)
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.VERIFIED, owner.state.value.outcome)
        assertEquals(1, owner.state.value.verifiedSigners)
        owner.close()
        cms.fill(0)
    }

    @Test
    fun detachedEmptyContentIsMissingUntilAnEmptyOriginalIsExplicitlySelected() = runTest {
        val cms = OfflineCmsFixtures.signed(
            byteArrayOf(), listOf(OfflineCmsFixtures.signer()), OfflineCmsOptions(encapsulate = false),
        )
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler))
        choose(owner, OfflineInputKind.CMS, cms)
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.DETACHED_CONTENT_REQUIRED, owner.state.value.outcome)
        choose(owner, OfflineInputKind.ORIGINAL, byteArrayOf())
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.VERIFIED, owner.state.value.outcome)
        owner.clearOriginal(); owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.DETACHED_CONTENT_REQUIRED, owner.state.value.outcome)
        owner.close()
        cms.fill(0)
    }

    @Test
    fun attachedWithExtraneousOriginalFailsClosedAndDoesNotSilentlyIgnoreEvenEmptyBytes() = runTest {
        val cms = OfflineCmsFixtures.signed(byteArrayOf(5), listOf(OfflineCmsFixtures.signer()))
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler))
        choose(owner, OfflineInputKind.CMS, cms)
        choose(owner, OfflineInputKind.ORIGINAL, byteArrayOf())
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.INVALID, owner.state.value.outcome)
        assertEquals(0, owner.state.value.verifiedSigners)
        owner.clearOriginal(); owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.VERIFIED, owner.state.value.outcome)
        owner.close()
        cms.fill(0)
    }

    @Test
    fun detachedWrongContentMalformedCmsAndUnsupportedAlgorithmCannotClaimIntegrity() = runTest {
        val signer = OfflineCmsFixtures.signer()
        val detached = OfflineCmsFixtures.signed(byteArrayOf(6), listOf(signer), OfflineCmsOptions(encapsulate = false))
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler))
        choose(owner, OfflineInputKind.CMS, detached)
        choose(owner, OfflineInputKind.ORIGINAL, byteArrayOf(7))
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.INVALID, owner.state.value.outcome)
        owner.clearOriginal()
        choose(owner, OfflineInputKind.CMS, byteArrayOf(0x30, 0x00))
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.INVALID, owner.state.value.outcome)
        val unsupported = OfflineCmsFixtures.signed(byteArrayOf(8), listOf(OfflineCmsFixtures.signer("SHA1withRSA")))
        choose(owner, OfflineInputKind.CMS, unsupported)
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.UNSUPPORTED, owner.state.value.outcome)
        assertEquals(0, owner.state.value.verifiedSigners)
        owner.close()
        detached.fill(0); unsupported.fill(0)
    }

    private fun choose(owner: OfflineVerificationController, kind: OfflineInputKind, bytes: ByteArray) {
        owner.completeSelection(owner.beginSelection(kind)!!, SyntheticOfflineDocument(bytes))
    }
}
