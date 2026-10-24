package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SigningAdapterGoldenCharacterizationTest(private val case: AdapterGoldenCase) {
    private val identity = syntheticIdentity()
    private val owned = mutableListOf<Closeable>()
    private val adapter get() = case.adapter
    private val unsupported = SigningErrorCode.UNSUPPORTED_PROTOCOL
    private val failed = SigningErrorCode.PROTOCOL_FAILED
    private val inputError get() = if (case.name == "CDTI") unsupported else failed

    @After
    fun closeOwnedBuffers() {
        owned.asReversed().forEach { it.close() }
    }

    @Test
    fun prepareCompleteMatchesSemanticGoldenAndConsumesOnce() = runTest {
        val request = request()
        val preSign = prepare(request)
        val borrowedInput = preSign.withBytesToSign { it }
        val local = sign(preSign)
        val borrowedLocal = local.withBytes { it }
        val result = complete(request, preSign, local)
        assertZero(borrowedInput)
        assertThrows(IllegalStateException::class.java) { preSign.withBytesToSign { it.size } }
        result.withBytes { AdapterGoldenVerifier.verify(case, it, identity.certificate) }
        assertCompletionFailure(failed, adapter.complete(request, preSign, local))
        val borrowedResult = result.withBytes { it }
        result.close()
        result.close()
        assertZero(borrowedResult)
        assertThrows(IllegalStateException::class.java) { result.withBytes { it.size } }
        local.close()
        assertZero(borrowedLocal)
        val borrowedPayload = request.withPayload { it }
        request.close()
        request.close()
        assertZero(borrowedPayload)
    }

    @Test
    fun independentPreparationsRetainTheirContainerSemantics() = runTest {
        val firstOwner = request()
        val secondOwner = request()
        val first = prepare(firstOwner)
        val second = prepare(secondOwner)
        val firstInput = first.withBytesToSign { it.copyOf() }
        val secondInput = second.withBytesToSign { it.copyOf() }
        try {
            val firstResult = complete(firstOwner, first, sign(first))
            val secondResult = complete(secondOwner, second, sign(second))
            firstResult.withBytes { AdapterGoldenVerifier.verify(case, it, identity.certificate) }
            secondResult.withBytes { AdapterGoldenVerifier.verify(case, it, identity.certificate) }
            if (case.format == SigningFormat.CADES) {
                assertArrayEquals(firstInput, secondInput)
                firstResult.withBytes { one -> secondResult.withBytes { two -> assertArrayEquals(one, two) } }
            }
            // XML has random UUIDs: both actual documents must meet the independent semantic golden.
        } finally {
            firstInput.fill(0)
            secondInput.fill(0)
        }
    }

    @Test
    fun sameUuidAndTupleDoNotTransferOwnershipOrConsumeOriginalState() = runTest {
        val owner = request()
        val impostor = request()
        assertEquals(owner.requestId, impostor.requestId)
        val preSign = prepare(owner)
        val local = sign(preSign)
        val original = preSign.withBytesToSign { it.copyOf() }
        try {
            assertCompletionFailure(failed, adapter.complete(impostor, preSign, local))
            preSign.withBytesToSign { assertArrayEquals(original, it) }
            complete(owner, preSign, local).withBytes { AdapterGoldenVerifier.verify(case, it, identity.certificate) }
        } finally {
            original.fill(0)
        }
    }

    @Test
    fun wrongTupleFailsBeforeConsumptionAndOriginalCanStillComplete() = runTest {
        val owner = request()
        val preSign = prepare(owner)
        val local = sign(preSign)
        val wrong = mutableListOf(
            request(origin = TrustedOrigin("https", "${case.host}.invalid", 443)),
            request(origin = TrustedOrigin("http", case.host, 80)),
            request(origin = TrustedOrigin("https", case.host, 444)),
            request(protocol = SigningProtocolId("other-golden-protocol")),
            request(profile = "other-golden-profile"),
            request(version = 2),
            request(algorithm = if (case.algorithm == SigningAlgorithm.SHA1_WITH_RSA) {
                SigningAlgorithm.SHA256_WITH_RSA
            } else SigningAlgorithm.SHA1_WITH_RSA),
            request(format = SigningFormat.PADES),
        )
        if (case.descriptionGuard) wrong += request(description = "changed description")
        if (case.page != null) wrong += request(page = "https://${case.host}/wrong-page")
        wrong.forEach { mutation ->
            assertPrepareFailure(unsupported, adapter.prepare(mutation, identity.chain))
            assertCompletionFailure(unsupported, adapter.complete(mutation, preSign, local))
        }
        complete(owner, preSign, local).withBytes { AdapterGoldenVerifier.verify(case, it, identity.certificate) }
    }

    @Test
    fun emptyOversizedPayloadAndUnknownPropertiesHavePinnedPrepareErrors() = runTest {
        listOf(ByteArray(0), ByteArray(case.maxBytes + 1) { 'a'.code.toByte() }).forEach { data ->
            assertPrepareFailure(
                inputError,
                adapter.prepare(request(data = data, adapterBoundaryEnvelope = true), identity.chain),
            )
        }
        assertPrepareFailure(
            inputError,
            adapter.prepare(request(properties = properties() + "\nunknown=true"), identity.chain),
        )
    }

    @Test
    fun certificateChainBoundsPreserveFamilySpecificErrors() = runTest {
        val request = request()
        assertPrepareFailure(unsupported, adapter.prepare(request, emptyList()))
        val max = if (case.format == SigningFormat.CADES) 10 else 8
        assertPrepareFailure(
            if (case.format == SigningFormat.CADES) unsupported else failed,
            adapter.prepare(request, List(max + 1) { identity.certificate }),
        )
    }

    @Test
    fun cancellationClosesAndZeroizesExposedInputWithoutAllowingCompletion() = runTest {
        val request = request()
        val preSign = prepare(request)
        val local = sign(preSign)
        val borrowed = preSign.withBytesToSign { it }
        preSign.close()
        preSign.close()
        assertZero(borrowed)
        assertThrows(IllegalStateException::class.java) { preSign.withBytesToSign { it.size } }
        assertCompletionFailure(failed, adapter.complete(request, preSign, local))
    }

    @Test
    fun invalidLocalSignatureConsumesStateAndCannotBeRetriedWithValidSignature() = runTest {
        val request = request()
        val preSign = prepare(request)
        val valid = sign(preSign)
        val borrowed = preSign.withBytesToSign { it }
        val invalid = keep(LocalSignature(ByteArray(256)))
        assertCompletionFailure(failed, adapter.complete(request, preSign, invalid))
        assertZero(borrowed)
        assertCompletionFailure(failed, adapter.complete(request, preSign, valid))
    }

    @Test
    fun completePayloadRevalidationRetainsItsLegacyErrorAndConsumptionOrder() = runTest {
        val request = request()
        val preSign = prepare(request)
        val local = sign(preSign)
        val originalPayload = request.payloadCopy()
        val borrowed = preSign.withBytesToSign { it }
        try {
            // Existing internal seam, no reflection or production hook: damage the same owner's envelope.
            request.withPayload { it.fill(0) }
            val result = adapter.complete(request, preSign, local)
            if (case.revalidationError == null) {
                val signature = keep((result as ProtocolCompletionResult.Success).signature)
                signature.withBytes { AdapterGoldenVerifier.verify(case, it, identity.certificate) }
                assertZero(borrowed)
            } else {
                assertCompletionFailure(checkNotNull(case.revalidationError), result)
                assertTrue(borrowed.any { it != 0.toByte() })
                request.withPayload { originalPayload.copyInto(it) }
                complete(request, preSign, local)
                assertZero(borrowed)
            }
        } finally {
            originalPayload.fill(0)
        }
    }

    @Test
    fun decodedPropertiesMutationOnCompletePreservesRevalidationPolicy() = runTest {
        val request = request()
        val preSign = prepare(request)
        val local = sign(preSign)
        val original = request.payloadCopy()
        val props = properties()
        val changed = if (props.isNotEmpty()) {
            MiniAppletPayloadCodec.encode(case.data, "!" + props.drop(1))
        } else {
            // Same envelope size, but decoded nonempty properties (and one fewer data byte).
            MiniAppletPayloadCodec.encode(case.data.copyOf(case.data.size - 1), "x")
        }
        try {
            assertEquals(original.size, changed.size)
            request.withPayload { changed.copyInto(it) }
            val result = adapter.complete(request, preSign, local)
            if (case.revalidationError == null) {
                keep((result as ProtocolCompletionResult.Success).signature).withBytes {
                    AdapterGoldenVerifier.verify(case, it, identity.certificate)
                }
            } else {
                assertCompletionFailure(checkNotNull(case.revalidationError), result)
                request.withPayload { original.copyInto(it) }
                complete(request, preSign, local)
            }
        } finally {
            original.fill(0)
            changed.fill(0)
        }
    }

    @Test
    fun closedRequestPrepareAndCompletePreserveExistingBoundary() = runTest {
        val request = request()
        val preSign = prepare(request)
        val local = sign(preSign)
        request.close()
        assertPrepareFailure(inputError, adapter.prepare(request, identity.chain))
        val result = adapter.complete(request, preSign, local)
        if (case.revalidationError == null) {
            keep((result as ProtocolCompletionResult.Success).signature)
        } else {
            assertCompletionFailure(checkNotNull(case.revalidationError), result)
            // Early validation failures leave state owned by preSign until explicit cancellation.
            assertTrue(preSign.withBytesToSign { it.isNotEmpty() })
        }
    }

    @Test
    fun otherPortalCannotConsumePreparedState() = runTest {
        val request = request()
        val preSign = prepare(request)
        val local = sign(preSign)
        val other = adapterGoldenCases().first { it.name != case.name }.adapter
        assertCompletionFailure(unsupported, other.complete(request, preSign, local))
        complete(request, preSign, local)
    }

    @Test
    fun documentAndPrintableLimitsIncludeBothEndpoints() = runTest {
        if (case.name !in setOf("Lleida", "Badajoz", "Tenerife", "Eivissa")) return@runTest
        for (size in listOf(1, case.maxBytes)) {
            val data = ByteArray(size) { 'A'.code.toByte() }
            val request = request(data = data, adapterBoundaryEnvelope = true)
            val preSign = prepare(request)
            complete(request, preSign, sign(preSign)).withBytes {
                AdapterGoldenVerifier.verify(case.copy(data = data), it, identity.certificate)
            }
        }
        if (case.name in setOf("Lleida", "Badajoz")) {
            for (byte in listOf(0x1f, 0x7f, 0x80)) {
                assertPrepareFailure(failed, adapter.prepare(request(data = byteArrayOf(byte.toByte())), identity.chain))
            }
        }
    }

    @Test
    fun documentEnvelopeLimitIsSeparateFromTheAdaptersDecodedDocumentLimit() = runTest {
        if (case.name !in setOf("Tenerife", "Eivissa")) return@runTest
        val properties = properties()
        val propertiesBytes = properties.encodeToByteArray().size
        val largestEncodableData = 524_288 - 12 - propertiesBytes
        val data = ByteArray(largestEncodableData) { 'A'.code.toByte() }
        val request = request(data = data)
        assertEquals(524_288, request.payloadSize)
        val preSign = prepare(request)
        complete(request, preSign, sign(preSign)).withBytes {
            AdapterGoldenVerifier.verify(case.copy(data = data), it, identity.certificate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MiniAppletPayloadCodec.encode(ByteArray(largestEncodableData + 1), properties)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MiniAppletPayloadCodec.encode(ByteArray(case.maxBytes), properties)
        }
    }

    @Test
    fun literalPropertiesDoNotSilentlyAcquireAnotherPortalsNormalization() = runTest {
        if (case.name !in setOf("Lleida", "Badajoz")) return@runTest
        val variants = listOf(
            "filters=nonexpired:true;authCert:true\nheadless=true\npolicy=FirmaAGE",
            properties().replace("\n", "\r\n"),
        )
        for (properties in variants) {
            val request = request(properties = properties)
            if (case.name == "Badajoz") {
                val preSign = prepare(request)
                complete(request, preSign, sign(preSign)).withBytes {
                    AdapterGoldenVerifier.verify(case, it, identity.certificate)
                }
            } else {
                assertPrepareFailure(failed, adapter.prepare(request, identity.chain))
            }
        }
        for (suffix in listOf("\nheadless=true", "\nunknown=true")) {
            assertPrepareFailure(failed, adapter.prepare(request(properties = properties() + suffix), identity.chain))
        }
    }

    @Test
    fun eivissaSelectedCertificateMimeAndPropertiesBoundStayPinned() = runTest {
        if (case.name != "Eivissa") return@runTest
        val withMime = request(properties = properties() + "mimeType=application/pdf\n")
        val preSign = prepare(withMime)
        complete(withMime, preSign, sign(preSign)).withBytes {
            AdapterGoldenVerifier.verify(case, it, identity.certificate)
        }
        val otherCertificate = freshSyntheticIdentity().certificate
        val foreign = properties().replace(
            Base64.getEncoder().encodeToString(identity.certificate.encoded),
            Base64.getEncoder().encodeToString(otherCertificate.encoded),
        )
        for (properties in listOf(
            foreign, properties().trimEnd('\n'), properties().replace("\n", "\r\n"),
            properties() + "mimeType=bad type\n", "x".repeat(16_385),
        )) {
            assertPrepareFailure(failed, adapter.prepare(request(properties = properties), identity.chain))
        }
    }

    private fun properties(): String = if (case.name == "Eivissa") {
        "headless=true\nfilter=encodedcert:" + Base64.getEncoder().encodeToString(identity.certificate.encoded) +
            ";filter=nonexpired:\nmode=implicit\n"
    } else case.properties

    private fun request(
        data: ByteArray = case.data,
        properties: String = properties(),
        origin: TrustedOrigin = TrustedOrigin("https", case.host, 443),
        protocol: SigningProtocolId = adapter.id,
        profile: String = case.profile,
        version: Int = 1,
        algorithm: SigningAlgorithm = case.algorithm,
        format: SigningFormat = case.format,
        description: String = case.description,
        page: String? = case.page,
        adapterBoundaryEnvelope: Boolean = false,
    ): NormalizedSignRequest = keep(NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = protocol,
        context = SigningContext(
            profileId = profile, profileVersion = version, origin = origin,
            navigationId = NavigationId("golden-navigation"), navigationEpoch = 7,
            observedAt = goldenInstant, pageUrl = page,
        ),
        algorithm = algorithm, format = format, safeDescription = description,
        payload = if (adapterBoundaryEnvelope) {
            directAdapterEnvelope(data, properties)
        } else MiniAppletPayloadCodec.encode(data, properties),
    ))

    // Test-only direct adapter input, deliberately outside encoder/store admission limits.
    // JFM1 framing: magic + decoded data length + UTF-8 properties length + both bodies.
    // This is necessary to characterize decoded MAX_PAYLOAD_BYTES / MAX_PAYLOAD_BYTES+1
    // without the upstream 524,288-byte *whole envelope* guard preventing adapter invocation.
    private fun directAdapterEnvelope(data: ByteArray, properties: String): ByteArray {
        val encodedProperties = properties.encodeToByteArray()
        return try {
            ByteBuffer.allocate(12 + data.size + encodedProperties.size)
                .putInt(0x4A464D31)
                .putInt(data.size)
                .putInt(encodedProperties.size)
                .put(data)
                .put(encodedProperties)
                .array()
        } finally {
            encodedProperties.fill(0)
        }
    }

    private suspend fun prepare(request: NormalizedSignRequest): PreSignResult =
        keep((adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success).preSign)

    private suspend fun sign(preSign: PreSignResult): LocalSignature = keep(
        (preSign.withBytesToSign { JcaLocalSignatureEngine().sign(it, identity, case.algorithm) }
            as LocalSignatureResult.Success).signature,
    )

    private suspend fun complete(request: NormalizedSignRequest, preSign: PreSignResult, local: LocalSignature) =
        keep((adapter.complete(request, preSign, local) as ProtocolCompletionResult.Success).signature)

    private fun assertPrepareFailure(code: SigningErrorCode, result: ProtocolPrepareResult) {
        assertEquals(code, (result as ProtocolPrepareResult.Failure).code)
    }

    private fun assertCompletionFailure(code: SigningErrorCode, result: ProtocolCompletionResult) {
        assertEquals(code, (result as ProtocolCompletionResult.Failure).code)
    }

    private fun assertZero(bytes: ByteArray) = assertTrue(bytes.all { it == 0.toByte() })
    private fun <T : Closeable> keep(value: T): T = value.also { owned += it }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = adapterGoldenCases().map { arrayOf<Any>(it) }
    }
}
