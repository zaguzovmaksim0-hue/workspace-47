package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URI
import java.time.Clock
import java.time.ZoneOffset
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** End-to-end application pipeline with a synthetic identity and an in-memory
 * storage boundary. No government server or personal certificate is used. */
class NativeAfirmaOperationTest {
    @Test fun everySupportedModeAndDigestRoundTripsCertificateAndVerifiedSignature() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        val payload = "original synthetic signing bytes\u0000".toByteArray()
        for (detached in listOf(true, false)) for (algorithm in SigningAlgorithm.entries) {
            val invocation = invocation(payload, algorithm, detached)
            var authorized = 0
            var wire: String? = null
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { endpoint, id, result ->
                assertEquals(1, authorized)
                assertEquals(URI("https://store.example/StorageService?route=test"), endpoint)
                assertEquals("Request-123", id)
                wire = result
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, clock = clockFor(identity.identity))
            assertEquals(payload.size, operation.details.payloadBytes)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(identity.identity) { authorized++ })
            val fields = checkNotNull(wire).split('|')
            assertEquals(2, fields.size)
            val cert = AfirmaIntermediateCipher.decode(fields[0], "12345678")
            val signature = AfirmaIntermediateCipher.decode(fields[1], "12345678")
            try {
                assertArrayEquals(identity.identity.certificate.encoded, cert)
                assertTrue(NativeCadesEngine().verify(signature, payload, identity.identity.certificate, algorithm, detached))
            } finally { cert.fill(0); signature.fill(0); operation.close() }
        }
        assertEquals(0, identity.encodedReads.get())
    }

    @Test fun selectCertificateUsesExactlyOneFieldAndDoesNotSign() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        val invocation = AfirmaServletInvocation(AfirmaServletOperation.SELECT_CERTIFICATE, "https://portal.example",
            URI("https://store.example/select"), "Select-123", null, null, true, ByteArray(0))
        var body: String? = null
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, result -> body = result; AfirmaDeliveryResult.ACKNOWLEDGED }, clock = clockFor(identity.identity))
        assertEquals("selectcert", operation.details.operation)
        assertNull(operation.details.payloadSha256)
        operation.execute(identity.identity) {}
        assertFalse(checkNotNull(body).contains('|'))
        val decoded = AfirmaIntermediateCipher.decode(checkNotNull(body), null)
        assertArrayEquals(identity.identity.certificate.encoded, decoded)
        assertEquals(0, identity.encodedReads.get())
        decoded.fill(0); operation.close()
    }

    @Test fun aesCallerParametersReachTheActualResultPipelineWithoutDesDowngrade() = runBlocking {
        val key = ByteArray(32) { it.toByte() }; val iv = ByteArray(16) { (it + 16).toByte() }
        val decoder = AfirmaAesParameters(key, iv)
        val json = "{\"algo\":\"AES\",\"key\":\"${b64(key)}\",\"iv\":\"${b64(iv)}\",\"legDes\":\"12345678\"}"
        val uri = "afirma://sign?id=Aes-123&stservlet=https%3A%2F%2Fstore.example%2Fput&key=12345678&cipher=" +
            URLEncoder.encode(b64(json.toByteArray()), "UTF-8") + "&format=CAdES&algorithm=SHA256withRSA&dat=" + b64("payload".toByteArray())
        val parsed = AfirmaServletInvocationParser.parse(uri, "https://portal.example/start") as AfirmaServletParseResult.Accepted
        val identity = nonExportableSyntheticIdentity()
        var wire: String? = null
        val operation = NativeAfirmaOperation(parsed.invocation, AfirmaResultTransport { _, _, result -> wire = result; AfirmaDeliveryResult.ACKNOWLEDGED }, clock = clockFor(identity.identity))
        operation.execute(identity.identity) {}
        val fields = checkNotNull(wire).split('|'); assertEquals(2, fields.size)
        assertTrue(fields.none { it.contains('.') })
        val cert = decoder.decode(fields[0]); val signature = decoder.decode(fields[1])
        assertArrayEquals(identity.identity.certificate.encoded, cert)
        assertTrue(NativeCadesEngine().verify(signature, "payload".toByteArray(), identity.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, true))
        cert.fill(0); signature.fill(0); decoder.close(); operation.close(); key.fill(0); iv.fill(0)
    }

    @Test fun deniedFinalAuthorizationNeverContactsStorage() = runBlocking {
        val identity = nonExportableSyntheticIdentity(); var uploads = 0
        val operation = NativeAfirmaOperation(invocation(), AfirmaResultTransport { _, _, _ -> uploads++; AfirmaDeliveryResult.ACKNOWLEDGED }, clock = clockFor(identity.identity))
        try {
            operation.execute(identity.identity) { throw CancellationException("Synthetic changed owner") }
            fail("An unowned request must not be sent")
        } catch (_: CancellationException) { }
        assertEquals(0, uploads); operation.close()
    }

    @Test fun operationClosedDuringAuthorizationDoesNotSendItsSnapshot() = runBlocking {
        val identity = nonExportableSyntheticIdentity(); var uploads = 0
        val operation = NativeAfirmaOperation(invocation(), AfirmaResultTransport { _, _, _ -> uploads++; AfirmaDeliveryResult.ACKNOWLEDGED }, clock = clockFor(identity.identity))
        try {
            operation.execute(identity.identity) { operation.close() }
            fail("A closed operation must not upload")
        } catch (_: IllegalStateException) { }
        assertEquals(0, uploads)
    }

    @Test fun aSecondExecutionNeverDuplicatesTheStorageRequest() = runBlocking {
        val identity = nonExportableSyntheticIdentity(); var uploads = 0
        val operation = NativeAfirmaOperation(invocation(), AfirmaResultTransport { _, _, _ -> uploads++; AfirmaDeliveryResult.UNCERTAIN }, clock = clockFor(identity.identity))
        assertEquals(AfirmaDeliveryResult.UNCERTAIN, operation.execute(identity.identity) {})
        try { operation.execute(identity.identity) {}; fail("No automatic repeat after uncertainty") }
        catch (_: IllegalStateException) { }
        assertEquals(1, uploads); operation.close()
    }

    @Test fun preparationDetailsDoNotExposeSessionCipherOrPayload() {
        val operation = NativeAfirmaOperation(invocation())
        val summary = operation.details.toString()
        assertFalse(summary.contains("Request-123")); assertFalse(summary.contains("12345678"))
        assertFalse(summary.contains("private-query-token")); assertFalse(summary.contains("payload".uppercase()))
        assertNotNull(operation.details.payloadSha256)
        assertEquals("https://store.example/StorageService", operation.details.destination)
        operation.close()
    }

    @Test fun expiredAndNotYetValidIdentitiesAreRejectedBeforeCryptographyOrStorage() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        for (instant in listOf(identity.identity.summary.validFrom.minusSeconds(1), identity.identity.summary.validUntil.plusSeconds(1))) {
            var uploads = 0
            val operation = NativeAfirmaOperation(invocation(), AfirmaResultTransport { _, _, _ -> uploads++; AfirmaDeliveryResult.ACKNOWLEDGED },
                clock = Clock.fixed(instant, ZoneOffset.UTC))
            assertFalse(operation.certificateCompatible(identity.identity))
            try { operation.execute(identity.identity) {}; fail("Invalid date must not reach storage") }
            catch (_: IllegalStateException) { }
            assertEquals(0, uploads); operation.close()
        }
        assertEquals(0, identity.encodedReads.get())
    }

    private fun clockFor(identity: UnlockedIdentity) = Clock.fixed(identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC)

    private fun invocation(payload: ByteArray = "PAYLOAD_NOT_FOR_LOGS".toByteArray(),
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA, detached: Boolean = true) =
        AfirmaServletInvocation(AfirmaServletOperation.SIGN, "https://portal.example",
            URI("https://store.example/StorageService?route=test"), "Request-123", "12345678", algorithm, detached, payload)
    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}
