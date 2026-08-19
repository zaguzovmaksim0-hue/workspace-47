package dev.junta.firmamobile.signing

import java.security.cert.X509Certificate
import java.time.Clock

/** Local XAdES Enveloping adapter for the exact AIReF Instancia General signing contract. */
class AirefXadesEnvelopingAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.isExactAirefRequest() || certificateChain.isEmpty()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(extraProperties.isEmpty())
                    require(data.isExactAirefPayload())
                    SevillaAtseXadesEnvelopingCodec.createPreSign(
                        data = data,
                        certificateChain = certificateChain,
                        clock = clock,
                        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
                        payloadValidator = ByteArray::isExactAirefPayload,
                    )
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedInfo,
                    state = AirefXadesPreSignState(
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
        if (!request.isExactAirefRequest()) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? AirefXadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    SevillaAtseXadesEnvelopingCodec.complete(
                        unsignedDocument = ownedState.unsignedDocument(),
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
                        payloadValidator = ByteArray::isExactAirefPayload,
                    )
                }
                ProtocolCompletionResult.Success(LocalSignature(result))
            } catch (_: Exception) {
                ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
            }
        }
    }

    private fun NormalizedSignRequest.isExactAirefRequest(): Boolean =
        protocolId == ID &&
            context.profileId == PROFILE_ID &&
            context.profileVersion == PROFILE_VERSION &&
            context.origin.scheme == HTTPS &&
            context.origin.host == HOST &&
            context.origin.port == HTTPS_PORT &&
            algorithm == SigningAlgorithm.SHA1_WITH_RSA &&
            format == SigningFormat.XADES &&
            safeDescription == SAFE_DESCRIPTION

    companion object {
        val ID = SigningProtocolId("airef-xades-enveloping-v1")
        const val PROFILE_ID = "airef-instancia-general"
        const val PROFILE_VERSION = 1
        const val SAFE_DESCRIPTION = "Firma de la solicitud de Instancia General de la AIReF"
        const val PUBLIC_START_URL =
            "https://sede.airef.es/invesiteRE/action/inicio?authMethod=Clave&organismo=AIREF&tramite=AF-01"
        const val INITIATOR_ORIGIN = "https://sede.airef.es"
        const val SIGNING_PATH = "/invesiteRE/action/solicitud/view"
        const val CLIENT_AUTH_SOURCE_URL = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        const val CLIENT_AUTH_REQUEST_ORIGIN = "https://pasarela-ident.clave.gob.es"
        const val CLIENT_AUTH_REQUEST_PATH = "/IdP2/AuthenticateCitizen"
        const val PAYLOAD_BYTES = 32
        private const val HTTPS = "https"
        private const val HOST = "sede.airef.es"
        private const val HTTPS_PORT = 443
    }
}

private class AirefXadesPreSignState(
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

internal fun ByteArray.isExactAirefPayload(): Boolean = size == AirefXadesEnvelopingAdapter.PAYLOAD_BYTES
