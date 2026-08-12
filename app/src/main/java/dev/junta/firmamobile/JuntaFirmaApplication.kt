package dev.junta.firmamobile

import android.app.Application
import android.os.SystemClock
import android.util.Log
import dev.junta.firmamobile.browser.ClientCertPreferenceCoordinator
import dev.junta.firmamobile.certificate.AndroidKeystoreCertificateUnlockCache
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.ContentResolverCertificateDocumentAccess
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.certificate.PreferencesCertificateReferenceStore
import dev.junta.firmamobile.certificate.certificateReferenceDataStore
import dev.junta.firmamobile.network.BuildVariantSecureTunnelRuntimeFactory
import dev.junta.firmamobile.network.SecureTunnelRuntime
import dev.junta.firmamobile.security.ApplicationSanitizedLoggerFactory
import dev.junta.firmamobile.security.SanitizedLogSink
import dev.junta.firmamobile.security.SanitizedLogger

class JuntaFirmaApplication : Application() {
    lateinit var certificateGateway: CertificateGateway
        internal set

    lateinit var certificateSession: CertificateSession
        internal set

    lateinit var certificateUnlockCache: CertificateUnlockCache
        internal set

    lateinit var sanitizedLogger: SanitizedLogger
        internal set

    lateinit var clientCertPreferenceCoordinator: ClientCertPreferenceCoordinator
        internal set

    internal lateinit var secureTunnelRuntime: SecureTunnelRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        sanitizedLogger = ApplicationSanitizedLoggerFactory.create(
            filesDirectory = filesDir,
            qaEnabled = BuildConfig.ALLOW_QA_PROFILES,
            diagnosticMirror = SanitizedLogSink { record ->
                Log.i(QA_DIAGNOSTIC_TAG, record)
            },
        )
        secureTunnelRuntime = BuildVariantSecureTunnelRuntimeFactory.create(this)
        clientCertPreferenceCoordinator = ClientCertPreferenceCoordinator()
        certificateSession = CertificateSession(monotonicNanos = SystemClock::elapsedRealtimeNanos)
        certificateUnlockCache = AndroidKeystoreCertificateUnlockCache(this)
        certificateGateway = CertificateRepository(
            documentAccess = ContentResolverCertificateDocumentAccess(contentResolver),
            referenceStore = PreferencesCertificateReferenceStore(certificateReferenceDataStore),
            loader = Pkcs12Loader(),
        )
    }

    private companion object {
        const val QA_DIAGNOSTIC_TAG = "JFM_QA"
    }
}
