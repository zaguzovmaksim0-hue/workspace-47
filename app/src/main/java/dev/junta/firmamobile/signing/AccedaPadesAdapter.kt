package dev.junta.firmamobile.signing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
    private val OBJ_DEF_REGEX = Regex("""(?:^|\n)(\d+)\s+(\d+)\s+obj\b""")
    private val ROOT_REF_REGEX = Regex("""/Root\s+(\d+)\s+\d+\s+R""")
    private val PAGES_REF_REGEX = Regex("""/Pages\s+(\d+)\s+\d+\s+R""")
    private val ACRO_FORM_REF_REGEX = Regex("""/AcroForm\s+(\d+)\s+\d+\s+R""")
    private val FIELDS_ARRAY_REGEX = Regex("""/Fields\s*\[\s*([\s\S]*?)\s*\]""")
    private val ANNOTS_ARRAY_REGEX = Regex("""/Annots\s*\[\s*([\s\S]*?)\s*\]""")
    private val STARTXREF_REGEX = Regex("""startxref\s+(\d+)\s+%%EOF""")
    private val BYTE_RANGE_REGEX = Regex("""/ByteRange\s*\[\s*0\s+(\d+)\s+(\d+)\s+(\d+)\s*\]""")
    private val OBJ_REF_REGEX = Regex("""(\d+)\s+\d+\s+R""")
    private val TRAILER_DICT_REGEX = Regex("""trailer\s*<<([\s\S]*?)>>\s*(?:startxref|$)""")
    private val INFO_REF_REGEX = Regex("""/Info\s+(\d+\s+\d+\s+R)""")
    private val ID_ARRAY_REGEX = Regex("""/ID\s*(\[[\s\S]*?\])""")
    private val SIG_FLAGS_REGEX = Regex("""/SigFlags\s+\d+""")

    fun createPreSign(
        pdfBytes: ByteArray,
        certificateChain: List<X509Certificate>,
        clock: Clock,
        provider: Provider = BouncyCastleProvider(),
        signingAlgorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
    ): PadesPreSignMaterial {
        require(pdfBytes.size in 32..MAX_PDF_SIZE)
        require(pdfBytes.startsWithBytes(PDF_HEADER))
        val pdfText = String(pdfBytes, Charsets.ISO_8859_1)
        require(pdfText.contains("%%EOF"))

        val parsedDoc = parsePdfDocument(pdfText, pdfBytes.size)

        val dateStr = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(clock.instant())

        val existingAcroFormNum = parsedDoc.existingAcroFormObjNum
        val acroFormObjNum: Int
        val sigFieldObjNum: Int
        val sigDictObjNum: Int
        val newMaxObjNum: Int

        if (existingAcroFormNum == null) {
            acroFormObjNum = parsedDoc.maxObjNum + 1
            sigFieldObjNum = parsedDoc.maxObjNum + 2
            sigDictObjNum = parsedDoc.maxObjNum + 3
            newMaxObjNum = parsedDoc.maxObjNum + 3
        } else {
            acroFormObjNum = existingAcroFormNum
            sigFieldObjNum = parsedDoc.maxObjNum + 1
            sigDictObjNum = parsedDoc.maxObjNum + 2
            newMaxObjNum = parsedDoc.maxObjNum + 2
        }

        val fieldName = "Signature" + (parsedDoc.existingFieldCount + 1)

        val updatedCatalogObj: ByteArray? = if (existingAcroFormNum == null) {
            val catalogBodyWithoutAcroForm = parsedDoc.catalogBody.replace(ACRO_FORM_REF_REGEX, "").trim()
            (
                "${parsedDoc.rootObjNum} 0 obj\n" +
                    "<<\n" +
                    catalogBodyWithoutAcroForm + "\n" +
                    "/AcroForm $acroFormObjNum 0 R\n" +
                    ">>\n" +
                    "endobj\n"
            ).toByteArray(Charsets.US_ASCII)
        } else {
            null
        }

        val updatedAcroFormObj: ByteArray = if (existingAcroFormNum == null) {
            (
                "$acroFormObjNum 0 obj\n" +
                    "<<\n" +
                    "/Fields [ $sigFieldObjNum 0 R ]\n" +
                    "/SigFlags 3\n" +
                    ">>\n" +
                    "endobj\n"
            ).toByteArray(Charsets.US_ASCII)
        } else {
            val existingBody = parsedDoc.existingAcroFormBody ?: ""
            val bodyWithoutSigFlags = existingBody.replace(SIG_FLAGS_REGEX, "").trim()
            val bodyWithField = if (FIELDS_ARRAY_REGEX.containsMatchIn(bodyWithoutSigFlags)) {
                FIELDS_ARRAY_REGEX.replace(bodyWithoutSigFlags) { match ->
                    val inner = match.groupValues[1].trim()
                    if (inner.isEmpty()) "/Fields [ $sigFieldObjNum 0 R ]" else "/Fields [ $inner $sigFieldObjNum 0 R ]"
                }
            } else {
                "$bodyWithoutSigFlags\n/Fields [ $sigFieldObjNum 0 R ]"
            }
            (
                "$acroFormObjNum 0 obj\n" +
                    "<<\n" +
                    bodyWithField + "\n" +
                    "/SigFlags 3\n" +
                    ">>\n" +
                    "endobj\n"
            ).toByteArray(Charsets.US_ASCII)
        }

        val updatedPageObj: ByteArray = run {
            val pageBody = parsedDoc.firstPageBody
            val bodyWithAnnot = if (ANNOTS_ARRAY_REGEX.containsMatchIn(pageBody)) {
                ANNOTS_ARRAY_REGEX.replace(pageBody) { match ->
                    val inner = match.groupValues[1].trim()
                    if (inner.isEmpty()) "/Annots [ $sigFieldObjNum 0 R ]" else "/Annots [ $inner $sigFieldObjNum 0 R ]"
                }
            } else {
                "$pageBody\n/Annots [ $sigFieldObjNum 0 R ]"
            }
            (
                "${parsedDoc.firstPageObjNum} 0 obj\n" +
                    "<<\n" +
                    bodyWithAnnot + "\n" +
                    ">>\n" +
                    "endobj\n"
            ).toByteArray(Charsets.US_ASCII)
        }

        val sigFieldObj = (
            "$sigFieldObjNum 0 obj\n" +
                "<<\n" +
                "/FT /Sig\n" +
                "/Type /Annot\n" +
                "/Subtype /Widget\n" +
                "/Rect [ 0 0 0 0 ]\n" +
                "/F 132\n" +
                "/T ($fieldName)\n" +
                "/V $sigDictObjNum 0 R\n" +
                "/P ${parsedDoc.firstPageObjNum} 0 R\n" +
                ">>\n" +
                "endobj\n"
        ).toByteArray(Charsets.US_ASCII)

        val dummySigHeader = (
            "$sigDictObjNum 0 obj\n" +
                "<<\n" +
                "/Type /Sig\n" +
                "/Filter /Adobe.PPKLite\n" +
                "/SubFilter /ETSI.CAdES.detached\n" +
                "/ByteRange [ 0 0000000000 0000000000 0000000000 ]\n" +
                "/Contents <"
        ).toByteArray(Charsets.US_ASCII)
        val sigHeaderLen = dummySigHeader.size

        val sigFooter = (
            ">\n" +
                "/M (D:$dateStr)\n" +
                "/Reason (Firma electronica ACCEDA)\n" +
                ">>\n" +
                "endobj\n"
        ).toByteArray(Charsets.US_ASCII)

        val updatedOffsets = sortedMapOf<Int, Int>()
        var currOffset = pdfBytes.size

        updatedCatalogObj?.let {
            updatedOffsets[parsedDoc.rootObjNum] = currOffset
            currOffset += it.size
        }

        updatedOffsets[parsedDoc.firstPageObjNum] = currOffset
        currOffset += updatedPageObj.size

        updatedOffsets[acroFormObjNum] = currOffset
        currOffset += updatedAcroFormObj.size

        updatedOffsets[sigFieldObjNum] = currOffset
        currOffset += sigFieldObj.size

        val sigDictOffset = currOffset
        updatedOffsets[sigDictObjNum] = sigDictOffset
        val off1 = currOffset + sigHeaderLen - 1
        val off2 = off1 + 1 + CONTENTS_HEX_CHARS + 1
        currOffset += sigHeaderLen + CONTENTS_HEX_CHARS + sigFooter.size

        val xrefOffset = currOffset
        val xrefBuilder = StringBuilder("xref\n")
        for ((objNum, off) in updatedOffsets) {
            xrefBuilder.append("$objNum 1\n")
            xrefBuilder.append(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        val xrefBytes = xrefBuilder.toString().toByteArray(Charsets.US_ASCII)
        currOffset += xrefBytes.size

        val infoSnippet = parsedDoc.infoSnippet?.let { "\n$it" } ?: ""
        val idSnippet = parsedDoc.idSnippet?.let { "\n$it" } ?: ""
        val trailerBytes = (
            "trailer\n" +
                "<<\n" +
                "/Size ${newMaxObjNum + 1}\n" +
                "/Root ${parsedDoc.rootObjNum} 0 R\n" +
                "/Prev ${parsedDoc.lastXrefOffset}" +
                infoSnippet +
                idSnippet + "\n" +
                ">>\n" +
                "startxref\n" +
                "$xrefOffset\n" +
                "%%EOF\n"
        ).toByteArray(Charsets.US_ASCII)
        currOffset += trailerBytes.size

        val totalLen = currOffset
        val len2 = totalLen - off2

        val sigHeader = (
            "$sigDictObjNum 0 obj\n" +
                "<<\n" +
                "/Type /Sig\n" +
                "/Filter /Adobe.PPKLite\n" +
                "/SubFilter /ETSI.CAdES.detached\n" +
                "/ByteRange [ 0 " +
                String.format(Locale.US, "%010d %010d %010d", off1, off2, len2) +
                " ]\n" +
                "/Contents <"
        ).toByteArray(Charsets.US_ASCII)
        require(sigHeader.size == sigHeaderLen)

        val out = ByteArrayOutputStream(totalLen)
        out.write(pdfBytes)
        updatedCatalogObj?.let { out.write(it) }
        out.write(updatedPageObj)
        out.write(updatedAcroFormObj)
        out.write(sigFieldObj)
        out.write(sigHeader)
        val placeholderBytes = ByteArray(CONTENTS_HEX_CHARS) { '0'.code.toByte() }
        out.write(placeholderBytes)
        out.write(sigFooter)
        out.write(xrefBytes)
        out.write(trailerBytes)

        val pdfTemplate = out.toByteArray()
        require(pdfTemplate.size == totalLen)
        require(pdfTemplate[off1] == '<'.code.toByte())
        require(pdfTemplate[off2 - 1] == '>'.code.toByte())

        val byteRangeData = ByteArray(off1 + len2)
        System.arraycopy(pdfTemplate, 0, byteRangeData, 0, off1)
        System.arraycopy(pdfTemplate, off2, byteRangeData, off1, len2)

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
        val text = String(signatureDocument, Charsets.ISO_8859_1)
        require(text.contains("%%EOF"))

        val parsed = parsePdfDocument(text, signatureDocument.size)

        val catalogBody = parsed.catalogBody
        val acroFormChunk: String = if (parsed.existingAcroFormObjNum != null) {
            parsed.existingAcroFormBody ?: return false
        } else {
            val inlineMatch = Regex("""/AcroForm\s*<<([\s\S]*?)>>""").find(catalogBody)
                ?: return false
            inlineMatch.groupValues[1]
        }

        val fieldsMatch = FIELDS_ARRAY_REGEX.find(acroFormChunk) ?: return false
        val fieldRefs = OBJ_REF_REGEX.findAll(fieldsMatch.groupValues[1])
            .map { it.groupValues[1].toInt() }
            .toList()
        require(fieldRefs.isNotEmpty())

        val pageBody = parsed.firstPageBody
        val annotsMatch = ANNOTS_ARRAY_REGEX.find(pageBody) ?: return false
        val pageAnnotRefs = OBJ_REF_REGEX.findAll(annotsMatch.groupValues[1])
            .map { it.groupValues[1].toInt() }
            .toSet()

        var signatureValidated = false
        for (fieldNum in fieldRefs) {
            val fieldBody = parsed.objectBodies[fieldNum] ?: continue
            if (!fieldBody.contains("/FT /Sig") && !fieldBody.contains("/FT/Sig")) continue
            if (!fieldBody.contains("/Widget")) continue
            if (!pageAnnotRefs.contains(fieldNum)) return false

            val vMatch = Regex("""/V\s+(\d+)\s+\d+\s+R""").find(fieldBody) ?: continue
            val sigDictNum = vMatch.groupValues[1].toInt()
            val sigDictBody = parsed.objectBodies[sigDictNum] ?: continue
            if (!sigDictBody.contains("/Type /Sig") && !sigDictBody.contains("/Type/Sig")) continue

            val brMatch = BYTE_RANGE_REGEX.find(sigDictBody) ?: continue
            val off1 = brMatch.groupValues[1].toInt()
            val off2 = brMatch.groupValues[2].toInt()
            val len2 = brMatch.groupValues[3].toInt()

            require(off1 > 0)
            require(off2 > off1 + 2)
            require(len2 > 0)
            require(off2 + len2 == signatureDocument.size)
            require(signatureDocument[off1] == '<'.code.toByte())
            require(signatureDocument[off2 - 1] == '>'.code.toByte())

            val hexChars = String(
                signatureDocument,
                off1 + 1,
                (off2 - 1) - (off1 + 1),
                Charsets.US_ASCII,
            )
            val cmsBytes = extractCmsDerFromPaddedHex(hexChars)

            val byteRangeData = ByteArray(off1 + len2)
            System.arraycopy(signatureDocument, 0, byteRangeData, 0, off1)
            System.arraycopy(signatureDocument, off2, byteRangeData, off1, len2)

            val valid = CadesDetachedCodec.validate(
                signatureDocument = cmsBytes,
                detachedContent = byteRangeData,
                expectedContentBytes = byteRangeData.size,
                expectedCertificateFingerprint = expectedCertificateFingerprint,
                provider = provider,
                signingAlgorithm = signingAlgorithm,
            )
            if (valid) {
                signatureValidated = true
                break
            }
        }
        signatureValidated
    }.getOrDefault(false)

    private fun extractCmsDerFromPaddedHex(hexString: String): ByteArray {
        val rawBytes = hexString.hexToBytes()
        return ASN1InputStream(ByteArrayInputStream(rawBytes)).use { stream ->
            val obj = stream.readObject() ?: error("Empty ASN.1 structure")
            obj.encoded
        }
    }

    private data class ParsedPdf(
        val maxObjNum: Int,
        val lastXrefOffset: Long,
        val rootObjNum: Int,
        val pagesObjNum: Int,
        val firstPageObjNum: Int,
        val catalogBody: String,
        val firstPageBody: String,
        val existingAcroFormObjNum: Int?,
        val existingAcroFormBody: String?,
        val existingFieldCount: Int,
        val infoSnippet: String?,
        val idSnippet: String?,
        val objectBodies: Map<Int, String>,
    )

    private fun parsePdfDocument(text: String, fileSize: Int): ParsedPdf {
        val lastXrefOffset = STARTXREF_REGEX.findAll(text)
            .lastOrNull()?.groupValues?.get(1)?.toLongOrNull()
            ?: error("Missing startxref before %%EOF")
        require(lastXrefOffset in 0L until fileSize.toLong())

        val trailerMatches = TRAILER_DICT_REGEX.findAll(text).toList()
        require(trailerMatches.isNotEmpty()) { "Missing trailer dictionary" }
        val lastTrailer = trailerMatches.last().groupValues[1]

        val rootObjNum = ROOT_REF_REGEX.find(lastTrailer)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Missing /Root in trailer")

        val infoSnippet = INFO_REF_REGEX.find(lastTrailer)?.value
        val idSnippet = ID_ARRAY_REGEX.find(lastTrailer)?.value

        val objectBodies = mutableMapOf<Int, String>()
        var maxObjNum = 0

        val objMatches = OBJ_DEF_REGEX.findAll(text).toList()
        for (m in objMatches) {
            val num = m.groupValues[1].toInt()
            maxObjNum = maxOf(maxObjNum, num)
            val startIdx = m.range.last + 1
            val endIdx = text.indexOf("endobj", startIdx)
            if (endIdx != -1) {
                val rawObj = text.substring(startIdx, endIdx)
                val dictStart = rawObj.indexOf("<<")
                val dictEnd = rawObj.lastIndexOf(">>")
                if (dictStart != -1 && dictEnd != -1 && dictEnd > dictStart) {
                    objectBodies[num] = rawObj.substring(dictStart + 2, dictEnd).trim()
                } else {
                    objectBodies[num] = rawObj.trim()
                }
            }
        }

        val catalogBody = objectBodies[rootObjNum] ?: error("Missing catalog object $rootObjNum")
        val pagesObjNum = PAGES_REF_REGEX.find(catalogBody)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Missing /Pages in catalog")

        val firstPageObjNum = resolveFirstPage(pagesObjNum, objectBodies)

        val firstPageBody = objectBodies[firstPageObjNum] ?: error("Missing first page object $firstPageObjNum")

        val existingAcroFormObjNum = ACRO_FORM_REF_REGEX.find(catalogBody)?.groupValues?.get(1)?.toIntOrNull()
        val existingAcroFormBody = existingAcroFormObjNum?.let { objectBodies[it] }

        val existingFieldCount = if (existingAcroFormBody != null) {
            FIELDS_ARRAY_REGEX.find(existingAcroFormBody)?.groupValues?.get(1)?.let { fieldsStr ->
                OBJ_REF_REGEX.findAll(fieldsStr).count()
            } ?: 0
        } else {
            0
        }

        return ParsedPdf(
            maxObjNum = maxObjNum,
            lastXrefOffset = lastXrefOffset,
            rootObjNum = rootObjNum,
            pagesObjNum = pagesObjNum,
            firstPageObjNum = firstPageObjNum,
            catalogBody = catalogBody,
            firstPageBody = firstPageBody,
            existingAcroFormObjNum = existingAcroFormObjNum,
            existingAcroFormBody = existingAcroFormBody,
            existingFieldCount = existingFieldCount,
            infoSnippet = infoSnippet,
            idSnippet = idSnippet,
            objectBodies = objectBodies,
        )
    }

    private val PAGE_TYPE_REGEX = Regex("""/Type\s*/Page\b""")

    private fun resolveFirstPage(nodeNum: Int, objectBodies: Map<Int, String>): Int {
        val body = objectBodies[nodeNum] ?: error("Missing page node $nodeNum")
        if (PAGE_TYPE_REGEX.containsMatchIn(body) || (body.contains("/MediaBox") && !body.contains("/Kids"))) {
            return nodeNum
        }
        val kidsMatch = Regex("""/Kids\s*\[\s*([\s\S]*?)\s*\]""").find(body)
            ?: error("Missing /Kids in page node $nodeNum")
        val firstKid = OBJ_REF_REGEX.find(kidsMatch.groupValues[1])?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Empty /Kids in page node $nodeNum")
        return resolveFirstPage(firstKid, objectBodies)
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
