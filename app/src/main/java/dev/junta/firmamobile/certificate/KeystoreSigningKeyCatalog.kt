package dev.junta.firmamobile.certificate

import java.security.MessageDigest
import java.security.cert.X509Certificate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Only confirmed placements are published; UNKNOWN_SECURE cannot be labelled TEE or StrongBox. */
enum class StoredKeystoreLevel { STRONGBOX, TEE }

data class StoredSigningKeyReference(
    val version: Int,
    val alias: String,
    val level: StoredKeystoreLevel,
    val certificateSha256: String,
    val displayName: String,
) {
    internal fun validate() {
        require(version == 1 && isOwnedAlias(alias))
        require(certificateSha256.matches(Regex("[0-9a-f]{64}")))
        require(displayName.isNotEmpty() && displayName == CertificateDisplayNamePolicy.sanitize(displayName))
    }

    internal val placement: KeystorePlacement
        get() = if (level == StoredKeystoreLevel.STRONGBOX) KeystorePlacement.STRONGBOX else KeystorePlacement.DEFAULT

    companion object {
        internal fun isOwnedAlias(alias: String): Boolean =
            alias.startsWith(KeystoreSigningKeyImporter.ALIAS_PREFIX) &&
                alias.removePrefix(KeystoreSigningKeyImporter.ALIAS_PREFIX).matches(Regex("[0-9a-f]{32}"))
    }
}

/** Storage slice only. Runtime/UI and TLS stay disconnected until physical acceptance (ADR 0005). */
internal class KeystoreSigningKeyCatalog(
    private val store: CertificateReferenceCatalogStore,
    private val backend: SigningKeyStoreBackend,
    private val documentAccess: CertificateDocumentAccess,
    private val loader: Pkcs12Loader = Pkcs12Loader(),
    private val enabled: Boolean = false,
) {
    suspend fun migrate(
        expected: StoredCertificateEntry,
        password: CharArray,
        acknowledgeKeyLoss: Boolean,
        allowTrustedEnvironment: Boolean,
    ): SigningKeyImportResult {
        try {
            return mutex.withLock {
                check(enabled && acknowledgeKeyLoss && expected.signingKey == null)
                checkCurrent(expected)
                val source = checkNotNull(expected.reference)
                val loaded = documentAccess.loadReferenced(loader, source, password)
                check(loaded is CertificateLoadResult.Success) { "Source certificate unlock failed" }
                importAndPublish(expected, loaded.identity, allowTrustedEnvironment)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun importAndPublish(
        expected: StoredCertificateEntry,
        identity: UnlockedIdentity,
        allowTrustedEnvironment: Boolean,
    ): SigningKeyImportResult {
        // Cancellation cannot split a successful DataStore commit from alias ownership.
        return withContext(NonCancellable) {
            val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment)
            if (result !is SigningKeyImportResult.Imported) return@withContext result
            runCatching {
                val level = when (result.securityLevel) {
                    KeystoreSecurityLevel.STRONGBOX -> StoredKeystoreLevel.STRONGBOX
                    KeystoreSecurityLevel.TRUSTED_ENVIRONMENT -> StoredKeystoreLevel.TEE
                    else -> {
                        backend.delete(result.alias)
                        return@withContext SigningKeyImportResult.Failed(SigningKeyImportFailure.NOT_HARDWARE_BACKED)
                    }
                }
                val key = StoredSigningKeyReference(
                    1, result.alias, level, fingerprint(identity.certificate),
                    checkNotNull(expected.reference).displayName,
                )
                store.replace(expected, expected.copy(
                    revision = Math.addExact(expected.revision, 1), signingKey = key,
                ))
            }.onFailure {
                // If persistence outcome is unreadable, leave the alias for a later safe sweep.
                val published = runCatching {
                    store.readCatalog().entries.any { it.signingKey?.alias == result.alias }
                }.getOrDefault(true)
                if (!published) backend.delete(result.alias)
            }.getOrThrow()
            result
        }
    }

    /** A separate, optional commit: forget the app's reference, never delete the user's backup file. */
    suspend fun forgetSource(expected: StoredCertificateEntry) = mutex.withLock {
        check(enabled && expected.signingKey != null && expected.reference != null)
        store.replace(expected, expected.copy(revision = Math.addExact(expected.revision, 1), reference = null))
    }

    suspend fun remove(expected: StoredCertificateEntry) = mutex.withLock {
        check(enabled)
        withContext(NonCancellable) {
            store.remove(expected) // Tombstone is durable before touching AndroidKeyStore.
            expected.signingKey?.let { deleteTombstone(it.alias) }
        }
    }

    suspend fun sweepOrphans() = mutex.withLock {
        check(enabled)
        withContext(NonCancellable) {
            val catalog = store.readCatalog() // Corrupt/unreadable catalogue means no deletions.
            val live = catalog.entries.mapNotNull { it.signingKey?.alias }.toSet()
            val orphaned = backend.aliases().filter {
                it.startsWith(KeystoreSigningKeyImporter.ALIAS_PREFIX) && it !in live
            }
            for (alias in (orphaned + catalog.tombstones).toSet()) deleteTombstone(alias)
        }
    }

    /** Called only by the explicit app-level authentication flow; never restores a password cache. */
    suspend fun unlock(
        expected: StoredCertificateEntry,
        session: CertificateSession,
        authenticate: suspend () -> Boolean,
    ): Boolean = mutex.withLock {
        check(enabled)
        session.lock()
        checkCurrent(expected)
        if (!authenticate()) return@withLock false
        checkCurrent(expected)
        val record = checkNotNull(expected.signingKey)
        record.validate()
        val chain = backend.certificateChain(record.alias)
        check(chain.size in 1..MAX_CHAIN_LENGTH)
        val certificate = chain.first()
        check(fingerprint(certificate) == record.certificateSha256)
        val verified = KeystoreSigningKeyImporter(backend).verify(record.alias, certificate, record.placement)
        val expectedLevel = if (record.level == StoredKeystoreLevel.STRONGBOX) {
            KeystoreSecurityLevel.STRONGBOX
        } else KeystoreSecurityLevel.TRUSTED_ENVIRONMENT
        check(verified == SigningKeyImportResult.Imported(record.alias, expectedLevel))
        session.unlock(UnlockedIdentity(
            checkNotNull(backend.privateKey(record.alias)), certificate, chain, certificateSummary(certificate),
            keystorePlacement = record.placement,
        ))
        true
    }

    private suspend fun checkCurrent(expected: StoredCertificateEntry) {
        check(store.readCatalog().entries.singleOrNull { it.id == expected.id } == expected) {
            "Stale certificate entry"
        }
    }

    private suspend fun deleteTombstone(alias: String) {
        check(alias.startsWith(KeystoreSigningKeyImporter.ALIAS_PREFIX))
        backend.delete(alias)
        store.acknowledgeKeyDeletion(alias)
    }

    private fun fingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    private companion object {
        // Shared across instances: a sweep must never delete an import waiting for its catalogue commit.
        val mutex = Mutex()
        const val MAX_CHAIN_LENGTH = 16
    }
}
