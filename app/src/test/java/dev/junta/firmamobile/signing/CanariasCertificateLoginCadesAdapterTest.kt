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

class CanariasCertificateLoginCadesAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = CanariasCertificateLoginCadesAdapter(clock)

    @Test
    fun producesSha1DetachedCadesForExactUtcLoginChallenge() = runTest {
        val challenge = "Wed, 02 Jan 2030 03:04:05 GMT".encodeToByteArray()
        val request = request(challenge.copyOf())
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
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)

        assertTrue(CMSSignedData(CMSProcessableByteArray(challenge), result).isDetachedSignature)
        assertTrue(
            CadesDetachedCodec.validate(
                signatureDocument = result,
                detachedContent = challenge,
                expectedContentBytes = challenge.size,
                expectedCertificateFingerprint = fingerprint,
                signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
            ),
        )

        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
        result.fill(0)
        challenge.fill(0)
        fingerprint.fill(0)
    }

    @Test
    fun rejectsEveryContractMismatchAndNonExactChallenge() = runTest {
        assertFailure(request(profileId = "other-profile"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(origin = "https://sede.gobiernodecanarias.org.evil.example"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(pageUrl = "https://sede.gobiernodecanarias.org/sede/other"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.XADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(extraProperties = "format=CAdES Detached"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request("Mon, 17 Aug 2026 21:55:05 UTC".encodeToByteArray()), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request("Mon, 7 Aug 2026 21:55:05 GMT".encodeToByteArray()), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(ByteArray(0)), SigningErrorCode.PROTOCOL_FAILED)
    }

    private suspend fun assertFailure(request: NormalizedSignRequest, code: SigningErrorCode) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertEquals(code, result.code)
        request.close()
    }

    private fun request(
        payload: ByteArray = "Wed, 02 Jan 2030 03:04:05 GMT".encodeToByteArray(),
        extraProperties: String = CanariasCertificateLoginCadesAdapter.EXPECTED_EXTRA_PROPERTIES,
        protocolId: SigningProtocolId = CanariasCertificateLoginCadesAdapter.ID,
        profileId: String = CanariasCertificateLoginCadesAdapter.PROFILE_ID,
        profileVersion: Int = CanariasCertificateLoginCadesAdapter.PROFILE_VERSION,
        origin: String = CanariasCertificateLoginCadesAdapter.INITIATOR_ORIGIN,
        pageUrl: String = CanariasCertificateLoginCadesAdapter.SIGNING_PAGE_URL,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
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
            pageUrl = pageUrl,
        ),
        algorithm = algorithm,
        format = format,
        safeDescription = CanariasCertificateLoginCadesAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(payload, extraProperties),
    ).also { payload.fill(0) }
}
