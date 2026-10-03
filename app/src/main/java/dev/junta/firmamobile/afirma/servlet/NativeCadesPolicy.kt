package dev.junta.firmamobile.afirma.servlet

import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.x509.AlgorithmIdentifier

/** Explicit request-supplied CAdES policy reference. No policy document is
 * fetched and no legal/compliance claim is inferred from its identifier/hash.
 * Immutable, bounded public metadata; every returned encoding is a copy. */
internal class NativeCadesPolicy private constructor(
    val identifier: String,
    val hashAlgorithm: String,
    val hashOid: String,
    digest: ByteArray,
    val qualifier: String?,
) {
    private val ownedDigest = digest.copyOf()
    fun hashCopy(): ByteArray = ownedDigest.copyOf()
    fun encoded(): ByteArray {
        val fields = mutableListOf<org.bouncycastle.asn1.ASN1Encodable>(
            ASN1ObjectIdentifier(identifier),
            DERSequence(arrayOf(AlgorithmIdentifier(ASN1ObjectIdentifier(hashOid)), DEROctetString(ownedDigest))),
        )
        qualifier?.let {
            fields.add(DERSequence(DERSequence(arrayOf(ASN1ObjectIdentifier("1.2.840.113549.1.9.16.5.1"), DERIA5String(it)))))
        }
        return DERSequence(fields.toTypedArray()).getEncoded(ASN1Encoding.DER)
    }
    fun attribute(): Attribute = Attribute(ATTRIBUTE_OID,
        DERSet(org.bouncycastle.asn1.ASN1Primitive.fromByteArray(encoded())))
    override fun toString() = "NativeCadesPolicy(explicit=true, hashAlgorithm=$hashAlgorithm, qualifierPresent=${qualifier != null})"

    companion object {
        val ATTRIBUTE_OID = ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.15")
        val PROPERTY_NAMES: Set<String> = setOf("policyIdentifier", "policyIdentifierHash", "policyIdentifierHashAlgorithm", "policyQualifier")
        private val oidSyntax = Regex("[0-2]\\.(?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*))*")

        fun parse(properties: Map<String, String>): NativeCadesPolicy? {
            if (properties.keys.none { it in PROPERTY_NAMES }) return null
            val identifier = requireNotNull(properties["policyIdentifier"]) { "Incomplete policy metadata" }
                .let { if (it.startsWith("urn:oid:", true)) it.substring(8) else it }
            require(identifier.length in 3..256 && oidSyntax.matches(identifier)) { "Unsupported policy identifier" }
            val arcs = identifier.split('.')
            require(arcs.size <= 32 && arcs.all { it.length <= 64 }) { "Policy identifier exceeds bounds" }
            require(arcs[0] == "2" || arcs[1].toIntOrNull()?.let { it in 0..39 } == true) { "Invalid policy identifier" }
            // Constructing a bounded OID is validation, never network access.
            ASN1ObjectIdentifier(identifier)
            val algorithm = requireNotNull(properties["policyIdentifierHashAlgorithm"]) { "Missing policy digest algorithm" }
            val hash = when (algorithm.uppercase(Locale.ROOT)) {
                "SHA1", "SHA-1", "1.3.14.3.2.26" -> Triple("SHA-1", "1.3.14.3.2.26", 20)
                "SHA256", "SHA-256", "2.16.840.1.101.3.4.2.1" -> Triple("SHA-256", "2.16.840.1.101.3.4.2.1", 32)
                "SHA384", "SHA-384", "2.16.840.1.101.3.4.2.2" -> Triple("SHA-384", "2.16.840.1.101.3.4.2.2", 48)
                "SHA512", "SHA-512", "2.16.840.1.101.3.4.2.3" -> Triple("SHA-512", "2.16.840.1.101.3.4.2.3", 64)
                else -> throw IllegalArgumentException("Unsupported policy digest algorithm")
            }
            val encoded = requireNotNull(properties["policyIdentifierHash"]) { "Missing policy digest" }
            val bytes = AfirmaServletInvocationParser.strictBase64(encoded, 64)
            try {
                require(bytes.size == hash.third) { "Wrong policy digest length" }
                val qualifier = properties["policyQualifier"]?.takeIf(String::isNotEmpty)?.let { value ->
                    require(value.isNotEmpty() && value.length <= 2048 && value.all { it.code in 0x21..0x7e }) { "Invalid policy qualifier" }
                    val uri = URI(value)
                    require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && uri.host != null &&
                        uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..65535)) { "Invalid policy qualifier" }
                    // A signed informational URI, never visited by this class.
                    value
                }
                return NativeCadesPolicy(identifier, hash.first, hash.second, bytes, qualifier)
            } finally { bytes.fill(0) }
        }

        /** Creation-time verification is bound to the exact request, not just
         * presence of an attribute or success of the RSA primitive. */
        fun matches(attributes: AttributeTable, expected: NativeCadesPolicy?): Boolean = runCatching {
            val found = attributes.getAll(ATTRIBUTE_OID)
            if (expected == null) return found.size() == 0
            if (found.size() != 1) return false
            val attribute = Attribute.getInstance(found.get(0))
            if (attribute.attrValues.size() != 1) return false
            val actual = attribute.attrValues.getObjectAt(0).toASN1Primitive().getEncoded(ASN1Encoding.DER)
            MessageDigest.isEqual(actual, expected.encoded())
        }.getOrDefault(false)
    }
}
