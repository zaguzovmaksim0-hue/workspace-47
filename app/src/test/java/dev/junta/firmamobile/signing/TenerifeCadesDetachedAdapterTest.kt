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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TenerifeCadesDetachedAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = TenerifeCadesDetachedAdapter(clock)

    @Test
    fun producesSha512DetachedCadesForTheDownloadedApplicationDocument() = runTest {
        val document = "synthetic Tenerife application document".encodeToByteArray()
        val request = request(document.copyOf())
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA512_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)

        assertTrue(CMSSignedData(CMSProcessableByteArray(document), result).isDetachedSignature)
        assertTrue(
            CadesDetachedCodec.validate(
                signatureDocument = result,
                detachedContent = document,
                expectedContentBytes = document.size,
                expectedCertificateFingerprint = fingerprint,
                signingAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
            ),
        )

        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
        result.fill(0)
        document.fill(0)
        fingerprint.fill(0)
    }

    @Test
    fun rejectsEveryContractMismatchAndEmptyPayload() = runTest {
        assertFailure(request(profileId = "other-profile"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(origin = "https://sede.tenerife.es.evil.example"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.XADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(extraProperties = "mode=implicit"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(ByteArray(0)), SigningErrorCode.PROTOCOL_FAILED)
    }

    private suspend fun assertFailure(request: NormalizedSignRequest, code: SigningErrorCode) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertEquals(code, result.code)
        request.close()
    }

    private fun request(
        payload: ByteArray = "synthetic Tenerife application document".encodeToByteArray(),
        extraProperties: String = TenerifeCadesDetachedAdapter.EXPECTED_EXTRA_PROPERTIES,
        protocolId: SigningProtocolId = TenerifeCadesDetachedAdapter.ID,
        profileId: String = TenerifeCadesDetachedAdapter.PROFILE_ID,
        profileVersion: Int = TenerifeCadesDetachedAdapter.PROFILE_VERSION,
        origin: String = TenerifeCadesDetachedAdapter.INITIATOR_ORIGIN,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
        format: SigningFormat = SigningFormat.CADES,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
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
        safeDescription = TenerifeCadesDetachedAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(payload, extraProperties),
    ).also { payload.fill(0) }
}
