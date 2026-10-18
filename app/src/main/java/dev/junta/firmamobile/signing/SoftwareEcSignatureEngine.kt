package dev.junta.firmamobile.signing

import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import org.bouncycastle.crypto.signers.StandardDSAEncoding

/** Software scalars only; no runtime, import, CMS, TLS, P1363 or XML activation. */
internal class SoftwareEcSignatureEngine(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val maxInputBytes: Int = MAX_INPUT_BYTES,
    private val maxOutputBytes: Int = MAX_OUTPUT_BYTES,
) {
    init {
        require(maxInputBytes in 1..MAX_INPUT_BYTES)
        require(maxOutputBytes in 1..MAX_OUTPUT_BYTES)
    }

    fun sign(input: ByteArray, request: SoftwareEcSignatureRequest): SoftwareEcSignatureResult {
        if (input.size > maxInputBytes) {
            return SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.INPUT_TOO_LARGE)
        }
        val inputCopy = input.copyOf()
        var generated: ByteArray? = null
        return try {
            val expected = SoftwareEcCurvePolicy.parameters(request.algorithm)
            val privateSpec = SoftwareEcCurvePolicy.privateSpec(request.privateKey, expected)
            val publicSpec = SoftwareEcCurvePolicy.publicSpec(request.certificate, expected)
            if (privateSpec == null || publicSpec == null) {
                SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.UNSUPPORTED_KEY)
            } else {
                val keyFactory = KeyFactory.getInstance("EC", BundledBouncyCastle.provider)
                // Snapshot already-readable numerical material. Never encode the source key
                // or dispatch an opaque handle to a software provider, even as a fallback.
                val signingKey = keyFactory.generatePrivate(privateSpec)
                val verificationKey = keyFactory.generatePublic(publicSpec)
                val bytes = Signature.getInstance(request.algorithm.jcaName, BundledBouncyCastle.provider).run {
                    initSign(signingKey, secureRandom)
                    update(inputCopy)
                    sign()
                }
                generated = bytes
                when {
                    bytes.size > minOf(maxOutputBytes, request.algorithm.maxDerBytes) ->
                        SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.OUTPUT_TOO_LARGE)
                    !verifyDer(inputCopy, bytes, verificationKey, request.algorithm) ->
                        SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.SIGNATURE_FAILED)
                    else -> {
                        generated = null
                        SoftwareEcSignatureResult.Success(bytes)
                    }
                }
            }
        } catch (_: Exception) {
            SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.SIGNATURE_FAILED)
        } finally {
            inputCopy.fill(0)
            generated?.fill(0)
        }
    }

    /** The same bounded verification boundary used before returning a generated signature. */
    fun verify(
        input: ByteArray,
        derSignature: ByteArray,
        certificate: X509Certificate,
        algorithm: SoftwareEcSignatureAlgorithm,
    ): Boolean {
        if (input.size > maxInputBytes || derSignature.size > minOf(maxOutputBytes, algorithm.maxDerBytes)) {
            return false
        }
        val inputCopy = input.copyOf()
        val signatureCopy = derSignature.copyOf()
        return try {
            val expected = SoftwareEcCurvePolicy.parameters(algorithm)
            val publicSpec = SoftwareEcCurvePolicy.publicSpec(certificate, expected)
            if (publicSpec == null) {
                false
            } else {
                val key = KeyFactory.getInstance("EC", BundledBouncyCastle.provider).generatePublic(publicSpec)
                verifyDer(inputCopy, signatureCopy, key, algorithm)
            }
        } catch (_: Exception) {
            false
        } finally {
            inputCopy.fill(0)
            signatureCopy.fill(0)
        }
    }

    private fun verifyDer(
        input: ByteArray,
        signature: ByteArray,
        key: PublicKey,
        algorithm: SoftwareEcSignatureAlgorithm,
    ): Boolean {
        // Enforces a canonical two-INTEGER DER sequence and scalar ranges, not P1363/BER.
        StandardDSAEncoding.INSTANCE.decode(SoftwareEcCurvePolicy.parameters(algorithm).order, signature)
        return Signature.getInstance(algorithm.jcaName, BundledBouncyCastle.provider).run {
            initVerify(key)
            update(input)
            verify(signature)
        }
    }

    companion object {
        const val MAX_INPUT_BYTES = 524_288
        const val MAX_OUTPUT_BYTES = 104
    }
}
