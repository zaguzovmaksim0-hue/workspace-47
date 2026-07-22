package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.EndpointPurpose
import dev.junta.firmamobile.profile.HttpMethod
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SignatureMode
import dev.junta.firmamobile.profile.SignaturePackaging
import dev.junta.firmamobile.profile.SignatureFormat as ProfileSignatureFormat
import java.net.URI
import java.security.cert.X509Certificate

class JuntaOfvirtualTriPhaseAdapter internal constructor(
    transport: ProfileHttpTransport = HttpsProfileHttpTransport(URL_POLICY),
    codec: TriPhaseProtocolCodec = AutoFirmaCadesTriPhaseCodec(
        urlPolicy = URL_POLICY,
        expectedExtraProperties = FIXED_EXTRA_PROPERTIES,
    ),
    callTimeoutMillis: Long = DEFAULT_CALL_TIMEOUT_MILLIS,
) : SigningProtocolAdapter {
    private val delegate = AutoFirmaTriPhaseExecutionAdapter(
        contract = CONTRACT,
        transport = transport,
        codec = codec,
        callTimeoutMillis = callTimeoutMillis,
    )

    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult = delegate.prepare(request, certificateChain)

    override suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult = delegate.complete(request, preSign, localSignature)

    companion object {
        val ID = SigningProtocolId("junta-ofvirtual-miniapplet-triphase-cades-v1")
        const val ENDPOINT =
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService"
        private val URL_POLICY = SafeNetworkUrlPolicy(setOf(URI(ENDPOINT)))
        private val FIXED_EXTRA_PROPERTIES = mapOf(
            "serverUrl" to ENDPOINT,
            "filters" to "keyusage.digitalsignature:true;nonexpired:",
        )
        private val CONTRACT by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val profile = checkNotNull(
                BuiltInSiteProfiles.catalog.profiles.singleOrNull {
                    it.profileId == ProfileId("junta-ofvirtual")
                },
            )
            val operation = checkNotNull(profile.operationPolicies[ProtocolOperation.SIGN])
            val endpoint = checkNotNull(operation.endpointId?.let(profile.endpoints::get))
            check(operation.format == ProfileSignatureFormat.CADES)
            check(operation.packaging == SignaturePackaging.DETACHED)
            check(operation.mode == SignatureMode.EXPLICIT)
            check(endpoint.purpose == EndpointPurpose.TRIPHASE && endpoint.method == HttpMethod.POST)
            check(endpoint.url.toASCIIString() == ENDPOINT)
            check(operation.fixedExtraProperties == FIXED_EXTRA_PROPERTIES)
            TriPhaseExecutionContract(
                protocolId = ID,
                profileId = profile.profileId.value,
                profileVersion = profile.profileVersion,
                initiatorOrigins = profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized },
                endpoint = endpoint.url,
                format = SigningFormat.CADES,
                algorithms = setOf(SigningAlgorithm.SHA1_WITH_RSA),
            )
        }
        private const val DEFAULT_CALL_TIMEOUT_MILLIS = 20_000L
    }
}
