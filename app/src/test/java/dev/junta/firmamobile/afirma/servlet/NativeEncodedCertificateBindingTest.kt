package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.cert.X509Certificate
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test

/** Real parser -> operation -> signature/transport seam, only generated keys. */
class NativeEncodedCertificateBindingTest {
    @Test fun anExplicitEncodedCertificateCanSelectAndSignWithoutRemovingUserAuthorization() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val other = freshConstraintIdentity()
        assertFalse("The negative fixture must contain a different certificate", key.identity.certificate.encoded.contentEquals(other.identity.certificate.encoded))
        for (operationName in listOf("selectcert", "sign")) {
            val data = "Synthetic approved input".toByteArray()
            val fields = if (operationName == "sign") generalFields(data, "CAdES") else mapOf(
                "id" to "Bound123", "stservlet" to "https://storage.example/put")
            val invocation = acceptedGeneral(operationName, fields + ("properties" to props(key.identity.certificate)))
            var consent = 0; var sends = 0
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, wire ->
                assertEquals(1, consent); sends++
                val parts = wire.split('|')
                assertArrayEquals(key.identity.certificate.encoded, AfirmaIntermediateCipher.decode(parts[0], null))
                if (operationName == "sign") {
                    assertEquals(2, parts.size)
                    val signature = AfirmaIntermediateCipher.decode(parts[1], null)
                    try {
                        val signer = CMSSignedData(CMSProcessableByteArray(data), signature).signerInfos.signers.single()
                        assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(key.identity.certificate)))
                    } finally { signature.fill(0) }
                } else assertEquals(1, parts.size)
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
            try {
                assertTrue(operation.certificateCompatible(key.identity))
                assertFalse("The requested exact certificate must not silently change", operation.certificateCompatible(other.identity))
                assertEquals(0, sends); assertEquals(0, consent)
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { consent++ })
                assertEquals(1, sends)
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) { error("No automatic replay") } } }
            } finally { operation.close(); data.fill(0) }
        }
        assertEquals(0, key.encodedReads.get()); assertEquals(0, other.encodedReads.get())
    }

    @Test fun aDifferentUnlockedCertificateCannotSendEvenItsPublicCertificate() {
        val required = nonExportableSyntheticIdentity(); val other = freshConstraintIdentity()
        val invocation = acceptedGeneral("selectcert", mapOf("id" to "Bound123", "stservlet" to "https://storage.example/put",
            "properties" to props(required.identity.certificate)))
        var sends = 0; var authorized = 0
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, _ -> sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(other.identity))
        try {
            assertFalse(operation.certificateCompatible(other.identity))
            assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(other.identity) { authorized++ } } }
            assertEquals(0, authorized); assertEquals(0, sends)
        } finally { operation.close() }
        assertEquals(0, other.encodedReads.get())
    }

    @Test fun anOrdinaryUnfilteredRequestKeepsItsExistingExplicitConsentPath() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); var sends = 0; var consent = 0
        val operation = NativeAfirmaOperation(acceptedGeneral("selectcert", mapOf("id" to "Plain123", "stservlet" to "https://storage.example/put")),
            AfirmaResultTransport { _, _, _ -> assertEquals(1, consent); sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        try {
            assertTrue(operation.certificateCompatible(key.identity)); assertEquals(0, sends)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { consent++ })
            assertEquals(1, sends)
        } finally { operation.close() }
    }

    @Test fun remoteSigningRejectsTheWrongCertificateBeforeAnyServiceExchange() {
        val required = nonExportableSyntheticIdentity(); val other = freshConstraintIdentity()
        for (format in listOf("CAdEStri", "PAdEStri", "XAdEStri")) {
            val invocation = acceptedGeneral("sign", generalFields("abc".toByteArray(), format) + mapOf(
                "serverurl" to "https://signer.example/service", "properties" to props(required.identity.certificate)))
            var network = 0; var consent = 0
            val operation = NativeMultiPhaseOperation(invocation,
                NativeSigningServiceTransport { _, _ -> network++; error("No request with the wrong identity") },
                AfirmaResultTransport { _, _, _ -> network++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(other.identity))
            try {
                assertTrue(operation.details.requiresExactCertificate)
                assertFalse(operation.certificateCompatible(other.identity))
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(other.identity) { consent++ } } }
                assertEquals(0, network); assertEquals(0, consent)
            } finally { operation.close() }
        }
        assertEquals(0, other.encodedReads.get())
    }

    @Test fun matchingRemoteIdentityKeepsItsConstraintAcrossBothPhasesAndSingleUpload() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); var calls = 0; var consent = 0; var sends = 0
        val invocation = acceptedGeneral("sign", generalFields("abc".toByteArray(), "XAdEStri") + mapOf(
            "serverurl" to "https://signer.example/service", "properties" to props(key.identity.certificate)))
        val operation = NativeMultiPhaseOperation(invocation, NativeSigningServiceTransport { _, fields ->
            calls++
            assertArrayEquals(key.identity.certificate.encoded, Base64.getUrlDecoder().decode(fields.getValue("cert")))
            val forwarded = Base64.getUrlDecoder().decode(fields.getValue("params")).toString(Charsets.UTF_8)
            assertFalse(forwarded.contains("encodedcert")); assertFalse(forwarded.contains("headless"))
            if (fields["op"] == "pre") {
                assertEquals(0, consent)
                AfirmaRetrievedBytes(url64(checkNotNull(NativeTriphaseXml.encode(NativeTriSession("XAdES", listOf(
                    NativeTriSign("one", null, mapOf("PRE" to Base64.getEncoder().encodeToString("synthetic PRE".toByteArray()), "NEED_PRE" to "false"))
                ))))).toByteArray())
            } else {
                assertEquals(1, consent)
                val signed = checkNotNull(NativeTriphaseXml.parse(Base64.getUrlDecoder().decode(fields.getValue("session"))))
                val value = Base64.getDecoder().decode(signed.signs.single().parameters.getValue("PK1"))
                assertTrue(java.security.Signature.getInstance("SHA256withRSA").run {
                    initVerify(key.identity.certificate); update("synthetic PRE".toByteArray()); verify(value)
                })
                AfirmaRetrievedBytes(("OK NEWID=" + url64("result".toByteArray())).toByteArray())
            }
        }, AfirmaResultTransport { _, _, _ -> sends++; assertEquals(1, consent); AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        try {
            assertTrue(operation.certificateCompatible(key.identity))
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { consent++ })
            assertEquals(2, calls); assertEquals(1, sends); assertEquals(0, key.encodedReads.get())
        } finally { operation.close() }
    }

    @Test fun everyLocalFormatAndTheLocalBatchUseTheSameRequestedIdentityRule() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val other = freshConstraintIdentity()
        assertFalse("The negative fixture must contain a different certificate", key.identity.certificate.encoded.contentEquals(other.identity.certificate.encoded))
        for ((format, data) in listOf("CAdES" to "abc".toByteArray(), "PAdES" to nativePadesFixture(), "XAdES" to "<root/>".toByteArray())) {
            val operation = nativeOperation(acceptedGeneral("sign", generalFields(data, format) + ("properties" to props(key.identity.certificate))))
            try {
                assertTrue(operation.details.requiresExactCertificate)
                assertFalse(operation.certificateCompatible(other.identity))
            } finally { operation.close(); data.fill(0) }
        }
        val batch = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "format" to "CAdES",
            "singlesigns" to listOf(mapOf("id" to "one", "datareference" to "YWJj"))))
        val fields = mapOf("id" to "BoundBatch", "stservlet" to "https://storage.example/put", "jsonbatch" to "true",
            "localBatchProcess" to "true", "dat" to url64(batch), "properties" to props(key.identity.certificate))
        var sends = 0; var consent = 0
        val operation = NativeMultiPhaseOperation(acceptedGeneral("batch", fields),
            NativeSigningServiceTransport { _, _ -> error("No remote phase for local batch") },
            AfirmaResultTransport { _, _, _ -> assertEquals(1, consent); sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        try {
            assertTrue(operation.certificateCompatible(key.identity)); assertFalse(operation.certificateCompatible(other.identity))
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { consent++ })
            assertEquals(1, sends); assertEquals(0, key.encodedReads.get())
        } finally { operation.close(); batch.fill(0) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun headlessAndUnlockDoNotConfirmAnIdentityBoundRequest() = kotlinx.coroutines.test.runTest {
        val key = nonExportableSyntheticIdentity(); val other = freshConstraintIdentity()
        assertFalse("The negative fixture must contain a different certificate", key.identity.certificate.encoded.contentEquals(other.identity.certificate.encoded))
        var selected: dev.junta.firmamobile.certificate.UnlockedIdentity? = null
        var prompt: AfirmaConsentPrompt? = null; var sends = 0; val owner = Any()
        val operation = NativeAfirmaOperation(acceptedGeneral("selectcert", mapOf("id" to "BoundUi", "stservlet" to "https://storage.example/put",
            "properties" to props(key.identity.certificate))), AfirmaResultTransport { _, _, _ -> sends++; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        val controller = AfirmaConsentController<Any>(scope = this, isCurrent = { o, epoch -> o === owner && epoch == 1L },
            identityProvider = { selected }, canRespond = { true }, onPrompt = { prompt = it })
        try {
            assertTrue(controller.offer(owner, 1L, operation)); runCurrent()
            assertEquals(AfirmaConsentProblem.LOCKED, prompt!!.problem); assertFalse(prompt!!.canConfirm)
            selected = other.identity; controller.refreshIdentity(); runCurrent()
            assertEquals(AfirmaConsentProblem.INCOMPATIBLE, prompt!!.problem)
            assertFalse(controller.confirm(prompt!!.token)); assertEquals(0, sends)
            selected = key.identity; controller.refreshIdentity(); runCurrent()
            assertTrue(prompt!!.details.requiresExactCertificate); assertTrue(prompt!!.canConfirm)
            assertEquals(AfirmaConsentPhase.REVIEW, prompt!!.phase); assertEquals(0, sends)
        } finally { controller.close() }
        assertEquals(0, key.encodedReads.get()); assertEquals(0, other.encodedReads.get())
    }

    private fun props(certificate: X509Certificate) = url64(("filters=encodedcert:" + Base64.getEncoder().encodeToString(certificate.encoded) + "\nheadless=true").toByteArray())
}

/** The common non-exportable fixture is intentionally cached. A different
 * certificate must be generated explicitly for negative identity tests. */
internal fun freshConstraintIdentity(): dev.junta.firmamobile.signing.NonExportableSyntheticIdentity {
    val source = dev.junta.firmamobile.signing.freshSyntheticIdentity()
    val reads = java.util.concurrent.atomic.AtomicInteger()
    val wrapped = source.withPrivateKey { delegate ->
        val rsa = delegate as java.security.interfaces.RSAPrivateKey
        val key = object : java.security.interfaces.RSAPrivateKey {
            override fun getAlgorithm() = rsa.algorithm
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray { reads.incrementAndGet(); error("No test key export") }
            override fun getModulus() = rsa.modulus
            override fun getPrivateExponent() = rsa.privateExponent
        }
        dev.junta.firmamobile.certificate.UnlockedIdentity(key, source.certificate, source.chain, source.summary)
    }
    return dev.junta.firmamobile.signing.NonExportableSyntheticIdentity(wrapped, reads)
}
