package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.ProfileHttpFailure
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import java.security.cert.X509Certificate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class JuntaTriPhaseAdapter internal constructor(
    private val transport: ProfileHttpTransport = HttpsProfileHttpTransport(),
    private val codec: JuntaTriPhaseCodec = JuntaTriPhaseCodec(),
    private val callTimeoutMillis: Long = DEFAULT_CALL_TIMEOUT_MILLIS,
) : SigningProtocolAdapter {
    init {
        require(callTimeoutMillis > 0)
    }

    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matchesObservedJuntaContract()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val decoded = try {
            codec.decodeRequest(request, certificateChain)
        } catch (error: TriPhaseCodecException) {
            return ProtocolPrepareResult.Failure(error.toSigningError())
        }
        var transferred = false
        return try {
            val preRequest = codec.buildPreRequest(decoded)
            val networkResult = preRequest.use { ownedRequest ->
                postWithDeadline(ownedRequest)
            }
            when (networkResult) {
                is ProfileHttpResult.Failure ->
                    ProtocolPrepareResult.Failure(networkResult.code.toSigningError())
                is ProfileHttpResult.Success -> networkResult.response.use { response ->
                    try {
                        val preSign = response.withBody { body ->
                            codec.parsePreResponse(decoded, body)
                        }
                        transferred = true
                        ProtocolPrepareResult.Success(preSign)
                    } catch (error: TriPhaseCodecException) {
                        ProtocolPrepareResult.Failure(error.toSigningError())
                    }
                }
            }
        } catch (error: TriPhaseCodecException) {
            ProtocolPrepareResult.Failure(error.toSigningError())
        } finally {
            if (!transferred) decoded.close()
        }
    }

    override suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult {
        if (!request.matchesObservedJuntaContract()) {
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
                codec.buildPostRequest(state, localSignature)
            } finally {
                state.close()
            }
            val networkResult = postRequest.use { ownedRequest ->
                postWithDeadline(ownedRequest)
            }
            when (networkResult) {
                is ProfileHttpResult.Failure ->
                    ProtocolCompletionResult.Failure(networkResult.code.toSigningError())
                is ProfileHttpResult.Success -> networkResult.response.use { response ->
                    try {
                        ProtocolCompletionResult.Success(
                            response.withBody(codec::parsePostResponse),
                        )
                    } catch (error: TriPhaseCodecException) {
                        ProtocolCompletionResult.Failure(error.toSigningError())
                    }
                }
            }
        } catch (error: TriPhaseCodecException) {
            ProtocolCompletionResult.Failure(error.toSigningError())
        } finally {
            localSignature.close()
            preSign.close()
        }
    }

    private fun NormalizedSignRequest.matchesObservedJuntaContract(): Boolean =
        protocolId == ID &&
            context.profileId == PROFILE_ID &&
            context.profileVersion == PROFILE_VERSION &&
            context.origin.serialized == PORTAL_ORIGIN &&
            format == SigningFormat.CADES &&
            (algorithm == SigningAlgorithm.SHA1_WITH_RSA ||
                algorithm == SigningAlgorithm.SHA256_WITH_RSA)

    private suspend fun postWithDeadline(
        request: dev.junta.firmamobile.network.ProfileHttpRequest,
    ): ProfileHttpResult = try {
        withTimeout(callTimeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = ProfileHttpCancellation()
                continuation.invokeOnCancellation { cancellation.cancel() }
                CoroutineScope(Dispatchers.IO).launch {
                    val result = try {
                        transport.post(request, cancellation)
                    } catch (_: Exception) {
                        ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
                    }
                    continuation.resume(result) { _, cancelledResult, _ ->
                        if (cancelledResult is ProfileHttpResult.Success) {
                            cancelledResult.response.close()
                        }
                    }
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
    }

    private fun TriPhaseCodecException.toSigningError(): SigningErrorCode = when (code) {
        TriPhaseCodecError.INVALID_REQUEST -> SigningErrorCode.INVALID_REQUEST
        TriPhaseCodecError.ORIGIN_NOT_ALLOWED -> SigningErrorCode.ORIGIN_NOT_ALLOWED
        TriPhaseCodecError.REQUEST_TOO_LARGE,
        TriPhaseCodecError.RESPONSE_TOO_LARGE,
        -> SigningErrorCode.REQUEST_TOO_LARGE
        TriPhaseCodecError.RESPONSE_FORMAT_INVALID -> SigningErrorCode.PROTOCOL_FAILED
    }

    private fun ProfileHttpFailure.toSigningError(): SigningErrorCode = when (this) {
        ProfileHttpFailure.SESSION_EXPIRED -> SigningErrorCode.SESSION_EXPIRED
        ProfileHttpFailure.INVALID_ENDPOINT,
        ProfileHttpFailure.PRIVATE_ADDRESS,
        ProfileHttpFailure.REDIRECT_BLOCKED,
        -> SigningErrorCode.ORIGIN_NOT_ALLOWED
        ProfileHttpFailure.RESPONSE_TOO_LARGE -> SigningErrorCode.REQUEST_TOO_LARGE
        ProfileHttpFailure.CONTENT_TYPE_INVALID,
        ProfileHttpFailure.HTTP_ERROR,
        ProfileHttpFailure.NETWORK_ERROR,
        -> SigningErrorCode.PROTOCOL_FAILED
    }

    companion object {
        val ID = SigningProtocolId("junta-miniapplet-triphase-cades-v1")
        private const val PROFILE_ID = "junta-andalucia"
        private const val PROFILE_VERSION = 1
        private const val PORTAL_ORIGIN = "https://www.juntadeandalucia.es"
        private const val DEFAULT_CALL_TIMEOUT_MILLIS = 20_000L
    }
}
