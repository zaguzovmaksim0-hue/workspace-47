package dev.junta.firmamobile.browser

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.DiagnosticEventCode
import dev.junta.firmamobile.security.BoundedReplayLedger
import dev.junta.firmamobile.security.MonotonicSecurityTime
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningContext
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.json.JSONObject

internal data class AfirmaShimCompatibilityFlags(
    val ugr: Boolean,
    val cantabria: Boolean,
    val jccm: Boolean,
    val sevillaAtse: Boolean,
    val airef: Boolean,
    val cdti: Boolean,
    val policia: Boolean,
    val granCanaria: Boolean,
    val canarias: Boolean,
    val mineco: Boolean,
    val melillaBatch: Boolean,
    val lugoBatch: Boolean,
    val isciiiCertificateSelection: Boolean,
    val valenciaCertificateSelection: Boolean,
)

class WebMessageBridge(
    private val profileId: ProfileId,
    private val logger: SanitizedLogger,
    private val onAfirmaRequest: (AfirmaRequest) -> Unit,
    private val onMiniAppletRequest: ((MiniAppletBridgeRequest, MiniAppletReplyChannel) -> Unit)? =
        null,
    private val onMiniAppletCancel: (UUID) -> Unit = {},
    private val onCertificateSelectionRequest:
        ((CertificateSelectionBridgeRequest, CertificateSelectionReplyChannel) -> Unit)? = null,
    private val onCertificateSelectionCancel: (UUID) -> Unit = {},
    private val onMelillaBatchRequest:
        ((MelillaBatchRequest, MelillaBatchReplyChannel) -> Unit)? = null,
    private val onMelillaBatchCancel: (UUID) -> Unit = {},
    private val router: WebMessageRouter = WebMessageRouter(profileId),
    private val activeProfileId: () -> ProfileId? = { null },
    clock: Clock = Clock.systemUTC(),
    monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val miniAppletAdapter: MiniAppletBridgeAdapter = MiniAppletBridgeAdapter(
        clock = clock,
        monotonicNanos = monotonicNanos,
        activeProfileId = activeProfileId,
    ),
    isciiiCertificateSelectionAdapter: IsciiiCertificateSelectionBridgeAdapter? = null,
    valenciaCertificateSelectionAdapter: ValenciaCertificateSelectionBridgeAdapter? = null,
    private val miniAppletMode: MiniAppletBridgeMode = MiniAppletBridgeMode.OBSERVATION,
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val currentPageUrl: () -> String? = { null },
    private val currentDocumentId: () -> UUID? = { null },
    private val qaDiagnosticsEnabled: Boolean = BuildConfig.ALLOW_QA_PROFILES,
    melillaBatchAdapter: MelillaBatchBridgeAdapter? = null,
    extremaduraBatchAdapter: ExtremaduraBatchBridgeAdapter? = null,
    laPalmaBatchAdapter: LaPalmaBatchBridgeAdapter? = null,
    huescaBatchAdapter: HuescaBatchBridgeAdapter? = null,
    lugoBatchAdapter: LugoBatchBridgeAdapter? = null,
    burgosBatchAdapter: BurgosBatchBridgeAdapter? = null,
) {
    private var batchDocumentId: UUID? = null
    private var batchDocumentEpoch: Long? = null

    private val certificateSelectionAdapter: (
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        navigationEpoch: Long,
        currentPageUrl: String?,
    ) -> CertificateSelectionBridgeRouteResult = when (profileId.value) {
        IsciiiCertificateSelectionBridgeAdapter.PROFILE_ID -> {
            val adapter = isciiiCertificateSelectionAdapter ?: IsciiiCertificateSelectionBridgeAdapter(
                activeProfileId = activeProfileId,
                clock = clock,
            )
            adapter::route
        }
        ValenciaCertificateSelectionBridgeAdapter.PROFILE_ID -> {
            val adapter = valenciaCertificateSelectionAdapter ?: ValenciaCertificateSelectionBridgeAdapter(
                activeProfileId = activeProfileId,
                clock = clock,
            )
            adapter::route
        }
        else -> { _, _, _, _, _ -> CertificateSelectionBridgeRouteResult.NotApplicable }
    }

    private val batchRuntime: StaBatchBridgeRuntime? = when (profileId.value) {
        MelillaBatchBridgeAdapter.PROFILE_ID -> StaBatchBridgeRuntime(
            sourceOrigin = MelillaBatchBridgeAdapter.SOURCE_ORIGIN,
            trustedOrigin = TrustedOrigin("https", "sede.melilla.es", 443),
            adapter = StaBatchBridgeAdapterOps.from(
                melillaBatchAdapter ?: MelillaBatchBridgeAdapter(
                    activeProfileId = activeProfileId,
                    currentNavigationEpoch = currentNavigationEpoch,
                    currentDocumentId = { batchCurrentDocumentId() },
                    currentOrigin = currentOrigin,
                ),
            ),
        )
        ExtremaduraBatchBridgeAdapter.PROFILE_ID -> StaBatchBridgeRuntime(
            sourceOrigin = ExtremaduraBatchBridgeAdapter.SOURCE_ORIGIN,
            trustedOrigin = TrustedOrigin("https", "tramites.juntaex.es", 443),
            adapter = StaBatchBridgeAdapterOps.from(
                extremaduraBatchAdapter ?: ExtremaduraBatchBridgeAdapter(
                    activeProfileId = activeProfileId,
                    currentNavigationEpoch = currentNavigationEpoch,
                    currentDocumentId = { batchCurrentDocumentId() },
                    currentOrigin = currentOrigin,
                ),
            ),
        )
        LaPalmaBatchBridgeAdapter.PROFILE_ID -> StaBatchBridgeRuntime(
            sourceOrigin = LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN,
            trustedOrigin = TrustedOrigin("https", "sedeelectronica.cabildodelapalma.es", 443),
            adapter = StaBatchBridgeAdapterOps.from(
                laPalmaBatchAdapter ?: LaPalmaBatchBridgeAdapter(
                    activeProfileId = activeProfileId,
                    currentNavigationEpoch = currentNavigationEpoch,
                    currentDocumentId = { batchCurrentDocumentId() },
                    currentOrigin = currentOrigin,
                ),
            ),
        )
        HuescaBatchBridgeAdapter.PROFILE_ID -> StaBatchBridgeRuntime(
            sourceOrigin = HuescaBatchBridgeAdapter.SOURCE_ORIGIN,
            trustedOrigin = TrustedOrigin("https", "ovc24.dphuesca.es", 443),
            adapter = StaBatchBridgeAdapterOps.from(
                huescaBatchAdapter ?: HuescaBatchBridgeAdapter(
                    activeProfileId = activeProfileId,
                    currentNavigationEpoch = currentNavigationEpoch,
                    currentDocumentId = { batchCurrentDocumentId() },
                    currentOrigin = currentOrigin,
                ),
            ),
        )
        LugoBatchBridgeAdapter.PROFILE_ID -> StaBatchBridgeRuntime(
            sourceOrigin = LugoBatchBridgeAdapter.SOURCE_ORIGIN,
            trustedOrigin = TrustedOrigin("https", "sede.deputacionlugo.org", 443),
            adapter = StaBatchBridgeAdapterOps.from(
                lugoBatchAdapter ?: LugoBatchBridgeAdapter(
                    activeProfileId = activeProfileId,
                    currentNavigationEpoch = currentNavigationEpoch,
                    currentDocumentId = { batchCurrentDocumentId() },
                    currentOrigin = currentOrigin,
                ),
            ),
        )
        BurgosBatchBridgeAdapter.PROFILE_ID -> StaBatchBridgeRuntime(
            sourceOrigin = BurgosBatchBridgeAdapter.SOURCE_ORIGIN,
            trustedOrigin = TrustedOrigin("https", "registro.diputaciondeburgos.es", 443),
            adapter = StaBatchBridgeAdapterOps.from(
                burgosBatchAdapter ?: BurgosBatchBridgeAdapter(
                    activeProfileId = activeProfileId,
                    currentNavigationEpoch = currentNavigationEpoch,
                    currentDocumentId = { batchCurrentDocumentId() },
                    currentOrigin = currentOrigin,
                ),
            ),
        )
        else -> null
    }

    private val staBatchEnabled: Boolean
        get() = batchRuntime != null &&
            miniAppletMode == MiniAppletBridgeMode.FUNCTIONAL &&
            onMelillaBatchRequest != null

    private val replyRegistry = MiniAppletReplyRegistry(
        currentNavigationEpoch = currentNavigationEpoch,
        currentOrigin = currentOrigin,
        currentPageUrl = currentPageUrl,
        monotonicNanos = monotonicNanos,
    )

    private val certificateSelectionReplyRegistry = CertificateSelectionReplyRegistry(
        activeProfileId = activeProfileId,
        currentNavigationEpoch = currentNavigationEpoch,
        currentOrigin = currentOrigin,
        currentPageUrl = currentPageUrl,
        monotonicNanos = monotonicNanos,
    )

    private val melillaBatchReplyRegistry = MelillaBatchReplyRegistry(
        activeProfileId = activeProfileId,
        currentNavigationEpoch = currentNavigationEpoch,
        currentOrigin = currentOrigin,
        currentDocumentId = { batchCurrentDocumentId() },
        monotonicNanos = monotonicNanos,
        onTerminal = { requestId ->
            batchRuntime?.adapter?.abandon(requestId)
            Unit
        },
    )

    fun attach(webView: WebView): WebMessageBridgeAttachment {
        val originRules = (
            JuntaOriginPolicy.webMessageOriginRules(profileId) +
                if (staBatchEnabled) {
                    setOf(checkNotNull(batchRuntime).sourceOrigin)
                } else {
                    emptySet()
                }
            ).toSet()
        if (originRules.isEmpty()) {
            return WebMessageBridgeAttachment(webView = webView)
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_FEATURE_UNAVAILABLE)
            return WebMessageBridgeAttachment(webView = webView)
        }

        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            originRules,
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            receive(message, sourceOrigin, isMainFrame, replyProxy)
        }

        val shimFlags = shimCompatibilityFlags(
            profileId = profileId,
            profileActive = BuiltInSiteProfiles.runtimeRegistry.profile(profileId) != null,
            melillaBatchEnabled = staBatchEnabled,
        )
        val scriptHandler = if (
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                AfirmaJavascriptShim.load(
                    webView.context,
                    miniAppletMode,
                    qaDiagnosticsEnabled,
                    ugrCompatibilityEnabled = shimFlags.ugr,
                    cantabriaCompatibilityEnabled = shimFlags.cantabria,
                    jccmCompatibilityEnabled = shimFlags.jccm,
                    sevillaAtseCompatibilityEnabled = shimFlags.sevillaAtse,
                    airefCompatibilityEnabled = shimFlags.airef,
                    cdtiCompatibilityEnabled = shimFlags.cdti,
                    policiaCompatibilityEnabled = shimFlags.policia,
                    granCanariaCompatibilityEnabled = shimFlags.granCanaria,
                    canariasCompatibilityEnabled = shimFlags.canarias,
                    minecoCompatibilityEnabled = shimFlags.mineco,
                    melillaBatchCompatibilityEnabled = shimFlags.melillaBatch,
                    lugoBatchCompatibilityEnabled = shimFlags.lugoBatch,
                    staBatchOrigin = batchRuntime?.sourceOrigin ?: MelillaBatchBridgeAdapter.SOURCE_ORIGIN,
                    isciiiCertificateSelectionEnabled = shimFlags.isciiiCertificateSelection,
                    valenciaCertificateSelectionEnabled = shimFlags.valenciaCertificateSelection,
                ),
                originRules,
            )
        } else {
            logger.recordBrowserEvent(DiagnosticEventCode.DOCUMENT_START_SCRIPT_UNAVAILABLE)
            null
        }

        return WebMessageBridgeAttachment(
            webView = webView,
            listenerAttached = true,
            scriptHandler = scriptHandler,
            abandonPending = ::abandonAllMiniAppletRequests,
        )
    }

    private fun receive(
        message: WebMessageCompat,
        sourceOrigin: android.net.Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val rawMessage = message.data
        if (message.type != WebMessageCompat.TYPE_STRING || rawMessage == null) {
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
            return
        }
        if (receiveBatchDocumentReady(rawMessage, sourceOrigin, isMainFrame)) {
            return
        }

        when (
            val diagnostic = PortalCallbackDiagnosticProtocol.parse(
                rawMessage = rawMessage,
                sourceOrigin = sourceOrigin,
                isMainFrame = isMainFrame,
                expectedProfileId = profileId,
                registry = BuiltInSiteProfiles.runtimeRegistry,
                enabled = qaDiagnosticsEnabled,
            )
        ) {
            is PortalCallbackDiagnosticParseResult.Accepted -> {
                logger.recordPortalCallback(diagnostic.stage.name, sourceOrigin.host.orEmpty())
                return
            }
            PortalCallbackDiagnosticParseResult.Rejected -> {
                logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                return
            }
            PortalCallbackDiagnosticParseResult.NotApplicable -> Unit
        }

        when (
            val selectionResult = certificateSelectionAdapter(
                rawMessage,
                sourceOrigin,
                isMainFrame,
                currentNavigationEpoch(),
                currentPageUrl(),
            )
        ) {
            is CertificateSelectionBridgeRouteResult.Accepted -> {
                val reply = certificateSelectionReplyRegistry.create(
                    request = selectionResult.request,
                    postMessage = replyProxy::postMessage,
                )
                val handler = onCertificateSelectionRequest
                if (reply == null || handler == null) {
                    reply?.failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
                    logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                    return
                }
                runCatching { handler(selectionResult.request, reply) }.onFailure {
                    logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                    reply.failure(SigningErrorCode.PROTOCOL_FAILED)
                }
                return
            }
            is CertificateSelectionBridgeRouteResult.Cancelled -> {
                if (certificateSelectionReplyRegistry.abandon(
                        selectionResult.requestId,
                        selectionResult.navigationId,
                    )
                ) {
                    runCatching { onCertificateSelectionCancel(selectionResult.requestId) }
                }
                return
            }
            is CertificateSelectionBridgeRouteResult.Rejected -> {
                logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                selectionResult.requestId?.let { requestId ->
                    CertificateSelectionReplyChannel(
                        requestId = requestId,
                        postMessage = replyProxy::postMessage,
                    ).failure(selectionResult.code)
                }
                return
            }
            CertificateSelectionBridgeRouteResult.NotApplicable -> Unit
        }

        if (staBatchEnabled) {
            val runtime = checkNotNull(batchRuntime)
            val batchConsumer = checkNotNull(onMelillaBatchRequest)
            when (
                val batchResult = runtime.adapter.route(
                    rawMessage,
                    sourceOrigin,
                    isMainFrame,
                    currentNavigationEpoch(),
                )
            ) {
                is MelillaBatchBridgeRouteResult.Accepted -> {
                    val reply = melillaBatchReplyRegistry.create(
                        request = batchResult.request,
                        postMessage = replyProxy::postMessage,
                    )
                    if (reply == null) {
                        runtime.adapter.abandon(batchResult.request.requestId)
                        replyStaBatchFailure(runtime,
                            replyProxy = replyProxy,
                            sourceOrigin = sourceOrigin,
                            isMainFrame = isMainFrame,
                            requestId = batchResult.request.requestId,
                            code = SigningErrorCode.PROTOCOL_FAILED,
                        )
                        logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                        return
                    }
                    runCatching { batchConsumer(batchResult.request, reply) }.onFailure {
                        logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                        reply.failure(SigningErrorCode.PROTOCOL_FAILED)
                    }
                    return
                }
                is MelillaBatchBridgeRouteResult.Cancelled -> {
                    val owned = melillaBatchReplyRegistry.abandon(batchResult.requestId)
                    runtime.adapter.abandon(batchResult.requestId)
                    if (owned) {
                        runCatching { onMelillaBatchCancel(batchResult.requestId) }
                    }
                    return
                }
                is MelillaBatchBridgeRouteResult.Rejected -> {
                    logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                    replyStaBatchFailure(runtime,
                        replyProxy = replyProxy,
                        sourceOrigin = sourceOrigin,
                        isMainFrame = isMainFrame,
                        requestId = batchResult.requestId,
                        code = batchResult.code,
                    )
                    return
                }
                MelillaBatchBridgeRouteResult.NotApplicable -> Unit
            }
        }

        when (
            val directResult = miniAppletAdapter.route(
                rawMessage = rawMessage,
                sourceOrigin = sourceOrigin,
                isMainFrame = isMainFrame,
                navigationEpoch = currentNavigationEpoch(),
                currentPageUrl = currentPageUrl(),
            )
        ) {
            is MiniAppletBridgeRouteResult.Accepted -> {
                receiveMiniAppletRequest(directResult.request, replyProxy)
                return
            }
            is MiniAppletBridgeRouteResult.Rejected -> {
                logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                directResult.requestId?.let { requestId ->
                    MiniAppletReplyChannel(
                        requestId = requestId,
                        postMessage = replyProxy::postMessage,
                    ).failure(directResult.code)
                }
                return
            }
            is MiniAppletBridgeRouteResult.Cancelled -> {
                if (replyRegistry.abandon(
                        directResult.requestId,
                        directResult.navigationId,
                    )
                ) {
                    runCatching { onMiniAppletCancel(directResult.requestId) }
                }
                return
            }
            MiniAppletBridgeRouteResult.NotApplicable -> Unit
        }

        when (
            val result = router.route(
                rawMessage = rawMessage,
                sourceOrigin = sourceOrigin,
                isMainFrame = isMainFrame,
            )
        ) {
            is WebMessageRouteResult.Accepted -> {
                logger.recordAfirmaRequest(result.request)
                val delivered = runCatching { onAfirmaRequest(result.request) }.isSuccess
                if (delivered) {
                    reply(
                        replyProxy = replyProxy,
                        requestId = result.requestId,
                        status = WebMessageReplyStatus.ACCEPTED,
                    )
                } else {
                    logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                    reply(
                        replyProxy = replyProxy,
                        requestId = result.requestId,
                        status = WebMessageReplyStatus.REJECTED,
                        errorCode = ERROR_NATIVE_HANDLER_FAILURE,
                    )
                }
            }
            is WebMessageRouteResult.Rejected -> {
                logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                result.requestId?.let { requestId ->
                    reply(
                        replyProxy = replyProxy,
                        requestId = requestId,
                        status = WebMessageReplyStatus.REJECTED,
                        errorCode = result.errorCode,
                    )
                }
            }
        }
    }

    private fun receiveMiniAppletRequest(
        request: MiniAppletBridgeRequest,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (miniAppletMode != MiniAppletBridgeMode.FUNCTIONAL) {
            request.normalized.close()
            MiniAppletReplyChannel(
                requestId = request.normalized.requestId,
                postMessage = replyProxy::postMessage,
            ).failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
            return
        }
        val reply = replyRegistry.create(
            requestId = request.normalized.requestId,
            context = request.normalized.context,
            postMessage = replyProxy::postMessage,
        )
        if (reply == null) {
            request.normalized.close()
            MiniAppletReplyChannel(
                requestId = request.normalized.requestId,
                postMessage = replyProxy::postMessage,
            ).failure(SigningErrorCode.PROTOCOL_FAILED)
            return
        }
        val handler = onMiniAppletRequest
        if (handler == null) {
            request.normalized.close()
            reply.failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
            return
        }
        runCatching { handler(request, reply) }.onFailure {
            request.normalized.close()
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
            reply.failure(SigningErrorCode.PROTOCOL_FAILED)
        }
    }

    @Synchronized
    private fun receiveBatchDocumentReady(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
    ): Boolean {
        val json = runCatching { JSONObject(rawMessage) }.getOrNull()
            ?: return false
        if (json.optString(DOCUMENT_READY_FIELD) != DOCUMENT_READY_TYPE) return false

        val runtime = batchRuntime
        val accepted = staBatchEnabled &&
            runtime != null &&
            isMainFrame &&
            isExactStaSourceOrigin(sourceOrigin, runtime)
        if (!accepted || json.keys().asSequence().toSet() != DOCUMENT_READY_KEYS) {
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
            return true
        }

        val rawDocumentId = json.opt("documentId") as? String
        val documentId = rawDocumentId
            ?.takeIf { DOCUMENT_UUID_PATTERN.matches(it) }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.takeIf { it.toString() == rawDocumentId?.lowercase() }
        val epoch = currentNavigationEpoch()
        if (documentId == null || epoch < 0L || epoch == Long.MAX_VALUE ||
            currentDocumentId()?.let { it != documentId } == true
        ) {
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
            return true
        }
        if (batchDocumentId != null && batchDocumentId != documentId) {
            runtime.adapter.invalidateDocument(batchDocumentId)
        }
        batchDocumentId = documentId
        batchDocumentEpoch = epoch
        return true
    }

    @Synchronized
    private fun batchCurrentDocumentId(): UUID? {
        val externallyBound = runCatching { currentDocumentId() }.getOrNull()
        if (externallyBound != null) return externallyBound
        return batchDocumentEpoch
            ?.takeIf { it == runCatching { currentNavigationEpoch() }.getOrNull() }
            ?.let { batchDocumentId }
    }

    private fun abandonAllMiniAppletRequests() {
        certificateSelectionReplyRegistry.abandonAll().forEach { requestId ->
            runCatching { onCertificateSelectionCancel(requestId) }
        }
        batchRuntime?.adapter?.invalidateDocument(batchCurrentDocumentId())
        batchRuntime?.adapter?.abandonAll()
        melillaBatchReplyRegistry.abandonAll().forEach { requestId ->
            runCatching { onMelillaBatchCancel(requestId) }
        }
        batchDocumentId = null
        batchDocumentEpoch = null
        replyRegistry.abandonAll().forEach { requestId ->
            runCatching { onMiniAppletCancel(requestId) }
        }
    }

    private fun isExactStaSourceOrigin(uri: Uri, runtime: StaBatchBridgeRuntime): Boolean =
        uri.scheme == runtime.trustedOrigin.scheme &&
            uri.host == runtime.trustedOrigin.host &&
            uri.port in setOf(-1, runtime.trustedOrigin.port) &&
            uri.encodedUserInfo == null &&
            uri.path.isNullOrEmpty() &&
            uri.query == null &&
            uri.fragment == null

    private fun replyStaBatchFailure(
        runtime: StaBatchBridgeRuntime,
        replyProxy: JavaScriptReplyProxy,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        requestId: UUID?,
        code: SigningErrorCode,
    ) {
        if (!isMainFrame || requestId == null ||
            !isExactStaSourceOrigin(sourceOrigin, runtime)
        ) {
            return
        }
        MelillaBatchReplyChannel(
            requestId = requestId,
            postMessage = replyProxy::postMessage,
        ).failure(code)
    }

    private fun reply(
        replyProxy: JavaScriptReplyProxy,
        requestId: String,
        status: WebMessageReplyStatus,
        errorCode: String? = null,
    ) {
        runCatching {
            replyProxy.postMessage(
                WebMessageProtocol.replyJson(
                    requestId = requestId,
                    status = status,
                    errorCode = errorCode,
                ),
            )
        }.onFailure {
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
        }
    }

    companion object {
        const val BRIDGE_NAME = "JuntaFirmaMobile"
        private const val CANTABRIA_PROFILE_ID = "cantabria-rec-cert-login"
        private const val UGR_PROFILE_ID = "ugr-certificado-login"
        private const val JCCM_PROFILE_ID = "jccm-certificate-login-probe"
        private const val SEVILLA_ATSE_PROFILE_ID = "sevilla-atse-certificate-login"
        private const val AIREF_PROFILE_ID = "airef-instancia-general"
        private const val CDTI_PROFILE_ID = "cdti-certificate-validation"
        private const val ISCIII_PROFILE_ID = "isciii-certificate-selection"
        private const val VALENCIA_PROFILE_ID = "diputacion-valencia-sede"
        private const val POLICIA_PROFILE_ID = "policia-solicitud-generica"
        private const val GRAN_CANARIA_PROFILE_ID = "gran-canaria-sede-electronica"
        private const val CANARIAS_PROFILE_ID = "canarias-sede"
        private const val MINECO_PROFILE_ID = "ministerio-economia-instancia-generica"

        internal fun shimCompatibilityFlags(
            profileId: ProfileId,
            profileActive: Boolean,
            melillaBatchEnabled: Boolean,
        ): AfirmaShimCompatibilityFlags = AfirmaShimCompatibilityFlags(
            ugr = profileActive && profileId.value == UGR_PROFILE_ID,
            cantabria = profileActive && profileId.value == CANTABRIA_PROFILE_ID,
            jccm = profileActive && profileId.value == JCCM_PROFILE_ID,
            sevillaAtse = profileActive && profileId.value == SEVILLA_ATSE_PROFILE_ID,
            airef = profileActive && profileId.value == AIREF_PROFILE_ID,
            cdti = profileActive && profileId.value == CDTI_PROFILE_ID,
            policia = profileActive && profileId.value == POLICIA_PROFILE_ID,
            granCanaria = profileActive && profileId.value == GRAN_CANARIA_PROFILE_ID,
            canarias = profileActive && profileId.value == CANARIAS_PROFILE_ID,
            mineco = profileActive && profileId.value == MINECO_PROFILE_ID,
            melillaBatch = melillaBatchEnabled && profileId.value != LugoBatchBridgeAdapter.PROFILE_ID,
            lugoBatch = melillaBatchEnabled && profileId.value == LugoBatchBridgeAdapter.PROFILE_ID,
            isciiiCertificateSelection = profileActive && profileId.value == ISCIII_PROFILE_ID,
            valenciaCertificateSelection = profileActive && profileId.value == VALENCIA_PROFILE_ID,
        )

        private const val ERROR_NATIVE_HANDLER_FAILURE = "NATIVE_HANDLER_FAILURE"
        private const val DOCUMENT_READY_FIELD = "type"
        private const val DOCUMENT_READY_TYPE = "MINIAPPLET_DOCUMENT_READY"
        private val DOCUMENT_READY_KEYS = setOf("type", "documentId")
        private val DOCUMENT_UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-" +
                "[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
        )
    }
}

