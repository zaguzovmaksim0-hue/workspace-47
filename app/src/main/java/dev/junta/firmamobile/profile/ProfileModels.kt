package dev.junta.firmamobile.profile

import dev.junta.firmamobile.network.TrustedOrigin
import java.net.IDN
import java.net.URI
import java.time.LocalDate
import java.util.Locale

@JvmInline
value class ProfileId(val value: String) {
    init { require(ID_PATTERN.matches(value)) }
    private companion object { val ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}") }
}

@JvmInline
value class EndpointId(val value: String) {
    init { require(ID_PATTERN.matches(value)) }
    private companion object { val ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}") }
}

@JvmInline
value class ProtocolInputAdapterId(val value: String) {
    init { require(ID_PATTERN.matches(value)) }
    private companion object { val ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}") }
}

@JvmInline
value class CallbackContractId(val value: String) {
    init { require(ID_PATTERN.matches(value)) }
    private companion object { val ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}") }
}

@ConsistentCopyVisibility
data class ExactOrigin private constructor(
    val scheme: String,
    val host: String,
    val port: Int,
) {
    val serialized: String get() = "https://$host"
    fun toTrustedOrigin() = TrustedOrigin(scheme, host, port)

    companion object {
        fun parse(raw: String): ExactOrigin {
            require(raw.length <= 512 && !raw.any(Char::isISOControl))
            require(!raw.contains('*') && !raw.endsWith('.'))
            val uri = URI(raw)
            require(!uri.isOpaque && uri.scheme == "https" && uri.userInfo == null)
            require(uri.rawQuery == null && uri.rawFragment == null)
            require(uri.rawPath.isNullOrEmpty())
            require(uri.port == -1 || uri.port == 443)
            val rawHost = requireNotNull(uri.host)
            require(!rawHost.equals("localhost", ignoreCase = true) && !rawHost.endsWith('.'))
            require(!rawHost.startsWith('[') && !rawHost.matches(IPV4_LIKE))
            val host = IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            require(host.isNotEmpty() && host == rawHost.lowercase(Locale.ROOT))
            require(!host.matches(IPV4_LIKE))
            return ExactOrigin("https", host, 443)
        }

        fun fromTrusted(origin: TrustedOrigin): ExactOrigin? = runCatching {
            require(origin.scheme.equals("https", ignoreCase = true) && origin.port == 443)
            parse("https://${origin.host}")
        }.getOrNull()

        private val IPV4_LIKE = Regex("(?i)(?:[0-9.]+|0x[0-9a-f]+)")
    }
}

enum class CompatibilityStatus { VERIFIED_E2E, VERIFIED_CONTRACT, EXPERIMENTAL, BROWSE_ONLY, UNSUPPORTED }
enum class ProfileActivation { DISABLED, QA_ONLY, ENABLED }
enum class ClientAuthTransitionMode { REDIRECT_AFTER_SOURCE, DIRECT_FROM_SOURCE, IN_PLACE_FROM_SOURCE }
enum class TrustMode { TRUSTED_SIGNING, TRUSTED_CLIENT_AUTH, TRUSTED_BROWSE, BROWSE_ONLY, EXTERNAL_ONLY, BLOCKED }
enum class EndpointPurpose { TRIPHASE, STORAGE, RETRIEVE, PORTAL_RESULT }
enum class HttpMethod { GET, POST }
enum class RedirectPolicy { DENY }
enum class ProtocolOperation { SIGN, SELECT_CERTIFICATE }
enum class Capability { SIGN, SELECT_CERTIFICATE, CLIENT_TLS_AUTH, LEGACY_SHA1, AFIRMA_URI }
enum class SignatureAlgorithm { SHA1_WITH_RSA, SHA256_WITH_RSA, SHA512_WITH_RSA }
enum class SignatureFormat { CADES, PADES, XADES, FACTURAE }
enum class SignaturePackaging { ATTACHED, DETACHED }
enum class SignatureMode { IMPLICIT, EXPLICIT }

data class ProfileEndpoint(
    val endpointId: EndpointId,
    val purpose: EndpointPurpose,
    val url: URI,
    val method: HttpMethod,
    val requestContentTypes: Set<String>,
    val responseContentTypes: Set<String>,
    val maxRequestBytes: Int,
    val maxResponseBytes: Int,
    val redirects: RedirectPolicy,
)

data class OperationPolicy(
    val operation: ProtocolOperation,
    val safeDescription: String,
    val inputAdapterId: ProtocolInputAdapterId,
    val callbackContractId: CallbackContractId,
    val capabilities: Set<Capability>,
    val endpointId: EndpointId?,
    val algorithms: Set<SignatureAlgorithm>,
    val format: SignatureFormat?,
    val packaging: SignaturePackaging?,
    val mode: SignatureMode?,
    val fixedExtraProperties: Map<String, String>,
    val allowedExtraProperties: Set<String>,
)

data class ClientAuthUrlConstraint(
    val origin: ExactOrigin,
    val path: String,
    val fixedQueryParameters: Map<String, String> = emptyMap(),
    val requiredEphemeralQueryParameters: Set<String> = emptySet(),
)

data class ClientAuthPolicy(
    val transitionMode: ClientAuthTransitionMode,
    val requestOrigins: Set<ExactOrigin>,
    val sourceUrls: Set<URI>,
    val requestPath: String,
    val fixedQueryParameters: Map<String, String>,
    val requiredEphemeralQueryParameters: Set<String>,
    val allowEmptyIssuerList: Boolean,
    val grantTtlSeconds: Int,
    val requestPort: Int = 443,
    val requestMethod: HttpMethod = HttpMethod.GET,
    val sourceFixedQueryParameters: Map<String, String> = emptyMap(),
    val sourceRequiredEphemeralQueryParameters: Set<String> = emptySet(),
    val linkedEphemeralQueryParameters: Set<String> = emptySet(),
    val linkedEphemeralQueryParameterMappings: Map<String, String> = emptyMap(),
    val sourceBase64UrlConstraints: Map<String, ClientAuthUrlConstraint> = emptyMap(),
    val returnUrlConstraints: Set<ClientAuthUrlConstraint> = emptySet(),
)

data class CertificateFilterRules(
    val allowedKeyAlgorithms: Set<String>,
    val requireDigitalSignatureKeyUsage: Boolean,
)

data class EvidenceReference(
    val url: URI,
    val reviewedOn: LocalDate,
)

data class SiteProfile(
    val profileId: ProfileId,
    val profileVersion: Int,
    val displayName: String,
    val compatibilityStatus: CompatibilityStatus,
    val activation: ProfileActivation,
    val startUrl: URI,
    val initiatorOrigins: Set<ExactOrigin>,
    val redirectOrigins: Set<ExactOrigin>,
    val trustedBrowseOrigins: Set<ExactOrigin>,
    val endpoints: Map<EndpointId, ProfileEndpoint>,
    val operationPolicies: Map<ProtocolOperation, OperationPolicy>,
    val capabilities: Set<Capability>,
    val clientAuthPolicy: ClientAuthPolicy?,
    val certificateRules: CertificateFilterRules,
    val evidence: List<EvidenceReference>,
)

data class SiteProfileCatalog(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val profiles: List<SiteProfile>,
)

enum class BuildTrustPolicy { RELEASE, QA }
