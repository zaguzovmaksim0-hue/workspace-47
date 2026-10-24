package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.TestCertificateFactory
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.verification.OfflineCmsStatus
import dev.junta.firmamobile.verification.OfflineCmsVerifier
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.time.Clock
import java.time.ZoneOffset
import org.bouncycastle.cms.CMSSignedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFileSigningControllerTest {
    private val clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC)
    private val session = CertificateSession(clock = clock).apply { unlock(syntheticIdentity()) }
    private val controller = LocalFileSigningController(
        session,
        BouncyCastleCadesSigner(clock = clock),
    )

    @Test
    fun reviewedLocalDocumentProducesVerifiedDetachedSignatureOnlyOnExplicitSave() {
        val document = "abc".encodeToByteArray()
        val token = controller.begin()
        controller.load(token, "document.txt") { document.inputStream() }
        val review = controller.state.value as LocalFileSigningState.Review
        assertEquals("document.txt", review.name)
        assertEquals(3, review.document.byteCount)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", review.document.sha256ForDisplay())
        assertEquals(session.identityForSigning()!!.summary.ownerName, review.certificateOwner)
        controller.sign(token)
        assertTrue(controller.state.value is LocalFileSigningState.Ready)
        assertNull(review.document.sha256ForDisplay())
        val output = ByteArrayOutputStream()
        controller.save(token) { output }
        val cms = output.toByteArray()
        assertNull(CMSSignedData(cms).signedContent)
        assertEquals(OfflineCmsStatus.VERIFIED, OfflineCmsVerifier.verify(cms, document).status)
        assertEquals(OfflineCmsStatus.INVALID, OfflineCmsVerifier.verify(cms, byteArrayOf(0)).status)
        assertTrue(controller.state.value is LocalFileSigningState.Saved)
        cms.fill(0)
        document.fill(0)
        controller.close()
    }

    @Test
    fun cancellationClearsReviewAndStaleSelectionCannotReadOrSave() {
        val old = controller.begin()
        controller.load(old, "old") { byteArrayOf(1).inputStream() }
        val review = controller.state.value as LocalFileSigningState.Review
        val current = controller.begin()
        assertNull(review.document.sha256ForDisplay())
        controller.load(old, "stale") { error("Stale selection opened") }
        controller.sign(old)
        controller.save(old) { error("Stale destination opened") }
        assertTrue(controller.state.value is LocalFileSigningState.Loading)
        controller.load(current, "new") { byteArrayOf(2).inputStream() }
        controller.sign(old)
        assertEquals("new", (controller.state.value as LocalFileSigningState.Review).name)
        controller.close()
    }

    @Test
    fun oversizedProviderIsReadOnlyToTheLimitPlusOneAndClosed() {
        var read = 0
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = 1.also { read++ }
            override fun close() { closed = true }
        }
        val token = controller.begin()
        controller.load(token, "oversized") { input }
        assertEquals(BouncyCastleCadesSigner.MAX_INPUT_BYTES + 1, read)
        assertTrue(closed)
        assertTrue(controller.state.value is LocalFileSigningState.Failed)
        controller.save(token) { error("Oversized input offered for saving") }
    }

    @Test
    fun emptyAndExactlyLimitDocumentsCanBeReviewed() {
        for (size in listOf(0, BouncyCastleCadesSigner.MAX_INPUT_BYTES)) {
            val token = controller.begin()
            controller.load(token, "bounded") { ByteArray(size).inputStream() }
            assertEquals(size, (controller.state.value as LocalFileSigningState.Review).document.byteCount)
        }
        controller.close()
    }

    @Test
    fun lockedOrReplacedCertificateCannotSignAReviewedDocument() {
        for (replace in listOf(false, true)) {
            session.unlock(syntheticIdentity())
            val token = controller.begin()
            controller.load(token, "document") { byteArrayOf(1).inputStream() }
            if (replace) session.unlock(freshSyntheticIdentity()) else session.lock()
            controller.sign(token)
            assertTrue(controller.state.value is LocalFileSigningState.Failed)
            controller.save(token) { error("Changed certificate offered for saving") }
        }
    }

    @Test
    fun selfVerificationFailureClearsBothInputsAndNeverOffersExport() {
        var ownedInput: ByteArray? = null
        val invalidCms = byteArrayOf(1, 2, 3)
        val broken = withSigner { content, _, _ ->
            ownedInput = content
            LocalSignatureResult.Success(LocalSignature(invalidCms))
        }
        val token = broken.begin()
        broken.load(token, "document") { byteArrayOf(42).inputStream() }
        broken.sign(token)
        assertTrue(broken.state.value is LocalFileSigningState.Failed)
        broken.save(token) { error("Invalid CMS offered for saving") }
        assertTrue(invalidCms.all { it == 0.toByte() })
        assertTrue(checkNotNull(ownedInput).all { it == 0.toByte() })
    }

    @Test
    fun cancellationDuringSigningDiscardsAndClearsTheLateResult() {
        val lateCms = byteArrayOf(4, 5, 6)
        lateinit var cancelled: LocalFileSigningController
        cancelled = withSigner { _, _, _ ->
            cancelled.close()
            LocalSignatureResult.Success(LocalSignature(lateCms))
        }
        val token = cancelled.begin()
        cancelled.load(token, "document") { byteArrayOf(1).inputStream() }
        cancelled.sign(token)
        assertTrue(cancelled.state.value is LocalFileSigningState.Idle)
        assertTrue(lateCms.all { it == 0.toByte() })
        cancelled.save(token) { error("Cancelled signature offered for saving") }
    }

    @Test
    fun cancellationDuringReadClosesStreamAndCannotReplaceNewSelection() {
        var closed = false
        val old = controller.begin()
        val stream = object : InputStream() {
            override fun read(): Int { controller.close(); return 1 }
            override fun close() { closed = true }
        }
        controller.load(old, "cancelled") { stream }
        assertTrue(closed)
        assertTrue(controller.state.value is LocalFileSigningState.Idle)
    }

    @Test
    fun failedSaveClosesDestinationAndCannotBeRetriedWithoutFreshConsent() {
        val token = controller.begin()
        controller.load(token, "document") { byteArrayOf(1).inputStream() }
        controller.sign(token)
        var closed = false
        controller.save(token) {
            object : ByteArrayOutputStream() {
                override fun write(bytes: ByteArray, offset: Int, length: Int) { throw IOException("synthetic") }
                override fun close() { closed = true }
            }
        }
        assertTrue(closed)
        assertTrue(controller.state.value is LocalFileSigningState.Failed)
        var opened = false
        controller.save(token) { opened = true; ByteArrayOutputStream() }
        assertFalse(opened)
    }

    private fun withSigner(
        sign: (ByteArray, UnlockedIdentity, SigningAlgorithm) -> LocalSignatureResult,
    ) = LocalFileSigningController(session, object : CadesSigner {
        override fun signDetached(content: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm) =
            sign(content, identity, algorithm)
    })
}