private data class StaBatchBridgeRuntime(
    val sourceOrigin: String,
    val trustedOrigin: TrustedOrigin,
    val adapter: StaBatchBridgeAdapterOps,
)

private class StaBatchBridgeAdapterOps(
    val route: (String, Uri, Boolean, Long) -> MelillaBatchBridgeRouteResult,
    val abandon: (UUID?) -> Boolean,
    val invalidateDocument: (UUID?) -> Unit,
    val abandonAll: () -> Unit,
) {
    companion object {
        fun from(adapter: MelillaBatchBridgeAdapter) = StaBatchBridgeAdapterOps(
            route = { raw, origin, mainFrame, epoch ->
                adapter.route(raw, origin, mainFrame, epoch)
            },
            abandon = adapter::abandon,
            invalidateDocument = adapter::invalidateDocument,
            abandonAll = adapter::abandonAll,
        )

        fun from(adapter: ExtremaduraBatchBridgeAdapter) = StaBatchBridgeAdapterOps(
            route = { raw, origin, mainFrame, epoch ->
                adapter.route(raw, origin, mainFrame, epoch)
            },
            abandon = adapter::abandon,
            invalidateDocument = adapter::invalidateDocument,
            abandonAll = adapter::abandonAll,
        )

        fun from(adapter: LaPalmaBatchBridgeAdapter) = StaBatchBridgeAdapterOps(
            route = { raw, origin, mainFrame, epoch ->
                adapter.route(raw, origin, mainFrame, epoch)
            },
            abandon = adapter::abandon,
            invalidateDocument = adapter::invalidateDocument,
            abandonAll = adapter::abandonAll,
        )

        fun from(adapter: HuescaBatchBridgeAdapter) = StaBatchBridgeAdapterOps(
            route = { raw, origin, mainFrame, epoch ->
                adapter.route(raw, origin, mainFrame, epoch)
            },
            abandon = adapter::abandon,
            invalidateDocument = adapter::invalidateDocument,
            abandonAll = adapter::abandonAll,
        )

        fun from(adapter: LugoBatchBridgeAdapter) = StaBatchBridgeAdapterOps(
            route = { raw, origin, mainFrame, epoch ->
                adapter.route(raw, origin, mainFrame, epoch)
            },
            abandon = adapter::abandon,
            invalidateDocument = adapter::invalidateDocument,
            abandonAll = adapter::abandonAll,
        )
        fun from(adapter: BurgosBatchBridgeAdapter) = StaBatchBridgeAdapterOps(
            route = { raw, origin, mainFrame, epoch ->
                adapter.route(raw, origin, mainFrame, epoch)
            },
            abandon = adapter::abandon,
            invalidateDocument = adapter::invalidateDocument,
            abandonAll = adapter::abandonAll,
        )
    }
}

