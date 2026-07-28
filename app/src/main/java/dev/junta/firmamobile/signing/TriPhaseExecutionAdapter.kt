package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpFailure
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import java.io.Closeable
import java.net.URI
import java.security.cert.X509Certificate
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal interface TriPhaseExecutionAdapter : SigningProtocolAdapter

internal enum class TriPhaseCodecError {
    INVALID_REQUEST,
    ORIGIN_NOT_ALLOWED,
    REQUEST_TOO_LARGE,
    RESPONSE_TOO_LARGE,
    RESPONSE_FORMAT_INVALID,
}

internal class TriPhaseCodecException(
    val code: TriPhaseCodecError,
) : Exception(code.name)

internal interface TriPhaseDecodedRequest : Closeable

internal interface TriPhaseProtocolCodec {
    fun decodeRequest(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): TriPhaseDecodedRequest

    fun buildPreRequest(data: TriPhaseDecodedRequest): ProfileHttpRequest

    fun parsePreResponse(data: TriPhaseDecodedRequest, response: ByteArray): PreSignResult

    fun buildPostRequest(state: PreSignState, localSignature: LocalSignature): ProfileHttpRequest

    fun parsePostResponse(response: ByteArray): LocalSignature
}

internal data class TriPhaseExecutionContract(
    val protocolId: SigningProtocolId,
    val profileId: String,
    val profileVersion: Int,
    val initiatorOrigins: Set<String>,
    val endpoint: URI,
    val format: SigningFormat,
    val algorithms: Set<SigningAlgorithm>,
)

internal class AutoFirmaTriPhaseExecutionAdapter(
    private val contract: TriPhaseExecutionContract,
    private val transport: ProfileHttpTransport,
    private val codec: TriPhaseProtocolCodec,
    private val callTimeoutMillis: Long,
    private val executor: Executor = Dispatchers.IO.asExecutor(),
) : TriPhaseExecutionAdapter {
    init {
        require(callTimeoutMillis > 0)
        require(contract.profileVersion >= 1)
        require(contract.initiatorOrigins.isNotEmpty() && contract.algorithms.isNotEmpty())
    }

    override val id: SigningProtocolId = contract.protocolId

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matches(contract)) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val decoded = try {
            codec.decodeRequest(request, certificateChain)
        } catch (error: TriPhaseCodecException) {
            return ProtocolPrepareResult.Failure(error.toSigningError())
        } catch (_: RuntimeException) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
        var transferred = false
        return try {
            val preRequest = codec.buildPreRequest(decoded).use { generatedRequest ->
                generatedRequest.duplicateWithRequestId(request.requestId)
            }
            val networkResult = preRequest.use { ownedRequest -> postWithDeadline(ownedRequest) }
            when (networkResult) {
                is ProfileHttpResult.Failure ->
                    ProtocolPrepareResult.Failure(networkResult.toSigningError())
                is ProfileHttpResult.Success -> networkResult.response.use { response ->
                    try {
                        val preSign = response.withBody { body -> codec.parsePreResponse(decoded, body) }
                        transferred = true
                        ProtocolPrepareResult.Success(preSign)
                    } catch (error: TriPhaseCodecException) {
                        ProtocolPrepareResult.Failure(error.toSigningError())
                    }
                }
            }
        } catch (error: TriPhaseCodecException) {
            ProtocolPrepareResult.Failure(error.toSigningError())
        } catch (_: RuntimeException) {
            ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        } finally {
            if (!transferred) decoded.close()
        }
    }

    override suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult {
        if (!request.matches(contract)) {
            localSignature.close()
            preSign.close()
            return ProtocolCompletionResult.Failure(SigningErrorCode.PAYLOAD_CHANGED)
        }
        val state = preSign.consumeState(request)
        if (state == null) {
            localSignature.close()
            preSign.close()
            return ProtocolCompletionResult.Failure(SigningErrorCode.PAYLOAD_CHANGED)
        }
        return try {
            val postRequest = try {
                codec.buildPostRequest(state, localSignature).use { generatedRequest ->
                    generatedRequest.duplicateWithRequestId(request.requestId)
                }
            } finally {
                state.close()
            }
            val networkResult = postRequest.use { ownedRequest -> postWithDeadline(ownedRequest) }
            when (networkResult) {
                is ProfileHttpResult.Failure ->
                    ProtocolCompletionResult.Failure(networkResult.toSigningError())
                is ProfileHttpResult.Success -> networkResult.response.use { response ->
                    try {
                        ProtocolCompletionResult.Success(response.withBody(codec::parsePostResponse))
                    } catch (error: TriPhaseCodecException) {
                        ProtocolCompletionResult.Failure(error.toSigningError())
                    }
                }
            }
        } catch (error: TriPhaseCodecException) {
            ProtocolCompletionResult.Failure(error.toSigningError())
        } catch (_: RuntimeException) {
            ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        } finally {
            localSignature.close()
            preSign.close()
        }
    }

    private suspend fun postWithDeadline(request: ProfileHttpRequest): ProfileHttpResult {
        if (request.url.uri != contract.endpoint) {
            return ProfileHttpResult.Failure(ProfileHttpFailure.INVALID_ENDPOINT)
        }
        val cancellation = ProfileHttpCancellation()
        val result = withTimeoutOrNull(callTimeoutMillis) {
            suspendCancellableCoroutine<ProfileHttpResult> { continuation ->
                continuation.invokeOnCancellation { cancellation.cancel() }
                try {
                    executor.execute {
                        val networkResult = try {
                            transport.post(request, cancellation)
                        } catch (_: Exception) {
                            ProfileHttpResult.Failure(
                                cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR),
                            )
                        }
                        continuation.resume(networkResult) { _, cancelledResult, _ ->
                            if (cancelledResult is ProfileHttpResult.Success) {
                                cancelledResult.response.close()
                            }
                        }
                    }
                } catch (_: RuntimeException) {
                    continuation.resume(
                        ProfileHttpResult.Failure(
                            cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR),
                        ),
                    ) { _, _, _ -> }
                }
            }
        }
        if (result != null) return result

        cancellation.cancel()
        return ProfileHttpResult.Failure(
            cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR),
        )
    }

    private fun NormalizedSignRequest.matches(expected: TriPhaseExecutionContract): Boolean =
        protocolId == expected.protocolId &&
            context.profileId == expected.profileId &&
            context.profileVersion == expected.profileVersion &&
            context.origin.serialized in expected.initiatorOrigins &&
            format == expected.format &&
            algorithm in expected.algorithms
}

