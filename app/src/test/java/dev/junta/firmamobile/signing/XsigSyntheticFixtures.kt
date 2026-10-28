package dev.junta.firmamobile.signing

import dev.junta.firmamobile.afirma.servlet.NativeXadesEngine
import dev.junta.firmamobile.afirma.servlet.NativeXadesOptions
import dev.junta.firmamobile.afirma.servlet.NativeXadesPackaging
import dev.junta.firmamobile.afirma.servlet.generalClockFor
import dev.junta.firmamobile.certificate.UnlockedIdentity

internal data class SyntheticXsig(
    val bytes: ByteArray,
    val payload: ByteArray,
    val identity: UnlockedIdentity,
    val algorithm: SigningAlgorithm,
    val options: NativeXadesOptions,
)

internal object XsigSyntheticFixtures {
    fun sign(
        packaging: NativeXadesPackaging,
        payload: ByteArray,
        mimeType: String = "application/octet-stream",
        contentDescription: String? = null,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
    ): SyntheticXsig {
        val identity = freshSyntheticIdentity()
        val options = NativeXadesOptions(packaging, mimeType, contentDescription)
        val engine = NativeXadesEngine(generalClockFor(identity))
        val signed = engine.sign(payload, identity, algorithm, options)
        check(signed is LocalSignatureResult.Success)
        return SyntheticXsig(
            bytes = signed.signature.withBytes { it.copyOf() },
            payload = payload,
            identity = identity,
            algorithm = algorithm,
            options = options,
        )
    }
}
