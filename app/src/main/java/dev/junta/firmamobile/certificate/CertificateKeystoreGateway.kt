package dev.junta.firmamobile.certificate

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Key catalogue management is separate from file selection and password unlocking. */
interface CertificateKeystoreGateway {
    /** Selected metadata only: never returns a private key or unlocks the session. */
    suspend fun currentKeyEntry(): StoredCertificateEntry? = null

    /** Changes only the selected record; callers revoke the former unlock first. */
    suspend fun activateKey(entry: StoredCertificateEntry): Boolean = false

    /** Removes exactly the confirmed entry after caller-side unlock-cache revocation. */
    suspend fun forget(entry: StoredCertificateEntry): Unit = error("No keystore catalogue")

    suspend fun hasPendingKeyDeletion(): Boolean = false
}

internal class RepositoryKeystoreCertificates(
    private val store: CertificateReferenceCatalogStore?,
    private val keys: CertificateKeystoreCatalog?,
    private val ioDispatcher: CoroutineDispatcher,
) : CertificateKeystoreGateway {
    override suspend fun currentKeyEntry(): StoredCertificateEntry? = withContext(ioDispatcher) {
        store?.readCatalog()?.active?.takeIf { it.signingKey != null }
    }

    override suspend fun activateKey(entry: StoredCertificateEntry): Boolean = withContext(ioDispatcher) {
        keys != null && entry.signingKey != null && store?.selectKeyEntry(entry) == true
    }

    override suspend fun hasPendingKeyDeletion(): Boolean = withContext(ioDispatcher) {
        store?.readCatalog()?.tombstones?.isNotEmpty() == true
    }

    override suspend fun forget(entry: StoredCertificateEntry): Unit = withContext(ioDispatcher) {
        check(entry.signingKey != null)
        checkNotNull(keys) { "No keystore catalogue" }.remove(entry)
    }
}
