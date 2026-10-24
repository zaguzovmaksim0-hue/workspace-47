package dev.junta.firmamobile.certificate

import android.content.Context

/** Decorates only local persistence; portal protocols, validation and unlock policy are unchanged. */
internal class ReminderCertificateReferenceStore(
    context: Context,
    private val delegate: CertificateReferenceStore,
    private val reminders: CertificateExpiryReminders = CertificateExpiryReminders(context, delegate),
) : CertificateReferenceStore {
    override suspend fun read(): StoredCertificateReference? = delegate.read()

    override suspend fun write(reference: StoredCertificateReference) = reminders.changeReference {
        delegate.write(reference)
    }

    override suspend fun clear() = reminders.changeReference { delegate.clear() }
}