class WebMessageBridgeAttachment internal constructor(
    private val webView: WebView,
    val listenerAttached: Boolean = false,
    private val scriptHandler: ScriptHandler? = null,
    private val abandonPending: () -> Unit = {},
) : AutoCloseable {
    val documentStartScriptAttached: Boolean
        get() = scriptHandler != null

    private var closed = false

    fun abandonMiniAppletRequests() {
        abandonPending()
    }

    override fun close() {
        if (closed) return
        closed = true
        abandonPending()
        scriptHandler?.remove()
        if (listenerAttached) {
            WebViewCompat.removeWebMessageListener(webView, WebMessageBridge.BRIDGE_NAME)
        }
    }
}

internal class MiniAppletReplyRegistry(
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val currentPageUrl: () -> String? = { null },
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    replayRetention: Duration = DEFAULT_REPLAY_RETENTION,
    maxReplayEntries: Int = MAX_SEEN_REQUESTS,
) {
    private val pending = linkedMapOf<UUID, PendingReply>()
    private val replyTtlNanos = MonotonicSecurityTime.durationNanos(REPLY_TTL)
    private val replayLedger = BoundedReplayLedger<UUID>(
        monotonicNanos = monotonicNanos,
        retention = replayRetention,
        maxEntries = maxReplayEntries,
    )

    @Synchronized
    fun create(
        requestId: UUID,
        context: SigningContext,
        postMessage: (String) -> Unit,
    ): MiniAppletReplyChannel? {
        expireInvalidPending()
        if (pending.isNotEmpty() || replayLedger.contains(requestId)) return null
        val binding = PendingBinding(
            profileId = context.profileId,
            origin = context.origin,
            pageUrl = context.pageUrl,
            navigationId = context.navigationId,
            navigationEpoch = context.navigationEpoch,
            observedAtMonotonicNanos = monotonicNanos(),
        )
        if (!isCurrent(binding) || !replayLedger.recordNew(requestId)) return null
        lateinit var channel: MiniAppletReplyChannel
        channel = MiniAppletReplyChannel(
            requestId = requestId,
            postMessage = postMessage,
            onTerminal = { remove(requestId, channel) },
            canDeliver = { isCurrent(binding) },
        )
        pending[requestId] = PendingReply(binding, channel)
        return channel
    }

    @Synchronized
    fun abandon(requestId: UUID, navigationId: NavigationId): Boolean {
        val pendingReply = pending[requestId]
            ?.takeIf { it.binding.navigationId == navigationId }
            ?: return false
        return pendingReply.channel.abandon()
    }

    fun abandonAll(): List<UUID> {
        val replies = synchronized(this) { pending.toMap() }
        return replies.mapNotNull { (requestId, reply) ->
            requestId.takeIf { reply.channel.abandon() }
        }
    }

    @Synchronized
    private fun remove(requestId: UUID, channel: MiniAppletReplyChannel) {
        if (pending[requestId]?.channel === channel) {
            pending.remove(requestId)
            replayLedger.refresh(requestId)
        }
    }

    private fun expireInvalidPending() {
        pending.values
            .filterNot { isCurrent(it.binding) }
            .forEach { it.channel.abandon() }
    }

    private fun isCurrent(binding: PendingBinding): Boolean = runCatching {
        if (MonotonicSecurityTime.isExpiredOrInvalid(
                binding.observedAtMonotonicNanos,
                replyTtlNanos,
                monotonicNanos(),
            ) ||
            currentNavigationEpoch() != binding.navigationEpoch ||
            currentOrigin() != binding.origin ||
            (binding.pageUrl != null && currentPageUrl() != binding.pageUrl)
        ) return@runCatching false
        BuiltInSiteProfiles.runtimeRegistry.resolve(binding.origin)
            ?.profile?.profileId?.value == binding.profileId
    }.getOrDefault(false)

    private companion object {
        const val MAX_SEEN_REQUESTS = 64
        val REPLY_TTL: Duration = Duration.ofMinutes(2)
        val DEFAULT_REPLAY_RETENTION: Duration = Duration.ofMinutes(5)
    }

    private data class PendingReply(
        val binding: PendingBinding,
        val channel: MiniAppletReplyChannel,
    )

    private data class PendingBinding(
        val profileId: String,
        val origin: TrustedOrigin,
        val pageUrl: String?,
        val navigationId: NavigationId,
        val navigationEpoch: Long,
        val observedAtMonotonicNanos: Long,
    )
}


