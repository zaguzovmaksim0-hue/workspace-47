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
        val aragon = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("aragon-siraw"),
            ProtocolOperation.SIGN,
        )
        assertEquals(LocalCadesDetachedAdapter.ID, aragon?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), aragon?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), aragon?.callbackContractId)
        val ugr = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("ugr-certificado-login"),
            ProtocolOperation.SIGN,
        )
        assertEquals("ugr-certificado-login-local-cades-v1", ugr?.signingProtocolId?.value)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), ugr?.inputAdapterId)
        val cantabria = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("cantabria-rec-cert-login"),
            ProtocolOperation.SIGN,
        )
        assertEquals("cantabria-rec-cert-login-cades-v1", cantabria?.signingProtocolId?.value)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), cantabria?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), cantabria?.callbackContractId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), ugr?.callbackContractId)
        val sevilla = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("sevilla-atse-certificate-login"),
            ProtocolOperation.SIGN,
        )
        assertEquals(SevillaAtseXadesEnvelopingAdapter.ID, sevilla?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), sevilla?.inputAdapterId)
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), sevilla?.callbackContractId)

        val tenerife = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId(TenerifeCadesDetachedAdapter.PROFILE_ID),
            ProtocolOperation.SIGN,
        )
        assertEquals(TenerifeCadesDetachedAdapter.ID, tenerife?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), tenerife?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), tenerife?.callbackContractId)

        val jccm = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("jccm-certificate-login-probe"),
            ProtocolOperation.SIGN,
        )
        assertEquals(
            JccmCertificateLoginProbeCadesAdapter.ID,
            jccm?.signingProtocolId,
        )
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), jccm?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), jccm?.callbackContractId)

        val jccmRegistro = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId(LocalXadesDetachedAdapter.JCCM_REGISTRO_PROFILE_ID),
            ProtocolOperation.SIGN,
        )
        assertEquals(LocalXadesDetachedAdapter.ID, jccmRegistro?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), jccmRegistro?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), jccmRegistro?.callbackContractId)

        val transportes = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId(TransportesXadesEnvelopedAdapter.PROFILE_ID),
            ProtocolOperation.SIGN,
        )
        assertEquals(TransportesXadesEnvelopedAdapter.ID, transportes?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), transportes?.inputAdapterId)
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), transportes?.callbackContractId)

        val mites = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId(MitesCertificateLoginCadesAdapter.PROFILE_ID),
            ProtocolOperation.SIGN,
        )
        assertEquals(MitesCertificateLoginCadesAdapter.ID, mites?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), mites?.inputAdapterId)
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), mites?.callbackContractId)

        val melilla = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("melilla-sede"),
            ProtocolOperation.SIGN,
        )
        assertEquals(MelillaBatchProtocolAdapter.ID, melilla?.signingProtocolId)
        assertEquals(
            ProtocolInputAdapterId("melilla-batch-autoscript-v1"),
            melilla?.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("melilla-batch-result-v1"),
            melilla?.callbackContractId,
        )
        assertNull(
            BuiltInProtocolAdapterRegistry.registry.resolve(
                ProfileId("melilla-sede"),
                ProtocolOperation.SELECT_CERTIFICATE,
            ),
        )

        val policia = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId("policia-solicitud-generica"),
            ProtocolOperation.SIGN,
        )
        assertEquals(PoliciaXadesDetachedAdapter.ID, policia?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), policia?.inputAdapterId)
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), policia?.callbackContractId)

        val lleida = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId(DiputacionLleidaCadesAdapter.PROFILE_ID),
            ProtocolOperation.SIGN,
        )
        assertEquals(DiputacionLleidaCadesAdapter.ID, lleida?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), lleida?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), lleida?.callbackContractId)
        assertNull(
            BuiltInProtocolAdapterRegistry.registry.resolve(
                ProfileId(DiputacionLleidaCadesAdapter.PROFILE_ID),
                ProtocolOperation.SELECT_CERTIFICATE,
            ),
        )

        val badajoz = BuiltInProtocolAdapterRegistry.registry.resolve(
            ProfileId(DiputacionBadajozCadesAdapter.PROFILE_ID),
            ProtocolOperation.SIGN,
        )
        assertEquals(DiputacionBadajozCadesAdapter.ID, badajoz?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), badajoz?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), badajoz?.callbackContractId)
        assertNull(
            BuiltInProtocolAdapterRegistry.registry.resolve(
                ProfileId(DiputacionBadajozCadesAdapter.PROFILE_ID),
                ProtocolOperation.SELECT_CERTIFICATE,
            ),
        )

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
