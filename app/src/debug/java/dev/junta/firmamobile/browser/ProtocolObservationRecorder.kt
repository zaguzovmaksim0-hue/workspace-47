package dev.junta.firmamobile.browser

import android.net.Uri
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.security.DiagnosticEventCode
import dev.junta.firmamobile.security.SanitizedLogger
import java.io.StringReader
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class ObservedMiniAppletCall {
    LOAD,
    SIGN,
}

enum class ObservedRuntimeBranch {
    AFIRMA,
    INTENT,
    WEBSOCKET,
    DIRECT_NETWORK,
    NONE,
}

enum class ObservationCorrelation {
    REQUEST_ID,
    ACTIVE_CALL_WINDOW,
    NONE,
}

data class SafeProtocolObservation(
    val call: ObservedMiniAppletCall,
    val originHost: String,
    val algorithm: String?,
    val format: String?,
    val argumentLengths: List<Int>,
    val branch: ObservedRuntimeBranch,
    val correlation: ObservationCorrelation = ObservationCorrelation.NONE,
)

internal data class SafeRecorderState(
    val documentActive: Boolean,
    val failedClosed: Boolean,
    val bufferedMessageCount: Int,
    val pendingCallCount: Int,
    val activationResult: DocumentActivationResult,
)

internal enum class DocumentActivationResult {
    NONE,
    STALE_GENERATION,
    INVALID_ID,
    INVALID_ORIGIN,
    ACTIVE_MISMATCH,
    BUFFER_MISMATCH,
    SUCCESS,
    LIFECYCLE_REJECTED,
}

fun interface ProtocolObservationSink {
    fun record(observation: SafeProtocolObservation)
}

