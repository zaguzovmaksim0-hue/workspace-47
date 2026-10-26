package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** Local detached CAdES adapter for the exact public Gobierno de Canarias certificate-login challenge. */
class CanariasCertificateLoginCadesAdapter internal constructor(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter by ParameterizedCadesDetachedAdapter(
    contract = CadesDetachedContract(
        id = ID,
        profileId = PROFILE_ID,
        profileVersion = PROFILE_VERSION,
        initiatorOrigin = INITIATOR_ORIGIN,
        algorithm = SigningAlgorithm.SHA1_WITH_RSA,
        contentBytes = CHALLENGE_BYTES,
        extraProperties = EXPECTED_EXTRA_PROPERTIES,
        safeDescription = SAFE_DESCRIPTION,
        pageUrl = SIGNING_PAGE_URL,
        revalidatePayloadOnComplete = true,
        contentValidator = ::isExactChallenge,
    ),
    clock = clock,
    provider = provider,
) {
    companion object {
        val ID = SigningProtocolId("canarias-certificate-login-cades-v1")
        const val PROFILE_ID = "canarias-sede"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.gobiernodecanarias.org"
        const val PUBLIC_START_URL = "https://sede.gobiernodecanarias.org/sede/la_sede"
        const val SIGNING_PAGE_URL = "https://sede.gobiernodecanarias.org/sede/identificacion"
        const val SAFE_DESCRIPTION =
            "Acceso con certificado a la Sede electrónica del Gobierno de Canarias"
        const val EXPECTED_EXTRA_PROPERTIES =
            "format=CAdES Detached\n" +
                "serverUrl=https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService\n" +
                "referencesDigestMethod=http://www.w3.org/2001/04/xmlenc#sha512\n" +
                "filters=nonexpired:true;signingCert:true;issuer.rfc2254:" +
                "(&(!(CN=CiberCentro*))(!(CN=GobCanCA))(!(O=Gobierno de Canarias))" +
                "(!(O=PKI))(!(O=DO_NOT_TRUST*)))"
        internal val EXPECTED_FIXED_EXTRA_PROPERTIES = linkedMapOf(
            "format" to "CAdES Detached",
            "serverUrl" to "https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService",
            "referencesDigestMethod" to "http://www.w3.org/2001/04/xmlenc#sha512",
            "filters" to "nonexpired:true;signingCert:true;issuer.rfc2254:" +
                "(&(!(CN=CiberCentro*))(!(CN=GobCanCA))(!(O=Gobierno de Canarias))" +
                "(!(O=PKI))(!(O=DO_NOT_TRUST*)))",
        )
        internal const val CHALLENGE_BYTES = 29
        private val CHALLENGE_PATTERN = Regex(
            "(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun), [0-9]{2} " +
                "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) " +
                "[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT",
        )

        internal fun isExactChallenge(data: ByteArray): Boolean =
            data.size == CHALLENGE_BYTES &&
                data.all { byte -> (byte.toInt() and 0xff) in 0x20..0x7e } &&
                CHALLENGE_PATTERN.matches(data.toString(Charsets.US_ASCII))
    }
}
