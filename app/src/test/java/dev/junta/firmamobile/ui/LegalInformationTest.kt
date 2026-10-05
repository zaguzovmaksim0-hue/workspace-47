package dev.junta.firmamobile.ui

import org.junit.Assert.*
import org.junit.Test

class LegalInformationTest {
    @Test fun assetsCannotEscapeTheBundledLegalDirectory() {
        assertTrue(validLegalAsset("legal/privacy.txt"))
        assertTrue(validLegalAsset("legal/dependencies/a.txt"))
        listOf("../privacy.txt", "legal/../private.txt", "legal//x.txt", "legal/x\\y.txt", "https://example.test/x.txt")
            .forEach { assertFalse(it, validLegalAsset(it)) }
    }
    @Test fun longNoticesUseBoundedParagraphsWithoutLosingText() {
        val input = "a".repeat(5000) + "\n\n" + "b".repeat(2001)
        val chunks = legalTextChunks(input)
        assertTrue(chunks.all { it.length <= 2000 })
        assertEquals(input.replace("\n\n", ""), chunks.joinToString(""))
    }
}
