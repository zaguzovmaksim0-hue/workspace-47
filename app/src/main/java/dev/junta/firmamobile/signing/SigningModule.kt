package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.BatchBridgeSigningRoutes
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.SiteProfileRegistry
import kotlinx.coroutines.CoroutineScope

internal class SigningModule(
    private val certificateSession: CertificateSession,
    private val scope: CoroutineScope,
) {
    fun ordinary(
        directJuntaTransport: HttpsProfileHttpTransport,
        ofvirtualTransport: ProfileHttpTransport,
        currentOrigin: () -> TrustedOrigin?,
        currentNavigationEpoch: () -> Long,
        currentPageUrl: () -> String?,
    ): SigningCoordinator {
        val adapters = RuntimeSigningAdapters.ordinary(
            directJuntaTransport = directJuntaTransport,
            ofvirtualTransport = ofvirtualTransport,
        )
        return SigningCoordinator(
            certificateSession = certificateSession,
            adapter = checkNotNull(adapters.resolve(JuntaTriPhaseAdapter.ID)),
            localSignatureEngine = JcaLocalSignatureEngine(),
            currentOrigin = currentOrigin,
            currentNavigationEpoch = currentNavigationEpoch,
            currentPageUrl = currentPageUrl,
            expiryScheduler = CoroutineSigningExpiryScheduler(scope),
            adapterResolver = adapters::resolve,
        )
    }

    fun batch(
        currentOrigin: () -> TrustedOrigin?,
        currentNavigationEpoch: () -> Long,
        profileDisplayName: String,
        compatibilityStatus: CompatibilityStatus,
        profileRegistry: SiteProfileRegistry,
    ): BatchSigningCoordinator {
        val adapters = RuntimeSigningAdapters.batch()
        return BatchSigningCoordinator(
            certificateSession = certificateSession,
            adapter = checkNotNull(adapters.resolve(MelillaBatchProtocolAdapter.ID)),
            adapterResolver = adapters::resolve,
            localSignatureEngine = JcaLocalSignatureEngine(),
            currentOrigin = currentOrigin,
            currentNavigationEpoch = currentNavigationEpoch,
            expiryScheduler = CoroutineSigningExpiryScheduler(scope),
            profileDisplayName = profileDisplayName,
            compatibilityStatus = compatibilityStatus,
            profileRegistry = profileRegistry,
        )
    }

    /** Fresh per signing host, like the coordinators; [profileRegistry] binds every batch bridge profile. */
    fun batchBridgeRoutes(profileRegistry: SiteProfileRegistry) = BatchBridgeSigningRoutes(profileRegistry)
}
