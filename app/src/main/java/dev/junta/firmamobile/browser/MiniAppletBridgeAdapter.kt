package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.OperationPolicy
import dev.junta.firmamobile.profile.ProfileActivation
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SignatureAlgorithm
import dev.junta.firmamobile.profile.SignatureFormat
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.TrustMode
import dev.junta.firmamobile.security.MonotonicSecurityTime
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.DgtVerificationCadesAdapter
import dev.junta.firmamobile.signing.DiputacionLleidaCadesAdapter
import dev.junta.firmamobile.signing.JccmCertificateLoginProbeCadesAdapter
import dev.junta.firmamobile.signing.GranCanariaPadesAdapter
import dev.junta.firmamobile.signing.MiniAppletCallbackAdapter
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.NormalizedSignRequest
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningContext
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningFormat
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.signing.ProtocolAdapterRegistry
import dev.junta.firmamobile.signing.ProtocolInputAdapter
import dev.junta.firmamobile.signing.UgrCadesDetachedAdapter
import java.io.StringReader
import java.time.Clock
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

data class MiniAppletBridgeRequest(
    val normalized: NormalizedSignRequest,
)

sealed interface MiniAppletBridgeRouteResult {
    data object NotApplicable : MiniAppletBridgeRouteResult

    data class Accepted(val request: MiniAppletBridgeRequest) : MiniAppletBridgeRouteResult

    data class Cancelled(
        val requestId: UUID,
        val navigationId: NavigationId,
    ) : MiniAppletBridgeRouteResult

    data class Rejected(
        val requestId: UUID?,
        val code: SigningErrorCode,
    ) : MiniAppletBridgeRouteResult
}

class MiniAppletBridgeAdapter(
    clock: Clock = Clock.systemUTC(),
    monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    activeProfileId: () -> dev.junta.firmamobile.profile.ProfileId? = { null },
) {
    private val delegate = ProfileMiniAppletBridgeAdapter(
        clock = clock,
        monotonicNanos = monotonicNanos,
        activeProfileId = activeProfileId,
    )

    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        navigationEpoch: Long = 0L,
        currentPageUrl: String? = null,
    ): MiniAppletBridgeRouteResult = delegate.route(
        rawMessage = rawMessage,
        sourceOrigin = sourceOrigin,
        isMainFrame = isMainFrame,
        navigationEpoch = navigationEpoch,
        currentPageUrl = currentPageUrl,
    )

    companion object {
        const val MAX_DECODED_DATA_BYTES = ProfileMiniAppletBridgeAdapter.MAX_DECODED_DATA_BYTES
        const val MAX_DATA_BASE64_CHARS = ProfileMiniAppletBridgeAdapter.MAX_DATA_BASE64_CHARS
        const val MAX_EXTRA_PROPERTIES_CHARS = ProfileMiniAppletBridgeAdapter.MAX_EXTRA_PROPERTIES_CHARS
        const val MAX_MESSAGE_CHARS = ProfileMiniAppletBridgeAdapter.MAX_MESSAGE_CHARS
    }
}

