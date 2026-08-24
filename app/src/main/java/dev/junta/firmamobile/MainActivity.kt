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
import dev.junta.firmamobile.browser.BurgosBatchBridgeAdapter
import dev.junta.firmamobile.browser.CaibBatchBridgeAdapter
import dev.junta.firmamobile.browser.CaibBatchSigningAdapter
import dev.junta.firmamobile.browser.BurgosBatchSigningAdapter
import dev.junta.firmamobile.browser.ExtremaduraBatchBridgeAdapter
import dev.junta.firmamobile.browser.ExtremaduraBatchSigningAdapter
import dev.junta.firmamobile.browser.HuescaBatchBridgeAdapter
import dev.junta.firmamobile.browser.HuescaBatchSigningAdapter
import dev.junta.firmamobile.browser.LaPalmaBatchBridgeAdapter
import dev.junta.firmamobile.browser.LaPalmaBatchSigningAdapter
import dev.junta.firmamobile.browser.LugoBatchBridgeAdapter
import dev.junta.firmamobile.browser.LugoBatchSigningAdapter
import dev.junta.firmamobile.browser.MelillaBatchBridgeAdapter
import dev.junta.firmamobile.browser.MelillaBatchBridgeRequest
import dev.junta.firmamobile.browser.MelillaBatchReplyChannel
import dev.junta.firmamobile.browser.MelillaBatchSigningAdapter
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalCatalogScreen
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PublicPortalCatalogParser
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.network.TunnelRouteEvent
import dev.junta.firmamobile.network.TunnelRouteObserver
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.signing.BatchSigningCoordinator
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.CoroutineSigningExpiryScheduler
import dev.junta.firmamobile.signing.JcaLocalSignatureEngine
import dev.junta.firmamobile.signing.JuntaTriPhaseAdapter
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import dev.junta.firmamobile.signing.LocalCadesDetachedAdapter
import dev.junta.firmamobile.signing.DgtVerificationCadesAdapter
import dev.junta.firmamobile.signing.DiputacionBadajozCadesAdapter
import dev.junta.firmamobile.signing.DiputacionLleidaCadesAdapter
import dev.junta.firmamobile.signing.CanariasCertificateLoginCadesAdapter
import dev.junta.firmamobile.signing.EivissaCadesDetachedAdapter
import dev.junta.firmamobile.signing.UgrCadesDetachedAdapter
import dev.junta.firmamobile.signing.JccmCertificateLoginProbeCadesAdapter
import dev.junta.firmamobile.signing.MitesCertificateLoginCadesAdapter
import dev.junta.firmamobile.signing.GranCanariaPadesAdapter
import dev.junta.firmamobile.signing.FuerteventuraPadesAdapter
import dev.junta.firmamobile.signing.TransparenciaPadesAdapter
import dev.junta.firmamobile.signing.MinecoPadesAdapter
import dev.junta.firmamobile.signing.TenerifeCadesDetachedAdapter
import dev.junta.firmamobile.signing.SevillaAtseXadesEnvelopingAdapter
import dev.junta.firmamobile.signing.AirefXadesEnvelopingAdapter
import dev.junta.firmamobile.signing.CdtiXadesEnvelopingAdapter
import dev.junta.firmamobile.signing.TransportesXadesEnvelopedAdapter
import dev.junta.firmamobile.signing.PoliciaXadesDetachedAdapter
import dev.junta.firmamobile.signing.LocalXadesDetachedAdapter
import dev.junta.firmamobile.signing.BurgosBatchProtocolAdapter
import dev.junta.firmamobile.signing.CaibBatchProtocolAdapter
import dev.junta.firmamobile.signing.ExtremaduraBatchProtocolAdapter
import dev.junta.firmamobile.signing.HuescaBatchProtocolAdapter
import dev.junta.firmamobile.signing.LaPalmaBatchProtocolAdapter
import dev.junta.firmamobile.signing.LugoBatchProtocolAdapter
import dev.junta.firmamobile.signing.MelillaBatchProtocolAdapter
import dev.junta.firmamobile.signing.UnizarTriPhaseAdapter
import dev.junta.firmamobile.signing.XuntaPadesTriPhaseAdapter
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningCoordinator
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningPreparationResult
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.smoke.CatalogSmokeController
import dev.junta.firmamobile.smoke.CatalogSmokeHook
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.BrowserScreen
import dev.junta.firmamobile.ui.CertificateUiState
import dev.junta.firmamobile.ui.CertificateViewModel
import dev.junta.firmamobile.ui.SensitiveWindowProtection
import dev.junta.firmamobile.ui.SensitiveWindowStatePolicy
import dev.junta.firmamobile.ui.WindowSecureFlagPolicy
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
import java.net.URI
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var currentWebView: WebView? = null
    private var destination by mutableStateOf<AppDestination>(AppDestination.Certificate)
    private lateinit var signingCoordinator: SigningCoordinator
    private lateinit var batchSigningCoordinator: BatchSigningCoordinator
    private lateinit var melillaBatchSigningAdapter: MelillaBatchSigningAdapter
    private lateinit var extremaduraBatchSigningAdapter: ExtremaduraBatchSigningAdapter
    private lateinit var laPalmaBatchSigningAdapter: LaPalmaBatchSigningAdapter
    private lateinit var huescaBatchSigningAdapter: HuescaBatchSigningAdapter
    private lateinit var lugoBatchSigningAdapter: LugoBatchSigningAdapter
    private lateinit var caibBatchSigningAdapter: CaibBatchSigningAdapter
    private lateinit var burgosBatchSigningAdapter: BurgosBatchSigningAdapter
    private val signingFlowOwnership = SigningFlowOwnershipGate()
    private lateinit var catalogRepository: PortalCatalogRepository
    private lateinit var catalogSmokeHook: CatalogSmokeHook
    private var currentNavigationEpoch: Long = 0L
    private val signingJobs = SigningJobRegistry()

    private val certificateViewModel: CertificateViewModel by viewModels {
        val app = application as JuntaFirmaApplication
        CertificateViewModel.Factory(
            app.certificateGateway,
            app.certificateSession,
            app.certificateUnlockCache,
        )
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
        val routeObserver = TunnelRouteObserver(::onTunnelRouteEvent)
        val directJuntaTransport = HttpsProfileHttpTransport()
        val ofvirtualTransport = app.secureTunnelRuntime.transportFor(
            profileId = ProfileId("junta-ofvirtual"),
            endpoint = URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT),
            observer = routeObserver,
        )
        val juntaAdapter = JuntaTriPhaseAdapter(transport = directJuntaTransport)
        val juntaOfvirtualAdapter = JuntaOfvirtualTriPhaseAdapter(transport = ofvirtualTransport)
        val redsaraAdapter = LocalXadesDetachedAdapter()
        val aragonAdapter = LocalCadesDetachedAdapter()
        val dgtAdapter = DgtVerificationCadesAdapter()
        val lleidaAdapter = DiputacionLleidaCadesAdapter()
        val badajozAdapter = DiputacionBadajozCadesAdapter()
        val canariasAdapter = CanariasCertificateLoginCadesAdapter()
        val ugrAdapter = UgrCadesDetachedAdapter()
        val jccmAdapter = JccmCertificateLoginProbeCadesAdapter()
        val mitesAdapter = MitesCertificateLoginCadesAdapter()
        val granCanariaAdapter = GranCanariaPadesAdapter()
        val fuerteventuraAdapter = FuerteventuraPadesAdapter()
        val xuntaAdapter = XuntaPadesTriPhaseAdapter()
        val transparenciaAdapter = TransparenciaPadesAdapter()
        val minecoAdapter = MinecoPadesAdapter()
        val tenerifeAdapter = TenerifeCadesDetachedAdapter()
        val eivissaAdapter = EivissaCadesDetachedAdapter()
        val sevillaAdapter = SevillaAtseXadesEnvelopingAdapter()
        val airefAdapter = AirefXadesEnvelopingAdapter()
        val cdtiAdapter = CdtiXadesEnvelopingAdapter()
        val transportesAdapter = TransportesXadesEnvelopedAdapter()
        val policiaAdapter = PoliciaXadesDetachedAdapter()
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
            currentPageUrl = { currentWebView?.url },
            expiryScheduler = CoroutineSigningExpiryScheduler(lifecycleScope),
            adapterResolver = { id ->
                when (id) {
                    juntaAdapter.id -> juntaAdapter
                    juntaOfvirtualAdapter.id -> juntaOfvirtualAdapter
                    redsaraAdapter.id -> redsaraAdapter
                    aragonAdapter.id -> aragonAdapter
                    dgtAdapter.id -> dgtAdapter
                    lleidaAdapter.id -> lleidaAdapter
                    badajozAdapter.id -> badajozAdapter
                    canariasAdapter.id -> canariasAdapter
                    ugrAdapter.id -> ugrAdapter
                    jccmAdapter.id -> jccmAdapter
                    mitesAdapter.id -> mitesAdapter
                    granCanariaAdapter.id -> granCanariaAdapter
                    fuerteventuraAdapter.id -> fuerteventuraAdapter
                    xuntaAdapter.id -> xuntaAdapter
                    transparenciaAdapter.id -> transparenciaAdapter
                    minecoAdapter.id -> minecoAdapter
                    tenerifeAdapter.id -> tenerifeAdapter
                    eivissaAdapter.id -> eivissaAdapter
                    sevillaAdapter.id -> sevillaAdapter
                    airefAdapter.id -> airefAdapter
                    cdtiAdapter.id -> cdtiAdapter
                    transportesAdapter.id -> transportesAdapter
                    policiaAdapter.id -> policiaAdapter
                    unizarAdapter.id -> unizarAdapter
                    else -> null
                }
            },
        )
        val melillaProfile = checkNotNull(
            BuiltInSiteProfiles.catalog.profiles.singleOrNull {
                it.profileId == ProfileId("melilla-sede")
            },
        )
        melillaBatchSigningAdapter = MelillaBatchSigningAdapter(
            registry = BuiltInSiteProfiles.runtimeRegistry,
        )
        extremaduraBatchSigningAdapter = ExtremaduraBatchSigningAdapter(
            registry = BuiltInSiteProfiles.runtimeRegistry,
        )
        laPalmaBatchSigningAdapter = LaPalmaBatchSigningAdapter(
            registry = BuiltInSiteProfiles.runtimeRegistry,
        )
        huescaBatchSigningAdapter = HuescaBatchSigningAdapter(
            registry = BuiltInSiteProfiles.runtimeRegistry,
        )
        lugoBatchSigningAdapter = LugoBatchSigningAdapter(
            registry = BuiltInSiteProfiles.runtimeRegistry,
        )
        caibBatchSigningAdapter = CaibBatchSigningAdapter(
            registry = BuiltInSiteProfiles.runtimeRegistry,
        )
        burgosBatchSigningAdapter = BurgosBatchSigningAdapter(
            registry = BuiltInSiteProfiles.runtimeRegistry,
        )
        val melillaBatchProtocolAdapter = MelillaBatchProtocolAdapter(transport = HttpsProfileHttpTransport())
        val extremaduraBatchProtocolAdapter = ExtremaduraBatchProtocolAdapter(transport = HttpsProfileHttpTransport())
        val laPalmaBatchProtocolAdapter = LaPalmaBatchProtocolAdapter(transport = HttpsProfileHttpTransport())
        val huescaBatchProtocolAdapter = HuescaBatchProtocolAdapter(transport = HttpsProfileHttpTransport())
        val lugoBatchProtocolAdapter = LugoBatchProtocolAdapter()
        val caibBatchProtocolAdapter = CaibBatchProtocolAdapter()
        val burgosBatchProtocolAdapter = BurgosBatchProtocolAdapter(transport = HttpsProfileHttpTransport())
        batchSigningCoordinator = BatchSigningCoordinator(
            certificateSession = app.certificateSession,
            adapter = melillaBatchProtocolAdapter,
            adapterResolver = { id ->
                when (id) {
                    melillaBatchProtocolAdapter.id -> melillaBatchProtocolAdapter
                    extremaduraBatchProtocolAdapter.id -> extremaduraBatchProtocolAdapter
                    laPalmaBatchProtocolAdapter.id -> laPalmaBatchProtocolAdapter
                    huescaBatchProtocolAdapter.id -> huescaBatchProtocolAdapter
                    lugoBatchProtocolAdapter.id -> lugoBatchProtocolAdapter
                    caibBatchProtocolAdapter.id -> caibBatchProtocolAdapter
                    burgosBatchProtocolAdapter.id -> burgosBatchProtocolAdapter
                    else -> null
                }
            },
            localSignatureEngine = JcaLocalSignatureEngine(),
            currentOrigin = { currentSigningOrigin() },
            currentNavigationEpoch = { currentNavigationEpoch },
            expiryScheduler = CoroutineSigningExpiryScheduler(lifecycleScope),
            profileDisplayName = melillaProfile.displayName,
            supportLevel = melillaProfile.compatibilityStatus.name,
            profileRegistry = BuiltInSiteProfiles.runtimeRegistry,
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
            val ordinarySigningState = signingCoordinator.state.collectAsStateWithLifecycle()
            val batchSigningState = batchSigningCoordinator.state.collectAsStateWithLifecycle()
            val signingState = when (signingFlowOwnership.current()?.kind) {
                SigningFlowKind.BATCH -> batchSigningState.value
                SigningFlowKind.ORDINARY -> ordinarySigningState.value
                null -> SigningUiState.Idle
            }
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
                        clientCertPreferenceCoordinator = app.clientCertPreferenceCoordinator,
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
        certificateViewModel.onAppForegrounded()
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
        batchSigningCoordinator.close()
        super.onDestroy()
    }

    private fun prepareMiniAppletSigning(
        request: MiniAppletBridgeRequest,
        reply: SigningReplySink,
    ) {
        val requestId = request.normalized.requestId
        if (!signingFlowOwnership.acquire(SigningFlowKind.ORDINARY, requestId)) {
            request.normalized.close()
            runCatching { reply.failure(SigningErrorCode.PROTOCOL_FAILED) }
            return
        }
        val result = signingCoordinator.prepare(request.normalized, reply)
        if (result is SigningPreparationResult.Rejected && signingCoordinator.state.value is SigningUiState.Idle) {
            signingFlowOwnership.release(SigningFlowKind.ORDINARY, requestId)
        }
    }

    private fun prepareMelillaBatchSigning(
        request: MelillaBatchBridgeRequest,
        reply: MelillaBatchReplyChannel,
    ) {
        val normalized = when (request.profileId.value) {
            MelillaBatchBridgeAdapter.PROFILE_ID -> melillaBatchSigningAdapter.normalize(request)
            ExtremaduraBatchBridgeAdapter.PROFILE_ID -> extremaduraBatchSigningAdapter.normalize(request)
            LaPalmaBatchBridgeAdapter.PROFILE_ID -> laPalmaBatchSigningAdapter.normalize(request)
            HuescaBatchBridgeAdapter.PROFILE_ID -> huescaBatchSigningAdapter.normalize(request)
            LugoBatchBridgeAdapter.PROFILE_ID -> lugoBatchSigningAdapter.normalize(request)
            CaibBatchBridgeAdapter.PROFILE_ID -> caibBatchSigningAdapter.normalize(request)
            BurgosBatchBridgeAdapter.PROFILE_ID -> burgosBatchSigningAdapter.normalize(request)
            else -> null
        }
        val replySink = when (request.profileId.value) {
            MelillaBatchBridgeAdapter.PROFILE_ID -> melillaBatchSigningAdapter.replySink(reply)
            ExtremaduraBatchBridgeAdapter.PROFILE_ID -> extremaduraBatchSigningAdapter.replySink(reply)
            LaPalmaBatchBridgeAdapter.PROFILE_ID -> laPalmaBatchSigningAdapter.replySink(reply)
            HuescaBatchBridgeAdapter.PROFILE_ID -> huescaBatchSigningAdapter.replySink(reply)
            LugoBatchBridgeAdapter.PROFILE_ID -> lugoBatchSigningAdapter.replySink(reply)
            CaibBatchBridgeAdapter.PROFILE_ID -> caibBatchSigningAdapter.replySink(reply)
            BurgosBatchBridgeAdapter.PROFILE_ID -> burgosBatchSigningAdapter.replySink(reply)
            else -> {
                runCatching { reply.failure(SigningErrorCode.INVALID_REQUEST) }
                return
            }
        }
        if (normalized == null) {
            runCatching { replySink.failure(SigningErrorCode.INVALID_REQUEST) }
            return
        }
        val requestId = normalized.requestId
        if (!signingFlowOwnership.acquire(SigningFlowKind.BATCH, requestId)) {
            normalized.close()
            runCatching { replySink.failure(SigningErrorCode.PROTOCOL_FAILED) }
            return
        }
        val result = batchSigningCoordinator.prepare(normalized, replySink)
        if (result is SigningPreparationResult.Rejected && batchSigningCoordinator.state.value is SigningUiState.Idle) {
            signingFlowOwnership.release(SigningFlowKind.BATCH, requestId)
        }
    }

    private fun confirmSigning(requestId: UUID) {
        val owner = signingFlowOwnership.current() ?: return
        if (owner.requestId != requestId) return
        val awaiting = when (owner.kind) {
            SigningFlowKind.ORDINARY -> signingCoordinator.state.value
            SigningFlowKind.BATCH -> batchSigningCoordinator.state.value
        } as? SigningUiState.AwaitingConfirmation
        if (awaiting?.requestId != requestId) return
        val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            when (owner.kind) {
                SigningFlowKind.ORDINARY -> signingCoordinator.confirm(requestId)
                SigningFlowKind.BATCH -> batchSigningCoordinator.confirm(requestId)
            }
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
        val owner = signingFlowOwnership.current() ?: return
        if (requestId != null && owner.requestId != requestId) return
        val accepted = when (owner.kind) {
            SigningFlowKind.ORDINARY -> signingCoordinator.cancel(reason, requestId)
            SigningFlowKind.BATCH -> batchSigningCoordinator.cancel(reason, requestId)
        }
        val cancellationJob = signingJobs.takeForCancellation(requestId, accepted)
        if (accepted && cancellationJob != null) {
            cancellationJob.invokeOnCompletion {
                signingFlowOwnership.release(owner.kind, owner.requestId)
            }
            cancellationJob.cancel()
        } else if (accepted) {
            signingFlowOwnership.release(owner.kind, owner.requestId)
        }
    }

    private fun dismissSigningState() {
        val owner = signingFlowOwnership.current() ?: return
        when (owner.kind) {
            SigningFlowKind.ORDINARY -> signingCoordinator.dismissTerminalState()
            SigningFlowKind.BATCH -> batchSigningCoordinator.dismissTerminalState()
        }
        val state = when (owner.kind) {
            SigningFlowKind.ORDINARY -> signingCoordinator.state.value
            SigningFlowKind.BATCH -> batchSigningCoordinator.state.value
        }
        if (state is SigningUiState.Idle) {
            signingFlowOwnership.release(owner.kind, owner.requestId)
        }
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
            if (::signingCoordinator.isInitialized) {
                if (signingCoordinator.onTunnelRouteEvent(requestId, event)) {
                    (application as JuntaFirmaApplication).sanitizedLogger.recordTunnelRouteEvent(event)
                }
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
