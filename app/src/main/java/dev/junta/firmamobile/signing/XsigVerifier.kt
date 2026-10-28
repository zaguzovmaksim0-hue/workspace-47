package dev.junta.firmamobile.signing

import dev.junta.firmamobile.afirma.servlet.NativeXadesEngine
import dev.junta.firmamobile.afirma.servlet.NativeXadesPackaging

object XsigVerifier {
    private const val MAX_XSIG_BYTES = 2_097_152
    private const val MAX_ORIGINAL_BYTES = 524_288
    private const val MAX_SIGNER_DISPLAY_LENGTH = 256
    private val engine = NativeXadesEngine()

    // Detekt: early returns keep each terminal verification state explicit and
    // prevent nested cryptographic checks.
    @Suppress("ReturnCount", "MagicNumber")
    fun verify(bytes: ByteArray, original: ByteArray?): XsigVerificationResult {
        if (bytes.isEmpty()) return result(XsigCryptographicStatus.READ_FAILED)
        if (bytes.size > MAX_XSIG_BYTES) return result(XsigCryptographicStatus.LIMIT_EXCEEDED)

        val profile = XsigProfileClassifier.classify(bytes)
            ?: return result(XsigCryptographicStatus.UNSUPPORTED)

        if (profile.packaging == NativeXadesPackaging.ENVELOPED ||
            profile.packaging == NativeXadesPackaging.EXTERNALLY_DETACHED
        ) {
            if (original == null) {
                return result(XsigCryptographicStatus.ORIGINAL_REQUIRED, profile)
            }
            if (original.size > MAX_ORIGINAL_BYTES) {
                return result(XsigCryptographicStatus.LIMIT_EXCEEDED, profile)
            }
        }

        val payload = profile.embeddedPayload ?: original
        if (payload == null || payload.isEmpty() || payload.size > NativeXadesEngine.MAX_INPUT) {
            return result(XsigCryptographicStatus.LIMIT_EXCEEDED, profile)
        }

        val verified = engine.verify(bytes, payload, profile.certificate, profile.algorithm, profile.options)
        return result(
            status = if (verified) XsigCryptographicStatus.VERIFIED else XsigCryptographicStatus.INVALID,
            profile = profile,
        )
    }

    private fun result(
        status: XsigCryptographicStatus,
        profile: XsigProfile? = null,
    ) = XsigVerificationResult(
        cryptographicStatus = status,
        certificateTrust = XsigCertificateTrust.NOT_EVALUATED,
        packaging = profile?.packaging,
        signerDisplay = profile?.certificate?.subjectX500Principal?.name?.take(MAX_SIGNER_DISPLAY_LENGTH),
    )
}
