package dev.junta.firmamobile

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dev.junta.firmamobile.browser.ClientCertPreferenceCoordinator
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.network.BuildVariantSecureTunnelRuntimeFactory
import dev.junta.firmamobile.network.SecureTunnelRuntime
import dev.junta.firmamobile.profile.ProfileScopePreferences
import dev.junta.firmamobile.profile.RuntimeProfileScope
import dev.junta.firmamobile.security.ApplicationSanitizedLoggerFactory
import dev.junta.firmamobile.security.SanitizedLogSink
import dev.junta.firmamobile.security.SanitizedLogger

class JuntaFirmaApplication : Application() {
    private val catalogLoader = dev.junta.firmamobile.catalog.CatalogRepositoryLoader {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val publicCatalog = resources.openRawResource(R.raw.public_portal_catalog_v1)
                .bufferedReader().use {
                    dev.junta.firmamobile.catalog.PublicPortalCatalogParser.parse(it.readText())
                }
            dev.junta.firmamobile.catalog.PortalCatalogRepository(
                registry = dev.junta.firmamobile.profile.BuiltInSiteProfiles.runtimeRegistry,
                profileCatalog = dev.junta.firmamobile.profile.BuiltInSiteProfiles.catalog,
                publicCatalog = publicCatalog,
            ).also { it.portals() } // Warm the search index off the UI thread as well.
        }
    }

    internal suspend fun loadCatalogRepository() = catalogLoader.get()

    lateinit var certificateGateway: CertificateGateway
        internal set

    lateinit var certificateSession: CertificateSession
        internal set

    val certificateUnlockAuthentication = dev.junta.firmamobile.certificate.AndroidCertificateUnlockAuthentication()

    lateinit var certificateUnlockCache: CertificateUnlockCache
        internal set

    lateinit var sanitizedLogger: SanitizedLogger
        internal set

    lateinit var clientCertPreferenceCoordinator: ClientCertPreferenceCoordinator
        internal set

    internal lateinit var appContainer: AppContainer
        private set

    internal lateinit var secureTunnelRuntime: SecureTunnelRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        RuntimeProfileScope.configure(ProfileScopePreferences(this).verifiedOnly())
        PDFBoxResourceLoader.init(this)
        sanitizedLogger = ApplicationSanitizedLoggerFactory.create(
            filesDirectory = filesDir,
            qaEnabled = BuildConfig.DEBUG && BuildConfig.ALLOW_QA_PROFILES,
            diagnosticMirror = SanitizedLogSink { record ->
                Log.i(QA_DIAGNOSTIC_TAG, record)
            },
        )
        secureTunnelRuntime = BuildVariantSecureTunnelRuntimeFactory.create(this)
        clientCertPreferenceCoordinator = ClientCertPreferenceCoordinator()
        appContainer = AppContainer.create(this, certificateUnlockAuthentication)
        val certificateModule = appContainer.certificateModule
        certificateSession = certificateModule.session
        certificateUnlockCache = certificateModule.unlockCache
        certificateGateway = certificateModule.gateway
    }

    private companion object {
        const val QA_DIAGNOSTIC_TAG = "JFM_QA"
    }
}
