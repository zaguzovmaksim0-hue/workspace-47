package dev.junta.firmamobile.certificate

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class E2eStagedCertificateDocumentAccessTest {
    @Test
    fun `staged handle is app private bounded and released by repository lifecycle`() {
        val root = Files.createTempDirectory("jfm-e2e-certificate").toFile()
        val delegate = RecordingDocumentAccess()
        val access = E2eStagedCertificateDocumentAccess(root, delegate)
        val handle = "0123456789abcdef0123456789abcdef"
        val staged = root.resolve("$handle.p12")
        staged.writeBytes(ByteArray(128) { it.toByte() })
        val uri = E2eStagedCertificateDocumentAccess.uriForHandle(handle)

        val metadata = access.queryMetadata(uri)
        assertEquals(CertificateRepository.DEFAULT_DISPLAY_NAME, metadata.displayName)
        assertEquals(CertificateRepository.MIME_X_PKCS12, metadata.mimeType)
        assertEquals(128L, metadata.size)
        access.takePersistableReadPermission(uri)
        assertEquals(128, access.open(uri).use(InputStream::readBytes).size)
        assertEquals(0, delegate.calls)

        access.releasePersistableReadPermission(uri)
        assertFalse(staged.exists())
        root.deleteRecursively()
    }

    @Test
    fun `invalid staged handles and oversized files fail closed while normal URIs delegate`() {
        val root = Files.createTempDirectory("jfm-e2e-certificate-invalid").toFile()
        val delegate = RecordingDocumentAccess()
        val access = E2eStagedCertificateDocumentAccess(root, delegate)

        val external = Uri.parse("content://com.android.externalstorage.documents/document/fixture")
        assertEquals("delegate.p12", access.queryMetadata(external).displayName)
        assertEquals(1, delegate.calls)

        val invalid = Uri.parse("content://${E2eStagedCertificateDocumentAccess.AUTHORITY}/not-a-handle")
        assertFileNotFound { access.queryMetadata(invalid) }

        val handle = "abcdef0123456789abcdef0123456789"
        val oversized = root.resolve("$handle.p12")
        RandomAccessFile(oversized, "rw").use {
            it.setLength(Pkcs12Loader.MAX_INPUT_BYTES.toLong() + 1L)
        }
        assertFileNotFound {
            access.queryMetadata(E2eStagedCertificateDocumentAccess.uriForHandle(handle))
        }
        assertTrue(oversized.exists())
        root.deleteRecursively()
    }

    private fun assertFileNotFound(block: () -> Unit) {
        var failedClosed = false
        try {
            block()
        } catch (_: FileNotFoundException) {
            failedClosed = true
        }
        assertTrue(failedClosed)
    }

    private class RecordingDocumentAccess : CertificateDocumentAccess {
        var calls = 0

        override fun queryMetadata(uri: Uri): CertificateDocumentMetadata {
            calls++
            return CertificateDocumentMetadata("delegate.p12", CertificateRepository.MIME_X_PKCS12, 1L)
        }

        override fun takePersistableReadPermission(uri: Uri) {
            calls++
        }

        override fun releasePersistableReadPermission(uri: Uri) {
            calls++
        }

        override fun open(uri: Uri): InputStream {
            calls++
            return ByteArrayInputStream(byteArrayOf(1))
        }
    }
}
