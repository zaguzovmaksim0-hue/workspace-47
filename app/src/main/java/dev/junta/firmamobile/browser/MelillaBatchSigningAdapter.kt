package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SignatureAlgorithm as ProfileSignatureAlgorithm
import dev.junta.firmamobile.profile.SignatureFormat as ProfileSignatureFormat
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.TrustMode
import dev.junta.firmamobile.signing.BatchSigningFormat
import dev.junta.firmamobile.signing.MelillaBatchProtocolAdapter
import dev.junta.firmamobile.signing.NormalizedBatchSigningDocument
import dev.junta.firmamobile.signing.NormalizedBatchSigningRequest
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningContext
import java.time.Clock

/**
 * Converts an already validated Melilla WebMessage batch into the signing-core model.
 *
 * The bridge and protocol adapters keep their own URL validation boundaries. This adapter only
 * binds that bridge request to the currently active, exact QA profile contract before the signing
 * coordinator can own it.
 */
internal class MelillaBatchSigningAdapter(
    private val registry: SiteProfileRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun normalize(request: MelillaBatchBridgeRequest): NormalizedBatchSigningRequest? = runCatching {
        val resolved = registry.resolve(request.sourceOrigin)
            ?.takeIf {
                it.trustMode == TrustMode.TRUSTED_SIGNING &&
                    it.profile.profileId == request.profileId
            }
            ?: return null
        val profile = registry.profile(request.profileId)
            ?.takeIf { it === resolved.profile || it == resolved.profile }
            ?: return null
        val operation = profile.operationPolicies[ProtocolOperation.SIGN] ?: return null

        if (profile.profileId.value != MelillaBatchBridgeAdapter.PROFILE_ID ||
            profile.profileVersion != MELILLA_PROFILE_VERSION ||
            profile.compatibilityStatus != CompatibilityStatus.VERIFIED_CONTRACT ||
            profile.capabilities != setOf(Capability.SIGN) ||
            operation.operation != ProtocolOperation.SIGN ||
            operation.inputAdapterId.value != MelillaBatchProtocolAdapter.ID.value ||
            operation.algorithms != setOf(ProfileSignatureAlgorithm.SHA256_WITH_RSA) ||
            operation.format != ProfileSignatureFormat.CADES ||
            request.algorithm != MELILLA_ALGORITHM ||
            request.format != MELILLA_FORMAT ||
            request.suboperation != MELILLA_SUBOPERATION ||
            request.stopOnError
        ) {
            return null
        }

        val documents = request.documents.map { document ->
            if (document.suboperation != null && document.suboperation != MELILLA_SUBOPERATION) {
                return null
            }
            NormalizedBatchSigningDocument(
                id = document.id,
                dataReference = document.dataReference,
                format = document.format.orEmpty().ifEmpty { request.format }.toBatchFormat()
                    ?: return null,
                suboperation = document.suboperation,
            )
        }

        NormalizedBatchSigningRequest(
            requestId = request.requestId,
            protocolId = MelillaBatchProtocolAdapter.ID,
            context = SigningContext(
                profileId = profile.profileId.value,
                profileVersion = profile.profileVersion,
                origin = request.sourceOrigin,
                navigationId = NavigationId(request.documentId.toString()),
                navigationEpoch = request.navigationEpoch,
                observedAt = clock.instant(),
            ),
            algorithm = SigningAlgorithm.SHA256_WITH_RSA,
            format = BatchSigningFormat.CADES,
            suboperation = request.suboperation,
            stopOnError = request.stopOnError,
            operationId = request.operationId,
            preSignerUrl = request.batchPreSignerUrl,
            postSignerUrl = request.batchPostSignerUrl,
            documents = documents,
        )
    }.getOrNull()

    private fun String.toBatchFormat(): BatchSigningFormat? = when (this) {
        "CAdES" -> BatchSigningFormat.CADES
        "PAdES" -> BatchSigningFormat.PADES
        "XAdES" -> BatchSigningFormat.XADES
        else -> null
    }

    private companion object {
        const val MELILLA_PROFILE_VERSION = 1
        const val MELILLA_ALGORITHM = "SHA256withRSA"
        const val MELILLA_FORMAT = "CAdES"
        const val MELILLA_SUBOPERATION = "sign"
    }
}
