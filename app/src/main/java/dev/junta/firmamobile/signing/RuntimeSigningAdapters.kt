package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.ProfileHttpTransport

internal class SigningAdapterIndex<A : Any>(
    adapters: List<A>,
    idOf: (A) -> SigningProtocolId,
) {
    val all: List<A> = adapters.toList()
    private val byId: Map<SigningProtocolId, A> = all.associateBy(idOf)

    init {
        require(byId.size == all.size) { "Duplicate signing protocol id" }
    }

    fun resolve(id: SigningProtocolId): A? = byId[id]
}

internal object RuntimeSigningAdapters {
    fun ordinary(
        directJuntaTransport: HttpsProfileHttpTransport,
        ofvirtualTransport: ProfileHttpTransport,
    ): SigningAdapterIndex<SigningProtocolAdapter> = SigningAdapterIndex(
        adapters = listOf<SigningProtocolAdapter>(
            JuntaTriPhaseAdapter(transport = directJuntaTransport),
            JuntaOfvirtualTriPhaseAdapter(transport = ofvirtualTransport),
            LocalXadesDetachedAdapter(),
            LocalCadesDetachedAdapter(),
            DgtVerificationCadesAdapter(),
            DiputacionLleidaCadesAdapter(),
            DiputacionBadajozCadesAdapter(),
            CanariasCertificateLoginCadesAdapter(),
            UgrCadesDetachedAdapter(),
            JccmCertificateLoginProbeCadesAdapter(),
            MitesCertificateLoginCadesAdapter(),
            CantabriaRecCertificateLoginCadesAdapter(),
            GranCanariaPadesAdapter(),
            FuerteventuraPadesAdapter(),
            XuntaPadesTriPhaseAdapter(),
            TransparenciaPadesAdapter(),
            MinecoPadesAdapter(),
            TenerifeCadesDetachedAdapter(),
            EivissaCadesDetachedAdapter(),
            SevillaAtseXadesEnvelopingAdapter(),
            AirefXadesEnvelopingAdapter(),
            CdtiXadesEnvelopingAdapter(),
            TransportesXadesEnvelopedAdapter(),
            PoliciaXadesDetachedAdapter(),
            UnizarTriPhaseAdapter(),
            AccedaPadesAdapter(),
        ),
        idOf = SigningProtocolAdapter::id,
    )

    fun batch(): SigningAdapterIndex<BatchSigningProtocolAdapter> = SigningAdapterIndex(
        adapters = listOf<BatchSigningProtocolAdapter>(
            MelillaBatchProtocolAdapter(transport = dev.junta.firmamobile.network.StaBatchHttpTransport(dev.junta.firmamobile.network.MelillaBatchUrlPolicy()::validate)),
            ExtremaduraBatchProtocolAdapter(transport = dev.junta.firmamobile.network.StaBatchHttpTransport(dev.junta.firmamobile.network.ExtremaduraBatchUrlPolicy()::validate)),
            LaPalmaBatchProtocolAdapter(transport = dev.junta.firmamobile.network.StaBatchHttpTransport(dev.junta.firmamobile.network.LaPalmaBatchUrlPolicy()::validate)),
            HuescaBatchProtocolAdapter(transport = dev.junta.firmamobile.network.StaBatchHttpTransport(dev.junta.firmamobile.network.HuescaBatchUrlPolicy()::validate)),
            LugoBatchProtocolAdapter(),
            CaibBatchProtocolAdapter(),
            BurgosBatchProtocolAdapter(transport = dev.junta.firmamobile.network.StaBatchHttpTransport(dev.junta.firmamobile.network.BurgosBatchUrlPolicy()::validate)),
        ),
        idOf = BatchSigningProtocolAdapter::id,
    )
}
