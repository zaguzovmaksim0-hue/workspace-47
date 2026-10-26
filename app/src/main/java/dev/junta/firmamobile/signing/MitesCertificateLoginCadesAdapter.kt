package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** QA-only local CAdES adapter for MITES' exact public certificate-login challenge contract. */
class MitesCertificateLoginCadesAdapter internal constructor(
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

        fun isExactChallenge(data: ByteArray): Boolean =
            data.size == CHALLENGE_BYTES && data.all { byte -> byte.toInt() in 0x61..0x7a }
    }
}
