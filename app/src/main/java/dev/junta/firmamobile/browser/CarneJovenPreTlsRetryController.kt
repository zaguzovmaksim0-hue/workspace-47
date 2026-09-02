package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.HttpMethod
import dev.junta.firmamobile.profile.matchesRequestUrl
import dev.junta.firmamobile.profile.matchesSourceUrl
import java.net.URI

/**
 * Bounded recovery for the observed flaky TCP route to the Carné Joven TLS facade.
 *
 * A retry is allowed only while the flow is still pre-TLS: the caller must invoke this
 * only for a user-confirmed target whose ClientCertRequest has not been consumed. Every
 * retry returns to the exact first-party source so the portal issues fresh ephemeral
 * ticket/session values; no previous target URL is replayed.
 */
internal class CarneJovenPreTlsRetryController(
    private val maxRetries: Int = MAX_RETRIES,
) {
    private var retriesUsed = 0

    init {
        require(maxRetries in 1..MAX_RETRIES)
    }

    @Synchronized
    fun nextSource(authorized: AuthorizedClientAuthTarget): URI? {
        if (!matchesRetryContract(authorized) || retriesUsed >= maxRetries) return null
        retriesUsed++
        return authorized.source
    }

    @Synchronized
    fun reset() {
        retriesUsed = 0
    }

    internal fun matchesRetryContract(authorized: AuthorizedClientAuthTarget): Boolean {
        val policy = authorized.policy
        return authorized.profileId.value == PROFILE_ID &&
            policy.transitionMode == ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE &&
            policy.requestMethod == HttpMethod.GET &&
            policy.requestPort == 443 &&
            policy.requestOrigins == setOf(REQUEST_ORIGIN) &&
            policy.sourceUrls == setOf(SOURCE_URI) &&
            policy.requestPath == TARGET_PATH &&
            policy.fixedQueryParameters == FIXED_QUERY &&
            policy.requiredEphemeralQueryParameters == EPHEMERAL_QUERY &&
            policy.allowEmptyIssuerList &&
            policy.grantTtlSeconds == 15 &&
            authorized.source == SOURCE_URI &&
            authorized.target.scheme == "https" &&
            authorized.target.host.equals(TARGET_HOST, ignoreCase = true) &&
            authorized.target.path == TARGET_PATH &&
            policy.matchesSourceUrl(authorized.source) &&
            policy.matchesRequestUrl(authorized.target)
    }

    private companion object {
        const val MAX_RETRIES = 4
        const val PROFILE_ID = "carne-joven-andalucia"
        const val TARGET_HOST = "ws235.juntadeandalucia.es"
        const val TARGET_PATH = "/authenticationFacade"
        const val SOURCE_URL =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val COME_BACK_URL =
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ="

        val SOURCE_URI: URI = URI(SOURCE_URL)
        val REQUEST_ORIGIN: ExactOrigin = ExactOrigin.parse("https://$TARGET_HOST")
        val FIXED_QUERY = mapOf(
            "action" to "validateCert",
            "appId" to "IAJ.CARNETJOVEN",
            "comeBackURL" to COME_BACK_URL,
        )
        val EPHEMERAL_QUERY = setOf("ticketId", "webSessionId")
    }
}
