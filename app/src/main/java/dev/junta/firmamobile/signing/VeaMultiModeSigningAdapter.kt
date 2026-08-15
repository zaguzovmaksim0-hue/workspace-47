package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.VeaMultiModeBridgeRequest
import dev.junta.firmamobile.browser.VeaMultiModeReplyChannel
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.util.Base64
import java.util.UUID

interface VeaMultiModeReplySink {
    val requestId: UUID
    fun success(signaturesB64: String, certificateB64: String): Boolean
    fun failure(code: SigningErrorCode): Boolean
    fun abandon(): Boolean
}

class VeaMultiModeSigningAdapter(
    private val prehashedEngine: PrehashedRsaSignatureEngine = JcaPrehashedRsaSignatureEngine(),
) {
    fun execute(
        request: VeaMultiModeBridgeRequest,
        identity: UnlockedIdentity,
        reply: VeaMultiModeReplySink,
    ): Boolean {
        if (request.hashes.isEmpty() || request.hashes.size != request.arrayLength) {
            reply.failure(SigningErrorCode.INVALID_REQUEST)
            return false
        }
        val signatures = mutableListOf<String>()
        val generatedSignatures = mutableListOf<LocalSignature>()

        try {
            for (digest in request.hashes) {
                when (val result = prehashedEngine.sign(digest, request.hashAlgorithm, identity)) {
                    is LocalSignatureResult.Failure -> {
                        reply.failure(SigningErrorCode.LOCAL_SIGNATURE_FAILED)
                        return false
                    }
                    is LocalSignatureResult.Success -> {
                        generatedSignatures.add(result.signature)
                        val b64 = result.signature.withBytes { bytes ->
                            Base64.getEncoder().encodeToString(bytes)
                        }
                        signatures.add(b64)
                    }
                }
            }

            val signaturePayload = if (signatures.size == 1) {
                signatures[0]
            } else {
                signatures.joinToString(":")
            }

            val certB64 = Base64.getEncoder().encodeToString(identity.certificate.encoded)
            val delivered = reply.success(signaturePayload, certB64)
            if (!delivered) {
                reply.failure(SigningErrorCode.RESULT_DELIVERY_FAILED)
                return false
            }
            return true
        } catch (_: Exception) {
            reply.failure(SigningErrorCode.PROTOCOL_FAILED)
            return false
        } finally {
            generatedSignatures.forEach(LocalSignature::close)
            generatedSignatures.clear()
        }
    }

    fun replySink(channel: VeaMultiModeReplyChannel): VeaMultiModeReplySink = object : VeaMultiModeReplySink {
        override val requestId: UUID = channel.requestId

        override fun success(signaturesB64: String, certificateB64: String): Boolean =
            channel.success(signaturesB64, certificateB64)

        override fun failure(code: SigningErrorCode): Boolean =
            channel.failure(code)

        override fun abandon(): Boolean =
            channel.abandon()
    }
}
