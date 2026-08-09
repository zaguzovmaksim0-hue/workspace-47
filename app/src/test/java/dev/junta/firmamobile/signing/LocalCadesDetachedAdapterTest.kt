package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.MessageDigest
import java.net.URI
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

class LocalCadesDetachedAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = LocalCadesDetachedAdapter(clock)

    @Test
    fun createsVerifiableDetachedCadesAndRejectsTampering() = runTest {
        val challenge = ByteArray(LocalCadesDetachedAdapter.CHALLENGE_BYTES) { it.toByte() }
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
                expectedContentBytes = LocalCadesDetachedAdapter.CHALLENGE_BYTES,
                expectedCertificateFingerprint = fingerprint,
            ),
        )
        val tamperedContent = challenge.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(
            CadesDetachedCodec.validate(
                signatureDocument = result,
                detachedContent = tamperedContent,
                expectedContentBytes = LocalCadesDetachedAdapter.CHALLENGE_BYTES,
                expectedCertificateFingerprint = fingerprint,
            ),
        )
        val tamperedSignature = result.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFalse(
            CadesDetachedCodec.validate(
                signatureDocument = tamperedSignature,
                detachedContent = challenge,
                expectedContentBytes = LocalCadesDetachedAdapter.CHALLENGE_BYTES,
                expectedCertificateFingerprint = fingerprint,
            ),
        )

        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
        challenge.fill(0)
        result.fill(0)
        fingerprint.fill(0)
        tamperedContent.fill(0)
        tamperedSignature.fill(0)
    }

    @Test
    fun failsClosedForWrongTuplePayloadPropertiesAndCertificate() = runTest {
        val wrongOrigin = request(origin = "https://www.aragon.es")
        assertFailure(wrongOrigin, SigningErrorCode.UNSUPPORTED_PROTOCOL)

        val wrongProfile = request(profileId = "other-profile")
        assertFailure(wrongProfile, SigningErrorCode.UNSUPPORTED_PROTOCOL)

        val wrongAlgorithm = request(algorithm = SigningAlgorithm.SHA256_WITH_RSA)
        assertFailure(wrongAlgorithm, SigningErrorCode.UNSUPPORTED_PROTOCOL)

        val wrongFormat = request(format = SigningFormat.XADES)
        assertFailure(wrongFormat, SigningErrorCode.UNSUPPORTED_PROTOCOL)

        val wrongSize = request(ByteArray(LocalCadesDetachedAdapter.CHALLENGE_BYTES - 1))
        assertFailure(wrongSize, SigningErrorCode.PROTOCOL_FAILED)

        val wrongProperties = request(extraProperties = "mode=explicit")
        assertFailure(wrongProperties, SigningErrorCode.PROTOCOL_FAILED)

        val missingCertificate = request()
        val failure = adapter.prepare(missingCertificate, emptyList()) as ProtocolPrepareResult.Failure
        assertTrue(failure.code == SigningErrorCode.UNSUPPORTED_PROTOCOL)
        missingCertificate.close()
    }

    @Test
    fun rejectsSignatureAndStateFromAnotherRequest() = runTest {
        val first = request()
        val second = request(requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174099"))
        val prepared = adapter.prepare(first, identity.chain) as ProtocolPrepareResult.Success
        val signature = prepared.preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val result = adapter.complete(second, prepared.preSign, signature.signature)
        assertTrue(result is ProtocolCompletionResult.Failure)

        signature.signature.close()
        prepared.preSign.close()
        first.close()
        second.close()
    }

    private suspend fun assertFailure(request: NormalizedSignRequest, code: SigningErrorCode) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertTrue(result.code == code)
        request.close()
    }

    private fun request(
        challenge: ByteArray = ByteArray(LocalCadesDetachedAdapter.CHALLENGE_BYTES) { it.toByte() },
        extraProperties: String = LocalCadesDetachedAdapter.EXPECTED_EXTRA_PROPERTIES,
        requestId: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        profileId: String = LocalCadesDetachedAdapter.PROFILE_ID,
        origin: String = LocalCadesDetachedAdapter.INITIATOR_ORIGIN,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        format: SigningFormat = SigningFormat.CADES,
    ) = NormalizedSignRequest(
        requestId = requestId,
        protocolId = LocalCadesDetachedAdapter.ID,
        context = SigningContext(
            profileId = profileId,
            profileVersion = LocalCadesDetachedAdapter.PROFILE_VERSION,
            origin = URI(origin).let { TrustedOrigin(it.scheme, it.host, 443) },
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
            navigationEpoch = 7,
            observedAt = clock.instant(),
        ),
        algorithm = algorithm,
        format = format,
        safeDescription = "Acceso con certificado a SIRAW",
        payload = MiniAppletPayloadCodec.encode(challenge, extraProperties),
    ).also { challenge.fill(0) }
}
