package dev.junta.firmamobile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.junta.firmamobile.browser.BrowserSessionStatePolicy
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalCatalogScreen
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PublicPortalCatalogParser
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.CoroutineSigningExpiryScheduler
import dev.junta.firmamobile.signing.JcaLocalSignatureEngine
import dev.junta.firmamobile.signing.JuntaTriPhaseAdapter
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import dev.junta.firmamobile.signing.LocalXadesDetachedAdapter
import dev.junta.firmamobile.signing.UnizarTriPhaseAdapter
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningCoordinator
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.smoke.CatalogSmokeController
import dev.junta.firmamobile.smoke.CatalogSmokeHook
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.BrowserScreen
import dev.junta.firmamobile.ui.CertificateUiState
import dev.junta.firmamobile.ui.CertificateViewModel
import dev.junta.firmamobile.ui.SensitiveWindowProtection
import dev.junta.firmamobile.ui.WindowSecureFlagPolicy
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
import java.net.URI
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var currentWebView: WebView? = null
    private var destination by mutableStateOf<AppDestination>(AppDestination.Certificate)
    private lateinit var signingCoordinator: SigningCoordinator
    private lateinit var catalogRepository: PortalCatalogRepository
    private lateinit var catalogSmokeHook: CatalogSmokeHook
    private var currentNavigationEpoch: Long = 0L
    private val signingJobs = SigningJobRegistry()

    private val certificateViewModel: CertificateViewModel by viewModels {
        val app = application as JuntaFirmaApplication
        CertificateViewModel.Factory(app.certificateGateway, app.certificateSession)
    }

    private val certificatePicker = registerForActivityResult(
        OpenableDocumentContract,
    ) { uri ->
        uri?.let(certificateViewModel::onCertificateSelected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        BrowserSessionStatePolicy.discardLegacyWebViewState(savedInstanceState)
        super.onCreate(savedInstanceState)
        val app = application as JuntaFirmaApplication
        val juntaAdapter = JuntaTriPhaseAdapter()
        val juntaOfvirtualAdapter = JuntaOfvirtualTriPhaseAdapter()
        val redsaraAdapter = LocalXadesDetachedAdapter()
        val unizarAdapter = UnizarTriPhaseAdapter()
        signingCoordinator = SigningCoordinator(
            certificateSession = app.certificateSession,
            adapter = juntaAdapter,
            localSignatureEngine = JcaLocalSignatureEngine(),
            currentOrigin = originProvider@{
                val selectedProfileId = (destination as? AppDestination.Browser)?.profileId
                    ?: return@originProvider null
                currentWebView?.url?.let { url ->
                    runCatching {
                        JuntaOriginPolicy.signingOriginFor(
                            Uri.parse(url),
                            selectedProfileId,
                        )
                    }.getOrNull()
                }
            },
            currentNavigationEpoch = { currentNavigationEpoch },
            expiryScheduler = CoroutineSigningExpiryScheduler(lifecycleScope),
            adapterResolver = { id ->
                when (id) {
                    juntaAdapter.id -> juntaAdapter
                    juntaOfvirtualAdapter.id -> juntaOfvirtualAdapter
                    redsaraAdapter.id -> redsaraAdapter
                    unizarAdapter.id -> unizarAdapter
                    else -> null
                }
            },
        )
        val publicCatalog = resources.openRawResource(R.raw.public_portal_catalog_v1)
            .bufferedReader().use { PublicPortalCatalogParser.parse(it.readText()) }
        catalogRepository = PortalCatalogRepository(
            registry = BuiltInSiteProfiles.runtimeRegistry,
            profileCatalog = BuiltInSiteProfiles.catalog,
            publicCatalog = publicCatalog,
        )
        catalogSmokeHook = CatalogSmokeHook(
            activity = this,
            execute = CatalogSmokeController(
                repository = catalogRepository,
                certificateUnlocked = {
                    certificateViewModel.state.value is CertificateUiState.Unlocked
                },
                openProfile = { launch ->
                    cancelSigning(SigningCancelReason.NAVIGATION)
                    currentWebView = null
                    destination = AppDestination.Browser(
                        profileId = launch.profileId,
                        entryUrl = launch.entryUrl,
                    )
                },
                activeWebViewMatches = { profileId -> activeWebViewMatches(profileId) },
                adapterIdForProfile = { profileId -> smokeAdapterId(profileId) },
            )::execute,
        )
        val paperSystemBar = getColor(R.color.jfm_paper)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                scrim = paperSystemBar,
                darkScrim = paperSystemBar,
            ),
        )
        setContent {
            val certificateState = certificateViewModel.state.collectAsStateWithLifecycle()
            val signingState = signingCoordinator.state.collectAsStateWithLifecycle()
            val updateSecureWindow = remember(window) {
                { sensitive: Boolean ->
                    WindowSecureFlagPolicy.apply(window, sensitive)
                }
            }
            SensitiveWindowProtection(
                enabled = certificateState.value is CertificateUiState.Locked,
                updateSecure = updateSecureWindow,
            )
            LaunchedEffect(certificateState.value) {
                if (certificateState.value !is CertificateUiState.Unlocked) {
                    destination = AppDestination.Certificate
                }
            }
            JuntaFirmaTheme {
                val unlocked = certificateState.value as? CertificateUiState.Unlocked
                val recentPortals = remember { mutableStateListOf<PortalId>() }
                val favoritePortals = remember { mutableStateListOf<PortalId>() }
                val browserDestination = destination as? AppDestination.Browser
                if (browserDestination != null && unlocked != null) {
                    val app = application as JuntaFirmaApplication
                    key(
                        browserDestination.profileId.value,
                        browserDestination.entryUrl.toASCIIString(),
                    ) {
                        BrowserScreen(
                        profileId = browserDestination.profileId,
                        entryUrl = browserDestination.entryUrl,
                        certificateState = unlocked,
                        logger = app.sanitizedLogger,
                        signingState = signingState.value,
                        onMiniAppletRequest = ::prepareMiniAppletSigning,
                        onMiniAppletCancel = { requestId ->
                            cancelSigning(SigningCancelReason.JAVASCRIPT, requestId)
                        },
                        onConfirmSigning = ::confirmSigning,
                        onCancelSigning = ::cancelSigning,
                        onDismissSigningState = signingCoordinator::dismissTerminalState,
                        onExitBrowser = {
                            cancelSigning(SigningCancelReason.NAVIGATION)
                            destination = AppDestination.Catalog
                        },
                        onOpenExternal = { uri ->
                            cancelSigning(SigningCancelReason.NAVIGATION)
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (_: ActivityNotFoundException) {
                                // The validated URL stays closed if no browser can handle it.
                            }
                        },
                        onChangeCertificate = {
                            cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                            destination = AppDestination.Certificate
                            launchCertificatePicker()
                        },
                        onLockCertificate = {
                            cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                            destination = AppDestination.Certificate
                            certificateViewModel.lock()
                        },
                        onClearSession = {
                            cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                            destination = AppDestination.Certificate
                            certificateViewModel.lock()
                        },
                        clientCertificateIdentityProvider = {
                            app.certificateSession.identityForSigning()
                        },
                        onWebViewChanged = { currentWebView = it },
                            onNavigationEpochChanged = { currentNavigationEpoch = it },
                        )
                    }
                } else if (destination == AppDestination.Catalog && unlocked != null) {
                    PortalCatalogScreen(
                        repository = catalogRepository,
                        favoritePortalIds = favoritePortals.toSet(),
                        recentPortalIds = recentPortals,
                        onToggleFavorite = { portalId ->
                            if (!favoritePortals.remove(portalId)) {
                                favoritePortals.add(portalId)
                            }
                        },
                        onOpenPortal = { item ->
                            val launch = catalogRepository.resolveLaunch(item)
                            if (launch != null) {
                                cancelSigning(SigningCancelReason.NAVIGATION)
                                currentWebView = null
                                recentPortals.remove(item.portalId)
                                recentPortals.add(0, item.portalId)
                                while (recentPortals.size > MAX_RECENT_PROFILES) {
                                    recentPortals.removeAt(recentPortals.lastIndex)
                                }
                                destination = AppDestination.Browser(
                                    profileId = launch.profileId,
                                    entryUrl = launch.entryUrl,
                                )
                            }
                        },
                    )
                } else {
                    AppRoot(
                        state = certificateState.value,
                        onSelectCertificate = ::launchCertificatePicker,
                        onUnlock = certificateViewModel::unlock,
                        onLock = certificateViewModel::lock,
                        onForget = certificateViewModel::forget,
                        onContinue = { destination = AppDestination.Catalog },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        catalogSmokeHook.start()
    }

    override fun onStop() {
        catalogSmokeHook.stop()
        cancelSigning(SigningCancelReason.BACKGROUND)
        certificateViewModel.onAppBackgrounded()
        super.onStop()
    }

    override fun onLowMemory() {
        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
        certificateViewModel.onMemoryPressure()
        super.onLowMemory()
    }

    private fun launchCertificatePicker() {
        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
        certificateViewModel.prepareForCertificateSelection()
        certificatePicker.launch(PKCS12_MIME_TYPES)
    }

    override fun onDestroy() {
        catalogSmokeHook.stop()
        cancelSigning(SigningCancelReason.BACKGROUND)
        signingCoordinator.close()
        super.onDestroy()
    }

    private fun prepareMiniAppletSigning(
        request: MiniAppletBridgeRequest,
        reply: SigningReplySink,
    ) {
        signingCoordinator.prepare(request.normalized, reply)
    }

    private fun confirmSigning(requestId: UUID) {
        val awaiting = signingCoordinator.state.value as? SigningUiState.AwaitingConfirmation
        if (awaiting?.requestId != requestId) return
        val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            signingCoordinator.confirm(requestId)
        }
        if (signingJobs.register(requestId, job)) {
            job.start()
        } else {
            job.cancel()
        }
    }

    private fun cancelSigning(
        reason: SigningCancelReason,
        requestId: UUID? = null,
    ) {
        val accepted = signingCoordinator.cancel(reason, requestId)
        signingJobs.takeForCancellation(requestId, accepted)?.cancel()
    }

    private fun activeWebViewMatches(profileId: ProfileId): Boolean {
        val browser = destination as? AppDestination.Browser ?: return false
        if (browser.profileId != profileId) return false
        val currentUrl = currentWebView?.url ?: return false
        val uri = runCatching { URI(currentUrl) }.getOrNull() ?: return false
        return BuiltInSiteProfiles.runtimeRegistry.resolve(uri)?.profile?.profileId == profileId
    }

    private fun smokeAdapterId(profileId: ProfileId): String? {
        val profile = BuiltInSiteProfiles.runtimeRegistry.profile(profileId) ?: return null
        return when {
            Capability.SIGN in profile.capabilities -> BuiltInProtocolAdapterRegistry.registry
                .resolve(profileId, ProtocolOperation.SIGN)
                ?.signingProtocolId
                ?.value
            Capability.CLIENT_TLS_AUTH in profile.capabilities -> "client-tls-auth"
            else -> "browse-only"
        }
    }

    companion object {
        private const val MAX_RECENT_PROFILES = 8
        private object OpenableDocumentContract : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent =
                super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
        }

        private val PKCS12_MIME_TYPES = arrayOf(
            CertificateRepository.MIME_X_PKCS12,
            CertificateRepository.MIME_PKCS12,
            CertificateRepository.MIME_OCTET_STREAM,
        )
    }
}

private sealed interface AppDestination {
    data object Certificate : AppDestination

    data object Catalog : AppDestination

    data class Browser(
        val profileId: ProfileId,
        val entryUrl: URI,
    ) : AppDestination
}
