package dev.junta.firmamobile.browser.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Non-exported activity. Only an in-process random ticket is passed in its
 * Intent; URLs, cookies and response bytes never enter saved-instance extras. */
class DocumentDownloadActivity : ComponentActivity() {
    private lateinit var model: DocumentDownloadModel
    private val destination = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri?.scheme == "content") {
            model.save(uri, contentResolver)
        } else model.pickerCancelled()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DocumentDownloadModel(
                intent.getStringExtra(TICKET)?.let(DocumentDownloadTickets.store::take),
                File(cacheDir, "browser-documents"),
            ) as T
        })[DocumentDownloadModel::class.java]
        setContent {
            dev.junta.firmamobile.ui.theme.JuntaFirmaTheme {
                dev.junta.firmamobile.ui.FilterObscuredTouches()
                DocumentDownloadScreen(model.plan, model.state, model.received, model.problem,
                    onStart = model::start,
                    onSave = {
                        if (model.beginPicker()) try {
                            destination.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = model.plan?.mimeType ?: "application/octet-stream"
                                putExtra(Intent.EXTRA_TITLE, model.plan?.fileName ?: "documento.bin")
                            })
                        } catch (_: Exception) { model.pickerCancelled() }
                    },
                    onClose = { model.cancel(); finish() },
                )
            }
        }
    }
    companion object {
        private const val TICKET = "download-ticket"
        internal fun prepare(context: Context, plan: DocumentDownloadPlan): Intent? {
            val ticket = DocumentDownloadTickets.store.offer(plan) ?: return null
            return Intent(context, DocumentDownloadActivity::class.java).putExtra(TICKET, ticket)
        }
        internal fun revoke(intent: Intent) { intent.getStringExtra(TICKET)?.let(DocumentDownloadTickets.store::revoke) }
    }
}

internal enum class DocumentDownloadState { OFFER, FETCHING, READY, PICKING, COPYING, SAVED, SAVE_FAILED, FAILED, EXPIRED }

/** Rotation retains only in-process state. A killed process cannot silently
 * reconstruct an authenticated GET; the user must generate a fresh download. */
internal class DocumentDownloadModel(
    val plan: DocumentDownloadPlan?,
    private val directory: File,
    private val fetch: suspend (DocumentDownloadPlan, File, (Long) -> Unit) -> StagedDocument = { p, dir, progress ->
        DocumentDownloadTransport().fetch(p, dir, progress)
    },
) : ViewModel() {
    // A document the page already held needs no request: it is ready to save.
    var state by mutableStateOf(when {
        plan == null -> DocumentDownloadState.EXPIRED
        plan.isInline -> DocumentDownloadState.READY
        else -> DocumentDownloadState.OFFER
    })
        private set
    var received by mutableStateOf(plan?.inline?.bytes ?: 0L)
        private set
    var problem by mutableStateOf<DocumentDownloadProblem?>(null)
        private set
    private var staged: StagedDocument? = plan?.inline
    private var job: Job? = null

    fun start() {
        val request = plan ?: return
        if (state != DocumentDownloadState.OFFER) return
        state = DocumentDownloadState.FETCHING
        job = viewModelScope.launch {
            try {
                // Only our private prefix is eligible for recovery cleanup.
                withContext(Dispatchers.IO) {
                    StagedDocument.pruneStale(directory, System.currentTimeMillis())
                }
                var lastPublished = 0L
                val result = fetch(request, directory) { count ->
                    val now = System.nanoTime()
                    if (now - lastPublished >= 100_000_000L) {
                        lastPublished = now
                        viewModelScope.launch { if (state == DocumentDownloadState.FETCHING) received = count }
                    }
                }
                if (state != DocumentDownloadState.FETCHING) { result.close(); return@launch }
                staged = result; received = result.bytes; state = DocumentDownloadState.READY
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                problem = (failure as? DocumentDownloadException)?.problem ?: DocumentDownloadProblem.NETWORK
                state = DocumentDownloadState.FAILED
            } finally {
                // A form body is sent at most once; clear it from memory now.
                request.form?.close()
            }
        }
    }
    fun beginPicker(): Boolean {
        if (state !in setOf(DocumentDownloadState.READY, DocumentDownloadState.SAVE_FAILED) || staged == null) return false
        state = DocumentDownloadState.PICKING
        return true
    }
    fun pickerCancelled() { if (state == DocumentDownloadState.PICKING) state = DocumentDownloadState.READY }
    fun save(uri: Uri, resolver: android.content.ContentResolver) {
        val source = staged ?: return
        if (state != DocumentDownloadState.PICKING) return
        if (uri.scheme != "content") { state = DocumentDownloadState.SAVE_FAILED; return }
        state = DocumentDownloadState.COPYING
        job = viewModelScope.launch {
            val exportingJob = coroutineContext[Job]!!
            var committed = false
            var recoverableFailure = false
            try {
                val receipt = withContext(Dispatchers.IO) {
                    source.file.inputStream().use { input ->
                        (resolver.openOutputStream(uri, "w") ?: throw java.io.IOException("Destination unavailable")).use { output ->
                            BoundedDocumentCopy.copy(input, output, DocumentDownloadPlan.MAX_BYTES, source.bytes,
                                active = { exportingJob.isActive })
                        }
                    }
                }
                check(receipt.bytes == source.bytes && receipt.sha256 == source.sha256)
                committed = true
                source.close(); staged = null; state = DocumentDownloadState.SAVED
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { recoverableFailure = true }
            finally {
                if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                    // This URI was returned by CREATE_DOCUMENT, not an existing
                    // file supplied by a page. Remove only that partial output.
                    runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                }
                // Do not expose retry while deletion of the earlier destination
                // is still running: a provider may reuse a just-created URI.
                if (recoverableFailure && state == DocumentDownloadState.COPYING) {
                    state = DocumentDownloadState.SAVE_FAILED
                }
            }
        }
    }
    fun cancel() {
        state = DocumentDownloadState.EXPIRED
        job?.cancel(); staged?.close(); staged = null
        plan?.discard()
    }
    override fun onCleared() { cancel() }
}
