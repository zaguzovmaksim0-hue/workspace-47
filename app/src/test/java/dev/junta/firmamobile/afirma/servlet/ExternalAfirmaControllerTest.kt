package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.TestCertificateFactory
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class ExternalAfirmaControllerTest {
    @Test fun externalSyntaxDoesNotManufactureABrowserOrigin() {
        val result = AfirmaServletInvocationParser.parseExternal(SELECT)
        assertTrue(result is AfirmaServletParseResult.Accepted)
        (result as AfirmaServletParseResult.Accepted).invocation.use {
            assertNull(it.sourceOrigin)
            assertNotNull(it.externalContext)
            assertEquals(AfirmaServletOperation.SELECT_CERTIFICATE, it.operation)
        }
    }

    @Test fun admissionRequiresForegroundAndIdleWithoutPreparingOrReplacingAnything() {
        val f = Fixture()
        f.foreground = false
        assertFalse(f.controller.receive(SELECT))
        f.foreground = true; f.busy = true
        assertFalse(f.controller.receive(SELECT))
        assertEquals(0, f.preparations)
        f.busy = false
        assertTrue(f.controller.receive(SELECT))
        val token = f.prompt!!.token
        assertEquals(AfirmaConsentProblem.LOCKED, f.prompt!!.problem)
        assertFalse(f.controller.confirm(token))
        assertFalse(f.controller.receive(SELECT.replace("fixture", "other")))
        assertEquals(token, f.prompt!!.token)
        assertEquals(1, f.preparations)
        assertEquals(0, f.effects)
        f.controller.close()
    }

    @Test fun aReviewCannotDisplayAQueryOrDifferentPayloadThanTheApprovedRequest() {
        val f = Fixture()
        f.unsafeReview = true
        assertFalse(f.controller.receive(SELECT))
        assertNull(f.prompt)
        assertEquals(0, f.effects)
        f.controller.close()
    }

    @Test fun losingForegroundAtTheConfirmButtonInvalidatesTheToken() {
        val f = Fixture(); f.identity = identity()
        assertTrue(f.controller.receive(f.raw))
        val token = f.prompt!!.token
        f.foreground = false
        assertFalse(f.controller.confirm(token))
        f.foreground = true
        assertFalse(f.controller.confirm(token))
        assertEquals(0, f.effects)
        f.controller.close()
    }

    @Test fun unlockRefreshesReviewButNeverConfirmsAndOldTokenCannotBeUsed() {
        val f = Fixture(); assertTrue(f.controller.receive(f.raw))
        val old = f.prompt!!.token
        f.identity = identity(); f.controller.refreshIdentity()
        assertNotEquals(old, f.prompt!!.token)
        assertTrue(f.prompt!!.canConfirm)
        assertFalse(f.controller.confirm(old))
        assertEquals(0, f.effects)
        assertTrue(f.controller.confirm(f.prompt!!.token))
        assertEquals(1, f.effects)
        f.controller.close()
    }

    @Test fun oneConfirmationConsumesAuthorityAndProcessLocalReplayIsRejected() {
        val f = Fixture(); f.identity = identity()
        assertTrue(f.controller.receive(f.raw)); val token = f.prompt!!.token
        assertEquals("https://storage.example/store", f.prompt!!.details.destination)
        assertFalse(f.prompt!!.details.destination.contains("private"))
        assertTrue(f.controller.confirm(token)); assertFalse(f.controller.confirm(token))
        assertEquals(1, f.effects)
        f.controller.dismiss(token)
        val next = Fixture(f.raw); next.identity = f.identity
        assertFalse(next.controller.receive(next.raw))
        assertEquals(0, next.preparations)
        f.controller.close(); next.controller.close()
    }

    @Test fun cancelledDeliveryCanOnlyBeReviewedAgainWithAFreshToken() {
        val f = Fixture(); f.identity = identity()
        assertTrue(f.controller.receive(f.raw)); val old = f.prompt!!.token
        f.controller.cancel(old)
        assertFalse(f.controller.confirm(old)); assertEquals(0, f.effects)
        assertEquals(0, f.serverCancellations)
        f.controller.dismiss(old)
        assertTrue(f.controller.receive(f.raw)); assertNotEquals(old, f.prompt!!.token)
        assertFalse(f.controller.confirm(old)); assertEquals(0, f.effects)
        assertTrue(f.controller.confirm(f.prompt!!.token)); assertEquals(1, f.effects)
        f.controller.close()
    }

    @Test fun newDeliveryDuringExecutionDoesNotReplaceOrCancelTheActiveOperation() {
        val f = Fixture(); f.identity = identity(); f.beforeUpload = CompletableDeferred()
        assertTrue(f.controller.receive(f.raw)); val token = f.prompt!!.token
        assertTrue(f.controller.confirm(token)); assertEquals(1, f.executions)
        assertFalse(f.controller.receive(f.raw.replace("private", "changed")))
        assertEquals(token, f.prompt!!.token)
        assertEquals(AfirmaConsentPhase.WORKING, f.prompt!!.phase)
        f.beforeUpload!!.complete(Unit)
        assertEquals(1, f.effects)
        f.controller.close()
    }

    @Test fun backgroundAndLockRevokeReviewAndInFlightUploadPermissionWithoutResumeReplay() {
        for (executing in listOf(false, true)) {
            val f = Fixture(); f.identity = identity(); f.beforeUpload = CompletableDeferred()
            assertTrue(f.controller.receive(f.raw)); val token = f.prompt!!.token
            if (executing) assertTrue(f.controller.confirm(token))
            f.foreground = false; f.controller.onBackground()
            f.identity = null; f.foreground = true; f.controller.onForeground()
            f.identity = identity(); f.controller.refreshIdentity()
            f.beforeUpload!!.complete(Unit)
            assertFalse(f.controller.confirm(token)); assertEquals(0, f.effects)
            assertEquals(0, f.serverCancellations)
            assertEquals(AfirmaConsentProblem.CANCELLED, f.prompt!!.problem)
            f.controller.close()
        }
    }

    @Test fun certificateChangedAtClickRequiresFreshConsentWithoutBurningAnUnusedRequest() {
        val f = Fixture(); f.identity = identity()
        assertTrue(f.controller.receive(f.raw)); val old = f.prompt!!.token
        f.identity = identity(TestCertificateFactory.nonRepudiationCertificate())
        assertFalse(f.controller.confirm(old)); assertNotEquals(old, f.prompt!!.token)
        assertEquals(0, f.effects)
        assertTrue(f.controller.confirm(f.prompt!!.token)); assertEquals(1, f.effects)
        f.controller.close()
    }

    @Test fun certificateReplacementBeforeUploadStopsDisclosure() {
        val f = Fixture(); f.identity = identity(); f.beforeUpload = CompletableDeferred()
        assertTrue(f.controller.receive(f.raw))
        assertTrue(f.controller.confirm(f.prompt!!.token))
        f.identity = identity(TestCertificateFactory.nonRepudiationCertificate())
        f.beforeUpload!!.complete(Unit)
        assertEquals(0, f.effects)
        assertEquals(AfirmaConsentProblem.CANCELLED, f.prompt!!.problem)
        f.controller.close()
    }

    @Test fun exactTwoMinuteExpiryAndClockRollbackDenyConfirmation() {
        for (time in listOf(120_000_000_001L, 0L)) {
            val f = Fixture(); f.identity = identity()
            assertTrue(f.controller.receive(f.raw)); val token = f.prompt!!.token
            f.now = time
            assertFalse(f.controller.confirm(token)); assertEquals(0, f.effects)
            assertEquals(AfirmaConsentProblem.EXPIRED, f.prompt!!.problem)
            f.controller.close()
        }
    }

    @Test fun dismissalAndReceiverReplacementNeverTransferConsent() {
        val f = Fixture(); f.identity = identity()
        assertTrue(f.controller.receive(f.raw)); val token = f.prompt!!.token
        f.controller.dismiss(token)
        assertNull(f.prompt); assertFalse(f.controller.confirm(token)); assertEquals(0, f.effects)
        assertTrue(f.controller.receive(f.raw)); val replacement = f.prompt!!.token
        f.controller.close()
        val next = Fixture(f.raw); next.identity = f.identity
        assertTrue(next.controller.receive(next.raw))
        assertFalse(next.controller.confirm(replacement)); assertEquals(0, next.effects)
        next.controller.close()
    }

    @Test fun reviewBindsPublicCertificateEvidenceToTheCurrentIdentity() {
        val f = Fixture(); f.identity = identity()
        assertTrue(f.controller.receive(f.raw))
        val bytes = f.identity!!.certificate.encoded
        val evidence = f.prompt!!.certificateEvidence!!
        assertEquals(bytes.size, evidence.bytes)
        assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }, evidence.sha256)
        f.identity = null; f.controller.refreshIdentity()
        assertNull(f.prompt!!.certificateEvidence)
        assertFalse(f.prompt!!.canConfirm)
        f.controller.close()
    }

    private class Fixture(val raw: String = SELECT.replace("fixture", UUID.randomUUID().toString())) {

        var identity: UnlockedIdentity? = null
        var now = 1L
        var unsafeReview = false
        var foreground = true
        var busy = false
        var preparations = 0
        var effects = 0
        var executions = 0
        var serverCancellations = 0
        var beforeUpload: CompletableDeferred<Unit>? = null
        var prompt: AfirmaConsentPrompt? = null
        val controller = ExternalAfirmaController(
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            canRespond = { foreground && !busy }, identityProvider = { identity },
            onPrompt = { prompt = it }, monotonicNanos = { now },
            prepare = { invocation ->
                preparations++
                object : PreparedAfirmaOperation {
                    override val details = AfirmaConsentDetails(null, if (unsafeReview) "https://storage.example/store?token=private" else "https://storage.example/store", "selectcert",
                        null, null, 0, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        externalContext = invocation.externalContext)
                    override fun certificateCompatible(identity: UnlockedIdentity) = true
                    override suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
                        executions++; beforeUpload?.await(); authorizeUpload(); effects++
                        return AfirmaDeliveryResult.ACKNOWLEDGED
                    }
                    override suspend fun notifyCancellation(authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
                        serverCancellations++
                        return AfirmaDeliveryResult.ACKNOWLEDGED
                    }
                    override fun close() = invocation.close()
                }
            },
        )
    }

    companion object {
        private fun identity(certificate: X509Certificate = TestCertificateFactory.digitalSignatureCertificate()): UnlockedIdentity {
            val key = object : PrivateKey {
                override fun getAlgorithm() = "RSA"
                override fun getFormat(): String? = null
                override fun getEncoded(): ByteArray = error("Controller must never export or use the private key")
            }
            return UnlockedIdentity(key, certificate, listOf(certificate), CertificateSummary("Synthetic owner", "Synthetic issuer",
                certificate.notBefore.toInstant(), certificate.notAfter.toInstant()))
        }

        const val SELECT = "afirma://selectcert?id=fixture&stservlet=https%3A%2F%2Fstorage.example%2Fstore%3Ftoken%3Dprivate"
    }
}
