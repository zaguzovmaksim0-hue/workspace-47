package dev.junta.firmamobile.signing

import java.security.cert.X509Certificate

interface SigningProtocolAdapter {
    val id: SigningProtocolId

    suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult

    suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult
}
