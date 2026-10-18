package dev.junta.firmamobile.signing

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.Provider
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Calendar
import java.util.TimeZone

/** The exact, fixed parameters that distinguish one MiniApplet PAdES portal from another. */
internal class MiniAppletPadesContract(
    val id: SigningProtocolId,
    val profileId: String,
    val initiatorOrigin: String,
    val safeDescription: String,
    val expectedExtraProperties: String,
    val signingAlgorithm: SigningAlgorithm,
    val signaturePolicy: CadesSignaturePolicy? = null,
    val profileVersion: Int = 1,
    val isSigningPage: (String?) -> Boolean,
)

/** Shared MiniApplet PAdES flow; each portal supplies only its exact [MiniAppletPadesContract]. */
open class MiniAppletPadesAdapter internal constructor(
    private val contract: MiniAppletPadesContract,
    private val clock: Clock = Clock.systemUTC(),
    private val provider: Provider = BundledBouncyCastle.provider,
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = contract.id

    final override fun documentConsent(request: NormalizedSignRequest): DocumentConsentEvidence {
        if (!request.matchesContract()) return DocumentConsentEvidence.unavailable(SigningPayloadKind.ENVELOPE)
        return try {
            request.withPayload { payload ->
                require(payload.size <= PendingSignRequestStore.MAX_PAYLOAD_BYTES)
                MiniAppletPayloadCodec.withDecoded(payload) { pdfBytes, extraProperties ->
                    if (isExpectedDocument(pdfBytes, extraProperties)) {
                        DocumentConsentEvidence.fromEnvelope(request, payload, pdfBytes)
                    } else {
                        DocumentConsentEvidence.unavailable(SigningPayloadKind.ENVELOPE)
                    }
                }
            }
        } catch (_: Exception) {
            DocumentConsentEvidence.unavailable(SigningPayloadKind.ENVELOPE)
        }
    }

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
                    if (!isExpectedDocument(pdfBytes, extraProperties)) {
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
        val state = preSign.consumeState(request) as? MiniAppletPadesState
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
                        signingAlgorithm = contract.signingAlgorithm,
                        signaturePolicy = contract.signaturePolicy,
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
            } catch (_: Exception) {
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
            PDDocument.load(
                ByteArrayInputStream(sourcePdf),
                MemoryUsageSetting.setupMainMemoryOnly(16L * 1024 * 1024),
            )
        } catch (error: Exception) {
            sourcePdf.fill(0)
            throw error
        }
        var options: SignatureOptions? = null
        var output: ClearingByteArrayOutputStream? = null
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
            output = ClearingByteArrayOutputStream()
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
                signingAlgorithm = contract.signingAlgorithm,
                signaturePolicy = contract.signaturePolicy,
            )
            detachedContent.fill(0)
            detachedContent = null
            val state = MiniAppletPadesState(
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
        PDDocument.load(
            ByteArrayInputStream(bytes),
            MemoryUsageSetting.setupMainMemoryOnly(16L * 1024 * 1024),
        ).use { document ->
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
        protocolId == contract.id &&
            context.profileId == contract.profileId &&
            context.profileVersion == contract.profileVersion &&
            context.origin.serialized == contract.initiatorOrigin &&
            contract.isSigningPage(context.pageUrl) &&
            safeDescription == contract.safeDescription &&
            algorithm == contract.signingAlgorithm &&
            format == SigningFormat.PADES


    private fun isExpectedDocument(pdfBytes: ByteArray, extraProperties: String): Boolean =
        extraProperties == contract.expectedExtraProperties &&
            pdfBytes.size in 5..MAX_PDF_BYTES && pdfBytes.startsWithPdfHeader()

    private fun ByteArray.startsWithPdfHeader(): Boolean =
        size >= 5 && this[0] == '%'.code.toByte() && this[1] == 'P'.code.toByte() &&
            this[2] == 'D'.code.toByte() && this[3] == 'F'.code.toByte() && this[4] == '-'.code.toByte()

    companion object {
        const val MAX_PDF_BYTES = 500_000
        private const val MAX_BYTE_RANGE_BYTES = 524_288
        private const val MAX_OUTPUT_PDF_BYTES = 2 * 1024 * 1024
        private const val PREFERRED_SIGNATURE_BYTES = 64 * 1024
        private const val MAX_CERTIFICATES = 10
    }
}

private class MiniAppletPadesState(
    private val document: PDDocument,
    sourcePdf: ByteArray,
    private val signatureOptions: SignatureOptions,
    val externalSigningSupport: ExternalSigningSupport,
    private val output: ClearingByteArrayOutputStream,
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

private class ClearingByteArrayOutputStream : ByteArrayOutputStream() {
    fun clear() {
        buf.fill(0)
        reset()
    }
}
