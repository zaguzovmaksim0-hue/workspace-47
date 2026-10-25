package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock

/** Pre-migration JCCM orchestration from a8c363e4; differential oracle, not a runtime adapter. */
internal class LegacyJccmCertificateLoginProbeCadesAdapter(
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
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(data.size == PAYLOAD_BYTES)
                    require(data.contentEquals(EXPECTED_PAYLOAD))
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    CadesDetachedCodec.createPreSign(
                        content = data,
                        certificateChain = certificateChain,
                        clock = clock,
                        provider = provider,
                        expectedContentBytes = PAYLOAD_BYTES,
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
            !request.matchesPayloadContract() -> SigningErrorCode.PROTOCOL_FAILED
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
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        provider = provider,
                        expectedContentBytes = PAYLOAD_BYTES,
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
            algorithm == SigningAlgorithm.SHA1_WITH_RSA &&
            format == SigningFormat.CADES

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                data.size == PAYLOAD_BYTES &&
                    data.contentEquals(EXPECTED_PAYLOAD) &&
                    extraProperties == EXPECTED_EXTRA_PROPERTIES
            }
        }
    }.getOrDefault(false)

    companion object {
        val ID = SigningProtocolId("jccm-certificate-login-probe-local-cades-v1")
        const val PROFILE_ID = "jccm-certificate-login-probe"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://ventanillaelectronica.jccm.es"
        const val SAFE_DESCRIPTION =
            "Validación pública de acceso con certificado de Castilla-La Mancha"
        const val EXPECTED_EXTRA_PROPERTIES = ""
        const val PAYLOAD_BYTES = 5
        internal val EXPECTED_PAYLOAD: ByteArray
            get() = EXPECTED_PAYLOAD_TEXT.encodeToByteArray()
        private const val MAX_CERTIFICATES = 10
        private const val EXPECTED_PAYLOAD_TEXT = "ABCDE"
    }
}
