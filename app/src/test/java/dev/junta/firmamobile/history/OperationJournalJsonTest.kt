package dev.junta.firmamobile.history

import java.lang.reflect.Modifier
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationJournalJsonTest {
    @Test
    fun emptyExportHasAnExactVersionedUtf8Representation() {
        val bytes = OperationJournalJson.toUtf8(InMemoryOperationJournal().snapshot())

        assertArrayEquals("{\"schemaVersion\":1,\"entries\":[]}\n".toByteArray(StandardCharsets.UTF_8), bytes)
    }

    @Test
    fun exportHasFixedKeyOrderAndPreservesNanosecondsAndAppendOrder() {
        val journal = InMemoryOperationJournal()
        journal.append(OperationKind.SIGN, LocalOutcome.COMPLETED, Instant.parse("2026-10-19T07:00:00.123456789Z"))
        journal.append(OperationKind.VERIFY, LocalOutcome.CANCELLED, Instant.ofEpochSecond(-1L))
        val expected = "{\"schemaVersion\":1,\"entries\":[" +
            "{\"sequence\":1,\"occurredAt\":\"2026-10-19T07:00:00.123456789Z\",\"kind\":\"SIGN\",\"outcome\":\"COMPLETED\"}," +
            "{\"sequence\":2,\"occurredAt\":\"1969-12-31T23:59:59Z\",\"kind\":\"VERIFY\",\"outcome\":\"CANCELLED\"}]}\n"

        assertEquals(expected, decode(OperationJournalJson.toUtf8(journal.snapshot())))
    }

    @Test
    fun everyTypedKindAndLocalOutcomeHasOnlyTheAllowlistedJsonKeys() {
        val journal = InMemoryOperationJournal()
        OperationKind.entries.forEach { kind ->
            LocalOutcome.entries.forEach { outcome -> journal.append(kind, outcome, Instant.EPOCH) }
        }
        val text = decode(OperationJournalJson.toUtf8(journal.snapshot()))
        val keys = Regex("\"([^\"]+)\":").findAll(text).map { it.groupValues[1] }.toSet()

        assertEquals(setOf("schemaVersion", "entries", "sequence", "occurredAt", "kind", "outcome"), keys)
        assertEquals(
            setOf("SIGN", "COSIGN", "COUNTERSIGN", "BATCH", "VERIFY", "IDENTIFY"),
            OperationKind.entries.map { it.name }.toSet(),
        )
        assertEquals(setOf("COMPLETED", "CANCELLED", "FAILED"), LocalOutcome.entries.map { it.name }.toSet())
        val rowPattern = Regex(
            "\\{\"sequence\":[1-9][0-9]*,\"occurredAt\":\"1970-01-01T00:00:00Z\",\"kind\":\"[A-Z]+\",\"outcome\":\"[A-Z]+\"}",
        )
        assertEquals(OperationKind.entries.size * LocalOutcome.entries.size, rowPattern.findAll(text).count())
    }

    @Test
    fun entryFieldsAndAppendInputsAreClosedTypedAndImmutableByConstruction() {
        val fields = OperationJournalEntry::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(
            mapOf(
                "sequence" to java.lang.Long.TYPE,
                "occurredAt" to Instant::class.java,
                "kind" to OperationKind::class.java,
                "outcome" to LocalOutcome::class.java,
            ),
            fields.associate { it.name to it.type },
        )
        assertTrue(fields.all { Modifier.isFinal(it.modifiers) })
        assertTrue(Modifier.isFinal(OperationJournalEntry::class.java.modifiers))
        assertTrue(OperationKind::class.java.isEnum)
        assertTrue(LocalOutcome::class.java.isEnum)
        val append = InMemoryOperationJournal::class.java.declaredMethods.single { it.name == "append" }
        assertArrayEquals(
            arrayOf(OperationKind::class.java, LocalOutcome::class.java, Instant::class.java),
            append.parameterTypes,
        )
    }

    @Test
    fun minimumAndMaximumInstantsDoNotNeedLossyOrOverflowingEpochMillis() {
        val journal = InMemoryOperationJournal()
        journal.append(OperationKind.COUNTERSIGN, LocalOutcome.FAILED, Instant.MIN)
        journal.append(OperationKind.IDENTIFY, LocalOutcome.COMPLETED, Instant.MAX)
        val text = decode(OperationJournalJson.toUtf8(journal.snapshot()))

        assertTrue(text.contains("\"occurredAt\":\"-1000000000-01-01T00:00:00Z\""))
        assertTrue(text.contains("\"occurredAt\":\"+1000000000-12-31T23:59:59.999999999Z\""))
    }

    @Test
    fun worstCaseFullExportStaysWithinTheFixedByteBoundWithoutTruncation() {
        val journal = InMemoryOperationJournal(64, Long.MAX_VALUE - 64L)
        val longestTime = Instant.MAX
        repeat(64) { journal.append(OperationKind.COUNTERSIGN, LocalOutcome.COMPLETED, longestTime) }
        val snapshot = journal.snapshot()
        val bytes = OperationJournalJson.toUtf8(snapshot)
        val text = decode(bytes)

        assertEquals(16_384, OperationJournalJson.MAX_EXPORT_BYTES)
        assertTrue(bytes.size <= 16_384)
        assertEquals(64, Regex("\"sequence\":").findAll(text).count())
        assertTrue(text.contains("\"sequence\":${Long.MAX_VALUE}"))
        assertArrayEquals(bytes, OperationJournalJson.toUtf8(snapshot))
        assertTrue(text.endsWith("]}\n"))
    }

    @Test
    fun serializationIsIndependentOfDefaultLocaleAndTimeZone() {
        val journal = InMemoryOperationJournal()
        journal.append(OperationKind.SIGN, LocalOutcome.COMPLETED, Instant.parse("2026-10-19T07:00:00Z"))
        val snapshot = journal.snapshot()
        val expected = OperationJournalJson.toUtf8(snapshot)
        val locale = Locale.getDefault()
        val timeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            assertArrayEquals(expected, OperationJournalJson.toUtf8(snapshot))
        } finally {
            Locale.setDefault(locale)
            TimeZone.setDefault(timeZone)
        }
    }

    @Test
    fun exportsAreDetachedByteArraysAndFrozenSnapshotsSurviveClearAndEviction() {
        val journal = InMemoryOperationJournal(1)
        journal.append(OperationKind.BATCH, LocalOutcome.FAILED, Instant.EPOCH)
        val snapshot = journal.snapshot()
        val expected = OperationJournalJson.toUtf8(snapshot)
        val tampered = OperationJournalJson.toUtf8(snapshot)
        tampered.fill(0)
        journal.append(OperationKind.COSIGN, LocalOutcome.CANCELLED, Instant.MAX)
        journal.clear()

        assertArrayEquals(expected, OperationJournalJson.toUtf8(snapshot))
        assertEquals("{\"schemaVersion\":1,\"entries\":[]}\n", decode(OperationJournalJson.toUtf8(journal.snapshot())))
    }

    private fun decode(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}
