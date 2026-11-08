package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** AEMET's AUTO login signs only a decimal nonce, using the client's explicit detached CAdES default. */
class AemetCertificateLoginCadesAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter by ParameterizedCadesDetachedAdapter(
    contract = CadesDetachedContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        initiatorOrigin = INITIATOR_ORIGIN,
        algorithm = SigningAlgorithm.SHA1_WITH_RSA,
        contentBytes = MAX_CHALLENGE_BYTES,
        extraProperties = "",
        safeDescription = SAFE_DESCRIPTION,
        pageUrls = setOf(SIGNING_PAGE_URL),
        revalidatePayloadOnComplete = true,
        contentValidator = ::isExactChallenge,
    ),
    clock = clock,
    provider = provider,
) {
    companion object {
        val ID = SigningProtocolId("aemet-certificate-login-cades-v1")
        const val PROFILE_ID = "aemet-public-solicitud-navigation"
        const val PROFILE_VERSION = 2
        const val INITIATOR_ORIGIN = "https://sede.aemet.gob.es"
        const val PUBLIC_START_URL = "$INITIATOR_ORIGIN/AEMET/es/GestionPeticiones/home"
        const val SIGNING_PAGE_URL = "$INITIATOR_ORIGIN/AEMET/es/GestionPeticiones/sso"
        const val SAFE_DESCRIPTION = "Acceso con certificado a la Sede electrónica de AEMET"
        internal const val MAX_CHALLENGE_BASE64_CHARS = 8
        private const val MAX_CHALLENGE_BYTES = 5
        private val CHALLENGE_PATTERN = Regex("(?:0|[1-9][0-9]{0,3}|10000)")

        internal fun isExactChallenge(data: ByteArray): Boolean =
            data.size in 1..MAX_CHALLENGE_BYTES &&
                data.all { it.toInt() in '0'.code..'9'.code } &&
                CHALLENGE_PATTERN.matches(data.toString(Charsets.US_ASCII))
    }
}
