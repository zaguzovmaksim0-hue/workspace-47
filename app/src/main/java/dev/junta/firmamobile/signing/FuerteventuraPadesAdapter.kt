package dev.junta.firmamobile.signing

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import java.io.ByteArrayOutputStream
import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Calendar
import java.util.TimeZone
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Exact authenticated Fuerteventura MiniApplet PAdES contract. QA-only until physical E2E. */
class FuerteventuraPadesAdapter(
    private val clock: Clock = Clock.systemUTC(),
    private val provider: Provider = BouncyCastleProvider(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matchesContract()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        if (certificateChain.isEmpty() || certificateChain.size > MAX_CERTIFICATES) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }

        return try {
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { pdfBytes, extraProperties ->
                    if (extraProperties != EXPECTED_EXTRA_PROPERTIES ||
                        pdfBytes.size !in 5..MAX_PDF_BYTES ||
                        !pdfBytes.startsWithPdfHeader()
                    ) {
                        return@withDecoded ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
                    }
                    preparePdf(request, pdfBytes, certificateChain)
                }
            }
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
            localSignature.close()
            preSign.close()
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? FuerteventuraPadesState
        if (state == null) {
            localSignature.close()
            return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }

        return state.use { ownedState ->
            try {
                val cms = localSignature.withBytes { signatureValue ->
                    CadesDetachedCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        detachedContent = ownedState.detachedContent(),
                        expectedContentBytes = ownedState.expectedContentBytes,
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signatureValue,
                        provider = provider,
                        signingAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
                    )
                }
                try {
                    ownedState.externalSigningSupport.setSignature(cms)
                } finally {
                    cms.fill(0)
                }
                val result = ownedState.outputBytes()
                if (!isValidSignedPdf(result, ownedState.initialSignatureCount)) {
                    result.fill(0)
                    ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
                } else {
                    ProtocolCompletionResult.Success(LocalSignature(result))
                }
            } catch (error: Exception) {
                error.printStackTrace()
                ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
            } finally {
                localSignature.close()
            }
        }
    }

    private fun preparePdf(
        request: NormalizedSignRequest,
        pdfBytes: ByteArray,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        val sourcePdf = pdfBytes.copyOf()
        val document = try {
            PDDocument.load(sourcePdf)
        } catch (error: Exception) {
            sourcePdf.fill(0)
            throw error
        }
        var options: SignatureOptions? = null
        var output: FuerteventuraClearingByteArrayOutputStream? = null
        var detachedContent: ByteArray? = null
        var material: CadesPreSignMaterial? = null
        try {
            require(!document.isEncrypted)
            require(document.numberOfPages > 0)
            val initialSignatureCount = document.signatureDictionaries.size
            val signature = PDSignature().apply {
                setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED)
                signDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = clock.millis()
                }
            }
            options = SignatureOptions().apply {
                setPreferredSignatureSize(PREFERRED_SIGNATURE_BYTES)
            }
            document.addSignature(signature, options)
            output = FuerteventuraClearingByteArrayOutputStream()
            val external = document.saveIncrementalForExternalSigning(output)
            detachedContent = external.content.use { stream ->
                val bytes = stream.readBytes()
                require(bytes.isNotEmpty() && bytes.size <= MAX_BYTE_RANGE_BYTES)
                bytes
            }
            material = CadesDetachedCodec.createPreSign(
                content = detachedContent,
                expectedContentBytes = detachedContent.size,
                certificateChain = certificateChain,
                clock = clock,
                provider = provider,
                signingAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
            )
            detachedContent.fill(0)
            detachedContent = null
            val state = FuerteventuraPadesState(
                document = document,
                sourcePdf = sourcePdf,
                signatureOptions = options,
                externalSigningSupport = external,
                output = output,
                placeholderCms = material.placeholderCms,
                detachedContent = material.detachedContent,
                signingCertificateFingerprint = material.signingCertificateFingerprint,
                signedAttributes = material.signedAttributes,
                expectedContentBytes = material.detachedContent.size,
                initialSignatureCount = initialSignatureCount,
            )
            options = null
            output = null
            material = null
            return ProtocolPrepareResult.Success(
                PreSignResult(request, state.signedAttributes(), state),
            )
        } catch (error: Exception) {
            material?.placeholderCms?.fill(0)
            material?.detachedContent?.fill(0)
            material?.signedAttributes?.fill(0)
            material?.signingCertificateFingerprint?.fill(0)
            detachedContent?.fill(0)
            output?.clear()
            runCatching { options?.close() }
            runCatching { document.close() }
            sourcePdf.fill(0)
            throw error
        }
    }

    private fun isValidSignedPdf(bytes: ByteArray, initialSignatureCount: Int): Boolean = runCatching {
        require(bytes.size in 5..MAX_OUTPUT_PDF_BYTES && bytes.startsWithPdfHeader())
        PDDocument.load(bytes).use { document ->
            require(!document.isEncrypted)
            val signatures = document.signatureDictionaries
            require(signatures.size == initialSignatureCount + 1)
            val added = signatures.last()
            require(added.filter == PDSignature.FILTER_ADOBE_PPKLITE.name)
            require(added.subFilter == PDSignature.SUBFILTER_ETSI_CADES_DETACHED.name)
            require(added.contents.isNotEmpty())
        }
        true
    }.getOrDefault(false)

    private fun NormalizedSignRequest.matchesContract(): Boolean =
        protocolId == ID &&
            context.profileId == PROFILE_ID &&
            context.profileVersion == PROFILE_VERSION &&
            context.origin.serialized == INITIATOR_ORIGIN &&
            context.pageUrl == SIGNING_PAGE_URL &&
            safeDescription == SAFE_DESCRIPTION &&
            algorithm == SigningAlgorithm.SHA256_WITH_RSA &&
            format == SigningFormat.PADES


    private fun ByteArray.startsWithPdfHeader(): Boolean =
        size >= 5 && this[0] == '%'.code.toByte() && this[1] == 'P'.code.toByte() &&
            this[2] == 'D'.code.toByte() && this[3] == 'F'.code.toByte() && this[4] == '-'.code.toByte()

    companion object {
        val ID = SigningProtocolId("fuerteventura-register-pades-v1")
        const val PROFILE_ID = "fuerteventura-sede-electronica"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.cabildofuer.es"
        const val PUBLIC_START_URL = "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=comenzar&tipoReg=1"
        const val SIGNING_PAGE_URL = "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=verYfirmar&modo=cert"
        const val SAFE_DESCRIPTION = "Firma PAdES de solicitud en la Sede electrónica del Cabildo Insular de Fuerteventura"
        val EXPECTED_EXTRA_PROPERTIES = listOf(
            "signaturePositionOnPageLowerLeftX = 50",
            "signaturePositionOnPageLowerLeftY = 15",
            "signaturePositionOnPageUpperRightX = 150",
            "signaturePositionOnPageUpperRightY = 50",
            "signaturePages = all",
            "layer2Text= Firmado por \$\$SUBJECTCN\$\$ el día \$\$SIGNDATE=dd/MM/yyyy\$\$ \$\$ORGANIZATION\$\$",
            "layer2FontSize= 6",
            "layer2FontFamily= 0",
            "layer2FontStyle= 0",
            "signatureRotation= 0",
            "includeQuestionMark= false",
            "obfuscateCertText= true",
        ).joinToString(separator = "\n", postfix = "\n")
        const val MAX_PDF_BYTES = 500_000
        private const val MAX_BYTE_RANGE_BYTES = 524_288
        private const val MAX_OUTPUT_PDF_BYTES = 2 * 1024 * 1024
        private const val PREFERRED_SIGNATURE_BYTES = 64 * 1024
        private const val MAX_CERTIFICATES = 10
    }
}

