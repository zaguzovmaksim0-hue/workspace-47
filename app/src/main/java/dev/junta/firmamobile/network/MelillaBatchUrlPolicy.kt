package dev.junta.firmamobile.network

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class MelillaBatchUrlOperation {
    PRESIGN,
    POSTSIGN,
    GETDATA,
}

data class MelillaBatchUrlBinding(
    val operation: MelillaBatchUrlOperation,
    val operationId: String,
    val documentId: String? = null,
) {
    val operacionId: String get() = operationId
    val docId: String? get() = documentId
}

enum class MelillaBatchUrlError {
    TOO_LONG,
    MALFORMED,
    ORIGIN_NOT_ALLOWED,
    PATH_NOT_ALLOWED,
    FRAGMENT_NOT_ALLOWED,
    QUERY_NOT_ALLOWED,
    DUPLICATE_QUERY_PARAMETER,
    UNKNOWN_QUERY_PARAMETER,
    INVALID_OPERATION,
    INVALID_IDENTIFIER,
    BINDING_MISMATCH,
}

sealed interface MelillaBatchUrlValidation {
    data class Allowed(
        val url: URI,
        val binding: MelillaBatchUrlBinding,
    ) : MelillaBatchUrlValidation

    data class Rejected(val error: MelillaBatchUrlError) : MelillaBatchUrlValidation {
        val reason: MelillaBatchUrlError get() = error
    }
}

/**
 * Validates the runtime base URLs owned by Melilla's AutoFirma batch servlet.
 *
 * The live servlet routes operation and document identity in path segments.
 * Runtime identifiers stay opaque: this policy validates and binds them but
 * never creates, rewrites, or guesses one.
 */
class MelillaBatchUrlPolicy {
    private val delegate = StaBatchUrlPolicy(HOST)

    fun validate(rawUrl: String): MelillaBatchUrlValidation = delegate.validate(rawUrl)

    fun validate(
        rawUrl: String,
        expectedOperationId: String?,
        expectedDocumentId: String? = null,
    ): MelillaBatchUrlValidation =
        delegate.validate(rawUrl, expectedOperationId, expectedDocumentId)

    fun validate(
        rawUrl: String,
        expectedOperation: MelillaBatchUrlOperation,
        expectedOperationId: String? = null,
        expectedDocumentId: String? = null,
    ): MelillaBatchUrlValidation =
        delegate.validate(rawUrl, expectedOperation, expectedOperationId, expectedDocumentId)

    fun validatePreSignerUrl(rawUrl: String): MelillaBatchUrlBinding? =
        delegate.validatePreSignerUrl(rawUrl)

    fun validatePostSignerUrl(rawUrl: String): MelillaBatchUrlBinding? =
        delegate.validatePostSignerUrl(rawUrl)

    fun validateDataReference(
        rawUrl: String,
        expectedOperacionId: String? = null,
        expectedDocId: String? = null,
    ): MelillaBatchUrlBinding? =
        delegate.validateDataReference(rawUrl, expectedOperacionId, expectedDocId)

    companion object {
        const val ORIGIN = "https://sede.melilla.es"
        const val PATH = STA_BATCH_PATH
        const val MAX_URL_CHARS = STA_MAX_URL_CHARS
        const val MAX_RAW_QUERY_CHARS = 4_096
        const val MAX_EPHEMERAL_VALUE_CHARS = STA_MAX_EPHEMERAL_VALUE_CHARS

        private const val HOST = "sede.melilla.es"
    }
}

/**
 * Validates the same public STA batch URL grammar for the Extremadura portal,
 * while fixing ownership to its one exact HTTPS host.
 */
class ExtremaduraBatchUrlPolicy {
    private val delegate = StaBatchUrlPolicy(HOST)

    fun validate(rawUrl: String): MelillaBatchUrlValidation = delegate.validate(rawUrl)

    fun validate(
        rawUrl: String,
        expectedOperationId: String?,
        expectedDocumentId: String? = null,
    ): MelillaBatchUrlValidation =
        delegate.validate(rawUrl, expectedOperationId, expectedDocumentId)

    fun validate(
        rawUrl: String,
        expectedOperation: MelillaBatchUrlOperation,
        expectedOperationId: String? = null,
        expectedDocumentId: String? = null,
    ): MelillaBatchUrlValidation =
        delegate.validate(rawUrl, expectedOperation, expectedOperationId, expectedDocumentId)

    fun validatePreSignerUrl(rawUrl: String): MelillaBatchUrlBinding? =
        delegate.validatePreSignerUrl(rawUrl)

    fun validatePostSignerUrl(rawUrl: String): MelillaBatchUrlBinding? =
        delegate.validatePostSignerUrl(rawUrl)

