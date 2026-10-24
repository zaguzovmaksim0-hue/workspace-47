package dev.junta.firmamobile.certificate

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Pkcs12AliasSelectionTest {
    private val clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC)
    private val loader = Pkcs12Loader(clock = clock)

    @Test
    fun explicitlyLoadsTheChosenPrivateEntryRatherThanTheFirst() = runBlocking {
        val bytes = TestCertificateFactory.multiplePrivateEntries()
        inspect(bytes).use { selection ->
            assertEquals(2, selection.candidates.size)
            assertEquals(setOf(1, 2), selection.candidates.map { it.number }.toSet())
            val chosen = selection.candidates.last()
            assertFalse(selection.candidates.first().certificateSha256 == chosen.certificateSha256)
            val loaded = loadSelected(bytes, selection, chosen) as CertificateLoadResult.Success
            assertEquals(chosen.summary.ownerName, loaded.identity.summary.ownerName)
            assertEquals(chosen.certificateSha256, sha256(loaded.identity.certificate.encoded))
        }
    }

    @Test
    fun ordinaryLoadRemainsFailClosedForMultipleEntriesAndAcceptsOnePrivatePlusPublicEntries() = runBlocking {
        assertFailure(CertificateErrorCode.MULTIPLE_PRIVATE_KEYS, load(TestCertificateFactory.multiplePrivateEntries()))
        val bytes = bundle(listOf(TestCertificateFactory.validRsa()), certificateOnlyAliases = 2)
        inspect(bytes).use { selection -> assertEquals(1, selection.candidates.size) }
        val loaded = load(bytes) as CertificateLoadResult.Success
        assertEquals("Persona de Prueba", loaded.identity.summary.ownerName)
    }

    @Test
    fun previewDoesNotOfferCertificateOnlyEntries() = runBlocking {
        assertEquals(
            Pkcs12InspectionResult.Failure(CertificateErrorCode.PRIVATE_KEY_MISSING),
            preview(TestCertificateFactory.certificateOnly()),
        )
        val bytes = bundle(
            listOf(TestCertificateFactory.validRsa(), TestCertificateFactory.freshValidRsa()),
            certificateOnlyAliases = 1,
        )
        inspect(bytes).use { selection -> assertEquals(2, selection.candidates.size) }
    }

    @Test
    fun candidateAndResultStringsDoNotRevealRawAliasesOrCredentials() = runBlocking {
        val rawAlias = "private-alias-\u202e" + "x".repeat(600)
        val bytes = bundle(listOf(TestCertificateFactory.validRsa()), aliasPrefix = rawAlias)
        val result = preview(bytes) as Pkcs12InspectionResult.Success
        result.selection.use { selection ->
            val candidate = selection.candidates.single()
            assertEquals("Persona de Prueba", candidate.summary.ownerName)
            assertEquals(64, candidate.certificateSha256.length)
            assertTrue(candidate.summary.ownerName.length <= 256)
            val descriptions = listOf(result.toString(), selection.toString(), candidate.toString(), candidate.summary.toString())
            descriptions.take(3).forEach { assertFalse(it.contains("Persona de Prueba")) }
            descriptions.forEach { description ->
                assertFalse(description.contains("private-alias"))
                assertFalse(description.contains("test-password-123"))
            }
        }
        assertTrue(Pkcs12InspectionResult.Failure::class.java.declaredFields.none {
            it.type == String::class.java || Throwable::class.java.isAssignableFrom(it.type)
        })
    }

    @Test
    fun rejectsForeignCandidateEvenWhenItsDisplayMetadataMatches() = runBlocking {
        val bytes = TestCertificateFactory.validRsa()
        inspect(bytes).use { first ->
            inspect(bytes).use { second ->
                val original = first.candidates.single()
                val foreign = second.candidates.single()
                assertEquals(original.summary, foreign.summary)
                assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loadSelected(bytes, first, foreign))
                assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loadSelected(bytes, first, original))
                assertTrue(loadSelected(bytes, second, foreign) is CertificateLoadResult.Success)
            }
        }
    }

    @Test
    fun nullChoiceAndExplicitCancellationCannotFallBackToAnyPrivateKey() = runBlocking {
        val bytes = TestCertificateFactory.multiplePrivateEntries()
        inspect(bytes).use { selection ->
            val candidate = selection.candidates.first()
            assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loadSelected(bytes, selection, null))
            assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loadSelected(bytes, selection, candidate))
        }
        val selection = inspect(bytes)
        val candidate = selection.candidates.first()
        selection.close()
        val noRead = object : InputStream() {
            override fun read(): Int = error("Cancelled selections must not read input")
        }
        assertFailure(
            CertificateErrorCode.NO_CERTIFICATE_SELECTED,
            loader.loadSelected(noRead, bytes.size.toLong(), TestCertificateFactory.password(), selection, candidate),
        )
        assertTrue(selection.candidates.isEmpty())
    }

    @Test
    fun successConsumesAllCandidatesAndCannotBeReplayed() = runBlocking {
        val bytes = TestCertificateFactory.multiplePrivateEntries()
        inspect(bytes).use { selection ->
            val choices = selection.candidates
            assertTrue(loadSelected(bytes, selection, choices.first()) is CertificateLoadResult.Success)
            choices.forEach { candidate ->
                assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loadSelected(bytes, selection, candidate))
            }
        }
    }

    @Test
    fun changedContainerIsRejectedEvenWithTheSameAliasesAndCertificates() = runBlocking {
        val bytes = TestCertificateFactory.multiplePrivateEntries()
        val changed = reserialize(bytes)
        assertFalse(bytes.contentEquals(changed))
        inspect(bytes).use { selection ->
            val chosen = selection.candidates.first()
            assertFailure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE, loadSelected(changed, selection, chosen))
            assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loadSelected(bytes, selection, chosen))
        }
    }

    @Test
    fun wrongPasswordConsumesSelectionWithoutExposingExceptionDetails() = runBlocking {
        val bytes = TestCertificateFactory.multiplePrivateEntries()
        inspect(bytes).use { selection ->
            val candidate = selection.candidates.first()
            assertFailure(
                CertificateErrorCode.INVALID_PASSWORD_OR_FILE,
                loadSelected(bytes, selection, candidate, "incorrecta".toCharArray()),
            )
            assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loadSelected(bytes, selection, candidate))
        }
        assertEquals(
            Pkcs12InspectionResult.Failure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE),
            preview(bytes, "incorrecta".toCharArray()),
        )
    }

    @Test
    fun selectedInvalidEntryNeverFallsBackToTheValidAlternative() = runBlocking {
        val cases = listOf(
            TestCertificateFactory.expired() to CertificateErrorCode.CERTIFICATE_EXPIRED,
            TestCertificateFactory.notYetValid() to CertificateErrorCode.CERTIFICATE_NOT_YET_VALID,
            TestCertificateFactory.withoutDigitalSignatureUsage() to CertificateErrorCode.KEY_USAGE_NOT_PERMITTED,
            TestCertificateFactory.mismatchedKeyAndCertificate() to CertificateErrorCode.KEY_CERTIFICATE_MISMATCH,
            TestCertificateFactory.ecIdentity() to CertificateErrorCode.UNSUPPORTED_KEY_TYPE,
            TestCertificateFactory.oversizedChain() to CertificateErrorCode.CHAIN_TOO_LONG,
        )
        for ((invalid, expected) in cases) {
            val bytes = bundle(listOf(TestCertificateFactory.validRsa(), invalid))
            inspect(bytes).use { selection ->
                val chosen = selection.candidates.single { it.summary.ownerName != "Persona de Prueba" }
                assertFailure(expected, loadSelected(bytes, selection, chosen))
            }
        }
    }

    @Test
    fun anInvalidUnselectedEntryDoesNotChangeTheExplicitlyChosenIdentity() = runBlocking {
        val bytes = bundle(listOf(TestCertificateFactory.expired(), TestCertificateFactory.validRsa()))
        inspect(bytes).use { selection ->
            val chosen = selection.candidates.single { it.summary.ownerName == "Persona de Prueba" }
            assertTrue(loadSelected(bytes, selection, chosen) is CertificateLoadResult.Success)
        }
    }

    @Test
    fun selectionDoesNotBypassValidityAtTheTimeOfLoading() = runBlocking {
        val bytes = TestCertificateFactory.validRsa()
        inspect(bytes).use { selection ->
            val laterLoader = Pkcs12Loader(clock = Clock.fixed(TestCertificateFactory.now.plusSeconds(86_400L * 366), ZoneOffset.UTC))
            assertFailure(
                CertificateErrorCode.CERTIFICATE_EXPIRED,
                laterLoader.loadSelected(
                    ByteArrayInputStream(bytes), bytes.size.toLong(), TestCertificateFactory.password(),
                    selection, selection.candidates.single(),
                ),
            )
        }
    }

    @Test
    fun keepsTheThirtyTwoAliasBoundIncludingPublicEntries() = runBlocking {
        val bytes = bundle(listOf(TestCertificateFactory.validRsa()), certificateOnlyAliases = 31)
        inspect(bytes).use { selection -> assertEquals(1, selection.candidates.size) }
        assertEquals(
            Pkcs12InspectionResult.Failure(CertificateErrorCode.TOO_MANY_ENTRIES),
            preview(bundle(listOf(TestCertificateFactory.validRsa()), certificateOnlyAliases = 32)),
        )
        assertEquals(
            Pkcs12InspectionResult.Failure(CertificateErrorCode.TOO_MANY_ENTRIES),
            preview(TestCertificateFactory.tooManyAliases()),
        )
    }

    @Test
    fun previewRetainsInputBoundsAndSanitizedParseFailures() = runBlocking {
        assertEquals(
            Pkcs12InspectionResult.Failure(CertificateErrorCode.FILE_TOO_LARGE),
            loader.inspect(
                ByteArrayInputStream(ByteArray(Pkcs12Loader.MAX_INPUT_BYTES + 1)),
                declaredSize = null, password = TestCertificateFactory.password(),
            ),
        )
        val noRead = object : InputStream() {
            override fun read(): Int = error("Oversize declarations must not read input")
        }
        assertEquals(
            Pkcs12InspectionResult.Failure(CertificateErrorCode.FILE_TOO_LARGE),
            loader.inspect(noRead, Pkcs12Loader.MAX_INPUT_BYTES + 1L, TestCertificateFactory.password()),
        )
        assertEquals(
            Pkcs12InspectionResult.Failure(CertificateErrorCode.INVALID_PKCS12_FILE),
            preview(byteArrayOf(1, 2, 3)),
        )
    }

    @Test
    fun previewClearsInternalInputAndPasswordCopiesWithoutPerformingKeyMatch() = runBlocking {
        val cleared = mutableSetOf<SensitiveCopyKind>()
        val observedLoader = Pkcs12Loader(clock = clock, sensitiveCopyObserver = SensitiveCopyObserver { kind, allZero ->
            assertTrue(allZero)
            cleared += kind
        })
        val bytes = TestCertificateFactory.multiplePrivateEntries()
        val password = TestCertificateFactory.password()
        val originalPassword = password.copyOf()
        val result = observedLoader.inspect(ByteArrayInputStream(bytes), bytes.size.toLong(), password)
            as Pkcs12InspectionResult.Success
        result.selection.close()
        assertArrayEquals(originalPassword, password)
        assertEquals(setOf(SensitiveCopyKind.PASSWORD, SensitiveCopyKind.PKCS12_BYTES), cleared)
    }

    @Test
    fun cancellationPropagatesClearsCopiesAndInvalidatesTheChoice() = runBlocking {
        val bytes = TestCertificateFactory.validRsa()
        inspect(bytes).use { selection ->
            val candidate = selection.candidates.single()
            val cancellation = CancellationException("synthetic cancellation")
            val cancelledInput = object : InputStream() {
                override fun read(): Int = throw cancellation
            }
            val cleared = mutableSetOf<SensitiveCopyKind>()
            val observedLoader = Pkcs12Loader(clock = clock, sensitiveCopyObserver = SensitiveCopyObserver { kind, allZero ->
                assertTrue(allZero)
                cleared += kind
            })
            try {
                observedLoader.loadSelected(cancelledInput, null, TestCertificateFactory.password(), selection, candidate)
                fail("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertSame(cancellation, actual)
            }
            assertEquals(setOf(SensitiveCopyKind.PASSWORD), cleared)
            assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loadSelected(bytes, selection, candidate))
        }
    }

    @Test
    fun choiceExpiresAfterFiveMonotonicMinutesAndRejectsClockRollback() = runBlocking {
        val bytes = TestCertificateFactory.validRsa()
        val cases = listOf(299_999_999_999L to true, 300_000_000_000L to false, -1L to false)
        for ((ageNanos, permitted) in cases) {
            var nowNanos = 1_000L
            val timedLoader = Pkcs12Loader(clock = clock, monotonicNanos = { nowNanos })
            val result = timedLoader.inspect(ByteArrayInputStream(bytes), bytes.size.toLong(), TestCertificateFactory.password())
                as Pkcs12InspectionResult.Success
            result.selection.use { selection ->
                val candidate = selection.candidates.single()
                nowNanos += ageNanos
                val loaded = timedLoader.loadSelected(
                    ByteArrayInputStream(bytes), bytes.size.toLong(), TestCertificateFactory.password(), selection, candidate,
                )
                if (permitted) assertTrue(loaded is CertificateLoadResult.Success)
                else assertFailure(CertificateErrorCode.NO_CERTIFICATE_SELECTED, loaded)
                assertTrue(selection.candidates.isEmpty())
            }
        }
    }

    @Test
    fun failureClearsInternalCopiesAndReplayCreatesNoNewPasswordOrInputCopy() = runBlocking {
        val bytes = TestCertificateFactory.validRsa()
        val cleared = mutableSetOf<SensitiveCopyKind>()
        val observedLoader = Pkcs12Loader(clock = clock, sensitiveCopyObserver = SensitiveCopyObserver { kind, allZero ->
            assertTrue(allZero)
            cleared += kind
        })
        val preview = observedLoader.inspect(ByteArrayInputStream(bytes), bytes.size.toLong(), TestCertificateFactory.password())
            as Pkcs12InspectionResult.Success
        preview.selection.use { selection ->
            val candidate = selection.candidates.single()
            val wrongPassword = "incorrecta".toCharArray()
            val originalPassword = wrongPassword.copyOf()
            cleared.clear()
            assertFailure(
                CertificateErrorCode.INVALID_PASSWORD_OR_FILE,
                observedLoader.loadSelected(ByteArrayInputStream(bytes), bytes.size.toLong(), wrongPassword, selection, candidate),
            )
            assertEquals(setOf(SensitiveCopyKind.PASSWORD, SensitiveCopyKind.PKCS12_BYTES), cleared)
            assertArrayEquals(originalPassword, wrongPassword)
            cleared.clear()
            assertFailure(
                CertificateErrorCode.NO_CERTIFICATE_SELECTED,
                observedLoader.loadSelected(ByteArrayInputStream(bytes), bytes.size.toLong(), wrongPassword, selection, candidate),
            )
            assertTrue(cleared.isEmpty())
        }
    }

    @Test
    fun racingCompletionsCanLoadOnlyOneOfTheOfferedIdentities() = runBlocking {
        val bytes = TestCertificateFactory.multiplePrivateEntries()
        inspect(bytes).use { selection ->
            val candidates = selection.candidates
            val results = candidates.map { candidate ->
                async(Dispatchers.Default) { loadSelected(bytes, selection, candidate) }
            }.awaitAll()
            assertEquals(1, results.count { it is CertificateLoadResult.Success })
            assertEquals(
                listOf(CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)),
                results.filterIsInstance<CertificateLoadResult.Failure>(),
            )
        }
    }

    private suspend fun preview(bytes: ByteArray, password: CharArray = TestCertificateFactory.password()) =
        loader.inspect(ByteArrayInputStream(bytes), bytes.size.toLong(), password)

    private suspend fun inspect(bytes: ByteArray): Pkcs12AliasSelection =
        (preview(bytes) as Pkcs12InspectionResult.Success).selection

    private suspend fun load(bytes: ByteArray) =
        loader.load(ByteArrayInputStream(bytes), bytes.size.toLong(), TestCertificateFactory.password())

    private suspend fun loadSelected(
        bytes: ByteArray,
        selection: Pkcs12AliasSelection,
        candidate: Pkcs12Candidate?,
        password: CharArray = TestCertificateFactory.password(),
    ) = loader.loadSelected(ByteArrayInputStream(bytes), bytes.size.toLong(), password, selection, candidate)

    private fun assertFailure(expected: CertificateErrorCode, actual: CertificateLoadResult) {
        assertEquals(CertificateLoadResult.Failure(expected), actual)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun reserialize(bytes: ByteArray): ByteArray {
        val password = TestCertificateFactory.password()
        return try {
            val store = KeyStore.getInstance("PKCS12")
            store.load(ByteArrayInputStream(bytes), password)
            ByteArrayOutputStream().use { output ->
                store.store(output, password)
                output.toByteArray()
            }
        } finally {
            password.fill('\u0000')
        }
    }

    /** Synthetic stores only. Aliases are deliberately unrelated to certificate identities. */
    private fun bundle(
        containers: List<ByteArray>,
        certificateOnlyAliases: Int = 0,
        aliasPrefix: String = "private-alias-",
    ): ByteArray {
        val password = TestCertificateFactory.password()
        return try {
            val store = KeyStore.getInstance("PKCS12")
            store.load(null, password)
            containers.forEachIndexed { index, bytes ->
                val source = KeyStore.getInstance("PKCS12")
                source.load(ByteArrayInputStream(bytes), password)
                val alias = source.aliases().toList().single { source.isKeyEntry(it) }
                store.setKeyEntry("$aliasPrefix$index", source.getKey(alias, password), password, source.getCertificateChain(alias))
            }
            repeat(certificateOnlyAliases) { index ->
                store.setCertificateEntry("public-only-$index", TestCertificateFactory.digitalSignatureCertificate())
            }
            ByteArrayOutputStream().use { output ->
                store.store(output, password)
                output.toByteArray()
            }
        } finally {
            containers.forEach { it.fill(0) }
            password.fill('\u0000')
        }
    }
}
