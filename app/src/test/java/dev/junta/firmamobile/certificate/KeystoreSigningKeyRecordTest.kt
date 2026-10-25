package dev.junta.firmamobile.certificate

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.junta.firmamobile.signing.syntheticIdentity
import java.security.Security
import java.io.IOException
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class KeystoreSigningKeyRecordTest {
    private val provider = HardwareKeyProvider()
    private val backend = FakeSigningKeyStoreBackend(strongBoxAvailable = true)
    private val data = MemoryPreferences()
    private val store = PreferencesCertificateReferenceStore(data)
    private val identity = syntheticIdentity()
    private val documents = object : CertificateDocumentAccess {
        override fun queryMetadata(uri: Uri) = error("Not used")
        override fun takePersistableReadPermission(uri: Uri) = error("Not used")
        override fun releasePersistableReadPermission(uri: Uri) = error("Not used")
        override fun open(uri: Uri): java.io.InputStream {
            assertEquals(source().uri, uri)
            return ByteArrayInputStream(TestCertificateFactory.validRsa())
        }
    }

    @Before fun installProvider() { Security.insertProviderAt(provider, 1) }
    @After fun removeProvider() { Security.removeProvider(provider.name) }

    @Test fun explicitMigrationCommitsVerifiedReferenceBeforeForgettingSource() = runBlocking {
        store.write(source())
        val before = checkNotNull(store.readCatalog().active)
        val keys = keys()

        val result = keys.migrate(before, TestCertificateFactory.password(), acknowledgeKeyLoss = true, allowTrustedEnvironment = false)
        val migrated = checkNotNull(store.readCatalog().active)
        assertTrue(result is SigningKeyImportResult.Imported)
        assertEquals(before.id, migrated.id)
        assertEquals(before.revision + 1, migrated.revision)
        assertEquals(before.reference, migrated.reference)
        assertNotNull(migrated.signingKey)
        assertEquals(StoredKeystoreLevel.STRONGBOX, migrated.signingKey?.level)
        assertEquals(setOf(migrated.signingKey?.alias), backend.keys.keys)
        assertEquals(store.readCatalog(), PreferencesCertificateReferenceStore(data).readCatalog())

        keys.forgetSource(migrated)
        val forgotten = checkNotNull(store.readCatalog().active)
        assertNull(forgotten.reference)
        assertEquals(migrated.signingKey, forgotten.signingKey)
    }

    @Test fun defaultFlagAndMissingAcknowledgementCannotImport() = runBlocking {
        store.write(source())
        val entry = checkNotNull(store.readCatalog().active)
        assertTrue(runCatching {
            keys(enabled = false).migrate(entry, TestCertificateFactory.password(), true, false)
        }.isFailure)
        assertTrue(runCatching {
            keys().migrate(entry, TestCertificateFactory.password(), false, false)
        }.isFailure)
        assertTrue(backend.keys.isEmpty())
        assertEquals(entry, store.readCatalog().active)
    }

    @Test fun failedCommitRollsBackAliasAndPreservesSource() = runBlocking {
        store.write(source())
        val entry = checkNotNull(store.readCatalog().active)
        data.failNextCommit = true
        assertTrue(runCatching { migrate(entry) }.exceptionOrNull() is IOException)
        assertTrue(backend.keys.isEmpty())
        assertEquals(entry, store.readCatalog().active)
    }

    @Test fun failedOptionalForgetRetainsBothCommittedKeyAndSource() = runBlocking {
        val migrated = migrateSource()
        data.failNextCommit = true
        assertTrue(runCatching { keys().forgetSource(migrated) }.exceptionOrNull() is IOException)
        assertEquals(migrated, store.readCatalog().active)
        assertEquals(setOf(migrated.signingKey?.alias), backend.keys.keys)
    }

    @Test fun deletionCommitsTombstoneBeforeDeletingAndRetriesAfterRestart() = runBlocking {
        val migrated = migrateSource()
        backend.failDelete = true
        assertTrue(runCatching { keys().remove(migrated) }.isFailure)
        assertNull(store.readCatalog().active)
        assertTrue(store.readCatalog().entries.isEmpty())
        assertEquals(setOf(migrated.signingKey?.alias), store.readCatalog().tombstones)
        backend.failDelete = false
        keys().sweepOrphans()
        assertTrue(backend.keys.isEmpty())
        assertTrue(store.readCatalog().tombstones.isEmpty())
    }

    @Test fun failedRemovalCommitNeverDeletesKey() = runBlocking {
        val migrated = migrateSource()
        data.failNextCommit = true
        assertTrue(runCatching { keys().remove(migrated) }.isFailure)
        assertEquals(migrated, store.readCatalog().active)
        assertEquals(setOf(migrated.signingKey?.alias), backend.keys.keys)
    }

    @Test fun sweepRemovesOnlyOwnedOrphansAndNeverTouchesLiveOrForeignAliases() = runBlocking {
        val migrated = migrateSource()
        identity.withPrivateKey { backend.keys["other.application.key"] = it }
        val orphan = KeystoreSigningKeyImporter.ALIAS_PREFIX + "f".repeat(32)
        identity.withPrivateKey { backend.keys[orphan] = it }
        keys().sweepOrphans()
        assertEquals(setOf(migrated.signingKey?.alias, "other.application.key"), backend.keys.keys)
        assertEquals(listOf(orphan), backend.deleted)
    }

    @Test fun unreadableCatalogPreventsOrphanSweep() = runBlocking {
        val migrated = migrateSource()
        val unreadable = object : CertificateReferenceCatalogStore by store {
            override suspend fun readCatalog(): StoredCertificateCatalog = throw IOException("Unreadable")
        }
        assertTrue(runCatching {
            keys(catalog = unreadable).sweepOrphans()
        }.isFailure)
        assertEquals(setOf(migrated.signingKey?.alias), backend.keys.keys)
        assertTrue(backend.deleted.isEmpty())
    }

    @Test fun staleMigrationAndForgetCannotOverwriteNewerEntry() = runBlocking {
        store.write(source())
        val stale = checkNotNull(store.readCatalog().active)
        store.write(source().copy(displayName = "changed.p12"))
        val latest = store.readCatalog().active
        assertTrue(runCatching { migrate(stale) }.isFailure)
        assertEquals(latest, store.readCatalog().active)
        assertTrue(backend.keys.isEmpty())
        val migrated = migrate(checkNotNull(latest))
        store.write(source().copy(displayName = "replacement.p12"))
        assertTrue(runCatching { keys().forgetSource(migrated) }.isFailure)
        assertEquals(setOf(migrated.signingKey?.alias), store.readCatalog().tombstones)
    }

    @Test fun unlockRequiresAuthorizationAndBindsFingerprintPlacementAndRevision() = runBlocking {
        val migrated = migrateSource()
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        assertTrue(!keys().unlock(migrated, session) { false })
        assertNull(session.identityForSigning())
        assertTrue(keys().unlock(migrated, session) { true })
        assertEquals(KeystorePlacement.STRONGBOX, session.identityForSigning()?.keystorePlacement)
        assertTrue(runCatching {
            keys().unlock(migrated, session) {
                store.clear()
                true
            }
        }.isFailure)
        assertNull(session.identityForSigning())
    }

    @Test fun wrongFingerprintCannotUnlock() = runBlocking {
        val migrated = migrateSource()
        val wrong = migrated.copy(
            revision = migrated.revision + 1,
            signingKey = migrated.signingKey?.copy(certificateSha256 = "0".repeat(64)),
        )
        store.replace(migrated, wrong)
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        assertTrue(runCatching { keys().unlock(wrong, session) { true } }.isFailure)
        assertNull(session.identityForSigning())
    }

    @Test fun versionOneKeyOnlyCodecRejectsForeignAliasesUnknownVersionsAndDuplicates() {
        val record = StoredSigningKeyReference(
            1, KeystoreSigningKeyImporter.ALIAS_PREFIX + "a".repeat(32), StoredKeystoreLevel.TEE,
            "a".repeat(64), "Test certificate",
        )
        val entry = StoredCertificateEntry(UUID.randomUUID().toString(), 1, null, record)
        val snapshot = StoredCertificateCatalog(listOf(entry), entry.id)
        assertEquals(snapshot, CertificateReferenceCatalogCodec.decode(CertificateReferenceCatalogCodec.encode(snapshot)))
        for (invalid in listOf(record.copy(version = 2), record.copy(alias = "foreign"),
            record.copy(certificateSha256 = "abc"), record.copy(displayName = "\nunsafe"))) {
            assertTrue(runCatching {
                CertificateReferenceCatalogCodec.encode(snapshot.copy(entries = listOf(entry.copy(signingKey = invalid))))
            }.isFailure)
        }
        assertTrue(runCatching {
            CertificateReferenceCatalogCodec.encode(snapshot.copy(tombstones = setOf(record.alias)))
        }.isFailure)
        assertTrue(runCatching {
            CertificateReferenceCatalogCodec.encode(snapshot.copy(entries = listOf(entry, entry.copy(id = UUID.randomUUID().toString()))))
        }.isFailure)
    }

    @Test fun unspecifiedHardwareCannotBePublishedAsTee() = runBlocking {
        store.write(source())
        val entry = checkNotNull(store.readCatalog().active)
        val unknown = FakeSigningKeyStoreBackend(
            strongBoxAvailable = false, defaultLevel = KeystoreSecurityLevel.SECURE_HARDWARE_UNSPECIFIED,
        )
        val result = runCatching {
            keys(keyBackend = unknown)
                .migrate(entry, TestCertificateFactory.password(), acknowledgeKeyLoss = true, allowTrustedEnvironment = true)
        }.getOrNull()
        assertEquals(SigningKeyImportResult.Failed(SigningKeyImportFailure.NOT_HARDWARE_BACKED), result)
        assertEquals(entry, store.readCatalog().active)
        assertTrue(unknown.keys.isEmpty())
    }

    @Test fun wrongSourcePasswordCannotPublishAndPasswordIsCleared() = runBlocking {
        store.write(source())
        val entry = checkNotNull(store.readCatalog().active)
        val password = "wrong".toCharArray()
        assertTrue(runCatching { keys().migrate(entry, password, true, false) }.isFailure)
        assertTrue(password.all { it == '\u0000' })
        assertTrue(backend.keys.isEmpty())
        assertEquals(entry, store.readCatalog().active)
    }

    @Test fun boundSourceWhoseChosenEntryIsGoneCannotImportTheFilesOtherKey() = runBlocking {
        store.write(source().copy(selectedCertificateSha256 = "0".repeat(64)))
        val entry = checkNotNull(store.readCatalog().active)
        assertTrue(runCatching { keys().migrate(entry, TestCertificateFactory.password(), true, false) }.isFailure)
        assertTrue(backend.keys.isEmpty())
        assertEquals(entry, store.readCatalog().active)
    }

    @Test fun ambiguousCommittedWriteNeverDeletesThePublishedKey() = runBlocking {
        store.write(source())
        val before = checkNotNull(store.readCatalog().active)
        val ambiguous = object : CertificateReferenceCatalogStore by store {
            override suspend fun replace(expected: StoredCertificateEntry, replacement: StoredCertificateEntry) {
                store.replace(expected, replacement)
                throw IOException("Failure reported after durable publication")
            }
        }
        assertTrue(runCatching {
            keys(catalog = ambiguous).migrate(before, TestCertificateFactory.password(), true, false)
        }.isFailure)
        val committed = checkNotNull(store.readCatalog().active)
        assertEquals(before.reference, committed.reference)
        assertEquals(setOf(committed.signingKey?.alias), backend.keys.keys)
        keys().sweepOrphans()
        assertEquals(setOf(committed.signingKey?.alias), backend.keys.keys)
    }

    @Test fun cancelledPublicationRollsBackNewAliasWithoutLosingSource() = runBlocking {
        store.write(source())
        val before = checkNotNull(store.readCatalog().active)
        val cancelled = object : CertificateReferenceCatalogStore by store {
            override suspend fun replace(expected: StoredCertificateEntry, replacement: StoredCertificateEntry) {
                throw CancellationException("Synthetic cancellation before commit")
            }
        }
        assertTrue(runCatching {
            keys(catalog = cancelled).migrate(before, TestCertificateFactory.password(), true, false)
        }.exceptionOrNull() is CancellationException)
        assertEquals(before, store.readCatalog().active)
        assertTrue(backend.keys.isEmpty())
    }

    @Test fun crashAfterAliasDeletionRetainsTombstoneUntilSuccessfulAcknowledgement() = runBlocking {
        val migrated = migrateSource()
        val interrupted = object : CertificateReferenceCatalogStore by store {
            override suspend fun acknowledgeKeyDeletion(alias: String) = throw IOException("Interrupted acknowledgement")
        }
        assertTrue(runCatching { keys(catalog = interrupted).remove(migrated) }.isFailure)
        assertTrue(backend.keys.isEmpty())
        assertEquals(setOf(migrated.signingKey?.alias), store.readCatalog().tombstones)
        keys().sweepOrphans()
        assertTrue(store.readCatalog().tombstones.isEmpty())
    }

    @Test fun explicitTeeConsentPublishesAndUnlocksDefaultPlacement() = runBlocking {
        store.write(source())
        val tee = FakeSigningKeyStoreBackend(strongBoxAvailable = false)
        val keys = keys(keyBackend = tee)
        val before = checkNotNull(store.readCatalog().active)
        assertEquals(SigningKeyImportResult.StrongBoxUnsuitable,
            keys.migrate(before, TestCertificateFactory.password(), true, false))
        assertEquals(before, store.readCatalog().active)
        keys.migrate(before, TestCertificateFactory.password(), true, true)
        val migrated = checkNotNull(store.readCatalog().active)
        assertEquals(StoredKeystoreLevel.TEE, migrated.signingKey?.level)
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        assertTrue(keys.unlock(migrated, session) { true })
        assertEquals(KeystorePlacement.DEFAULT, session.identityForSigning()?.keystorePlacement)
    }

    private fun keys(
        catalog: CertificateReferenceCatalogStore = store,
        keyBackend: SigningKeyStoreBackend = backend,
        enabled: Boolean = true,
    ) = KeystoreSigningKeyCatalog(
        catalog, keyBackend, documents,
        loader = Pkcs12Loader(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC)),
        enabled = enabled,
    )

    private suspend fun migrate(entry: StoredCertificateEntry): StoredCertificateEntry {
        keys().migrate(entry, TestCertificateFactory.password(), acknowledgeKeyLoss = true, allowTrustedEnvironment = false)
        return checkNotNull(store.readCatalog().active)
    }

    private suspend fun migrateSource(): StoredCertificateEntry {
        store.write(source())
        return migrate(checkNotNull(store.readCatalog().active))
    }

    private fun source() = StoredCertificateReference(
        Uri.parse("content://documents/test"), "test.p12", "application/x-pkcs12", null, identity.summary,
    )

    private class MemoryPreferences : DataStore<Preferences> {
        var failNextCommit = false
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also {
                if (failNextCommit) {
                    failNextCommit = false
                    throw IOException("Synthetic commit failure")
                }
                data.value = it
            }
    }
}
