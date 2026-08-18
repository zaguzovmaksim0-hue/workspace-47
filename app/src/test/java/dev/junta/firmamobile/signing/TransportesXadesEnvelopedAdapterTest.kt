package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportesXadesEnvelopedAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val adapter = TransportesXadesEnvelopedAdapter(clock)

    @Test
    fun createsValidSha1XadesEnvelopedForExactPublicLoginChallenge() = runTest {
        val request = request()
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedInfo ->
            JcaLocalSignatureEngine().sign(signedInfo, identity, SigningAlgorithm.SHA1_WITH_RSA)
        } as LocalSignatureResult.Success
        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)

        assertTrue(TransportesXadesEnvelopedCodec.validate(result, fingerprint))
        val text = result.toString(Charsets.UTF_8)
        assertTrue(text.contains("<tag1_timestamp>17/08/2026 17:52:13</tag1_timestamp>"))
        assertTrue(text.contains("enveloped-signature"))
        assertTrue(text.contains("rsa-sha1"))

        result.fill(0)
        fingerprint.fill(0)
        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
    }

    @Test
    fun acceptsOnlyExactChallengeGrammar() {
        assertTrue(TransportesXadesEnvelopedAdapter.isExactChallenge(CHALLENGE.encodeToByteArray()))
        assertFalse(
            TransportesXadesEnvelopedAdapter.isExactChallenge(
                CHALLENGE.replace("17/08/2026", "31/02/2026").encodeToByteArray(),
            ),
        )
        assertFalse(
            TransportesXadesEnvelopedAdapter.isExactChallenge(
                CHALLENGE.replace("Id=\"tag1\"", "Id=\"tag2\"").encodeToByteArray(),
            ),
        )
        assertFalse(
            TransportesXadesEnvelopedAdapter.isExactChallenge((CHALLENGE + " ").encodeToByteArray()),
        )
    }

    @Test
    fun rejectsWrongPageTupleAndProperties() = runTest {
        assertFailure(request(pageUrl = TransportesXadesEnvelopedAdapter.START_URL))
        assertFailure(request(origin = "sede.transportes.gob.es.evil.example"))
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA))
        assertFailure(request(format = SigningFormat.CADES))
        assertFailure(
            request(properties = "format=XAdES Enveloped\n"),
            SigningErrorCode.PROTOCOL_FAILED,
        )
        assertFailure(
            request(challenge = CHALLENGE.replace("tag1_timestamp", "other")),
            SigningErrorCode.PROTOCOL_FAILED,
        )
    }

    private suspend fun assertFailure(
        request: NormalizedSignRequest,
        expectedCode: SigningErrorCode = SigningErrorCode.UNSUPPORTED_PROTOCOL,
    ) {
        val result = adapter.prepare(request, identity.chain)
        assertEquals(ProtocolPrepareResult.Failure(expectedCode), result)
        request.close()
    }

    private fun request(
        challenge: String = CHALLENGE,
        properties: String = TransportesXadesEnvelopedAdapter.EXPECTED_EXTRA_PROPERTIES,
        pageUrl: String = TransportesXadesEnvelopedAdapter.AUTH_PAGE_URL,
        origin: String = "sede.transportes.gob.es",
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        format: SigningFormat = SigningFormat.XADES,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174510"),
        protocolId = TransportesXadesEnvelopedAdapter.ID,
        context = SigningContext(
            profileId = TransportesXadesEnvelopedAdapter.PROFILE_ID,
            profileVersion = 1,
            origin = TrustedOrigin("https", origin, 443),
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174511"),
            navigationEpoch = 75,
            observedAt = clock.instant(),
            pageUrl = pageUrl,
        ),
        algorithm = algorithm,
        format = format,
        safeDescription = TransportesXadesEnvelopedAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(challenge.encodeToByteArray(), properties),
    )

    private companion object {
        const val CHALLENGE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tag1 Id=\"tag1\"><tag1_timestamp>17/08/2026 17:52:13</tag1_timestamp></tag1>"
    }
}
