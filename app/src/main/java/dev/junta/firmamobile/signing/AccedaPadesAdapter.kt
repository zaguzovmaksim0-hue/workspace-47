package dev.junta.firmamobile.signing

import java.io.ByteArrayInputStream
import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Local detached PAdES adapter for ACCEDA's observed doSignSolicitud contract. */
class AccedaPadesAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val provider: Provider = BouncyCastleProvider(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matchesContract() || certificateChain.isEmpty() ||
            certificateChain.size > MAX_CERTIFICATES
        ) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { pdfData, extraProperties ->
                    require(pdfData.isNotEmpty() && pdfData.size <= MAX_PDF_BYTES)
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    PadesDetachedCodec.createPreSign(
                        pdfBytes = pdfData,
                        certificateChain = certificateChain,
                        clock = clock,
                        provider = provider,
                        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
                    )
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedAttributes,
                    state = PadesPreSignState(
                        pdfTemplate = material.pdfTemplate,
                        contentsStartOffset = material.contentsStartOffset,
                        contentsHexLength = material.contentsHexLength,
                        byteRangeData = material.byteRangeData,
                        placeholderCms = material.placeholderCms,
                        signingCertificateFingerprint = material.signingCertificateFingerprint,
                    ),
                ),
            )
        } catch (_: Exception) {
            ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
    }

    override suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult {
        if (!request.matchesContract()) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? PadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    PadesDetachedCodec.complete(
                        pdfTemplate = ownedState.pdfTemplate(),
                        contentsStartOffset = ownedState.contentsStartOffset(),
                        contentsHexLength = ownedState.contentsHexLength(),
                        byteRangeData = ownedState.byteRangeData(),
                        placeholderCms = ownedState.placeholderCms(),
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        provider = provider,
                        signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
                    )
                }
                ProtocolCompletionResult.Success(LocalSignature(result))
            } catch (_: Exception) {
                ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
            }
        }
    }

    private fun NormalizedSignRequest.matchesContract(): Boolean =
        protocolId == ID &&
            context.profileId == PROFILE_ID &&
            context.profileVersion == PROFILE_VERSION &&
            context.origin.serialized == INITIATOR_ORIGIN &&
            format == SigningFormat.PADES &&
            algorithm == SigningAlgorithm.SHA1_WITH_RSA

    companion object {
        val ID = SigningProtocolId("age-acceda-local-pades-v1")
        const val PROFILE_ID = "age-acceda"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.administracionespublicas.gob.es"
        const val START_URL =
            "https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES"
        const val SAFE_DESCRIPTION = "Firma de solicitud en Plataforma ACCEDA"
        internal const val EXPECTED_EXTRA_PROPERTIES =
            "format=PAdES Detached\nexpPolicy=FirmaAGE\nnonexpired:true"
        internal val FIXED_EXTRA_PROPERTIES = linkedMapOf(
            "format" to "PAdES Detached",
            "expPolicy" to "FirmaAGE",
            "nonexpired" to "true",
        )
        internal const val MAX_PDF_BYTES = 524_288
        private const val MAX_CERTIFICATES = 10
    }
}

internal class PadesPreSignState(
    pdfTemplate: ByteArray,
    private val contentsStartOffset: Int,
    private val contentsHexLength: Int,
    byteRangeData: ByteArray,
    placeholderCms: ByteArray,
    signingCertificateFingerprint: ByteArray,
) : PreSignState {
    private var template = pdfTemplate
    private var rangeData = byteRangeData
    private var placeholder = placeholderCms
    private var fingerprint = signingCertificateFingerprint
    private var closed = false

    @Synchronized
    fun pdfTemplate(): ByteArray = check(!closed).let { template }

    @Synchronized
    fun contentsStartOffset(): Int = check(!closed).let { contentsStartOffset }

    @Synchronized
    fun contentsHexLength(): Int = check(!closed).let { contentsHexLength }

    @Synchronized
    fun byteRangeData(): ByteArray = check(!closed).let { rangeData }

    @Synchronized
    fun placeholderCms(): ByteArray = check(!closed).let { placeholder }

    @Synchronized
    fun signingCertificateFingerprint(): ByteArray = check(!closed).let { fingerprint }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        template.fill(0)
        rangeData.fill(0)
        placeholder.fill(0)
        fingerprint.fill(0)
        template = ByteArray(0)
        rangeData = ByteArray(0)
        placeholder = ByteArray(0)
        fingerprint = ByteArray(0)
    }
}

