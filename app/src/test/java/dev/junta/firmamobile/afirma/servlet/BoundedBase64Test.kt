package dev.junta.firmamobile.afirma.servlet

import java.util.Base64
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class BoundedBase64Test {
    @Test fun bothAlphabetsAndPaddingFormsMatchTheStandardDecoder() {
        val random = Random(47)
        for (size in 0..256) {
            val bytes = ByteArray(size).also(random::nextBytes)
            for (encoder in listOf(Base64.getEncoder(), Base64.getUrlEncoder())) {
                for (text in listOf(encoder.encodeToString(bytes), encoder.withoutPadding().encodeToString(bytes))) {
                    assertArrayEquals(bytes, BoundedBase64.decode(text, size))
                }
            }
        }
    }

    @Test fun malformedMixedAlphabetsAndNonzeroPaddingBitsAreRejected() {
        for (text in listOf("a", "Zh==", "Zh", "Zm9=", "Zg=", "====", "Zg===", "Z=g=", "ab-_+/", "Z g==", "Zg==\n")) {
            assertThrows(text, Exception::class.java) { BoundedBase64.decode(text, 1024) }
        }
    }

    @Test fun exactDecodedBudgetIsEnforcedBeforeReturningBytes() {
        assertArrayEquals(byteArrayOf(0, 0), BoundedBase64.decode("AAA=", 2))
        assertThrows(Exception::class.java) { BoundedBase64.decode("AAAA", 2) }
        assertThrows(Exception::class.java) { BoundedBase64.decode("AAAA", -1) }
    }

    @Test fun eightMegabyteDataUsesTheSameBoundedDecoder() {
        val bytes = ByteArray(8 * 1024 * 1024) { (it * 31).toByte() }
        val encoded = Base64.getUrlEncoder().encodeToString(bytes)
        val result = BoundedBase64.decode(encoded, bytes.size)
        try { assertArrayEquals(bytes, result) }
        finally { bytes.fill(0); result.fill(0) }
    }
}
