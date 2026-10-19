package dev.junta.firmamobile.verification

import java.lang.reflect.Modifier
import java.security.Security
import java.util.Date
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCmsVerifierTest {
    private val content = "offline CMS integrity payload".toByteArray(Charsets.UTF_8)

    @Test
    fun verifiesAttachedAndDetachedContent() {
        val signer = OfflineCmsFixtures.signer()
        val attached = OfflineCmsFixtures.signed(content, listOf(signer))
        val detached = OfflineCmsFixtures.signed(
            content,
            listOf(signer),
            OfflineCmsOptions(encapsulate = false),
        )

        OfflineCmsAssertions.assertVerified(attached)
        OfflineCmsAssertions.assertVerified(detached, content)
    }

    @Test
    fun distinguishesAttachedAndDetachedEmptyContent() {
        val signer = OfflineCmsFixtures.signer()
        val attached = OfflineCmsFixtures.signed(byteArrayOf(), listOf(signer))
        val detached = OfflineCmsFixtures.signed(
            byteArrayOf(),
            listOf(signer),
            OfflineCmsOptions(encapsulate = false),
        )

        OfflineCmsAssertions.assertVerified(attached)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.DETACHED_CONTENT_REQUIRED, detached)
        OfflineCmsAssertions.assertVerified(detached, byteArrayOf())
    }

    @Test
    fun rejectsAnySuppliedDetachedContentForAttachedCms() {
        val cms = OfflineCmsFixtures.signed(content, listOf(OfflineCmsFixtures.signer()))

        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, cms, byteArrayOf())
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, cms, content)
    }

    @Test
    fun rejectsTamperedSignatureAndSameLengthDetachedContent() {
        val signer = OfflineCmsFixtures.signer()
        val detached = OfflineCmsFixtures.signed(
            content,
            listOf(signer),
            OfflineCmsOptions(encapsulate = false),
        )
        val changedContent = content.clone()
        changedContent[0] = (changedContent[0].toInt() xor CHANGE_MASK).toByte()

        OfflineCmsAssertions.assertRejected(
            OfflineCmsStatus.INVALID,
            OfflineCmsSignerInfoFixtures.tamperSignature(detached),
            content,
        )
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, detached, changedContent)
    }

    @Test
    fun verifiesEverySignerAndRejectsTheWholeSetWhenOneFails() {
        val signers = listOf(
            OfflineCmsFixtures.signer(),
            OfflineCmsFixtures.signer(),
        )
        val cms = OfflineCmsFixtures.signed(content, signers)
        val oneBadSigner = OfflineCmsSignerInfoFixtures.tamperSignature(cms, index = SECOND_SIGNER)

        OfflineCmsAssertions.assertVerified(cms, signerCount = signers.size)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, oneBadSigner)
    }

    @Test
    fun rejectsMissingAmbiguousAndDuplicateSignerCertificates() {
        val signer = OfflineCmsFixtures.signer()
        val noSigners = OfflineCmsFixtures.signed(content, emptyList())
        val noCertificates = OfflineCmsFixtures.signed(
            content,
            listOf(signer),
            OfflineCmsOptions(certificates = emptyList()),
        )
        val unrelated = OfflineCmsFixtures.signer(serial = UNRELATED_CERTIFICATE_SERIAL)
        val noMatchingCertificate = OfflineCmsFixtures.signed(
            content,
            listOf(signer),
            OfflineCmsOptions(certificates = listOf(unrelated.certificate)),
        )
        val ambiguousMate = OfflineCmsFixtures.signer(
            serial = AMBIGUOUS_SERIAL,
            notBefore = Date(EXPIRED_START_MILLIS),
        )
        val ambiguousSigner = OfflineCmsFixtures.signer(
            serial = AMBIGUOUS_SERIAL,
            notBefore = Date(DISTINCT_CERTIFICATE_START_MILLIS),
        )
        val ambiguous = OfflineCmsFixtures.signed(
            content,
            listOf(ambiguousSigner),
            OfflineCmsOptions(
                certificates = listOf(ambiguousSigner.certificate, ambiguousMate.certificate),
            ),
        )
        val oneSigner = OfflineCmsFixtures.signed(content, listOf(signer))
        val duplicateSigner = OfflineCmsSignerInfoFixtures.duplicateFirstSigner(oneSigner)

        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, noSigners)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, noCertificates)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, noMatchingCertificate)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, ambiguous)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, duplicateSigner)
    }

    @Test
    fun keepsProviderRegistryInputsAndResultStable() {
        val providersBefore = Security.getProviders().map { it.name }
        val signer = OfflineCmsFixtures.signer()
        val cmsBytes = OfflineCmsFixtures.signed(
            content,
            listOf(signer),
            OfflineCmsOptions(encapsulate = false),
        )
        val cmsInput = cmsBytes.clone()
        val contentInput = content.clone()
        val expected = OfflineCmsResult(
            OfflineCmsStatus.VERIFIED,
            verifiedSigners = VERIFIED_SIGNER_COUNT,
            scope = OfflineCmsScope.INTEGRITY_ONLY,
            trust = OfflineCmsTrust.NOT_EVALUATED,
        )

        val result = OfflineCmsVerifier.verify(cmsInput, contentInput)

        assertEquals(expected, result)
        assertArrayEquals(cmsBytes, cmsInput)
        assertArrayEquals(content, contentInput)
        assertEquals(providersBefore, Security.getProviders().map { it.name })

        cmsInput[0] = (cmsInput[0].toInt() xor CHANGE_MASK).toByte()
        contentInput[0] = (contentInput[0].toInt() xor CHANGE_MASK).toByte()
        assertEquals(expected, result)

        val fields = OfflineCmsResult::class.java.declaredFields.filterNot { it.isSynthetic }
        assertEquals(
            setOf("status", "verifiedSigners", "scope", "trust"),
            fields.map { it.name }.toSet(),
        )
        assertTrue(fields.all { Modifier.isFinal(it.modifiers) })
        assertFalse(fields.any { it.type.isArray })
    }

    @Test
    fun permitsExpiredSelfSignedCertificateForIntegrityVerification() {
        val expired = OfflineCmsFixtures.signer(
            notBefore = Date(EXPIRED_START_MILLIS),
            notAfter = Date(EXPIRED_END_MILLIS),
        )
        val cms = OfflineCmsFixtures.signed(content, listOf(expired))

        OfflineCmsAssertions.assertVerified(cms)
    }

    private companion object {
        const val CHANGE_MASK = 1
        const val VERIFIED_SIGNER_COUNT = 1
        const val SECOND_SIGNER = 1
        const val UNRELATED_CERTIFICATE_SERIAL = 90_002L
        const val AMBIGUOUS_SERIAL = 90_001L
        const val DISTINCT_CERTIFICATE_START_MILLIS = 2_000L
        const val EXPIRED_START_MILLIS = 0L
        const val EXPIRED_END_MILLIS = 1L
    }
}
