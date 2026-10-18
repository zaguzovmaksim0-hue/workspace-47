package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSigningAdaptersTest {
    @Test
    fun ordinaryIndexResolvesAllBuiltInAdapters() {
        val adapters = ordinaryAdapters()

        assertEquals(25, adapters.all.size)
        assertTrue(adapters.resolve(JuntaTriPhaseAdapter.ID) is JuntaTriPhaseAdapter)
        assertTrue(adapters.resolve(JuntaOfvirtualTriPhaseAdapter.ID) is JuntaOfvirtualTriPhaseAdapter)
        assertTrue(adapters.resolve(LocalXadesDetachedAdapter.ID) is LocalXadesDetachedAdapter)
        assertTrue(adapters.resolve(LocalCadesDetachedAdapter.ID) is LocalCadesDetachedAdapter)
        assertTrue(adapters.resolve(DgtVerificationCadesAdapter.ID) is DgtVerificationCadesAdapter)
        assertTrue(adapters.resolve(DiputacionLleidaCadesAdapter.ID) is DiputacionLleidaCadesAdapter)
        assertTrue(adapters.resolve(DiputacionBadajozCadesAdapter.ID) is DiputacionBadajozCadesAdapter)
        assertTrue(adapters.resolve(CanariasCertificateLoginCadesAdapter.ID) is CanariasCertificateLoginCadesAdapter)
        assertTrue(adapters.resolve(UgrCadesDetachedAdapter.ID) is UgrCadesDetachedAdapter)
        assertTrue(adapters.resolve(JccmCertificateLoginProbeCadesAdapter.ID) is JccmCertificateLoginProbeCadesAdapter)
        assertTrue(adapters.resolve(MitesCertificateLoginCadesAdapter.ID) is MitesCertificateLoginCadesAdapter)
        assertTrue(adapters.resolve(GranCanariaPadesAdapter.ID) is GranCanariaPadesAdapter)
        assertTrue(adapters.resolve(FuerteventuraPadesAdapter.ID) is FuerteventuraPadesAdapter)
        assertTrue(adapters.resolve(XuntaPadesTriPhaseAdapter.ID) is XuntaPadesTriPhaseAdapter)
        assertTrue(adapters.resolve(TransparenciaPadesAdapter.ID) is TransparenciaPadesAdapter)
        assertTrue(adapters.resolve(MinecoPadesAdapter.ID) is MinecoPadesAdapter)
        assertTrue(adapters.resolve(TenerifeCadesDetachedAdapter.ID) is TenerifeCadesDetachedAdapter)
        assertTrue(adapters.resolve(EivissaCadesDetachedAdapter.ID) is EivissaCadesDetachedAdapter)
        assertTrue(adapters.resolve(SevillaAtseXadesEnvelopingAdapter.ID) is SevillaAtseXadesEnvelopingAdapter)
        assertTrue(adapters.resolve(AirefXadesEnvelopingAdapter.ID) is AirefXadesEnvelopingAdapter)
        assertTrue(adapters.resolve(CdtiXadesEnvelopingAdapter.ID) is CdtiXadesEnvelopingAdapter)
        assertTrue(adapters.resolve(TransportesXadesEnvelopedAdapter.ID) is TransportesXadesEnvelopedAdapter)
        assertTrue(adapters.resolve(PoliciaXadesDetachedAdapter.ID) is PoliciaXadesDetachedAdapter)
        assertTrue(adapters.resolve(UnizarTriPhaseAdapter.ID) is UnizarTriPhaseAdapter)
        assertTrue(adapters.resolve(AccedaPadesAdapter.ID) is AccedaPadesAdapter)
    }

    @Test
    fun cantabriaRegistryBindingResolvesToRuntimeAdapter() {
        val id = SigningProtocolId("cantabria-rec-cert-login-cades-v1")

        assertEquals(id, ordinaryAdapters().resolve(id)?.id)
    }

    @Test
    fun batchIndexResolvesAllBuiltInAdapters() {
        val adapters = RuntimeSigningAdapters.batch()

        assertEquals(7, adapters.all.size)
        assertTrue(adapters.resolve(MelillaBatchProtocolAdapter.ID) is MelillaBatchProtocolAdapter)
        assertTrue(adapters.resolve(ExtremaduraBatchProtocolAdapter.ID) is ExtremaduraBatchProtocolAdapter)
        assertTrue(adapters.resolve(LaPalmaBatchProtocolAdapter.ID) is LaPalmaBatchProtocolAdapter)
        assertTrue(adapters.resolve(HuescaBatchProtocolAdapter.ID) is HuescaBatchProtocolAdapter)
        assertTrue(adapters.resolve(LugoBatchProtocolAdapter.ID) is LugoBatchProtocolAdapter)
        assertTrue(adapters.resolve(CaibBatchProtocolAdapter.ID) is CaibBatchProtocolAdapter)
        assertTrue(adapters.resolve(BurgosBatchProtocolAdapter.ID) is BurgosBatchProtocolAdapter)
    }

    @Test
    fun duplicateProtocolIdsAreRejected() {
        val duplicateId = SigningProtocolId("duplicate")

        assertThrows(IllegalArgumentException::class.java) {
            SigningAdapterIndex(
                adapters = listOf(TestAdapter(duplicateId), TestAdapter(duplicateId)),
                idOf = TestAdapter::id,
            )
        }
    }

    private fun ordinaryAdapters(): SigningAdapterIndex<SigningProtocolAdapter> =
        RuntimeSigningAdapters.ordinary(
            directJuntaTransport = HttpsProfileHttpTransport(),
            ofvirtualTransport = HttpsProfileHttpTransport(),
        )

    private data class TestAdapter(val id: SigningProtocolId)
}
