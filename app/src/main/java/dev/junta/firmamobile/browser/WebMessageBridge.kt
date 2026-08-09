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

class WebMessageBridge(
    private val profileId: ProfileId,
    private val logger: SanitizedLogger,
    private val onAfirmaRequest: (AfirmaRequest) -> Unit,
    private val onMiniAppletRequest: ((MiniAppletBridgeRequest, MiniAppletReplyChannel) -> Unit)? =
        null,
    private val onMiniAppletCancel: (UUID) -> Unit = {},
    private val onMelillaBatchRequest:
        ((MelillaBatchRequest, MelillaBatchReplyChannel) -> Unit)? = null,
    private val router: WebMessageRouter = WebMessageRouter(profileId),
    private val activeProfileId: () -> ProfileId? = { null },
    clock: Clock = Clock.systemUTC(),
    monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val miniAppletMode: MiniAppletBridgeMode = MiniAppletBridgeMode.OBSERVATION,
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val currentDocumentId: () -> UUID? = { null },
    private val qaDiagnosticsEnabled: Boolean = BuildConfig.ALLOW_QA_PROFILES,
    private val miniAppletAdapter: MiniAppletBridgeAdapter = MiniAppletBridgeAdapter(
        clock = clock,
        monotonicNanos = monotonicNanos,
        activeProfileId = activeProfileId,
    ),
    melillaBatchAdapter: MelillaBatchBridgeAdapter? = null,
) {
    private var batchDocumentId: UUID? = null
    private var batchDocumentEpoch: Long? = null

    private val batchAdapter: MelillaBatchBridgeAdapter =
        melillaBatchAdapter ?: MelillaBatchBridgeAdapter(
            activeProfileId = activeProfileId,
            currentNavigationEpoch = currentNavigationEpoch,
            currentDocumentId = { batchCurrentDocumentId() },
            currentOrigin = currentOrigin,
        )

    private val melillaBatchEnabled: Boolean
        get() = profileId.value == MelillaBatchBridgeAdapter.PROFILE_ID &&
            miniAppletMode == MiniAppletBridgeMode.FUNCTIONAL &&
            onMelillaBatchRequest != null

    private val replyRegistry = MiniAppletReplyRegistry(
        currentNavigationEpoch = currentNavigationEpoch,
        currentOrigin = currentOrigin,
        monotonicNanos = monotonicNanos,
    )

    private val melillaBatchReplyRegistry = MelillaBatchReplyRegistry(
        activeProfileId = activeProfileId,
        currentNavigationEpoch = currentNavigationEpoch,
        currentOrigin = currentOrigin,
        currentDocumentId = { batchCurrentDocumentId() },
        monotonicNanos = monotonicNanos,
        onTerminal = { requestId ->
            batchAdapter.abandon(requestId)
            Unit
        },
    )

    fun attach(webView: WebView): WebMessageBridgeAttachment {
        val originRules = (
            JuntaOriginPolicy.webMessageOriginRules(profileId) +
                if (melillaBatchEnabled) {
                    setOf(MelillaBatchBridgeAdapter.SOURCE_ORIGIN)
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

        val scriptHandler = if (
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                AfirmaJavascriptShim.load(
                    webView.context,
                    miniAppletMode,
                    qaDiagnosticsEnabled,
                    ugrCompatibilityEnabled = profileId.value == UGR_PROFILE_ID &&
                        BuiltInSiteProfiles.runtimeRegistry.profile(profileId) != null,
                    melillaBatchCompatibilityEnabled = melillaBatchEnabled,
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
        if (receiveMelillaDocumentReady(rawMessage, sourceOrigin, isMainFrame)) {
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

        if (
            profileId.value == MelillaBatchBridgeAdapter.PROFILE_ID &&
            miniAppletMode == MiniAppletBridgeMode.FUNCTIONAL &&
            onMelillaBatchRequest != null
        ) {
            val batchConsumer = checkNotNull(onMelillaBatchRequest)
            when (
                val batchResult = batchAdapter.route(
                    rawMessage = rawMessage,
                    sourceOrigin = sourceOrigin,
                    isMainFrame = isMainFrame,
                    navigationEpoch = currentNavigationEpoch(),
                )
            ) {
                is MelillaBatchBridgeRouteResult.Accepted -> {
                    val reply = melillaBatchReplyRegistry.create(
                        request = batchResult.request,
                        postMessage = replyProxy::postMessage,
                    )
                    if (reply == null) {
                        batchAdapter.abandon(batchResult.request.requestId)
                        replyMelillaBatchFailure(
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
                    melillaBatchReplyRegistry.abandon(batchResult.requestId)
                    batchAdapter.abandon(batchResult.requestId)
                    return
                }
                is MelillaBatchBridgeRouteResult.Rejected -> {
                    logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                    replyMelillaBatchFailure(
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
    private fun receiveMelillaDocumentReady(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
    ): Boolean {
        val json = runCatching { JSONObject(rawMessage) }.getOrNull()
            ?: return false
        if (json.optString(DOCUMENT_READY_FIELD) != DOCUMENT_READY_TYPE) return false

        val accepted = profileId.value == MelillaBatchBridgeAdapter.PROFILE_ID &&
            miniAppletMode == MiniAppletBridgeMode.FUNCTIONAL &&
            isMainFrame &&
            isExactMelillaSourceOrigin(sourceOrigin)
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
            batchAdapter.invalidateDocument(batchDocumentId)
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
        batchAdapter.invalidateDocument(batchCurrentDocumentId())
        batchAdapter.abandonAll()
        melillaBatchReplyRegistry.abandonAll()
        batchDocumentId = null
        batchDocumentEpoch = null
        replyRegistry.abandonAll().forEach { requestId ->
            runCatching { onMiniAppletCancel(requestId) }
        }
    }

    private fun isExactMelillaSourceOrigin(uri: Uri): Boolean =
        uri.scheme == "https" &&
            uri.host == "sede.melilla.es" &&
            uri.port in setOf(-1, 443) &&
            uri.encodedUserInfo == null &&
            uri.path.isNullOrEmpty() &&
            uri.query == null &&
            uri.fragment == null

    private fun replyMelillaBatchFailure(
        replyProxy: JavaScriptReplyProxy,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        requestId: UUID?,
        code: SigningErrorCode,
    ) {
        if (!isMainFrame || requestId == null ||
            !isExactMelillaSourceOrigin(sourceOrigin)
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
        private const val UGR_PROFILE_ID = "ugr-certificado-login"
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
            currentOrigin() != binding.origin
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
        )
        pending[request.requestId] = PendingReply(binding, channel)
        return channel
    }

    @Synchronized
    fun abandon(requestId: UUID): Boolean =
        pending[requestId]?.channel?.abandon() == true

    fun abandonAll() {
        val replies = synchronized(this) { pending.toMap() }
        replies.values.forEach { it.channel.abandon() }
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
