package dev.junta.firmamobile.signing

import java.net.URI
import java.time.Clock

/** Local XAdES Enveloping adapter for the exact AIReF Instancia General signing contract. */
class AirefXadesEnvelopingAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
) : SigningProtocolAdapter by ParameterizedXadesEnvelopingAdapter(
    contract = XadesEnvelopingContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        algorithm = SigningAlgorithm.SHA1_WITH_RSA,
        safeDescription = SAFE_DESCRIPTION,
        contextMatches = {
            it.origin.scheme == HTTPS && it.origin.host == HOST && it.origin.port == HTTPS_PORT
        },
        extraProperties = "",
        payloadValidator = ByteArray::isExactAirefPayload,
        validatePayloadOnMatch = false,
    ),
    clock = clock,
) {
    companion object {
        val ID = SigningProtocolId("airef-xades-enveloping-v1")
        const val PROFILE_ID = "airef-instancia-general"
        const val PROFILE_VERSION = 1
        const val SAFE_DESCRIPTION = "Firma de la solicitud de Instancia General de la AIReF"
        const val INITIATOR_ORIGIN = "https://sede.airef.es"
        const val PUBLIC_START_URL =
            "$INITIATOR_ORIGIN/invesiteRE/action/inicio?authMethod=Clave&organismo=AIREF&tramite=AF-01"
        const val SIGNING_PATH = "/invesiteRE/action/solicitud/view"
        const val CLIENT_AUTH_SOURCE_ORIGIN = "https://pasarela.clave.gob.es"
        const val CLIENT_AUTH_SOURCE_URL = "$CLIENT_AUTH_SOURCE_ORIGIN/Proxy2/ServiceProvider"
        const val CLIENT_AUTH_REQUEST_ORIGIN = "https://pasarela-ident.clave.gob.es"
        const val CLIENT_AUTH_REQUEST_PATH = "/IdP2/AuthenticateCitizen"
        const val PAYLOAD_BYTES = 32
        private const val HTTPS = "https"
        private val HOST = requireNotNull(URI(INITIATOR_ORIGIN).host)
        private const val HTTPS_PORT = 443
    }
}

internal fun ByteArray.isExactAirefPayload(): Boolean = size == AirefXadesEnvelopingAdapter.PAYLOAD_BYTES
