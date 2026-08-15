package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSSignedData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MurciaSedeCmsAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = MurciaSedeCmsAdapter(clock)

    @Test
    fun producesSha256AttachedCmsForMurciaPayloadAndVerifies() = runTest {
        val document = "synthetic Murcia CARM application payload 12345".encodeToByteArray()
        val request = request(document.copyOf())
        val preSign = assertPrepared(request)
        val local = preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA256_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)

        val cms = CMSSignedData(result)
        assertFalse(cms.isDetachedSignature)
        val signer = cms.signerInfos.signers.single()
        assertNull(signer.signedAttributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2))
        val extractedStream = ByteArrayOutputStream()
        cms.signedContent.write(extractedStream)
        assertArrayEquals(document, extractedStream.toByteArray())

        assertTrue(
            MurciaCmsCodec.validate(
                signatureDocument = result,
                expectedContent = document,
                expectedCertificateFingerprint = fingerprint,
            ),
        )

        completed.signature.close()
        local.signature.close()
        preSign.close()
        request.close()
        result.fill(0)
        document.fill(0)
        fingerprint.fill(0)
    }

    @Test
    fun rejectsTamperedContentSignatureAndFingerprint() = runTest {
        val document = "synthetic Murcia CARM payload for tampering test".encodeToByteArray()
        val request = request(document.copyOf())
        val preSign = assertPrepared(request)
        val local = preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA256_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)

        val tamperedContent = document.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(
            MurciaCmsCodec.validate(
                signatureDocument = result,
                expectedContent = tamperedContent,
                expectedCertificateFingerprint = fingerprint,
            ),
        )

        val tamperedSignature = result.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFalse(
            MurciaCmsCodec.validate(
                signatureDocument = tamperedSignature,
                expectedContent = document,
                expectedCertificateFingerprint = fingerprint,
            ),
        )

        val tamperedFingerprint = fingerprint.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(
            MurciaCmsCodec.validate(
                signatureDocument = result,
                expectedContent = document,
                expectedCertificateFingerprint = tamperedFingerprint,
            ),
        )

        completed.signature.close()
        local.signature.close()
        preSign.close()
        request.close()
        result.fill(0)
        document.fill(0)
        fingerprint.fill(0)
        tamperedContent.fill(0)
        tamperedSignature.fill(0)
        tamperedFingerprint.fill(0)
    }

    @Test
    fun rejectsEveryContractMismatchAndEmptyPayload() = runTest {
        assertFailure(request(profileId = "other-profile"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(origin = "https://sede.carm.es.evil.example"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA1_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA512_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.CADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.XADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(extraProperties = "mode=explicit"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(extraProperties = "filters=nonexpired:\nmode=explicit"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(ByteArray(0)), SigningErrorCode.PROTOCOL_FAILED)
    }

    @Test
    fun rejectsCertificateThatExpiredAfterUnlockBeforePresign() = runTest {
        val expiredAtSigning = MurciaSedeCmsAdapter(
            Clock.fixed(Instant.parse("2032-01-02T03:04:05Z"), ZoneOffset.UTC),
        )
        val request = request()
        val result = expiredAtSigning.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertEquals(SigningErrorCode.PROTOCOL_FAILED, result.code)
        request.close()
    }

    @Test
    fun enforcesSingleUseStateConsumptionAndZeroization() = runTest {
        val document = "single-use test document".encodeToByteArray()
        val request = request(document.copyOf())
        val preSign = assertPrepared(request)
        val local = preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA256_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, preSign, local.signature)
            as ProtocolCompletionResult.Success
        completed.signature.close()

        val secondComplete = adapter.complete(request, preSign, local.signature)
        assertTrue(secondComplete is ProtocolCompletionResult.Failure)
        assertEquals(SigningErrorCode.PROTOCOL_FAILED, (secondComplete as ProtocolCompletionResult.Failure).code)

        val otherRequest = request("other document".encodeToByteArray())
        val preparedOther = adapter.prepare(otherRequest, identity.chain) as ProtocolPrepareResult.Success
        val stolenConsume = preparedOther.preSign.consumeState(request)
        assertNull(stolenConsume)

        local.signature.close()
        preSign.close()
        preparedOther.preSign.close()
        request.close()
        otherRequest.close()
    }

    private suspend fun assertPrepared(request: NormalizedSignRequest): PreSignResult {
        return when (val result = adapter.prepare(request, identity.chain)) {
            is ProtocolPrepareResult.Success -> result.preSign
            is ProtocolPrepareResult.Failure -> error("Murcia prepare failed with ${result.code}")
        }
    }

    private suspend fun assertFailure(request: NormalizedSignRequest, code: SigningErrorCode) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertEquals(code, result.code)
        request.close()
    }

    private fun request(
        payload: ByteArray = "synthetic Murcia CARM application payload 12345".encodeToByteArray(),
        extraProperties: String = MurciaSedeCmsAdapter.EXPECTED_EXTRA_PROPERTIES,
        protocolId: SigningProtocolId = MurciaSedeCmsAdapter.ID,
        profileId: String = MurciaSedeCmsAdapter.PROFILE_ID,
        profileVersion: Int = MurciaSedeCmsAdapter.PROFILE_VERSION,
        origin: String = MurciaSedeCmsAdapter.INITIATOR_ORIGIN,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format: SigningFormat = SigningFormat.CMS,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = protocolId,
        context = SigningContext(
            profileId = profileId,
            profileVersion = profileVersion,
            origin = URI(origin).let { TrustedOrigin(it.scheme, it.host, 443) },
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
            navigationEpoch = 9,
            observedAt = clock.instant(),
        ),
        algorithm = algorithm,
        format = format,
        safeDescription = MurciaSedeCmsAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(payload, extraProperties),
    ).also { payload.fill(0) }
}
