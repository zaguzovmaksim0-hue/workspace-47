package dev.junta.firmamobile.signing

import dev.junta.firmamobile.profile.CallbackContractId
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolInputAdapterId
import dev.junta.firmamobile.profile.ProtocolOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DgtVerificationProtocolAdapterRegistryTest {
    @Test
    fun bindsOnlyTheExactDgtProfileOperationAndCallback() {
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId(DgtVerificationCadesAdapter.PROFILE_ID),
            ProtocolOperation.SIGN,
        )

        assertEquals(DgtVerificationCadesAdapter.ID, binding?.signingProtocolId)
        assertEquals(
            ProtocolInputAdapterId("miniapplet-autoscript-v1"),
            binding?.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("miniapplet-sign-callback-v1"),
            binding?.callbackContractId,
        )
        assertNull(
            BuiltInProtocolAdapterRegistry.registry.resolve(
                ProfileId(DgtVerificationCadesAdapter.PROFILE_ID),
                ProtocolOperation.SELECT_CERTIFICATE,
            ),
        )
    }
}
