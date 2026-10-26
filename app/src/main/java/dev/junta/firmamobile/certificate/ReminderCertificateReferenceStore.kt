package dev.junta.firmamobile.certificate

import android.content.Context

/** Decorates only local persistence; portal protocols, validation and unlock policy are unchanged. Reminders
 * follow the active entry only, so every change of the active entry reconciles them. */
internal class ReminderCertificateReferenceStore(
    context: Context,
    private val delegate: CertificateReferenceCatalogStore,
    private val reminders: CertificateExpiryReminders = CertificateExpiryReminders(context, delegate),
) : CertificateReferenceCatalogStore by delegate {
    override suspend fun selectEntry(id: String, revision: Long): Boolean {
        var selected = false
        reminders.changeReference { selected = delegate.selectEntry(id, revision) }
        return selected
    }

    override suspend fun selectKeyEntry(expected: StoredCertificateEntry): Boolean {
        var selected = false
        reminders.changeReference { selected = delegate.selectKeyEntry(expected) }
        return selected
    }

    override suspend fun replace(expected: StoredCertificateEntry, replacement: StoredCertificateEntry) =
        reminders.changeReference { delegate.replace(expected, replacement) }

    override suspend fun remove(expected: StoredCertificateEntry, onlyIfInactive: Boolean) = reminders.changeReference {
        delegate.remove(expected, onlyIfInactive)
    }

    override suspend fun write(reference: StoredCertificateReference) = reminders.changeReference {
        delegate.write(reference)
    }

    override suspend fun clear() = reminders.changeReference { delegate.clear() }

}
