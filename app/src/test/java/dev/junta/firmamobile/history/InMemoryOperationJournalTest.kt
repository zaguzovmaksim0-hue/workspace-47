package dev.junta.firmamobile.history

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOperationJournalTest {
    @Test
    fun newJournalIsEmptyAndDefaultCapacityIsSmall() {
        val journal = InMemoryOperationJournal()
        assertTrue(journal.snapshot().entries.isEmpty())

        repeat(InMemoryOperationJournal.DEFAULT_CAPACITY + 1) { append(journal) }

        val entries = journal.snapshot().entries
        assertEquals(32, InMemoryOperationJournal.DEFAULT_CAPACITY)
        assertEquals(32, entries.size)
        assertEquals(2L, entries.first().sequence)
    }

    @Test
    fun capacitiesHaveClosedBounds() {
        assertEquals(64, InMemoryOperationJournal.MAX_CAPACITY)
        listOf(Int.MIN_VALUE, -1, 0, 65, Int.MAX_VALUE).forEach { capacity ->
            assertThrows(IllegalArgumentException::class.java) {
                InMemoryOperationJournal(capacity)
            }
        }
        assertTrue(InMemoryOperationJournal(1).snapshot().entries.isEmpty())
        assertTrue(InMemoryOperationJournal(64).snapshot().entries.isEmpty())
    }

    @Test
    fun capacityOneEvictsBeforeRetainingTheNextEntry() {
        val journal = InMemoryOperationJournal(1)
        val first = append(journal)
        val frozen = journal.snapshot()
        val second = append(journal)

        assertEquals(listOf(first), frozen.entries)
        assertEquals(listOf(second), journal.snapshot().entries)
        assertEquals(2L, second.sequence)
    }

    @Test
    fun maximumCapacityEvictsOnlyOldestEntriesInAppendOrder() {
        val journal = InMemoryOperationJournal(64)
        repeat(67) { append(journal) }

        assertEquals((4L..67L).toList(), journal.snapshot().entries.map { it.sequence })
    }

    @Test
    fun equalBackwardAndNegativeInstantsNeverReorderTheLocalSequence() {
        val journal = InMemoryOperationJournal()
        val times = listOf(Instant.MAX, Instant.EPOCH, Instant.EPOCH, Instant.MIN)
        times.forEach { append(journal, it) }

        val entries = journal.snapshot().entries
        assertEquals((1L..4L).toList(), entries.map { it.sequence })
        assertEquals(times, entries.map { it.occurredAt })
    }

    @Test
    fun clearIsIdempotentResetsSequenceAndCannotRevokeIssuedSnapshots() {
        val journal = InMemoryOperationJournal()
        val first = append(journal)
        val frozen = journal.snapshot()

        journal.clear()
        journal.clear()

        assertTrue(journal.snapshot().entries.isEmpty())
        assertEquals(listOf(first), frozen.entries)
        assertEquals(1L, append(journal).sequence)
        assertEquals(listOf(first), frozen.entries)
    }

    @Test
    fun snapshotsRejectMutationEvenThroughJavaMutableListViews() {
        val journal = InMemoryOperationJournal()
        val entry = append(journal)
        val frozen = journal.snapshot()
        val mutableView = frozen.entries as MutableList<OperationJournalEntry>

        assertThrows(UnsupportedOperationException::class.java) { mutableView.clear() }
        assertThrows(UnsupportedOperationException::class.java) { mutableView.add(entry) }
        assertThrows(UnsupportedOperationException::class.java) { mutableView[0] = entry }
        assertThrows(UnsupportedOperationException::class.java) {
            mutableView.listIterator().apply { next(); remove() }
        }
        assertEquals(listOf(entry), journal.snapshot().entries)
    }

    @Test
    fun snapshotsCopyTheirInputAndRejectAnOversizedCollection() {
        val entry = OperationJournalEntry(1L, Instant.EPOCH, OperationKind.SIGN, LocalOutcome.COMPLETED)
        val input = mutableListOf(entry)
        val snapshot = OperationJournalSnapshot(input)
        input.clear()

        assertEquals(listOf(entry), snapshot.entries)
        assertThrows(IllegalArgumentException::class.java) {
            OperationJournalSnapshot(List(65) { entry })
        }
    }

    @Test
    fun sequenceExhaustionFailsBeforeEvictionAndClearAllowsAFreshLocalSequence() {
        val journal = InMemoryOperationJournal(capacity = 1, initialSequence = Long.MAX_VALUE - 1L)
        val last = append(journal)
        val frozen = journal.snapshot()

        assertEquals(Long.MAX_VALUE, last.sequence)
        assertThrows(IllegalStateException::class.java) { append(journal) }
        assertEquals(frozen.entries, journal.snapshot().entries)

        journal.clear()
        assertEquals(1L, append(journal).sequence)
    }

    @Test
    fun invalidSequencesCannotBeConstructed() {
        listOf(Long.MIN_VALUE, -1L, 0L).forEach { sequence ->
            assertThrows(IllegalArgumentException::class.java) {
                OperationJournalEntry(sequence, Instant.EPOCH, OperationKind.SIGN, LocalOutcome.FAILED)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            InMemoryOperationJournal(capacity = 1, initialSequence = -1L)
        }
    }

    @Test(timeout = 30_000)
    fun concurrentAppendsAndSnapshotsAreBoundedAtomicAndOrdered() {
        val journal = InMemoryOperationJournal(64)
        val sequences = ConcurrentLinkedQueue<Long>()
        val pool = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        try {
            val writers = (0 until 4).map { writer ->
                pool.submit {
                    start.await()
                    repeat(128) { index ->
                        val time = Instant.ofEpochSecond((writer * 128 + index).toLong())
                        sequences.add(append(journal, time).sequence)
                    }
                }
            }
            val readers = (0 until 2).map {
                pool.submit {
                    start.await()
                    repeat(256) { assertOrderedAndBounded(journal.snapshot()) }
                }
            }
            start.countDown()
            (writers + readers).forEach { it.get(15, TimeUnit.SECONDS) }

            assertEquals((1L..512L).toList(), sequences.sorted())
            assertEquals((449L..512L).toList(), journal.snapshot().entries.map { it.sequence })
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 30_000)
    fun clearCanRunConcurrentlyWithAppendAndSnapshot() {
        val journal = InMemoryOperationJournal(64)
        val pool = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        try {
            val tasks = listOf(
                pool.submit { start.await(); repeat(256) { append(journal) } },
                pool.submit { start.await(); repeat(128) { journal.clear() } },
                pool.submit { start.await(); repeat(256) { assertOrderedAndBounded(journal.snapshot()) } },
            )
            start.countDown()
            tasks.forEach { it.get(15, TimeUnit.SECONDS) }
            journal.clear()
            assertTrue(journal.snapshot().entries.isEmpty())
            assertEquals(1L, append(journal).sequence)
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun assertOrderedAndBounded(snapshot: OperationJournalSnapshot) {
        assertTrue(snapshot.entries.size <= 64)
        assertTrue(snapshot.entries.all { it.sequence > 0L })
        snapshot.entries.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous.sequence + 1L, next.sequence)
        }
    }

    private fun append(
        journal: InMemoryOperationJournal,
        time: Instant = Instant.EPOCH,
    ): OperationJournalEntry = journal.append(OperationKind.SIGN, LocalOutcome.COMPLETED, time)
}
