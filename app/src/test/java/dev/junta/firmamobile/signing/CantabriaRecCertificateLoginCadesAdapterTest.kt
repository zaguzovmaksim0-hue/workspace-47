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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CantabriaRecCertificateLoginCadesAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = CantabriaRecCertificateLoginCadesAdapter(clock)

    @Test
    fun acceptsOnlyExactFortyHexByteChallengeAndObservedTuple() = runTest {
        val request = request()
        val prepared = adapter.prepare(request, identity.chain)
        assertTrue(prepared is ProtocolPrepareResult.Success)
        (prepared as ProtocolPrepareResult.Success).preSign.close()
        request.close()
    }

    @Test
    fun rejectsWrongContractDimensionsPayloadAndProperties() = runTest {
        assertFailure(request(profileId = "other-profile"))
        assertFailure(request(profileVersion = 2))
        assertFailure(request(protocolId = SigningProtocolId("other-protocol")))
        assertFailure(request(safeDescription = "Other operation"))
        assertFailure(request(origin = "https://rec.cantabria.es.evil.example"))
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA))
        assertFailure(request(format = SigningFormat.XADES))
        assertFailure(request(payload = "0123456789abcdef0123456789abcdef012345678".encodeToByteArray()), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(payload = "0123456789abcdef0123456789abcdef0123456F".encodeToByteArray()), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(extraProperties = "mode=implicit"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(payload = ByteArray(39) { 'a'.code.toByte() }), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(payload = ByteArray(40) { 0xff.toByte() }), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(extraProperties = "filters=\nmode=implicit\nextra=true"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(
            request(
                extraProperties =
                    "mode=implicit\nfilters=",
            ),
            SigningErrorCode.PROTOCOL_FAILED,
        )
    }

    @Test
    fun signsTheExactChallengeWithSha512AndRejectsTamperingAndReplay() = runTest {
        val challenge = "0123456789abcdef0123456789abcdef01234567".encodeToByteArray()
        request(challenge.copyOf()).use { request ->
            val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
            prepared.preSign.use { preSign ->
                val local = preSign.withBytesToSign { bytes ->
                    JcaLocalSignatureEngine().sign(bytes, identity, SigningAlgorithm.SHA512_WITH_RSA)
                } as LocalSignatureResult.Success
                local.signature.use { signature ->
                    val completed = adapter.complete(request, preSign, signature) as ProtocolCompletionResult.Success
                    completed.signature.use { result ->
                        result.withBytes { document ->
                            val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)
                            try {
                                assertTrue(CadesDetachedCodec.validate(
                                    signatureDocument = document,
                                    detachedContent = challenge,
                                    expectedContentBytes = CantabriaRecCertificateLoginCadesAdapter.CHALLENGE_BYTES,
                                    expectedCertificateFingerprint = fingerprint,
                                    signingAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
                                ))
                                val tampered = challenge.copyOf().also { it[0] = 'f'.code.toByte() }
                                assertFalse(CadesDetachedCodec.validate(
                                    signatureDocument = document,
                                    detachedContent = tampered,
                                    expectedContentBytes = CantabriaRecCertificateLoginCadesAdapter.CHALLENGE_BYTES,
                                    expectedCertificateFingerprint = fingerprint,
                                    signingAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
                                ))
                                tampered.fill(0)
                            } finally {
                                fingerprint.fill(0)
                            }
                        }
                    }
                    assertEquals(
                        ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                        adapter.complete(request, preSign, signature),
                    )
                }
            }
        }
        challenge.fill(0)
    }

    @Test
    fun rejectsMissingAndExcessiveCertificateChains() = runTest {
        request().use { request ->
            assertEquals(
                ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                adapter.prepare(request, emptyList()),
            )
            assertEquals(
                ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                adapter.prepare(request, List(11) { identity.certificate }),
            )
        }
    }

    @Test
    fun completionCannotConsumeAnotherRequestsState() = runTest {
        request().use { owner ->
            request().use { other ->
                val prepared = adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Success
                prepared.preSign.use { preSign ->
                    LocalSignature(byteArrayOf(1)).use { signature ->
                        assertEquals(
                            ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                            adapter.complete(other, preSign, signature),
                        )
                    }
                    // A rejected owner must not consume the original request's state.
                    preSign.withBytesToSign { assertTrue(it.isNotEmpty()) }
                }
            }
        }
    }

    @Test
    fun rejectsInvalidLocalSignatureAndDoesNotAllowRetryingConsumedState() = runTest {
        request().use { request ->
            val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
            prepared.preSign.use { preSign ->
                LocalSignature(byteArrayOf(1)).use { signature ->
                    repeat(2) {
                        assertEquals(
                            ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                            adapter.complete(request, preSign, signature),
                        )
                    }
                }
            }
        }
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
        payload: ByteArray = "0123456789abcdef0123456789abcdef01234567".encodeToByteArray(),
        extraProperties: String = CantabriaRecCertificateLoginCadesAdapter.EXPECTED_EXTRA_PROPERTIES,
        requestId: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId: SigningProtocolId = CantabriaRecCertificateLoginCadesAdapter.ID,
        profileId: String = CantabriaRecCertificateLoginCadesAdapter.PROFILE_ID,
        profileVersion: Int = CantabriaRecCertificateLoginCadesAdapter.PROFILE_VERSION,
        origin: String = CantabriaRecCertificateLoginCadesAdapter.INITIATOR_ORIGIN,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
        format: SigningFormat = SigningFormat.CADES,
        safeDescription: String = CantabriaRecCertificateLoginCadesAdapter.SAFE_DESCRIPTION,
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
        safeDescription = safeDescription,
        payload = MiniAppletPayloadCodec.encode(payload, extraProperties),
    ).also { payload.fill(0) }
}
