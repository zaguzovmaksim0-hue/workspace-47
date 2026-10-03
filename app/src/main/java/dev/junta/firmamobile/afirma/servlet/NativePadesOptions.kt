package dev.junta.firmamobile.afirma.servlet

/** A implemented PDF property is either honored or rejected, never silently
 * treated as a different signing policy or a visible signature instruction. */
internal data class NativePadesOptions(
    val subFilter: String = "ETSI.CAdES.detached",
    val reason: String? = null,
    val location: String? = null,
    val contact: String? = null,
) {
    override fun toString() = "NativePadesOptions(subFilter=$subFilter, metadataPresent=${reason != null || location != null || contact != null})"
    companion object {
        private val ALLOWED = setOf("mode", "format", "signatureSubFilter", "signReason", "signatureProductionCity", "signerContact", "headless", "signingCertificateV2")
        fun acceptsFormat(format: String): Boolean = listOf("PAdES", "PAdES Detached", "Adobe PDF").any { it.equals(format, true) }
        fun parse(properties: Map<String, String>): NativePadesOptions? {
            if (properties.size > 8 || properties.keys.any { it !in ALLOWED }) return null
            if (properties["format"]?.let { !acceptsFormat(it) } == true) return null
            if (properties["mode"]?.let { !it.equals("implicit", true) && !it.equals("explicit", true) } == true) return null
            if (properties["headless"]?.let { !it.equals("true", true) && !it.equals("false", true) } == true) return null
            if (properties["signingCertificateV2"]?.let { !it.equals("true", true) } == true) return null
            val subFilter = properties["signatureSubFilter"] ?: "ETSI.CAdES.detached"
            if (subFilter !in setOf("ETSI.CAdES.detached", "adbe.pkcs7.detached")) return null
            for (key in listOf("signReason", "signatureProductionCity", "signerContact")) {
                val value = properties[key] ?: continue
                if (value.length > 256 || value.any { Character.isISOControl(it) || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }) return null
            }
            return NativePadesOptions(subFilter, properties["signReason"], properties["signatureProductionCity"], properties["signerContact"])
        }
    }
}
