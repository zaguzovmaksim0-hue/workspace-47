package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** Local detached CAdES adapter for Diputación de Badajoz's exact certificate-login contract. */
class DiputacionBadajozCadesAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter by ParameterizedCadesDetachedAdapter(
    contract = CadesDetachedContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        initiatorOrigin = INITIATOR_ORIGIN,
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        contentBytes = MAX_CHALLENGE_BYTES,
        extraProperties = EXPECTED_EXTRA_PROPERTIES,
        safeDescription = SAFE_DESCRIPTION,
        revalidatePayloadOnComplete = true,
        contentValidator = { it.isSafeChallenge() },
        extraPropertiesMatcher = {
            LiteralSigningProperties.canonicalize(it, EXPECTED_EXTRA_PROPERTIES) != null
        },
    ),
    clock = clock,
    provider = provider,
) {
    companion object {
        val ID = SigningProtocolId("diputacion-badajoz-login-cades-v1")
        const val PROFILE_ID = "diputacion-badajoz-portal"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.dip-badajoz.es"
        const val START_URL = INITIATOR_ORIGIN
        const val LOGIN_PAGE_URL =
            "$INITIATOR_ORIGIN/portal/entidades.do?ent_id=10&idioma=1"
        const val SAFE_DESCRIPTION = "Acceso con certificado a la Diputación de Badajoz"
        const val EXPECTED_EXTRA_PROPERTIES =
            "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true"
        const val MAX_CHALLENGE_BYTES = 512

        internal fun ByteArray.isSafeChallenge(): Boolean =
            isNotEmpty() && size <= MAX_CHALLENGE_BYTES && all { byte ->
                val v = byte.toInt() and 0xff
                v in 0x20..0x7e
            }
    }
}
