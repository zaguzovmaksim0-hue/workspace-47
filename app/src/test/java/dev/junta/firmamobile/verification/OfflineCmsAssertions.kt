package dev.junta.firmamobile.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

internal object OfflineCmsAssertions {
    private const val NO_VERIFIED_SIGNERS = 0
    fun assertVerified(
        cms: ByteArray,
        detachedContent: ByteArray? = null,
        signerCount: Int = 1,
    ) {
        val result = OfflineCmsVerifier.verify(cms, detachedContent)
        assertEquals(OfflineCmsStatus.VERIFIED, result.status)
        assertEquals(signerCount, result.verifiedSigners)
        assertEquals(OfflineCmsScope.INTEGRITY_ONLY, result.scope)
        assertEquals(OfflineCmsTrust.NOT_EVALUATED, result.trust)
    }

    fun assertRejected(
        expected: OfflineCmsStatus,
        cms: ByteArray,
        detachedContent: ByteArray? = null,
    ) {
        val result = OfflineCmsVerifier.verify(cms, detachedContent)
        assertEquals(expected, result.status)
        assertEquals(NO_VERIFIED_SIGNERS, result.verifiedSigners)
        assertEquals(OfflineCmsScope.INTEGRITY_ONLY, result.scope)
        assertEquals(OfflineCmsTrust.NOT_EVALUATED, result.trust)
    }

    fun assertNotVerified(cms: ByteArray) {
        val result = OfflineCmsVerifier.verify(cms)
        assertFalse(result.status == OfflineCmsStatus.VERIFIED)
        assertEquals(0, result.verifiedSigners)
        assertEquals(OfflineCmsScope.INTEGRITY_ONLY, result.scope)
        assertEquals(OfflineCmsTrust.NOT_EVALUATED, result.trust)
    }
}
