package dev.junta.firmamobile.certificate

import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** File entries of the catalogue, beside [CertificateRepository] and [KeystoreSigningKeyCatalog]. Selecting an
 * entry stays behind the view model's revocation gate, key-backed entries belong to the keystore catalogue and
 * the active entry leaves only through forget(). Not wired to the UI yet. */
internal class CertificateFileCatalog(
    private val documentAccess: CertificateDocumentAccess,
    private val store: CertificateReferenceCatalogStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Saves [uri] as an inactive entry: nothing is selected, unlocked or scheduled. */
    suspend fun add(uri: Uri): CertificateSelectionResult = withContext(ioDispatcher) {
        val inspected = documentAccess.inspectCertificate(uri)
        if (inspected !is CertificateSelectionResult.Success) return@withContext inspected
        val failure = documentAccess.grantAndPersist(uri, store) { store.add(inspected.reference) }
        if (failure == null) inspected else CertificateSelectionResult.Failure(failure)
    }

    /** Removes an inactive file entry, then releases its grant unless another entry still needs it. */
    suspend fun remove(expected: StoredCertificateEntry) {
        check(expected.signingKey == null) { "Key-backed entries belong to the keystore catalogue" }
        // The grant is released only after the removal is durable.
        withContext(ioDispatcher + NonCancellable) {
            store.remove(expected, onlyIfInactive = true)
            expected.reference?.let { documentAccess.releaseUnlessReferenced(it.uri, store) }
        }
    }
}
