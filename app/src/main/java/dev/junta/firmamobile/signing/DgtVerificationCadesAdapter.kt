package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** QA-only local detached CAdES adapter for DGT's equipment-verification call. */
class DgtVerificationCadesAdapter internal constructor(
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
        contentLiteral = EXPECTED_PAYLOAD_TEXT,
    ),
    clock = clock,
    provider = provider,
) {
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
    }
}
