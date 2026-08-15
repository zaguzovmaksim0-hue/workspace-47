package dev.junta.firmamobile.browser

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ProfileActivation
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.SigningContext
import dev.junta.firmamobile.signing.SigningErrorCode
import java.io.StringReader
import java.time.Clock
import java.util.UUID
import org.json.JSONObject

class ValenciaCertificateSelectionBridgeAdapter(
    private val profileRegistry: SiteProfileRegistry = BuiltInSiteProfiles.runtimeRegistry,
    private val activeProfileId: () -> ProfileId? = { null },
    private val clock: Clock = Clock.systemUTC(),
) {
    fun route(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        navigationEpoch: Long = 0L,
        currentPageUrl: String? = null,
    ): CertificateSelectionBridgeRouteResult {
        if (rawMessage.length > MAX_MESSAGE_CHARS) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                null,
                SigningErrorCode.REQUEST_TOO_LARGE,
            )
        }
        val keys = rawMessage.uniqueTopLevelKeys()
            ?: return CertificateSelectionBridgeRouteResult.Rejected(
                null,
                SigningErrorCode.INVALID_REQUEST,
            )
        val json = runCatching { JSONObject(rawMessage) }.getOrNull()
            ?: return CertificateSelectionBridgeRouteResult.Rejected(
                null,
                SigningErrorCode.INVALID_REQUEST,
            )
        val type = json.strictString(TYPE_FIELD)
            ?: return CertificateSelectionBridgeRouteResult.Rejected(
                null,
                SigningErrorCode.INVALID_REQUEST,
            )
        if (type != SELECT_TYPE && type != CANCEL_TYPE) {
            return CertificateSelectionBridgeRouteResult.NotApplicable
        }
        val requestId = json.strictUuid(REQUEST_ID_FIELD)
        if (!isMainFrame) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.NAVIGATION_CHANGED,
            )
        }
        if (navigationEpoch < 0L || requestId == null) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        val navigationId = json.strictUuid(DOCUMENT_ID_FIELD)
            ?.let { NavigationId(it.toString()) }
            ?: return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        if (activeProfileId()?.value != PROFILE_ID) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.PROFILE_NOT_ACTIVE,
            )
        }
        if (!isExactOrigin(sourceOrigin)) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.ORIGIN_NOT_ALLOWED,
            )
        }
        if (currentPageUrl != START_URL) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.UNOBSERVED_CONTRACT,
            )
        }
        val profile = profileRegistry.profile(ProfileId(PROFILE_ID))
            ?: return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.PROFILE_NOT_ACTIVE,
            )
        val operation = profile.operationPolicies[ProtocolOperation.SELECT_CERTIFICATE]
            ?: return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.UNSUPPORTED_PROTOCOL,
            )
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(
            profile.profileId,
            ProtocolOperation.SELECT_CERTIFICATE,
        ) ?: return CertificateSelectionBridgeRouteResult.Rejected(
            requestId,
            SigningErrorCode.UNSUPPORTED_PROTOCOL,
        )
        if (!isExactProfileContract(
                profile,
                operation,
                binding.inputAdapterId.value,
                binding.callbackContractId.value,
                binding.signingProtocolId.value,
            )
        ) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.UNOBSERVED_CONTRACT,
            )
        }
        if (type == CANCEL_TYPE) {
            if (keys != CANCEL_KEYS) {
                return CertificateSelectionBridgeRouteResult.Rejected(
                    requestId,
                    SigningErrorCode.INVALID_REQUEST,
                )
            }
            return CertificateSelectionBridgeRouteResult.Cancelled(requestId, navigationId)
        }
        if (keys != SELECT_KEYS) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        val extraProperties = json.strictString(EXTRA_PROPERTIES_FIELD)
        if (extraProperties != EXTRA_PROPERTIES) {
            return CertificateSelectionBridgeRouteResult.Rejected(
                requestId,
                SigningErrorCode.INVALID_REQUEST,
            )
        }
        return CertificateSelectionBridgeRouteResult.Accepted(
            CertificateSelectionBridgeRequest(
                requestId = requestId,
                context = SigningContext(
                    profileId = PROFILE_ID,
                    profileVersion = PROFILE_VERSION,
                    origin = TrustedOrigin("https", HOST, 443),
                    navigationId = navigationId,
                    navigationEpoch = navigationEpoch,
                    observedAt = clock.instant(),
                ),
                pageUrl = START_URL,
                safeDescription = SAFE_DESCRIPTION,
                extraProperties = extraProperties,
            ),
        )
    }

    private fun isExactOrigin(uri: Uri): Boolean =
        !uri.isOpaque &&
            uri.scheme == "https" &&
            uri.host == HOST &&
            uri.port in setOf(-1, 443) &&
            uri.encodedUserInfo == null &&
            uri.path.isNullOrEmpty() &&
            uri.query == null &&
            uri.fragment == null

    private fun isExactProfileContract(
        profile: dev.junta.firmamobile.profile.SiteProfile,
        operation: dev.junta.firmamobile.profile.OperationPolicy,
        inputAdapterId: String,
        callbackContractId: String,
        protocolId: String,
    ): Boolean =
        profile.profileVersion == PROFILE_VERSION &&
            profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT &&
            profile.activation == ProfileActivation.QA_ONLY &&
            profile.startUrl.toASCIIString() == START_URL &&
            profile.initiatorOrigins.singleOrNull()?.serialized == ORIGIN &&
            profile.redirectOrigins.isEmpty() &&
            profile.trustedBrowseOrigins.isEmpty() &&
            profile.endpoints.isEmpty() &&
            profile.capabilities == setOf(Capability.SELECT_CERTIFICATE) &&
            profile.clientAuthPolicy == null &&
            profile.operationPolicies.keys == setOf(ProtocolOperation.SELECT_CERTIFICATE) &&
            operation.operation == ProtocolOperation.SELECT_CERTIFICATE &&
            operation.safeDescription == SAFE_DESCRIPTION &&
            operation.inputAdapterId.value == INPUT_ADAPTER_ID &&
            operation.callbackContractId.value == CALLBACK_CONTRACT_ID &&
            operation.capabilities == setOf(Capability.SELECT_CERTIFICATE) &&
            operation.endpointId == null &&
            operation.algorithms.isEmpty() && operation.format == null &&
            operation.packaging == null && operation.mode == null &&
            operation.fixedExtraProperties == mapOf(
                "filters" to "keyusage.nonrepudiation:true;nonexpired:true",
                "headless" to "true",
            ) &&
            operation.allowedExtraProperties.isEmpty() &&
            inputAdapterId == INPUT_ADAPTER_ID &&
            callbackContractId == CALLBACK_CONTRACT_ID &&
            protocolId == PROTOCOL_ID

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
        val raw = strictString(name) ?: return null
        if (!UUID_PATTERN.matches(raw)) return null
        return runCatching { UUID.fromString(raw) }
            .getOrNull()
            ?.takeIf { it.toString() == raw.lowercase() }
    }

    private fun JSONObject.strictString(name: String): String? = opt(name) as? String

    companion object {
        const val PROFILE_ID = "diputacion-valencia-sede"
        const val PROFILE_VERSION = 1
        const val ORIGIN = "https://portafirmas.dival.es"
        const val HOST = "portafirmas.dival.es"
        const val START_URL = "https://portafirmas.dival.es/signingpad/xhtml/login.xhtml"
        const val SAFE_DESCRIPTION =
            "Compartir certificado con el Portafirmas de la Diputació de València"
        const val EXTRA_PROPERTIES =
            "filters=keyusage.nonrepudiation:true;nonexpired:true\nheadless=true"
        const val INPUT_ADAPTER_ID = "autoscript-select-certificate-v1"
        const val CALLBACK_CONTRACT_ID = "autoscript-select-certificate-callback-v1"
        const val PROTOCOL_ID = "valencia-select-certificate-v1"
        private const val SELECT_TYPE = "MINIAPPLET_SELECT_CERTIFICATE"
        private const val CANCEL_TYPE = "MINIAPPLET_SELECT_CERTIFICATE_CANCEL"
        private const val TYPE_FIELD = "type"
        private const val DOCUMENT_ID_FIELD = "documentId"
        private const val REQUEST_ID_FIELD = "requestId"
        private const val EXTRA_PROPERTIES_FIELD = "extraProperties"
        private const val MAX_MESSAGE_CHARS = 16 * 1024
        private val SELECT_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
            EXTRA_PROPERTIES_FIELD,
        )
        private val CANCEL_KEYS = setOf(TYPE_FIELD, DOCUMENT_ID_FIELD, REQUEST_ID_FIELD)
        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-" +
                "[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
        )
    }
}
