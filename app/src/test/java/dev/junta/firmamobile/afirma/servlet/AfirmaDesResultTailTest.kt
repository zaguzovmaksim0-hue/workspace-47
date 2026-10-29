package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URI
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking

class AfirmaDesResultTailTest {
    @Test fun lastDesResultPieceCarriesEightExtraZeroBytes() {
        val vectors = listOf(
            0 to "0.PXWVqYv_gJ0=",
            1 to "7.PXWVqYv_gJ09dZWpi_-AnQ==",
            7 to "1.8dQvLfi63TM9dZWpi_-AnQ==",
            8 to "0.0ckTTw7CLtw9dZWpi_-AnQ==",
            9 to "7.0ckTTw7CLtwpZRuSBSBsSz11lamL_4Cd",
            255 to "1.0ckTTw7CLtxQFSm18xZwPHStG5qoQgJ1IP1vpckNrlMuXf8Lt2bSQqcWxooAJ3rri7R6DPCpYm0LWHkNw_QrjqrXt9ykRPeGp2eDfdjTQRZpZgInuqZlPPbwkGOcuM4Bkryq9vNxa3sM3az8hKIhKt9BwFNnjWpsJ2iDiHM7hGtCF3lOsiVB7LxsywnVHalG3Ruue4qbsi_kNY0EBEPKbYgjcSmy3v8HRtJSNz0QAeiSbnUE3uvCP6lHRrYqPDHQeCIEtahyETlNXFf9JQwX7qJAFnJsBxLDf4_h-Kh381qpb0H__ZE0WenEStX0wqnl5UHvkwWxjrfwcn0lLcAK7T11lamL_4Cd",
            256 to "0.0ckTTw7CLtxQFSm18xZwPHStG5qoQgJ1IP1vpckNrlMuXf8Lt2bSQqcWxooAJ3rri7R6DPCpYm0LWHkNw_QrjqrXt9ykRPeGp2eDfdjTQRZpZgInuqZlPPbwkGOcuM4Bkryq9vNxa3sM3az8hKIhKt9BwFNnjWpsJ2iDiHM7hGtCF3lOsiVB7LxsywnVHalG3Ruue4qbsi_kNY0EBEPKbYgjcSmy3v8HRtJSNz0QAeiSbnUE3uvCP6lHRrYqPDHQeCIEtahyETlNXFf9JQwX7qJAFnJsBxLDf4_h-Kh381qpb0H__ZE0WenEStX0wqnl5UHvkwWxjrcP4Sfxdu0XSj11lamL_4Cd",
        )
        for ((size, expected) in vectors) {
            val data = ByteArray(size) { it.toByte() }; val original = data.copyOf()
            assertEquals(expected, AfirmaIntermediateCipher.encodeLast(data, "12345678"))
            assertArrayEquals(original, data)
            val keyBytes = "12345678".toByteArray(Charsets.US_ASCII)
            val cipher = Cipher.getInstance("DES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
            val encoded = expected.substringAfter('.')
            val normalized = encoded.replace('-', '+').replace('_', '/')
            val decrypted = cipher.doFinal(java.util.Base64.getDecoder().decode(normalized))
            val padding = expected.substringBefore('.').toInt()
            assertEquals(size + padding + 8, decrypted.size)
            assertArrayEquals(original, decrypted.copyOf(size))
            assertTrue(decrypted.copyOfRange(size, size + padding).all { it == 0.toByte() })
            assertTrue(decrypted.copyOfRange(size + padding, decrypted.size).all { it == 0.toByte() })
            keyBytes.fill(0)
        }
    }

    @Test fun signResultPadsOnlyItsLastSignaturePiece() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        val payload = "original synthetic signing bytes\u0000".toByteArray()
        var wire: String? = null
        val operation = NativeAfirmaOperation(
            AfirmaServletInvocation(
                AfirmaServletMetadata(
                    AfirmaServletOperation.SIGN, "https://portal.example",
                    URI("https://store.example/StorageService?route=test"), "Request-123",
                    "12345678", SigningAlgorithm.SHA256_WITH_RSA, true
                ),
                payload
            ),
            AfirmaResultTransport { _, _, result -> wire = result; AfirmaDeliveryResult.ACKNOWLEDGED },
            clock = Clock.fixed(identity.identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC)
        )
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(identity.identity) {})
        val fields = checkNotNull(wire).split('|')
        assertEquals(2, fields.size)
        val certificate = decryptDes(fields[0], "12345678")
        val signature = decryptDes(fields[1], "12345678")
        val certificateBytes = identity.identity.certificate.encoded
        assertEquals((certificateBytes.size + 7) / 8 * 8, certificate.size)
        assertEquals(0, signature.size % 8)
        assertTrue(signature.size >= 8)
        assertEquals(0, (signature.size - 8) % 8)
        assertTrue(signature.copyOfRange(signature.size - 8, signature.size).all { it == 0.toByte() })
        operation.close()
    }

    @Test fun existingDesEncodingAndNoKeyBehaviorAreUnchanged() {
        val data = ByteArray(9) { it.toByte() }
        assertEquals("7.0ckTTw7CLtwpZRuSBSBsSw==", AfirmaIntermediateCipher.encode(data, "12345678"))
        assertEquals(java.util.Base64.getUrlEncoder().encodeToString(data), AfirmaIntermediateCipher.encodeLast(data, null))
        assertArrayEquals(data, AfirmaIntermediateCipher.decode(AfirmaIntermediateCipher.encodeLast(data, null), null))
    }

    private fun decryptDes(wire: String, key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.US_ASCII)
        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
        val encoded = wire.substringAfter('.')
        val normalized = encoded.replace('-', '+').replace('_', '/')
        return cipher.doFinal(java.util.Base64.getDecoder().decode(normalized)).also { keyBytes.fill(0) }
    }
}
