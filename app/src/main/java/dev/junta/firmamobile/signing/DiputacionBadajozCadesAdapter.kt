package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Local detached CAdES adapter for Diputación de Badajoz's exact certificate-login contract. */
class DiputacionBadajozCadesAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val provider: Provider = BouncyCastleProvider(),
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
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(data.isNotEmpty() && data.size <= MAX_CHALLENGE_BYTES)
                    require(data.isSafeChallenge())
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    CadesDetachedCodec.createPreSign(
                        content = data,
                        expectedContentBytes = data.size,
                        certificateChain = certificateChain,
                        clock = clock,
                        provider = provider,
                        signingAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
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
        if (!request.matchesPayloadContract()) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
        val state = preSign.consumeState(request) as? CadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val detachedContent = ownedState.detachedContent()
                val result = localSignature.withBytes { signature ->
                    CadesDetachedCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        detachedContent = detachedContent,
                        expectedContentBytes = detachedContent.size,
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        provider = provider,
                        signingAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
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
            safeDescription == SAFE_DESCRIPTION &&
            algorithm == SigningAlgorithm.SHA256_WITH_RSA &&
            format == SigningFormat.CADES

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                data.isNotEmpty() && data.size <= MAX_CHALLENGE_BYTES &&
                    data.isSafeChallenge() &&
                    extraProperties == EXPECTED_EXTRA_PROPERTIES
            }
        }
    }.getOrDefault(false)

    companion object {
        val ID = SigningProtocolId("diputacion-badajoz-login-cades-v1")
        const val PROFILE_ID = "diputacion-badajoz-portal"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.dip-badajoz.es"
        const val SAFE_DESCRIPTION = "Acceso con certificado a la Diputación de Badajoz"
        const val EXPECTED_EXTRA_PROPERTIES =
            "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true"
        const val MAX_CHALLENGE_BYTES = 512
        private const val MAX_CERTIFICATES = 10

        internal fun ByteArray.isSafeChallenge(): Boolean =
            isNotEmpty() && size <= MAX_CHALLENGE_BYTES && all { byte ->
                val v = byte.toInt() and 0xff
                v in 0x20..0x7e
            }
    }
}
