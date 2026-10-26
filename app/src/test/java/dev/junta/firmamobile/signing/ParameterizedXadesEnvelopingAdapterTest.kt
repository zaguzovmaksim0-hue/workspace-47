package dev.junta.firmamobile.signing

import java.security.cert.X509Certificate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterizedXadesEnvelopingAdapterTest {
    @Test
    fun bothContractsProduceIndependentlyVerifiedEnvelopes() = runBlocking {
        for (cdti in listOf(false, true)) {
            val f = XadesMigrationFixture(cdti)
            val adapter = ParameterizedXadesEnvelopingAdapter(contract(f), f.clock)
            assertEquals(f.protocol, adapter.id)
            f.request().use { owner ->
                f.prepare(adapter, owner).use { prepared ->
                    f.sign(prepared).use { f.finish(adapter, owner, prepared, it) }
                }
            }
        }
    }

    @Test
    fun payloadPreflightPrecedesTheChainGuardOnlyWhenConfigured() = runBlocking {
        val f = XadesMigrationFixture(false)
        for (early in listOf(false, true)) {
            val events = mutableListOf<String>()
            val selected = contract(f).copy(
                contextMatches = { events += "context"; true },
                payloadValidator = { events += "payload"; true },
                validatePayloadOnMatch = early,
            )
            val chain = object : AbstractList<X509Certificate>() {
                override val size: Int get() { events += "chain"; return 0 }
                override fun get(index: Int): X509Certificate = error("Empty chain")
            }
            f.request().use {
                assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                    ParameterizedXadesEnvelopingAdapter(selected, f.clock).prepare(it, chain))
            }
            assertEquals(if (early) listOf("context", "payload", "chain") else listOf("context", "chain"), events)
        }
    }

    @Test
    fun propertyAndPayloadValidationOrderPreservesTheFailureStage() = runBlocking {
        val f = XadesMigrationFixture(false)
        for (early in listOf(false, true)) {
            var calls = 0
            val selected = contract(f).copy(
                payloadValidator = { calls++; true }, validatePayloadOnMatch = early,
            )
            f.request(extra = "wrong").use {
                val error = if (early) SigningErrorCode.UNSUPPORTED_PROTOCOL else SigningErrorCode.PROTOCOL_FAILED
                assertEquals(ProtocolPrepareResult.Failure(error),
                    ParameterizedXadesEnvelopingAdapter(selected, f.clock).prepare(it, f.identity.chain))
            }
            assertEquals(if (early) 1 else 0, calls)
        }
    }

    @Test
    fun rejectedContextDoesNotReadClosedPayloadOrCertificateChain() = runBlocking {
        val f = XadesMigrationFixture(false)
        val selected = contract(f).copy(
            contextMatches = { false }, payloadValidator = { error("Payload must not be inspected") },
            validatePayloadOnMatch = true,
        )
        val chain = object : AbstractList<X509Certificate>() {
            override val size: Int get() = error("Chain must not be inspected")
            override fun get(index: Int): X509Certificate = error("Chain must not be inspected")
        }
        f.request().use {
            it.close()
            assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                ParameterizedXadesEnvelopingAdapter(selected, f.clock).prepare(it, chain))
        }
    }

    @Test
    fun decodedBuffersAndConsumedSignedBytesAreClearedAndReplayIsRejected() = runBlocking {
        val f = XadesMigrationFixture(false)
        var borrowedPayload: ByteArray? = null
        val selected = contract(f).copy(payloadValidator = { borrowedPayload = it; it.size == 32 })
        val adapter = ParameterizedXadesEnvelopingAdapter(selected, f.clock)
        f.request().use { owner ->
            f.prepare(adapter, owner).use { prepared ->
                assertTrue(checkNotNull(borrowedPayload).all { it == 0.toByte() })
                val borrowedSignedInfo = prepared.withBytesToSign { it }
                f.sign(prepared).use { local ->
                    f.finish(adapter, owner, prepared, local)
                    assertTrue(borrowedSignedInfo.all { it == 0.toByte() })
                    assertTrue(checkNotNull(borrowedPayload).all { it == 0.toByte() })
                    assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                        adapter.complete(owner, prepared, local))
                }
            }
        }
    }

    private fun contract(f: XadesMigrationFixture) = XadesEnvelopingContract(
        id = f.protocol, profileId = f.profile, profileVersion = 1,
        algorithm = f.algorithm, safeDescription = f.description,
        contextMatches = {
            it.origin.scheme == "https" && it.origin.host == f.host && it.origin.port == 443 &&
                (!f.cdti || it.pageUrl == f.page)
        },
        extraProperties = f.extra,
        payloadValidator = { if (f.cdti) LegacyCdtiXadesEnvelopingAdapter.isExactChallenge(it) else it.size == 32 },
        validatePayloadOnMatch = f.cdti,
    )
}
