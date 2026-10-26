package dev.junta.firmamobile

import android.app.Application
import android.content.Context
import dev.junta.firmamobile.catalog.AndroidRegionDetector
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalCatalogViewModel
import dev.junta.firmamobile.catalog.PreferencesCatalogPreferencesStore
import dev.junta.firmamobile.catalog.catalogPreferencesDataStore
import dev.junta.firmamobile.certificate.AndroidCertificateUnlockAuthentication
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateModule
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.signing.SigningModule
import dev.junta.firmamobile.ui.CertificateViewModel
import kotlinx.coroutines.CoroutineScope

/** Application-owned construction boundary; factories are invoked, not retained. */
internal class AppContainer(
    certificateModuleFactory: () -> CertificateModule,
) {
    val certificateModule: CertificateModule = certificateModuleFactory()

    /** The caller supplies its current session and retains ownership of its scope. */
    fun createSigningModule(
        certificateSession: CertificateSession,
        scope: CoroutineScope,
    ): SigningModule = SigningModule(certificateSession, scope)

    /** Callers pass the application's current, possibly test-injected, objects rather than module defaults. */
    fun certificateViewModelFactory(
        gateway: CertificateGateway,
        session: CertificateSession,
        unlockCache: CertificateUnlockCache,
    ) = CertificateViewModel.Factory(gateway, session, unlockCache)

    /** [context] only builds the stores; the repository stays the single loaded instance. */
    fun catalogViewModelFactory(
        context: Context,
        repository: PortalCatalogRepository,
    ) = PortalCatalogViewModel.Factory(
        repository = repository,
        preferencesStore = PreferencesCatalogPreferencesStore(context.applicationContext.catalogPreferencesDataStore),
        regionDetector = AndroidRegionDetector(context.applicationContext),
    )

    companion object {
        fun create(
            application: Application,
            unlockAuthentication: AndroidCertificateUnlockAuthentication,
        ): AppContainer =
            AppContainer { CertificateModule.create(application, unlockAuthentication) }
    }
}
