package dev.junta.firmamobile.network

import java.net.URI

/** Admit only the exact runtime PRE/POST URL accepted by one fixed STA host policy. */
internal class StaBatchHttpTransport(
    private val validate: (String) -> MelillaBatchUrlValidation,
    private val factory: (URI) -> ProfileHttpTransport = { endpoint ->
        HttpsProfileHttpTransport(SafeNetworkUrlPolicy(setOf(endpoint)))
    },
) : ProfileHttpTransport {
    override fun post(request: ProfileHttpRequest, cancellation: ProfileHttpCancellation): ProfileHttpResult {
        val admitted = validate(request.url.uri.toASCIIString()) as? MelillaBatchUrlValidation.Allowed
        if (admitted == null || admitted.binding.operation == MelillaBatchUrlOperation.GETDATA ||
            admitted.url != request.url.uri || request.encodedQuery != null
        ) return ProfileHttpResult.Failure(ProfileHttpFailure.INVALID_ENDPOINT)
        if (cancellation.isCancelled()) return ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
        return factory(admitted.url).post(request, cancellation)
    }
}
