package dev.junta.firmamobile.signing

import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Base64

/** QA-only local detached CAdES adapter for Consell d'Eivissa Instancia General. */
class EivissaCadesDetachedAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter by ParameterizedCadesDetachedAdapter(
    contract = CadesDetachedContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        initiatorOrigin = INITIATOR_ORIGIN,
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        contentBytes = MAX_PAYLOAD_BYTES,
        extraProperties = "",
        safeDescription = SAFE_DESCRIPTION,
        contentValidator = { it.isNotEmpty() },
        extraPropertiesMatcher = { true },
        certificatePropertiesMatcher = ::matchesPortalProperties,
    ),
    clock = clock,
    provider = provider,
) {
    companion object {
        val ID = SigningProtocolId("eivissa-instancia-general-cades-v1")
        const val PROFILE_ID = "eivissa-sede-electronica"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://seu.conselldeivissa.es"
        val START_URL = "$INITIATOR_ORIGIN/"
        const val PROCEDURE_ID = "6269002703260065905043"
        const val SAFE_DESCRIPTION = "Firma de Instancia General en la Sede electrónica del Consell Insular d’Eivissa"
        const val MAX_PAYLOAD_BYTES = 524_288
        const val MAX_EXTRA_PROPERTIES_CHARS = 16_384
        private val MIME_TYPE = Regex("[A-Za-z0-9][A-Za-z0-9!#$&^_.+\\-/]{0,127}")
        private val EXTRA_PROPERTIES = Regex(
            "headless=true\\nfilter=encodedcert:([A-Za-z0-9+/]+={0,2});filter=nonexpired:\\n" +
                "mode=implicit\\n(?:mimeType=([^\\r\\n]{1,128})\\n)?",
        )

        private fun matchesPortalProperties(properties: String, signingCertificate: X509Certificate): Boolean {
            val match = properties.takeIf { it.length <= MAX_EXTRA_PROPERTIES_CHARS }
                ?.let(EXTRA_PROPERTIES::matchEntire)
                ?: return false
            val mimeType = match.groupValues.getOrNull(2).orEmpty()
            return (mimeType.isEmpty() || MIME_TYPE.matches(mimeType)) &&
                match.groupValues[1] == Base64.getEncoder().encodeToString(signingCertificate.encoded)
        }
    }
}