    fun validateDataReference(
        rawUrl: String,
        expectedOperacionId: String? = null,
        expectedDocId: String? = null,
    ): MelillaBatchUrlBinding? =
        delegate.validateDataReference(rawUrl, expectedOperacionId, expectedDocId)

    companion object {
        const val ORIGIN = "https://tramites.juntaex.es"
        const val PATH = STA_BATCH_PATH

        private const val HOST = "tramites.juntaex.es"
    }
}

/**
 * Validates the observed public STA batch URL grammar for the Cabildo de La Palma portal,
 * while fixing ownership to its one exact HTTPS host.
 */
class LaPalmaBatchUrlPolicy {
    private val delegate = StaBatchUrlPolicy(HOST)

    fun validate(rawUrl: String): MelillaBatchUrlValidation = delegate.validate(rawUrl)

    fun validate(
        rawUrl: String,
        expectedOperationId: String?,
        expectedDocumentId: String? = null,
    ): MelillaBatchUrlValidation =
        delegate.validate(rawUrl, expectedOperationId, expectedDocumentId)

    fun validate(
        rawUrl: String,
        expectedOperation: MelillaBatchUrlOperation,
        expectedOperationId: String? = null,
        expectedDocumentId: String? = null,
    ): MelillaBatchUrlValidation =
        delegate.validate(rawUrl, expectedOperation, expectedOperationId, expectedDocumentId)

    fun validatePreSignerUrl(rawUrl: String): MelillaBatchUrlBinding? =
        delegate.validatePreSignerUrl(rawUrl)

    fun validatePostSignerUrl(rawUrl: String): MelillaBatchUrlBinding? =
        delegate.validatePostSignerUrl(rawUrl)

    fun validateDataReference(
        rawUrl: String,
        expectedOperacionId: String? = null,
        expectedDocId: String? = null,
    ): MelillaBatchUrlBinding? =
        delegate.validateDataReference(rawUrl, expectedOperacionId, expectedDocId)

    companion object {
        const val ORIGIN = "https://sedeelectronica.cabildodelapalma.es"
        const val PATH = STA_BATCH_PATH

        private const val HOST = "sedeelectronica.cabildodelapalma.es"
    }
}

