package dev.junta.firmamobile.profile

/** Closed, Kotlin-owned module IDs; never resource paths or executable catalog data. */
enum class ShimFeature(val moduleId: String) {
    UGR_LOGIN("ugr-login"),
    CANTABRIA_LOGIN("cantabria-login"),
    JCCM_PROBE("jccm-probe"),
    JCCM_REGISTRO("jccm-registro"),
    SEVILLA_ATSE("sevilla-atse"),
    AIREF_SIGN("airef-sign"),
    CDTI_SIGN("cdti-sign"),
    POLICIA_SIGN("policia-sign"),
    GRAN_CANARIA_SIGN("gran-canaria-sign"),
    FUERTEVENTURA_SIGN("fuerteventura-sign"),
    CANARIAS_LOGIN("canarias-login"),
    MINECO_SIGN("mineco-sign"),
    ACCEDA_SIGN("acceda-sign"),
    BADAJOZ_SIGN("badajoz-sign"),
    ISCIII_SELECTION("isciii-selection"),
    VALENCIA_SELECTION("valencia-selection"),
    XUNTA_SIGN_SELECTION("xunta-sign-selection"),
    EUSKADI_CLIENT_AUTH_POST("euskadi-client-auth-post"),
    STA_JSON_BATCH("sta-json-batch"),
    LUGO_XML_BATCH("lugo-xml-batch"),
    CAIB_PORTAFIB_BATCH("caib-portafib-batch");

    companion object {
        fun fromModuleId(value: String): ShimFeature = requireNotNull(entries.singleOrNull { it.moduleId == value }) {
            "Unknown shim feature"
        }
    }
}
