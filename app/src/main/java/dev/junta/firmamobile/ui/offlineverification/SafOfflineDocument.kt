package dev.junta.firmamobile.ui.offlineverification

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

/** Reads SIZE only, never a display name. Used exclusively on the worker dispatcher. */
internal class SafOfflineDocument(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : OfflineDocumentSource {
    override fun declaredSize(): Long? {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        return resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column) else null
        }
    }

    override fun open(): OfflineOpenedDocument {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val descriptor = resolver.openAssetFileDescriptor(uri, "r") ?: throw IOException()
        var transferred = false
        return try {
            val size = descriptor.length
            // The asset's AutoCloseInputStream owns and closes its ParcelFileDescriptor.
            val document = OfflineOpenedDocument(size, descriptor.createInputStream())
            transferred = true
            document
        } finally {
            if (!transferred) descriptor.close()
        }
    }
}
