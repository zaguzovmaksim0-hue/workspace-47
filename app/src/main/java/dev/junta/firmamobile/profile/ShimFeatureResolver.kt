package dev.junta.firmamobile.profile

import dev.junta.firmamobile.browser.MelillaBatchBridgeAdapter
import dev.junta.firmamobile.browser.ExtremaduraBatchBridgeAdapter
import dev.junta.firmamobile.browser.LaPalmaBatchBridgeAdapter
import dev.junta.firmamobile.browser.HuescaBatchBridgeAdapter
import dev.junta.firmamobile.browser.BurgosBatchBridgeAdapter
import dev.junta.firmamobile.browser.LugoBatchBridgeAdapter
import dev.junta.firmamobile.browser.CaibBatchBridgeAdapter

/** Selection only: registry activation and native origin/consent checks remain authoritative. */
internal class ShimFeatureResolver(catalog: SiteProfileCatalog) {
    private val profiles = catalog.profiles.associateBy { it.profileId }

    init {
        require(catalog.schemaVersion == 1 || catalog.schemaVersion == 2)
        require(profiles.size == catalog.profiles.size)
        if (catalog.schemaVersion == 1) {
            require(profiles.values.all { it.shimFeatures == null })
        } else {
            profiles.values.forEach { validate(it) }
        }
    }

    fun resolve(
        registry: SiteProfileRegistry,
        profileId: ProfileId,
        staBatchEnabled: Boolean = false,
        staBatchSourceOrigin: String? = null,
    ): Set<ShimFeature> {
        val activeProfile = registry.profile(profileId) ?: return emptySet()
        return profiles[profileId]?.let { profile ->
            require(profile.copy(shimFeatures = null) == activeProfile.copy(shimFeatures = null)) {
                "Shim catalog differs from active registry contract"
            }
            featuresFor(profile, staBatchEnabled, staBatchSourceOrigin)
        } ?: emptySet()
    }

    private fun featuresFor(
        profile: SiteProfile,
        staBatchEnabled: Boolean,
        staBatchSourceOrigin: String?,
    ): Set<ShimFeature> {
        val contract = REVIEWED_CONTRACTS[profile.profileId.value]
        // null is schema v1 absence; an explicit v2 [] must never use legacy selection.
        val features = profile.shimFeatures?.also { validate(profile) }
            ?: setOfNotNull(contract?.feature)
        val batchOrigin = contract?.sourceOrigin?.takeIf { features.isNotEmpty() }
        if (batchOrigin != null && staBatchEnabled) {
            require(staBatchSourceOrigin == batchOrigin) { "STA runtime source origin mismatch" }
        }
        return if (batchOrigin == null || staBatchEnabled) features.toSet() else emptySet()
    }