internal class ProfileMiniAppletBridgeAdapter(
    private val clock: Clock = Clock.systemUTC(),
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val profileRegistry: dev.junta.firmamobile.profile.SiteProfileRegistry =
        BuiltInSiteProfiles.runtimeRegistry,
    private val adapterRegistry: ProtocolAdapterRegistry = BuiltInProtocolAdapterRegistry.registry,
    private val activeProfileId: () -> dev.junta.firmamobile.profile.ProfileId? = { null },
) : ProtocolInputAdapter {
    override val id = dev.junta.firmamobile.profile.ProtocolInputAdapterId("miniapplet-autoscript-v1")

    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        navigationEpoch: Long = 0L,
        currentPageUrl: String? = null,
    ): MiniAppletBridgeRouteResult {
        if (navigationEpoch < 0L) return MiniAppletBridgeRouteResult.NotApplicable
        if (rawMessage.length > WebMessageProtocol.MAX_MESSAGE_CHARS) {
            return MiniAppletBridgeRouteResult.NotApplicable
        }
        val streamedKeys = rawMessage.uniqueTopLevelKeys()
        val json = try {
            JSONObject(rawMessage)
        } catch (_: Exception) {
            return MiniAppletBridgeRouteResult.NotApplicable
        }
        val messageType = json.optString(TYPE_FIELD)
        if (messageType != TYPE_MINIAPPLET_SIGN && messageType != TYPE_MINIAPPLET_CANCEL) {
            return MiniAppletBridgeRouteResult.NotApplicable
        }
        if (rawMessage.length > MAX_MESSAGE_CHARS) {
            return MiniAppletBridgeRouteResult.Rejected(null, SigningErrorCode.REQUEST_TOO_LARGE)
        }
        val requestId = json.strictUuid(REQUEST_ID_FIELD)
        val requiredKeys = if (messageType == TYPE_MINIAPPLET_CANCEL) {
            CANCEL_KEYS
        } else {
            SIGN_KEYS
        }
        if (streamedKeys == null || json.keySet() != streamedKeys ||
            streamedKeys != requiredKeys
        ) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST)
        }
        if (!isMainFrame) {
            return MiniAppletBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.NAVIGATION_CHANGED,
            )
        }
        val resolved = profileRegistry.resolve(sourceOrigin)
            ?.takeIf { it.trustMode == TrustMode.TRUSTED_SIGNING }
            ?: return MiniAppletBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.ORIGIN_NOT_ALLOWED,
            )
        val profile = resolved.profile
        val activeProfile = activeProfileId()
            ?: return MiniAppletBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.ORIGIN_NOT_ALLOWED,
            )
        if (profile.profileId != activeProfile) {
            return MiniAppletBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.ORIGIN_NOT_ALLOWED,
            )
        }
        val operation = profile.operationPolicies[ProtocolOperation.SIGN]
            ?: return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        val binding = adapterRegistry.resolve(profile.profileId, ProtocolOperation.SIGN)
            ?.takeIf {
                it.inputAdapterId == id &&
                it.inputAdapterId == operation.inputAdapterId &&
                    it.callbackContractId == operation.callbackContractId
            }
            ?: return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        val isUgrContract = isExactUgrContract(
            profile = profile,
            origin = resolved.origin,
            operation = operation,
            signingProtocolId = binding.signingProtocolId.value,
        )
        if (profile.profileId.value == UgrCadesDetachedAdapter.PROFILE_ID && !isUgrContract) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val isCantabriaContract = isExactCantabriaContract(
            profile = profile,
            origin = resolved.origin,
            operation = operation,
            signingProtocolId = binding.signingProtocolId.value,
        )
        if (profile.profileId.value == CANTABRIA_PROFILE_ID && !isCantabriaContract) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val isJccmContract = isExactJccmContract(
            profile = profile,
            origin = resolved.origin,
            operation = operation,
            signingProtocolId = binding.signingProtocolId.value,
            currentPageUrl = currentPageUrl,
        )
        if (profile.profileId.value == JccmCertificateLoginProbeCadesAdapter.PROFILE_ID &&
            !isJccmContract
        ) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val isGranCanariaContract = isExactGranCanariaContract(
            profile = profile,
            origin = resolved.origin,
            operation = operation,
            signingProtocolId = binding.signingProtocolId.value,
            currentPageUrl = currentPageUrl,
        )
        if (profile.profileId.value == GranCanariaPadesAdapter.PROFILE_ID && !isGranCanariaContract) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val isSevillaAtseContract = isExactSevillaAtseContract(
            profile = profile,
            origin = resolved.origin,
            operation = operation,
            signingProtocolId = binding.signingProtocolId.value,
            currentPageUrl = currentPageUrl,
        )
        if (profile.profileId.value == SEVILLA_ATSE_PROFILE_ID && !isSevillaAtseContract) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val isPoliciaContract = isExactPoliciaContract(
            profile = profile,
            origin = resolved.origin,
            operation = operation,
            signingProtocolId = binding.signingProtocolId.value,
            currentPageUrl = currentPageUrl,
        )
        if (profile.profileId.value == POLICIA_PROFILE_ID && !isPoliciaContract) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val isDiputacionLleidaContract = isExactDiputacionLleidaContract(
            profile = profile,
            origin = resolved.origin,
            operation = operation,
            signingProtocolId = binding.signingProtocolId.value,
            currentPageUrl = currentPageUrl,
        )
        if (profile.profileId.value == DiputacionLleidaCadesAdapter.PROFILE_ID &&
            !isDiputacionLleidaContract
        ) {
            return MiniAppletBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val canonicalRequestId = requestId
            ?: return MiniAppletBridgeRouteResult.Rejected(null, SigningErrorCode.INVALID_REQUEST)
        val documentId = json.strictUuid(DOCUMENT_ID_FIELD)
            ?: return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        val navigationId = NavigationId(documentId.toString())
        if (messageType == TYPE_MINIAPPLET_CANCEL) {
            return MiniAppletBridgeRouteResult.Cancelled(canonicalRequestId, navigationId)
        }
        val algorithm = when (json.strictString(ALGORITHM_FIELD)) {
            ALGORITHM_SHA1_RSA -> SigningAlgorithm.SHA1_WITH_RSA to SignatureAlgorithm.SHA1_WITH_RSA
            ALGORITHM_SHA256_RSA -> SigningAlgorithm.SHA256_WITH_RSA to SignatureAlgorithm.SHA256_WITH_RSA
            ALGORITHM_SHA512_RSA -> SigningAlgorithm.SHA512_WITH_RSA to SignatureAlgorithm.SHA512_WITH_RSA
            else -> return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        val format = when (json.strictString(FORMAT_FIELD)) {
            FORMAT_CADES -> SigningFormat.CADES to SignatureFormat.CADES
            FORMAT_PADES -> if (isGranCanariaContract) {
                SigningFormat.PADES to SignatureFormat.PADES
            } else {
                null
            }
            FORMAT_XADES_DETACHED -> if (isSevillaAtseContract || isPoliciaContract) {
                null
            } else {
                SigningFormat.XADES to SignatureFormat.XADES
            }
            FORMAT_XADES -> if (isSevillaAtseContract || isPoliciaContract) {
                SigningFormat.XADES to SignatureFormat.XADES
            } else {
                null
            }
            else -> null
        }
        if (algorithm.second !in operation.algorithms || format == null ||
            format.second != operation.format
        ) {
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        val dataBase64 = json.strictString(DATA_FIELD)
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_DATA_BASE64_CHARS }
            ?: return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.REQUEST_TOO_LARGE,
            )
        if (!BASE64_PATTERN.matches(dataBase64)) {
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        val rawExtraProperties = if (isUgrContract) {
            json.strictString(EXTRA_PROPERTIES_FIELD)
                ?.takeIf { it.isEmpty() }
                ?: return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
        } else if (isCantabriaContract) {
            json.strictString(EXTRA_PROPERTIES_FIELD)
                ?.takeIf { it == CANTABRIA_EXTRA_PROPERTIES }
                ?: return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
        } else if (isJccmContract) {
            val value = json.opt(EXTRA_PROPERTIES_FIELD)
            if (value !== JSONObject.NULL && !(value is String && value.isEmpty())) {
                return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
            }
            ""
        } else if (isGranCanariaContract) {
            json.strictString(EXTRA_PROPERTIES_FIELD)
                ?.takeIf { it == GranCanariaPadesAdapter.EXPECTED_EXTRA_PROPERTIES }
                ?: return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
        } else if (isSevillaAtseContract) {
            if (json.opt(EXTRA_PROPERTIES_FIELD) !== JSONObject.NULL) {
                return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
            }
            ""
        } else if (isPoliciaContract) {
            val raw = json.strictString(EXTRA_PROPERTIES_FIELD)
                ?: return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
            if (raw.length > MAX_EXTRA_PROPERTIES_CHARS || !raw.hasSafeControls()) {
                return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
            }
            canonicalExtraProperties(raw, operation.fixedExtraProperties)
                ?: return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
        } else if (operation.fixedExtraProperties.isEmpty()) {
            if (json.opt(EXTRA_PROPERTIES_FIELD) !== JSONObject.NULL) {
                return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
            }
            ""
        } else {
            json.strictString(EXTRA_PROPERTIES_FIELD)
                ?.takeIf { it.length <= MAX_EXTRA_PROPERTIES_CHARS && it.hasSafeControls() }
                ?: return MiniAppletBridgeRouteResult.Rejected(
                    canonicalRequestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
        }
        val decodedData = try {
            Base64.getDecoder().decode(dataBase64)
        } catch (_: IllegalArgumentException) {
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        if (decodedData.size > MAX_DECODED_DATA_BYTES) {
            decodedData.fill(0)
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.REQUEST_TOO_LARGE,
            )
        }
        if (binding.signingProtocolId == DgtVerificationCadesAdapter.ID &&
            !decodedData.contentEquals(DgtVerificationCadesAdapter.EXPECTED_PAYLOAD)
        ) {
            decodedData.fill(0)
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        if (isUgrContract && !decodedData.contentEquals(UGR_PAYLOAD)) {
            decodedData.fill(0)
            return MiniAppletBridgeRouteResult.Rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        }
        if (isCantabriaContract && !decodedData.isExactCantabriaChallenge()) {
            decodedData.fill(0)
            return MiniAppletBridgeRouteResult.Rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
        }
        if (isJccmContract &&
            !decodedData.contentEquals(JccmCertificateLoginProbeCadesAdapter.EXPECTED_PAYLOAD)
        ) {
            decodedData.fill(0)
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        if (isSevillaAtseContract && !decodedData.isExactSevillaAtseChallenge()) {
            decodedData.fill(0)
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        if (isDiputacionLleidaContract && !decodedData.isExactDiputacionLleidaChallenge()) {
            decodedData.fill(0)
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        val extraProperties = if (isCantabriaContract) {
            CANTABRIA_EXTRA_PROPERTIES
        } else if (isGranCanariaContract) {
            GranCanariaPadesAdapter.EXPECTED_EXTRA_PROPERTIES
        } else if (operation.fixedExtraProperties.isEmpty()) {
            ""
        } else canonicalExtraProperties(rawExtraProperties, operation.fixedExtraProperties)
            ?: run {
                decodedData.fill(0)
                return MiniAppletBridgeRouteResult.Rejected(canonicalRequestId, SigningErrorCode.INVALID_REQUEST)
            }
        val payload = try {
            MiniAppletPayloadCodec.encode(decodedData, extraProperties)
        } catch (_: IllegalArgumentException) {
            return MiniAppletBridgeRouteResult.Rejected(
                canonicalRequestId,
                SigningErrorCode.REQUEST_TOO_LARGE,
            )
        } finally {
            decodedData.fill(0)
        }
        return MiniAppletBridgeRouteResult.Accepted(
            MiniAppletBridgeRequest(
                normalized = NormalizedSignRequest(
                    requestId = canonicalRequestId,
                    protocolId = binding.signingProtocolId,
                    context = SigningContext(
                        profileId = profile.profileId.value,
                        profileVersion = profile.profileVersion,
                        origin = resolved.origin.toTrustedOrigin(),
                        navigationId = navigationId,
                        navigationEpoch = navigationEpoch,
                        observedAt = clock.instant(),
                        pageUrl = currentPageUrl,
                    ),
                    algorithm = algorithm.first,
                    format = format.first,
                    safeDescription = operation.safeDescription,
                    payload = payload,
                    observedAtMonotonicNanos = monotonicNanos(),
                ),
            ),
        )
    }

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

    private fun JSONObject.strictUuid(name: String): UUID? {
        val value = strictString(name) ?: return null
        if (!UUID_PATTERN.matches(value)) return null
        return try {
            UUID.fromString(value).takeIf { it.toString() == value.lowercase() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun JSONObject.strictString(name: String): String? = opt(name) as? String

    private fun JSONObject.keySet(): Set<String> = buildSet {
        val keys = keys()
        while (keys.hasNext()) add(keys.next())
    }

    private fun String.hasSafeControls(): Boolean = all { character ->
        !character.isISOControl() || character == '\n' || character == '\r' || character == '\t'
    }
    private fun isExactJccmContract(
        profile: SiteProfile,
        origin: ExactOrigin,
        operation: OperationPolicy,
        signingProtocolId: String,
        currentPageUrl: String?,
    ): Boolean =
        currentPageUrl == JCCM_START_URL &&
            profile.profileId.value == JccmCertificateLoginProbeCadesAdapter.PROFILE_ID &&
            profile.profileVersion == JccmCertificateLoginProbeCadesAdapter.PROFILE_VERSION &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == JCCM_START_URL &&
            origin.serialized == JccmCertificateLoginProbeCadesAdapter.INITIATOR_ORIGIN &&
            profile.initiatorOrigins == setOf(
                ExactOrigin.parse(JccmCertificateLoginProbeCadesAdapter.INITIATOR_ORIGIN),
            ) &&
            profile.redirectOrigins.isEmpty() &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() &&
            profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1) &&
            profile.clientAuthPolicy == null &&
            profile.certificateRules.allowedKeyAlgorithms == setOf("RSA") &&
            profile.certificateRules.requireDigitalSignatureKeyUsage &&
            profile.operationPolicies.size == 1 &&
            operation.operation == ProtocolOperation.SIGN &&
            operation.safeDescription == JccmCertificateLoginProbeCadesAdapter.SAFE_DESCRIPTION &&
            operation.inputAdapterId.value == "miniapplet-autoscript-v1" &&
            operation.callbackContractId.value == "miniapplet-sign-callback-v1" &&
            operation.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1) &&
            operation.endpointId == null &&
            operation.algorithms == setOf(SignatureAlgorithm.SHA1_WITH_RSA) &&
            operation.format == SignatureFormat.CADES &&
            operation.packaging == dev.junta.firmamobile.profile.SignaturePackaging.DETACHED &&
            operation.mode == dev.junta.firmamobile.profile.SignatureMode.EXPLICIT &&
            operation.fixedExtraProperties.isEmpty() &&
            operation.allowedExtraProperties.isEmpty() &&
            signingProtocolId == JccmCertificateLoginProbeCadesAdapter.ID.value


    private fun isExactGranCanariaContract(
        profile: SiteProfile,
        origin: ExactOrigin,
        operation: OperationPolicy,
        signingProtocolId: String,
        currentPageUrl: String?,
    ): Boolean =
        currentPageUrl == GranCanariaPadesAdapter.SIGNING_PAGE_URL &&
            profile.profileId.value == GranCanariaPadesAdapter.PROFILE_ID &&
            profile.profileVersion == GranCanariaPadesAdapter.PROFILE_VERSION &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == GranCanariaPadesAdapter.PUBLIC_START_URL &&
            origin.serialized == GranCanariaPadesAdapter.INITIATOR_ORIGIN &&
            profile.initiatorOrigins == setOf(ExactOrigin.parse(GranCanariaPadesAdapter.INITIATOR_ORIGIN)) &&
            profile.redirectOrigins.isEmpty() &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() &&
            profile.capabilities == setOf(Capability.SIGN) &&
            profile.clientAuthPolicy == null &&
            profile.certificateRules.allowedKeyAlgorithms == setOf("RSA") &&
            !profile.certificateRules.requireDigitalSignatureKeyUsage &&
            profile.operationPolicies.size == 1 &&
            operation.operation == ProtocolOperation.SIGN &&
            operation.safeDescription == GranCanariaPadesAdapter.SAFE_DESCRIPTION &&
            operation.inputAdapterId.value == "miniapplet-autoscript-v1" &&
            operation.callbackContractId.value == "miniapplet-sign-callback-v1" &&
            operation.capabilities == setOf(Capability.SIGN) &&
            operation.endpointId == null &&
            operation.algorithms == setOf(SignatureAlgorithm.SHA512_WITH_RSA) &&
            operation.format == SignatureFormat.PADES &&
            operation.packaging == dev.junta.firmamobile.profile.SignaturePackaging.ATTACHED &&
            operation.mode == null &&
            operation.fixedExtraProperties == GRAN_CANARIA_FIXED_EXTRA_PROPERTIES &&
            operation.allowedExtraProperties.isEmpty() &&
            signingProtocolId == GranCanariaPadesAdapter.ID.value

    private fun isExactSevillaAtseContract(
        profile: SiteProfile,
        origin: ExactOrigin,
        operation: OperationPolicy,
        signingProtocolId: String,
        currentPageUrl: String?,
    ): Boolean =
        currentPageUrl == SEVILLA_ATSE_START_URL &&
            profile.profileId.value == SEVILLA_ATSE_PROFILE_ID &&
            profile.profileVersion == SEVILLA_ATSE_PROFILE_VERSION &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == SEVILLA_ATSE_START_URL &&
            origin.serialized == SEVILLA_ATSE_ORIGIN &&
            profile.initiatorOrigins == setOf(ExactOrigin.parse(SEVILLA_ATSE_ORIGIN)) &&
            profile.redirectOrigins.isEmpty() &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() &&
            profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1) &&
            profile.clientAuthPolicy == null &&
            profile.certificateRules.allowedKeyAlgorithms == setOf("RSA") &&
            profile.certificateRules.requireDigitalSignatureKeyUsage &&
            profile.operationPolicies.size == 1 &&
            operation.operation == ProtocolOperation.SIGN &&
            operation.safeDescription == SEVILLA_ATSE_SAFE_DESCRIPTION &&
            operation.inputAdapterId.value == "miniapplet-autoscript-v1" &&
            operation.callbackContractId.value == "autoscript-sign-callback-v1" &&
            operation.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1) &&
            operation.endpointId == null &&
            operation.algorithms == setOf(SignatureAlgorithm.SHA1_WITH_RSA) &&
            operation.format == SignatureFormat.XADES &&
            operation.packaging == dev.junta.firmamobile.profile.SignaturePackaging.ATTACHED &&
            operation.mode == null &&
            operation.fixedExtraProperties.isEmpty() &&
            operation.allowedExtraProperties.isEmpty() &&
            signingProtocolId == SEVILLA_ATSE_PROTOCOL_ID

    private fun isExactPoliciaContract(
        profile: SiteProfile,
        origin: ExactOrigin,
        operation: OperationPolicy,
        signingProtocolId: String,
        currentPageUrl: String?,
    ): Boolean =
        profile.profileId.value == POLICIA_PROFILE_ID &&
            profile.profileVersion == POLICIA_PROFILE_VERSION &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == POLICIA_START_URL &&
            origin.serialized == POLICIA_ORIGIN &&
            currentPageUrl == POLICIA_PROCEDURE_PAGE &&
            profile.initiatorOrigins == setOf(ExactOrigin.parse(POLICIA_ORIGIN)) &&
            profile.redirectOrigins.isEmpty() &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() &&
            profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1) &&
            profile.clientAuthPolicy == null &&
            profile.certificateRules.allowedKeyAlgorithms == setOf("RSA") &&
            !profile.certificateRules.requireDigitalSignatureKeyUsage &&
            profile.operationPolicies.size == 1 &&
            operation.operation == ProtocolOperation.SIGN &&
            operation.safeDescription == POLICIA_SAFE_DESCRIPTION &&
            operation.inputAdapterId.value == "miniapplet-autoscript-v1" &&
            operation.callbackContractId.value == "autoscript-sign-callback-v1" &&
            operation.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1) &&
            operation.endpointId == null &&
            operation.algorithms == setOf(SignatureAlgorithm.SHA1_WITH_RSA) &&
            operation.format == SignatureFormat.XADES &&
            operation.packaging == dev.junta.firmamobile.profile.SignaturePackaging.DETACHED &&
            operation.mode == null &&
            operation.fixedExtraProperties == POLICIA_FIXED_EXTRA_PROPERTIES &&
            operation.allowedExtraProperties.isEmpty() &&
            signingProtocolId == POLICIA_PROTOCOL_ID

    private fun ByteArray.isExactSevillaAtseChallenge(): Boolean =
        size == SEVILLA_ATSE_CHALLENGE_BYTES && all { byte ->
            val value = byte.toInt() and 0xff
            value in 0x30..0x39 ||
                value in 0x41..0x5a ||
                value in 0x61..0x7a ||
                value == 0x5f ||
                value == 0x2d
        }

    private fun isExactUgrContract(
        profile: SiteProfile,
        origin: ExactOrigin,
        operation: OperationPolicy,
        signingProtocolId: String,
    ): Boolean =
        profile.profileId.value == UgrCadesDetachedAdapter.PROFILE_ID &&
            profile.profileVersion == UgrCadesDetachedAdapter.PROFILE_VERSION &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == UGR_START_URL &&
            origin.serialized == UgrCadesDetachedAdapter.INITIATOR_ORIGIN &&
            profile.initiatorOrigins == setOf(ExactOrigin.parse(UgrCadesDetachedAdapter.INITIATOR_ORIGIN)) &&
            profile.redirectOrigins.isEmpty() &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() &&
            profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1) &&
            profile.clientAuthPolicy == null &&
            profile.certificateRules.allowedKeyAlgorithms == setOf("RSA") &&
            profile.certificateRules.requireDigitalSignatureKeyUsage &&
            profile.operationPolicies.size == 1 &&
            operation.operation == ProtocolOperation.SIGN &&
            operation.safeDescription == UGR_SAFE_DESCRIPTION &&
            operation.inputAdapterId.value == "miniapplet-autoscript-v1" &&
            operation.callbackContractId.value == "miniapplet-sign-callback-v1" &&
            operation.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1) &&
            operation.endpointId == null &&
            operation.algorithms == setOf(SignatureAlgorithm.SHA1_WITH_RSA) &&
            operation.format == SignatureFormat.CADES &&
            operation.packaging == dev.junta.firmamobile.profile.SignaturePackaging.DETACHED &&
            operation.mode == dev.junta.firmamobile.profile.SignatureMode.EXPLICIT &&
            operation.fixedExtraProperties.isEmpty() &&
            operation.allowedExtraProperties.isEmpty() &&
            signingProtocolId == UgrCadesDetachedAdapter.ID.value

    private fun isExactCantabriaContract(
        profile: SiteProfile,
        origin: ExactOrigin,
        operation: OperationPolicy,
        signingProtocolId: String,
    ): Boolean =
        profile.profileId.value == CANTABRIA_PROFILE_ID &&
            profile.profileVersion == CANTABRIA_PROFILE_VERSION &&
            profile.displayName == CANTABRIA_DISPLAY_NAME &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == CANTABRIA_START_URL &&
            origin.serialized == CANTABRIA_ORIGIN &&
            profile.initiatorOrigins == setOf(ExactOrigin.parse(CANTABRIA_ORIGIN)) &&
            profile.redirectOrigins.isEmpty() &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() &&
            profile.capabilities == setOf(Capability.SIGN) &&
            profile.clientAuthPolicy == null &&
            profile.certificateRules.allowedKeyAlgorithms == setOf("RSA") &&
            profile.certificateRules.requireDigitalSignatureKeyUsage &&
            profile.operationPolicies.size == 1 &&
            operation.operation == ProtocolOperation.SIGN &&
            operation.safeDescription == CANTABRIA_SAFE_DESCRIPTION &&
            operation.inputAdapterId.value == "miniapplet-autoscript-v1" &&
            operation.callbackContractId.value == "miniapplet-sign-callback-v1" &&
            operation.capabilities == setOf(Capability.SIGN) &&
            operation.endpointId == null &&
            operation.algorithms == setOf(SignatureAlgorithm.SHA512_WITH_RSA) &&
            operation.format == SignatureFormat.CADES &&
            operation.packaging == dev.junta.firmamobile.profile.SignaturePackaging.DETACHED &&
            operation.mode == dev.junta.firmamobile.profile.SignatureMode.IMPLICIT &&
            operation.fixedExtraProperties == CANTABRIA_FIXED_EXTRA_PROPERTIES &&
            operation.allowedExtraProperties.isEmpty() &&
            signingProtocolId == CANTABRIA_PROTOCOL_ID

    private fun ByteArray.isExactCantabriaChallenge(): Boolean =
        size == CANTABRIA_CHALLENGE_BYTES && all { byte ->
            byte.toInt() in 0x30..0x39 ||
                byte.toInt() in 0x61..0x66
        }

    private fun isExactDiputacionLleidaContract(
        profile: SiteProfile,
        origin: ExactOrigin,
        operation: OperationPolicy,
        signingProtocolId: String,
        currentPageUrl: String?,
    ): Boolean =
        currentPageUrl == LLEIDA_LOGIN_PAGE_URL &&
            profile.profileId.value == DiputacionLleidaCadesAdapter.PROFILE_ID &&
            profile.profileVersion == DiputacionLleidaCadesAdapter.PROFILE_VERSION &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == LLEIDA_START_URL &&
            origin.serialized == DiputacionLleidaCadesAdapter.INITIATOR_ORIGIN &&
            profile.initiatorOrigins == setOf(ExactOrigin.parse(DiputacionLleidaCadesAdapter.INITIATOR_ORIGIN)) &&
            profile.redirectOrigins.isEmpty() &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() &&
            profile.capabilities == setOf(Capability.SIGN) &&
            profile.clientAuthPolicy == null &&
            profile.certificateRules.allowedKeyAlgorithms == setOf("RSA") &&
            profile.certificateRules.requireDigitalSignatureKeyUsage &&
            profile.operationPolicies.size == 1 &&
            operation.operation == ProtocolOperation.SIGN &&
            operation.safeDescription == DiputacionLleidaCadesAdapter.SAFE_DESCRIPTION &&
            operation.inputAdapterId.value == "miniapplet-autoscript-v1" &&
            operation.callbackContractId.value == "miniapplet-sign-callback-v1" &&
            operation.capabilities == setOf(Capability.SIGN) &&
            operation.endpointId == null &&
            operation.algorithms == setOf(SignatureAlgorithm.SHA256_WITH_RSA) &&
            operation.format == SignatureFormat.CADES &&
            operation.packaging == dev.junta.firmamobile.profile.SignaturePackaging.DETACHED &&
            operation.mode == dev.junta.firmamobile.profile.SignatureMode.EXPLICIT &&
            operation.fixedExtraProperties == LLEIDA_FIXED_EXTRA_PROPERTIES &&
            operation.allowedExtraProperties.isEmpty() &&
            signingProtocolId == DiputacionLleidaCadesAdapter.ID.value

    private fun ByteArray.isExactDiputacionLleidaChallenge(): Boolean =
        isNotEmpty() && size <= MAX_LLEIDA_CHALLENGE_BYTES && all { byte ->
            val value = byte.toInt() and 0xff
            value in 0x20..0x7e
        }

    private fun canonicalExtraProperties(raw: String, fixed: Map<String, String>): String? {
        val observed = linkedMapOf<String, String>()
        val lines = raw.split('\n')
        if (lines.isEmpty() || lines.size > MAX_EXTRA_PROPERTY_COUNT) return null
        for (rawLine in lines) {
            val line = rawLine.removeSuffix("\r")
            if (line.isEmpty()) return null
            val separator = line.indexOf('=')
            if (separator <= 0) return null
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (!PROPERTY_KEY.matches(key) || value.length > MAX_EXTRA_PROPERTY_VALUE_CHARS ||
                observed.put(key, value) != null
            ) return null
        }
        if (observed != fixed) return null
        return fixed.entries.joinToString("\n") { (key, value) -> "$key=$value" }
    }

    companion object {
        const val MAX_DECODED_DATA_BYTES = 524_288
        const val MAX_DATA_BASE64_CHARS = 699_052
        const val MAX_EXTRA_PROPERTIES_CHARS = 65_536
        const val MAX_MESSAGE_CHARS = 786_432
        private const val TYPE_FIELD = "type"
        private const val DOCUMENT_ID_FIELD = "documentId"
        private const val REQUEST_ID_FIELD = "requestId"
        private const val DATA_FIELD = "dataB64"
        private const val ALGORITHM_FIELD = "algorithm"
        private const val FORMAT_FIELD = "format"
        private const val EXTRA_PROPERTIES_FIELD = "extraProperties"
        private const val TYPE_MINIAPPLET_SIGN = "MINIAPPLET_SIGN"
        private const val TYPE_MINIAPPLET_CANCEL = "MINIAPPLET_CANCEL"
        private const val ALGORITHM_SHA1_RSA = "SHA1withRSA"
        private const val ALGORITHM_SHA256_RSA = "SHA256withRSA"
        private const val ALGORITHM_SHA512_RSA = "SHA512withRSA"
        private const val FORMAT_CADES = "CAdES"
        private const val FORMAT_PADES = "PAdES"
        private const val FORMAT_XADES_DETACHED = "XAdES Detached"
        private const val FORMAT_XADES = "XAdES"
        private const val SEVILLA_ATSE_PROFILE_ID = "sevilla-atse-certificate-login"
        private const val SEVILLA_ATSE_PROFILE_VERSION = 1
        private const val SEVILLA_ATSE_START_URL =
            "https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"
        private const val SEVILLA_ATSE_ORIGIN = "https://www.sevilla.org"
        private const val SEVILLA_ATSE_SAFE_DESCRIPTION =
            "Acceso con certificado a la Agencia Tributaria de Sevilla"
        private const val SEVILLA_ATSE_PROTOCOL_ID = "sevilla-atse-xades-enveloping-v1"
        private const val SEVILLA_ATSE_CHALLENGE_BYTES = 40
        private const val POLICIA_PROFILE_ID = "policia-solicitud-generica"
        private const val POLICIA_PROFILE_VERSION = 1
        private const val POLICIA_START_URL = "https://sede.policia.gob.es/"
        private const val POLICIA_PROCEDURE_PAGE =
            "https://sede.policia.gob.es/portalCiudadano/_es/solicitudGenerica.xhtml"
        private const val POLICIA_ORIGIN = "https://sede.policia.gob.es"
        private const val POLICIA_SAFE_DESCRIPTION =
            "Firma de solicitud en la Sede de la Policía Nacional"
        private const val POLICIA_PROTOCOL_ID = "policia-xades-detached-v1"
        private val POLICIA_FIXED_EXTRA_PROPERTIES = linkedMapOf(
            "format" to "XAdES Detached",
            "filters.1" to "dnie:;nonexpired:",
            "filters.2" to "keyusage.nonrepudiation:true;nonexpired:",
        )
        private const val UGR_START_URL = "https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp"
        private const val LLEIDA_LOGIN_PAGE_URL =
            "https://seu.diputaciolleida.cat/portal/entidades.do?ent_id=1&idioma=2"
        private const val JCCM_START_URL =
            "https://ventanillaelectronica.jccm.es/administracion_electronica/" +
                "formularios/identificacion.phtml"
        private const val UGR_SAFE_DESCRIPTION = "Acceso con certificado a la Universidad de Granada"
        private val UGR_PAYLOAD = "Universidad de Granada".encodeToByteArray()
        private val GRAN_CANARIA_FIXED_EXTRA_PROPERTIES = linkedMapOf(
            "headless" to "true",
            "filters" to "nonexpired:",
        )
        private const val CANTABRIA_PROFILE_ID = "cantabria-rec-cert-login"
        private const val CANTABRIA_PROFILE_VERSION = 1
        private const val CANTABRIA_DISPLAY_NAME =
            "Registro Electrónico Común de Cantabria — Acceso con certificado"
        private const val CANTABRIA_START_URL = "https://rec.cantabria.es/rec/bienvenida.htm"
        private const val CANTABRIA_ORIGIN = "https://rec.cantabria.es"
        private const val CANTABRIA_SAFE_DESCRIPTION =
            "Acceso con certificado al Registro Electrónico Común de Cantabria"
        private const val CANTABRIA_PROTOCOL_ID = "cantabria-rec-cert-login-cades-v1"
        private const val CANTABRIA_EXTRA_PROPERTIES = "filters=\nmode=implicit"
        private val CANTABRIA_FIXED_EXTRA_PROPERTIES = linkedMapOf(
            "filters" to "",
            "mode" to "implicit",
        )
        private const val CANTABRIA_CHALLENGE_BYTES = 40
        private const val LLEIDA_START_URL = "https://seu.diputaciolleida.cat"
        private const val MAX_LLEIDA_CHALLENGE_BYTES = 512
        private val LLEIDA_FIXED_EXTRA_PROPERTIES = linkedMapOf(
            "policy" to "FirmaAGE",
            "headless" to "true",
            "filters" to "nonexpired:true;authCert:true",
        )
        private const val MAX_EXTRA_PROPERTY_COUNT = 32
        private const val MAX_EXTRA_PROPERTY_VALUE_CHARS = 2_048
        private val PROPERTY_KEY = Regex("[A-Za-z][A-Za-z0-9._-]{0,63}")
        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
        private val BASE64_PATTERN = Regex(
            "(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?",
        )
        private val SIGN_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
            DATA_FIELD,
            ALGORITHM_FIELD,
            FORMAT_FIELD,
            EXTRA_PROPERTIES_FIELD,
        )
        private val CANCEL_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
        )
    }
}

