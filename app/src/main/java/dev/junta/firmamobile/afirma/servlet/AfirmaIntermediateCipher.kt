package dev.junta.firmamobile.afirma.servlet

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Legacy intermediate-server interoperability, not a new encryption design.
 * TLS remains mandatory. The pinned Java reference uses DES/ECB/NoPadding,
 * even where a JS comment says CBC. The decimal prefix is the ONLY padding
 * removed here; adding/removing an unconditional eight bytes corrupts data.
 */
internal object AfirmaIntermediateCipher {
    private const val MAX_BYTES = 2_097_152
    private val BASE64 = Regex("[A-Za-z0-9+/_-]*={0,2}")

    fun encode(data: ByteArray, key: String?): String {
        require(data.size <= MAX_BYTES) { "Payload exceeds wire budget" }
        if (key == null) return Base64.getUrlEncoder().encodeToString(data)
        val keyBytes = keyBytes(key)
        val padding = (8 - data.size % 8) % 8
        val padded = data.copyOf(data.size + padding)
        var encrypted: ByteArray? = null
        return try {
            val cipher = Cipher.getInstance("DES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
            val result = cipher.doFinal(padded)
            encrypted = result
            "$padding." + Base64.getUrlEncoder().encodeToString(result)
        } finally { keyBytes.fill(0); padded.fill(0); encrypted?.fill(0) }
    }

    fun decode(encoded: String, key: String?): ByteArray {
        require(encoded.length <= (MAX_BYTES + 7L + 2L) / 3L * 4L + 2L) { "Payload exceeds wire budget" }
        if (key == null) return base64(encoded).also { require(it.size <= MAX_BYTES) }
        val keyBytes = keyBytes(key)
        var cipherBytes: ByteArray? = null
        var plain: ByteArray? = null
        return try {
            val separator = encoded.indexOf('.')
            val padding = if (separator < 0) 0 else {
                require(separator == 1 && encoded[0] in '0'..'7') { "Invalid padding declaration" }
                encoded[0] - '0'
            }
            cipherBytes = base64(if (separator < 0) encoded else encoded.substring(2))
            val encrypted = checkNotNull(cipherBytes)
            require(encrypted.size % 8 == 0 && encrypted.size <= MAX_BYTES + 7) { "Invalid DES block length" }
            val cipher = Cipher.getInstance("DES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
            val decoded = cipher.doFinal(encrypted)
            plain = decoded
            require(padding <= decoded.size && decoded.size - padding <= MAX_BYTES) { "Invalid padding size" }
            require((decoded.size - padding until decoded.size).all { decoded[it] == 0.toByte() }) { "Invalid zero padding" }
            decoded.copyOf(decoded.size - padding)
        } finally { keyBytes.fill(0); cipherBytes?.fill(0); plain?.fill(0) }
    }

    private fun keyBytes(key: String): ByteArray {
        require(key.length == 8 && key.all { it.code in 0x20..0x7e }) { "Legacy DES requires an eight-byte ASCII key" }
        return key.toByteArray(Charsets.US_ASCII)
    }

    private fun base64(text: String): ByteArray {
        require(BASE64.matches(text) && text.length % 4 != 1 && (!text.contains('=') || text.length % 4 == 0)) { "Invalid base64" }
        require(!((text.contains('+') || text.contains('/')) && (text.contains('-') || text.contains('_')))) { "Mixed base64 alphabets" }
        val normalized = text.replace('-', '+').replace('_', '/')
        val bytes = Base64.getDecoder().decode(normalized)
        if (Base64.getEncoder().withoutPadding().encodeToString(bytes) != normalized.trimEnd('=')) {
            bytes.fill(0); throw IllegalArgumentException("Noncanonical base64")
        }
        return bytes
    }
}
