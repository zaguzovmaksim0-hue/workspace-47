package dev.junta.firmamobile.signing

import java.io.Closeable
import java.util.UUID

/** Formats observed in AutoFirma JSON batch contracts. */
enum class BatchSigningFormat {
    CADES,
    PADES,
    XADES,
}

data class NormalizedBatchSigningDocument(
    val id: String,
    val dataReference: String,
    val format: BatchSigningFormat,
    val suboperation: String? = null,
) {
    init {
        require(id.isSafeBatchValue())
        require(dataReference.isNotEmpty() && dataReference.length <= MAX_BATCH_URL_CHARS)
        require(suboperation == null || suboperation.isSafeBatchValue())
    }
}

class NormalizedBatchSigningRequest internal constructor(
    val requestId: UUID,
    val protocolId: SigningProtocolId,
    val context: SigningContext,
    val algorithm: SigningAlgorithm,
    val format: BatchSigningFormat,
    val suboperation: String,
    val stopOnError: Boolean,
    val operationId: String,
    val preSignerUrl: String,
    val postSignerUrl: String,
    documents: List<NormalizedBatchSigningDocument>,
) : Closeable {
    val documents: List<NormalizedBatchSigningDocument> = documents.toList()
    private var open = true

    init {
        require(suboperation.isSafeBatchValue())
        require(operationId.isSafeBatchValue())
        require(preSignerUrl.isNotEmpty() && preSignerUrl.length <= MAX_BATCH_URL_CHARS)
        require(postSignerUrl.isNotEmpty() && postSignerUrl.length <= MAX_BATCH_URL_CHARS)
        require(this.documents.isNotEmpty() && this.documents.size <= MAX_BATCH_DOCUMENTS)
        require(this.documents.map { it.id }.toSet().size == this.documents.size)
    }

    @Synchronized
    internal fun isOpen(): Boolean = open

    @Synchronized
    override fun close() {
        open = false
    }
}

internal interface BatchPreSignState : Closeable

class BatchPreSignResult internal constructor(
    private val requestOwner: NormalizedBatchSigningRequest,
    inputs: List<ByteArray>,
    state: BatchPreSignState,
) : Closeable {
    private var ownedInputs: MutableList<ByteArray>? = inputs.mapTo(ArrayList(inputs.size)) { it.copyOf() }
    private var ownedState: BatchPreSignState? = state

    val inputCount: Int
        @Synchronized get() = ownedInputs?.size ?: 0

    @Synchronized
    fun <T> withInput(index: Int, block: (ByteArray) -> T): T {
        val inputs = checkNotNull(ownedInputs) { "Batch pre-sign input is closed" }
        return block(inputs[index])
    }

    @Synchronized
    internal fun isOwnedBy(expectedOwner: NormalizedBatchSigningRequest): Boolean =
        requestOwner === expectedOwner

    @Synchronized
    internal fun consumeState(
        expectedOwner: NormalizedBatchSigningRequest,
        expectedInputCount: Int,
    ): BatchPreSignState? {
        if (requestOwner !== expectedOwner) return null
        val inputs = ownedInputs ?: return null
        if (inputs.size != expectedInputCount) return null
        val state = ownedState ?: return null
        ownedState = null
        inputs.forEach { it.fill(0) }
        ownedInputs = null
        return state
    }

    @Synchronized
    override fun close() {
        ownedInputs?.forEach { it.fill(0) }
        ownedInputs = null
        ownedState?.close()
        ownedState = null
    }
}

sealed interface BatchProtocolPrepareResult {
    data class Success(val preSign: BatchPreSignResult) : BatchProtocolPrepareResult

    data class Failure(val code: SigningErrorCode) : BatchProtocolPrepareResult
}

class BatchProtocolResponse internal constructor(
    bytes: ByteArray,
) : Closeable {
    private var ownedBytes: ByteArray? = bytes

    @Synchronized
    fun <T> withBytes(block: (ByteArray) -> T): T =
        block(checkNotNull(ownedBytes) { "Batch protocol response is closed" })

    @Synchronized
    override fun close() {
        ownedBytes?.fill(0)
        ownedBytes = null
    }
}

sealed interface BatchProtocolCompletionResult {
    data class Success(val response: BatchProtocolResponse) : BatchProtocolCompletionResult

    data class Failure(val code: SigningErrorCode) : BatchProtocolCompletionResult
}

interface BatchSigningReplySink {
    val requestId: UUID

    fun success(response: BatchProtocolResponse): Boolean

    fun failure(code: SigningErrorCode): Boolean

    fun abandon(): Boolean
}

private fun String.isSafeBatchValue(): Boolean =
    isNotEmpty() &&
        length <= MAX_BATCH_VALUE_CHARS &&
        all { !it.isISOControl() && !it.isWhitespace() }

internal const val MAX_BATCH_DOCUMENTS = 128
internal const val MAX_BATCH_URL_CHARS = 8_192
private const val MAX_BATCH_VALUE_CHARS = 1_024
