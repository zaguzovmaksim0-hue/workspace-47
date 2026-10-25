package dev.junta.firmamobile.certificate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Choosing one private-key entry of a multi-key file. */
interface CertificateEntryChooser {
    /** Private-key entries of the selected file; empty when it cannot be listed. Nothing is unlocked or saved. */
    suspend fun certificateChoices(password: CharArray): List<CertificateEntryChoice> = emptyList()

    /** Unlocks the entry with [certificateSha256] and, on success only, binds the saved reference to it. */
    suspend fun unlockChosen(password: CharArray, certificateSha256: String): CertificateLoadResult =
        CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)
}

internal class FileCertificateEntryChooser(
    private val documentAccess: CertificateDocumentAccess,
    private val referenceStore: CertificateReferenceStore,
    private val loader: Pkcs12Loader,
    private val ioDispatcher: CoroutineDispatcher,
) : CertificateEntryChooser {
    override suspend fun certificateChoices(password: CharArray): List<CertificateEntryChoice> =
        withContext(ioDispatcher) {
            try {
                referenceStore.read()?.takeIf { isValidContentUri(it.uri) }
                    ?.let { documentAccess.listChoices(loader, it, password) }.orEmpty()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun unlockChosen(password: CharArray, certificateSha256: String): CertificateLoadResult =
        referenceStore.unlockSaved(ioDispatcher, documentAccess, loader, password) {
            it.copy(selectedCertificateSha256 = certificateSha256)
        }
}

/** Unlocks the saved reference after [bind]; only a successful unlock writes the bound reference back. */
internal suspend fun CertificateReferenceStore.unlockSaved(
    dispatcher: CoroutineDispatcher,
    documentAccess: CertificateDocumentAccess,
    loader: Pkcs12Loader,
    password: CharArray,
    bind: (StoredCertificateReference) -> StoredCertificateReference,
): CertificateLoadResult = withContext(dispatcher) {
    val reference = try {
        read()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        return@withContext CertificateLoadResult.Failure(
            CertificateErrorCode.REFERENCE_STORAGE_FAILURE,
        )
    }?.let(bind) ?: return@withContext CertificateLoadResult.Failure(
        CertificateErrorCode.NO_CERTIFICATE_SELECTED,
    )
    if (!isValidContentUri(reference.uri)) {
        return@withContext CertificateLoadResult.Failure(
            CertificateErrorCode.DOCUMENT_UNAVAILABLE,
        )
    }

    val result = try {
        documentAccess.loadReferenced(loader, reference, password)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CertificateLoadResult.Failure(CertificateErrorCode.DOCUMENT_UNAVAILABLE)
    }

    currentCoroutineContext().ensureActive()
    if (result is CertificateLoadResult.Success) {
        try {
            write(reference.copy(summary = result.identity.summary))
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

/** Display data for every private-key entry; an unreadable file has no choices. */
private suspend fun CertificateDocumentAccess.listChoices(
    loader: Pkcs12Loader,
    reference: StoredCertificateReference,
    password: CharArray,
): List<CertificateEntryChoice> {
    val inspected = open(reference.uri).use { loader.inspect(it, reference.size, password) }
        as? Pkcs12InspectionResult.Success ?: return emptyList()
    return inspected.selection.use { selection ->
        selection.candidates.map { CertificateEntryChoice(it.number, it.summary, it.certificateSha256) }
    }
}
