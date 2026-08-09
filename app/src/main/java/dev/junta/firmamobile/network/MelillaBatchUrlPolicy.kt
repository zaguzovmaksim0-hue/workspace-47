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
 * Validates the runtime URLs owned by Melilla's AutoFirma batch wrapper.
 *
 * This policy deliberately treats operation and document identifiers as opaque
 * values. It validates their shape and their later binding, but never creates,
 * rewrites, or guesses an identifier.
 */
class MelillaBatchUrlPolicy {
    fun validate(rawUrl: String): MelillaBatchUrlValidation {
        if (rawUrl.length > MAX_URL_CHARS || rawUrl.any(Char::isISOControl)) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.TOO_LONG)
        }

        val uri = try {
            URI(rawUrl)
        } catch (_: Exception) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.MALFORMED)
        }

        if (uri.isOpaque || uri.scheme != HTTPS_SCHEME || uri.host != HOST ||
            uri.port != -1 && uri.port != HTTPS_PORT ||
            uri.userInfo != null
        ) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.ORIGIN_NOT_ALLOWED)
        }
        if (uri.rawAuthority != HOST && uri.rawAuthority != "$HOST:$HTTPS_PORT") {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.ORIGIN_NOT_ALLOWED)
        }
        if (uri.rawFragment != null) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.FRAGMENT_NOT_ALLOWED)
        }
        if (uri.rawPath != PATH) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.PATH_NOT_ALLOWED)
        }

        val rawQuery = uri.rawQuery
            ?: return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.QUERY_NOT_ALLOWED)
        if (rawQuery.length > MAX_RAW_QUERY_CHARS) {
            return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.TOO_LONG)
        }
        val query = parseQuery(rawQuery)
            ?: return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.QUERY_NOT_ALLOWED)

        val operationValue = query[OP_PARAMETER]
            ?: return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.QUERY_NOT_ALLOWED)
        val operation = when (operationValue) {
            "presign" -> MelillaBatchUrlOperation.PRESIGN
            "postsign" -> MelillaBatchUrlOperation.POSTSIGN
            "getdata" -> MelillaBatchUrlOperation.GETDATA
            else -> return MelillaBatchUrlValidation.Rejected(
                MelillaBatchUrlError.INVALID_OPERATION,
            )
        }
        val expectedKeys = if (operation == MelillaBatchUrlOperation.GETDATA) {
            setOf(OP_PARAMETER, OPERATION_ID_PARAMETER, DOCUMENT_ID_PARAMETER)
        } else {
            setOf(OP_PARAMETER, OPERATION_ID_PARAMETER)
        }
        if (query.keys != expectedKeys) {
            val error = if (query.keys.any { it !in expectedKeys }) {
                MelillaBatchUrlError.UNKNOWN_QUERY_PARAMETER
            } else {
                MelillaBatchUrlError.QUERY_NOT_ALLOWED
            }
            return MelillaBatchUrlValidation.Rejected(error)
        }

        val operationId = query[OPERATION_ID_PARAMETER]
            ?.takeIf(::isSafeIdentifier)
            ?: return MelillaBatchUrlValidation.Rejected(MelillaBatchUrlError.INVALID_IDENTIFIER)
        val documentId = if (operation == MelillaBatchUrlOperation.GETDATA) {
            query[DOCUMENT_ID_PARAMETER]
                ?.takeIf(::isSafeIdentifier)
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

    private fun parseQuery(rawQuery: String): Map<String, String>? {
        if (rawQuery.isEmpty()) return null
        val values = linkedMapOf<String, String>()
        for (pair in rawQuery.split('&', limit = -1)) {
            if (pair.isEmpty()) return null
            val separator = pair.indexOf('=')
            if (separator <= 0 || separator != pair.lastIndexOf('=')) return null
            val rawName = pair.substring(0, separator)
            val rawValue = pair.substring(separator + 1)
            val name = decodeComponent(rawName) ?: return null
            if (name != rawName) return null
            val value = decodeComponent(rawValue) ?: return null
            if (values.put(name, value) != null) return null
        }
        return values
    }

    private fun decodeComponent(raw: String): String? {
        val bytes = ByteArrayOutputStream(raw.length)
        var index = 0
        while (index < raw.length) {
            if (raw[index] != '%') {
                val encoded = raw[index].toString().encodeToByteArray()
                bytes.write(encoded, 0, encoded.size)
                index++
                continue
            }
            if (index + 2 >= raw.length) return null
            val high = raw[index + 1].hexValue() ?: return null
            val low = raw[index + 2].hexValue() ?: return null
            bytes.write((high shl 4) or low)
            index += 3
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun isSafeIdentifier(value: String): Boolean =
        value.isNotEmpty() &&
            value.length <= MAX_EPHEMERAL_VALUE_CHARS &&
            value.all { !it.isISOControl() && !it.isWhitespace() } &&
            value.none { it == '&' || it == '=' || it == '#' || it == '?' }

    private fun Char.hexValue(): Int? = when (this) {
        in '0'..'9' -> code - '0'.code
        in 'a'..'f' -> code - 'a'.code + 10
        in 'A'..'F' -> code - 'A'.code + 10
        else -> null
    }

    companion object {
        const val ORIGIN = "https://sede.melilla.es"
        const val PATH = "/sta/AutofirmaLote"
        const val MAX_URL_CHARS = 8_192
        const val MAX_RAW_QUERY_CHARS = 4_096
        const val MAX_EPHEMERAL_VALUE_CHARS = 1_024

        private const val HOST = "sede.melilla.es"
        private const val HTTPS_SCHEME = "https"
        private const val HTTPS_PORT = 443
        private const val OP_PARAMETER = "op"
        private const val OPERATION_ID_PARAMETER = "operacionId"
        private const val DOCUMENT_ID_PARAMETER = "docId"
    }
}
