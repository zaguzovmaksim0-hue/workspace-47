package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** QA-only local detached CAdES adapter for Tenerife's public AutoFirma document contract. */
class TenerifeCadesDetachedAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter by ParameterizedCadesDetachedAdapter(
    contract = CadesDetachedContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        initiatorOrigin = INITIATOR_ORIGIN,
        algorithm = SigningAlgorithm.SHA512_WITH_RSA,
        contentBytes = MAX_PAYLOAD_BYTES,
        extraProperties = EXPECTED_EXTRA_PROPERTIES,
        safeDescription = SAFE_DESCRIPTION,
        revalidatePayloadOnComplete = true,
        contentValidator = { it.isNotEmpty() && it.size <= MAX_PAYLOAD_BYTES },
    ),
    clock = clock,
    provider = provider,
) {
    companion object {
        val ID = SigningProtocolId("tenerife-sede-local-cades-v1")
        const val PROFILE_ID = "tenerife-sede-electronica"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.tenerife.es"
        const val SAFE_DESCRIPTION =
            "Firma de solicitud en la Sede electrónica del Cabildo Insular de Tenerife"
        const val EXPECTED_EXTRA_PROPERTIES = "mode=explicit"
        const val MAX_PAYLOAD_BYTES = 524_288
    }
}
