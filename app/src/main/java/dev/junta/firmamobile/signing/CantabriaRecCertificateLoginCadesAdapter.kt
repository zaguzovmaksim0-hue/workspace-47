package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** QA-only local CAdES adapter for Cantabria REC’s exact public certificate-login challenge contract. */
class CantabriaRecCertificateLoginCadesAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter by ParameterizedCadesDetachedAdapter(
    contract = CadesDetachedContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        initiatorOrigin = INITIATOR_ORIGIN,
        algorithm = SigningAlgorithm.SHA512_WITH_RSA,
        contentBytes = CHALLENGE_BYTES,
        extraProperties = EXPECTED_EXTRA_PROPERTIES,
        safeDescription = SAFE_DESCRIPTION,
        revalidatePayloadOnComplete = true,
        contentValidator = ::isExactChallenge,
        payloadRevalidationError = SigningErrorCode.UNSUPPORTED_PROTOCOL,
    ),
    clock = clock,
    provider = provider,
) {
    companion object {
        val ID = SigningProtocolId("cantabria-rec-cert-login-cades-v1")
        const val PROFILE_ID = "cantabria-rec-cert-login"
        const val PROFILE_VERSION = 1
        const val START_URL = "https://rec.cantabria.es/rec/bienvenida.htm"
        const val INITIATOR_ORIGIN = "https://rec.cantabria.es"
        const val SAFE_DESCRIPTION = "Acceso con certificado al Registro Electrónico Común de Cantabria"
        const val EXPECTED_EXTRA_PROPERTIES =
            "filters=\nmode=implicit"
        const val CHALLENGE_BYTES = 40

        internal fun isExactChallenge(data: ByteArray): Boolean =
            data.size == CHALLENGE_BYTES && data.all { byte ->
                byte.toInt() in '0'.code..'9'.code || byte.toInt() in 'a'.code..'f'.code
            }
    }
}