internal fun TriPhaseCodecException.toSigningError(): SigningErrorCode = when (code) {
    TriPhaseCodecError.INVALID_REQUEST -> SigningErrorCode.INVALID_REQUEST
    TriPhaseCodecError.ORIGIN_NOT_ALLOWED -> SigningErrorCode.ORIGIN_NOT_ALLOWED
    TriPhaseCodecError.REQUEST_TOO_LARGE,
    TriPhaseCodecError.RESPONSE_TOO_LARGE,
    -> SigningErrorCode.REQUEST_TOO_LARGE
    TriPhaseCodecError.RESPONSE_FORMAT_INVALID -> SigningErrorCode.PROTOCOL_FAILED
}

internal fun ProfileHttpResult.Failure.toSigningError(): SigningErrorCode = when (code) {
    ProfileHttpFailure.SESSION_EXPIRED -> SigningErrorCode.SESSION_EXPIRED
    ProfileHttpFailure.INVALID_ENDPOINT,
    ProfileHttpFailure.PRIVATE_ADDRESS,
    ProfileHttpFailure.REDIRECT_BLOCKED,
    -> SigningErrorCode.ORIGIN_NOT_ALLOWED
    ProfileHttpFailure.RESPONSE_TOO_LARGE -> SigningErrorCode.REQUEST_TOO_LARGE
    ProfileHttpFailure.CONTENT_TYPE_INVALID,
    ProfileHttpFailure.HTTP_ERROR,
    -> SigningErrorCode.PROTOCOL_FAILED
    ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN -> SigningErrorCode.NETWORK_RESULT_UNCERTAIN
    ProfileHttpFailure.NETWORK_ERROR,
    ProfileHttpFailure.DIRECT_CONNECT_UNAVAILABLE,
    ProfileHttpFailure.TUNNEL_AUTH_UNAVAILABLE,
    ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE,
    ProfileHttpFailure.UPSTREAM_CONNECT_UNAVAILABLE,
    -> if (detail.safeForRouteFallback) {
        SigningErrorCode.SIGNING_SERVICE_UNAVAILABLE
    } else {
        SigningErrorCode.NETWORK_RESULT_UNCERTAIN
    }
}

internal fun ProfileHttpFailure.toSigningError(): SigningErrorCode =
    ProfileHttpResult.Failure(this).toSigningError()
