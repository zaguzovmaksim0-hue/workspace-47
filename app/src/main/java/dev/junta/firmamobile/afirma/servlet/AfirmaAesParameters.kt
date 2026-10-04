package dev.junta.firmamobile.afirma.servlet

import java.io.Closeable
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Current AutoScript AES-CBC carrier parameters, not a general encryption API.
 * The caller supplies a per-operation key/IV; TLS and explicit destination
 * consent are mandatory. No automatic legacy downgrade is performed. */
internal class AfirmaAesParameters(key: ByteArray, iv: ByteArray) : Closeable {
    init { require(key.size in setOf(16, 24, 32) && iv.size == 16) }
    private var ownedKey: ByteArray? = key.copyOf()
    private var ownedIv: ByteArray? = iv.copyOf()

    @Synchronized fun copy(): AfirmaAesParameters = AfirmaAesParameters(checkNotNull(ownedKey), checkNotNull(ownedIv))

    @Synchronized fun encode(bytes: ByteArray): String {
        require(bytes.size <= 2_097_152)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(checkNotNull(ownedKey), "AES"), IvParameterSpec(checkNotNull(ownedIv)))
        val ciphertext = cipher.doFinal(bytes)
        return try { Base64.getUrlEncoder().encodeToString(ciphertext) } finally { ciphertext.fill(0) }
    }

    @Synchronized fun decode(encoded: String): ByteArray {
        val ciphertext = AfirmaServletInvocationParser.strictBase64(encoded, 2_097_168)
        return try {
            require(ciphertext.isNotEmpty() && ciphertext.size % 16 == 0)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(checkNotNull(ownedKey), "AES"), IvParameterSpec(checkNotNull(ownedIv)))
            val plaintext = cipher.doFinal(ciphertext)
            if (plaintext.size > 2_097_152) { plaintext.fill(0); error("Payload exceeds limit") }
            plaintext
        } finally { ciphertext.fill(0) }
    }

    @Synchronized override fun close() { ownedKey?.fill(0); ownedIv?.fill(0); ownedKey = null; ownedIv = null }
    override fun toString() = "AfirmaAesParameters(redacted)"
}

/** Bounded, flat string-only JSON object. Checks duplicate decoded names before
 * a map can erase ambiguity; no untrusted JSON exception text is displayed. */
internal object AfirmaCipherJson {
    fun parse(text: String): Map<String, String> {
        require(text.length <= 4096)
        var offset = 0
        fun whitespace() { while (offset < text.length && text[offset] in " \t\r\n") offset++ }
        fun string(): String {
            require(offset < text.length && text[offset++] == '"')
            val value = StringBuilder()
            while (offset < text.length) {
                val char = text[offset++]
                if (char == '"') return value.toString()
                require(char.code >= 0x20)
                if (char != '\\') value.append(char) else {
                    require(offset < text.length)
                    when (val escaped = text[offset++]) {
                        '"', '\\', '/' -> value.append(escaped)
                        'b' -> value.append('\b')
                        'f' -> value.append('\u000c')
                        'n' -> value.append('\n')
                        'r' -> value.append('\r')
                        't' -> value.append('\t')
                        'u' -> {
                            require(offset + 4 <= text.length)
                            val digits = text.substring(offset, offset + 4)
                            require(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                            val code = digits.toInt(16)
                            require(code !in 0xd800..0xdfff)
                            value.append(code.toChar()); offset += 4
                        }
                        else -> error("Invalid JSON escape")
                    }
                }
            }
            error("Unterminated JSON string")
        }
        val fields = linkedMapOf<String, String>()
        whitespace(); require(offset < text.length && text[offset++] == '{'); whitespace()
        if (offset < text.length && text[offset] == '}') offset++ else while (true) {
            whitespace(); val key = string()
            whitespace(); require(offset < text.length && text[offset++] == ':'); whitespace()
            val value = string()
            require(fields.keys.none { it.equals(key, ignoreCase = true) })
            require(fields.put(key, value) == null && fields.size <= 8)
            whitespace(); require(offset < text.length)
            val next = text[offset++]
            if (next == '}') break
            require(next == ',')
        }
        whitespace(); require(offset == text.length)
        return fields
    }
}
