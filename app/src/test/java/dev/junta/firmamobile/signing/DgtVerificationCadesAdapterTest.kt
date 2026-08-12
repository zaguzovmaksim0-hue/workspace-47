package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DgtVerificationCadesAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = DgtVerificationCadesAdapter(clock)

    @Test
    fun producesVerifiableDetachedCadesForTheExactFixedPayload() = runTest {
        val request = request()
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }
        val payload = DgtVerificationCadesAdapter.EXPECTED_PAYLOAD.copyOf()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)

        assertTrue(CMSSignedData(CMSProcessableByteArray(payload), result).isDetachedSignature)
        assertTrue(
            CadesDetachedCodec.validate(
                signatureDocument = result,
                detachedContent = payload,
                expectedContentBytes = DgtVerificationCadesAdapter.PAYLOAD_BYTES,
                expectedCertificateFingerprint = fingerprint,
            ),
        )

        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
        result.fill(0)
        payload.fill(0)
        fingerprint.fill(0)
    }

    @Test
    fun rejectsEveryDgtContractMismatchBeforeProducingAProtocolResult() = runTest {
        assertFailure(request(profileId = "aragon-siraw"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(profileVersion = 2), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(origin = "https://sede.dgt.gob.es.evil.example"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.XADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(
            request(protocolId = SigningProtocolId("aragon-siraw-local-cades-v1")),
            SigningErrorCode.UNSUPPORTED_PROTOCOL,
        )
        assertFailure(
            request(payload = "Cadena a firmar".replace('a', 'b').encodeToByteArray()),
            SigningErrorCode.PROTOCOL_FAILED,
        )
        assertFailure(
            request(payload = "Cadena a firma".encodeToByteArray()),
            SigningErrorCode.PROTOCOL_FAILED,
        )
        assertFailure(
            request(extraProperties = "filter=nonexpired"),
            SigningErrorCode.PROTOCOL_FAILED,
        )
        assertFailure(
            request(extraProperties = "filter=nonexpired:\nmode=explicit"),
            SigningErrorCode.PROTOCOL_FAILED,
        )
    }

    @Test
    fun rejectsCompletionFromAChangeRequestOwnerEvenWhenTheProtocolTupleMatches() = runTest {
        val request = request()
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val changedRequest = request(payload = "Cadena a firma".encodeToByteArray())
        val result = adapter.complete(changedRequest, prepared.preSign, local.signature)

        assertTrue(result is ProtocolCompletionResult.Failure)
        assertTrue((result as ProtocolCompletionResult.Failure).code == SigningErrorCode.PROTOCOL_FAILED)

        local.signature.close()
        prepared.preSign.close()
        request.close()
        changedRequest.close()
    }

    private suspend fun assertFailure(
        request: NormalizedSignRequest,
        code: SigningErrorCode,
    ) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertTrue(result.code == code)
        request.close()
    }

    private fun request(
        payload: ByteArray = DgtVerificationCadesAdapter.EXPECTED_PAYLOAD.copyOf(),
        extraProperties: String = DgtVerificationCadesAdapter.EXPECTED_EXTRA_PROPERTIES,
        requestId: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId: SigningProtocolId = DgtVerificationCadesAdapter.ID,
        profileId: String = DgtVerificationCadesAdapter.PROFILE_ID,
        profileVersion: Int = DgtVerificationCadesAdapter.PROFILE_VERSION,
        origin: String = DgtVerificationCadesAdapter.INITIATOR_ORIGIN,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
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
        safeDescription = "Verificación del equipo DGT",
        payload = MiniAppletPayloadCodec.encode(payload, extraProperties),
    ).also { payload.fill(0) }
}
