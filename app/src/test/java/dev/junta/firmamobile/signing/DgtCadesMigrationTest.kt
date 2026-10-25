package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DgtCadesMigrationTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(goldenInstant, ZoneOffset.UTC)
    private val legacy = LegacyDgtVerificationCadesAdapter(clock)
    private val migrated = DgtVerificationCadesAdapter(clock)
    private val golden = adapterGoldenCases().first { it.name == "DGT" }

    @Test
    fun dgtMigrationMatchesLegacySignedBytesAndCompleteCms() = runTest {
        request().use { oldOwner ->
            request().use { newOwner ->
                prepare(legacy, oldOwner).use { old ->
                    prepare(migrated, newOwner).use { new ->
                        old.withBytesToSign { before ->
                            new.withBytesToSign { after -> assertArrayEquals(before, after) }
                        }
                        sign(old).use { local ->
                            val before = legacy.complete(oldOwner, old, local) as ProtocolCompletionResult.Success
                            val after = migrated.complete(newOwner, new, local) as ProtocolCompletionResult.Success
                            before.signature.use { expected ->
                                after.signature.use { actual ->
                                    expected.withBytes { one ->
                                        actual.withBytes { two ->
                                            assertArrayEquals(one, two)
                                            AdapterGoldenVerifier.verify(golden, one, identity.certificate)
                                            AdapterGoldenVerifier.verify(golden, two, identity.certificate)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun dgtMigrationRetainsLiteralAndExactPropertiesGuards() = runTest {
        for (candidate in listOf(legacy, migrated)) {
            val wrongRequests = listOf(
                request(data = ByteArray(14)),
                request(data = ByteArray(16)),
                request(data = ByteArray(15) { 7 }),
                request(properties = "filter=nonexpired"),
                request(properties = "filter=nonexpired:\nmode=explicit"),
            )
            wrongRequests.forEach { wrong ->
                wrong.use {
                    assertEquals(
                        SigningErrorCode.PROTOCOL_FAILED,
                        (candidate.prepare(it, identity.chain) as ProtocolPrepareResult.Failure).code,
                    )
                }
            }
        }
    }

    @Test
    fun dgtMigrationAllowsDifferentDescriptionAndDoesNotRevalidatePayloadOnComplete() = runTest {
        for (candidate in listOf(legacy, migrated)) {
            request(description = "different description").use { owner ->
                prepare(candidate, owner).use { prepared ->
                    sign(prepared).use { local ->
                        val borrowed = prepared.withBytesToSign { it }
                        owner.withPayload { it.fill(0) }
                        verifyCompletion(candidate, owner, prepared, local)
                        assertTrue(borrowed.all { it == 0.toByte() })
                        assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, local))
                    }
                }
            }
        }
    }

    @Test
    fun dgtMigrationRetainsOwnerIsolationAndCanCompleteWithClosedPayload() = runTest {
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                request().use { impostor ->
                    prepare(candidate, owner).use { prepared ->
                        val borrowed = prepared.withBytesToSign { it }
                        sign(prepared).use { local ->
                            assertEquals(owner.requestId, impostor.requestId)
                            assertFailure(
                                SigningErrorCode.PROTOCOL_FAILED, candidate.complete(impostor, prepared, local),
                            )
                            assertTrue(borrowed.any { it != 0.toByte() })
                            owner.close()
                            verifyCompletion(candidate, owner, prepared, local)
                            assertTrue(borrowed.all { it == 0.toByte() })
                        }
                    }
                }
            }
        }
    }

    @Test
    fun dgtMigrationConsumesStateAfterInvalidSignatureAndRejectsRetry() = runTest {
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                prepare(candidate, owner).use { prepared ->
                    val borrowed = prepared.withBytesToSign { it }
                    sign(prepared).use { valid ->
                        LocalSignature(ByteArray(256)).use { invalid ->
                            assertFailure(
                                SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, invalid),
                            )
                        }
                        assertTrue(borrowed.all { it == 0.toByte() })
                        assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, valid))
                    }
                }
            }
        }
    }

    private suspend fun verifyCompletion(
        candidate: SigningProtocolAdapter,
        owner: NormalizedSignRequest,
        prepared: PreSignResult,
        local: LocalSignature,
    ) {
        val completed = candidate.complete(owner, prepared, local) as ProtocolCompletionResult.Success
        completed.signature.use { result ->
            result.withBytes { AdapterGoldenVerifier.verify(golden, it, identity.certificate) }
        }
    }

    private suspend fun prepare(candidate: SigningProtocolAdapter, owner: NormalizedSignRequest): PreSignResult =
        (candidate.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign

    private suspend fun sign(prepared: PreSignResult): LocalSignature =
        (prepared.withBytesToSign { JcaLocalSignatureEngine().sign(it, identity, SigningAlgorithm.SHA1_WITH_RSA) }
            as LocalSignatureResult.Success).signature

    private fun assertFailure(code: SigningErrorCode, result: ProtocolCompletionResult) {
        assertEquals(code, (result as ProtocolCompletionResult.Failure).code)
    }

    private fun request(
        data: ByteArray = golden.data,
        description: String = golden.description,
        properties: String = golden.properties,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = DgtVerificationCadesAdapter.ID,
        context = SigningContext(
            profileId = golden.profile,
            profileVersion = 1,
            origin = TrustedOrigin("https", golden.host, 443),
            navigationId = NavigationId("dgt-migration"),
            observedAt = goldenInstant,
        ),
        algorithm = golden.algorithm,
        format = golden.format,
        safeDescription = description,
        payload = MiniAppletPayloadCodec.encode(data, properties),
    )
}
