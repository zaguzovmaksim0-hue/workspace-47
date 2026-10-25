package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** QA-only local detached CAdES adapter for JCCM's public certificate-login probe. */
class JccmCertificateLoginProbeCadesAdapter internal constructor(
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
        safeDescription = SAFE_DESCRIPTION,
        revalidatePayloadOnComplete = true,
    ),
    clock = clock,
    provider = provider,
) {
    companion object {
        val ID = SigningProtocolId("jccm-certificate-login-probe-local-cades-v1")
        const val PROFILE_ID = "jccm-certificate-login-probe"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://ventanillaelectronica.jccm.es"
        const val SAFE_DESCRIPTION =
            "Validación pública de acceso con certificado de Castilla-La Mancha"
        const val EXPECTED_EXTRA_PROPERTIES = ""
        const val PAYLOAD_BYTES = 5
        internal val EXPECTED_PAYLOAD: ByteArray
            get() = EXPECTED_PAYLOAD_TEXT.encodeToByteArray()
        private const val EXPECTED_PAYLOAD_TEXT = "ABCDE"
    }
}
