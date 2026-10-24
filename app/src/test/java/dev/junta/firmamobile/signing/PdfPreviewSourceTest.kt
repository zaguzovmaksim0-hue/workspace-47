package dev.junta.firmamobile.signing

import org.junit.Assert.*
import org.junit.Test

class PdfPreviewSourceTest {
    @Test fun onlyBoundedLocalPdfInputCreatesAPreviewCapability() {
        val pdf = "%PDF-test".encodeToByteArray()
        lateinit var probe: ByteArray
        PdfPreviewSource.fromLocalInput(pdf.size) { pdf.copyOf().also { probe = it } }!!.use { source ->
            assertTrue(probe.all { it == 0.toByte() })
            source.withBytes { assertArrayEquals(pdf, it) }
        }
        for (input in listOf(ByteArray(32), "https://example.invalid/pdf".encodeToByteArray(), pdf + pdf)) {
            assertNull(PdfPreviewSource.fromLocalInput(pdf.size) { input })
            assertTrue(input.all { it == 0.toByte() })
        }
        assertNull(PdfPreviewSource.fromLocalInput(100) { error("closed") })
    }

    @Test fun boundedCopyIsClearedAndCannotBeReadAfterClose() {
        val original = "%PDF-test".encodeToByteArray()
        val source = PdfPreviewSource(original.size) { original.copyOf() }
        lateinit var borrowed: ByteArray
        source.withBytes { borrowed = it; assertArrayEquals(original, it) }
        assertTrue(borrowed.all { it == 0.toByte() })
        source.close()
        assertThrows(IllegalStateException::class.java) { source.withBytes {} }
        assertEquals('%'.code.toByte(), original[0])
    }

    @Test fun oversizedAndNonPdfInputsAreRejectedAndCleared() {
        for (input in listOf(ByteArray(9), "%PDF-toolarge".encodeToByteArray())) {
            val source = PdfPreviewSource(9) { input }
            assertThrows(IllegalArgumentException::class.java) { source.withBytes {} }
            assertTrue(input.all { it == 0.toByte() })
        }
    }

    @Test fun exceptionsStillClearTheBorrowedCopy() {
        val input = "%PDF-test".encodeToByteArray()
        val source = PdfPreviewSource(input.size) { input }
        assertThrows(IllegalStateException::class.java) { source.withBytes { error("test") } }
        assertTrue(input.all { it == 0.toByte() })
    }

    @Test fun bitmapBoundsCoverPortraitLandscapeAndHostilePageDimensions() {
        assertEquals(1080 to 1080, PdfPreviewLimits.dimensions(100, 100))
        for ((width, height) in listOf(595 to 842, 842 to 595, 1 to Int.MAX_VALUE, Int.MAX_VALUE to 1)) {
            val (w, h) = PdfPreviewLimits.dimensions(width, height)
            assertTrue(w in 1..1080)
            assertTrue(h in 1..1920)
            assertTrue(w.toLong() * h <= 1_500_000)
        }
        assertThrows(IllegalArgumentException::class.java) { PdfPreviewLimits.dimensions(0, 10) }
    }
}