class ProtocolObservationRecorder(
    private val logger: SanitizedLogger,
    private val onUpdated: (String) -> Unit = {},
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) : ProtocolObservationSink {
    private val pendingCalls = LinkedHashMap<UUID, PendingCall>()
    private val correlatedCallsAwaitingEnd = LinkedHashMap<UUID, PendingCorrelation>()
    private val seenRequestIds = LinkedHashSet<UUID>()
    private val bufferedMessages = ArrayList<ParsedProbeMessage>()
    private var activeDocumentId: UUID? = null
    private var activeDocumentOriginHost: String? = null
    private var documentFailedClosed = false
    private var documentGeneration = 0L
    private var activationResult = DocumentActivationResult.NONE
    private var correlationRejectionReported = false

    @Synchronized
    override fun record(observation: SafeProtocolObservation) {
        if (!isValid(observation)) return
        logger.recordMiniAppletObservation(
            call = observation.call.name,
            originHost = observation.originHost,
            algorithm = observation.algorithm,
            format = observation.format,
            argumentLengths = observation.argumentLengths,
            branch = observation.branch.name,
            correlation = observation.correlation.name,
        )
        onUpdated(logger.exportText())
    }

    @Synchronized
    private fun recordBranch(
        branch: ObservedRuntimeBranch,
        originHost: String,
        requestId: UUID,
        correlation: ObservationCorrelation,
    ): Boolean {
        if (branch == ObservedRuntimeBranch.NONE || !ensureFreshCalls()) {
            return failClosed()
        }
        val pending = pendingCalls[requestId] ?: return failClosed()
        if (pending.documentId != activeDocumentId ||
            pending.observation.originHost != originHost ||
            activeDocumentOriginHost != originHost
        ) {
            return failClosed()
        }
        pendingCalls.remove(requestId)
        val observation = pending.observation.copy(
            originHost = originHost,
            branch = branch,
            correlation = correlation,
        )
        if (!isValid(observation)) return failClosed()
        correlatedCallsAwaitingEnd[requestId] = PendingCorrelation(
            observation = observation,
            openedAtMillis = pending.openedAtMillis,
        )
        return true
    }

    @Synchronized
    fun recordNavigationBranch(
        branch: ObservedRuntimeBranch,
        originHost: String,
        isMainFrame: Boolean,
    ): Boolean {
        if (!isMainFrame || branch !in NATIVE_NAVIGATION_BRANCHES ||
            originHost !in JuntaOriginPolicy.allowedHosts
        ) {
            return false
        }
        if (documentFailedClosed || activeDocumentId == null || !ensureFreshCalls()) {
            return failClosed()
        }
        val activeRequestId = singleEligibleSignRequestId(originHost)
            ?: return failClosed()
        return recordBranch(
            branch = branch,
            originHost = originHost,
            requestId = activeRequestId,
            correlation = ObservationCorrelation.ACTIVE_CALL_WINDOW,
        )
    }

    @Synchronized
    fun rejectNavigationTransition(
        originHost: String,
        isMainFrame: Boolean,
    ): Boolean {
        if (!isMainFrame || originHost !in JuntaOriginPolicy.allowedHosts) return false
        return failClosed()
    }

    @Synchronized
    fun beginDocument(): Long {
        pendingCalls.clear()
        correlatedCallsAwaitingEnd.clear()
        seenRequestIds.clear()
        bufferedMessages.clear()
        activeDocumentId = null
        activeDocumentOriginHost = null
        documentFailedClosed = false
        activationResult = DocumentActivationResult.NONE
        correlationRejectionReported = false
        documentGeneration = if (documentGeneration == Long.MAX_VALUE) 1L else {
            documentGeneration + 1L
        }
        return documentGeneration
    }

    @Synchronized
    fun activateDocument(
        rawDocumentId: String,
        originHost: String,
        generation: Long,
    ): Boolean {
        if (generation != documentGeneration) {
            activationResult = DocumentActivationResult.STALE_GENERATION
            return false
        }
        val documentId = rawDocumentId.canonicalUuid() ?: run {
            activationResult = DocumentActivationResult.INVALID_ID
            return failClosed()
        }
        if (originHost !in JuntaOriginPolicy.allowedHosts) {
            activationResult = DocumentActivationResult.INVALID_ORIGIN
            return failClosed()
        }
        if (documentFailedClosed) return false
        activeDocumentId?.let { active ->
            return if (active == documentId && activeDocumentOriginHost == originHost) {
                activationResult = DocumentActivationResult.SUCCESS
                true
            } else {
                activationResult = DocumentActivationResult.ACTIVE_MISMATCH
                failClosed()
            }
        }
        if (bufferedMessages.any {
                it.documentId != documentId || it.originHost != originHost
            }
        ) {
            activationResult = DocumentActivationResult.BUFFER_MISMATCH
            return failClosed()
        }
        activeDocumentId = documentId
        activeDocumentOriginHost = originHost
        val messages = bufferedMessages.toList()
        bufferedMessages.clear()
        for (message in messages) {
            if (!processMessage(message)) return failClosed()
        }
        activationResult = DocumentActivationResult.SUCCESS
        return true
    }

    @Synchronized
    fun rejectCurrentDocument(generation: Long) {
        if (generation == documentGeneration) {
            activationResult = DocumentActivationResult.LIFECYCLE_REJECTED
            failClosed()
        }
    }

    @Synchronized
    fun recordMessage(rawMessage: String, sourceOrigin: Uri, isMainFrame: Boolean): Boolean {
        if (!isMainFrame) return false
        val origin = JuntaOriginPolicy.originFor(sourceOrigin) ?: return false
        if (documentFailedClosed || rawMessage.length > MAX_MESSAGE_CHARS) {
            return failClosed()
        }
        val message = parseMessage(rawMessage, origin.host) ?: return failClosed()
        val active = activeDocumentId
        if (active == null) {
            if (bufferedMessages.size >= MAX_BUFFERED_MESSAGES) return failClosed()
            bufferedMessages.add(message)
            return true
        }
        if (message.documentId != active || message.originHost != activeDocumentOriginHost) {
            return failClosed()
        }
        return processMessage(message)
    }

    @Synchronized
    fun rejectInvalidMessage(sourceOrigin: Uri, isMainFrame: Boolean): Boolean {
        if (!isMainFrame || JuntaOriginPolicy.originFor(sourceOrigin) == null) return false
        return failClosed()
    }

    private fun parseMessage(rawMessage: String, originHost: String): ParsedProbeMessage? {
        val streamedKeys = rawMessage.uniqueTopLevelKeys() ?: return null
        val json = try {
            JSONObject(rawMessage)
        } catch (_: Exception) {
            return null
        }
        if (json.keySet() != streamedKeys) return null
        val documentId = json.canonicalUuid(DOCUMENT_ID_FIELD) ?: return null
        val requestId = json.canonicalUuid(REQUEST_ID_FIELD) ?: return null
        return when (json.optString(TYPE_FIELD)) {
            TYPE_MINIAPPLET_OBSERVATION -> {
                if (json.keySet() !in MINIAPPLET_ALLOWED_KEY_SETS) return null
                val call = json.stringEnum<ObservedMiniAppletCall>(CALL_FIELD) ?: return null
                val algorithm = json.optionalSafeToken(ALGORITHM_FIELD) ?: if (
                    json.has(ALGORITHM_FIELD) && !json.isNull(ALGORITHM_FIELD)
                ) {
                    return null
                } else {
                    null
                }
                val format = json.optionalSafeToken(FORMAT_FIELD) ?: if (
                    json.has(FORMAT_FIELD) && !json.isNull(FORMAT_FIELD)
                ) {
                    return null
                } else {
                    null
                }
                val lengths = json.safeArgumentLengths() ?: return null
                ParsedProbeMessage.Call(
                    documentId = documentId,
                    requestId = requestId,
                    observation = SafeProtocolObservation(
                        call = call,
                        originHost = originHost,
                        algorithm = algorithm,
                        format = format,
                        argumentLengths = lengths,
                        branch = ObservedRuntimeBranch.NONE,
                    ),
                )
            }
            TYPE_RUNTIME_BRANCH_OBSERVATION -> {
                if (json.keySet() != BRANCH_ALLOWED_KEYS) return null
                val branch = json.stringEnum<ObservedRuntimeBranch>(BRANCH_FIELD)
                    ?.takeUnless { it == ObservedRuntimeBranch.NONE }
                    ?: return null
                ParsedProbeMessage.Branch(
                    documentId = documentId,
                    requestId = requestId,
                    originHost = originHost,
                    branch = branch,
                )
            }
            TYPE_MINIAPPLET_CALL_END -> {
                if (json.keySet() != CALL_END_ALLOWED_KEYS) return null
                ParsedProbeMessage.CallEnd(documentId, requestId, originHost)
            }
            else -> null
        }
    }

    private fun processMessage(message: ParsedProbeMessage): Boolean {
        if (documentFailedClosed || message.documentId != activeDocumentId ||
            message.originHost != activeDocumentOriginHost
        ) {
            return failClosed()
        }
        return when (message) {
            is ParsedProbeMessage.Call -> recordCall(message.requestId, message.observation)
            is ParsedProbeMessage.Branch -> recordBranch(
                branch = message.branch,
                originHost = message.originHost,
                requestId = message.requestId,
                correlation = ObservationCorrelation.REQUEST_ID,
            )
            is ParsedProbeMessage.CallEnd -> recordCallEnd(message.requestId)
        }
    }

    @Synchronized
    fun exportText(): String = logger.exportText()

    @Synchronized
    internal fun safeStateForTesting(): SafeRecorderState = SafeRecorderState(
        documentActive = activeDocumentId != null,
        failedClosed = documentFailedClosed,
        bufferedMessageCount = bufferedMessages.size,
        pendingCallCount = pendingCalls.size + correlatedCallsAwaitingEnd.size,
        activationResult = activationResult,
    )

    @Synchronized
    private fun recordCall(
        requestId: UUID,
        observation: SafeProtocolObservation,
    ): Boolean {
        val documentId = activeDocumentId ?: return failClosed()
        if (documentFailedClosed || !ensureFreshCalls() || !isValid(observation) ||
            requestId in seenRequestIds || seenRequestIds.size >= MAX_SEEN_REQUESTS ||
            pendingCalls.size + correlatedCallsAwaitingEnd.size >= MAX_PENDING_CALLS
        ) {
            return failClosed()
        }
        seenRequestIds.add(requestId)
        pendingCalls[requestId] = PendingCall(
            observation = observation,
            openedAtMillis = elapsedRealtimeMillis(),
            documentId = documentId,
        )
        record(observation)
        return true
    }

    private fun recordCallEnd(requestId: UUID): Boolean {
        if (!ensureFreshCalls()) return failClosed()
        return when {
            pendingCalls.remove(requestId) != null -> true
            else -> {
                val correlation = correlatedCallsAwaitingEnd.remove(requestId)
                    ?: return failClosed()
                record(correlation.observation)
                true
            }
        }
    }

    private fun singleEligibleSignRequestId(originHost: String): UUID? {
        if (pendingCalls.size != 1) return null
        val (requestId, pending) = pendingCalls.entries.first()
        val observation = pending.observation
        return requestId.takeIf {
            observation.call == ObservedMiniAppletCall.SIGN &&
                pending.documentId == activeDocumentId &&
                observation.originHost == originHost &&
                observation.algorithm != null &&
                observation.format != null
        }
    }

    private fun ensureFreshCalls(): Boolean {
        val now = elapsedRealtimeMillis()
        val expiredCall = pendingCalls.values.any { pending ->
            val age = now - pending.openedAtMillis
            age < 0L || age > MAX_ACTIVE_CALL_MILLIS
        }
        val expiredCorrelation = correlatedCallsAwaitingEnd.values.any { pending ->
            val age = now - pending.openedAtMillis
            age < 0L || age > MAX_ACTIVE_CALL_MILLIS
        }
        return if (expiredCall || expiredCorrelation) failClosed() else true
    }

    private fun failClosed(): Boolean {
        documentFailedClosed = true
        pendingCalls.clear()
        correlatedCallsAwaitingEnd.clear()
        bufferedMessages.clear()
        if (!correlationRejectionReported) {
            correlationRejectionReported = true
            logger.recordBrowserEvent(DiagnosticEventCode.PROTOCOL_CORRELATION_REJECTED)
            onUpdated(logger.exportText())
        }
        return false
    }

    private fun isValid(observation: SafeProtocolObservation): Boolean =
        JuntaOriginPolicy.allowedHosts.contains(observation.originHost) &&
            observation.argumentLengths.size <= MAX_ARGUMENTS &&
            observation.argumentLengths.all { it in 0..MAX_ARGUMENT_LENGTH } &&
            observation.algorithm.isSafeOptionalToken() &&
            observation.format.isSafeOptionalToken()

    private fun JSONObject.safeArgumentLengths(): List<Int>? {
        val values = opt(ARGUMENT_LENGTHS_FIELD) as? JSONArray ?: return null
        if (values.length() > MAX_ARGUMENTS) return null
        return buildList(values.length()) {
            repeat(values.length()) { index ->
                val value = when (val number = values.opt(index)) {
                    is Byte -> number.toLong()
                    is Short -> number.toLong()
                    is Int -> number.toLong()
                    is Long -> number
                    else -> return null
                }
                if (value !in 0L..MAX_ARGUMENT_LENGTH.toLong()) return null
                add(value.toInt())
            }
        }
    }

    private fun JSONObject.optionalSafeToken(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name) as? String ?: return null
        return value.takeIf(SAFE_TOKEN::matches)
    }

    private fun JSONObject.canonicalUuid(name: String): UUID? {
        val raw = opt(name) as? String ?: return null
        return raw.canonicalUuid()
    }

    private fun String.canonicalUuid(): UUID? {
        val parsed = try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return parsed.takeIf { it.toString().equals(this, ignoreCase = true) }
    }

    private inline fun <reified T : Enum<T>> JSONObject.stringEnum(name: String): T? {
        val value = opt(name) as? String ?: return null
        return enumValues<T>().firstOrNull { it.name == value }
    }

    private fun JSONObject.keySet(): Set<String> = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }

    private fun String.uniqueTopLevelKeys(): Set<String>? = try {
        JsonReader(StringReader(this)).use { reader ->
            reader.isLenient = false
            val names = linkedSetOf<String>()
            reader.beginObject()
            while (reader.hasNext()) {
                if (!names.add(reader.nextName())) return null
                reader.skipValue()
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) return null
            names
        }
    } catch (_: Exception) {
        null
    }

    private fun String?.isSafeOptionalToken(): Boolean = this == null || SAFE_TOKEN.matches(this)

    private companion object {
        const val MAX_MESSAGE_CHARS = 4096
        const val MAX_ARGUMENTS = 32
        const val MAX_ARGUMENT_LENGTH = 1_048_576
        const val MAX_PENDING_CALLS = 8
        const val MAX_SEEN_REQUESTS = 64
        const val MAX_BUFFERED_MESSAGES = 64
        const val MAX_ACTIVE_CALL_MILLIS = 250L
        const val TYPE_FIELD = "type"
        const val DOCUMENT_ID_FIELD = "documentId"
        const val REQUEST_ID_FIELD = "requestId"
        const val CALL_FIELD = "call"
        const val ALGORITHM_FIELD = "algorithm"
        const val FORMAT_FIELD = "format"
        const val ARGUMENT_LENGTHS_FIELD = "argumentLengths"
        const val BRANCH_FIELD = "branch"
        const val TYPE_MINIAPPLET_OBSERVATION = "MINIAPPLET_OBSERVATION"
        const val TYPE_RUNTIME_BRANCH_OBSERVATION = "RUNTIME_BRANCH_OBSERVATION"
        const val TYPE_MINIAPPLET_CALL_END = "MINIAPPLET_CALL_END"
        val SAFE_TOKEN = Regex("[A-Za-z0-9._+\\-]{1,64}")
        val MINIAPPLET_REQUIRED_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
            CALL_FIELD,
            ARGUMENT_LENGTHS_FIELD,
        )
        val MINIAPPLET_ALLOWED_KEY_SETS = setOf(
            MINIAPPLET_REQUIRED_KEYS,
            MINIAPPLET_REQUIRED_KEYS + ALGORITHM_FIELD,
            MINIAPPLET_REQUIRED_KEYS + FORMAT_FIELD,
            MINIAPPLET_REQUIRED_KEYS + setOf(ALGORITHM_FIELD, FORMAT_FIELD),
        )
        val BRANCH_ALLOWED_KEYS = setOf(
            TYPE_FIELD,
            DOCUMENT_ID_FIELD,
            REQUEST_ID_FIELD,
            BRANCH_FIELD,
        )
        val CALL_END_ALLOWED_KEYS = setOf(TYPE_FIELD, DOCUMENT_ID_FIELD, REQUEST_ID_FIELD)
        val NATIVE_NAVIGATION_BRANCHES = setOf(
            ObservedRuntimeBranch.AFIRMA,
            ObservedRuntimeBranch.INTENT,
        )
    }

    private data class PendingCall(
        val observation: SafeProtocolObservation,
        val openedAtMillis: Long,
        val documentId: UUID,
    )

    private data class PendingCorrelation(
        val observation: SafeProtocolObservation,
        val openedAtMillis: Long,
    )

    private sealed interface ParsedProbeMessage {
        val documentId: UUID
        val requestId: UUID
        val originHost: String

        data class Call(
            override val documentId: UUID,
            override val requestId: UUID,
            val observation: SafeProtocolObservation,
        ) : ParsedProbeMessage {
            override val originHost: String = observation.originHost
        }

        data class Branch(
            override val documentId: UUID,
            override val requestId: UUID,
            override val originHost: String,
            val branch: ObservedRuntimeBranch,
        ) : ParsedProbeMessage

        data class CallEnd(
            override val documentId: UUID,
            override val requestId: UUID,
            override val originHost: String,
        ) : ParsedProbeMessage
    }
}
