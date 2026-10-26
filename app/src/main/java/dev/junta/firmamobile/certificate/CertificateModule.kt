package dev.junta.firmamobile.certificate

import android.app.Application
import android.os.SystemClock

internal class CertificateModule(
    monotonicNanos: () -> Long,
    unlockCacheFactory: () -> CertificateUnlockCache,
    gatewayFactory: () -> CertificateGateway,
    sessionFactory: (() -> Long) -> CertificateSession = {
        CertificateSession(monotonicNanos = it)
    },
) {
    val session: CertificateSession = sessionFactory(monotonicNanos)
    val unlockCache: CertificateUnlockCache = unlockCacheFactory()
    val gateway: CertificateGateway = gatewayFactory()

    companion object {
        fun create(
            application: Application,
            unlockAuthentication: AndroidCertificateUnlockAuthentication,
        ): CertificateModule {
            val applicationContext = application.applicationContext
            return CertificateModule(
                monotonicNanos = SystemClock::elapsedRealtimeNanos,
                unlockCacheFactory = {
                    AndroidKeystoreCertificateUnlockCache(applicationContext, authentication = unlockAuthentication)
                },
                gatewayFactory = {
                    val documents = ContentResolverCertificateDocumentAccess(applicationContext.contentResolver)
                    val references = ReminderCertificateReferenceStore(
                        applicationContext,
                        PreferencesCertificateReferenceStore(applicationContext.certificateReferenceDataStore),
                    )
                    CertificateRepository(
                        documentAccess = documents,
                        referenceStore = references,
                        loader = Pkcs12Loader(),
                        keyCatalog = KeystoreSigningKeyCatalog(
                            references, AndroidSigningKeyStoreBackend(applicationContext), documents, enabled = true,
                        ),
                    )
                },
            )
        }
    }
}
