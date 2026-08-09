package dev.junta.firmamobile.browser

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

class WebMessageBridge(
    private val profileId: ProfileId,
    private val logger: SanitizedLogger,
    private val onAfirmaRequest: (AfirmaRequest) -> Unit,
    private val onMiniAppletRequest: ((MiniAppletBridgeRequest, MiniAppletReplyChannel) -> Unit)? =
        null,
    private val onMiniAppletCancel: (UUID) -> Unit = {},
    private val router: WebMessageRouter = WebMessageRouter(profileId),
    activeProfileId: () -> ProfileId? = { null },
    clock: Clock = Clock.systemUTC(),
    monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val miniAppletAdapter: MiniAppletBridgeAdapter = MiniAppletBridgeAdapter(
        clock = clock,
        monotonicNanos = monotonicNanos,
        activeProfileId = activeProfileId,
    ),
    private val miniAppletMode: MiniAppletBridgeMode = MiniAppletBridgeMode.OBSERVATION,
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val currentOrigin: () -> TrustedOrigin? = { null },
    private val qaDiagnosticsEnabled: Boolean = BuildConfig.ALLOW_QA_PROFILES,
) {
    private val replyRegistry = MiniAppletReplyRegistry(
        currentNavigationEpoch = currentNavigationEpoch,
        currentOrigin = currentOrigin,
        monotonicNanos = monotonicNanos,
    )

    fun attach(webView: WebView): WebMessageBridgeAttachment {
        val originRules = JuntaOriginPolicy.webMessageOriginRules(profileId)
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
                    cantabriaCompatibilityEnabled = profileId.value == CANTABRIA_PROFILE_ID &&
                        BuiltInSiteProfiles.runtimeRegistry.profile(profileId) != null,
                    jccmCompatibilityEnabled = profileId.value == JCCM_PROFILE_ID &&
                        BuiltInSiteProfiles.runtimeRegistry.profile(profileId) != null,
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

    private fun abandonAllMiniAppletRequests() {
        replyRegistry.abandonAll().forEach { requestId ->
            runCatching { onMiniAppletCancel(requestId) }
        }
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
        private const val ERROR_NATIVE_HANDLER_FAILURE = "NATIVE_HANDLER_FAILURE"
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
