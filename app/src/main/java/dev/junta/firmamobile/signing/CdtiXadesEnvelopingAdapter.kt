package dev.junta.firmamobile.signing

import java.time.Clock
import java.util.Base64

/** QA-only XAdES Enveloping adapter for CDTI's public certificate-validation challenge. */
class CdtiXadesEnvelopingAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
) : SigningProtocolAdapter by ParameterizedXadesEnvelopingAdapter(
    contract = XadesEnvelopingContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        algorithm = SigningAlgorithm.SHA512_WITH_RSA,
        safeDescription = SAFE_DESCRIPTION,
        contextMatches = { it.origin.serialized == INITIATOR_ORIGIN && it.pageUrl == START_URL },
        extraProperties = EXPECTED_EXTRA_PROPERTIES,
        payloadValidator = ::isExactChallenge,
        validatePayloadOnMatch = true,
    ),
    clock = clock,
) {
    companion object {
        val ID = SigningProtocolId("cdti-xades-enveloping-v1")
        const val PROFILE_ID = "cdti-certificate-validation"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.cdti.gob.es"
        const val START_URL =
            "$INITIATOR_ORIGIN/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"
        const val SAFE_DESCRIPTION = "Validación de certificado digital en CDTI"
        const val EXPECTED_EXTRA_PROPERTIES = "filters=nonexpired"

        internal fun isExactChallenge(data: ByteArray): Boolean {
            if (data.size != CHALLENGE_DECODED_BYTES) return false
            val canonical = Base64.getEncoder().withoutPadding().encodeToString(data)
            if (canonical.length != CHALLENGE_TOKEN_CHARS) return false
            if (!CHALLENGE_PREFIX_PATTERN.matches(canonical.dropLast(1))) return false

            val tailIndex = BASE64_ALPHABET.indexOf(canonical.last())
            if (tailIndex < 0) return false
            val quartetStart = tailIndex and BASE64_QUARTET_MASK
            return (quartetStart until quartetStart + 4).any { index ->
                BASE64_ALPHABET[index].isLowerCase() || BASE64_ALPHABET[index].isDigit()
            }
        }

        private val CHALLENGE_PREFIX_PATTERN = Regex("CertExp[0-9a-f]{32}[0-9a-z]{23}")
        private const val BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        private const val BASE64_QUARTET_MASK = 0x3c
        private const val CHALLENGE_TOKEN_CHARS = 63
        private const val CHALLENGE_DECODED_BYTES = 47
    }
}
