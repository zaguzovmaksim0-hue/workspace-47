package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MitesCertificateLoginCadesAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = MitesCertificateLoginCadesAdapter(clock)

    @Test
    fun acceptsOnlyExactLowercaseTenByteChallengeAndObservedTuple() = runTest {
        val request = request()
        val prepared = adapter.prepare(request, identity.chain)
        assertTrue(prepared is ProtocolPrepareResult.Success)
        (prepared as ProtocolPrepareResult.Success).preSign.close()
        request.close()
    }

    @Test
    fun rejectsWrongContractDimensionsPayloadAndProperties() = runTest {
        assertFailure(request(profileId = "other-profile"))
        assertFailure(request(origin = "https://sede.mites.gob.es.evil.example"))
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA))
        assertFailure(request(format = SigningFormat.XADES))
        assertFailure(request(payload = "abcdefghijX".encodeToByteArray()), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(payload = "abcdefghiJ".encodeToByteArray()), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(extraProperties = "mode=implicit"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(
            request(
                extraProperties =
                    "filters.1=signingCert:;keyusage.nonrepudiation:true;nonexpired:\nmode=implicit",
            ),
            SigningErrorCode.PROTOCOL_FAILED,
        )
    }

    private suspend fun assertFailure(
        request: NormalizedSignRequest,
        expectedCode: SigningErrorCode = SigningErrorCode.UNSUPPORTED_PROTOCOL,
    ) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertEquals(expectedCode, result.code)
        request.close()
    }

    private fun request(
        payload: ByteArray = "abcdefghij".encodeToByteArray(),
        extraProperties: String = MitesCertificateLoginCadesAdapter.EXPECTED_EXTRA_PROPERTIES,
        requestId: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId: SigningProtocolId = MitesCertificateLoginCadesAdapter.ID,
        profileId: String = MitesCertificateLoginCadesAdapter.PROFILE_ID,
        profileVersion: Int = MitesCertificateLoginCadesAdapter.PROFILE_VERSION,
        origin: String = MitesCertificateLoginCadesAdapter.INITIATOR_ORIGIN,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
        format: SigningFormat = SigningFormat.CADES,
    ) = NormalizedSignRequest(
        requestId = requestId,
        protocolId = protocolId,
        context = SigningContext(
            profileId = profileId,
            profileVersion = profileVersion,
            origin = URI(origin).let { TrustedOrigin(it.scheme, it.host, 443) },
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
            navigationEpoch = 7,
            observedAt = clock.instant(),
        ),
        algorithm = algorithm,
        format = format,
        safeDescription = MitesCertificateLoginCadesAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(payload, extraProperties),
    ).also { payload.fill(0) }
}
