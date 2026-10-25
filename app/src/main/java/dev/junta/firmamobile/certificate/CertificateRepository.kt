package dev.junta.firmamobile.certificate

import android.net.Uri
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    /** Saved catalogue entries; empty where the store keeps a single reference. */
    suspend fun savedEntries(): List<StoredCertificateEntry> = emptyList()

    /** Saves [uri] beside the selection without selecting, unlocking or scheduling it. */
    suspend fun saveInactive(uri: Uri): CertificateSelectionResult =
        CertificateSelectionResult.Failure(CertificateSelectionErrorCode.STORAGE_FAILURE)

    /** Removes an inactive file entry and its grant; the active entry leaves only through [forget]. */
    suspend fun removeInactive(entry: StoredCertificateEntry): Unit = error("No certificate catalogue")

    /** Makes an unchanged file [entry] active. Callers revoke the saved unlock first, as for [select]. */
    suspend fun activate(entry: StoredCertificateEntry): CertificateSelectionResult =
        CertificateSelectionResult.Failure(CertificateSelectionErrorCode.STORAGE_FAILURE)
}

class CertificateRepository(
    private val documentAccess: CertificateDocumentAccess,
    private val referenceStore: CertificateReferenceStore,
    private val loader: Pkcs12Loader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CertificateGateway {
    private val catalogStore = referenceStore as? CertificateReferenceCatalogStore
    private val fileCatalog = catalogStore?.let { CertificateFileCatalog(documentAccess, it, ioDispatcher) }

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
            val selected = reference.selectedCertificateSha256
            if (selected == null) {
                documentAccess.open(reference.uri).use { input -> loader.load(input, reference.size, password) }
            } else {
                loadBoundEntry(reference, password, selected)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CertificateLoadResult.Failure(CertificateErrorCode.DOCUMENT_UNAVAILABLE)
        }

        currentCoroutineContext().ensureActive()
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

    override suspend fun savedEntries(): List<StoredCertificateEntry> = withContext(ioDispatcher) {
        catalogStore?.readCatalog()?.entries.orEmpty()
    }

    override suspend fun saveInactive(uri: Uri): CertificateSelectionResult =
        fileCatalog?.add(uri) ?: super.saveInactive(uri)

    override suspend fun removeInactive(entry: StoredCertificateEntry) =
        checkNotNull(fileCatalog) { "No certificate catalogue" }.remove(entry)

    override suspend fun activate(entry: StoredCertificateEntry): CertificateSelectionResult =
        withContext(ioDispatcher) {
            val reference = entry.reference
            // Key-backed entries unlock through the keystore catalogue, not a file password.
            val selected = reference != null && entry.signingKey == null &&
                catalogStore?.selectEntry(entry.id, entry.revision) == true
            if (selected) CertificateSelectionResult.Success(checkNotNull(reference)) else super.activate(entry)
        }

    /** Reopens exactly the entry chosen earlier; a file without it fails instead of picking another key. */
    private suspend fun loadBoundEntry(
        reference: StoredCertificateReference,
        password: CharArray,
        certificateSha256: String,
    ): CertificateLoadResult {
        val inspected = documentAccess.open(reference.uri).use { loader.inspect(it, reference.size, password) }
        if (inspected is Pkcs12InspectionResult.Failure) return CertificateLoadResult.Failure(inspected.code)
        return (inspected as Pkcs12InspectionResult.Success).selection.use { selection ->
            val candidate = selection.candidates.singleOrNull { it.certificateSha256 == certificateSha256 }
            if (candidate == null) {
                CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)
            } else {
                documentAccess.open(reference.uri).use {
                    loader.loadSelected(it, reference.size, password, selection, candidate)
                }
            }
        }
    }

    private suspend fun selectOnIo(uri: Uri): CertificateSelectionResult {
        val inspected = documentAccess.inspectCertificate(uri)
        if (inspected !is CertificateSelectionResult.Success) return inspected
        val previous = try {
            referenceStore.read()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return CertificateSelectionResult.Failure(CertificateSelectionErrorCode.STORAGE_FAILURE)
        }
        documentAccess.grantAndPersist(uri, referenceStore) { referenceStore.write(inspected.reference) }
            ?.let { return CertificateSelectionResult.Failure(it) }

        if (previous != null && previous.uri != uri) {
            documentAccess.releaseQuietly(previous.uri)
        }
        return inspected
    }

    companion object {
        const val DEFAULT_DISPLAY_NAME = CertificateDisplayNamePolicy.DEFAULT_DISPLAY_NAME
        const val MIME_X_PKCS12 = "application/x-pkcs12"
        const val MIME_PKCS12 = "application/pkcs12"
        const val MIME_OCTET_STREAM = "application/octet-stream"
    }
}

