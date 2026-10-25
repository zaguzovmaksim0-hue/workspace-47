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

class JccmCadesMigrationTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(goldenInstant, ZoneOffset.UTC)
    private val legacy = LegacyJccmCertificateLoginProbeCadesAdapter(clock)
    private val migrated = JccmCertificateLoginProbeCadesAdapter(clock)
    private val golden = adapterGoldenCases().first { it.name == "JCCM" }

    @Test
    fun jccmMigrationMatchesLegacySignedBytesAndCompleteCms() = runTest {
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
    fun jccmMigrationRetainsLiteralAndDescriptionGuards() = runTest {
        for (candidate in listOf(legacy, migrated)) {
            for (data in listOf(ByteArray(4), ByteArray(6), ByteArray(5) { 7 })) {
                request(data = data).use { wrong ->
                    assertEquals(
                        SigningErrorCode.PROTOCOL_FAILED,
                        (candidate.prepare(wrong, identity.chain) as ProtocolPrepareResult.Failure).code,
                    )
                }
            }
            request().use { owner ->
                prepare(candidate, owner).use { prepared ->
                    sign(prepared).use { local ->
                        request(description = "changed description").use { wrong ->
                            assertEquals(
                                SigningErrorCode.UNSUPPORTED_PROTOCOL,
                                (candidate.prepare(wrong, identity.chain) as ProtocolPrepareResult.Failure).code,
                            )
                            assertFailure(
                                SigningErrorCode.UNSUPPORTED_PROTOCOL, candidate.complete(wrong, prepared, local),
                            )
                        }
                        val completed = candidate.complete(owner, prepared, local)
                            as ProtocolCompletionResult.Success
                        completed.signature.close()
                    }
                }
            }
        }
    }

    @Test
    fun jccmMigrationRevalidatesPayloadBeforeConsumingStateAndAllowsRestoredOwner() = runTest {
        val mutations = listOf(
            MiniAppletPayloadCodec.encode(ByteArray(5) { 7 }, ""),
            MiniAppletPayloadCodec.encode(ByteArray(4) { 7 }, "x"),
            ByteArray(17),
        )
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                prepare(candidate, owner).use { prepared ->
                    sign(prepared).use { local ->
                        val borrowed = prepared.withBytesToSign { it }
                        val original = owner.payloadCopy()
                        try {
                            for (mutation in mutations) {
                                assertEquals(original.size, mutation.size)
                                owner.withPayload { mutation.copyInto(it) }
                                assertFailure(
                                    SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, local),
                                )
                                assertTrue(borrowed.any { it != 0.toByte() })
                            }
                            owner.withPayload { original.copyInto(it) }
                            val completed = candidate.complete(owner, prepared, local)
                                as ProtocolCompletionResult.Success
                            completed.signature.use { result ->
                                result.withBytes { AdapterGoldenVerifier.verify(golden, it, identity.certificate) }
                            }
                            assertTrue(borrowed.all { it == 0.toByte() })
                            assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, local))
                        } finally {
                            original.fill(0)
                        }
                    }
                }
            }
        }
        mutations.forEach { it.fill(0) }
    }

    @Test
    fun jccmMigrationRetainsOwnerIsolationAndClosedPayloadFailureBeforeConsumption() = runTest {
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
                            owner.close()
                            assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, local))
                            assertTrue(borrowed.any { it != 0.toByte() })
                            prepared.close()
                            assertTrue(borrowed.all { it == 0.toByte() })
                        }
                    }
                }
            }
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
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = JccmCertificateLoginProbeCadesAdapter.ID,
        context = SigningContext(
            profileId = golden.profile,
            profileVersion = 1,
            origin = TrustedOrigin("https", golden.host, 443),
            navigationId = NavigationId("jccm-migration"),
            observedAt = goldenInstant,
        ),
        algorithm = golden.algorithm,
        format = golden.format,
        safeDescription = description,
        payload = MiniAppletPayloadCodec.encode(data, ""),
    )
}
