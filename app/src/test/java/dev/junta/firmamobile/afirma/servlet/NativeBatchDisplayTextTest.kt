package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBatchDisplayTextTest {
    @Test
    fun whitespaceAndIsoControlsCollapseAndTrim() {
        assertEquals(
            "A B C",
            NativeBatchDisplayText.bounded(
                "\t \u0000A\r\n\u0007B\u0085\u00A0\u2003C\u2028\u2029 \u007F",
                512
            )
        )
        assertEquals(
            "",
            NativeBatchDisplayText.bounded("\t\r\n\u0000\u0085\u00A0\u2003\u2028\u2029", 1)
        )
        assertEquals("a", NativeBatchDisplayText.bounded(" a \t\u0000", 1))
    }

    @Test
    fun formatAndBidiControlsAreRemoved() {
        assertEquals(
            "ABCDE",
            NativeBatchDisplayText.bounded(
                "\u202EA\u202CB\u2066C\u2069\u200DD\u200BE\u200F\uFEFF\u00AD",
                5
            )
        )
        assertEquals("a b", NativeBatchDisplayText.bounded("a \u200E\t\u202Eb", 3))
        assertEquals(
            "Жé中\uD83D\uDE00",
            NativeBatchDisplayText.bounded("\u202EЖ\u202Cé\u2066中\u2069\u200D\uD83D\uDE00\uFEFF", 4)
        )
        assertEquals("", NativeBatchDisplayText.bounded("\u200E\u202E\u202C\u2066\u2069\uFEFF", 1))
    }

    @Test
    fun emojiBoundariesPreserveSupplementaryCodePoints() {
        val result = NativeBatchDisplayText.bounded("A\uD83D\uDE00B\uD83D\uDE80C", 4)
        assertEquals("A\uD83D\uDE00B\u2026", result)
        assertEquals(4, Character.codePointCount(result, 0, result.length))
        assertEquals(
            "\uD83D\uDE00\u2026",
            NativeBatchDisplayText.bounded("\uD83D\uDE00\uD83D\uDE80X", 2)
        )
        assertEquals(
            "\uD83D\uDE00\uD83D\uDE80",
            NativeBatchDisplayText.bounded("\uD83D\uDE00\uD83D\uDE80", 2)
        )
        assertEquals("\uD83D\uDE00", NativeBatchDisplayText.bounded("\uD83D\uDE00", 1))
        assertEquals("\u2026", NativeBatchDisplayText.bounded("\uD83D\uDE00X", 1))
    }

    @Test
    fun loneSurrogatesBecomeReplacementCharacters() {
        assertEquals(
            "\uFFFDA\uFFFDB\uFFFD\uFFFD",
            NativeBatchDisplayText.bounded("\uD800A\uDC00B\uD800\uD800", 512)
        )
        assertEquals(
            "\uFFFD\uD83D\uDE00\uFFFD",
            NativeBatchDisplayText.bounded("\uDC00\uD83D\uDE00\uD800", 512)
        )
        assertEquals("\uFFFD\u2026", NativeBatchDisplayText.bounded("\uD800AB", 2))
    }

    @Test
    fun exactAndOverBoundsIncludeEllipsisWithinLimit() {
        assertEquals("abc", NativeBatchDisplayText.bounded("abc", 3))
        assertEquals("ab\u2026", NativeBatchDisplayText.bounded("abcd", 3))
        assertEquals("a", NativeBatchDisplayText.bounded("a", 1))
        assertEquals("\u2026", NativeBatchDisplayText.bounded("ab", 1))
        assertEquals("", NativeBatchDisplayText.bounded("", 1))
        assertEquals("a b", NativeBatchDisplayText.bounded("  a \t b \r\n ", 3))
        assertEquals("a\u2026", NativeBatchDisplayText.bounded("a b", 2))
        assertEquals("a", NativeBatchDisplayText.bounded("a \u200E\t", 1))
    }

    @Test
    fun maxArgumentRangeIsEnforced() {
        for (limit in intArrayOf(Int.MIN_VALUE, -1, 0, 513, Int.MAX_VALUE)) {
            val failure = runCatching {
                NativeBatchDisplayText.bounded("", limit)
            }.exceptionOrNull()
            assertTrue("maxCodePoints=$limit", failure is IllegalArgumentException)
        }
        assertEquals("x", NativeBatchDisplayText.bounded("x", 1))
        assertEquals("x", NativeBatchDisplayText.bounded("x", 512))
    }
}
