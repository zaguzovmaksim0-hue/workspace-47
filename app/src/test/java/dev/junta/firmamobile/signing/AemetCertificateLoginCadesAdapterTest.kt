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

class AemetCertificateLoginCadesAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = AemetCertificateLoginCadesAdapter(clock)

    @Test
    fun producesSha1DetachedCadesForTheBoundedDecimalLoginNonce() = runTest {
        val challenge = "10000".encodeToByteArray()
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
    fun acceptsOnlyTheExactLoginPageAndRejectsItsNeighbors() = runTest {
        for (nonce in listOf("0", "1", "9999", "10000")) {
            val page = AemetCertificateLoginCadesAdapter.SIGNING_PAGE_URL
            request(nonce.encodeToByteArray(), pageUrl = page).use { request ->
                val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
                prepared.preSign.close()
            }
            for (suffix in listOf("/", "?extra=1", "#fragment", "/other")) {
                assertFailure(request(pageUrl = page + suffix), SigningErrorCode.UNSUPPORTED_PROTOCOL)
            }
        }
    }

    @Test
    fun rejectsEveryContractMismatchAndNonExactChallenge() = runTest {
        assertFailure(request(profileId = "other-profile"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(origin = "https://sede.aemet.gob.es.evil.example"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(pageUrl = "https://sede.aemet.gob.es/sede/other"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.XADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(extraProperties = "format=CAdES Detached"), SigningErrorCode.PROTOCOL_FAILED)
        for (nonce in listOf(
            "", "00", "01", "10001", "123456", "-1", "+1", "1\n", "%PDF-1.7", "<xml/>", "https://example.org/",
        )) {
            assertFailure(request(nonce.encodeToByteArray()), SigningErrorCode.PROTOCOL_FAILED)
        }
    }

    private suspend fun assertFailure(request: NormalizedSignRequest, code: SigningErrorCode) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertEquals(code, result.code)
        request.close()
    }

    private fun request(
        payload: ByteArray = "10000".encodeToByteArray(),
        extraProperties: String = "",
        protocolId: SigningProtocolId = AemetCertificateLoginCadesAdapter.ID,
        profileId: String = AemetCertificateLoginCadesAdapter.PROFILE_ID,
        profileVersion: Int = AemetCertificateLoginCadesAdapter.PROFILE_VERSION,
        origin: String = AemetCertificateLoginCadesAdapter.INITIATOR_ORIGIN,
        pageUrl: String = AemetCertificateLoginCadesAdapter.SIGNING_PAGE_URL,
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
        safeDescription = AemetCertificateLoginCadesAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(payload, extraProperties),
    ).also { payload.fill(0) }
}
