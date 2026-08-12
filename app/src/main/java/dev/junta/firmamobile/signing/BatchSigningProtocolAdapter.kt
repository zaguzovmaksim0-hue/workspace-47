package dev.junta.firmamobile.signing

import java.security.cert.X509Certificate

interface BatchSigningProtocolAdapter {
    val id: SigningProtocolId

    fun prepare(
        request: NormalizedBatchSigningRequest,
        certificateChain: List<X509Certificate>,
    ): BatchProtocolPrepareResult

    fun complete(
        request: NormalizedBatchSigningRequest,
        preSign: BatchPreSignResult,
        localSignatures: List<LocalSignature>,
    ): BatchProtocolCompletionResult
}
