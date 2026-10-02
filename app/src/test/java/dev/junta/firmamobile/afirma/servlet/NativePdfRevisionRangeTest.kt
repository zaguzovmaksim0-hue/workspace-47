package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class NativePdfRevisionRangeTest {
    private val prefix = "%PDF-1.7\nobj\n"
    private val tail = "\nbody\n%%EOF"
    private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

    private fun pdf(gap: String = "<0A>", tail: String = this.tail,
                    later: String = ""): Pair<ByteArray, IntArray> {
        val revision = prefix + gap + tail
        return ascii(revision + later) to
            intArrayOf(0, prefix.length, prefix.length + gap.length, tail.length)
    }

    private fun rejected(document: ByteArray, range: IntArray) {
        assertNull(NativePdfRevisionRange.revisionEnd(document, range))
        assertNull(NativePdfRevisionRange.extract(document, range))
    }

    @Test fun latestRevisionAcceptsPdfWhitespace() {
        for (spaces in listOf("", " \t\r\n\u000C\u0000", " ".repeat(32))) {
            val (document, range) = pdf(tail = tail + spaces)
            assertEquals(document.size, NativePdfRevisionRange.revisionEnd(document, range))
            assertArrayEquals(ascii(prefix + tail + spaces),
                NativePdfRevisionRange.extract(document, range))
        }
    }

    @Test fun historicalRevisionExcludesLaterBytes() {
        for (later in listOf("\n1 0 obj\nlater\nendobj\n%%EOF", "\nunfinished revision")) {
            val (document, range) = pdf(tail = tail + "\n", later = later)
            assertEquals(document.size - later.length,
                NativePdfRevisionRange.revisionEnd(document, range))
            assertArrayEquals(ascii(prefix + tail + "\n"),
                NativePdfRevisionRange.extract(document, range))
        }
    }

    @Test fun mixedCaseHexIsAccepted() {
        val (document, range) = pdf(gap = "<0123456789aBcDeF>")
        assertEquals(document.size, NativePdfRevisionRange.revisionEnd(document, range))
        assertArrayEquals(ascii(prefix + tail), NativePdfRevisionRange.extract(document, range))
    }

    @Test fun missingEofAndInvalidSuffixesAreRejected() {
        for (suffix in listOf("\nbody\n", "\n%%EO", "\n%%EOFx", "\n%%EOF\nbody",
            "\n%%EOF" + " ".repeat(33), "\n%%EOF" + " ".repeat(100), "\n%%EOF\u000B")) {
            val (document, range) = pdf(tail = suffix)
            rejected(document, range)
        }
    }

    @Test fun invalidGapsAreRejected() {
        for (gap in listOf("00", "[00]", "<>", "<0>", "<000>", "<GG>",
            "<0 A0>", "<0\tA0>", "<0\nA0>")) {
            val (document, range) = pdf(gap = gap)
            rejected(document, range)
        }
        val (document, range) = pdf()
        document[range[1] + 1] = 0xFF.toByte()
        rejected(document, range)
    }

    @Test fun invalidRangesAreRejectedWithoutOverflow() {
        val (document, range) = pdf()
        val invalid = listOf(
            intArrayOf(),
            intArrayOf(0, range[1], range[2]),
            intArrayOf(0, range[1], range[2], range[3], 0),
            range.copyOf().apply { this[0] = 1 },
            intArrayOf(0, -1, range[2], range[3]),
            intArrayOf(0, 4, range[2], range[3]),
            intArrayOf(0, range[1], -1, 1),
            intArrayOf(0, range[1], range[1] - 1, range[3]),
            intArrayOf(0, range[1], range[1] + 3, range[3]),
            intArrayOf(0, Int.MAX_VALUE, range[2], 1),
            intArrayOf(0, range[1], Int.MAX_VALUE, 1),
            intArrayOf(0, range[1], range[2], Int.MAX_VALUE),
            intArrayOf(0, range[1], range[2], -1),
            intArrayOf(0, range[1], range[2], 0),
            intArrayOf(0, range[1], document.size + 1, 1),
            intArrayOf(0, range[1], range[2], document.size - range[2] + 1)
        )
        for (badRange in invalid) rejected(document, badRange)
    }

    @Test fun headerAndDocumentLimitsAreEnforced() {
        val (document, range) = pdf()
        rejected(ascii("%PDF"), range)
        for (i in 0..4) rejected(document.copyOf().apply { this[i] = 0 }, range)
        val maximum = document.copyOf(2_097_152)
        ascii("%%EOF").copyInto(maximum, maximum.size - 5)
        val maximumRange = range.copyOf().apply { this[3] = maximum.size - this[2] }
        assertEquals(maximum.size, NativePdfRevisionRange.revisionEnd(maximum, maximumRange))
        rejected(maximum.copyOf(2_097_153), maximumRange)
    }

    @Test fun extractReturnsAnIndependentCopy() {
        val (document, range) = pdf()
        val result = NativePdfRevisionRange.extract(document, range)!!
        val original = document.copyOf()
        val expected = result.copyOf()
        assertNotSame(document, result)
        result[0] = 0
        assertArrayEquals(original, document)
        document[document.lastIndex] = 0
        assertEquals(expected.last(), result.last())
    }
}
