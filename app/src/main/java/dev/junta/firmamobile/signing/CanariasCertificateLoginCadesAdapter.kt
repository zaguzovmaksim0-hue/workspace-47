package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Local detached CAdES adapter for the exact public Gobierno de Canarias certificate-login challenge. */
class CanariasCertificateLoginCadesAdapter internal constructor(
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
                        expectedContentBytes = CHALLENGE_BYTES,
                        certificateChain = certificateChain,
                        clock = clock,
                        provider = provider,
                        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
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
                val result = localSignature.withBytes { signature ->
                    CadesDetachedCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        detachedContent = ownedState.detachedContent(),
                        expectedContentBytes = CHALLENGE_BYTES,
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        provider = provider,
                        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
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
            context.pageUrl == SIGNING_PAGE_URL &&
            safeDescription == SAFE_DESCRIPTION &&
            algorithm == SigningAlgorithm.SHA1_WITH_RSA &&
            format == SigningFormat.CADES

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                isExactChallenge(data) && extraProperties == EXPECTED_EXTRA_PROPERTIES
            }
        }
    }.getOrDefault(false)

    companion object {
        val ID = SigningProtocolId("canarias-certificate-login-cades-v1")
        const val PROFILE_ID = "canarias-sede"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.gobiernodecanarias.org"
        const val PUBLIC_START_URL = "https://sede.gobiernodecanarias.org/sede/la_sede"
        const val SIGNING_PAGE_URL = "https://sede.gobiernodecanarias.org/sede/identificacion"
        const val SAFE_DESCRIPTION =
            "Acceso con certificado a la Sede electrónica del Gobierno de Canarias"
        const val EXPECTED_EXTRA_PROPERTIES =
            "format=CAdES Detached\n" +
                "serverUrl=https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService\n" +
                "referencesDigestMethod=http://www.w3.org/2001/04/xmlenc#sha512\n" +
                "filters=nonexpired:true;signingCert:true;issuer.rfc2254:" +
                "(&(!(CN=CiberCentro*))(!(CN=GobCanCA))(!(O=Gobierno de Canarias))" +
                "(!(O=PKI))(!(O=DO_NOT_TRUST*)))"
        internal val EXPECTED_FIXED_EXTRA_PROPERTIES = linkedMapOf(
            "format" to "CAdES Detached",
            "serverUrl" to "https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService",
            "referencesDigestMethod" to "http://www.w3.org/2001/04/xmlenc#sha512",
            "filters" to "nonexpired:true;signingCert:true;issuer.rfc2254:" +
                "(&(!(CN=CiberCentro*))(!(CN=GobCanCA))(!(O=Gobierno de Canarias))" +
                "(!(O=PKI))(!(O=DO_NOT_TRUST*)))",
        )
        internal const val CHALLENGE_BYTES = 29
        private const val MAX_CERTIFICATES = 10
        private val CHALLENGE_PATTERN = Regex(
            "(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun), [0-9]{2} " +
                "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) " +
                "[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT",
        )

        internal fun isExactChallenge(data: ByteArray): Boolean =
            data.size == CHALLENGE_BYTES &&
                data.all { byte -> (byte.toInt() and 0xff) in 0x20..0x7e } &&
                CHALLENGE_PATTERN.matches(data.toString(Charsets.US_ASCII))
    }
}
