package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock

/** Exact local contract; portal-specific policies are added only with their own migration. */
internal data class CadesDetachedContract(
    val id: SigningProtocolId,
    val profileId: String,
    val profileVersion: Int,
    val initiatorOrigin: String,
    val algorithm: SigningAlgorithm,
    val contentBytes: Int,
    val extraProperties: String,
)

/** Owns prepare/complete sequencing; the existing codec and state retain CMS and buffer ownership. */
internal class ParameterizedCadesDetachedAdapter(
    private val contract: CadesDetachedContract,
    private val clock: Clock,
    private val provider: Provider,
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = contract.id

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matchesContract() || certificateChain.isEmpty() ||
            certificateChain.size > MAX_CERTIFICATES
        ) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { challenge, extraProperties ->
                    require(challenge.size == contract.contentBytes)
                    require(extraProperties == contract.extraProperties)
                    CadesDetachedCodec.createPreSign(
                        challenge, contract.contentBytes, certificateChain, clock, provider,
                        signingAlgorithm = contract.algorithm,
                    )
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedAttributes,
                    state = CadesPreSignState(
                        placeholderCms = material.placeholderCms,
                        detachedContent = material.detachedContent,
                        signingCertificateFingerprint = material.signingCertificateFingerprint,
                    ),
                ),
            )
        } catch (_: Exception) {
            ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
    }

    override suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult {
        if (!request.matchesContract()) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? CadesPreSignState
        return if (state == null) {
            ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        } else state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    CadesDetachedCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        detachedContent = ownedState.detachedContent(),
                        expectedContentBytes = contract.contentBytes,
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        provider = provider,
                        signingAlgorithm = contract.algorithm,
                    )
                }
                ProtocolCompletionResult.Success(LocalSignature(result))
            } catch (_: Exception) {
                ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
            }
        }
    }

    private fun NormalizedSignRequest.matchesContract(): Boolean =
        protocolId == contract.id &&
            context.profileId == contract.profileId &&
            context.profileVersion == contract.profileVersion &&
            context.origin.serialized == contract.initiatorOrigin &&
            format == SigningFormat.CADES &&
            algorithm == contract.algorithm

    private companion object {
        const val MAX_CERTIFICATES = 10
    }
}
