package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.verification.OfflineCmsResult
import dev.junta.firmamobile.verification.OfflineCmsStatus
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineVerificationLifecycleTest {
    @Test
    fun missingAndSelectedEmptyOriginalAreDifferentAndClearDoesNotVerify() = runTest {
        val lengths = mutableListOf<Int?>()
        val snapshots = mutableListOf<ByteArray>()
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { cms, original ->
            snapshots.add(cms)
            original?.let(snapshots::add)
            lengths.add(original?.size)
            if (original == null) OfflineCmsResult(OfflineCmsStatus.DETACHED_CONTENT_REQUIRED)
            else OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
        }
        choose(owner, OfflineInputKind.CMS, SyntheticOfflineDocument(byteArrayOf(1)))
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.DETACHED_CONTENT_REQUIRED, owner.state.value.outcome)
        choose(owner, OfflineInputKind.ORIGINAL, SyntheticOfflineDocument(byteArrayOf()))
        assertNull(owner.state.value.outcome)
        assertEquals(1, lengths.size)
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.VERIFIED, owner.state.value.outcome)
        owner.clearOriginal()
        assertNull(owner.state.value.outcome)
        assertEquals(2, lengths.size)
        owner.verify(); advanceUntilIdle()
        assertEquals(listOf(null, 0, null), lengths)
        assertTrue(snapshots.all { bytes -> bytes.all { it == 0.toByte() } })
        owner.close()
    }

    @Test
    fun stalePickerResultAndResultAfterDismissCannotRestoreASelection() = runTest {
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler))
        val first = owner.beginSelection(OfflineInputKind.CMS)!!
        val second = owner.beginSelection(OfflineInputKind.CMS)!!
        val source = SyntheticOfflineDocument(byteArrayOf(5))
        owner.completeSelection(first, source)
        assertFalse(owner.state.value.cmsSelected)
        owner.completeSelection(second, source)
        assertTrue(owner.state.value.cmsSelected)
        owner.close()
        owner.completeSelection(second, source)
        owner.verify()
        advanceUntilIdle()
        assertEquals(OfflineVerificationState(), owner.state.value)
        assertNull(owner.beginSelection(OfflineInputKind.CMS))
        assertEquals(0, source.opens)
    }

    @Test
    fun replacementDuringNonpreemptibleCryptoRejectsOldCompletionAndCleansSnapshot() = runTest {
        lateinit var owner: OfflineVerificationController
        var oldSnapshot: ByteArray? = null
        val replacement = SyntheticOfflineDocument(byteArrayOf(2))
        owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { cms, _ ->
            if (cms[0] == 1.toByte()) {
                oldSnapshot = cms
                // Same test UI thread: simulate new input arriving just before crypto returns.
                choose(owner, OfflineInputKind.CMS, replacement)
                OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
            } else OfflineCmsResult(OfflineCmsStatus.VERIFIED, 2)
        }
        choose(owner, OfflineInputKind.CMS, SyntheticOfflineDocument(byteArrayOf(1)))
        owner.verify(); advanceUntilIdle()
        assertTrue(oldSnapshot!!.all { it == 0.toByte() })
        assertNull(owner.state.value.outcome)
        assertFalse(owner.state.value.busy)
        owner.verify(); advanceUntilIdle()
        assertEquals(2, owner.state.value.verifiedSigners)
        owner.close()
    }

    @Test
    fun cancelDuringReadClosesAndClearsStreamWithoutInvokingCrypto() = runTest {
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { _, _ ->
            error("crypto must not run")
        }
        val source = SyntheticOfflineDocument(byteArrayOf(9), beforeRead = owner::cancel)
        choose(owner, OfflineInputKind.CMS, source)
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationState(), owner.state.value)
        assertEquals(1, source.closes)
        assertTrue(source.buffers.all { bytes -> bytes.all { it == 0.toByte() } })
        owner.close()
    }

    @Test
    fun dismissDuringCryptoSuppressesCompletionAndClearsBothOwnedInputs() = runTest {
        lateinit var owner: OfflineVerificationController
        val captured = mutableListOf<ByteArray>()
        owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { cms, original ->
            captured.add(cms); captured.add(original!!)
            owner.close()
            OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
        }
        choose(owner, OfflineInputKind.CMS, SyntheticOfflineDocument(byteArrayOf(11)))
        choose(owner, OfflineInputKind.ORIGINAL, SyntheticOfflineDocument(byteArrayOf(12)))
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationState(), owner.state.value)
        assertEquals(2, captured.size)
        assertTrue(captured.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    @Test
    fun cancelledPendingVerificationDoesNotOpenAndDuplicateVerifyDoesNotQueueWork() = runTest {
        var calls = 0
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { _, _ ->
            calls++; OfflineCmsResult(OfflineCmsStatus.INVALID)
        }
        val source = SyntheticOfflineDocument(byteArrayOf(1))
        choose(owner, OfflineInputKind.CMS, source)
        owner.verify(); owner.cancel(); advanceUntilIdle()
        assertEquals(0, source.opens)
        choose(owner, OfflineInputKind.CMS, source)
        owner.verify(); owner.verify(); advanceUntilIdle()
        assertEquals(1, calls)
        owner.close()
    }

    @Test
    fun privateExceptionsAreReducedToEnumAndOwnedInputsAreCleared() = runTest {
        val captured = mutableListOf<ByteArray>()
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { cms, original ->
            captured.add(cms); captured.add(original!!)
            throw IOException("content://private/document.p7m CERTIFICATE-DIGEST-CANARY")
        }
        choose(owner, OfflineInputKind.CMS, SyntheticOfflineDocument(byteArrayOf(41)))
        choose(owner, OfflineInputKind.ORIGINAL, SyntheticOfflineDocument(byteArrayOf(42)))
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.READ_FAILED, owner.state.value.outcome)
        assertEquals(0, owner.state.value.verifiedSigners)
        assertFalse(owner.state.value.toString().contains("CANARY"))
        assertTrue(captured.all { bytes -> bytes.all { it == 0.toByte() } })
        owner.close()
    }

    @Test
    fun impossibleSuccessCountsFailClosedAndFailureNeverCarriesPartialCount() = runTest {
        var returned = OfflineCmsResult(OfflineCmsStatus.VERIFIED, 0)
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { _, _ -> returned }
        choose(owner, OfflineInputKind.CMS, SyntheticOfflineDocument(byteArrayOf(1)))
        for (count in listOf(0, 9, Int.MAX_VALUE, -1)) {
            returned = OfflineCmsResult(OfflineCmsStatus.VERIFIED, count)
            owner.verify(); advanceUntilIdle()
            assertEquals(OfflineVerificationOutcome.INVALID, owner.state.value.outcome)
            assertEquals(0, owner.state.value.verifiedSigners)
        }
        returned = OfflineCmsResult(OfflineCmsStatus.INVALID, 7)
        owner.verify(); advanceUntilIdle()
        assertEquals(0, owner.state.value.verifiedSigners)
        owner.close()
    }

    @Test
    fun cancelledOptionalPickerAndFailedCmsPickerKeepOnlySafeSelectionState() = runTest {
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler))
        choose(owner, OfflineInputKind.CMS, SyntheticOfflineDocument(byteArrayOf(1)))
        val optional = owner.beginSelection(OfflineInputKind.ORIGINAL)!!
        owner.completeSelection(optional, null)
        assertTrue(owner.state.value.canVerify)
        assertFalse(owner.state.value.originalSelected)
        assertNull(owner.state.value.outcome)
        val replacement = owner.beginSelection(OfflineInputKind.CMS)!!
        owner.selectionFailed(replacement)
        assertEquals(OfflineVerificationOutcome.READ_FAILED, owner.state.value.outcome)
        assertFalse(owner.state.value.cmsSelected)
        owner.completeSelection(replacement, SyntheticOfflineDocument(byteArrayOf(2)))
        assertFalse(owner.state.value.cmsSelected)
        owner.close()
    }

    @Test
    fun declaredLimitBecomesSafeLimitOutcomeWithoutInvokingCrypto() = runTest {
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { _, _ ->
            error("oversize must never reach crypto")
        }
        val source = SyntheticOfflineDocument(byteArrayOf(), 1_048_577L)
        choose(owner, OfflineInputKind.CMS, source)
        owner.verify(); advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.LIMIT_EXCEEDED, owner.state.value.outcome)
        assertEquals(0, owner.state.value.verifiedSigners)
        assertEquals(0, source.opens)
        owner.close()
    }

    private fun choose(owner: OfflineVerificationController, kind: OfflineInputKind, source: OfflineDocumentSource?) {
        owner.completeSelection(owner.beginSelection(kind)!!, source)
    }
}