class MiniAppletReplyChannel internal constructor(
    override val requestId: UUID,
    private val postMessage: (String) -> Unit,
    private val onTerminal: () -> Unit = {},
    private val canDeliver: () -> Boolean = { true },
    private val encoder: dev.junta.firmamobile.signing.SigningResultEncoder =
        MiniAppletCallbackAdapter(),
) : SigningReplySink {
    private val terminal = AtomicBoolean(false)

    override fun success(signature: LocalSignature, certificateDer: ByteArray): Boolean {
        if (!terminal.compareAndSet(false, true)) {
            signature.close()
            certificateDer.fill(0)
            return false
        }
        if (!runCatching(canDeliver).getOrDefault(false)) {
            signature.close()
            certificateDer.fill(0)
            onTerminal()
            return false
        }
        return try {
            val signatureBase64 = signature.use { ownedSignature ->
                ownedSignature.withBytes { bytes ->
                    require(bytes.size <= MAX_SIGNATURE_BYTES)
                    Base64.getEncoder().encodeToString(bytes)
                }
            }
            require(certificateDer.size <= MAX_CERTIFICATE_BYTES)
            val certificateBase64 = Base64.getEncoder().encodeToString(certificateDer)
            postMessage(encoder.encodeSuccess(requestId, signatureBase64, certificateBase64))
            true
        } catch (_: Exception) {
            false
        } finally {
            signature.close()
            certificateDer.fill(0)
            onTerminal()
        }
    }

    override fun failure(code: SigningErrorCode): Boolean {
        if (!terminal.compareAndSet(false, true)) return false
        if (!runCatching(canDeliver).getOrDefault(false)) {
            onTerminal()
            return false
        }
        return try {
            postMessage(encoder.encodeError(requestId, code))
            true
        } catch (_: Exception) {
            false
        } finally {
            onTerminal()
        }
    }

    override fun abandon(): Boolean {
        if (!terminal.compareAndSet(false, true)) return false
        onTerminal()
        return true
    }

    private companion object {
        const val MAX_SIGNATURE_BYTES = 2_097_152
        const val MAX_CERTIFICATE_BYTES = 65_536
    }
}
