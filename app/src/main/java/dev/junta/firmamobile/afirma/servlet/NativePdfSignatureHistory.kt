package dev.junta.firmamobile.afirma.servlet

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Public verification facts only. Validity is cryptographic, not an attestation
 * of the prior signer's identity, legal status, timestamp or certificate trust. */
internal data class NativePdfPriorSignature(
    val field: String, val range: List<Int>, val contentsSha256: String,
    val certificateSha256: String, val filter: String?, val subFilter: String?,
    val name: String?, val reason: String?, val location: String?, val contact: String?, val claimedTime: String?,
) {
    val revisionEnd: Int get() = range[2] + range[3]
}

/** Rejects unsupported document permissions, missing/hidden signature entries,
 * invalid old signatures and unsigned trailing revisions. Each old dictionary
 * is rebound to its own signed revision, not trusted solely from the newest xref. */
internal object NativePdfSignatureHistory {
    const val MAX_SIGNATURES = 8
    private val provider = BouncyCastleProvider()

    fun inspect(pdf: ByteArray, document: PDDocument): List<NativePdfPriorSignature>? = runCatching {
        val current = read(pdf, document, verifyCryptography = true) ?: return null
        for ((index, entry) in current.withIndex()) {
            if (entry.revisionEnd == pdf.size) continue
            val revision = pdf.copyOf(entry.revisionEnd)
            try {
                PDDocument.load(ByteArrayInputStream(revision), memory()).use { previous ->
                    val historical = read(revision, previous, verifyCryptography = false) ?: return null
                    if (historical != current.take(index + 1)) return null
                }
            } finally { revision.fill(0) }
        }
        current
    }.getOrNull()

    private fun read(pdf: ByteArray, document: PDDocument, verifyCryptography: Boolean): List<NativePdfPriorSignature>? {
        if (pdf.size !in 5..2_097_152 || document.isEncrypted || document.numberOfPages !in 1..2_000 ||
            document.document.objects.size > 20_000) return null
        val catalog = document.documentCatalog.cosObject
        if (catalog.containsKey(COSName.PERMS)) return null
        val form = catalog.getDictionaryObject(COSName.ACRO_FORM) as? COSDictionary
        if (form?.containsKey(COSName.XFA) == true) return null
        val signatures = document.signatureDictionaries
        val fields = document.signatureFields
        if (signatures.size > MAX_SIGNATURES || fields.size != signatures.size) return null
        val observed = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
        val entries = ArrayList<NativePdfPriorSignature>()
        val fieldNames = HashSet<String>()
        for (field in fields) {
            val signature = field.signature ?: return null // Do not choose or fill an existing blank field.
            if (!observed.add(signature.cosObject) || field.cosObject.containsKey(COSName.getPDFName("Lock")) ||
                signature.cosObject.containsKey(COSName.getPDFName("Reference"))) return null
            val fieldName = field.fullyQualifiedName ?: return null
            if (!fieldNames.add(fieldName) || fieldName.length > 1024) return null
            if (signature.cosObject.getNameAsString(COSName.TYPE) != "Sig" ||
                signature.filter != PDSignature.FILTER_ADOBE_PPKLITE.name ||
                signature.subFilter !in setOf("ETSI.CAdES.detached", "adbe.pkcs7.detached")) return null
            val range = range(signature) ?: return null
            val end = NativePdfRevisionRange.revisionEnd(pdf, range) ?: return null
            val content = NativePdfRevisionRange.extract(pdf, range) ?: return null
            var contents: ByteArray? = null
            var encoded: ByteArray? = null
            try {
                // PDFBox exposes the dictionary's backing bytes here. Only an
                // owned copy may be erased after cryptographic inspection.
                contents = signature.contents?.copyOf() ?: return null
                if (contents.size !in 1..65_536) return null
                val actualGap = signature.getContents(pdf)
                try { if (!MessageDigest.isEqual(actualGap, contents)) return null }
                finally { actualGap.fill(0) }
                val stream = ByteArrayInputStream(contents)
                ASN1InputStream(stream).use { if (it.readObject() == null) return null }
                val length = contents.size - stream.available()
                if (length !in 1..65_536) return null
                for (i in length until contents.size) if (contents[i] != 0.toByte()) return null
                encoded = contents.copyOf(length)
                val cms = CMSSignedData(encoded)
                if (cms.signedContent != null || cms.certificates.getMatches(null).size !in 1..10) return null
                val signed = CMSSignedData(CMSProcessableByteArray(content), encoded)
                val signer = signed.signerInfos.signers.singleOrNull() ?: return null
                if (NativePdfSignerAlgorithms.fromOids(signer.digestAlgOID, signer.encryptionAlgOID) == null) return null
                val certificate = signed.certificates.getMatches(null).filter(signer.sid::match).singleOrNull() ?: return null
                if (verifyCryptography && !signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(certificate))) return null
                entries.add(NativePdfPriorSignature(
                    fieldName, range.toList(), hash(encoded), hash(certificate.encoded), signature.filter, signature.subFilter,
                    signature.name, signature.reason, signature.location, signature.contactInfo,
                    signature.cosObject.getString(COSName.M),
                ))
                if (end != entries.last().revisionEnd) return null
            } finally { content.fill(0); contents?.fill(0); encoded?.fill(0) }
        }
        if (signatures.any { it.cosObject !in observed }) return null
        // A second, non-field signature dictionary must not remain unexamined.
        for (obj in document.document.objects) {
            val dict = obj.`object` as? COSDictionary ?: continue
            if ((dict.getNameAsString(COSName.TYPE) in setOf("Sig", "DocTimeStamp") || dict.containsKey(COSName.BYTERANGE)) &&
                dict !in observed) return null
        }
        entries.sortBy { it.revisionEnd }
        var previousEnd = 5
        for (entry in entries) {
            if (entry.range[1] < previousEnd || entry.revisionEnd <= previousEnd) return null
            previousEnd = entry.revisionEnd
        }
        if (entries.isNotEmpty() && entries.last().revisionEnd != pdf.size) return null
        return entries
    }

    private fun range(signature: PDSignature): IntArray? {
        val array = signature.cosObject.getDictionaryObject(COSName.BYTERANGE) as? COSArray ?: return null
        if (array.size() != 4) return null
        return IntArray(4) { index ->
            val value = (array.getObject(index) as? COSInteger)?.longValue() ?: return null
            if (value !in 0..Int.MAX_VALUE.toLong()) return null
            value.toInt()
        }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun memory() = MemoryUsageSetting.setupMainMemoryOnly(16L * 1024 * 1024)
}
