package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.EndpointPurpose
import dev.junta.firmamobile.profile.HttpMethod
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SignaturePackaging
import java.net.URI
import java.security.cert.X509Certificate
import java.util.Base64

class XuntaPadesTriPhaseAdapter internal constructor(
    transport: ProfileHttpTransport = HttpsProfileHttpTransport(URL_POLICY),
    codec: TriPhaseProtocolCodec = AutoFirmaCadesTriPhaseCodec(
        urlPolicy = URL_POLICY,
        expectedDocumentBytes = DOCUMENT_ID_BYTES.size,
        expectedSigningFormat = SigningFormat.PADES,
        wireFormat = "pades",
        expectedSessionFormat = "PAdES",
        extraPropertiesValidator = ::validExtraProperties,
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
    ): ProtocolPrepareResult {
        if (!request.hasExactDocumentId()) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }
        return delegate.prepare(request, certificateChain)
    }

    override suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult = delegate.complete(request, preSign, localSignature)

    private fun NormalizedSignRequest.hasExactDocumentId(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { document, _ ->
                document.contentEquals(DOCUMENT_ID_BYTES)
            }
        }
    }.getOrDefault(false)

    companion object {
        val ID = SigningProtocolId("xunta-galicia-pades-triphase-v1")
        const val PROFILE_ID = "xunta-galicia-solicitude-xenerica"
        const val PROFILE_VERSION = 1
        const val PUBLIC_START_URL = "https://sede.xunta.gal/tramites-e-servizos/solicitude-xenerica"
        const val SIGNING_PAGE_URL = "https://sede.xunta.gal/presenta/novo/PR004A_2025_1"
        const val INITIATOR_ORIGIN = "https://sede.xunta.gal"
        const val ENDPOINT = "https://sede.xunta.gal/presenta/sinatura/SignatureService"
        const val SAFE_DESCRIPTION = "Firma PAdES de solicitud genérica en la Sede de la Xunta de Galicia"
        const val SELECT_CERTIFICATE_SAFE_DESCRIPTION =
            "Seleccionar certificado para la solicitud genérica de la Xunta de Galicia"
        const val SELECT_CERTIFICATE_EXTRA_PROPERTIES = "filters=nonexpired"
        const val SELECT_CERTIFICATE_PROTOCOL_ID = "xunta-galicia-select-certificate-v1"
        val FIXED_EXTRA_PROPERTIES = linkedMapOf(
            "format" to "PAdES",
            "signatureSubFilter" to "ETSI.CAdES.detached",
            "serverUrl" to ENDPOINT,
            "referencesDigestMethod" to "http://www.w3.org/2000/09/xmldsig#sha1",
            "mimeType" to "hash/sha256",
            "headless" to "true",
        )
        val ALLOWED_EXTRA_PROPERTIES = linkedSetOf(
            "filters",
            "locale",
            "nif",
            "id",
            "codigoSeguridad",
            "marcaFirmaCustom",
            "dataUser",
            "idBorrador",
        )
        private val REQUIRED_DYNAMIC_PROPERTIES = setOf("filters", "locale")
        private val URL_POLICY = SafeNetworkUrlPolicy(setOf(URI(ENDPOINT)))
        private val DOCUMENT_ID_BYTES = "doc".encodeToByteArray()
        private val CONTRACT by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val profile = checkNotNull(
                BuiltInSiteProfiles.catalog.profiles.singleOrNull {
                    it.profileId == ProfileId(PROFILE_ID)
                },
            )
            val operation = checkNotNull(profile.operationPolicies[ProtocolOperation.SIGN])
            val endpoint = checkNotNull(operation.endpointId?.let(profile.endpoints::get))
            check(profile.capabilities == setOf(Capability.SIGN, Capability.SELECT_CERTIFICATE, Capability.LEGACY_SHA1))
            check(operation.packaging == SignaturePackaging.ATTACHED)
            check(endpoint.purpose == EndpointPurpose.TRIPHASE && endpoint.method == HttpMethod.POST)
            check(endpoint.url.toASCIIString() == ENDPOINT)
            check(operation.fixedExtraProperties == FIXED_EXTRA_PROPERTIES)
            check(operation.allowedExtraProperties == ALLOWED_EXTRA_PROPERTIES)
            TriPhaseExecutionContract(
                protocolId = ID,
                profileId = profile.profileId.value,
                profileVersion = profile.profileVersion,
                initiatorOrigins = profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized },
                endpoint = endpoint.url,
                format = SigningFormat.PADES,
                algorithms = setOf(SigningAlgorithm.SHA1_WITH_RSA),
            )
        }
        private const val DEFAULT_CALL_TIMEOUT_MILLIS = 20_000L

        private fun validExtraProperties(
            actual: Map<String, String>,
            certificateChain: List<X509Certificate>,
        ): Boolean {
            if (certificateChain.isEmpty()) return false
            if (!FIXED_EXTRA_PROPERTIES.all { (key, value) -> actual[key] == value }) return false
            if (!REQUIRED_DYNAMIC_PROPERTIES.all(actual::containsKey)) return false
            if (actual.keys.any { it !in FIXED_EXTRA_PROPERTIES && it !in ALLOWED_EXTRA_PROPERTIES }) return false
            if (actual["locale"].isNullOrBlank()) return false
            if (actual.filterKeys { it in ALLOWED_EXTRA_PROPERTIES }.values.any {
                    it.length > 8_192 || it.any(Char::isISOControl)
                }
            ) return false
            val filter = actual["filters"] ?: return false
            if (filter == "nonexpired") return true
            val prefix = "nonexpired;encodedcert:"
            if (!filter.startsWith(prefix)) return false
            val encoded = filter.removePrefix(prefix)
            if (encoded.isEmpty()) return false
            val expected = runCatching { Base64.getEncoder().encodeToString(certificateChain.first().encoded) }
                .getOrNull() ?: return false
            return encoded == expected
        }
    }
}
