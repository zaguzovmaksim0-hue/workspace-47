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
    val contentLiteral: String? = null,
    val safeDescription: String? = null,
    val revalidatePayloadOnComplete: Boolean = false,
    // Exact size by default; portal validators may accept shorter bounded challenges.
    val contentValidator: (ByteArray) -> Boolean = { it.size == contentBytes },
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
                    require(matchesPayload(challenge, extraProperties))
                    CadesDetachedCodec.createPreSign(
                        challenge, challenge.size, certificateChain, clock, provider,
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
        val validationError = when {
            !request.matchesContract() -> SigningErrorCode.UNSUPPORTED_PROTOCOL
            contract.revalidatePayloadOnComplete && !request.matchesPayloadContract() ->
                SigningErrorCode.PROTOCOL_FAILED
            else -> null
        }
        if (validationError != null) return ProtocolCompletionResult.Failure(validationError)
        val state = preSign.consumeState(request) as? CadesPreSignState
        return if (state == null) {
            ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        } else state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    CadesDetachedCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        detachedContent = ownedState.detachedContent(),
                        expectedContentBytes = ownedState.detachedContent().size,
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
            (contract.safeDescription == null || safeDescription == contract.safeDescription) &&
            format == SigningFormat.CADES &&
            algorithm == contract.algorithm

    private fun matchesPayload(data: ByteArray, extraProperties: String): Boolean =
        data.size <= contract.contentBytes && contract.contentValidator(data) &&
            (contract.contentLiteral == null || data.contentEquals(contract.contentLiteral.encodeToByteArray())) &&
            extraProperties == contract.extraProperties

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                matchesPayload(data, extraProperties)
            }
        }
    }.getOrDefault(false)

    private companion object {
        const val MAX_CERTIFICATES = 10
    }
}
