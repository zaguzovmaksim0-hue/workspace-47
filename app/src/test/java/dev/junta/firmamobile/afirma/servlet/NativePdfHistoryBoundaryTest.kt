package dev.junta.firmamobile.afirma.servlet

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NativePdfHistoryBoundaryTest {
    @Test
    public fun acceptsExactlySixteenUnsignedDraftRevisions() {
        val pdf = draft(16)
        assertEquals(16, footerCount(pdf))
        PDDocument.load(pdf).use { document ->
            val history = NativePdfSignatureHistory.inspect(pdf, document)
            assertNotNull(history)
            assertTrue(history!!.isEmpty())
        }
    }

    @Test
    public fun rejectsSeventeenUnsignedDraftRevisions() {
        val pdf = draft(17)
        assertEquals(17, footerCount(pdf))
        PDDocument.load(pdf).use { document ->
            assertNull(NativePdfSignatureHistory.inspect(pdf, document))
        }
    }

    private fun draft(revisions: Int): ByteArray {
        require(revisions >= 1)
        var bytes = nativePadesFixture()
        repeat(revisions - 1) { index ->
            val updated = ByteArrayOutputStream().use { out ->
                PDDocument.load(bytes).use { document ->
                    val information = document.documentInformation
                    information.title = "Local draft ${index}"
                    information.cosObject.isNeedToBeUpdated = true
                    document.saveIncremental(out)
                }
                out.toByteArray()
            }
            bytes = updated
        }
        return bytes
    }

    private fun footerCount(pdf: ByteArray): Int =
        Regex("startxref\\s+\\d+\\s+%%EOF")
            .findAll(String(pdf, Charsets.ISO_8859_1))
            .count()
}