private class FuerteventuraPadesState(
    private val document: PDDocument,
    sourcePdf: ByteArray,
    private val signatureOptions: SignatureOptions,
    val externalSigningSupport: ExternalSigningSupport,
    private val output: FuerteventuraClearingByteArrayOutputStream,
    placeholderCms: ByteArray,
    detachedContent: ByteArray,
    signingCertificateFingerprint: ByteArray,
    signedAttributes: ByteArray,
    val expectedContentBytes: Int,
    val initialSignatureCount: Int,
) : PreSignState {
    private var sourcePdf = sourcePdf
    private var placeholder = placeholderCms
    private var content = detachedContent
    private var fingerprint = signingCertificateFingerprint
    private var attributes = signedAttributes
    private var closed = false

    fun signedAttributes(): ByteArray = check(!closed).let { attributes.copyOf() }

    fun placeholderCms(): ByteArray = check(!closed).let { placeholder }
    fun detachedContent(): ByteArray = check(!closed).let { content }
    fun signingCertificateFingerprint(): ByteArray = check(!closed).let { fingerprint }
    fun outputBytes(): ByteArray = check(!closed).let { output.toByteArray() }

    override fun close() {
        if (closed) return
        closed = true
        placeholder.fill(0)
        content.fill(0)
        fingerprint.fill(0)
        attributes.fill(0)
        placeholder = ByteArray(0)
        content = ByteArray(0)
        fingerprint = ByteArray(0)
        attributes = ByteArray(0)
        output.clear()
        runCatching { signatureOptions.close() }
        runCatching { document.close() }
        sourcePdf.fill(0)
        sourcePdf = ByteArray(0)
    }
}

private class FuerteventuraClearingByteArrayOutputStream : ByteArrayOutputStream() {
    fun clear() {
        buf.fill(0)
        reset()
    }
}
