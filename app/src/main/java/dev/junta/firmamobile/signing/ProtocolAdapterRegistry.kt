package dev.junta.firmamobile.signing

import dev.junta.firmamobile.profile.CallbackContractId
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolInputAdapterId
import dev.junta.firmamobile.profile.ProtocolOperation

data class ProtocolAdapterBinding(
    val profileId: ProfileId,
    val operation: ProtocolOperation,
    val inputAdapterId: ProtocolInputAdapterId,
    val callbackContractId: CallbackContractId,
    val signingProtocolId: SigningProtocolId,
)

class ProtocolAdapterRegistry(
    bindings: List<ProtocolAdapterBinding>,
) {
    private val bindings = bindings.toList()

    init {
        require(bindings.isNotEmpty())
        require(bindings.map { it.profileId to it.operation }.toSet().size == bindings.size)
        require(bindings.map { Triple(it.profileId, it.inputAdapterId, it.callbackContractId) }.toSet().size == bindings.size)
    }

    fun resolve(profileId: ProfileId, operation: ProtocolOperation): ProtocolAdapterBinding? =
        bindings.singleOrNull { it.profileId == profileId && it.operation == operation }
}

object BuiltInProtocolAdapterRegistry {
    val registry = ProtocolAdapterRegistry(
        listOf(
            ProtocolAdapterBinding(
                profileId = ProfileId("junta-andalucia"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = JuntaTriPhaseAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("reg-age-redsara"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                signingProtocolId = LocalXadesDetachedAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("unizar-tramitador"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                signingProtocolId = UnizarTriPhaseAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("junta-ofvirtual"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = JuntaOfvirtualTriPhaseAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(LocalCadesDetachedAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = LocalCadesDetachedAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(DgtVerificationCadesAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = DgtVerificationCadesAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(UgrCadesDetachedAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = UgrCadesDetachedAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(SevillaAtseXadesEnvelopingAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                signingProtocolId = SevillaAtseXadesEnvelopingAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("cantabria-rec-cert-login"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = SigningProtocolId("cantabria-rec-cert-login-cades-v1"),
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(GranCanariaPadesAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = GranCanariaPadesAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(TenerifeCadesDetachedAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = TenerifeCadesDetachedAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(JccmCertificateLoginProbeCadesAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = JccmCertificateLoginProbeCadesAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("melilla-sede"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("melilla-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("melilla-batch-result-v1"),
                signingProtocolId = MelillaBatchProtocolAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("extremadura-tramites"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("extremadura-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("extremadura-batch-result-v1"),
                signingProtocolId = ExtremaduraBatchProtocolAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("isciii-certificate-selection"),
                operation = ProtocolOperation.SELECT_CERTIFICATE,
                inputAdapterId = ProtocolInputAdapterId("autoscript-select-certificate-v1"),
                callbackContractId = CallbackContractId("autoscript-select-certificate-callback-v1"),
                signingProtocolId = SigningProtocolId("isciii-select-certificate-v1"),
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("la-palma-sede-electronica"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("la-palma-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("la-palma-batch-result-v1"),
                signingProtocolId = LaPalmaBatchProtocolAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("diputacion-burgos-portal"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("burgos-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("burgos-batch-result-v1"),
                signingProtocolId = BurgosBatchProtocolAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("diputacion-huesca-portal"),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("huesca-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("huesca-batch-result-v1"),
                signingProtocolId = HuescaBatchProtocolAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(LugoBatchProtocolAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("lugo-clientsigner-xml-batch-v1"),
                callbackContractId = CallbackContractId("lugo-clientsigner-batch-result-v1"),
                signingProtocolId = LugoBatchProtocolAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId("diputacion-valencia-sede"),
                operation = ProtocolOperation.SELECT_CERTIFICATE,
                inputAdapterId = ProtocolInputAdapterId("autoscript-select-certificate-v1"),
                callbackContractId = CallbackContractId("autoscript-select-certificate-callback-v1"),
                signingProtocolId = SigningProtocolId("valencia-select-certificate-v1"),
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(PoliciaXadesDetachedAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                signingProtocolId = PoliciaXadesDetachedAdapter.ID,
            ),
            ProtocolAdapterBinding(
                profileId = ProfileId(DiputacionLleidaCadesAdapter.PROFILE_ID),
                operation = ProtocolOperation.SIGN,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                signingProtocolId = DiputacionLleidaCadesAdapter.ID,
            ),
        ),
    )
}