internal data class PadesPreSignMaterial(
    val pdfTemplate: ByteArray,
    val contentsStartOffset: Int,
    val contentsHexLength: Int,
    val byteRangeData: ByteArray,
    val placeholderCms: ByteArray,
    val signedAttributes: ByteArray,
    val signingCertificateFingerprint: ByteArray,
)

internal object PadesDetachedCodec {
    const val CONTENTS_HEX_CHARS = 16384
    private const val MAX_PDF_SIZE = 524_288
    private val PDF_HEADER = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-
    private val PDF_OBJ_REGEX = Regex("""(\d+)\s+0\s+obj""")
    private val PDF_ROOT_REGEX = Regex("""/Root\s+(\d+)\s+0\s+R""")
    private val PDF_PREV_XREF_REGEX = Regex("""startxref\s+(\d+)\s+%%EOF""")
    private val BYTE_RANGE_REGEX = Regex("""/ByteRange\s*\[\s*0\s+(\d+)\s+(\d+)\s+(\d+)\s*\]""")

    fun createPreSign(
        pdfBytes: ByteArray,
        certificateChain: List<X509Certificate>,
        clock: Clock,
        provider: Provider = BouncyCastleProvider(),
        signingAlgorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
    ): PadesPreSignMaterial {
        require(pdfBytes.size in 32..MAX_PDF_SIZE)
        require(pdfBytes.startsWithBytes(PDF_HEADER))
        val pdfText = String(pdfBytes, Charsets.US_ASCII)
        require(pdfText.contains("%%EOF"))

        val maxObjNum = PDF_OBJ_REGEX.findAll(pdfText)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull() ?: 1
        val rootObjNum = PDF_ROOT_REGEX.find(pdfText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val prevXrefOffset = PDF_PREV_XREF_REGEX.findAll(pdfText)
            .lastOrNull()?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val sigObjNum = maxObjNum + 1
        val dateStr = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(clock.instant())

        val sigObjHeaderTemplate = (
            "\n$sigObjNum 0 obj\n" +
                "<<\n" +
                "/Type /Sig\n" +
                "/Filter /Adobe.PPKLite\n" +
                "/SubFilter /ETSI.CAdES.detached\n" +
                "/ByteRange [ 0 %010d %010d %010d ]\n" +
                "/Contents <"
            ).toByteArray(Charsets.US_ASCII)

        val headerLen = sigObjHeaderTemplate.size
        val sigObjFooter = (
            ">\n" +
                "/M (D:$dateStr)\n" +
                "/Reason (Firma electronica ACCEDA)\n" +
                ">>\n" +
                "endobj\n"
            ).toByteArray(Charsets.US_ASCII)

        val sigObjOffset = pdfBytes.size + 1
        val off1 = pdfBytes.size + headerLen - 1
        val off2 = off1 + 1 + CONTENTS_HEX_CHARS
        val xrefOffset = pdfBytes.size + headerLen + CONTENTS_HEX_CHARS + sigObjFooter.size

        val xrefTrailer = (
            "xref\n" +
                "$sigObjNum 1\n" +
                String.format(Locale.US, "%010d 00000 n \n", sigObjOffset) +
                "trailer\n" +
                "<<\n" +
                "/Size ${sigObjNum + 1}\n" +
                "/Root $rootObjNum 0 R\n" +
                "/Prev $prevXrefOffset\n" +
                ">>\n" +
                "startxref\n" +
                "$xrefOffset\n" +
                "%%EOF\n"
            ).toByteArray(Charsets.US_ASCII)

        val totalLen = xrefOffset + xrefTrailer.size
        val len2 = totalLen - (off2 + 1)
        val sigObjHeader = String.format(
            Locale.US,
            String(sigObjHeaderTemplate, Charsets.US_ASCII),
            off1,
            off2 + 1,
            len2,
        ).toByteArray(Charsets.US_ASCII)

        val pdfTemplate = ByteArray(totalLen)
        System.arraycopy(pdfBytes, 0, pdfTemplate, 0, pdfBytes.size)
        System.arraycopy(sigObjHeader, 0, pdfTemplate, pdfBytes.size, sigObjHeader.size)
        pdfTemplate.fill('0'.code.toByte(), off1 + 1, off2)
        System.arraycopy(sigObjFooter, 0, pdfTemplate, off2, sigObjFooter.size)
        System.arraycopy(xrefTrailer, 0, pdfTemplate, xrefOffset, xrefTrailer.size)

        val byteRangeData = ByteArray(off1 + len2)
        System.arraycopy(pdfTemplate, 0, byteRangeData, 0, off1)
        System.arraycopy(pdfTemplate, off2 + 1, byteRangeData, off1, len2)

        val cadesMaterial = CadesDetachedCodec.createPreSign(
            content = byteRangeData,
            expectedContentBytes = byteRangeData.size,
            certificateChain = certificateChain,
            clock = clock,
            provider = provider,
            signingAlgorithm = signingAlgorithm,
        )

        return PadesPreSignMaterial(
            pdfTemplate = pdfTemplate,
            contentsStartOffset = off1 + 1,
            contentsHexLength = CONTENTS_HEX_CHARS,
            byteRangeData = byteRangeData,
            placeholderCms = cadesMaterial.placeholderCms,
            signedAttributes = cadesMaterial.signedAttributes,
            signingCertificateFingerprint = cadesMaterial.signingCertificateFingerprint,
        )
    }

    fun complete(
        pdfTemplate: ByteArray,
        contentsStartOffset: Int,
        contentsHexLength: Int,
        byteRangeData: ByteArray,
        placeholderCms: ByteArray,
        signingCertificateFingerprint: ByteArray,
        signatureValue: ByteArray,
        provider: Provider = BouncyCastleProvider(),
        signingAlgorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
    ): ByteArray {
        val cmsBytes = CadesDetachedCodec.complete(
            placeholderCms = placeholderCms,
            detachedContent = byteRangeData,
            expectedContentBytes = byteRangeData.size,
            signingCertificateFingerprint = signingCertificateFingerprint,
            signatureValue = signatureValue,
            provider = provider,
            signingAlgorithm = signingAlgorithm,
        )
        val hex = cmsBytes.toHexUpper()
        require(hex.length <= contentsHexLength)
        val paddedHex = hex.padEnd(contentsHexLength, '0')
        val hexBytes = paddedHex.toByteArray(Charsets.US_ASCII)

        val completedPdf = pdfTemplate.copyOf()
        System.arraycopy(hexBytes, 0, completedPdf, contentsStartOffset, contentsHexLength)

        require(
            validate(
                signatureDocument = completedPdf,
                expectedCertificateFingerprint = signingCertificateFingerprint,
                provider = provider,
                signingAlgorithm = signingAlgorithm,
            ),
        )
        return completedPdf
    }

    internal fun validate(
        signatureDocument: ByteArray,
        expectedCertificateFingerprint: ByteArray? = null,
        provider: Provider = BouncyCastleProvider(),
        signingAlgorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
    ): Boolean = runCatching {
        require(signatureDocument.size in 32..MAX_PDF_SIZE)
        require(signatureDocument.startsWithBytes(PDF_HEADER))
        val pdfText = String(signatureDocument, Charsets.US_ASCII)
        require(pdfText.contains("%%EOF"))

        val match = BYTE_RANGE_REGEX.find(pdfText) ?: return false
        val off1 = match.groupValues[1].toInt()
        val off2Plus1 = match.groupValues[2].toInt()
        val len2 = match.groupValues[3].toInt()

        require(off1 > 0)
        require(off2Plus1 > off1 + 2)
        require(len2 > 0)
        require(off2Plus1 + len2 == signatureDocument.size)
        require(signatureDocument[off1] == '<'.code.toByte())
        require(signatureDocument[off2Plus1 - 1] == '>'.code.toByte())

        val byteRangeData = ByteArray(off1 + len2)
        System.arraycopy(signatureDocument, 0, byteRangeData, 0, off1)
        System.arraycopy(signatureDocument, off2Plus1, byteRangeData, off1, len2)

        val hexChars = String(
            signatureDocument,
            off1 + 1,
            (off2Plus1 - 1) - (off1 + 1),
            Charsets.US_ASCII,
        )
        val cmsBytes = extractCmsDerFromPaddedHex(hexChars)

        CadesDetachedCodec.validate(
            signatureDocument = cmsBytes,
            detachedContent = byteRangeData,
            expectedContentBytes = byteRangeData.size,
            expectedCertificateFingerprint = expectedCertificateFingerprint,
            provider = provider,
            signingAlgorithm = signingAlgorithm,
        )
    }.getOrDefault(false)

    private fun extractCmsDerFromPaddedHex(hexString: String): ByteArray {
        val rawBytes = hexString.hexToBytes()
        return ASN1InputStream(ByteArrayInputStream(rawBytes)).use { stream ->
            val obj = stream.readObject() ?: error("Empty ASN.1 structure")
            obj.encoded
        }
    }

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.toHexUpper(): String {
        val hexChars = CharArray(size * 2)
        val digits = "0123456789ABCDEF"
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        val len = length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            val high = Character.digit(this[i * 2], 16)
            val low = Character.digit(this[i * 2 + 1], 16)
            require(high >= 0 && low >= 0)
            result[i] = ((high shl 4) or low).toByte()
        }
        return result
    }
}
