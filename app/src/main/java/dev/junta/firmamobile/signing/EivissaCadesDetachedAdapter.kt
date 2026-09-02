package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** QA-only local detached CAdES adapter for Consell d'Eivissa Instancia General. */
class EivissaCadesDetachedAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val provider: Provider = BouncyCastleProvider(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matchesContract() || certificateChain.isEmpty() || certificateChain.size > MAX_CERTIFICATES) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    require(data.isNotEmpty() && data.size <= MAX_PAYLOAD_BYTES)
                    require(extraProperties.matchesPortalProperties(certificateChain.first()))
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
        if (!request.matchesContract()) return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
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
        protocolId == ID && context.profileId == PROFILE_ID && context.profileVersion == PROFILE_VERSION &&
            context.origin.serialized == INITIATOR_ORIGIN && safeDescription == SAFE_DESCRIPTION &&
            algorithm == SigningAlgorithm.SHA256_WITH_RSA && format == SigningFormat.CADES

    private fun String.matchesPortalProperties(signingCertificate: X509Certificate): Boolean {
        if (length > MAX_EXTRA_PROPERTIES_CHARS) return false
        val match = EXTRA_PROPERTIES.matchEntire(this) ?: return false
        val encodedCertificate = match.groupValues[1]
        val mimeType = match.groupValues.getOrNull(2).orEmpty()
        if (mimeType.isNotEmpty() && !MIME_TYPE.matches(mimeType)) return false
        val expected = Base64.getEncoder().encodeToString(signingCertificate.encoded)
        return encodedCertificate == expected
    }

    companion object {
        val ID = SigningProtocolId("eivissa-instancia-general-cades-v1")
        const val PROFILE_ID = "eivissa-sede-electronica"
        const val PROFILE_VERSION = 2
        const val INITIATOR_ORIGIN = "https://seu.conselldeivissa.es"
        const val PROCEDURE_ID = "6269002703260065905043"
        const val START_URL =
            "https://seu.conselldeivissa.es/sta/CarpetaPublic/Public?" +
                "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=$PROCEDURE_ID"
        const val SAFE_DESCRIPTION = "Firma de Instancia General en la Sede electrónica del Consell Insular d’Eivissa"
        const val MAX_PAYLOAD_BYTES = 524_288
        const val MAX_EXTRA_PROPERTIES_CHARS = 16_384
        private const val MAX_CERTIFICATES = 10
        private val MIME_TYPE = Regex("[A-Za-z0-9][A-Za-z0-9!#$&^_.+\\-/]{0,127}")
        private val EXTRA_PROPERTIES = Regex(
            "headless=true\\nfilter=encodedcert:([A-Za-z0-9+/]+={0,2});filter=nonexpired:\\n" +
                "mode=implicit\\n(?:mimeType=([^\\r\\n]{1,128})\\n)?",
        )
    }
}
