package dev.junta.firmamobile.signing

import java.security.cert.X509Certificate
import java.time.Clock

/** Only the two observed portal contracts; context and payload policy retain their original order. */
internal data class XadesEnvelopingContract(
    val id: SigningProtocolId,
    val profileId: String,
    val profileVersion: Int,
    val algorithm: SigningAlgorithm,
    val safeDescription: String,
    val contextMatches: (SigningContext) -> Boolean,
    val extraProperties: String,
    val payloadValidator: (ByteArray) -> Boolean,
    val validatePayloadOnMatch: Boolean,
)

/** Shares sequencing and state ownership while leaving the existing XML codec unchanged. */
internal class ParameterizedXadesEnvelopingAdapter(
    private val contract: XadesEnvelopingContract,
    private val clock: Clock,
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = contract.id

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
                    require(matchesPayload(data, extraProperties))
                    SevillaAtseXadesEnvelopingCodec.createPreSign(
                        data = data,
                        certificateChain = certificateChain,
                        clock = clock,
                        signingAlgorithm = contract.algorithm,
                        payloadValidator = contract.payloadValidator,
                    )
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedInfo,
                    state = XadesEnvelopingPreSignState(
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
        val state = preSign.consumeState(request) as? XadesEnvelopingPreSignState
        return if (state == null) {
            ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        } else state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    SevillaAtseXadesEnvelopingCodec.complete(
                        unsignedDocument = ownedState.unsignedDocument(),
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        signingAlgorithm = contract.algorithm,
                        payloadValidator = contract.payloadValidator,
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
            contract.contextMatches(context) &&
            algorithm == contract.algorithm &&
            format == SigningFormat.XADES &&
            safeDescription == contract.safeDescription

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean =
        !contract.validatePayloadOnMatch || runCatching {
            withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    matchesPayload(data, extraProperties)
                }
            }
        }.getOrDefault(false)

    // CDTI checks data first during matching; AIReF checks properties first inside prepare.
    private fun matchesPayload(data: ByteArray, extraProperties: String): Boolean =
        if (contract.validatePayloadOnMatch) {
            contract.payloadValidator(data) && extraProperties == contract.extraProperties
        } else {
            extraProperties == contract.extraProperties && contract.payloadValidator(data)
        }
}

private class XadesEnvelopingPreSignState(
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
