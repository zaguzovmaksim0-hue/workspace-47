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
