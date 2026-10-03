package dev.junta.firmamobile.signing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiteralSigningPropertiesTest {
    private val expected = "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true"

    @Test
    fun equivalentOrderLineEndingsAndTrailingBlankLinesHaveOneCanonicalValue() {
        listOf(
            expected,
            "headless=true\nfilters=nonexpired:true;authCert:true\npolicy=FirmaAGE",
            expected.replace("\n", "\r\n") + "\r\n",
            expected.replace("\n", "\r"),
        ).forEach { assertEquals(expected, LiteralSigningProperties.canonicalize(it, expected)) }
    }

    @Test
    fun duplicatesUnknownKeysChangesEscapesAndControlCharactersAreRejected() {
        listOf(
            expected + "\npolicy=FirmaAGE",
            expected + "\npolicy=Other",
            expected + "\nunknown=true",
            expected.replace("FirmaAGE", "Other"),
            expected.replace("headless", "head\\less"),
            expected.replace("headless", "head\u0000less"),
            "x".repeat(65_537),
        ).forEach { assertNull(LiteralSigningProperties.canonicalize(it, expected)) }
    }
}
