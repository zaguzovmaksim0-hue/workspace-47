package dev.junta.firmamobile.afirma.servlet

import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.LocalSignatureError
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Calendar
import java.util.TimeZone
import org.bouncycastle.asn1.ASN1InputStream

/** Invisible PDF signatures through the existing Android PDFBox and CMS
 * implementations. No profile, endpoint, key export, timestamp or network API.
 * The byte-preserving incremental output is independently re-opened and checked
 * before it is eligible for the existing explicit one-shot upload. */
internal class NativePadesEngine(
    private val clock: Clock = Clock.systemUTC(),
    private val maxInputBytes: Int = 524_288,
    private val maxOutputBytes: Int = 2_097_152,
    private val cms: NativeCadesEngine = NativeCadesEngine(
        maxInputBytes = maxOutputBytes, maxOutputBytes = SIGNATURE_SPACE, clock = clock,
        includeSigningTime = false,
    ),
) {
    init { require(maxInputBytes in 1..524_288 && maxOutputBytes in 1..2_097_152) }

    fun sign(pdf: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm,
        options: NativePadesOptions = NativePadesOptions(), requireExistingSignature: Boolean = false): LocalSignatureResult {
        if (pdf.size > maxInputBytes) return failure(LocalSignatureError.INPUT_TOO_LARGE)
        if (!pdfHeader(pdf)) return failure()
        val original = pdf.copyOf()
        val output = ClearingOutput(maxOutputBytes)
        var range: ByteArray? = null
        var encoded: ByteArray? = null
        return try {
            check(identity.chain.size <= 10)
            val usage = identity.certificate.keyUsage
            check(identity.certificate.publicKey.algorithm.equals("RSA", true) &&
                (usage == null || usage.getOrElse(0) { false } || usage.getOrElse(1) { false }))
            load(original).use { document ->
                // Verify previous approval signatures before accessing the new
                // private key. Certified/locked documents and blank signature
                // fields remain unsupported rather than silently altered.
                val previous = checkNotNull(NativePdfSignatureHistory.inspect(original, document))
                check(previous.size < NativePdfSignatureHistory.MAX_SIGNATURES)
                check(!requireExistingSignature || previous.isNotEmpty())
                val signature = PDSignature().apply {
                    setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                    setSubFilter(COSName.getPDFName(options.subFilter))
                    signDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = clock.millis() }
                    options.reason?.let { reason = it }
                    options.location?.let { location = it }
                    options.contact?.let { contactInfo = it }
                }
                SignatureOptions().use { size ->
                    size.setPreferredSignatureSize(SIGNATURE_SPACE)
                    document.addSignature(signature, size)
                    val external = document.saveIncrementalForExternalSigning(output)
                    // The document cannot be mutated after this call.
                    range = external.content.use { readBounded(it, maxOutputBytes) }
                    when (val signed = cms.sign(checkNotNull(range), identity, algorithm, true)) {
                        is LocalSignatureResult.Failure -> return signed
                        is LocalSignatureResult.Success -> signed.signature.use { result ->
                            result.withBytes { bytes ->
                                check(bytes.size <= SIGNATURE_SPACE)
                                external.setSignature(bytes)
                            }
                        }
                    }
                }
            }
            encoded = output.toByteArray()
            if (!verify(checkNotNull(encoded), original, identity.certificate, algorithm, options)) return failure()
            val owned = checkNotNull(encoded)
            encoded = null
            LocalSignatureResult.Success(LocalSignature(owned))
        } catch (_: LimitExceeded) {
            failure(LocalSignatureError.OUTPUT_TOO_LARGE)
        } catch (_: Exception) {
            failure()
        } finally {
            original.fill(0); range?.fill(0); encoded?.fill(0); output.erase()
        }
    }

    /** Content, certificate, algorithm, dictionary and exact byte-range bound.
     * A valid signature over some unrelated bytes is not accepted as this PDF. */
    fun verify(signedPdf: ByteArray, original: ByteArray, certificate: X509Certificate,
        algorithm: SigningAlgorithm, options: NativePadesOptions = NativePadesOptions()): Boolean = runCatching {
        if (original.size !in 5..maxInputBytes || signedPdf.size !in 5..maxOutputBytes) return false
        if (!pdfHeader(original) || !pdfHeader(signedPdf)) return false
        val previous = load(original).use { NativePdfSignatureHistory.inspect(original, it) } ?: return false
        load(signedPdf).use { document ->
            val all = NativePdfSignatureHistory.inspect(signedPdf, document) ?: return false
            if (all.size != previous.size + 1 || all.dropLast(1) != previous) return false
            val signature = document.signatureDictionaries.singleOrNull {
                val range = it.byteRange
                range?.size == 4 && range[2].toLong() + range[3] == signedPdf.size.toLong()
            } ?: return false
            if (signature.filter != PDSignature.FILTER_ADOBE_PPKLITE.name || signature.subFilter != options.subFilter ||
                signature.signDate == null || signature.reason != options.reason ||
                signature.location != options.location || signature.contactInfo != options.contact
            ) return false
            val array = signature.cosObject.getDictionaryObject(COSName.BYTERANGE) as? com.tom_roush.pdfbox.cos.COSArray ?: return false
            if (array.size() != 4) return false
            val range = IntArray(4)
            for (index in 0..3) {
                val value = (array.getObject(index) as? COSInteger)?.longValue() ?: return false
                if (value !in 0..Int.MAX_VALUE.toLong()) return false
                range[index] = value.toInt()
            }
            val content = NativePdfByteRange.extract(signedPdf, range, original) ?: return false
            var rawCms: ByteArray? = null
            var cmsBytes: ByteArray? = null
            try {
                // Bind the excluded hex gap to this exact dictionary's Contents.
                val fromGap = signature.getContents(signedPdf)
                try { if (!MessageDigest.isEqual(fromGap, signature.contents)) return false }
                finally { fromGap.fill(0) }
                rawCms = signature.contents
                val input = ByteArrayInputStream(rawCms)
                ASN1InputStream(input).use { parser -> if (parser.readObject() == null) return false }
                val consumed = rawCms.size - input.available()
                if (consumed <= 0 || consumed > SIGNATURE_SPACE || rawCms.drop(consumed).any { it != 0.toByte() }) return false
                cmsBytes = rawCms.copyOf(consumed)
                cms.verify(cmsBytes, content, certificate, algorithm, true)
            } finally { content.fill(0); rawCms?.fill(0); cmsBytes?.fill(0) }
        }
    }.getOrDefault(false)

    private fun load(bytes: ByteArray): PDDocument = PDDocument.load(
        ByteArrayInputStream(bytes), MemoryUsageSetting.setupMainMemoryOnly(16L * 1024 * 1024))
    private fun pdfHeader(bytes: ByteArray) = bytes.size >= 5 &&
        bytes[0] == 37.toByte() && bytes[1] == 80.toByte() && bytes[2] == 68.toByte() && bytes[3] == 70.toByte() && bytes[4] == 45.toByte()
    private fun failure(code: LocalSignatureError = LocalSignatureError.SIGNATURE_FAILED) = LocalSignatureResult.Failure(code)
    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val out = ClearingOutput(limit); val buffer = ByteArray(16_384)
        return try {
            while (true) { val count = input.read(buffer); if (count < 0) break; if (count > 0) out.write(buffer, 0, count) }
            out.toByteArray()
        } finally { buffer.fill(0); out.erase() }
    }
    private class LimitExceeded : IOException("PDF output exceeds supported budget")
    private class ClearingOutput(private val limit: Int) : ByteArrayOutputStream() {
        override fun write(value: Int) { if (count >= limit) throw LimitExceeded(); super.write(value) }
        override fun write(bytes: ByteArray, off: Int, len: Int) {
            if (len < 0 || count.toLong() + len > limit) throw LimitExceeded()
            super.write(bytes, off, len)
        }
        fun erase() { buf.fill(0); reset() }
    }
    companion object { private const val SIGNATURE_SPACE = 65_536 }
}
