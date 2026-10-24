package dev.junta.firmamobile.verification

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dev.junta.firmamobile.afirma.servlet.NativePdfRevisionRange
import dev.junta.firmamobile.afirma.servlet.NativePdfSignatureHistory
import dev.junta.firmamobile.signing.BundledBouncyCastle
import java.io.ByteArrayInputStream
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder

private const val MAX_DISPLAY_CHARACTERS = 256
private const val MAX_PDF_MEMORY_BYTES = 16_777_216L
private const val PDF_HEADER = "%PDF-"

internal data class OfflineDocumentSignature(
    val signer: String,
    val claimedTime: String?,
    val verified: Boolean,
    val laterChanges: Boolean = false,
)

internal data class OfflinePadesResult(
    val status: OfflineCmsStatus,
    val verifiedSigners: Int = 0,
    val document: OfflineDocumentVerification? = null,
)

internal data class OfflineDocumentVerification(
    val signatures: List<OfflineDocumentSignature>,
    val unsignedChanges: Boolean = false,
)

/** Metadata is transient UI data only; never pass it to the session journal. */
internal fun offlineDisplayText(value: String): String = value.take(MAX_DISPLAY_CHARACTERS).filterNot {
    it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069'
}

internal object OfflinePadesVerifier {
    const val MAX_BYTES = 2_097_152

    fun isPdf(bytes: ByteArray): Boolean = bytes.size >= PDF_HEADER.length &&
        PDF_HEADER.indices.all { bytes[it] == PDF_HEADER[it].code.toByte() }

    fun verify(pdf: ByteArray): OfflinePadesResult {
        if (pdf.size > MAX_BYTES) return OfflinePadesResult(OfflineCmsStatus.LIMIT_EXCEEDED)
        val snapshot = pdf.copyOf()
        return try {
            val memory = MemoryUsageSetting.setupMainMemoryOnly(MAX_PDF_MEMORY_BYTES)
            PDDocument.load(ByteArrayInputStream(snapshot), memory).use { doc ->
                val history = checkNotNull(NativePdfSignatureHistory.inspectForVerification(snapshot, doc))
                val signatures = history.map { entry ->
                    val signature = doc.signatureDictionaries.single { it.byteRange.toList() == entry.range }
                    val content = checkNotNull(NativePdfRevisionRange.extract(snapshot, entry.range.toIntArray()))
                    val contents = signature.contents.copyOf()
                    try {
                        val der = ASN1InputStream(contents).use { it.readObject().encoded }
                        try {
                            val cms = CMSSignedData(CMSProcessableByteArray(content), der)
                            val signer = cms.signerInfos.signers.single()
                            val certificate = cms.certificates.getMatches(null).filter(signer.sid::match).single()
                            val verified = verifySigner(signer, certificate)
                            OfflineDocumentSignature(
                                offlineDisplayText(certificate.subject.toString()),
                                entry.claimedTime?.let(::offlineDisplayText), verified,
                                entry.revisionEnd < snapshot.size,
                            )
                        } finally { der.fill(0) }
                    } finally { content.fill(0); contents.fill(0) }
                }
                val changed = history.lastOrNull()?.revisionEnd?.let { it < snapshot.size } ?: false
                val verified = signatures.isNotEmpty() && signatures.all { it.verified } && !changed
                OfflinePadesResult(
                    if (verified) OfflineCmsStatus.VERIFIED else OfflineCmsStatus.INVALID,
                    signatures.count { it.verified }, document = OfflineDocumentVerification(signatures, changed),
                )
            }
        } catch (_: Exception) {
            OfflinePadesResult(OfflineCmsStatus.INVALID)
        } finally { snapshot.fill(0) }
    }

    private fun verifySigner(signer: SignerInformation, certificate: X509CertificateHolder): Boolean = runCatching {
        val bindings = signer.signedAttributes?.getAll(PKCSObjectIdentifiers.id_aa_signingCertificateV2)
        require(bindings == null || bindings.size() <= 1)
        if (bindings != null && bindings.size() == 1) {
            val attribute = org.bouncycastle.asn1.cms.Attribute.getInstance(bindings.get(0))
            require(attribute.attrValues.size() == 1)
            OfflineCmsSigningCertificatePolicy.validate(
                attribute.attrValues.getObjectAt(0), certificate, setOf(signer.digestAlgOID),
            )
        }
        val publicKey = JcaX509CertificateConverter().setProvider(BundledBouncyCastle.provider)
            .getCertificate(certificate).publicKey
        signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(BundledBouncyCastle.provider).build(publicKey))
    }.getOrDefault(false)
}
