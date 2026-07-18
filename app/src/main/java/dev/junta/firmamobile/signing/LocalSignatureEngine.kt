package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.security.SecureRandom
import java.security.Signature

enum class LocalSignatureError {
    INPUT_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    UNSUPPORTED_KEY,
    SIGNATURE_FAILED,
}

sealed interface LocalSignatureResult {
    data class Success(val signature: LocalSignature) : LocalSignatureResult

    data class Failure(val error: LocalSignatureError) : LocalSignatureResult
}

interface LocalSignatureEngine {
    fun sign(
        input: ByteArray,
        identity: UnlockedIdentity,
        algorithm: SigningAlgorithm,
    ): LocalSignatureResult
}

class JcaLocalSignatureEngine internal constructor(
    private val maxInputBytes: Int = MAX_INPUT_BYTES,
    private val maxOutputBytes: Int = MAX_OUTPUT_BYTES,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val signatureObserver: SensitiveSignatureCopyObserver =
        SensitiveSignatureCopyObserver {},
) : LocalSignatureEngine {
    init {
        require(maxInputBytes > 0)
        require(maxOutputBytes > 0)
    }

    override fun sign(
        input: ByteArray,
        identity: UnlockedIdentity,
        algorithm: SigningAlgorithm,
    ): LocalSignatureResult {
        if (input.size > maxInputBytes) {
            return LocalSignatureResult.Failure(LocalSignatureError.INPUT_TOO_LARGE)
        }

        val inputCopy = input.copyOf()
        var generatedSignature: ByteArray? = null
        return try {
            val jcaAlgorithm = algorithm.jcaName()
            val signatureBytes = identity.withPrivateKey { privateKey ->
                if (!privateKey.algorithm.equals(RSA, ignoreCase = true)) {
                    null
                } else {
                    Signature.getInstance(jcaAlgorithm).run {
                        initSign(privateKey, secureRandom)
                        update(inputCopy)
                        sign()
                    }
                }
            } ?: return LocalSignatureResult.Failure(LocalSignatureError.UNSUPPORTED_KEY)
            generatedSignature = signatureBytes
            if (signatureBytes.size > maxOutputBytes) {
                return LocalSignatureResult.Failure(LocalSignatureError.OUTPUT_TOO_LARGE)
            }
            if (!verify(jcaAlgorithm, inputCopy, signatureBytes, identity)) {
                return LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
            }
            generatedSignature = null
            LocalSignatureResult.Success(
                LocalSignature(signatureBytes, signatureObserver),
            )
        } catch (_: Exception) {
            LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
        } finally {
            inputCopy.fill(0)
            generatedSignature?.fill(0)
        }
    }

    private fun verify(
        algorithm: String,
        input: ByteArray,
        signatureBytes: ByteArray,
        identity: UnlockedIdentity,
    ): Boolean = Signature.getInstance(algorithm).run {
        initVerify(identity.certificate.publicKey)
        update(input)
        verify(signatureBytes)
    }

    companion object {
        const val MAX_INPUT_BYTES = 524_288
        const val MAX_OUTPUT_BYTES = 2_097_152
        private const val RSA = "RSA"
    }
}

internal fun SigningAlgorithm.jcaName(): String = when (this) {
    SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
    SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
    SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
}
