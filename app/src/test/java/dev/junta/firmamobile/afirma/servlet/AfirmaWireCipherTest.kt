package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.*
import org.junit.Test

class AfirmaWireCipherTest {
    @Test fun legacyNonemptyGoldenVectorsMatchPinnedOfficialJavaScriptEncryption() {
        // Computed using Cipher.des/cipherDES from upstream commit
        // 0d7f3cf01fb65d2be5b245622d2c8f490f36e718, not this Kotlin implementation.
        val vectors = listOf(
            "00" to "7.PXWVqYv_gJ0=",
            "616263" to "5.LINpMRouOPo=",
            "31323334353637" to "1.GVm6kyoyvrQ=",
            "3132333435363738" to "0.ltACiHjVjIk=",
            "313233343536373800" to "7.ltACiHjVjIk9dZWpi_-AnQ==",
            "000102030405060708090a0b0c0d0e0f" to "0.0ckTTw7CLtxQFSm18xZwPA==",
            "61626364656667680000000000000000" to "0.lNRDa8O1tpM9dZWpi_-AnQ==",
            "000000000000000000000000000000000000000000000000" to "0.PXWVqYv_gJ09dZWpi_-AnT11lamL_4Cd"
        )
        for ((hex, expected) in vectors) {
            val data = bytes(hex); val original = data.copyOf()
            assertEquals(expected, AfirmaIntermediateCipher.encode(data, "12345678"))
            assertArrayEquals(original, data)
            assertArrayEquals(data, AfirmaIntermediateCipher.decode(expected, "12345678"))
        }
    }

    @Test fun exactBlocksAndRealTrailingZerosAreNeverMistakenForExtraPadding() {
        for (size in listOf(0, 8, 16, 24, 32)) {
            val value = ByteArray(size)
            val wire = AfirmaIntermediateCipher.encode(value, "12345678")
            assertTrue(wire.startsWith("0."))
            assertArrayEquals(value, AfirmaIntermediateCipher.decode(wire, "12345678"))
        }
    }

    @Test fun allRemaindersAndFullByteRangeRoundTripWithoutChangingInput() {
        for (size in 1..257) {
            val data = ByteArray(size) { it.toByte() }; val before = data.copyOf()
            assertArrayEquals(before, AfirmaIntermediateCipher.decode(AfirmaIntermediateCipher.encode(data, "12345678"), "12345678"))
            assertArrayEquals(before, data)
        }
    }

    @Test fun malformedCiphertextAndInvalidKeyDoNotProduceTruncatedPlaintext() {
        for (wire in listOf("8.ltACiHjVjIk=", "-1.ltACiHjVjIk=", "0.AA==", "x.AA==", "1.", "0.Zh==", "0.abcd$", "0.ltACiHjVjIk=|other")) {
            assertThrows(RuntimeException::class.java) { AfirmaIntermediateCipher.decode(wire, "12345678") }
        }
        // A real last byte '8' cannot be discarded as declared zero padding.
        assertThrows(RuntimeException::class.java) { AfirmaIntermediateCipher.decode("1.ltACiHjVjIk=", "12345678") }
        for (key in listOf("", "1234567", "123456789", "пароль12")) {
            assertThrows(IllegalArgumentException::class.java) { AfirmaIntermediateCipher.encode(byteArrayOf(1), key) }
        }
    }

    @Test fun noKeyMeansBase64RatherThanImplicitDesAndDoesNotExposeRawText() {
        val data = byteArrayOf(-5,-17,-1,0)
        val wire = AfirmaIntermediateCipher.encode(data, null)
        assertFalse(wire.contains('.'))
        assertArrayEquals(data, AfirmaIntermediateCipher.decode(wire, null))
    }

    @Test fun currentAesCbcMatchesIndependentWebCryptoGoldenVectors() {
        val vectors = listOf(
            "" to "4nQOivrU5NFdDWYbOC7KiQ",
            "616263" to "uC_4kwgHc-x6uqp8dpv9nw",
            "000102030405060708090a0b0c0d0e0f" to "ctdcKUFZ-sKKQ_Z8bgfuSwnF2Hjf94M1LK9tBPzVias",
            "61626364656667680000000000000000" to "OYwGr9oW5IkyWCIiV3Bre8dHEsIkMsbBD9e-yc9L4_E"
        )
        AfirmaAesParameters(ByteArray(32) { it.toByte() }, ByteArray(16) { (it + 32).toByte() }).use { cipher ->
            for ((hex, wire) in vectors) {
                assertEquals(wire, cipher.encode(bytes(hex)).trimEnd('='))
                assertArrayEquals(bytes(hex), cipher.decode(wire))
            }
        }
    }

    @Test fun aesOwnershipCloseAndInvalidPaddingAreExplicit() {
        val key = ByteArray(32) { it.toByte() }; val iv = ByteArray(16) { (it+32).toByte() }
        val cipher = AfirmaAesParameters(key, iv); val copy = cipher.copy(); key.fill(0); iv.fill(0)
        val wire = cipher.encode("abc".toByteArray())
        assertEquals("uC_4kwgHc-x6uqp8dpv9nw", wire.trimEnd('='))
        cipher.close(); cipher.close()
        assertThrows(IllegalStateException::class.java) { cipher.encode(byteArrayOf(1)) }
        assertArrayEquals("abc".toByteArray(), copy.decode(wire))
        assertThrows(RuntimeException::class.java) { copy.decode("AA==") }
        copy.close()
    }

    @Test fun cipherJsonRejectsDecodedDuplicatesNonStringsAndTrailingGarbage() {
        for (json in listOf("{\"algo\":\"AES\",\"ALGO\":\"DES\"}", "{\"key\":null}", "{\"key\":123}", "{}tail", "{\"key\":\"x\",}", "{\"key\":\"x\",\"\\u006bey\":\"y\"}")) {
            assertThrows(RuntimeException::class.java) { AfirmaCipherJson.parse(json) }
        }
        assertEquals(mapOf("algo" to "AES", "key" to "ab/cd="), AfirmaCipherJson.parse("{\"algo\":\"AES\",\"key\":\"ab\\/cd=\"}"))
    }

    @Test fun jsonUnicodeEscapeRequiresExactlyFourUnsignedHexDigits() {
        for (value in listOf("+041", "-001", "0x41")) {
            val json = "{\"algo\":\"" + "\\u" + value + "ES\"}"
            assertThrows(RuntimeException::class.java) { AfirmaCipherJson.parse(json) }
        }
        assertEquals("AES", AfirmaCipherJson.parse("{\"algo\":\"\\u0041ES\"}")["algo"])
    }

    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
