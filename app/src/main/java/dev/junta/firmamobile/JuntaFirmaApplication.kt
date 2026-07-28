package dev.junta.firmamobile

import android.app.Application
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.ContentResolverCertificateDocumentAccess
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.certificate.PreferencesCertificateReferenceStore
import dev.junta.firmamobile.certificate.certificateReferenceDataStore
import dev.junta.firmamobile.network.BuildVariantSecureTunnelRuntimeFactory
import dev.junta.firmamobile.network.SecureTunnelRuntime
import dev.junta.firmamobile.security.SanitizedLogger

class JuntaFirmaApplication : Application() {
    lateinit var certificateGateway: CertificateGateway
        internal set

    lateinit var certificateSession: CertificateSession
        internal set

    lateinit var sanitizedLogger: SanitizedLogger
        internal set

    internal lateinit var secureTunnelRuntime: SecureTunnelRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        sanitizedLogger = SanitizedLogger()
        secureTunnelRuntime = BuildVariantSecureTunnelRuntimeFactory.create(this)
        certificateSession = CertificateSession()
        certificateGateway = CertificateRepository(
            documentAccess = ContentResolverCertificateDocumentAccess(contentResolver),
            referenceStore = PreferencesCertificateReferenceStore(certificateReferenceDataStore),
            loader = Pkcs12Loader(),
        )
    }
}
