package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalXadesDetachedAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = LocalXadesDetachedAdapter(clock)

    @Test
    fun createsVerifiableSha512XadesDetachedAndRejectsTampering() = runTest {
        val request = request()
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val engine = JcaLocalSignatureEngine()
        val local = prepared.preSign.withBytesToSign { signedInfo ->
            engine.sign(signedInfo, identity, SigningAlgorithm.SHA512_WITH_RSA)
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }
        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()

        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)
        assertTrue(XadesDetachedCodec.validate(result, fingerprint))
        val tampered = result.copyOf().also { bytes ->
            val marker = "synthetic-registry".encodeToByteArray()
            val start = bytes.indexOfSubsequence(marker)
            check(start >= 0)
            bytes[start] = (bytes[start].toInt() xor 1).toByte()
        }
        assertFalse(XadesDetachedCodec.validate(tampered, fingerprint))
        result.fill(0)
        tampered.fill(0)
        fingerprint.fill(0)
    }

    @Test
    fun rejectsNonXmlWrongTupleAndNonEmptyProperties() = runTest {
        val malformed = request(data = "not-xml".encodeToByteArray())
        assertTrue(adapter.prepare(malformed, identity.chain) is ProtocolPrepareResult.Failure)
        malformed.close()

        val properties = request(extraProperties = "mode=explicit")
        assertTrue(adapter.prepare(properties, identity.chain) is ProtocolPrepareResult.Failure)
        properties.close()

        val wrongAlgorithm = request(algorithm = SigningAlgorithm.SHA256_WITH_RSA)
        assertTrue(adapter.prepare(wrongAlgorithm, identity.chain) is ProtocolPrepareResult.Failure)
        wrongAlgorithm.close()
    }

    @Test
    fun acceptsJccmRegistroExactImplicitPropertiesAndRejectsCrossProfileBroadening() = runTest {
        val accepted = request(
            profileId = LocalXadesDetachedAdapter.JCCM_REGISTRO_PROFILE_ID,
            extraProperties = LocalXadesDetachedAdapter.JCCM_REGISTRO_EXTRA_PROPERTIES,
        )
        val prepared = adapter.prepare(accepted, identity.chain)
        assertTrue(prepared is ProtocolPrepareResult.Success)
        (prepared as ProtocolPrepareResult.Success).preSign.close()
        accepted.close()

        val jccmWithRegAgeProperties = request(
            profileId = LocalXadesDetachedAdapter.JCCM_REGISTRO_PROFILE_ID,
            extraProperties = "",
        )
        assertTrue(adapter.prepare(jccmWithRegAgeProperties, identity.chain) is ProtocolPrepareResult.Failure)
        jccmWithRegAgeProperties.close()

        val regAgeWithJccmProperties = request(
            extraProperties = LocalXadesDetachedAdapter.JCCM_REGISTRO_EXTRA_PROPERTIES,
        )
        assertTrue(adapter.prepare(regAgeWithJccmProperties, identity.chain) is ProtocolPrepareResult.Failure)
        regAgeWithJccmProperties.close()
    }

    @Test
    fun rejectsDoctypeBeforeSigning() = runTest {
        val request = request(
            data = """<!DOCTYPE resumen [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><resumen>&xxe;</resumen>"""
                .encodeToByteArray(),
        )

        assertTrue(adapter.prepare(request, identity.chain) is ProtocolPrepareResult.Failure)
        request.close()
    }

    private fun request(
        data: ByteArray = XML.copyOf(),
        extraProperties: String = "",
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
        profileId: String = LocalXadesDetachedAdapter.REG_AGE_PROFILE_ID,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = LocalXadesDetachedAdapter.ID,
        context = SigningContext(
            profileId = profileId,
            profileVersion = 1,
            origin = TrustedOrigin("https", "reg.redsara.es", 443),
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
            navigationEpoch = 7,
            observedAt = clock.instant(),
        ),
        algorithm = algorithm,
        format = SigningFormat.XADES,
        safeDescription = "Firma del resumen XML del registro",
        payload = MiniAppletPayloadCodec.encode(data, extraProperties),
    ).also { data.fill(0) }

    private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int {
        for (start in 0..size - needle.size) {
            if (needle.indices.all { this[start + it] == needle[it] }) return start
        }
        return -1
    }

    private companion object {
        val XML = """<?xml version="1.0" encoding="UTF-8"?><resumen><dato>synthetic-registry</dato></resumen>"""
            .encodeToByteArray()
    }
}
