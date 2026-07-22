package dev.junta.firmamobile.signing

import dev.junta.firmamobile.profile.CallbackContractId
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolInputAdapterId
import dev.junta.firmamobile.profile.ProtocolOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtocolAdapterRegistryTest {
    @Test
    fun resolvesOnlyTheExactProfileOperationBindingWithoutFallback() {
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("junta-andalucia"),
            ProtocolOperation.SIGN,
        )

        assertEquals(JuntaTriPhaseAdapter.ID, binding?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), binding?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), binding?.callbackContractId)
        val unizar = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("unizar-tramitador"),
            ProtocolOperation.SIGN,
        )
        assertEquals(UnizarTriPhaseAdapter.ID, unizar?.signingProtocolId)
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), unizar?.callbackContractId)
        val juntaOfvirtual = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("junta-ofvirtual"),
            ProtocolOperation.SIGN,
        )
        assertEquals(JuntaOfvirtualTriPhaseAdapter.ID, juntaOfvirtual?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), juntaOfvirtual?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), juntaOfvirtual?.callbackContractId)
        assertNull(
            BuiltInProtocolAdapterRegistry.registry.resolve(
                ProfileId("unknown-profile"),
                ProtocolOperation.SIGN,
            ),
        )
    }

    @Test
    fun rejectsAmbiguousProfileOperationBindings() {
        val binding = checkNotNull(
            BuiltInProtocolAdapterRegistry.registry.resolve(
                ProfileId("junta-andalucia"),
                ProtocolOperation.SIGN,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolAdapterRegistry(listOf(binding, binding))
        }
    }
}
