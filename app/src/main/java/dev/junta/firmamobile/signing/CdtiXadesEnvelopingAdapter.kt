package dev.junta.firmamobile.signing

import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Base64

/** QA-only XAdES Enveloping adapter for CDTI's public certificate-validation challenge. */
class CdtiXadesEnvelopingAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matchesContract() || !request.matchesPayloadContract() || certificateChain.isEmpty()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(isExactChallenge(data))
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    SevillaAtseXadesEnvelopingCodec.createPreSign(
                        data = data,
                        certificateChain = certificateChain,
                        clock = clock,
                        signingAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
                        payloadValidator = ::isExactChallenge,
                    )
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedInfo,
                    state = CdtiXadesPreSignState(
                        material.unsignedDocument,
                        material.signingCertificateFingerprint,
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
        val state = preSign.consumeState(request) as? CdtiXadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    SevillaAtseXadesEnvelopingCodec.complete(
                        unsignedDocument = ownedState.unsignedDocument(),
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        signingAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
                        payloadValidator = ::isExactChallenge,
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
            context.pageUrl == START_URL &&
            algorithm == SigningAlgorithm.SHA512_WITH_RSA &&
            format == SigningFormat.XADES &&
            safeDescription == SAFE_DESCRIPTION

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                isExactChallenge(data) && extraProperties == EXPECTED_EXTRA_PROPERTIES
            }
        }
    }.getOrDefault(false)

    companion object {
        val ID = SigningProtocolId("cdti-xades-enveloping-v1")
        const val PROFILE_ID = "cdti-certificate-validation"
        const val PROFILE_VERSION = 1
        const val START_URL =
            "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"
        const val INITIATOR_ORIGIN = "https://sede.cdti.gob.es"
        const val SAFE_DESCRIPTION = "Validación de certificado digital en CDTI"
        const val EXPECTED_EXTRA_PROPERTIES = "filters=nonexpired"

        internal fun isExactChallenge(data: ByteArray): Boolean {
            if (data.size != CHALLENGE_DECODED_BYTES) return false
            val canonical = Base64.getEncoder().withoutPadding().encodeToString(data)
            if (canonical.length != CHALLENGE_TOKEN_CHARS) return false
            if (!CHALLENGE_PREFIX_PATTERN.matches(canonical.dropLast(1))) return false

            val tailIndex = BASE64_ALPHABET.indexOf(canonical.last())
            if (tailIndex < 0) return false
            val quartetStart = tailIndex and BASE64_QUARTET_MASK
            return (quartetStart until quartetStart + 4).any { index ->
                BASE64_ALPHABET[index].isLowerCase() || BASE64_ALPHABET[index].isDigit()
            }
        }

        private val CHALLENGE_PREFIX_PATTERN = Regex("CertExp[0-9a-f]{32}[0-9a-z]{23}")
        private const val BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        private const val BASE64_QUARTET_MASK = 0x3c
        private const val CHALLENGE_TOKEN_CHARS = 63
        private const val CHALLENGE_DECODED_BYTES = 47
    }
}

private class CdtiXadesPreSignState(
    unsignedDocument: ByteArray,
    signingCertificateFingerprint: ByteArray,
) : PreSignState {
    private var document = unsignedDocument
    private var fingerprint = signingCertificateFingerprint
    private var closed = false

    @Synchronized
    fun unsignedDocument(): ByteArray = check(!closed).let { document }

    @Synchronized
    fun signingCertificateFingerprint(): ByteArray = check(!closed).let { fingerprint }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        document.fill(0)
        fingerprint.fill(0)
        document = ByteArray(0)
        fingerprint = ByteArray(0)
    }
}
