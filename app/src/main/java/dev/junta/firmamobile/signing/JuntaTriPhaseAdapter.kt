package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.EndpointPurpose
import dev.junta.firmamobile.profile.HttpMethod
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SignatureMode
import dev.junta.firmamobile.profile.SignaturePackaging
import dev.junta.firmamobile.profile.SignatureAlgorithm as ProfileSignatureAlgorithm
import dev.junta.firmamobile.profile.SignatureFormat as ProfileSignatureFormat
import java.security.cert.X509Certificate

class JuntaTriPhaseAdapter internal constructor(
    private val transport: ProfileHttpTransport = HttpsProfileHttpTransport(),
    private val codec: JuntaTriPhaseCodec = JuntaTriPhaseCodec(),
    private val callTimeoutMillis: Long = DEFAULT_CALL_TIMEOUT_MILLIS,
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
        val ID = SigningProtocolId("junta-miniapplet-triphase-cades-v1")
        private val CONTRACT by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val profile = checkNotNull(
                BuiltInSiteProfiles.catalog.profiles.singleOrNull {
                    it.profileId == ProfileId("junta-andalucia")
                },
            )
            val operation = checkNotNull(profile.operationPolicies[ProtocolOperation.SIGN])
            val endpoint = checkNotNull(operation.endpointId?.let(profile.endpoints::get))
            check(operation.format == ProfileSignatureFormat.CADES)
            check(operation.packaging == SignaturePackaging.DETACHED)
            check(operation.mode == SignatureMode.EXPLICIT)
            check(endpoint.purpose == EndpointPurpose.TRIPHASE && endpoint.method == HttpMethod.POST)
            check(operation.fixedExtraProperties["serverUrl"] == endpoint.url.toASCIIString())
            check(operation.fixedExtraProperties["mode"] == "explicit")
            TriPhaseExecutionContract(
                protocolId = ID,
                profileId = profile.profileId.value,
                profileVersion = profile.profileVersion,
                initiatorOrigins = profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized },
                endpoint = endpoint.url,
                format = SigningFormat.CADES,
                algorithms = operation.algorithms.mapTo(linkedSetOf()) { algorithm ->
                    when (algorithm) {
                        ProfileSignatureAlgorithm.SHA1_WITH_RSA -> SigningAlgorithm.SHA1_WITH_RSA
                        ProfileSignatureAlgorithm.SHA256_WITH_RSA -> SigningAlgorithm.SHA256_WITH_RSA
                        ProfileSignatureAlgorithm.SHA512_WITH_RSA -> error("unsupported Junta algorithm")
                    }
                },
            )
        }
        private const val DEFAULT_CALL_TIMEOUT_MILLIS = 20_000L
    }
}
