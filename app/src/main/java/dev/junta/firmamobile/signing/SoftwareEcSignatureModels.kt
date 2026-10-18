package dev.junta.firmamobile.signing

import java.security.PrivateKey
import java.security.cert.X509Certificate

private const val P256_MAX_DER_BYTES = 72
private const val P384_MAX_DER_BYTES = 104

/** Raw DER ECDSA only. Not part of the runtime/RSA SigningAlgorithm API. */
internal enum class SoftwareEcSignatureAlgorithm(
    val namedCurve: String,
    val jcaName: String,
    val maxDerBytes: Int,
) {
    P256_SHA256("secp256r1", "SHA256withECDSA", P256_MAX_DER_BYTES),
    P384_SHA384("secp384r1", "SHA384withECDSA", P384_MAX_DER_BYTES),
}

/** References are fixed; input bytes are supplied separately and snapshotted by the engine. */
internal class SoftwareEcSignatureRequest(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val algorithm: SoftwareEcSignatureAlgorithm,
)

internal enum class SoftwareEcSignatureError {
    INPUT_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    UNSUPPORTED_KEY,
    SIGNATURE_FAILED,
}

internal sealed interface SoftwareEcSignatureResult {
    /** The caller owns these verified DER bytes. */
    class Success(val derSignature: ByteArray) : SoftwareEcSignatureResult

    data class Failure(val error: SoftwareEcSignatureError) : SoftwareEcSignatureResult
}
