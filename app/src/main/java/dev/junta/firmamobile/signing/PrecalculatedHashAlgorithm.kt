package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.security.SecureRandom
import java.security.Signature
import java.util.Base64

enum class PrecalculatedHashAlgorithm(
    val standardName: String,
    val digestByteLength: Int,
    val asn1Prefix: ByteArray,
    val matchingSigningAlgorithms: Set<SigningAlgorithm>,
) {
    SHA256(
        "SHA-256",
        32,
        byteArrayOf(
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01,
            0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20,
        ),
        setOf(SigningAlgorithm.SHA256_WITH_RSA),
    ),
    SHA512(
        "SHA-512",
        64,
        byteArrayOf(
            0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01,
            0x65, 0x03, 0x04, 0x02, 0x03, 0x05, 0x00, 0x04, 0x40,
        ),
        setOf(SigningAlgorithm.SHA512_WITH_RSA),
    ),
    SHA1(
        "SHA-1",
        20,
        byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14,
        ),
        setOf(SigningAlgorithm.SHA1_WITH_RSA),
    ),
    SHA384(
        "SHA-384",
        48,
        byteArrayOf(
            0x30, 0x41, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01,
            0x65, 0x03, 0x04, 0x02, 0x02, 0x05, 0x00, 0x04, 0x30,
        ),
        emptySet(),
    );

    fun decodeHash(raw: String): ByteArray? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length == digestByteLength * 2 &&
            trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        ) {
            val result = ByteArray(digestByteLength)
            for (i in 0 until digestByteLength) {
                val high = Character.digit(trimmed[i * 2], 16)
                val low = Character.digit(trimmed[i * 2 + 1], 16)
                if (high == -1 || low == -1) return null
                result[i] = ((high shl 4) or low).toByte()
            }
            return result
        }
        val base64Bytes = runCatching { Base64.getDecoder().decode(trimmed) }.getOrNull()
        if (base64Bytes != null && base64Bytes.size == digestByteLength) {
            return base64Bytes
        }
        return null
    }

    companion object {
        fun parse(raw: String): PrecalculatedHashAlgorithm? = when (raw.trim().uppercase().replace("-", "")) {
            "SHA256" -> SHA256
            "SHA512" -> SHA512
            "SHA1" -> SHA1
            "SHA384" -> SHA384
            else -> null
        }
    }
}

interface PrehashedRsaSignatureEngine {
    fun sign(
        digest: ByteArray,
        algorithm: PrecalculatedHashAlgorithm,
        identity: UnlockedIdentity,
    ): LocalSignatureResult
}

class JcaPrehashedRsaSignatureEngine internal constructor(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val signatureObserver: SensitiveSignatureCopyObserver =
        SensitiveSignatureCopyObserver {},
) : PrehashedRsaSignatureEngine {
    override fun sign(
        digest: ByteArray,
        algorithm: PrecalculatedHashAlgorithm,
        identity: UnlockedIdentity,
    ): LocalSignatureResult {
        if (digest.size != algorithm.digestByteLength) {
            return LocalSignatureResult.Failure(LocalSignatureError.INPUT_TOO_LARGE)
        }
        val digestInfo = algorithm.asn1Prefix + digest
        var generatedSignature: ByteArray? = null
        return try {
            val signatureBytes = identity.withPrivateKey { privateKey ->
                if (!privateKey.algorithm.equals(RSA, ignoreCase = true)) {
                    null
                } else {
                    Signature.getInstance(NONE_WITH_RSA).run {
                        initSign(privateKey, secureRandom)
                        update(digestInfo)
                        sign()
                    }
                }
            } ?: return LocalSignatureResult.Failure(LocalSignatureError.UNSUPPORTED_KEY)
            generatedSignature = signatureBytes
            if (signatureBytes.size > JcaLocalSignatureEngine.MAX_OUTPUT_BYTES) {
                return LocalSignatureResult.Failure(LocalSignatureError.OUTPUT_TOO_LARGE)
            }
            val verified = Signature.getInstance(NONE_WITH_RSA).run {
                initVerify(identity.certificate.publicKey)
                update(digestInfo)
                verify(signatureBytes)
            }
            if (!verified) {
                return LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
            }
            generatedSignature = null
            LocalSignatureResult.Success(
                LocalSignature(signatureBytes, signatureObserver),
            )
        } catch (_: Exception) {
            LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
        } finally {
            digestInfo.fill(0)
            generatedSignature?.fill(0)
        }
    }

    private companion object {
        const val RSA = "RSA"
        const val NONE_WITH_RSA = "NONEwithRSA"
    }
}
