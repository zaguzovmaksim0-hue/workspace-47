package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** QA-only local detached CAdES adapter for DGT's equipment-verification call. */
class DgtVerificationCadesAdapter internal constructor(
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
                MiniAppletPayloadCodec.withDecoded(payload) { challenge, extraProperties ->
                    require(challenge.size == PAYLOAD_BYTES)
                    require(challenge.contentEquals(EXPECTED_PAYLOAD))
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    CadesDetachedCodec.createPreSign(
                        content = challenge,
                        expectedContentBytes = PAYLOAD_BYTES,
                        certificateChain = certificateChain,
                        clock = clock,
                        provider = provider,
                    )
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedAttributes,
                    state = DgtCadesPreSignState(
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
        val state = preSign.consumeState(request) as? DgtCadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    CadesDetachedCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        detachedContent = ownedState.detachedContent(),
                        expectedContentBytes = PAYLOAD_BYTES,
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
        val ID = SigningProtocolId("dgt-verificacion-equipo-local-cades-v1")
        const val PROFILE_ID = "dgt-verificacion-equipo"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.dgt.gob.es"
        internal const val EXPECTED_EXTRA_PROPERTIES = "filter=nonexpired:"
        internal const val PAYLOAD_BYTES = 15
        internal val EXPECTED_PAYLOAD: ByteArray
            get() = EXPECTED_PAYLOAD_TEXT.encodeToByteArray()
        private const val EXPECTED_PAYLOAD_TEXT = "Cadena a firmar"
        private const val MAX_CERTIFICATES = 10
    }
}

private class DgtCadesPreSignState(
    placeholderCms: ByteArray,
    detachedContent: ByteArray,
    signingCertificateFingerprint: ByteArray,
) : PreSignState {
    private var placeholder = placeholderCms
    private var content = detachedContent
    private var fingerprint = signingCertificateFingerprint
    private var closed = false

    @Synchronized
    fun placeholderCms(): ByteArray = check(!closed).let { placeholder }

    @Synchronized
    fun detachedContent(): ByteArray = check(!closed).let { content }

    @Synchronized
    fun signingCertificateFingerprint(): ByteArray = check(!closed).let { fingerprint }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        placeholder.fill(0)
        content.fill(0)
        fingerprint.fill(0)
        placeholder = ByteArray(0)
        content = ByteArray(0)
        fingerprint = ByteArray(0)
    }
}
