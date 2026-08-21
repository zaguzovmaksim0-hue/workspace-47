package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirefXadesEnvelopingAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = AirefXadesEnvelopingAdapter(clock)

    @Test
    fun exactDynamicPayloadProducesValidatedSha1XadesEnveloping() = runTest {
        val request = request()
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedInfo ->
            JcaLocalSignatureEngine().sign(
                signedInfo,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }

        assertTrue(
            SevillaAtseXadesEnvelopingCodec.validate(
                signatureDocument = result,
                signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
                payloadValidator = ByteArray::isExactAirefPayload,
            ),
        )

        result.fill(0)
        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
    }

    @Test
    fun payloadLengthAndRequestTupleStayFailClosed() = runTest {
        val wrongPayload = request(ByteArray(AirefXadesEnvelopingAdapter.PAYLOAD_BYTES - 1))
        val payloadResult = adapter.prepare(wrongPayload, identity.chain)
        assertEquals(SigningErrorCode.PROTOCOL_FAILED, (payloadResult as ProtocolPrepareResult.Failure).code)
        wrongPayload.close()

        val wrongAlgorithm = request(algorithm = SigningAlgorithm.SHA256_WITH_RSA)
        val algorithmResult = adapter.prepare(wrongAlgorithm, identity.chain)
        assertEquals(
            SigningErrorCode.UNSUPPORTED_PROTOCOL,
            (algorithmResult as ProtocolPrepareResult.Failure).code,
        )
        wrongAlgorithm.close()
    }

    private fun request(
        payload: ByteArray = ByteArray(AirefXadesEnvelopingAdapter.PAYLOAD_BYTES) { index ->
            (index + 1).toByte()
        },
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174410"),
        protocolId = AirefXadesEnvelopingAdapter.ID,
        context = SigningContext(
            profileId = AirefXadesEnvelopingAdapter.PROFILE_ID,
            profileVersion = AirefXadesEnvelopingAdapter.PROFILE_VERSION,
            origin = TrustedOrigin("https", "sede.airef.es", 443),
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174411"),
            navigationEpoch = 52,
            observedAt = clock.instant(),
        ),
        algorithm = algorithm,
        format = SigningFormat.XADES,
        safeDescription = AirefXadesEnvelopingAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(payload, ""),
    )

}
