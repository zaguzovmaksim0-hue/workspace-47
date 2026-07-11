package dev.junta.firmamobile

import android.app.Application
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.ContentResolverCertificateDocumentAccess
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.certificate.PreferencesCertificateReferenceStore
import dev.junta.firmamobile.certificate.certificateReferenceDataStore

class JuntaFirmaApplication : Application() {
    lateinit var certificateGateway: CertificateGateway
        internal set

    lateinit var certificateSession: CertificateSession
        internal set

    override fun onCreate() {
        super.onCreate()
        certificateSession = CertificateSession()
        certificateGateway = CertificateRepository(
            documentAccess = ContentResolverCertificateDocumentAccess(contentResolver),
            referenceStore = PreferencesCertificateReferenceStore(certificateReferenceDataStore),
            loader = Pkcs12Loader(),
        )
    }
}
