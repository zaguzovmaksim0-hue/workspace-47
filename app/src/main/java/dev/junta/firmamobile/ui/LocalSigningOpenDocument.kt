package dev.junta.firmamobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import dev.junta.firmamobile.signing.LocalFileSigningController

private const val LOCAL_SIGNING_NAME_MAX_CHARS = 160

internal object LocalSigningOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
}

internal fun localSigningName(context: Context, uri: Uri, fallback: String): String = runCatching {
    require(uri.scheme == "content")
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getString(0)?.take(LOCAL_SIGNING_NAME_MAX_CHARS)?.filter {
            !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt()
        }?.replace('/', '_')?.replace('\\', '_')?.trim()?.ifBlank { null }
    }
}.getOrNull() ?: fallback

internal fun loadLocalSigningFile(
    controller: LocalFileSigningController,
    token: Long,
    context: Context,
    uri: Uri,
    fallback: String,
) {
    controller.load(token, localSigningName(context, uri, fallback)) {
        require(uri.scheme == "content")
        checkNotNull(context.contentResolver.openInputStream(uri))
    }
}

internal fun saveLocalSigningFile(controller: LocalFileSigningController, token: Long, context: Context, uri: Uri) {
    controller.save(token) {
        require(uri.scheme == "content")
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
    }
}
