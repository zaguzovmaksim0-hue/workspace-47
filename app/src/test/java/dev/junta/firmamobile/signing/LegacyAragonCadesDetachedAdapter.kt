package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock

/** Pre-migration Aragón orchestration from fd63b4c8; differential oracle, not a runtime adapter. */
internal class LegacyAragonCadesDetachedAdapter(
    private val clock: Clock = Clock.systemUTC(),
    private val provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

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
                    require(challenge.size == CHALLENGE_BYTES)
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    CadesDetachedCodec.createPreSign(challenge, CHALLENGE_BYTES, certificateChain, clock, provider)
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
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    CadesDetachedCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        detachedContent = ownedState.detachedContent(),
                        expectedContentBytes = CHALLENGE_BYTES,
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        provider = provider,
                    )
                }
                ProtocolCompletionResult.Success(LocalSignature(result))
            } catch (_: Exception) {
                ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
            }
        }
    }

    private fun NormalizedSignRequest.matchesContract(): Boolean =
        protocolId == ID &&
            context.profileId == PROFILE_ID &&
            context.profileVersion == PROFILE_VERSION &&
            context.origin.serialized == INITIATOR_ORIGIN &&
            format == SigningFormat.CADES &&
            algorithm == SigningAlgorithm.SHA1_WITH_RSA

    companion object {
        val ID = SigningProtocolId("aragon-siraw-local-cades-v1")
        const val PROFILE_ID = "aragon-siraw"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://aplicaciones.aragon.es"
        internal const val EXPECTED_EXTRA_PROPERTIES = "mode=explicit\nfilter=nonexpired"
        internal const val CHALLENGE_BYTES = 20
        private const val MAX_CERTIFICATES = 10
    }
}
