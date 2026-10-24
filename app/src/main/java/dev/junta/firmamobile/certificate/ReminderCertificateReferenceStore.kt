package dev.junta.firmamobile.certificate

import android.content.Context
import android.net.Uri

/** Decorates only local persistence; portal protocols, validation and unlock policy are unchanged. Reminders
 * follow the active entry only, so every change of the active entry reconciles them. */
internal class ReminderCertificateReferenceStore(
    context: Context,
    private val delegate: CertificateReferenceCatalogStore,
    private val reminders: CertificateExpiryReminders = CertificateExpiryReminders(context, delegate),
) : CertificateReferenceCatalogStore {
    override suspend fun read(): StoredCertificateReference? = delegate.read()

    override suspend fun readCatalog(): StoredCertificateCatalog = delegate.readCatalog()

    // Appending never selects, so the active entry and its reminders are unchanged.
    override suspend fun add(reference: StoredCertificateReference) = delegate.add(reference)

    override suspend fun selectEntry(id: String, revision: Long): Boolean {
        var selected = false
        reminders.changeReference { selected = delegate.selectEntry(id, revision) }
        return selected
    }

    override suspend fun replace(expected: StoredCertificateEntry, replacement: StoredCertificateEntry) =
        reminders.changeReference { delegate.replace(expected, replacement) }

    override suspend fun remove(expected: StoredCertificateEntry) = reminders.changeReference {
        delegate.remove(expected)
    }

    // Tombstones only: no entry or selection changes.
    override suspend fun acknowledgeKeyDeletion(alias: String) = delegate.acknowledgeKeyDeletion(alias)

    override suspend fun write(reference: StoredCertificateReference) = reminders.changeReference {
        delegate.write(reference)
    }

    override suspend fun clear() = reminders.changeReference { delegate.clear() }

    override suspend fun references(uri: Uri): Boolean = delegate.references(uri)
}
