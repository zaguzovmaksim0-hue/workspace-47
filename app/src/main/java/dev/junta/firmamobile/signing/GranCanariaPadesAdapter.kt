package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** Exact public Gran Canaria MiniApplet PAdES contract. QA-only until physical E2E. */
class GranCanariaPadesAdapter(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : MiniAppletPadesAdapter(CONTRACT, clock, provider) {
    companion object {
        val ID = SigningProtocolId("gran-canaria-direct-pades-v1")
        const val PROFILE_ID = "gran-canaria-sede-electronica"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.grancanaria.com"
        const val PUBLIC_START_URL = "$INITIATOR_ORIGIN/sede-privado/instancia-general?inicio"
        const val SIGNING_PAGE_URL = "$INITIATOR_ORIGIN/sede-privado/auth"
        const val SAFE_DESCRIPTION = "Firma PAdES de solicitud en la Sede del Cabildo de Gran Canaria"
        const val EXPECTED_EXTRA_PROPERTIES = "headless=true\nfilters=nonexpired:"
        const val MAX_PDF_BYTES = MiniAppletPadesAdapter.MAX_PDF_BYTES
        private val CONTRACT = MiniAppletPadesContract(
            id = ID,
            profileId = PROFILE_ID,
            initiatorOrigin = INITIATOR_ORIGIN,
            safeDescription = SAFE_DESCRIPTION,
            expectedExtraProperties = EXPECTED_EXTRA_PROPERTIES,
            signingAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
            profileVersion = PROFILE_VERSION,
            isSigningPage = { it == SIGNING_PAGE_URL },
        )
    }
}
