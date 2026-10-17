package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** Exact Portal de la Transparencia AutoScript PAdES contract. QA-only until physical E2E. */
class TransparenciaPadesAdapter(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : MiniAppletPadesAdapter(CONTRACT, clock, provider) {
    companion object {
        val ID = SigningProtocolId("transparencia-direct-pades-v1")
        const val PROFILE_ID = "age-portal-de-la-transparencia"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://transparencia.sede.gob.es"
        const val PUBLIC_START_URL = "https://transparencia.sede.gob.es/procedimiento/portada?idProc=133628&idAmb=101524"
        const val SIGNING_PAGE_PREFIX = "https://transparencia.sede.gob.es/procedimiento/firma?"
        private val SIGNING_PAGE_PATTERN = Regex(
            "^https://transparencia\\.sede\\.gob\\.es/procedimiento/firma\\?" +
                "idProc=133628&idBorr=[1-9][0-9]*&idAmb=101524$",
        )

        fun isSigningPageUrl(pageUrl: String?): Boolean =
            pageUrl != null && SIGNING_PAGE_PATTERN.matches(pageUrl)
        const val SAFE_DESCRIPTION = "Firma PAdES con certificado en el Portal de la Transparencia"
        const val EXPECTED_EXTRA_PROPERTIES = "filters=nonexpired:true;\nheadless=true"
        const val MAX_PDF_BYTES = MiniAppletPadesAdapter.MAX_PDF_BYTES
        private val CONTRACT = MiniAppletPadesContract(
            id = ID,
            profileId = PROFILE_ID,
            initiatorOrigin = INITIATOR_ORIGIN,
            safeDescription = SAFE_DESCRIPTION,
            expectedExtraProperties = EXPECTED_EXTRA_PROPERTIES,
            signingAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
            profileVersion = PROFILE_VERSION,
            isSigningPage = { isSigningPageUrl(it) },
        )
    }
}
