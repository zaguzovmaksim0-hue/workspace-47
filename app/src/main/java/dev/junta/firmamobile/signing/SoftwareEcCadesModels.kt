package dev.junta.firmamobile.signing

internal enum class SoftwareEcCadesError {
    INPUT_TOO_LARGE,
    CERTIFICATE_TOO_LARGE,
    SIGNED_ATTRIBUTES_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    UNSUPPORTED_KEY,
    SIGNATURE_FAILED,
}

internal sealed interface SoftwareEcCadesResult {
    /** Verified detached CMS DER; the caller owns this array. */
    class Success(val cmsDer: ByteArray) : SoftwareEcCadesResult
    data class Failure(val error: SoftwareEcCadesError) : SoftwareEcCadesResult
}

internal data class SoftwareEcCadesLimits(
    val maxInputBytes: Int = MAX_INPUT_BYTES,
    val maxOutputBytes: Int = MAX_OUTPUT_BYTES,
    val maxSignedAttributesBytes: Int = MAX_SIGNED_ATTRIBUTES_BYTES,
    val maxCertificateBytes: Int = MAX_CERTIFICATE_BYTES,
) {
    init {
        require(maxInputBytes in 1..MAX_INPUT_BYTES)
        require(maxOutputBytes in 1..MAX_OUTPUT_BYTES)
        require(maxSignedAttributesBytes in 1..MAX_SIGNED_ATTRIBUTES_BYTES)
        require(maxCertificateBytes in 1..MAX_CERTIFICATE_BYTES)
    }

    companion object {
        const val MAX_INPUT_BYTES = 524_288
        const val MAX_OUTPUT_BYTES = 131_072
        const val MAX_SIGNED_ATTRIBUTES_BYTES = 4_096
        const val MAX_CERTIFICATE_BYTES = 65_536
    }
}
