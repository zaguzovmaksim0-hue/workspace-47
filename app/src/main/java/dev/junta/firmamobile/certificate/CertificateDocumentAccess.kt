package dev.junta.firmamobile.certificate

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

data class CertificateDocumentMetadata(
    val displayName: String?,
    val mimeType: String?,
    val size: Long?,
)

interface CertificateDocumentAccess {
    fun queryMetadata(uri: Uri): CertificateDocumentMetadata

    fun takePersistableReadPermission(uri: Uri)

    fun releasePersistableReadPermission(uri: Uri)

    fun open(uri: Uri): InputStream
}

class ContentResolverCertificateDocumentAccess(
    private val contentResolver: ContentResolver,
) : CertificateDocumentAccess {
    override fun queryMetadata(uri: Uri): CertificateDocumentMetadata {
        var displayName: String? = null
        var size: Long? = null
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex).takeIf { it >= 0 }?.let { size = it }
                }
            }
        }
        return CertificateDocumentMetadata(
            displayName = displayName,
            mimeType = contentResolver.getType(uri),
            size = size,
        )
    }

    override fun takePersistableReadPermission(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    override fun releasePersistableReadPermission(uri: Uri) {
        contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    override fun open(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
}
