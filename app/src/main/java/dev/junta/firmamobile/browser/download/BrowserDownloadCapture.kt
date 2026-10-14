package dev.junta.firmamobile.browser.download

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.R
import dev.junta.firmamobile.browser.PublicBrowserAddress
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONArray

/** UI-thread offer handling; request evidence itself is synchronized because
 * WebView resource observation runs on an IO thread. Every rejected download
 * is explained to the user; nothing is dropped silently. */
internal class BrowserDownloadCapture(private val view: WebView) {
    private val evidence = DownloadRequestEvidence()
    private val main = Handler(Looper.getMainLooper())
    private var launcher: ((WebView, Intent) -> Boolean)? = null
    private var canRespond: () -> Boolean = { false }
    private var clientIdentity: (String) -> ClientTlsIdentity? = { null }
    private var pageScript: ScriptHandler? = null
    private var pending: PageRead? = null
    private var closed = false

    fun configure(allowed: () -> Boolean, launch: ((WebView, Intent) -> Boolean)?) {
        canRespond = allowed; launcher = launch
        view.setDownloadListener { url, userAgent, disposition, mimeType, length ->
            offer(url, userAgent, disposition, mimeType, length)
        }
        if (pageScript == null && launch != null) pageScript = runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(view, DownloadPageScript.SOURCE, setOf("*"))
            } else null
        }.getOrNull()
    }
    /** Supplies the certificate of a dedicated certificate session for its own host. */
    fun setClientIdentity(provider: (String) -> ClientTlsIdentity?) { clientIdentity = provider }
    fun observe(request: WebResourceRequest) {
        try { evidence.record(request.url.toString(), request.method) }
        catch (_: Exception) { evidence.clear() }
    }
    fun clear() { evidence.clear(); pending?.cancel(); pending = null }
    fun close() {
        closed = true; clear(); launcher = null; canRespond = { false }; clientIdentity = { null }
        runCatching { pageScript?.remove() }; pageScript = null
        view.setDownloadListener(null)
    }

    internal fun offer(url: String?, userAgent: String?, disposition: String?, mimeType: String?, length: Long): Boolean {
        if (launcher == null || closed || !view.isAttachedToWindow) return false
        if (!view.isShown || pending != null || !runCatching(canRespond).getOrDefault(false)) {
            notice(R.string.document_download_busy); return false
        }
        val raw = url ?: return false
        val page = view.url?.takeIf { PublicBrowserAddress.parse(it.substringBefore('#')) != null }
            ?: run { notice(R.string.document_download_unsupported); return false }
        val guessedName = runCatching { URLUtil.guessFileName(raw, disposition, mimeType) }.getOrNull()
        return when {
            raw.regionMatches(0, "data:", 0, 5, ignoreCase = true) -> offerData(page, raw, guessedName, mimeType, userAgent)
            raw.regionMatches(0, "blob:", 0, 5, ignoreCase = true) -> start(BlobRead(page, raw, disposition, mimeType, userAgent))
            else -> when (evidence.methodOf(raw)) {
                ObservedMethod.POST -> start(FormRead(page, raw, guessedName, mimeType, length, userAgent))
                ObservedMethod.OTHER -> { notice(R.string.document_download_unsupported); false }
                // An unobserved address is the target of a redirect, which is a GET.
                ObservedMethod.GET, null -> deliver(DocumentDownloadPlan.create(page, raw, guessedName, mimeType, length,
                    userAgent, browserCookies, null, identityFor(raw)))
            }
        }
    }

    private fun identityFor(url: String) = PublicBrowserAddress.parse(url.substringBefore('#'))?.host?.let {
        runCatching { clientIdentity(it) }.getOrNull()
    }

    private fun offerData(page: String, raw: String, guessedName: String?, mimeType: String?, userAgent: String?): Boolean {
        val document = DataUrlDocument.parse(raw) ?: run { notice(R.string.document_download_unsupported); return false }
        val staged = try {
            val file = StagedDocument.createFile(stagingDirectory())
            try {
                file.writeBytes(document.bytes)
                StagedDocument(file, document.bytes.size.toLong(), sha256(document.bytes))
            } catch (failure: Exception) { StagedDocument.abandon(file); throw failure }
        } catch (_: Exception) { notice(R.string.document_download_failed); return false }
        val type = document.mimeType ?: mimeType
        val name = runCatching { URLUtil.guessFileName("data:", null, type) }.getOrNull() ?: guessedName
        return deliver(DocumentDownloadPlan.inline(page, staged, name, type, userAgent))
    }

    private fun start(read: PageRead): Boolean {
        pending = read
        notice(R.string.document_download_preparing, Toast.LENGTH_SHORT)
        // A renderer that disappears may never answer evaluateJavascript.
        main.postDelayed({
            if (pending === read) { read.cancel(); finish(read, null, R.string.document_download_failed) }
        }, 75_000L)
        read.begin()
        return true
    }

    private fun finish(read: PageRead, plan: DocumentDownloadPlan?, failure: Int?) {
        if (pending !== read) { plan?.discard(); return }
        pending = null
        when {
            closed -> plan?.discard()
            plan != null -> {
                if (!view.isAttachedToWindow || !view.isShown || !runCatching(canRespond).getOrDefault(false)) {
                    plan.discard(); notice(R.string.document_download_busy)
                } else deliver(plan)
            }
            failure != null -> notice(failure)
        }
    }

    private fun deliver(plan: DocumentDownloadPlan?): Boolean {
        val launch = launcher
        if (plan == null || launch == null || closed) {
            plan?.discard()
            if (!closed) notice(R.string.document_download_unsupported)
            return false
        }
        val intent = DocumentDownloadActivity.prepare(view.context, plan)
            ?: run { plan.discard(); notice(R.string.document_download_busy); return false }
        val accepted = runCatching { launch(view, intent) }.getOrDefault(false)
        if (!accepted) { DocumentDownloadActivity.revoke(intent); notice(R.string.document_download_open_failed) }
        return accepted
    }

    private fun notice(message: Int, duration: Int = Toast.LENGTH_LONG) {
        runCatching { Toast.makeText(view.context, message, duration).show() }
    }

    private fun stagingDirectory() = File(view.context.cacheDir, "browser-documents").apply { mkdirs() }

    private val browserCookies = CookieLookup { url -> runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull() }

    /** Reads page state through evaluateJavascript; the main frame is the only one reachable. */
    private abstract inner class PageRead {
        protected var cancelled = false
        abstract fun begin()
        open fun cancel() { cancelled = true }
        protected fun evaluate(script: String, result: (String?) -> Unit) {
            if (cancelled || closed) return
            try {
                view.evaluateJavascript(script) { value -> if (!cancelled && !closed) result(jsString(value)) }
            } catch (_: Exception) { done(null, R.string.document_download_failed) }
        }
        protected fun done(plan: DocumentDownloadPlan?, failure: Int?) { finish(this, plan, failure) }
    }

    private inner class FormRead(
        private val page: String, private val url: String, private val name: String?,
        private val mimeType: String?, private val length: Long, private val userAgent: String?,
    ) : PageRead() {
        override fun begin() {
            evaluate("$HANDLE_CHECK ? ${DownloadPageScript.HANDLE}.form(${JSONArray(listOf(url)).toString().removeSurrounding("[", "]")}) : null") { json ->
                when (val lookup = FormSubmission.fromPageRecord(json, url)) {
                    is FormSubmission.Lookup.Found -> done(DocumentDownloadPlan.create(page, url, name, mimeType, length,
                        userAgent, browserCookies, lookup.submission, identityFor(url)), R.string.document_download_unsupported)
                    FormSubmission.Lookup.HasFiles -> done(null, R.string.document_download_form_files)
                    FormSubmission.Lookup.Missing -> done(null, R.string.document_download_form_missing)
                }
            }
        }
    }

    private inner class BlobRead(
        private val page: String, private val url: String, private val disposition: String?,
        private val mimeType: String?, private val userAgent: String?,
    ) : PageRead() {
        private val id = UUID.randomUUID().toString()
        private val quotedUrl = JSONArray(listOf(url)).toString().removeSurrounding("[", "]")
        private val writer: ExecutorService = Executors.newSingleThreadExecutor()
        private val digest = MessageDigest.getInstance("SHA-256")
        private val deadline = System.nanoTime() + 60_000_000_000L
        private var file: File? = null
        private var output: java.io.OutputStream? = null
        private var written = 0L

        override fun begin() {
            evaluate("$HANDLE_CHECK ? ${DownloadPageScript.HANDLE}.read($quotedUrl, '$id') : false") { started ->
                if (started == "true") poll() else fail(R.string.document_download_blob_unavailable)
            }
        }

        private fun poll() {
            if (System.nanoTime() - deadline > 0) { fail(R.string.document_download_failed); return }
            evaluate("$HANDLE_CHECK ? ${DownloadPageScript.HANDLE}.poll('$id', $quotedUrl) : 'missing'") { state ->
                when {
                    state == "pending" -> main.postDelayed({ if (!cancelled) poll() }, 150)
                    state == "too-large" -> fail(R.string.document_download_unsupported)
                    state != null && state.startsWith("ready:") -> ready(state)
                    else -> fail(R.string.document_download_blob_unavailable)
                }
            }
        }

        private fun ready(state: String) {
            val parts = state.split(':')
            val size = parts.getOrNull(1)?.toLongOrNull()
            val encoded = parts.getOrNull(2)?.toIntOrNull()
            if (size == null || encoded == null || size > DocumentDownloadPlan.MAX_BYTES || encoded < 0) {
                fail(R.string.document_download_unsupported); return
            }
            val type = parts.getOrNull(3)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?.takeIf { it.isNotEmpty() } ?: mimeType
            val label = parts.getOrNull(4)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }
            val name = label ?: runCatching { URLUtil.guessFileName(url, disposition, type) }.getOrNull()
            try {
                val created = StagedDocument.createFile(stagingDirectory())
                file = created; output = created.outputStream()
            } catch (_: Exception) { fail(R.string.document_download_failed); return }
            chunk(0, encoded, size, type, name)
        }

        private fun chunk(offset: Int, total: Int, size: Long, type: String?, name: String?) {
            if (offset >= total) { complete(size, type, name); return }
            if (System.nanoTime() - deadline > 0) { fail(R.string.document_download_failed); return }
            evaluate("$HANDLE_CHECK ? ${DownloadPageScript.HANDLE}.chunk('$id', $offset, $CHUNK) : null") { data ->
                if (data.isNullOrEmpty()) { fail(R.string.document_download_blob_unavailable); return@evaluate }
                onWriter {
                    val ok = try {
                        val bytes = Base64.getDecoder().decode(data)
                        if (written + bytes.size > size) throw IllegalStateException("Blob larger than announced")
                        checkNotNull(output).write(bytes); digest.update(bytes); written += bytes.size
                        true
                    } catch (_: Exception) { false }
                    main.post {
                        if (cancelled) return@post
                        if (ok) chunk(offset + data.length, total, size, type, name) else fail(R.string.document_download_failed)
                    }
                }
            }
        }

        private fun complete(size: Long, type: String?, name: String?) {
            onWriter {
                val staged = try {
                    checkNotNull(output).close(); output = null
                    if (written != size) throw IllegalStateException("Blob length mismatch")
                    StagedDocument(checkNotNull(file), written, digest.digest().joinToString("") { "%02x".format(it) })
                } catch (_: Exception) { null }
                main.post {
                    if (staged != null) file = null
                    release()
                    when {
                        staged == null -> fail(R.string.document_download_failed)
                        cancelled -> staged.close()
                        else -> done(DocumentDownloadPlan.inline(page, staged, name, type, userAgent), R.string.document_download_unsupported)
                    }
                }
            }
        }

        private fun fail(message: Int) { discardFile(); release(); done(null, message) }

        override fun cancel() { super.cancel(); discardFile(); release() }

        private fun discardFile() {
            val target = file ?: return
            file = null
            onWriter { runCatching { output?.close() }; output = null; StagedDocument.abandon(target) }
        }

        private fun release() {
            if (writer.isShutdown) return
            runCatching { view.evaluateJavascript("$HANDLE_CHECK && ${DownloadPageScript.HANDLE}.done('$id')", null) }
            writer.shutdown()
        }

        /** Keeps file writes ordered; after shutdown the remaining cleanup runs directly. */
        private fun onWriter(block: () -> Unit) {
            try { writer.execute(block) } catch (_: java.util.concurrent.RejectedExecutionException) { block() }
        }
    }

    private companion object {
        const val HANDLE_CHECK = "(typeof ${DownloadPageScript.HANDLE} === 'object')"
        /** Base64 characters per evaluateJavascript round trip; a multiple of four. */
        const val CHUNK = 512 * 1024

        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** evaluateJavascript reports a JSON value: a quoted string, null, or a literal. */
        fun jsString(value: String?): String? = runCatching {
            when (val decoded = JSONArray("[$value]").opt(0)) {
                null, org.json.JSONObject.NULL -> null
                else -> decoded.toString()
            }
        }.getOrNull()
    }
}
