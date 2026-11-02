package dev.junta.firmamobile.afirma.servlet

import java.util.Locale

/**
 * AutoFirma `expPolicy` expansion, mirroring `ExtraParamsProcessor.expandPolicyKeys`
 * and its bundled `policy.properties`: a recognised policy name becomes the same
 * explicit policy properties AutoFirma signs with. The values are public constants;
 * no policy document is fetched or interpreted. As in AutoFirma, expanded values
 * replace request-supplied explicit policy keys, and unknown names or unsupported
 * name/format pairs are rejected instead of being silently dropped.
 */
internal object NativeSignaturePolicyExpansion {
    const val PROPERTY = "expPolicy"
    const val AGE = "FirmaAGE"
    const val AGE_1_8 = "FirmaAGE18"

    /** Without an explicit `mode`, AutoFirma signs CAdES implicitly below 1 MiB. */
    const val CADES_IMPLICIT_LIMIT_BYTES = 1_048_576

    private const val HASH_ALGORITHM = "policyIdentifierHashAlgorithm"
    private const val SHA1_URI = "http://www.w3.org/2000/09/xmldsig#sha1"

    enum class Format { CADES, PADES }

    private class Definition(val identifier: String, val qualifier: String, val hash: String)

    // FirmaAGE is AGE policy 1.9 for CAdES, XAdES and PAdES; policy 1.8 never covered PAdES.
    private val AGE_1_9 = Definition(
        "urn:oid:2.16.724.1.3.1.1.2.1.9",
        "https://sede.administracion.gob.es/politica_de_firma_anexo_1.pdf",
        "G7roucf600+f03r/o0bAOQ6WAs0=",
    )
    private val AGE_1_8_CADES = Definition(
        "urn:oid:2.16.724.1.3.1.1.2.1.8",
        "https://sede.administracion.gob.es/PAG_Sede/dam/jcr:b0de3f91-5171-48e2-81f3-5c2407d9c091/" +
            "politica_firma_AGE_v1_8.pdf",
        "7SxX3erFuH31TvAw9LZ70N7p1vA=",
    )

    /** AutoFirma spells policy digests as XMLDSig URIs; [NativeCadesPolicy] takes canonical names. */
    private val DIGEST_URIS = mapOf(
        SHA1_URI to "SHA-1",
        "http://www.w3.org/2001/04/xmlenc#sha256" to "SHA-256",
        "http://www.w3.org/2001/04/xmldsig-more#sha384" to "SHA-384",
        "http://www.w3.org/2001/04/xmlenc#sha512" to "SHA-512",
    )

    /** True when a policy name is present and CAdES packaging is left to that policy. */
    fun leavesCadesModeToPolicy(properties: Map<String, String>): Boolean =
        PROPERTY in properties && "mode" !in properties

    /** [properties] itself without `expPolicy`; otherwise the expanded copy, or null
     * where AutoFirma refuses the name/format combination. */
    fun expand(properties: Map<String, String>, format: Format): Map<String, String>? {
        val name = properties[PROPERTY] ?: return properties
        return definition(name, format)?.let { policy ->
            LinkedHashMap(properties).apply {
                remove(PROPERTY)
                put("policyIdentifier", policy.identifier)
                put("policyQualifier", policy.qualifier)
                put(HASH_ALGORITHM, SHA1_URI)
                put("policyIdentifierHash", policy.hash)
            }
        }
    }

    /** Maps a known XMLDSig digest URI to its canonical name; other values stay unchanged. */
    fun normalizeDigest(properties: Map<String, String>): Map<String, String> {
        val canonical = properties[HASH_ALGORITHM]?.let { DIGEST_URIS[it.lowercase(Locale.ROOT)] }
        return if (canonical == null) properties else properties + (HASH_ALGORITHM to canonical)
    }

    /** Expanded and digest-normalized policy properties for a local signer. */
    fun policyProperties(properties: Map<String, String>, format: Format): Map<String, String>? =
        expand(properties, format)?.let(::normalizeDigest)

    private fun definition(name: String, format: Format): Definition? = when {
        name == AGE -> AGE_1_9
        name == AGE_1_8 && format == Format.CADES -> AGE_1_8_CADES
        else -> null
    }
}
