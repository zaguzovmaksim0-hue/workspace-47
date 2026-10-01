package dev.junta.firmamobile.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/** One explicitly selected upload, owned by a document, not by the whole app. */
class BrowserFileChooser(
    private val navigationEpoch: () -> Long,
    private val isCurrentView: (WebView) -> Boolean,
    private val canRead: (Uri) -> Boolean,
    private val launch: (Intent) -> Unit,
) {
    private data class Pending(
        val owner: WebView,
        val epoch: Long,
        val multiple: Boolean,
        val reply: ValueCallback<Array<Uri>>,
    )
    private var pending: Pending? = null
    private var pickerOutstanding = false

    fun open(
        view: WebView,
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        // Do not replace a live picker: its eventual Activity result must never
        // be delivered to the callback of a newer document/request.
        if (pickerOutstanding || !isCurrentView(view)) {
            callback.onReceiveValue(null)
            return true
        }
        if (params.mode != WebChromeClient.FileChooserParams.MODE_OPEN &&
            params.mode != WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        ) {
            callback.onReceiveValue(null)
            return true
        }
        val types = params.acceptTypes.orEmpty().map(String::trim)
            .filter { MIME.matches(it) }.distinct().take(16)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = types.singleOrNull() ?: "*/*"
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pending = Pending(view, navigationEpoch(), params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE, callback)
        pickerOutstanding = true
        try {
            launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            pickerOutstanding = false
            cancel()
        } catch (_: SecurityException) {
            pickerOutstanding = false
            cancel()
        }
        return true
    }

    fun deliver(resultCode: Int, data: Intent?) {
        pickerOutstanding = false
        val request = pending ?: return
        pending = null
        if (resultCode != Activity.RESULT_OK || !isCurrentView(request.owner) ||
            navigationEpoch() != request.epoch
        ) {
            request.reply.onReceiveValue(null)
            return
        }
        var invalidSelection = false
        val uris = buildList {
            data?.data?.let(::add)
            data?.clipData?.let { clip ->
                if (clip.itemCount > MAX_FILES) invalidSelection = true
                for (index in 0 until minOf(clip.itemCount, MAX_FILES)) {
                    val uri = clip.getItemAt(index).uri
                    if (uri == null) invalidSelection = true else add(uri)
                }
            }
        }.distinct()
        val valid = !invalidSelection && uris.isNotEmpty() && uris.size <= MAX_FILES &&
            (request.multiple || uris.size == 1) && uris.all { uri ->
                uri.scheme == "content" && !uri.authority.isNullOrBlank() &&
                    uri.userInfo == null && runCatching { canRead(uri) }.getOrDefault(false)
            }
        request.reply.onReceiveValue(if (valid) uris.toTypedArray() else null)
    }

    fun cancel() {
        val request = pending
        pending = null
        request?.reply?.onReceiveValue(null)
    }

    private companion object {
        const val MAX_FILES = 32
        val MIME = Regex("(?:[A-Za-z0-9!#$&^_.+-]+|\\*)/(?:[A-Za-z0-9!#$&^_.+-]+|\\*)")
    }
}
