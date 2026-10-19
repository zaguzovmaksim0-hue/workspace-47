package dev.junta.firmamobile.signing

import java.security.cert.X509Certificate

/** Internal detached software EC candidate only. No runtime, import, TLS or caller activation. */
internal class SoftwareEcCadesEngine(
    private val rawEngine: SoftwareEcSignatureEngine = SoftwareEcSignatureEngine(),
    private val limits: SoftwareEcCadesLimits = SoftwareEcCadesLimits(),
) {
    fun signDetached(input: ByteArray, request: SoftwareEcSignatureRequest): SoftwareEcCadesResult {
        if (input.size > limits.maxInputBytes) {
            return SoftwareEcCadesResult.Failure(SoftwareEcCadesError.INPUT_TOO_LARGE)
        }
        return SoftwareEcCadesBuffers().use { buffers ->
            try {
                val ownedInput = buffers.own(input.copyOf())
                val policy = SoftwareEcCadesPolicy(request.algorithm)
                val certificate = policy.certificate(request.certificate, request.algorithm, limits, buffers)
                val attributes = SoftwareEcCadesEnvelope.attributes(ownedInput, certificate, policy, buffers)
                val attributeDer = buffers.encode(attributes, limits.maxSignedAttributesBytes,
                    SoftwareEcCadesError.SIGNED_ATTRIBUTES_TOO_LARGE)
                val result = rawEngine.sign(attributeDer,
                    SoftwareEcSignatureRequest(request.privateKey, certificate.snapshot, request.algorithm))
                if (result is SoftwareEcSignatureResult.Failure) {
                    throw SoftwareEcCadesFailure(if (result.error == SoftwareEcSignatureError.UNSUPPORTED_KEY) {
                        SoftwareEcCadesError.UNSUPPORTED_KEY
                    } else {
                        SoftwareEcCadesError.SIGNATURE_FAILED
                    })
                }
                val signature = buffers.own((result as SoftwareEcSignatureResult.Success).derSignature)
                val envelope = SoftwareEcCadesEnvelope.create(attributes, signature, certificate, policy)
                val encoded = buffers.encode(envelope, limits.maxOutputBytes, SoftwareEcCadesError.OUTPUT_TOO_LARGE)
                if (!verifyOwned(ownedInput, encoded, certificate, request.algorithm, buffers)) {
                    throw SoftwareEcCadesFailure(SoftwareEcCadesError.SIGNATURE_FAILED)
                }
                SoftwareEcCadesResult.Success(buffers.release(encoded))
            } catch (failure: SoftwareEcCadesFailure) {
                SoftwareEcCadesResult.Failure(failure.error)
            } catch (_: Exception) {
                SoftwareEcCadesResult.Failure(SoftwareEcCadesError.SIGNATURE_FAILED)
            }
        }
    }

    fun verifyDetached(
        input: ByteArray,
        cmsDer: ByteArray,
        certificate: X509Certificate,
        algorithm: SoftwareEcSignatureAlgorithm,
    ): Boolean {
        if (input.size > limits.maxInputBytes || cmsDer.size > limits.maxOutputBytes) return false
        return SoftwareEcCadesBuffers().use { buffers ->
            try {
                val ownedInput = buffers.own(input.copyOf())
                val ownedCms = buffers.own(cmsDer.copyOf())
                val policy = SoftwareEcCadesPolicy(algorithm)
                val prepared = policy.certificate(certificate, algorithm, limits, buffers)
                verifyOwned(ownedInput, ownedCms, prepared, algorithm, buffers)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun verifyOwned(
        input: ByteArray,
        cmsDer: ByteArray,
        certificate: SoftwareEcCadesPolicy.Certificate,
        algorithm: SoftwareEcSignatureAlgorithm,
        buffers: SoftwareEcCadesBuffers,
    ): Boolean {
        val signer = SoftwareEcCadesEnvelope.readSigner(cmsDer)
        val signature = buffers.own(signer.encryptedDigest.octets)
        if (signature.isEmpty() || signature.size > algorithm.maxDerBytes) return false
        val actualAttributes = requireNotNull(signer.authenticatedAttributes)
        buffers.encode(actualAttributes, limits.maxSignedAttributesBytes,
            SoftwareEcCadesError.SIGNED_ATTRIBUTES_TOO_LARGE)
        val policy = SoftwareEcCadesPolicy(algorithm)
        val expectedAttributes = SoftwareEcCadesEnvelope.attributes(input, certificate, policy, buffers)
        val attributeDer = buffers.encode(expectedAttributes, limits.maxSignedAttributesBytes,
            SoftwareEcCadesError.SIGNED_ATTRIBUTES_TOO_LARGE)
        // Exact reconstruction accepts only this narrow DER profile: fixed OIDs/absent parameters,
        // detached data, one supplied certificate, one signer, three bound attributes, no extras.
        val expectedEnvelope = SoftwareEcCadesEnvelope.create(expectedAttributes, signature, certificate, policy)
        val expectedDer = buffers.encode(expectedEnvelope, limits.maxOutputBytes, SoftwareEcCadesError.OUTPUT_TOO_LARGE)
        return expectedDer.contentEquals(cmsDer) &&
            rawEngine.verify(attributeDer, signature, certificate.snapshot, algorithm)
    }
}