    companion object {
        fun validate(profile: SiteProfile) {
            val features = requireNotNull(profile.shimFeatures) { "Schema v2 requires shimFeatures" }
            if (features.isEmpty()) return
            val contract = requireNotNull(REVIEWED_CONTRACTS[profile.profileId.value]) { "Unreviewed shim profile" }
            // There are no reviewed multi-feature profile combinations at step 5a.
            // Xunta sign + selection is one indivisible module, not two portal features.
            require(features == setOf(contract.feature)) { "Unsupported shim feature combination" }
            require(profile.operationPolicies.mapValues { (_, operation) ->
                operation.inputAdapterId.value to operation.callbackContractId.value
            } == contract.operations) { "Shim operation/adapter contract mismatch" }
            require(profile.operationPolicies.all { (operation, policy) -> operation == policy.operation })
            if (contract.feature == ShimFeature.EUSKADI_CLIENT_AUTH_POST) validateEuskadi(profile)
        }

        private fun validateEuskadi(profile: SiteProfile) {
            val policy = requireNotNull(profile.clientAuthPolicy)
            require(policy.transitionMode == ClientAuthTransitionMode.DIRECT_FROM_SOURCE)
            require(policy.requestOrigins == setOf(ExactOrigin.parse("https://eidas2.izenpe.com")))
            require(policy.sourceUrls.map { it.toASCIIString() }.toSet() == setOf(
                "https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication",
            ))
            require(policy.requestPath == "/cert-authn-external-validation/authenticate")
        }

        // Exact reviewed IDs, operations and adapters; not display names, suffixes or capabilities.
        // Catalog parser pins continue validating the complete portal contracts before this check.
        private val REVIEWED_CONTRACTS = mapOf(
            "ugr-certificado-login" to ReviewedShimContract(
                feature = ShimFeature.UGR_LOGIN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "cantabria-rec-cert-login" to ReviewedShimContract(
                feature = ShimFeature.CANTABRIA_LOGIN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "jccm-certificate-login-probe" to ReviewedShimContract(
                feature = ShimFeature.JCCM_PROBE,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "jccm-registro-generico" to ReviewedShimContract(
                feature = ShimFeature.JCCM_REGISTRO,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "sevilla-atse-certificate-login" to ReviewedShimContract(
                feature = ShimFeature.SEVILLA_ATSE,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "autoscript-sign-callback-v1"
                    ),
                ),
            ),
            "airef-instancia-general" to ReviewedShimContract(
                feature = ShimFeature.AIREF_SIGN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "autoscript-sign-callback-v1"
                    ),
                ),
            ),
            "cdti-certificate-validation" to ReviewedShimContract(
                feature = ShimFeature.CDTI_SIGN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "autoscript-sign-callback-v1"
                    ),
                ),
            ),
            "policia-solicitud-generica" to ReviewedShimContract(
                feature = ShimFeature.POLICIA_SIGN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "autoscript-sign-callback-v1"
                    ),
                ),
            ),
            "gran-canaria-sede-electronica" to ReviewedShimContract(
                feature = ShimFeature.GRAN_CANARIA_SIGN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "fuerteventura-sede-electronica" to ReviewedShimContract(
                feature = ShimFeature.FUERTEVENTURA_SIGN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "canarias-sede" to ReviewedShimContract(
                feature = ShimFeature.CANARIAS_LOGIN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "ministerio-economia-instancia-generica" to ReviewedShimContract(
                feature = ShimFeature.MINECO_SIGN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "age-acceda" to ReviewedShimContract(
                feature = ShimFeature.ACCEDA_SIGN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "diputacion-badajoz-portal" to ReviewedShimContract(
                feature = ShimFeature.BADAJOZ_SIGN,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                ),
            ),
            "isciii-certificate-selection" to ReviewedShimContract(
                feature = ShimFeature.ISCIII_SELECTION,
                operations = mapOf(
                    ProtocolOperation.SELECT_CERTIFICATE to (
                        "autoscript-select-certificate-v1" to "autoscript-select-certificate-callback-v1"
                    ),
                ),
            ),
            "diputacion-valencia-sede" to ReviewedShimContract(
                feature = ShimFeature.VALENCIA_SELECTION,
                operations = mapOf(
                    ProtocolOperation.SELECT_CERTIFICATE to (
                        "autoscript-select-certificate-v1" to "autoscript-select-certificate-callback-v1"
                    ),
                ),
            ),
            "xunta-galicia-solicitude-xenerica" to ReviewedShimContract(
                feature = ShimFeature.XUNTA_SIGN_SELECTION,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "miniapplet-autoscript-v1" to "miniapplet-sign-callback-v1"
                    ),
                    ProtocolOperation.SELECT_CERTIFICATE to (
                        "autoscript-select-certificate-v1" to "autoscript-select-certificate-callback-v1"
                    ),
                ),
            ),
            "euskadi-sede-electronica" to ReviewedShimContract(
                feature = ShimFeature.EUSKADI_CLIENT_AUTH_POST,
                operations = emptyMap(),
            ),
            "melilla-sede" to ReviewedShimContract(
                feature = ShimFeature.STA_JSON_BATCH,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "melilla-batch-autoscript-v1" to "melilla-batch-result-v1"
                    ),
                ),
                sourceOrigin = MelillaBatchBridgeAdapter.SOURCE_ORIGIN,
            ),
            "extremadura-tramites" to ReviewedShimContract(
                feature = ShimFeature.STA_JSON_BATCH,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "extremadura-batch-autoscript-v1" to "extremadura-batch-result-v1"
                    ),
                ),
                sourceOrigin = ExtremaduraBatchBridgeAdapter.SOURCE_ORIGIN,
            ),
            "la-palma-sede-electronica" to ReviewedShimContract(
                feature = ShimFeature.STA_JSON_BATCH,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "la-palma-batch-autoscript-v1" to "la-palma-batch-result-v1"
                    ),
                ),
                sourceOrigin = LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN,
            ),
            "diputacion-huesca-portal" to ReviewedShimContract(
                feature = ShimFeature.STA_JSON_BATCH,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "huesca-batch-autoscript-v1" to "huesca-batch-result-v1"
                    ),
                ),
                sourceOrigin = HuescaBatchBridgeAdapter.SOURCE_ORIGIN,
            ),
            "diputacion-burgos-portal" to ReviewedShimContract(
                feature = ShimFeature.STA_JSON_BATCH,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "burgos-batch-autoscript-v1" to "burgos-batch-result-v1"
                    ),
                ),
                sourceOrigin = BurgosBatchBridgeAdapter.SOURCE_ORIGIN,
            ),
            "diputacion-lugo-sede" to ReviewedShimContract(
                feature = ShimFeature.LUGO_XML_BATCH,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "lugo-clientsigner-xml-batch-v1" to "lugo-clientsigner-batch-result-v1"
                    ),
                ),
                sourceOrigin = LugoBatchBridgeAdapter.SOURCE_ORIGIN,
            ),
            "caib-portafib" to ReviewedShimContract(
                feature = ShimFeature.CAIB_PORTAFIB_BATCH,
                operations = mapOf(
                    ProtocolOperation.SIGN to (
                        "caib-portafib-batch-v1" to "caib-portafib-batch-result-v1"
                    ),
                ),
                sourceOrigin = CaibBatchBridgeAdapter.SOURCE_ORIGIN,
            ),
        )
    }
}

private data class ReviewedShimContract(
    val feature: ShimFeature,
    val operations: Map<ProtocolOperation, Pair<String, String>>,
    val sourceOrigin: String? = null,
)
