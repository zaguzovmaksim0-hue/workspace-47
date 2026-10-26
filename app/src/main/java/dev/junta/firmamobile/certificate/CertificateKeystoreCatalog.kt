package dev.junta.firmamobile.certificate

/** Catalogue management only: no import, private key, password or unlock capability. */
interface CertificateKeystoreCatalog {
    suspend fun remove(expected: StoredCertificateEntry, onlyIfInactive: Boolean = false)

    /** Completes previously confirmed deletions; live entries are never swept here. */
    suspend fun finishPendingDeletions()
}
