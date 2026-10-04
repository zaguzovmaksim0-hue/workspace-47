package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.OpaqueRsaFixture
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test

/** Actual operation with a generated identity and an in-memory result store;
 * no site login, device key, browser flow or end-to-end administrative action. */
class NativePrecalculatedHashOperationTest {
    @Test fun oneSignatureBindsExactlyTheReceivedDigestForEverySupportedAlgorithm() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity()
        for (hash in NativePrecalculatedHash.entries) {
            val digest = MessageDigest.getInstance(hash.digestName).digest(ORIGINAL)
            val invocation = prehashedInvocation(hash, digest, "mode=implicit")
            var authorizations = 0; var stores = 0
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, wire ->
                assertEquals(1, authorizations); stores++
                val fields = wire.split('|'); assertEquals(2, fields.size)
                val cert = AfirmaIntermediateCipher.decode(fields[0], null)
                val cms = AfirmaIntermediateCipher.decode(fields[1], null)
                try {
                    assertArrayEquals(key.identity.certificate.encoded, cert)
                    assertDigestSignature(cms, ORIGINAL, digest, hash, key.identity)
                } finally { cert.fill(0); cms.fill(0) }
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
            try {
                assertEquals(hash.digestName, operation.details.providedDigestAlgorithm)
                assertEquals(digest.size, operation.details.payloadBytes)
                assertEquals(MessageDigest.getInstance("SHA-256").digest(digest).joinToString("") { "%02x".format(it) }, operation.details.payloadSha256)
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { authorizations++ })
                assertEquals(1, stores)
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) { error("No replay") } } }
            } finally { operation.close(); digest.fill(0) }
        }
        assertEquals(0, key.encodedReads.get())
    }

    @Test fun ordinaryDigestSizedBytesAreNotSilentlyReinterpretedAsAPrecomputedHash() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val digest = MessageDigest.getInstance("SHA-256").digest(ORIGINAL)
        val invocation = acceptedGeneral("sign", mapOf("id" to "RawDigest123", "stservlet" to STORE,
            "format" to "CAdES", "algorithm" to "SHA256withRSA", "dat" to encode(digest)))
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, wire ->
            val encoded = AfirmaIntermediateCipher.decode(wire.split('|')[1], null)
            try {
                val verification = NativeCadesEngine(clock = generalClockFor(key.identity))
                assertTrue(verification.verify(encoded, digest, key.identity.certificate, NativePrecalculatedHash.SHA256.signingAlgorithm, true))
                assertFalse(verification.verify(encoded, ORIGINAL, key.identity.certificate, NativePrecalculatedHash.SHA256.signingAlgorithm, true))
            } finally { encoded.fill(0) }
            AfirmaDeliveryResult.ACKNOWLEDGED
        }, generalClockFor(key.identity))
        try {
            assertNull(operation.details.providedDigestAlgorithm)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) {})
        } finally { operation.close() }
    }

    @Test fun hashOnlySigningWorksWithAnOpaqueProviderWithoutExportingTheKey() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            for (hash in NativePrecalculatedHash.entries) {
                val digest = MessageDigest.getInstance(hash.digestName).digest(ORIGINAL)
                val operation = NativeAfirmaOperation(prehashedInvocation(hash, digest), AfirmaResultTransport { _, _, wire ->
                    val encoded = AfirmaIntermediateCipher.decode(wire.split('|')[1], null)
                    try { assertDigestSignature(encoded, ORIGINAL, digest, hash, key.identity) } finally { encoded.fill(0) }
                    AfirmaDeliveryResult.ACKNOWLEDGED
                }, generalClockFor(key.identity))
                try { assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) {}) }
                finally { operation.close() }
            }
            assertEquals(4, key.signatures.get()); assertEquals(0, key.encodingReads.get())
        }
    }

    @Test fun deniedFinalAuthorizationNeverSendsOrAutomaticallyRetries() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); var stores = 0
        val operation = NativeAfirmaOperation(prehashedInvocation(), AfirmaResultTransport { _, _, _ -> stores++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        try {
            assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) { error("User denies final send") } } }
            assertEquals(0, stores)
            assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) {} } }
            assertEquals(0, stores)
        } finally { operation.close() }
    }

    @Test fun closingDuringFinalAuthorizationDoesNotSendAComputedSignature() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); var stores = 0
        val operation = NativeAfirmaOperation(prehashedInvocation(), AfirmaResultTransport { _, _, _ -> stores++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) { operation.close() } } }
        assertEquals(0, stores); operation.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun headlessHashMetadataAndUnlockNeverReplaceUserConfirmation() = runTest {
        val key = nonExportableSyntheticIdentity(); var identity: UnlockedIdentity? = null; var prompt: AfirmaConsentPrompt? = null; var sends = 0
        val owner = Any(); val operation = NativeAfirmaOperation(prehashedInvocation(extra = "headless=true"),
            AfirmaResultTransport { _, _, _ -> sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        val controller = AfirmaConsentController<Any>(this, { o, e -> o === owner && e == 1L }, { identity }, { true }, { prompt = it })
        try {
            assertTrue(controller.offer(owner, 1L, operation)); runCurrent()
            assertEquals(AfirmaConsentProblem.LOCKED, prompt!!.problem); assertFalse(prompt!!.canConfirm)
            identity = key.identity; controller.refreshIdentity(); runCurrent()
            assertEquals(AfirmaConsentPhase.REVIEW, prompt!!.phase); assertTrue(prompt!!.canConfirm)
            assertEquals("SHA-256", prompt!!.details.providedDigestAlgorithm); assertEquals(0, sends)
        } finally { controller.close() }
    }

    @Test fun existingExactCertificateConstraintStillRejectsAnotherIdentity() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            val other = freshConstraintIdentity()
            val props = "filters=encodedcert:" + Base64.getEncoder().encodeToString(other.identity.certificate.encoded)
            var sends = 0
            val operation = NativeAfirmaOperation(prehashedInvocation(extra = props), AfirmaResultTransport { _, _, _ -> sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
            try {
                assertTrue(operation.details.requiresExactCertificate); assertFalse(operation.certificateCompatible(key.identity))
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) {} } }
                assertEquals(0, sends); assertEquals(0, key.signatures.get()); assertEquals(0, key.encodingReads.get())
            } finally { operation.close() }
        }
    }

    @Test fun digestOwnershipAndClosedRequestCannotBeBypassedByACallerCopy() {
        val digest = MessageDigest.getInstance("SHA-256").digest(ORIGINAL); val copy = digest.copyOf()
        val invocation = prehashedInvocation(digest = digest)
        digest.fill(0)
        assertArrayEquals(copy, invocation.payloadCopy())
        val leakedCopy = invocation.payloadCopy(); leakedCopy.fill(0)
        assertArrayEquals(copy, invocation.payloadCopy())
        invocation.close()
        assertThrows(IllegalStateException::class.java) { invocation.payloadCopy() }
        assertFalse(invocation.toString().contains(encode(copy)))
    }

    @Test fun cancellationSendsOnlyTheExistingKeyFreeProtocolMarker() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            var authorizations = 0; var stores = 0
            val operation = NativeAfirmaOperation(prehashedInvocation(), AfirmaResultTransport { _, _, value ->
                assertEquals("CANCEL", value); assertEquals(1, authorizations); stores++; AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
            try {
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.notifyCancellation { authorizations++ })
                assertEquals(1, stores); assertEquals(0, key.signatures.get()); assertEquals(0, key.encodingReads.get())
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) {} } }
            } finally { operation.close() }
        }
    }

    companion object {
        val ORIGINAL: ByteArray get() = "Synthetic original document; never submitted to a government service".toByteArray()
        const val STORE = "https://storage.synthetic.example/put"
        fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().encodeToString(bytes)
        internal fun prehashedInvocation(hash: NativePrecalculatedHash = NativePrecalculatedHash.SHA256,
            digest: ByteArray = MessageDigest.getInstance(hash.digestName).digest(ORIGINAL), extra: String = ""): AfirmaServletInvocation =
            acceptedGeneral("sign", mapOf("id" to "Prehash123", "stservlet" to STORE, "format" to "CAdES",
                "algorithm" to with(NativeTriphaseCodec) { hash.signingAlgorithm.wireName() }, "dat" to encode(digest),
                "properties" to encode(("precalculatedHashAlgorithm=${hash.digestName}\n$extra").toByteArray())))
        internal fun assertDigestSignature(encoded: ByteArray, original: ByteArray, digest: ByteArray, hash: NativePrecalculatedHash, identity: UnlockedIdentity) {
            val envelope = CMSSignedData(encoded)
            assertNull(envelope.signedContent)
            val signer = envelope.signerInfos.signers.single()
            val received = ASN1OctetString.getInstance(signer.signedAttributes.get(CMSAttributes.messageDigest).attrValues.getObjectAt(0)).octets
            assertArrayEquals(digest, received)
            assertFalse(MessageDigest.getInstance(hash.digestName).digest(digest).contentEquals(received))
            assertEquals(NativeCadesEngine.digestOid(hash.signingAlgorithm), signer.digestAlgOID)
            assertTrue(CMSSignedData(CMSProcessableByteArray(original), encoded).signerInfos.signers.single()
                .verify(JcaSimpleSignerInfoVerifierBuilder().build(identity.certificate)))
            val verifier = NativeCadesEngine(clock = generalClockFor(identity))
            assertFalse(verifier.verify(encoded, digest, identity.certificate, hash.signingAlgorithm, true))
            val wrong = digest.copyOf(); wrong[0] = (wrong[0].toInt() xor 1).toByte()
            assertFalse(verifier.verifyDigest(encoded, wrong, identity.certificate, hash.signingAlgorithm))
        }
    }
}
