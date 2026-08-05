package dev.junta.firmamobile.certificate

import android.net.Uri
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CertificateSelectionErrorCode {
    INVALID_URI,
    DOCUMENT_UNAVAILABLE,
    UNSUPPORTED_FILE,
    FILE_TOO_LARGE,
    PERMISSION_DENIED,
    STORAGE_FAILURE,
}

sealed interface CertificateSelectionResult {
    data class Success(val reference: StoredCertificateReference) : CertificateSelectionResult

    data class Failure(val code: CertificateSelectionErrorCode) : CertificateSelectionResult
}

interface CertificateGateway {
    suspend fun currentReference(): StoredCertificateReference?

    suspend fun select(uri: Uri): CertificateSelectionResult

    suspend fun unlock(password: CharArray): CertificateLoadResult

    suspend fun forget()
}

class CertificateRepository(
    private val documentAccess: CertificateDocumentAccess,
    private val referenceStore: CertificateReferenceStore,
    private val loader: Pkcs12Loader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CertificateGateway {
    override suspend fun currentReference(): StoredCertificateReference? = withContext(ioDispatcher) {
        referenceStore.read()
    }

    override suspend fun select(uri: Uri): CertificateSelectionResult = withContext(ioDispatcher) {
        selectOnIo(uri)
    }

    override suspend fun unlock(password: CharArray): CertificateLoadResult = withContext(ioDispatcher) {
        val reference = try {
            referenceStore.read()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return@withContext CertificateLoadResult.Failure(
                CertificateErrorCode.REFERENCE_STORAGE_FAILURE,
            )
        } ?: return@withContext CertificateLoadResult.Failure(
            CertificateErrorCode.NO_CERTIFICATE_SELECTED,
        )
        if (!isValidContentUri(reference.uri)) {
            return@withContext CertificateLoadResult.Failure(
                CertificateErrorCode.DOCUMENT_UNAVAILABLE,
            )
        }

        val result = try {
            documentAccess.open(reference.uri).use { input ->
                loader.load(input, reference.size, password)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CertificateLoadResult.Failure(CertificateErrorCode.DOCUMENT_UNAVAILABLE)
        }

        if (result is CertificateLoadResult.Success) {
            try {
                referenceStore.write(reference.copy(summary = result.identity.summary))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return@withContext CertificateLoadResult.Failure(
                    CertificateErrorCode.REFERENCE_STORAGE_FAILURE,
                )
            }
        }
        result
    }

    override suspend fun forget() = withContext(ioDispatcher) {
        val reference = referenceStore.read()
        referenceStore.clear()
        if (reference != null) {
            try {
                documentAccess.releasePersistableReadPermission(reference.uri)
            } catch (_: Exception) {
                // The reference is already removed; a stale provider grant is non-secret state.
            }
        }
    }

    private suspend fun selectOnIo(uri: Uri): CertificateSelectionResult {
        if (!isValidContentUri(uri)) {
            return CertificateSelectionResult.Failure(CertificateSelectionErrorCode.INVALID_URI)
        }
        val metadata = try {
            documentAccess.queryMetadata(uri)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return CertificateSelectionResult.Failure(
                CertificateSelectionErrorCode.DOCUMENT_UNAVAILABLE,
            )
        }
        val normalizedMime = metadata.mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
        val rawDisplayName = metadata.displayName?.trim()
        val hasPkcs12Extension = rawDisplayName?.let(::hasPkcs12Extension) == true
        val supported = when (normalizedMime) {
            MIME_PKCS12, MIME_X_PKCS12 -> true
            MIME_OCTET_STREAM -> hasPkcs12Extension
            else -> false
        }
        if (!supported) {
            return CertificateSelectionResult.Failure(
                CertificateSelectionErrorCode.UNSUPPORTED_FILE,
            )
        }
        if (metadata.size != null && metadata.size > Pkcs12Loader.MAX_INPUT_BYTES) {
            return CertificateSelectionResult.Failure(CertificateSelectionErrorCode.FILE_TOO_LARGE)
        }

        val previous = try {
            referenceStore.read()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return CertificateSelectionResult.Failure(CertificateSelectionErrorCode.STORAGE_FAILURE)
        }
        try {
            documentAccess.takePersistableReadPermission(uri)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return CertificateSelectionResult.Failure(
                CertificateSelectionErrorCode.PERMISSION_DENIED,
            )
        }

        val reference = StoredCertificateReference(
            uri = uri,
            displayName = sanitizeDisplayName(rawDisplayName),
            mimeType = checkNotNull(normalizedMime),
            size = metadata.size?.takeIf { it >= 0 },
            summary = null,
        )
        try {
            referenceStore.write(reference)
        } catch (cancellation: CancellationException) {
            if (previous?.uri != uri) {
                releaseQuietly(uri)
            }
            throw cancellation
        } catch (_: Exception) {
            if (previous?.uri != uri) {
                releaseQuietly(uri)
            }
            return CertificateSelectionResult.Failure(CertificateSelectionErrorCode.STORAGE_FAILURE)
        }

        if (previous != null && previous.uri != uri) {
            releaseQuietly(previous.uri)
        }
        return CertificateSelectionResult.Success(reference)
    }

    private fun releaseQuietly(uri: Uri) {
        try {
            documentAccess.releasePersistableReadPermission(uri)
        } catch (_: Exception) {
            // Best effort; persisted app state never points at this URI after rollback/replace.
        }
    }

    private fun isValidContentUri(uri: Uri): Boolean =
        uri.scheme.equals("content", ignoreCase = true) &&
            !uri.authority.isNullOrBlank() &&
            uri.fragment == null &&
            uri.toString().length <= MAX_URI_LENGTH

    private fun hasPkcs12Extension(displayName: String): Boolean {
        val normalized = displayName.lowercase(Locale.ROOT)
        return normalized.endsWith(".p12") || normalized.endsWith(".pfx")
    }

    private fun sanitizeDisplayName(displayName: String?): String {
        val sanitized = displayName
            ?.filter { character -> character >= ' ' && character != '\u007f' }
            ?.take(MAX_DISPLAY_NAME_LENGTH)
            ?.trim()
        return sanitized.orEmpty().ifBlank { DEFAULT_DISPLAY_NAME }
    }

    companion object {
        const val DEFAULT_DISPLAY_NAME = "Certificado seleccionado"
        const val MIME_X_PKCS12 = "application/x-pkcs12"
        const val MIME_PKCS12 = "application/pkcs12"
        const val MIME_OCTET_STREAM = "application/octet-stream"
        private const val MAX_DISPLAY_NAME_LENGTH = 256
        private const val MAX_URI_LENGTH = 4096
    }
}
