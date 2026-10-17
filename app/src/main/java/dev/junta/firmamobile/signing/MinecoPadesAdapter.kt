package dev.junta.firmamobile.signing

import java.security.Provider
import java.time.Clock

/** Exact authenticated MINECO Instancia Genérica MiniApplet PAdES contract. QA-only until physical E2E. */
class MinecoPadesAdapter(
    clock: Clock = Clock.systemUTC(),
    provider: Provider = BundledBouncyCastle.provider,
) : MiniAppletPadesAdapter(CONTRACT, clock, provider) {
    companion object {
        val ID = SigningProtocolId("mineco-instancia-generica-pades-v1")
        const val PROFILE_ID = "ministerio-economia-instancia-generica"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://serviciosede.mineco.gob.es"
        const val PUBLIC_START_URL = "https://serviciosede.mineco.gob.es/FB/Home.aspx?control=161_IG"
        const val SIGNING_PAGE_URL = "https://serviciosede.mineco.gob.es/FB/solicitud/firma.aspx"
        const val SAFE_DESCRIPTION =
            "Firma PAdES de Instancia Genérica del Ministerio de Economía, Comercio y Empresa"
        const val EXPECTED_EXTRA_PROPERTIES =
            "filters=signingCert:;nonexpired:\nexpPolicy=FirmaAGE\nsignatureSubFilter=ETSI.CAdES.detached"
        const val AGE_POLICY_IDENTIFIER_OID = "2.16.724.1.3.1.1.2.1.9"
        const val AGE_POLICY_HASH_ALGORITHM_OID = "1.3.14.3.2.26"
        const val AGE_POLICY_HASH_BASE64 = "G7roucf600+f03r/o0bAOQ6WAs0="
        const val AGE_POLICY_QUALIFIER =
            "https://sede.administracion.gob.es/politica_de_firma_anexo_1.pdf"
        internal val SIGNATURE_POLICY = CadesSignaturePolicy(
            policyIdentifierOid = AGE_POLICY_IDENTIFIER_OID,
            hashAlgorithmOid = AGE_POLICY_HASH_ALGORITHM_OID,
            policyHashBase64 = AGE_POLICY_HASH_BASE64,
            qualifierUrl = AGE_POLICY_QUALIFIER,
        )
        const val MAX_PDF_BYTES = MiniAppletPadesAdapter.MAX_PDF_BYTES
        private val CONTRACT = MiniAppletPadesContract(
            id = ID,
            profileId = PROFILE_ID,
            initiatorOrigin = INITIATOR_ORIGIN,
            safeDescription = SAFE_DESCRIPTION,
            expectedExtraProperties = EXPECTED_EXTRA_PROPERTIES,
            signingAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
            signaturePolicy = SIGNATURE_POLICY,
            profileVersion = PROFILE_VERSION,
            isSigningPage = { it == SIGNING_PAGE_URL },
        )
    }
}
