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
        ),
    )
}
