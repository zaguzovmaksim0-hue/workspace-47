package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** QA-only local detached CAdES adapter for UGR's exact certificate-login contract. */
class UgrCadesDetachedAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter by ParameterizedCadesDetachedAdapter(
    contract = CadesDetachedContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        initiatorOrigin = INITIATOR_ORIGIN,
        algorithm = SigningAlgorithm.SHA1_WITH_RSA,
        contentBytes = PAYLOAD_BYTES,
        extraProperties = EXPECTED_EXTRA_PROPERTIES,
        contentLiteral = EXPECTED_PAYLOAD,
        safeDescription = SAFE_DESCRIPTION,
        revalidatePayloadOnComplete = true,
    ),
    clock = clock,
    provider = provider,
) {
    companion object {
        val ID = SigningProtocolId("ugr-certificado-login-local-cades-v1")
        const val PROFILE_ID = "ugr-certificado-login"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.ugr.es"
        const val START_URL = "$INITIATOR_ORIGIN/Hades/jsp/pantallacertificado.jsp"
        const val EXPECTED_EXTRA_PROPERTIES = ""
        const val PAYLOAD_BYTES = 22
        private const val SAFE_DESCRIPTION = "Acceso con certificado a la Universidad de Granada"
        private const val EXPECTED_PAYLOAD = "Universidad de Granada"
    }
}
