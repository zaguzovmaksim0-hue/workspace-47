package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.CertificateFilterRules
import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileActivation
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.security.BoundedReplayLedger
import dev.junta.firmamobile.security.MonotonicSecurityTime
import dev.junta.firmamobile.signing.SigningErrorCode
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import org.json.JSONObject

internal data class EuskadiClientAuthPostBridgeRequest(
    val requestId: UUID,
    val authorized: AuthorizedClientAuthTarget,
    val postBody: ByteArray,
)

internal sealed interface EuskadiClientAuthPostBridgeRouteResult {
    data object NotApplicable : EuskadiClientAuthPostBridgeRouteResult
    data class Accepted(val request: EuskadiClientAuthPostBridgeRequest) :
        EuskadiClientAuthPostBridgeRouteResult
    data class Rejected(
        val requestId: UUID?,
        val code: SigningErrorCode,
    ) : EuskadiClientAuthPostBridgeRouteResult
}

/**
 * Converts only the reviewed Izenpe certificate form POST into the existing isolated client-auth flow.
 * Opaque form values are validated in memory, form-encoded once, and never logged or persisted here.
 */
internal class EuskadiClientAuthPostBridgeAdapter(
    private val profileRegistry: SiteProfileRegistry = BuiltInSiteProfiles.runtimeRegistry,
    private val activeProfileId: () -> ProfileId? = { null },
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val authorizer: ClientAuthNavigationAuthorizer = ClientAuthNavigationAuthorizer(
        profileRegistry,
        monotonicNanos,
    ),
) {
    private val requestReplayLedger = BoundedReplayLedger<UUID>(
        monotonicNanos = monotonicNanos,
        retention = Duration.ofSeconds(REPLAY_RETENTION_SECONDS),
        maxEntries = MAX_REPLAY_ENTRIES,
    )
    private val correlationReplayLedger = BoundedReplayLedger<UUID>(
        monotonicNanos = monotonicNanos,
        retention = Duration.ofSeconds(REPLAY_RETENTION_SECONDS),
        maxEntries = MAX_REPLAY_ENTRIES,
    )

    @Synchronized
    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        navigationEpoch: Long,
        currentPageUrl: String?,
    ): EuskadiClientAuthPostBridgeRouteResult {
        if (rawMessage.length > MAX_MESSAGE_CHARS) {
            return rejected(null, SigningErrorCode.REQUEST_TOO_LARGE)
        }
        val keys = rawMessage.uniqueTopLevelKeys()
            ?: return rejected(null, SigningErrorCode.INVALID_REQUEST)
        val json = runCatching { JSONObject(rawMessage) }.getOrNull()
            ?: return rejected(null, SigningErrorCode.INVALID_REQUEST)
        val type = json.strictString(TYPE_FIELD)
            ?: return rejected(null, SigningErrorCode.INVALID_REQUEST)
        if (type != POST_TYPE) return EuskadiClientAuthPostBridgeRouteResult.NotApplicable

        val requestId = json.strictUuidV4(REQUEST_ID_FIELD)
        if (keys != POST_KEYS || requestId == null || navigationEpoch < 0L ||
            navigationEpoch == Long.MAX_VALUE
        ) {
            return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
        if (json.strictString(PROFILE_ID_FIELD) != PROFILE_ID) {
            return rejected(requestId, SigningErrorCode.PROFILE_NOT_ACTIVE)
        }
        if (json.strictString(METHOD_FIELD) != POST_METHOD ||
            json.strictString(CONTENT_TYPE_FIELD) != FORM_CONTENT_TYPE ||
            json.strictString(TARGET_URL_FIELD) != TARGET_URL
        ) {
            return rejected(requestId, SigningErrorCode.UNOBSERVED_CONTRACT)
        }
        if (!isMainFrame) return rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)
        if (activeProfileId()?.value != PROFILE_ID) {
            return rejected(requestId, SigningErrorCode.PROFILE_NOT_ACTIVE)
        }
        if (!isExactSourceOrigin(sourceOrigin)) {
            return rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED)
        }
        if (currentPageUrl != SOURCE_PAGE) {
            return rejected(requestId, SigningErrorCode.UNOBSERVED_CONTRACT)
        }

        val profile = profileRegistry.profile(ProfileId(PROFILE_ID))
            ?: return rejected(requestId, SigningErrorCode.PROFILE_NOT_ACTIVE)
        val policy = profile.clientAuthPolicy
            ?: return rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        if (!isExactProfileContract(profile, policy)) {
            return rejected(requestId, SigningErrorCode.UNOBSERVED_CONTRACT)
        }

        val opaqueRequest = json.strictString(OPAQUE_REQUEST_FIELD)
            ?.takeIf(::isSafeOpaqueRequest)
            ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val correlationRaw = json.strictString(CORRELATION_FIELD)
            ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        val correlationId = correlationRaw.canonicalUuidV4()
            ?: return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        if (requestReplayLedger.contains(requestId) || correlationReplayLedger.contains(correlationId)) {
            return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val authorized = authorizer.observeTopLevelNavigation(
            activeProfileId = profile.profileId,
            currentUrl = SOURCE_PAGE,
            targetUrl = TARGET_URL,
            currentEpoch = navigationEpoch,
            isModernMainFrameRequest = true,
        ) ?: return rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED)

        if (!requestReplayLedger.recordNew(requestId) || !correlationReplayLedger.recordNew(correlationId)) {
            return rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }

        val body = formEncode(opaqueRequest, correlationRaw).toByteArray(StandardCharsets.UTF_8)
        return EuskadiClientAuthPostBridgeRouteResult.Accepted(
            EuskadiClientAuthPostBridgeRequest(
                requestId = requestId,
                authorized = authorized,
                postBody = body,
            ),
        )
    }

    fun invalidate() {
        authorizer.invalidate()
    }

    private fun isExactProfileContract(profile: SiteProfile, policy: ClientAuthPolicy): Boolean =
        profile.profileVersion == PROFILE_VERSION &&
            profile.displayName == DISPLAY_NAME &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == START_URL &&
            profile.initiatorOrigins == setOf(ExactOrigin.parse(EUSKADI_ORIGIN)) &&
            profile.redirectOrigins == setOf(ExactOrigin.parse(IZENPE_ORIGIN)) &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() && profile.operationPolicies.isEmpty() &&
            profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH) &&
            profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true) &&
            policy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(TARGET_ORIGIN)),
                sourceUrls = setOf(URI(SOURCE_PAGE)),
                requestPath = TARGET_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = 15,
                requestPort = 443,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameterMappings = emptyMap(),
            )

    private fun isExactSourceOrigin(uri: Uri): Boolean =
        !uri.isOpaque && uri.scheme == "https" && uri.host == IZENPE_HOST &&
            uri.port in setOf(-1, 443) && uri.encodedUserInfo == null &&
            uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null

    private fun isSafeOpaqueRequest(value: String): Boolean =
        value.length in 1..MAX_OPAQUE_REQUEST_CHARS && value.all { it.code in 0x21..0x7e }

    private fun formEncode(request: String, correlation: String): String =
        "request=${encode(request)}&x_correlation_id=${encode(correlation)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun rejected(requestId: UUID?, code: SigningErrorCode) =
        EuskadiClientAuthPostBridgeRouteResult.Rejected(requestId, code)

    private fun String.uniqueTopLevelKeys(): Set<String>? = try {
        JsonReader(StringReader(this)).use { reader ->
            reader.isLenient = false
            val names = linkedSetOf<String>()
            reader.beginObject()
            while (reader.hasNext()) {
                if (!names.add(reader.nextName())) return null
                reader.skipValue()
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) return null
            names
        }
    } catch (_: Exception) {
        null
    }

    private fun JSONObject.strictUuidV4(name: String): UUID? =
        strictString(name)?.canonicalUuidV4()

    private fun String.canonicalUuidV4(): UUID? {
        if (!UUID_PATTERN.matches(this)) return null
        return runCatching { UUID.fromString(this) }.getOrNull()
            ?.takeIf { it.toString() == lowercase() }
    }

    private fun JSONObject.strictString(name: String): String? = opt(name) as? String

    companion object {
        const val PROFILE_ID = "euskadi-sede-electronica"
        const val PROFILE_VERSION = 1
        const val DISPLAY_NAME = "Gobierno Vasco — Registro Electrónico General"
        const val EUSKADI_ORIGIN = "https://www.euskadi.eus"
        const val IZENPE_ORIGIN = "https://eidas.izenpe.com"
        const val IZENPE_HOST = "eidas.izenpe.com"
        const val TARGET_ORIGIN = "https://eidas2.izenpe.com"
        const val START_URL =
            "https://www.euskadi.eus/web01-sedeform/es/x43kToolkitWar/form/fdp?" +
                "procedureId=1017701&tipoPresentacion=19&language=es"
        const val SOURCE_PAGE =
            "https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication"
        const val TARGET_PATH = "/cert-authn-external-validation/authenticate"
        const val TARGET_URL = "$TARGET_ORIGIN$TARGET_PATH"
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"
        private const val POST_TYPE = "EUSKADI_CLIENT_AUTH_POST"
        private const val POST_METHOD = "POST"
        private const val TYPE_FIELD = "type"
        private const val PROFILE_ID_FIELD = "profileId"
        private const val REQUEST_ID_FIELD = "requestId"
        private const val METHOD_FIELD = "method"
        private const val CONTENT_TYPE_FIELD = "contentType"
        private const val TARGET_URL_FIELD = "targetUrl"
        private const val OPAQUE_REQUEST_FIELD = "request"
        private const val CORRELATION_FIELD = "x_correlation_id"
        private const val MAX_MESSAGE_CHARS = 12 * 1024
        private const val MAX_OPAQUE_REQUEST_CHARS = 4 * 1024
        private const val REPLAY_RETENTION_SECONDS = 30L
        private const val MAX_REPLAY_ENTRIES = 32
        private val POST_KEYS = setOf(
            TYPE_FIELD,
            PROFILE_ID_FIELD,
            REQUEST_ID_FIELD,
            METHOD_FIELD,
            CONTENT_TYPE_FIELD,
            TARGET_URL_FIELD,
            OPAQUE_REQUEST_FIELD,
            CORRELATION_FIELD,
        )
        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-" +
                "[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
        )
    }
}
