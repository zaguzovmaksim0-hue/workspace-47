package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.history.OperationKind
import dev.junta.firmamobile.verification.OfflinePadesVerifierTest
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/** Real SAF-shaped bounded loader, verifier, controller and metadata-only journal. */
class OfflinePadesControllerTest {
    @Test fun validPdfPublishesTransientSignerAndOnlyTypedJournalOutcome() = runBlocking {
        val journal = InMemoryOperationJournal()
        val owner = OfflineVerificationController(this, Dispatchers.Unconfined, onTerminal = {
            journal.append(OperationKind.VERIFY, it, Instant.EPOCH)
        })
        var closed = false
        val bytes = OfflinePadesVerifierTest.signedPdf()
        val source = object : OfflineDocumentSource {
            override fun declaredSize(): Long? = null
            override fun open() = OfflineOpenedDocument(null, object : ByteArrayInputStream(bytes) {
                override fun close() { closed = true; super.close() }
            })
        }
        owner.completeSelection(owner.beginSelection(OfflineInputKind.CMS)!!, source)
        owner.verify(); yield()
        assertEquals(OfflineVerificationOutcome.VERIFIED, owner.state.value.outcome)
        assertTrue(owner.state.value.document!!.signatures.single().signer.contains("Synthetic PDF signer"))
        assertTrue(closed)
        val entry = journal.snapshot().entries.single()
        assertEquals(OperationKind.VERIFY, entry.kind)
        assertEquals(LocalOutcome.COMPLETED, entry.outcome)
        assertFalse(entry.toString().contains("Synthetic"))
        owner.cancel()
        assertNull(owner.state.value.document)
        assertEquals(1, journal.snapshot().entries.size)
        owner.close()
    }

    @Test fun dispatchDoesNotIgnoreOptionalOriginalOrRaiseCmsBudget() = runBlocking {
        val owner = OfflineVerificationController(this, Dispatchers.Unconfined)
        fun choose(kind: OfflineInputKind, bytes: ByteArray) {
            val source = object : OfflineDocumentSource {
                override fun declaredSize() = bytes.size.toLong()
                override fun open() = OfflineOpenedDocument(null, ByteArrayInputStream(bytes))
            }
            owner.completeSelection(owner.beginSelection(kind)!!, source)
        }
        choose(OfflineInputKind.CMS, OfflinePadesVerifierTest.signedPdf())
        choose(OfflineInputKind.ORIGINAL, byteArrayOf())
        owner.verify(); yield()
        assertEquals(OfflineVerificationOutcome.INVALID, owner.state.value.outcome)
        owner.clearOriginal()
        choose(OfflineInputKind.CMS, ByteArray(1_048_577))
        owner.verify(); yield()
        assertEquals(OfflineVerificationOutcome.LIMIT_EXCEEDED, owner.state.value.outcome)
        owner.close()
    }

    @Test fun unsignedChangesAreFailedInJournalEvenWhenOldSignatureVerifies() = runBlocking {
        val events = mutableListOf<LocalOutcome>()
        val owner = OfflineVerificationController(this, Dispatchers.Unconfined, onTerminal = events::add)
        val bytes = OfflinePadesVerifierTest.signedPdf() + "\n% unsigned\n".toByteArray()
        val source = object : OfflineDocumentSource {
            override fun declaredSize() = bytes.size.toLong()
            override fun open() = OfflineOpenedDocument(null, ByteArrayInputStream(bytes))
        }
        owner.completeSelection(owner.beginSelection(OfflineInputKind.CMS)!!, source)
        owner.verify(); yield()
        assertEquals(OfflineVerificationOutcome.INVALID, owner.state.value.outcome)
        assertTrue(owner.state.value.document!!.signatures.single().verified)
        assertTrue(owner.state.value.document!!.unsignedChanges)
        assertEquals(listOf(LocalOutcome.FAILED), events)
        owner.close()
        assertNull(owner.state.value.document)
    }
}