private class StaBatchUrlPolicy(
    private val host: String,
) {
    fun validate(rawUrl: String): MelillaBatchUrlValidation {
        if (rawUrl.length > STA_MAX_URL_CHARS || rawUrl.any(Char::isISOControl)) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.TOO_LONG)
        }

        val uri = try {
            URI(rawUrl)
        } catch (_: Exception) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.MALFORMED)
        }

        if (uri.isOpaque || uri.scheme != HTTPS_SCHEME || uri.host != host ||
            uri.port != -1 && uri.port != HTTPS_PORT ||
            uri.userInfo != null
        ) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.ORIGIN_NOT_ALLOWED)
        }
        if (uri.rawAuthority != host && uri.rawAuthority != "$host:$HTTPS_PORT") {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.ORIGIN_NOT_ALLOWED)
        }
        if (uri.rawFragment != null) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.FRAGMENT_NOT_ALLOWED)
        }
        if (uri.rawQuery != null) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.QUERY_NOT_ALLOWED)
        }

        val rawPath = uri.rawPath
            ?: return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.PATH_NOT_ALLOWED)
        if (!rawPath.startsWith(STA_BATCH_PATH_PREFIX)) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.PATH_NOT_ALLOWED)
        }

        val relativePath = rawPath.substring(STA_BATCH_PATH_PREFIX.length)
        if (relativePath.isEmpty() || relativePath.endsWith('/')) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.PATH_NOT_ALLOWED)
        }
        val segments = relativePath.split('/')
        if (segments.any { it.isEmpty() || ';' in it }) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.PATH_NOT_ALLOWED)
        }

        val operation = when (segments.firstOrNull()) {
            "presign" -> MelillaBatchUrlOperation.PRESIGN
            "postsign" -> MelillaBatchUrlOperation.POSTSIGN
            "getdata" -> MelillaBatchUrlOperation.GETDATA
            else -> return MelillaBatchUrlValidation.Rejected(
                MelillaBatchUrlError.INVALID_OPERATION,
            )
        }
        val expectedSegmentCount = if (operation == MelillaBatchUrlOperation.GETDATA) 3 else 2
        if (segments.size != expectedSegmentCount) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.PATH_NOT_ALLOWED)
        }

        val operationId = decodeIdentifier(segments[1])
            ?: return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.INVALID_IDENTIFIER)
        val documentId = if (operation == MelillaBatchUrlOperation.GETDATA) {
            decodeIdentifier(segments[2])
                ?: return MelillaBatchUrlValidation.Rejected(
                    MelillaBatchUrlError.INVALID_IDENTIFIER,
                )
        } else {
            null
        }

        return MelillaBatchUrlValidation.Allowed(
            url = uri,
            binding = MelillaBatchUrlBinding(
                operation = operation,
                operationId = operationId,
                documentId = documentId,
            ),
        )
    }

    fun validate(
        rawUrl: String,
        expectedOperationId: String?,
        expectedDocumentId: String? = null,
    ): MelillaBatchUrlValidation {
        val result = validate(rawUrl)
        if (result !is MelillaBatchUrlValidation.Allowed) return result
        if (expectedOperationId != null && result.binding.operationId != expectedOperationId) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.BINDING_MISMATCH)
        }
        if (expectedDocumentId != null && result.binding.documentId != expectedDocumentId) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.BINDING_MISMATCH)
        }
        return result
    }

    fun validate(
        rawUrl: String,
        expectedOperation: MelillaBatchUrlOperation,
        expectedOperationId: String? = null,
        expectedDocumentId: String? = null,
    ): MelillaBatchUrlValidation {
        val result = validate(rawUrl, expectedOperationId, expectedDocumentId)
        if (result !is MelillaBatchUrlValidation.Allowed) return result
        return if (result.binding.operation == expectedOperation) {
            result
        } else {
            MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.BINDING_MISMATCH)
        }
    }

    fun validatePreSignerUrl(rawUrl: String): MelillaBatchUrlBinding? =
        (validate(rawUrl) as? MelillaBatchUrlValidation.Allowed)
            ?.takeIf { it.binding.operation == MelillaBatchUrlOperation.PRESIGN }
            ?.binding

    fun validatePostSignerUrl(rawUrl: String): MelillaBatchUrlBinding? =
        (validate(rawUrl) as? MelillaBatchUrlValidation.Allowed)
            ?.takeIf { it.binding.operation == MelillaBatchUrlOperation.POSTSIGN }
            ?.binding

    fun validateDataReference(
        rawUrl: String,
        expectedOperacionId: String? = null,
        expectedDocId: String? = null,
    ): MelillaBatchUrlBinding? =
        (validate(rawUrl, expectedOperacionId, expectedDocId)
            as? MelillaBatchUrlValidation.Allowed)
            ?.takeIf { it.binding.operation == MelillaBatchUrlOperation.GETDATA }
            ?.binding

    private fun decodeIdentifier(raw: String): String? {
        if (raw.isEmpty()) return null

        val bytes = ByteArrayOutputStream(raw.length)
        var index = 0
        var literalStart = 0
        while (index < raw.length) {
            if (raw[index] != '%') {
                index++
                continue
            }
            if (literalStart < index) {
                val literal = raw.substring(literalStart, index).encodeToByteArray()
                bytes.write(literal, 0, literal.size)
            }
            if (index + 2 >= raw.length) return null
            val high = raw[index + 1].hexValue() ?: return null
            val low = raw[index + 2].hexValue() ?: return null
            bytes.write((high shl 4) or low)
            index += 3
            literalStart = index
        }
        if (literalStart < raw.length) {
            val literal = raw.substring(literalStart).encodeToByteArray()
            bytes.write(literal, 0, literal.size)
        }

        val decoded = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString()
        } catch (_: Exception) {
            return null
        }
        return decoded.takeIf(::isSafeIdentifier)
    }

    private fun isSafeIdentifier(value: String): Boolean =
        value.isNotEmpty() &&
            value.length <= STA_MAX_EPHEMERAL_VALUE_CHARS &&
            value != "." &&
            value != ".." &&
            value.all {
                !it.isISOControl() &&
                    !it.isWhitespace() &&
                    !Character.isSpaceChar(it)
            } &&
            value.none {
                it == '/' || it == '\\' || it == '?' || it == '#' || it == ';' || it == '%'
            }

    private fun Char.hexValue(): Int? = when (this) {
        in '0'..'9' -> code - '0'.code
        in 'a'..'f' -> code - 'a'.code + 10
        in 'A'..'F' -> code - 'A'.code + 10
        else -> null
    }

    companion object {
        private const val HTTPS_SCHEME = "https"
        private const val HTTPS_PORT = 443
    }
}

private const val STA_BATCH_PATH = "/sta/AutofirmaLote"
private const val STA_BATCH_PATH_PREFIX = "$STA_BATCH_PATH/"
private const val STA_MAX_URL_CHARS = 8_192
private const val STA_MAX_EPHEMERAL_VALUE_CHARS = 1_024
