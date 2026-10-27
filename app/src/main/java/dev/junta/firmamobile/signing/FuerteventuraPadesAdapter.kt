package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** Exact authenticated Fuerteventura MiniApplet PAdES contract. QA-only until physical E2E. */
class FuerteventuraPadesAdapter(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : MiniAppletPadesAdapter(CONTRACT, clock, provider) {
    companion object {
        val ID = SigningProtocolId("fuerteventura-register-pades-v1")
        const val PROFILE_ID = "fuerteventura-sede-electronica"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.cabildofuer.es"
        const val PUBLIC_START_URL = "$INITIATOR_ORIGIN/eAdmin/Registrar.do?action=comenzar&tipoReg=1"
        const val SIGNING_PAGE_URL = "$INITIATOR_ORIGIN/eAdmin/Registrar.do?action=verYfirmar&modo=cert"
        const val SAFE_DESCRIPTION = "Firma PAdES de solicitud en la Sede electrónica del Cabildo Insular de Fuerteventura"
        val EXPECTED_EXTRA_PROPERTIES = listOf(
            "signaturePositionOnPageLowerLeftX = 50",
            "signaturePositionOnPageLowerLeftY = 15",
            "signaturePositionOnPageUpperRightX = 150",
            "signaturePositionOnPageUpperRightY = 50",
            "signaturePages = all",
            "layer2Text= Firmado por \$\$SUBJECTCN\$\$ el día \$\$SIGNDATE=dd/MM/yyyy\$\$ \$\$ORGANIZATION\$\$",
            "layer2FontSize= 6",
            "layer2FontFamily= 0",
            "layer2FontStyle= 0",
            "signatureRotation= 0",
            "includeQuestionMark= false",
            "obfuscateCertText= true",
        ).joinToString(separator = "\n", postfix = "\n")
        const val MAX_PDF_BYTES = MiniAppletPadesAdapter.MAX_PDF_BYTES
        private val CONTRACT = MiniAppletPadesContract(
            id = ID,
            profileId = PROFILE_ID,
            initiatorOrigin = INITIATOR_ORIGIN,
            safeDescription = SAFE_DESCRIPTION,
            expectedExtraProperties = EXPECTED_EXTRA_PROPERTIES,
            signingAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
            profileVersion = PROFILE_VERSION,
            isSigningPage = { it == SIGNING_PAGE_URL },
        )
    }
}
