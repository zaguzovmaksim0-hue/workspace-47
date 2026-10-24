package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.history.OperationKind
import dev.junta.firmamobile.verification.OfflineCmsFixtures
import dev.junta.firmamobile.verification.OfflineCmsResult
import dev.junta.firmamobile.verification.OfflineCmsStatus
import java.io.IOException
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineVerificationTerminalOutcomeTest {
    @Test
    fun allAcceptedResultsMapToOnlyCompletedOrFailedWithoutAnErrorPayload() = runTest {
        val events = mutableListOf<LocalOutcome>()
        var next = OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
        var throwPrivateError = false
        val owner = OfflineVerificationController(
            this, StandardTestDispatcher(testScheduler), onTerminal = events::add,
        ) { _, _ ->
            if (throwPrivateError) throw IOException("content://private/CERTIFICATE-NAME-HASH-CANARY")
            next
        }
        choose(owner, byteArrayOf(1))
        for (result in listOf(
            OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1),
            OfflineCmsResult(OfflineCmsStatus.INVALID),
            OfflineCmsResult(OfflineCmsStatus.UNSUPPORTED),
            OfflineCmsResult(OfflineCmsStatus.LIMIT_EXCEEDED),
            OfflineCmsResult(OfflineCmsStatus.DETACHED_CONTENT_REQUIRED),
            OfflineCmsResult(OfflineCmsStatus.VERIFIED, 0),
        )) {
            next = result
            owner.verify()
            advanceUntilIdle()
        }
        throwPrivateError = true
        owner.verify()
        advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.READ_FAILED, owner.state.value.outcome)
        assertEquals(listOf(
            LocalOutcome.COMPLETED, LocalOutcome.FAILED, LocalOutcome.FAILED,
            LocalOutcome.FAILED, LocalOutcome.FAILED, LocalOutcome.FAILED, LocalOutcome.FAILED,
        ), events)
        owner.close()
        assertEquals(7, events.size)
    }

    @Test
    fun aFailingJournalCallbackCannotChangeCryptoResultOrPreventCancellation() = runTest {
        var attempts = 0
        val owner = OfflineVerificationController(
            this, StandardTestDispatcher(testScheduler),
            onTerminal = { attempts++; throw IllegalStateException("private-journal-error-CANARY") },
        ) { _, _ -> OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1) }
        choose(owner, byteArrayOf(1))
        owner.verify()
        advanceUntilIdle()
        assertEquals(OfflineVerificationOutcome.VERIFIED, owner.state.value.outcome)
        assertEquals(1, owner.state.value.verifiedSigners)
        owner.verify()
        owner.cancel()
        owner.close()
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertEquals(OfflineVerificationState(), owner.state.value)
    }

    @Test
    fun actualExpiredSelfSignedCmsAppendsOnlyIntegrityMetadataAndMalformedCmsAppendsFailed() = runTest {
        val journal = InMemoryOperationJournal()
        val cms = OfflineCmsFixtures.signed(byteArrayOf(3), listOf(OfflineCmsFixtures.signer(notAfter = Date(1L))))
        val owner = OfflineVerificationController(
            this, StandardTestDispatcher(testScheduler),
            onTerminal = { journal.append(OperationKind.VERIFY, it, Instant.EPOCH) },
        )
        try {
            choose(owner, cms)
            assertTrue(journal.snapshot().entries.isEmpty())
            owner.verify()
            advanceUntilIdle()
            assertEquals(OfflineVerificationOutcome.VERIFIED, owner.state.value.outcome)
            choose(owner, byteArrayOf(0x30, 0x00))
            owner.verify()
            advanceUntilIdle()
            assertEquals(OfflineVerificationOutcome.INVALID, owner.state.value.outcome)
            owner.close()
            val entries = journal.snapshot().entries
            assertEquals(listOf(1L, 2L), entries.map { it.sequence })
            assertEquals(listOf(OperationKind.VERIFY, OperationKind.VERIFY), entries.map { it.kind })
            assertEquals(listOf(LocalOutcome.COMPLETED, LocalOutcome.FAILED), entries.map { it.outcome })
            assertEquals(listOf(Instant.EPOCH, Instant.EPOCH), entries.map { it.occurredAt })
        } finally {
            owner.close()
            cms.fill(0)
        }
    }

    private fun choose(owner: OfflineVerificationController, bytes: ByteArray) {
        owner.completeSelection(owner.beginSelection(OfflineInputKind.CMS)!!, SyntheticOfflineDocument(bytes))
    }
}
