package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** QA-only local CAdES adapter for MITES' exact public certificate-login challenge contract. */
class MitesCertificateLoginCadesAdapter internal constructor(
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
                    require(isExactChallenge(data))
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    CadesDetachedCodec.createPreSign(
                        content = data,
                        certificateChain = certificateChain,
                        clock = clock,
                        provider = provider,
                        expectedContentBytes = CHALLENGE_BYTES,
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
        if (!request.matchesContract() || !request.matchesPayloadContract()) {
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
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        provider = provider,
                        expectedContentBytes = CHALLENGE_BYTES,
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
            algorithm == SigningAlgorithm.SHA512_WITH_RSA &&
            format == SigningFormat.CADES

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                isExactChallenge(data) && extraProperties == EXPECTED_EXTRA_PROPERTIES
            }
        }
    }.getOrDefault(false)

    companion object {
        val ID = SigningProtocolId("mites-certificate-login-local-cades-v1")
        const val PROFILE_ID = "mites-certificate-login"
        const val PROFILE_VERSION = 1
        const val START_URL = "https://sede.mites.gob.es/"
        const val LOGIN_PAGE_URL = "https://sede.mites.gob.es/auth"
        const val INITIATOR_ORIGIN = "https://sede.mites.gob.es"
        const val SAFE_DESCRIPTION = "Acceso con certificado a la Sede del Ministerio de Trabajo"
        const val EXPECTED_EXTRA_PROPERTIES =
            "mode=implicit\nfilters.1=signingCert:;keyusage.nonrepudiation:true;nonexpired:"
        const val CHALLENGE_BYTES = 10
        private const val MAX_CERTIFICATES = 10

        fun isExactChallenge(data: ByteArray): Boolean =
            data.size == CHALLENGE_BYTES && data.all { byte -> byte.toInt() in 0x61..0x7a }
    }
}
