package dev.junta.firmamobile.certificate

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption

internal object BuildVariantCertificateDocumentAccessFactory {
    fun create(context: Context): CertificateDocumentAccess =
        E2eStagedCertificateDocumentAccess(
            rootDirectory = context.noBackupFilesDir.resolve(
                E2eStagedCertificateDocumentAccess.RELATIVE_DIRECTORY,
            ),
            delegate = ContentResolverCertificateDocumentAccess(context.contentResolver),
        )
}

/**
 * QA/debug-only app-private certificate staging adapter. The shell stages the PKCS#12 with run-as;
 * the control Intent carries only an opaque handle. All non-E2E URIs keep the production resolver
 * path unchanged.
 */
internal class E2eStagedCertificateDocumentAccess(
    private val rootDirectory: File,
    private val delegate: CertificateDocumentAccess,
) : CertificateDocumentAccess {
    override fun queryMetadata(uri: Uri): CertificateDocumentMetadata {
        if (!owns(uri)) return delegate.queryMetadata(uri)
        val file = requireValidFile(uri)
        return CertificateDocumentMetadata(
            displayName = CertificateRepository.DEFAULT_DISPLAY_NAME,
            mimeType = CertificateRepository.MIME_X_PKCS12,
            size = file.length(),
        )
    }

    override fun takePersistableReadPermission(uri: Uri) {
        if (!owns(uri)) {
            delegate.takePersistableReadPermission(uri)
            return
        }
        requireValidFile(uri)
    }

    override fun releasePersistableReadPermission(uri: Uri) {
        if (!owns(uri)) {
            delegate.releasePersistableReadPermission(uri)
            return
        }
        val path = stagedPath(uri) ?: return
        runCatching { Files.deleteIfExists(path) }
    }

    override fun open(uri: Uri): InputStream {
        if (!owns(uri)) return delegate.open(uri)
        return FileInputStream(requireValidFile(uri))
    }

    private fun owns(uri: Uri): Boolean =
        uri.scheme.equals("content", ignoreCase = true) && uri.authority == AUTHORITY

    private fun requireValidFile(uri: Uri): File {
        val path = stagedPath(uri) ?: throw FileNotFoundException()
        val root = rootDirectory.toPath().toAbsolutePath().normalize()
        try {
            Files.createDirectories(root)
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw FileNotFoundException()
            }
            if (path.parent != root || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw FileNotFoundException()
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw FileNotFoundException()
            }
            val size = Files.size(path)
            if (size !in 1..Pkcs12Loader.MAX_INPUT_BYTES.toLong()) {
                throw FileNotFoundException()
            }
            return path.toFile()
        } catch (error: FileNotFoundException) {
            throw error
        } catch (_: Exception) {
            throw FileNotFoundException()
        }
    }

    private fun stagedPath(uri: Uri): java.nio.file.Path? {
        if (!owns(uri) || uri.query != null || uri.fragment != null) return null
        val segments = uri.pathSegments
        if (segments.size != 1) return null
        val handle = segments.single()
        if (!HANDLE.matches(handle)) return null
        val root = rootDirectory.toPath().toAbsolutePath().normalize()
        val path = root.resolve("$handle.p12").normalize()
        return path.takeIf { it.parent == root }
    }

    companion object {
        const val RELATIVE_DIRECTORY = "e2e-control/certificates"
        const val AUTHORITY = "dev.junta.firmamobile.e2e.certificate"
        internal val HANDLE = Regex("[a-f0-9]{32}")

        fun uriForHandle(handle: String): Uri {
            require(HANDLE.matches(handle))
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(handle)
                .build()
        }
    }
}
