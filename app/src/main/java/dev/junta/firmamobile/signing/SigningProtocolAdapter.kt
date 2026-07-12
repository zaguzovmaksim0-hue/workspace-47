package dev.junta.firmamobile.signing

interface SigningProtocolAdapter {
    val id: SigningProtocolId

    fun recognize(input: InterceptedSigningInput, profileId: String): Boolean

    fun normalize(
        input: InterceptedSigningInput,
        context: SigningContext,
    ): AdapterParseResult

    suspend fun prepare(request: NormalizedSignRequest): PreSignResult

    suspend fun complete(
        request: NormalizedSignRequest,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult
}
