package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.*
import org.junit.Test

class NativePdfByteRangeTest {
    private val original = "%PDF-fixture".toByteArray()
    private fun bytes(gap: String = "<00aB>") = original + gap.toByteArray() + "trailer".toByteArray()
    private fun range(gap: String = "<00aB>") = intArrayOf(0, original.size, original.size + gap.length, 7)
    @Test fun includesTheOriginalAndAllBytesOutsideTheSingleHexGap() {
        val document = bytes(); val before = document.copyOf()
        assertArrayEquals(original + "trailer".toByteArray(), NativePdfByteRange.extract(document, range(), original))
        assertArrayEquals(before, document)
    }
    @Test fun rejectsWrongOriginalAndPartialCoverage() {
        assertNull(NativePdfByteRange.extract(bytes(), range(), "wrong".toByteArray()))
        assertNull(NativePdfByteRange.extract(bytes(), range().also { it[3]-- }, original))
        assertNull(NativePdfByteRange.extract(bytes(), range().also { it[0] = 1 }, original))
    }
    @Test fun rejectsNegativeOverflowAndUnorderedOffsetsWithoutThrowing() {
        for (r in listOf(intArrayOf(), intArrayOf(0, 1, 2), intArrayOf(0, -1, 3, 2), intArrayOf(0, 20, 10, 4), intArrayOf(0, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE), intArrayOf(0, original.size, -1, 4))) {
            assertNull(NativePdfByteRange.extract(bytes(), r, original))
        }
    }
    @Test fun requiresOnlyEvenLengthHexBetweenActualDelimiters() {
        for (gap in listOf("<>", "<0>", "<0G>", "<00 0>", "[0000]", "<0\n>", "<aa>>")) {
            assertNull(gap, NativePdfByteRange.extract(bytes(gap), range(gap), original))
        }
        assertNotNull(NativePdfByteRange.extract(bytes("<Af>"), range("<Af>"), original))
    }
    @Test fun gapCannotHideAnyPartOfTheOriginalRevision() {
        assertNull(NativePdfByteRange.extract(bytes(), intArrayOf(0, original.size - 1, original.size + 6, 7), original))
        val altered = bytes().also { it[0] = 0 }
        assertNull(NativePdfByteRange.extract(altered, range(), original))
    }
    @Test fun budgetsAndEmptyInputsAreExplicit() {
        assertNull(NativePdfByteRange.extract(byteArrayOf(), range(), original))
        assertNull(NativePdfByteRange.extract(bytes(), range(), byteArrayOf()))
        assertNull(NativePdfByteRange.extract(ByteArray(2_097_153), range(), original))
        assertNull(NativePdfByteRange.extract(ByteArray(524_300), range(), ByteArray(524_289)))
        val prefix = ByteArray(524_288) { 1 }; val document = prefix + "<00>".toByteArray()
        assertArrayEquals(prefix, NativePdfByteRange.extract(document, intArrayOf(0, prefix.size, document.size, 0), prefix))
    }
}
