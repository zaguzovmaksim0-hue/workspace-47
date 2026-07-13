package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.browser.WebBridgeMessage
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.Closeable
import java.time.Instant
import java.util.UUID

@JvmInline
value class SigningProtocolId(val value: String) {
    init {
        require(PROTOCOL_ID_PATTERN.matches(value))
    }

    private companion object {
        val PROTOCOL_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}")
    }
}

enum class SigningAlgorithm {
    SHA1_WITH_RSA,
    SHA256_WITH_RSA,
}

enum class SigningFormat {
    CADES,
}

enum class SigningErrorCode {
    INVALID_REQUEST,
    REQUEST_TOO_LARGE,
    REQUEST_EXPIRED,
    PROFILE_NOT_ACTIVE,
    ORIGIN_NOT_ALLOWED,
    NAVIGATION_CHANGED,
    PAYLOAD_CHANGED,
    CERTIFICATE_LOCKED,
    UNSUPPORTED_PROTOCOL,
    UNOBSERVED_CONTRACT,
    SESSION_EXPIRED,
    LOCAL_SIGNATURE_FAILED,
    PROTOCOL_FAILED,
    RESULT_DELIVERY_FAILED,
    USER_CANCELLED,
}

data class SigningContext(
    val profileId: String,
    val profileVersion: Int,
    val origin: TrustedOrigin,
    val navigationId: NavigationId,
    val observedAt: Instant,
)

class NormalizedSignRequest internal constructor(
    val requestId: UUID,
    val protocolId: SigningProtocolId,
    val context: SigningContext,
    val algorithm: SigningAlgorithm,
    val format: SigningFormat,
    val safeDescription: String,
    payload: ByteArray,
    private val payloadObserver: SensitiveSigningCopyObserver = SensitiveSigningCopyObserver {},
) : Closeable {
    private var ownedPayload: ByteArray? = payload

    internal val payloadSize: Int
        @Synchronized get() = requirePayload().size

    @Synchronized
    internal fun payloadCopy(): ByteArray = requirePayload().copyOf()

    @Synchronized
    internal fun <T> withPayload(block: (ByteArray) -> T): T = block(requirePayload())

    @Synchronized
    override fun close() {
        val payloadToClear = ownedPayload ?: return
        payloadToClear.fill(0)
        payloadObserver.onCleared(payloadToClear.all { it == 0.toByte() })
        ownedPayload = null
    }

    private fun requirePayload(): ByteArray =
        checkNotNull(ownedPayload) { "Signing payload is closed" }
}

sealed interface InterceptedSigningInput {
    @ConsistentCopyVisibility
    data class AfirmaUri internal constructor(
        internal val rawUri: String,
    ) : InterceptedSigningInput

    @ConsistentCopyVisibility
    data class WebMessage internal constructor(
        internal val message: WebBridgeMessage,
    ) : InterceptedSigningInput
}

sealed interface AdapterParseResult {
    data class Accepted(val request: NormalizedSignRequest) : AdapterParseResult

    data class Rejected(val code: SigningErrorCode) : AdapterParseResult
}

internal interface PreSignState : Closeable

class PreSignResult internal constructor(
    private val requestOwner: NormalizedSignRequest,
    bytesToSign: ByteArray,
    state: PreSignState,
) : Closeable {
    private var ownedBytesToSign: ByteArray? = bytesToSign
    private var ownedState: PreSignState? = state

    @Synchronized
    internal fun <T> withBytesToSign(block: (ByteArray) -> T): T =
        block(checkNotNull(ownedBytesToSign) { "Pre-sign input is closed" })

    @Synchronized
    internal fun consumeState(expectedOwner: NormalizedSignRequest): PreSignState? {
        if (requestOwner !== expectedOwner) return null
        val stateToTransfer = ownedState ?: return null
        ownedState = null
        ownedBytesToSign?.fill(0)
        ownedBytesToSign = null
        return stateToTransfer
    }

    @Synchronized
    override fun close() {
        ownedBytesToSign?.fill(0)
        ownedBytesToSign = null
        ownedState?.close()
        ownedState = null
    }
}

sealed interface ProtocolPrepareResult {
    data class Success(val preSign: PreSignResult) : ProtocolPrepareResult

    data class Failure(val code: SigningErrorCode) : ProtocolPrepareResult
}

internal fun interface SensitiveSignatureCopyObserver {
    fun onCleared(allZero: Boolean)
}

class LocalSignature internal constructor(
    bytes: ByteArray,
    private val observer: SensitiveSignatureCopyObserver = SensitiveSignatureCopyObserver {},
) : Closeable {
    private var ownedBytes: ByteArray? = bytes

    @Synchronized
    internal fun <T> withBytes(block: (ByteArray) -> T): T = block(requireBytes())

    @Synchronized
    override fun close() {
        val bytesToClear = ownedBytes ?: return
        bytesToClear.fill(0)
        observer.onCleared(bytesToClear.all { it == 0.toByte() })
        ownedBytes = null
    }

    private fun requireBytes(): ByteArray =
        checkNotNull(ownedBytes) { "Local signature is closed" }
}

interface SigningReplySink {
    val requestId: UUID

    fun success(signature: LocalSignature, certificateDer: ByteArray): Boolean

    fun failure(code: SigningErrorCode): Boolean

    fun abandon(): Boolean
}

sealed interface ProtocolCompletionResult {
    data class Success(val signature: LocalSignature) : ProtocolCompletionResult

    data class Failure(val code: SigningErrorCode) : ProtocolCompletionResult
}
