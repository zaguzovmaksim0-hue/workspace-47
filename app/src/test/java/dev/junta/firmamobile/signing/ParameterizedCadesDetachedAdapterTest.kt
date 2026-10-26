package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterizedCadesDetachedAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val page = "https://example.test/sign"
    private val contract = CadesDetachedContract(
        id = SigningProtocolId("page-contract"),
        profileId = "page-profile",
        profileVersion = 1,
        initiatorOrigin = "https://example.test",
        algorithm = SigningAlgorithm.SHA1_WITH_RSA,
        contentBytes = 20,
        extraProperties = "",
    )

    @Test
    fun nullPageContractAcceptsAnyPageInPrepareAndComplete() = runBlocking {
        val adapter = ParameterizedCadesDetachedAdapter(contract, clock, BundledBouncyCastle.provider)
        for (pageUrl in listOf(null, page, "https://example.test/other")) {
            request(pageUrl).use { owner ->
                val prepared = (adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign
                prepared.use {
                    sign(it).use { signature ->
                        (adapter.complete(owner, it, signature) as ProtocolCompletionResult.Success).signature.close()
                    }
                }
            }
        }
    }

    @Test
    fun pinnedPageRejectsMismatchBeforePayloadOrStateConsumption() = runBlocking {
        val adapter = ParameterizedCadesDetachedAdapter(
            contract.copy(pageUrl = page), clock, BundledBouncyCastle.provider,
        )
        request(page).use { owner ->
            val prepared = (adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign
            prepared.use {
                val borrowed = it.withBytesToSign { bytes -> bytes }
                sign(it).use { signature ->
                    for (wrongPage in listOf(null, "https://example.test/other")) {
                        request(wrongPage).use { wrong ->
                            wrong.close()
                            assertEquals(
                                SigningErrorCode.UNSUPPORTED_PROTOCOL,
                                (adapter.prepare(wrong, identity.chain) as ProtocolPrepareResult.Failure).code,
                            )
                            assertEquals(
                                SigningErrorCode.UNSUPPORTED_PROTOCOL,
                                (adapter.complete(wrong, it, signature) as ProtocolCompletionResult.Failure).code,
                            )
                            assertTrue(borrowed.any { byte -> byte != 0.toByte() })
                        }
                    }
                    (adapter.complete(owner, it, signature) as ProtocolCompletionResult.Success).signature.close()
                    assertTrue(borrowed.all { byte -> byte == 0.toByte() })
                }
            }
        }
    }

    @Test
    fun absentCertificateMatcherAcceptsEitherSigningCertificate() = runBlocking {
        val adapter = ParameterizedCadesDetachedAdapter(contract, clock, BundledBouncyCastle.provider)
        for (chain in listOf(identity.chain, freshSyntheticIdentity().chain)) {
            request(null).use { owner ->
                val result = adapter.prepare(owner, chain)
                if (result is ProtocolPrepareResult.Success) result.preSign.close()
                assertTrue(result is ProtocolPrepareResult.Success)
            }
        }
    }

    @Test
    fun certificateMatcherBindsPropertiesToFirstSigningCertificate() = runBlocking {
        val bound = contract.copy(certificatePropertiesMatcher = { properties, certificate ->
            properties.isEmpty() && certificate.encoded.contentEquals(identity.certificate.encoded)
        })
        val adapter = ParameterizedCadesDetachedAdapter(bound, clock, BundledBouncyCastle.provider)
        request(null).use { owner ->
            (adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign.use { prepared ->
                sign(prepared).use { signature ->
                    (adapter.complete(owner, prepared, signature) as ProtocolCompletionResult.Success).signature.close()
                }
            }
            val other = freshSyntheticIdentity()
            val result = adapter.prepare(owner, listOf(other.certificate, identity.certificate))
            if (result is ProtocolPrepareResult.Success) result.preSign.close()
            assertEquals(SigningErrorCode.PROTOCOL_FAILED, (result as? ProtocolPrepareResult.Failure)?.code)
        }
    }

    @Test
    fun certificateMatcherCannotBeCombinedWithCompletionRevalidation() {
        val failure = runCatching {
            contract.copy(revalidatePayloadOnComplete = true, certificatePropertiesMatcher = { _, _ -> true })
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        contract.copy(revalidatePayloadOnComplete = true)
    }

    @Test
    fun certificateMatcherExceptionsBecomeProtocolFailures() = runBlocking {
        val adapter = ParameterizedCadesDetachedAdapter(
            contract.copy(certificatePropertiesMatcher = { _, _ -> error("invalid certificate properties") }),
            clock, BundledBouncyCastle.provider,
        )
        request(null).use { owner ->
            val result = adapter.prepare(owner, identity.chain)
            if (result is ProtocolPrepareResult.Success) result.preSign.close()
            assertEquals(SigningErrorCode.PROTOCOL_FAILED, (result as? ProtocolPrepareResult.Failure)?.code)
        }
    }

    @Test
    fun certificateMatcherRunsOnlyAfterChainAndPayloadGuards() = runBlocking {
        var calls = 0
        val bound = contract.copy(certificatePropertiesMatcher = { _, _ -> calls++; true })
        request(null).use { owner ->
            for (chain in listOf(emptyList(), List(11) { identity.certificate })) {
                val adapter = ParameterizedCadesDetachedAdapter(bound, clock, BundledBouncyCastle.provider)
                assertEquals(
                    SigningErrorCode.UNSUPPORTED_PROTOCOL,
                    (adapter.prepare(owner, chain) as ProtocolPrepareResult.Failure).code,
                )
            }
            for (invalid in listOf(
                bound.copy(contentValidator = { false }),
                bound.copy(extraPropertiesMatcher = { false }),
            )) {
                val adapter = ParameterizedCadesDetachedAdapter(invalid, clock, BundledBouncyCastle.provider)
                assertEquals(
                    SigningErrorCode.PROTOCOL_FAILED,
                    (adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Failure).code,
                )
            }
            assertEquals(0, calls)
            val adapter = ParameterizedCadesDetachedAdapter(bound, clock, BundledBouncyCastle.provider)
            (adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign.close()
            assertEquals(1, calls)
        }
    }

    @Test
    fun completionPayloadRevalidationDefaultErrorPreservesState() = runBlocking {
        assertRevalidationPolicy(
            contract.copy(revalidatePayloadOnComplete = true), SigningErrorCode.PROTOCOL_FAILED,
        )
    }

    @Test
    fun completionPayloadRevalidationConfiguredErrorPreservesState() = runBlocking {
        assertRevalidationPolicy(
            contract.copy(
                revalidatePayloadOnComplete = true,
                payloadRevalidationError = SigningErrorCode.UNSUPPORTED_PROTOCOL,
            ),
            SigningErrorCode.UNSUPPORTED_PROTOCOL,
        )
    }

    @Test
    fun completionPayloadErrorDoesNotChangeSignatureFailures() = runBlocking {
        val adapter = ParameterizedCadesDetachedAdapter(
            contract.copy(
                revalidatePayloadOnComplete = true,
                payloadRevalidationError = SigningErrorCode.UNSUPPORTED_PROTOCOL,
            ),
            clock, BundledBouncyCastle.provider,
        )
        request(null).use { owner ->
            (adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign.use { prepared ->
                val borrowed = prepared.withBytesToSign { it }
                LocalSignature(byteArrayOf(1, 2, 3)).use { invalid ->
                    assertEquals(
                        SigningErrorCode.PROTOCOL_FAILED,
                        (adapter.complete(owner, prepared, invalid) as ProtocolCompletionResult.Failure).code,
                    )
                    assertTrue(borrowed.all { it == 0.toByte() })
                    assertEquals(
                        SigningErrorCode.PROTOCOL_FAILED,
                        (adapter.complete(owner, prepared, invalid) as ProtocolCompletionResult.Failure).code,
                    )
                }
            }
        }
    }

    @Test
    fun completionPayloadErrorIsUnusedWhenRevalidationIsDisabled() = runBlocking {
        val adapter = ParameterizedCadesDetachedAdapter(
            contract.copy(payloadRevalidationError = SigningErrorCode.UNSUPPORTED_PROTOCOL),
            clock, BundledBouncyCastle.provider,
        )
        request(null).use { owner ->
            (adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign.use { prepared ->
                sign(prepared).use { local ->
                    owner.withPayload { it.fill(0) }
                    (adapter.complete(owner, prepared, local) as ProtocolCompletionResult.Success).signature.close()
                }
            }
        }
    }

    private suspend fun assertRevalidationPolicy(policy: CadesDetachedContract, expected: SigningErrorCode) {
        val adapter = ParameterizedCadesDetachedAdapter(policy.copy(pageUrl = page), clock, BundledBouncyCastle.provider)
        request(page).use { owner ->
            (adapter.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign.use { prepared ->
                val borrowed = prepared.withBytesToSign { it }
                val original = owner.payloadCopy()
                val mutations = listOf(ByteArray(original.size), MiniAppletPayloadCodec.encode(ByteArray(19), "?"))
                sign(prepared).use { local ->
                    try {
                        request(null).use { wrong ->
                            wrong.close()
                            assertEquals(
                                SigningErrorCode.UNSUPPORTED_PROTOCOL,
                                (adapter.complete(wrong, prepared, local) as ProtocolCompletionResult.Failure).code,
                            )
                        }
                        for (mutation in mutations) {
                            assertEquals(original.size, mutation.size)
                            owner.withPayload { mutation.copyInto(it) }
                            assertEquals(expected, (adapter.complete(owner, prepared, local) as ProtocolCompletionResult.Failure).code)
                            assertTrue(borrowed.any { it != 0.toByte() })
                        }
                        owner.withPayload { original.copyInto(it) }
                        (adapter.complete(owner, prepared, local) as ProtocolCompletionResult.Success).signature.close()
                        assertTrue(borrowed.all { it == 0.toByte() })
                        assertEquals(
                            SigningErrorCode.PROTOCOL_FAILED,
                            (adapter.complete(owner, prepared, local) as ProtocolCompletionResult.Failure).code,
                        )
                        owner.close()
                        assertEquals(expected, (adapter.complete(owner, prepared, local) as ProtocolCompletionResult.Failure).code)
                    } finally {
                        original.fill(0)
                        mutations.forEach { it.fill(0) }
                    }
                }
            }
        }
    }

    private fun sign(prepared: PreSignResult): LocalSignature =
        (prepared.withBytesToSign {
            JcaLocalSignatureEngine().sign(it, identity, SigningAlgorithm.SHA1_WITH_RSA)
        } as LocalSignatureResult.Success).signature

    private fun request(pageUrl: String?) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = contract.id,
        context = SigningContext(
            contract.profileId, contract.profileVersion, TrustedOrigin("https", "example.test", 443),
            NavigationId("page-contract"), observedAt = clock.instant(), pageUrl = pageUrl,
        ),
        algorithm = contract.algorithm,
        format = SigningFormat.CADES,
        safeDescription = "Synthetic page contract",
        payload = MiniAppletPayloadCodec.encode(ByteArray(20), ""),
    )
}
