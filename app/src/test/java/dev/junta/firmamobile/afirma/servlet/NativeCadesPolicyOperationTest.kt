package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.OpaqueRsaFixture
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test

/** Component-level native operations and in-memory transports, not E2E. */
class NativeCadesPolicyOperationTest {
    @Test fun directPolicyRequestDisclosesItsReferenceAndRequiresOneExplicitSend() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val props = NativeCadesPolicyRequestTest.policy().toMutableMap()
        val invocation = request(props); val expected = checkNotNull(invocation.cadesPolicy); props["policyIdentifier"] = "1.2.3.999"
        var authorization = 0; var stores = 0
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, wire ->
            assertEquals(1, authorization); stores++
            val bytes = AfirmaIntermediateCipher.decode(wire.split('|')[1], null)
            try { assertTrue(NativeCadesEngine(clock = generalClockFor(key.identity)).verifyWithPolicy(bytes, DATA,
                key.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, true, expected)) }
            finally { bytes.fill(0) }
            AfirmaDeliveryResult.ACKNOWLEDGED
        }, generalClockFor(key.identity))
        try {
            assertTrue(operation.details.signaturePolicySummary!!.contains(NativeCadesPolicyRequestTest.OID))
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { authorization++ })
            assertEquals(1, stores); assertEquals(0, key.encodedReads.get())
            assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) { error("No replay") } } }
        } finally { operation.close() }
    }

    @Test fun closingOrDenyingUploadDoesNotTransmitTheComputedPolicySignature() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity()
        for (close in listOf(false, true)) {
            var sends = 0
            val operation = NativeAfirmaOperation(request(), AfirmaResultTransport { _, _, _ -> sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
            try {
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) {
                    if (close) operation.close() else error("User denies upload")
                } } }
                assertEquals(0, sends)
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) {} } }
            } finally { operation.close() }
        }
    }

    @Test fun wrongExactCertificateIsRejectedBeforeThePolicySignerUsesItsKey() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            val other = freshConstraintIdentity(); var sends = 0
            val properties = NativeCadesPolicyRequestTest.policy() + ("filters" to "encodedcert:" + Base64.getEncoder().encodeToString(other.identity.certificate.encoded))
            val operation = NativeAfirmaOperation(request(properties), AfirmaResultTransport { _, _, _ -> sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
            try {
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) {} } }
                assertEquals(0, sends); assertEquals(0, key.signatures.get()); assertEquals(0, key.encodingReads.get())
            } finally { operation.close() }
        }
    }

    @Test fun localBatchCombinesPolicySignCosignAndPrehashWithAnHonestFailedItem() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val previousKey = freshConstraintIdentity(); val alg = SigningAlgorithm.SHA256_WITH_RSA
        val originalCms = NativeCadesPolicyCryptoTest.take(NativeCadesEngine(clock = generalClockFor(previousKey.identity)).sign(DATA, previousKey.identity, alg, false))
        val policy = NativeCadesPolicyRequestTest.policy(); val digest = MessageDigest.getInstance("SHA-256").digest(DATA)
        val rows = listOf(item("new", DATA, policy), item("cosign", originalCms, policy, "cosign"),
            item("prehash", digest, policy + ("precalculatedHashAlgorithm" to "SHA-256")),
            item("bad", DATA, policy - "policyIdentifierHash"))
        var permits = 0; var sends = 0
        val operation = NativeMultiPhaseOperation(batch(rows), NativeSigningServiceTransport { _, _ -> error("No policy or signing server fetch") }, AfirmaResultTransport { _, _, wire ->
            assertEquals(1, permits); sends++
            val outcomes = results(wire)
            assertEquals(listOf("DONE_AND_SAVED", "DONE_AND_SAVED", "DONE_AND_SAVED", "ERROR_PRE"), outcomes.map { it["result"] })
            assertFalse(outcomes.last().containsKey("signature"))
            val expected = checkNotNull(NativeCadesPolicy.parse(policy))
            for (row in outcomes.take(3)) {
                val encoded = Base64.getDecoder().decode(row["signature"] as String)
                try {
                    val cms = if (CMSSignedData(encoded).signedContent == null) CMSSignedData(CMSProcessableByteArray(DATA), encoded) else CMSSignedData(encoded)
                    assertEquals(1, cms.signerInfos.signers.count { NativeCadesPolicy.matches(it.signedAttributes, expected) })
                    for (signer in cms.signerInfos.signers) {
                        val certificate = cms.certificates.getMatches(null).filter(signer.sid::match).single()
                        assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(certificate)))
                    }
                } finally { encoded.fill(0) }
            }
            AfirmaDeliveryResult.ACKNOWLEDGED
        }, generalClockFor(key.identity))
        try {
            assertEquals(4, operation.details.signaturePolicyItems); assertEquals(1, operation.details.providedDigestItems)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { permits++ })
            assertEquals(1, sends); assertEquals(0, key.encodedReads.get())
        } finally { operation.close(); originalCms.fill(0); digest.fill(0) }
    }

    @Test fun anInvalidPolicyWithStopOnErrorPreventsRemainingKeyUse() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            val rows = listOf(item("bad", DATA, mapOf("policyIdentifier" to "https://must-not-fetch.example/policy")), item("next", DATA, NativeCadesPolicyRequestTest.policy()))
            val operation = NativeMultiPhaseOperation(batch(rows, true), NativeSigningServiceTransport { _, _ -> error("No fetch") }, AfirmaResultTransport { _, _, wire ->
                assertEquals(listOf("ERROR_PRE", "SKIPPED"), results(wire).map { it["result"] }); AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
            try {
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) {})
                assertEquals(0, key.signatures.get()); assertEquals(0, key.encodingReads.get())
            } finally { operation.close() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun aPolicyAndHeadlessFlagDoNotTurnUnlockIntoConsent() = runTest {
        val key = nonExportableSyntheticIdentity(); var identity: UnlockedIdentity? = null; var prompt: AfirmaConsentPrompt? = null; var sends = 0
        val owner = Any(); val operation = NativeAfirmaOperation(request(NativeCadesPolicyRequestTest.policy() + ("headless" to "true")),
            AfirmaResultTransport { _, _, _ -> sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        val controller = AfirmaConsentController<Any>(this, { o, e -> o === owner && e == 1L }, { identity }, { true }, { prompt = it })
        try {
            assertTrue(controller.offer(owner, 1L, operation)); runCurrent(); assertEquals(AfirmaConsentProblem.LOCKED, prompt!!.problem)
            identity = key.identity; controller.refreshIdentity(); runCurrent()
            assertTrue(prompt!!.canConfirm); assertNotNull(prompt!!.details.signaturePolicySummary); assertEquals(0, sends)
        } finally { controller.close() }
    }

    private fun request(properties: Map<String, String> = NativeCadesPolicyRequestTest.policy()) = acceptedGeneral("sign",
        mapOf("id" to "Policy123", "stservlet" to NativeCadesPolicyRequestTest.STORE, "format" to "CAdES", "algorithm" to "SHA256withRSA", "dat" to url64(DATA), "properties" to props(properties)))
    private fun item(id: String, data: ByteArray, properties: Map<String, String>, operation: String = "sign") =
        mapOf("id" to id, "format" to "CAdES", "suboperation" to operation, "datareference" to url64(data), "extraparams" to props(properties))
    private fun batch(items: List<Map<String, String>>, stop: Boolean = false) = acceptedGeneral("batch", mapOf(
        "id" to "PolicyBatch123", "stservlet" to NativeCadesPolicyRequestTest.STORE, "jsonbatch" to "true", "localBatchProcess" to "true",
        "dat" to url64(NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "stoponerror" to stop, "singlesigns" to items)))))
    private fun results(wire: String): List<Map<String, Any?>> {
        val bytes = AfirmaIntermediateCipher.decode(wire, null)
        return try { (NativeProtocolJson.parse(bytes)["signs"] as List<*>).map(NativeTriphaseCodec::objectValue) } finally { bytes.fill(0) }
    }
    private fun props(properties: Map<String, String>) = url64(NativeJavaProperties.encode(properties))
    companion object { private val DATA: ByteArray get() = NativeCadesPolicyCryptoTest.DATA }
}
