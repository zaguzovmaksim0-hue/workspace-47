package dev.junta.firmamobile

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.junta.firmamobile.browser.BrowserSessionStatePolicy
import dev.junta.firmamobile.browser.MelillaBatchBridgeRequest
import dev.junta.firmamobile.browser.MelillaBatchReplyChannel
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.browser.download.DocumentDownloadActivity
import dev.junta.firmamobile.catalog.PortalCatalogViewModel
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalCatalogScreen
import dev.junta.firmamobile.catalog.PortalOpenTarget
import dev.junta.firmamobile.catalog.PublicPortalCatalogParser
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.network.TunnelRouteEvent
import dev.junta.firmamobile.network.TunnelRouteObserver
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.smoke.CatalogSmokeController
import dev.junta.firmamobile.smoke.CatalogSmokeHook
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.CertificateEntryChoiceActions
import dev.junta.firmamobile.ui.LocalCertificateEntryChoice
import dev.junta.firmamobile.ui.LocalSavedCertificates
import dev.junta.firmamobile.ui.SavedCertificatesActions
import dev.junta.firmamobile.ui.BrowserScreen
import dev.junta.firmamobile.ui.CertificateUiState
import dev.junta.firmamobile.ui.CertificateViewModel
import dev.junta.firmamobile.ui.SensitiveWindowProtection
import dev.junta.firmamobile.ui.SensitiveWindowStatePolicy
import dev.junta.firmamobile.ui.WindowSecureFlagPolicy
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private class PendingExternalApp(
        val token: UUID,
        val sourceHost: String,
        val target: dev.junta.firmamobile.browser.ExternalAppLink,
    )
    private var pendingExternalApp by mutableStateOf<PendingExternalApp?>(null)
    private val externalAppConsent by lazy {
        dev.junta.firmamobile.browser.ExternalAppConsentLease<WebView>(
            isCurrent = { view, epoch -> view === currentWebView && epoch == currentNavigationEpoch },
            canRespond = { lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                !certificatePanelVisible && currentWebView?.isShown == true && !isFinishing && !isDestroyed },
        )
    }

    private var pendingExternalHandoff by mutableStateOf<dev.junta.firmamobile.browser.ExternalHandoff.Request?>(null)
    private var externalHandoffFailed by mutableStateOf(false)
    private val browserExternalReturn = dev.junta.firmamobile.browser.BrowserExternalReturnLease<WebView>()
    private var browserFileReturnToken: UUID? = null
    private var documentDownloadReturnToken: UUID? = null
    private val documentDownloadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        documentDownloadReturnToken?.let(browserExternalReturn::finish)
        documentDownloadReturnToken = null
    }
    private var certificateReturnToken: UUID? = null
    private val browserFilePicker: androidx.activity.result.ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        browserFileReturnToken?.let(browserExternalReturn::finish)
        browserFileReturnToken = null
        browserFileChooser.deliver(result.resultCode, result.data)
    }
    private val browserFileChooser: dev.junta.firmamobile.browser.BrowserFileChooser by lazy {
        dev.junta.firmamobile.browser.BrowserFileChooser(
            navigationEpoch = { currentNavigationEpoch },
            isCurrentView = { it === currentWebView },
            canRead = { uri ->
                // Only a picker-granted content URI, not app-private files or a
                // forged file:// path, is returned to a website.
                checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
            },
            launch = { intent -> withBrowserExternalReturn(onToken = { browserFileReturnToken = it }) { browserFilePicker.launch(intent) } },
        )
    }

    private var currentWebView: WebView? = null
    private val navigation = AppNavigationState()
    private val destination get() = navigation.destination
    private val certificatePanelVisible get() = navigation.certificatePanelVisible
    private var signing: SigningOrchestrator? = null
    private var externalAfirma: dev.junta.firmamobile.afirma.servlet.ExternalAfirmaController? = null
    private var externalAfirmaPrompt by mutableStateOf<dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt?>(null)
    /** Activity-owned operations that survive onStop; an external afirma delivery is refused while any holds. */
    private val activityPortalBusy: () -> Boolean = {
        signing?.hasOwner() == true || pendingExternalApp != null || pendingExternalHandoff != null ||
            certificatePanelVisible || certificateReturnToken != null || documentDownloadReturnToken != null ||
            browserFileReturnToken != null
    }
    private var startupJob: kotlinx.coroutines.Job? = null
    private lateinit var catalogRepository: PortalCatalogRepository
    private lateinit var catalogViewModel: PortalCatalogViewModel
    private lateinit var catalogSmokeHook: CatalogSmokeHook
    private var currentNavigationEpoch: Long = 0L

    private val certificateViewModel: CertificateViewModel by viewModels {
        val app = application as JuntaFirmaApplication
        app.appContainer.certificateViewModelFactory(
            app.certificateGateway,
            app.certificateSession,
            app.certificateUnlockCache,
        )
    }

    private val certificatePicker = registerForActivityResult(
        OpenableDocumentContract,
    ) { uri ->
        certificateReturnToken?.let(browserExternalReturn::finish)
        certificateReturnToken = null
        uri?.let(certificateViewModel::onCertificateSelected)
    }

    private val savedCertificatePicker = registerForActivityResult(
        OpenableDocumentContract,
    ) { uri ->
        certificateReturnToken?.let(browserExternalReturn::finish)
        certificateReturnToken = null
        uri?.let(certificateViewModel::addSavedCertificate)
    }

    private val coarseLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (::catalogViewModel.isInitialized) {
            if (granted) catalogViewModel.detectRegion()
            else catalogViewModel.onLocationPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        BrowserSessionStatePolicy.discardLegacyWebViewState(savedInstanceState)
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        (application as JuntaFirmaApplication).signingAdmission.registerPortal(this, activityPortalBusy)
        beginCatalogStartup()
    }

    private fun beginCatalogStartup() {
        if (startupJob?.isActive == true) return
        showCatalogStartup(failed = false)
        startupJob = lifecycleScope.launch {
            val repository = try {
                (application as JuntaFirmaApplication).loadCatalogRepository()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showCatalogStartup(failed = true)
                return@launch
            }
            if (isFinishing || isDestroyed) return@launch
            initializeReadyContent(repository)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                catalogSmokeHook.start()
            }
        }
    }

    private fun showCatalogStartup(failed: Boolean) {
        setContent {
            JuntaFirmaTheme {
                androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        if (failed) {
                            androidx.compose.material3.Text(getString(R.string.catalog_open_failed))
                            androidx.compose.material3.TextButton(onClick = ::beginCatalogStartup) {
                                androidx.compose.material3.Text(getString(R.string.browser_retry))
                            }
                        } else {
                            androidx.compose.material3.CircularProgressIndicator()
                            androidx.compose.material3.Text(getString(R.string.catalog_loading))
                        }
                    }
                }
            }
        }
    }

    private fun initializeReadyContent(repository: PortalCatalogRepository) {
        val app = application as JuntaFirmaApplication
        val signingModule = app.appContainer.createSigningModule(
            certificateSession = app.certificateSession,
            scope = lifecycleScope,
        )
        val signingCoordinator = signingModule.ordinary(
            tunnelRuntime = app.secureTunnelRuntime,
            routeObserver = TunnelRouteObserver(::onTunnelRouteEvent),
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
            currentPageUrl = { currentWebView?.url },
        )
        val melillaProfile = checkNotNull(
            BuiltInSiteProfiles.catalog.profiles.singleOrNull {
                it.profileId == ProfileId("melilla-sede")
            },
        )
        val batchBridgeRoutes = signingModule.batchBridgeRoutes(BuiltInSiteProfiles.runtimeRegistry)
        val batchSigningCoordinator = signingModule.batch(
            currentOrigin = { currentSigningOrigin() },
            currentNavigationEpoch = { currentNavigationEpoch },
            profileDisplayName = melillaProfile.displayName,
            compatibilityStatus = melillaProfile.compatibilityStatus,
            profileRegistry = BuiltInSiteProfiles.runtimeRegistry,
        )
        val signing = SigningOrchestrator(
            signingCoordinator = signingCoordinator,
            batchSigningCoordinator = batchSigningCoordinator,
            batchBridgeRoutes = batchBridgeRoutes,
            scope = lifecycleScope,
            externalAfirmaActive = ::externalAfirmaActive,
            recordMiniAppletStage = ::recordMiniAppletBridgeDiagnostic,
        )
        this.signing = signing
        catalogRepository = repository
        catalogViewModel = ViewModelProvider(
            this,
            app.appContainer.catalogViewModelFactory(applicationContext, catalogRepository),
        )[PortalCatalogViewModel::class.java]
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
                    navigation.openBrowser(
                        profileId = launch.profileId,
                        entryUrl = launch.entryUrl,
                    )
                },
                activeWebViewMatches = { profileId -> activeWebViewMatches(profileId) },
                adapterIdForProfile = { profileId -> smokeAdapterId(profileId) },
            )::execute,
        )
        externalAfirma = dev.junta.firmamobile.afirma.servlet.ExternalAfirmaController(
            scope = lifecycleScope,
            canRespond = { lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) },
            identityProvider = { app.certificateSession.identityForSigning() },
            prepare = { dev.junta.firmamobile.afirma.servlet.nativeOperation(it) },
            onPrompt = {
                externalAfirmaPrompt = it
                if (it == null) app.signingAdmission.releaseExternal(this)
            },
        )
        window.decorView.post(::drainExternalAfirma)
        val paperSystemBar = getColor(R.color.jfm_paper)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                scrim = paperSystemBar,
                darkScrim = paperSystemBar,
            ),
        )
        setContent {
            val certificateState = certificateViewModel.state.collectAsStateWithLifecycle()
            val catalogState = catalogViewModel.state.collectAsStateWithLifecycle()
            val ordinarySigningState = signing.ordinaryState.collectAsStateWithLifecycle()
            val batchSigningState = signing.batchState.collectAsStateWithLifecycle()
            // Read both observable states unconditionally. Ownership is an imperative gate rather than
            // Compose state; conditional reads would leave the initial Idle composition unsubscribed
            // from the coordinator that later acquires ownership and enters AwaitingConfirmation.
            val ordinarySigningStateValue = ordinarySigningState.value
            val batchSigningStateValue = batchSigningState.value
            val signingState = signing.visibleState(ordinarySigningStateValue, batchSigningStateValue)
            val updateSecureWindow = remember(window) {
                { sensitive: Boolean ->
                    WindowSecureFlagPolicy.apply(window, sensitive)
                }
            }
            SensitiveWindowProtection(
                enabled = SensitiveWindowStatePolicy.requiresSecureWindow(
                    certificateState = certificateState.value,
                    signingState = signingState,
                ),
                updateSecure = updateSecureWindow,
            )
            val savedCertificates = certificateViewModel.savedCertificates.collectAsStateWithLifecycle().value
            val savedCertificateActions = SavedCertificatesActions(
                state = savedCertificates,
                onAdd = {
                    withBrowserExternalReturn(onToken = { certificateReturnToken = it }) {
                        savedCertificatePicker.launch(PKCS12_MIME_TYPES)
                    }
                },
                onSwitch = { entry ->
                    // Same signing cancellation as choosing a new file.
                    cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                    certificateViewModel.switchToSavedCertificate(entry)
                },
                onRemove = certificateViewModel::removeSavedCertificate,
            )
            val entryChoiceActions = CertificateEntryChoiceActions(
                onChoose = certificateViewModel::chooseCertificate,
                onCancel = certificateViewModel::cancelCertificateChoice,
            )
            CompositionLocalProvider(
                LocalSavedCertificates provides savedCertificateActions,
                LocalCertificateEntryChoice provides entryChoiceActions,
            ) { JuntaFirmaTheme {
                val unlocked = certificateState.value as? CertificateUiState.Unlocked
                val app = application as JuntaFirmaApplication
                val navController = rememberNavController()
                LaunchedEffect(destination, navigation.activeBrowserSessionId) {
                    val currentEntry = navController.currentBackStackEntry
                    val currentDestination = currentEntry?.destination
                    val route = when (val destination = destination) {
                        AppDestination.Certificate -> AppNavigationRoutes.CERTIFICATE
                        AppDestination.Catalog -> AppNavigationRoutes.CATALOG
                        is AppDestination.Browser ->
                            AppNavigationRoutes.browser(navigation.activeBrowserSessionId.orEmpty())
                    }
                    val isCurrent = when (val destination = destination) {
                        AppDestination.Certificate ->
                            currentDestination?.route == AppNavigationRoutes.CERTIFICATE
                        AppDestination.Catalog ->
                            currentDestination?.route == AppNavigationRoutes.CATALOG
                        is AppDestination.Browser ->
                            currentDestination?.route == AppNavigationRoutes.BROWSER &&
                                currentEntry?.arguments?.getString(
                                    AppNavigationRoutes.SESSION_ARG,
                                ) == navigation.activeBrowserSessionId
                    }
                    if (!isCurrent) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = AppNavigationRoutes.CERTIFICATE,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    composable(AppNavigationRoutes.CERTIFICATE) {
                        AppRoot(
                            state = certificateState.value,
                            onSelectCertificate = ::launchCertificatePicker,
                            onUnlock = certificateViewModel::unlock,
                            onRestoreSavedUnlock = certificateViewModel::restoreSavedUnlock,
                            onLock = certificateViewModel::lock,
                            onForget = certificateViewModel::forget,
                            onContinue = { navigation.showCatalog() },
                        )
                    }
                    composable(AppNavigationRoutes.CATALOG) {
                        PortalCatalogScreen(
                            state = catalogState.value,
                            onSearchTextChange = catalogViewModel::updateSearchText,
                            onSelectRegion = catalogViewModel::selectRegion,
                            onUseLocation = {
                                if (
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    catalogViewModel.detectRegion()
                                } else {
                                    coarseLocationPermission.launch(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                }
                            },
                            onOpenLocationSettings = {
                                runCatching {
                                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                            },
                            onOpenAppSettings = {
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:$packageName"),
                                        ),
                                    )
                                }
                            },
                            onDismissLocationMessage = catalogViewModel::dismissLocationMessage,
                            onToggleFavorite = catalogViewModel::toggleFavorite,
                            onBackToCertificate = { navigation.showCertificate() },
                            onOpenPortal = { item ->
                                val target = catalogRepository.resolveUniversalOpenTarget(item)
                                if (target == null) catalogViewModel.onOpenFailed() else {
                                    cancelSigning(SigningCancelReason.NAVIGATION)
                                    currentWebView = null
                                    catalogViewModel.recordOpened(item.portalId)
                                    navigation.openBrowser(profileId = null, entryUrl = target.entryUrl)
                                }
                            },
                            onOpenCompatibilityPortal = { item ->
                                when (val target = catalogRepository.resolveOpenTarget(item)) {
                                    is PortalOpenTarget.InApp -> {
                                        cancelSigning(SigningCancelReason.NAVIGATION)
                                        currentWebView = null
                                        catalogViewModel.recordOpened(item.portalId)
                                        navigation.openBrowser(
                                            profileId = target.launch.profileId,
                                            entryUrl = target.launch.entryUrl,
                                        )
                                    }
                                    is PortalOpenTarget.PublicWeb -> {
                                        cancelSigning(SigningCancelReason.NAVIGATION)
                                        currentWebView = null
                                        catalogViewModel.recordOpened(item.portalId)
                                        navigation.openBrowser(profileId = null, entryUrl = target.entryUrl)
                                    }
                                    null -> catalogViewModel.onOpenFailed()
                                }
                            },
                            onUserMessageShown = catalogViewModel::onUserMessageShown,
                            onOpenPublicWeb = { uri ->
                                val accepted =
                                    dev.junta.firmamobile.browser.PublicBrowserAddress.parse(uri.toASCIIString())
                                if (accepted == null) catalogViewModel.onOpenFailed() else {
                                    cancelSigning(SigningCancelReason.NAVIGATION)
                                    currentWebView = null
                                    navigation.openBrowser(profileId = null, entryUrl = accepted)
                                }
                            },
                        )
                    }
                    composable(AppNavigationRoutes.BROWSER) { entry ->
                        val sessionId =
                            entry.arguments?.getString(AppNavigationRoutes.SESSION_ARG).orEmpty()
                        val session = navigation.resolveBrowserSession(sessionId)
                        if (session == null) {
                            LaunchedEffect(sessionId) {
                                navigation.showCertificate()
                            }
                        } else {
                            key(
                                session.profileId?.value,
                                session.entryUrl.toASCIIString(),
                            ) {
                                BrowserScreen(
                                    profileId = session.profileId,
                                    entryUrl = session.entryUrl,
                                    certificateState = unlocked,
                                    certificatePanelVisible = certificatePanelVisible,
                                    mayRetainExternalReturn = {
                                        currentWebView?.let {
                                            browserExternalReturn.markDeparture(it, currentNavigationEpoch)
                                        } == true
                                    },
                                    consumeExternalReturn = {
                                        currentWebView?.let {
                                            browserExternalReturn.consume(it, currentNavigationEpoch)
                                        } == true
                                    },
                                    logger = app.sanitizedLogger,
                                    signingState = signingState,
                                    onMiniAppletRequest = ::prepareMiniAppletSigning,
                                    onMiniAppletCancel = { requestId ->
                                        cancelSigning(SigningCancelReason.JAVASCRIPT, requestId)
                                    },
                                    onMelillaBatchRequest = ::prepareMelillaBatchSigning,
                                    onMelillaBatchCancel = { requestId ->
                                        cancelSigning(SigningCancelReason.JAVASCRIPT, requestId)
                                    },
                                    onConfirmSigning = ::confirmSigning,
                                    onCancelSigning = ::cancelSigning,
                                    onDismissSigningState = ::dismissSigningState,
                                    onExitBrowser = {
                                        cancelSigning(SigningCancelReason.NAVIGATION)
                                        navigation.showCatalog()
                                    },
                                    onOpenExternal = { uri ->
                                        requestExternalHandoff(
                                            dev.junta.firmamobile.browser.ExternalHandoff.Kind.BROWSER,
                                            uri,
                                        )
                                    },
                                    onOpenOfficialAutoFirma = { uri ->
                                        requestExternalHandoff(
                                            dev.junta.firmamobile.browser.ExternalHandoff.Kind.AUTOFIRMA,
                                            uri,
                                        )
                                    },
                                    onExternalAppRequest = ::requestExternalApp,
                                    onChangeCertificate = {
                                        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                                        navigation.showCertificatePanel()
                                    },
                                    onLockCertificate = {
                                        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                                        certificateViewModel.lock()
                                    },
                                    onClearSession = {
                                        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
                                        certificateViewModel.lock()
                                    },
                                    clientCertificateIdentityProvider = {
                                        app.certificateSession.identityForSigning()
                                    },
                                    clientCertPreferenceCoordinator = app.clientCertPreferenceCoordinator,
                                    onShowFileChooser = { view, callback, params ->
                                        browserFileChooser.open(view, callback, params)
                                    },
                                    onDocumentDownload = { view, request ->
                                        val ownView = currentWebView === view ||
                                            (currentWebView == null && view.isAttachedToWindow && view.isShown)
                                        val isDocumentDownloadTarget =
                                            request.component?.packageName == packageName &&
                                            request.component?.className ==
                                            DocumentDownloadActivity::class.java.name
                                        if (!ownView || certificatePanelVisible ||
                                            !isDocumentDownloadTarget
                                        ) false else try {
                                            withBrowserExternalReturn(
                                                onToken = { documentDownloadReturnToken = it },
                                            ) {
                                                documentDownloadLauncher.launch(request)
                                            }
                                            true
                                        } catch (_: Exception) { false }
                                    },
                                    onWebViewChanged = {
                                        if (currentWebView !== it) {
                                            clearExternalApp()
                                            browserFileChooser.cancel()
                                            pendingExternalHandoff = null
                                            browserExternalReturn.invalidate()
                                        }
                                        currentWebView = it
                                    },
                                    onNavigationEpochChanged = {
                                        clearExternalApp()
                                        browserExternalReturn.invalidate()
                                        browserFileChooser.cancel()
                                        pendingExternalHandoff = null
                                        currentNavigationEpoch = it
                                    },
                                )
                            }
                            DisposableEffect(sessionId) {
                                onDispose {
                                    navigation.invalidateBrowserSession(sessionId)
                                }
                            }
                        }
                    }
                }
                pendingExternalApp?.let { request ->
                    LaunchedEffect(request.token) {
                        kotlinx.coroutines.delay(120_000)
                        if (pendingExternalApp === request) clearExternalApp()
                    }
                    dev.junta.firmamobile.ui.ExternalAppConsentDialog(
                        sourceHost = request.sourceHost,
                        scheme = request.target.scheme,
                        requestedPackage = request.target.packageName,
                        fallbackHost = request.target.webFallback?.host,
                        onOpenApp = { launchExternalApp(request, fallback = false) },
                        onOpenWeb = { launchExternalApp(request, fallback = true) },
                        onCancel = ::clearExternalApp,
                    )
                }
                externalAfirmaPrompt?.let { prompt ->
                    LaunchedEffect(prompt.token, prompt.phase) {
                        while (prompt.phase != dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase.FINISHED) {
                            kotlinx.coroutines.delay(EXTERNAL_AFIRMA_TICK_MILLIS)
                            externalAfirma?.tick()
                        }
                    }
                    // Hidden, not cancelled, while the user unlocks a certificate for it.
                    if (!certificatePanelVisible) {
                        dev.junta.firmamobile.ui.NativeAfirmaConsentDialog(
                            prompt = prompt,
                            onConfirm = { externalAfirma?.confirm(prompt.token) },
                            onUnlock = { navigation.showCertificatePanel() },
                            onCancel = { externalAfirma?.cancel(prompt.token) },
                            onDismiss = { externalAfirma?.dismiss(prompt.token) },
                        )
                    }
                }
                LaunchedEffect(certificateState.value, certificatePanelVisible) { externalAfirma?.refreshIdentity() }
                pendingExternalHandoff?.let { request ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { pendingExternalHandoff = null },
                        title = { androidx.compose.material3.Text(getString(R.string.external_handoff_title)) },
                        text = {
                            androidx.compose.material3.Text(getString(
                                if (request.kind == dev.junta.firmamobile.browser.ExternalHandoff.Kind.BROWSER)
                                    R.string.external_browser_copy else R.string.external_autofirma_copy,
                                request.sourceHost.orEmpty(),
                            ))
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                pendingExternalHandoff = null
                                if (request.navigationEpoch == currentNavigationEpoch) launchExternalHandoff(request)
                            }) { androidx.compose.material3.Text(getString(R.string.external_handoff_continue)) }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { pendingExternalHandoff = null }) {
                                androidx.compose.material3.Text(getString(R.string.cancel))
                            }
                        },
                    )
                }
                if (externalHandoffFailed) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { externalHandoffFailed = false },
                        title = { androidx.compose.material3.Text(getString(R.string.external_handoff_unavailable_title)) },
                        text = { androidx.compose.material3.Text(getString(R.string.external_handoff_unavailable_copy)) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { externalHandoffFailed = false }) {
                                androidx.compose.material3.Text(getString(R.string.close))
                            }
                        },
                    )
                }
                if (certificatePanelVisible) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = {
                            // A dismissed panel must not keep the typed password for a later entry choice.
                            certificateViewModel.cancelCertificateChoice()
                            navigation.hideCertificatePanel()
                        },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn,
                        ),
                    ) {
                        AppRoot(
                            state = certificateState.value,
                            onSelectCertificate = ::launchCertificatePicker,
                            onUnlock = certificateViewModel::unlock,
                        onRestoreSavedUnlock = certificateViewModel::restoreSavedUnlock,
                            onLock = certificateViewModel::lock,
                            onForget = certificateViewModel::forget,
                            onContinue = { navigation.hideCertificatePanel() },
                        )
                    }
                }
            } }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as JuntaFirmaApplication).certificateUnlockAuthentication.bind(this)
        certificateViewModel.onAppForegrounded()
        externalAfirma?.onForeground()
        if (::catalogSmokeHook.isInitialized) catalogSmokeHook.start()
    }

    override fun onResume() {
        super.onResume()
        drainExternalAfirma()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) drainExternalAfirma()
    }

    /** Admits a parked external afirma delivery only into a resumed, focused, unlocked window, or reports why
     * not. Exactly one outcome per delivery: consent dialog, busy, rejected or expired (ADR 0006). */
    private fun drainExternalAfirma() {
        val app = application as JuntaFirmaApplication
        val pending = externalAfirma != null && app.externalAfirmaInbox.hasPending() &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        val busy = app.signingAdmission.externalActive || app.signingAdmission.portalBusy()
        // A busy app answers at once; otherwise wait for focus so nothing is admitted behind another window.
        if (!pending || !(busy || readyForExternalConsent())) return
        externalAfirmaOutcome(app, busy)?.let {
            android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun readyForExternalConsent(): Boolean = hasWindowFocus() &&
        getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == false

    private fun externalAfirmaOutcome(app: JuntaFirmaApplication, busy: Boolean): Int? =
        when (val delivery = app.externalAfirmaInbox.take()) {
            dev.junta.firmamobile.afirma.external.ExternalAfirmaDelivery.None -> null
            dev.junta.firmamobile.afirma.external.ExternalAfirmaDelivery.Stale -> R.string.external_afirma_expired
            is dev.junta.firmamobile.afirma.external.ExternalAfirmaDelivery.Fresh -> when {
                busy || !app.signingAdmission.tryAcquireExternal(this) -> R.string.external_afirma_busy
                externalAfirma?.receive(delivery.rawUri) == true -> null
                else -> {
                    app.signingAdmission.releaseExternal(this)
                    R.string.external_afirma_rejected
                }
            }
        }

    override fun onStop() {
        clearExternalApp()
        if (::catalogSmokeHook.isInitialized) catalogSmokeHook.stop()
        cancelSigning(SigningCancelReason.BACKGROUND)
        externalAfirma?.onBackground()
        certificateViewModel.onAppBackgrounded()
        super.onStop()
    }

    override fun onLowMemory() {
        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
        certificateViewModel.onMemoryPressure()
        super.onLowMemory()
    }

    private fun requestExternalApp(view: WebView, target: dev.junta.firmamobile.browser.ExternalAppLink): Boolean {
        if (view !== currentWebView || pendingExternalApp != null || pendingExternalHandoff != null ||
            externalHandoffFailed || certificatePanelVisible || !view.hasWindowFocus()
        ) return false
        val source = dev.junta.firmamobile.browser.PublicBrowserAddress.parse(view.url ?: "") ?: return false
        val token = externalAppConsent.reserve(view, currentNavigationEpoch) ?: return false
        pendingExternalApp = PendingExternalApp(token, source.host, target)
        return true
    }

    private fun clearExternalApp() {
        pendingExternalApp = null
        externalAppConsent.invalidate()
    }

    private fun launchExternalApp(request: PendingExternalApp, fallback: Boolean) {
        if (pendingExternalApp !== request) return
        // Consume the exact owner/epoch ticket before the external side effect.
        if (!externalAppConsent.consume(request.token)) { clearExternalApp(); return }
        pendingExternalApp = null
        val intent = if (fallback) request.target.fallbackIntent() else request.target.minimalIntent()
        if (intent == null) return
        cancelSigning(SigningCancelReason.NAVIGATION)
        try {
            withBrowserExternalReturn { startActivity(intent) }
        } catch (_: android.content.ActivityNotFoundException) {
            externalHandoffFailed = true
        } catch (_: SecurityException) {
            externalHandoffFailed = true
        }
    }

    private fun requestExternalHandoff(kind: dev.junta.firmamobile.browser.ExternalHandoff.Kind, uri: Uri) {
        if (pendingExternalHandoff != null || pendingExternalApp != null) return
        val request = dev.junta.firmamobile.browser.ExternalHandoff.Request(
            kind, uri, currentNavigationEpoch,
            if (kind == dev.junta.firmamobile.browser.ExternalHandoff.Kind.BROWSER) uri.host else currentWebView?.url?.let { Uri.parse(it).host },
        )
        if (dev.junta.firmamobile.browser.ExternalHandoff.intentFor(request) == null) {
            externalHandoffFailed = true
        } else pendingExternalHandoff = request
    }

    private fun launchExternalHandoff(request: dev.junta.firmamobile.browser.ExternalHandoff.Request) {
        val intent = dev.junta.firmamobile.browser.ExternalHandoff.intentFor(request) ?: return
        cancelSigning(SigningCancelReason.NAVIGATION)
        try {
            withBrowserExternalReturn { startActivity(intent) }
        } catch (_: android.content.ActivityNotFoundException) {
            externalHandoffFailed = true
        } catch (_: SecurityException) {
            externalHandoffFailed = true
        }
    }

    private fun <T> withBrowserExternalReturn(onToken: (UUID?) -> Unit = {}, action: () -> T): T {
        val token = currentWebView?.let { browserExternalReturn.begin(it, currentNavigationEpoch) }
        onToken(token)
        return try {
            action()
        } catch (error: Exception) {
            token?.let(browserExternalReturn::cancel)
            throw error
        }
    }

    private fun launchCertificatePicker() {
        cancelSigning(SigningCancelReason.CERTIFICATE_LOCKED)
        certificateViewModel.prepareForCertificateSelection()
        withBrowserExternalReturn(onToken = { certificateReturnToken = it }) { certificatePicker.launch(PKCS12_MIME_TYPES) }
    }

    override fun onDestroy() {
        navigation.clearBrowserSessions()
        (application as JuntaFirmaApplication).certificateUnlockAuthentication.unbind(this)
        externalAfirma?.close()
        externalAfirma = null
        (application as JuntaFirmaApplication).signingAdmission.let {
            it.releaseExternal(this)
            it.unregisterPortal(this, activityPortalBusy)
        }
        browserFileChooser.cancel()
        if (::catalogSmokeHook.isInitialized) catalogSmokeHook.stop()
        signing?.close()
        super.onDestroy()
    }

    private fun externalAfirmaActive() = (application as JuntaFirmaApplication).signingAdmission.externalActive

    private fun prepareMiniAppletSigning(
        request: MiniAppletBridgeRequest,
        reply: SigningReplySink,
    ) {
        signing?.prepareMiniAppletSigning(request, reply) ?: run {
            request.normalized.close()
            runCatching { reply.failure(SigningErrorCode.PROTOCOL_FAILED) }
        }
    }

    private fun recordMiniAppletBridgeDiagnostic(
        request: MiniAppletBridgeRequest,
        stage: String,
        errorCode: SigningErrorCode?,
    ) {
        if (!BuildConfig.ALLOW_QA_PROFILES) return
        (application as JuntaFirmaApplication).sanitizedLogger.recordMiniAppletBridge(
            stage = stage,
            originHost = request.normalized.context.origin.host,
            algorithm = request.normalized.algorithm.name,
            format = request.normalized.format.name,
            errorCode = errorCode?.name,
        )
    }

    private fun prepareMelillaBatchSigning(
        request: MelillaBatchBridgeRequest,
        reply: MelillaBatchReplyChannel,
    ) {
        signing?.prepareMelillaBatchSigning(request, reply)
            ?: runCatching { reply.failure(SigningErrorCode.INVALID_REQUEST) }
    }

    private fun confirmSigning(requestId: UUID) {
        signing?.confirmSigning(requestId)
    }

    private fun cancelSigning(
        reason: SigningCancelReason,
        requestId: UUID? = null,
    ) {
        signing?.cancelSigning(reason, requestId)
    }

    private fun dismissSigningState() {
        signing?.dismissSigningState()
    }

    private fun currentSigningOrigin() =
        (destination as? AppDestination.Browser)?.profileId?.let { selectedProfileId ->
            currentWebView?.url?.let { url ->
                runCatching {
                    JuntaOriginPolicy.signingOriginFor(Uri.parse(url), selectedProfileId)
                }.getOrNull()
            }
        }

    private fun onTunnelRouteEvent(requestId: UUID, event: TunnelRouteEvent) {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            if (signing?.onTunnelRouteEvent(requestId, event) == true) {
                (application as JuntaFirmaApplication).sanitizedLogger.recordTunnelRouteEvent(event)
            }
        }
    }

    private fun activeWebViewMatches(profileId: ProfileId): Boolean {
        val browser = destination as? AppDestination.Browser ?: return false
        if (browser.profileId != profileId) return false
        val currentUrl = currentWebView?.url ?: return false
        val uri = runCatching { URI(currentUrl) }.getOrNull() ?: return false
        return BuiltInSiteProfiles.runtimeRegistry.resolveForProfile(profileId, uri)?.profile?.profileId == profileId
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
        private const val EXTERNAL_AFIRMA_TICK_MILLIS = 1_000L

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

internal enum class SigningFlowKind {
    ORDINARY,
    BATCH,
}

internal data class SigningFlowOwner(
    val kind: SigningFlowKind,
    val requestId: UUID,
)

internal class SigningFlowOwnershipGate {
    private var owner: SigningFlowOwner? = null

    @Synchronized
    fun acquire(kind: SigningFlowKind, requestId: UUID): Boolean {
        if (owner != null) return false
        owner = SigningFlowOwner(kind, requestId)
        return true
    }

    @Synchronized
    fun current(): SigningFlowOwner? = owner

    @Synchronized
    fun release(kind: SigningFlowKind, requestId: UUID): Boolean {
        if (owner != SigningFlowOwner(kind, requestId)) return false
        owner = null
        return true
    }
}
