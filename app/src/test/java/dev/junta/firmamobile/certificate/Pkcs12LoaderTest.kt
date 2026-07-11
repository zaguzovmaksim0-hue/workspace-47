package dev.junta.firmamobile.certificate

import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pkcs12LoaderTest {
    private val clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC)

    @Test
    fun loadsOneValidRsaPrivateKeyEntryAndClearsInternalCopies() = runTest {
        val cleared = mutableSetOf<SensitiveCopyKind>()
        val loader = Pkcs12Loader(
            clock = clock,
            sensitiveCopyObserver = SensitiveCopyObserver { kind, allZero ->
                assertTrue("$kind was not zeroed", allZero)
                cleared += kind
            },
        )
        val bytes = TestCertificateFactory.validRsa()
        val password = TestCertificateFactory.password()
        val originalPassword = password.copyOf()

        val result = loader.load(ByteArrayInputStream(bytes), bytes.size.toLong(), password)

        val success = result as CertificateLoadResult.Success
        assertEquals("Persona de Prueba", success.identity.summary.ownerName)
        assertEquals("Persona de Prueba", success.identity.summary.issuerName)
        assertEquals(1, success.identity.chain.size)
        assertArrayEquals(originalPassword, password)
        assertEquals(
            setOf(
                SensitiveCopyKind.PASSWORD,
                SensitiveCopyKind.PKCS12_BYTES,
                SensitiveCopyKind.KEY_MATCH_CHALLENGE,
            ),
            cleared,
        )
    }

    @Test
    fun rejectsWrongPasswordWithoutLeakingAThrowableMessage() = runTest {
        val result = load(TestCertificateFactory.validRsa(), "incorrecta".toCharArray())

        assertFailure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE, result)
    }

    @Test
    fun rejectsMalformedInput() = runTest {
        val result = load(byteArrayOf(1, 2, 3, 4))

        assertFailure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE, result)
    }

    @Test
    fun rejectsDeclaredOversizeBeforeReading() = runTest {
        val result = Pkcs12Loader(clock = clock).load(
            input = ByteArrayInputStream(byteArrayOf(1)),
            declaredSize = Pkcs12Loader.MAX_INPUT_BYTES + 1L,
            password = TestCertificateFactory.password(),
        )

        assertFailure(CertificateErrorCode.FILE_TOO_LARGE, result)
    }

    @Test
    fun rejectsActualOversizeBeforeParsing() = runTest {
        val result = load(ByteArray(Pkcs12Loader.MAX_INPUT_BYTES + 1))

        assertFailure(CertificateErrorCode.FILE_TOO_LARGE, result)
    }

    @Test
    fun rejectsCertificateOnlyStore() = runTest {
        assertFailure(
            CertificateErrorCode.PRIVATE_KEY_MISSING,
            load(TestCertificateFactory.certificateOnly()),
        )
    }

    @Test
    fun rejectsEcPrivateKey() = runTest {
        assertFailure(
            CertificateErrorCode.UNSUPPORTED_KEY_TYPE,
            load(TestCertificateFactory.ecIdentity()),
        )
    }

    @Test
    fun rejectsExpiredCertificate() = runTest {
        assertFailure(
            CertificateErrorCode.CERTIFICATE_EXPIRED,
            load(TestCertificateFactory.expired()),
        )
    }

    @Test
    fun rejectsNotYetValidCertificate() = runTest {
        assertFailure(
            CertificateErrorCode.CERTIFICATE_NOT_YET_VALID,
            load(TestCertificateFactory.notYetValid()),
        )
    }

    @Test
    fun rejectsCertificateWithoutDigitalSignatureUsage() = runTest {
        assertFailure(
            CertificateErrorCode.KEY_USAGE_NOT_PERMITTED,
            load(TestCertificateFactory.withoutDigitalSignatureUsage()),
        )
    }

    @Test
    fun rejectsMultiplePrivateKeyEntries() = runTest {
        assertFailure(
            CertificateErrorCode.MULTIPLE_PRIVATE_KEYS,
            load(TestCertificateFactory.multiplePrivateEntries()),
        )
    }

    @Test
    fun rejectsMoreThanThirtyTwoAliases() = runTest {
        assertFailure(
            CertificateErrorCode.TOO_MANY_ENTRIES,
            load(TestCertificateFactory.tooManyAliases()),
        )
    }

    @Test
    fun rejectsChainLongerThanSixteenCertificates() = runTest {
        assertFailure(
            CertificateErrorCode.CHAIN_TOO_LONG,
            load(TestCertificateFactory.oversizedChain()),
        )
    }

    @Test
    fun rejectsMismatchedPrivateKeyAndCertificate() = runTest {
        assertFailure(
            CertificateErrorCode.KEY_CERTIFICATE_MISMATCH,
            load(TestCertificateFactory.mismatchedKeyAndCertificate()),
        )
    }

    @Test
    fun resultModelHasNoArbitraryThrowableOrMessageField() {
        val forbiddenTypes = setOf(Throwable::class.java, String::class.java)
        val failureFields = CertificateLoadResult.Failure::class.java.declaredFields

        assertTrue(failureFields.none { it.type in forbiddenTypes })
    }

    private suspend fun load(
        bytes: ByteArray,
        password: CharArray = TestCertificateFactory.password(),
    ): CertificateLoadResult = Pkcs12Loader(clock = clock).load(
        input = ByteArrayInputStream(bytes),
        declaredSize = bytes.size.toLong(),
        password = password,
    )

    private fun assertFailure(
        expected: CertificateErrorCode,
        result: CertificateLoadResult,
    ) {
        assertEquals(expected, (result as CertificateLoadResult.Failure).code)
    }
}