/** SAF checks shared by selection and the catalogue; a successful result holds no grant yet. */
internal fun CertificateDocumentAccess.inspectCertificate(uri: Uri): CertificateSelectionResult {
    val metadata = try {
        if (isValidContentUri(uri)) queryMetadata(uri) else null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        return CertificateSelectionResult.Failure(CertificateSelectionErrorCode.DOCUMENT_UNAVAILABLE)
    }
    val normalizedMime = metadata?.mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
    val rawDisplayName = metadata?.displayName?.trim()
    val supported = when (normalizedMime) {
        CertificateRepository.MIME_PKCS12, CertificateRepository.MIME_X_PKCS12 -> true
        CertificateRepository.MIME_OCTET_STREAM -> rawDisplayName?.let(::hasPkcs12Extension) == true
        else -> false
    }
    val failure = when {
        metadata == null -> CertificateSelectionErrorCode.INVALID_URI
        !supported -> CertificateSelectionErrorCode.UNSUPPORTED_FILE
        metadata.size != null && metadata.size > Pkcs12Loader.MAX_INPUT_BYTES ->
            CertificateSelectionErrorCode.FILE_TOO_LARGE
        else -> null
    }
    return failure?.let(CertificateSelectionResult::Failure) ?: CertificateSelectionResult.Success(
        StoredCertificateReference(
            uri = uri,
            displayName = CertificateDisplayNamePolicy.sanitize(rawDisplayName),
            mimeType = checkNotNull(normalizedMime),
            size = metadata?.size?.takeIf { it >= 0 },
            summary = null,
        ),
    )
}

/** Takes [uri]'s read grant, then runs [persist]. A failed or cancelled [persist] releases the grant unless a
 * reference saved in [owners] still needs it. Returns the failure, or null once persisted. */
internal suspend fun CertificateDocumentAccess.grantAndPersist(
    uri: Uri,
    owners: CertificateReferenceStore,
    persist: suspend () -> Unit,
): CertificateSelectionErrorCode? {
    try {
        takePersistableReadPermission(uri)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        return CertificateSelectionErrorCode.PERMISSION_DENIED
    }
    return try {
        persist()
        null
    } catch (cancellation: CancellationException) {
        withContext(NonCancellable) { releaseUnlessReferenced(uri, owners) }
        throw cancellation
    } catch (_: Exception) {
        releaseUnlessReferenced(uri, owners)
        CertificateSelectionErrorCode.STORAGE_FAILURE
    }
}

// Another saved entry, or a write that committed before cancellation, may still need this grant.
internal suspend fun CertificateDocumentAccess.releaseUnlessReferenced(uri: Uri, owners: CertificateReferenceStore) {
    val referenced = try {
        owners.references(uri)
    } catch (_: Exception) {
        true // Unknown ownership keeps the grant; a stale grant is non-secret state.
    }
    if (!referenced) releaseQuietly(uri)
}

internal fun CertificateDocumentAccess.releaseQuietly(uri: Uri) {
    try {
        releasePersistableReadPermission(uri)
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

private const val MAX_URI_LENGTH = 4096