internal class MelillaBatchReplyRegistry(
    private val activeProfileId: () -> ProfileId?,
    private val currentNavigationEpoch: () -> Long,
    private val currentOrigin: () -> TrustedOrigin?,
    private val currentDocumentId: () -> UUID?,
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val onTerminal: (UUID) -> Unit = {},
) {
    private val pending = linkedMapOf<UUID, PendingReply>()
    private val replyTtlNanos = MonotonicSecurityTime.durationNanos(REPLY_TTL)

    @Synchronized
    fun create(
        request: MelillaBatchRequest,
        postMessage: (String) -> Unit,
    ): MelillaBatchReplyChannel? {
        expireInvalidPending()
        if (pending.isNotEmpty()) return null
        val binding = PendingBinding(
            profileId = request.profileId.value,
            origin = request.sourceOrigin,
            documentId = request.documentId,
            navigationEpoch = request.navigationEpoch,
            observedAtMonotonicNanos = monotonicNanos(),
        )
        if (!isCurrent(binding)) return null
        lateinit var channel: MelillaBatchReplyChannel
        channel = MelillaBatchReplyChannel(
            requestId = request.requestId,
            postMessage = postMessage,
            onTerminal = {
                remove(request.requestId, channel)
                onTerminal(request.requestId)
            },
            canDeliver = { isCurrent(binding) },
            validationMode = if (request.profileId.value == LugoBatchBridgeAdapter.PROFILE_ID) {
                BatchReplyValidationMode.BASE64_XML
            } else {
                BatchReplyValidationMode.STRICT_JSON
            },
        )
        pending[request.requestId] = PendingReply(binding, channel)
        return channel
    }

    @Synchronized
    fun abandon(requestId: UUID): Boolean =
        pending[requestId]?.channel?.abandon() == true

    fun abandonAll(): List<UUID> {
        val replies = synchronized(this) { pending.toMap() }
        return replies.mapNotNull { (requestId, reply) ->
            requestId.takeIf { reply.channel.abandon() }
        }
    }

    @Synchronized
    private fun remove(requestId: UUID, channel: MelillaBatchReplyChannel) {
        if (pending[requestId]?.channel === channel) {
            pending.remove(requestId)
        }
    }

    private fun expireInvalidPending() {
        pending.values
            .filterNot { isCurrent(it.binding) }
            .forEach { it.channel.abandon() }
    }

    private fun isCurrent(binding: PendingBinding): Boolean = runCatching {
        activeProfileId()?.value == binding.profileId &&
            currentNavigationEpoch() == binding.navigationEpoch &&
            currentOrigin() == binding.origin &&
            currentDocumentId()?.let { it == binding.documentId } != false &&
            !MonotonicSecurityTime.isExpiredOrInvalid(
                binding.observedAtMonotonicNanos,
                replyTtlNanos,
                monotonicNanos(),
            )
    }.getOrDefault(false)

    private data class PendingReply(
        val binding: PendingBinding,
        val channel: MelillaBatchReplyChannel,
    )

    private data class PendingBinding(
        val profileId: String,
        val origin: TrustedOrigin,
        val documentId: UUID,
        val navigationEpoch: Long,
        val observedAtMonotonicNanos: Long,
    )

    private companion object {
        val REPLY_TTL: Duration = Duration.ofMinutes(2)
    }
}
