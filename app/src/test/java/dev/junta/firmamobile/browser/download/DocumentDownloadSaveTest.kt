package dev.junta.firmamobile.browser.download

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentDownloadSaveTest {
    @Test fun confirmedCopyUsesTheChosenProviderAndDeletesPrivateStaging() = fixture { f ->
        assertEquals(0, f.fetches)
        f.model.start(); f.waitState(DocumentDownloadState.READY)
        assertEquals(1, f.fetches); assertTrue(f.source.exists())
        assertTrue(f.model.beginPicker()); f.model.save(f.uri, f.context.contentResolver)
        f.waitState(DocumentDownloadState.SAVED)
        assertArrayEquals(f.bytes, f.target.readBytes()); assertFalse(f.source.exists())
        f.model.start(); assertEquals(1, f.fetches)
    }
    @Test fun pickerCancellationCanResumeSavingWithoutAnotherGet() = fixture { f ->
        f.model.start(); f.waitState(DocumentDownloadState.READY)
        assertTrue(f.model.beginPicker()); f.model.pickerCancelled()
        assertEquals(DocumentDownloadState.READY, f.model.state); assertTrue(f.source.exists())
        assertTrue(f.model.beginPicker()); f.model.save(f.uri, f.context.contentResolver)
        f.waitState(DocumentDownloadState.SAVED); assertEquals(1, f.fetches)
    }
    @Test fun failedDestinationCanBeChangedWithoutRedownloading() = fixture { f ->
        f.model.start(); f.waitState(DocumentDownloadState.READY)
        f.provider.rejectWrite = true
        f.model.beginPicker(); f.model.save(f.uri, f.context.contentResolver)
        f.waitState(DocumentDownloadState.SAVE_FAILED); assertTrue(f.source.exists())
        f.provider.rejectWrite = false
        assertTrue(f.model.beginPicker()); f.model.save(f.uri, f.context.contentResolver)
        f.waitState(DocumentDownloadState.SAVED)
        assertEquals(1, f.fetches); assertArrayEquals(f.bytes, f.target.readBytes())
    }
    @Test fun expiredOfferNeverDownloadsAndCancellationDeletesStagedBytes() = fixture { f ->
        val expired = DocumentDownloadModel(null, f.directory) { _, _, _ -> error("No expired download") }
        expired.start(); assertEquals(DocumentDownloadState.EXPIRED, expired.state)
        f.model.start(); f.waitState(DocumentDownloadState.READY)
        f.model.cancel(); assertFalse(f.source.exists()); assertEquals(DocumentDownloadState.EXPIRED, f.model.state)
    }
    private class Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = Files.createTempDirectory("download-save-").toFile()
        val bytes = "%PDF-synthetic export".toByteArray()
        val source = File(directory, "prepared.part").apply { writeBytes(bytes) }
        val target = File(directory, "export.pdf")
        val provider = Provider(target)
        val uri = Uri.parse("content://download-save-fixture/document/new")
        var fetches = 0
        val plan = checkNotNull(DocumentDownloadPlan.create("https://docs.example/page", "https://docs.example/file", "document.pdf", "application/pdf", -1, "test", null))
        val model = DocumentDownloadModel(plan, directory) { _, _, _ ->
            fetches++
            StagedDocument(source, bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
        init {
            provider.attachInfo(context, ProviderInfo().apply { authority = "download-save-fixture" })
            ShadowContentResolver.registerProviderInternal("download-save-fixture", provider)
        }
        fun waitState(expected: DocumentDownloadState) {
            val until = System.nanoTime() + 5_000_000_000L
            while (model.state != expected && System.nanoTime() < until) Thread.sleep(10)
            assertEquals(expected, model.state)
        }
    }
    private class Provider(private val file: File) : ContentProvider() {
        var rejectWrite = false
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/pdf"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = if (file.delete()) 1 else 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (rejectWrite) throw java.io.FileNotFoundException("Synthetic provider unavailable")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            if (method == "android:deleteDocument") file.delete()
            return Bundle()
        }
    }
    private fun fixture(block: (Fixture) -> Unit) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val f = Fixture()
        try { block(f) } finally { f.model.cancel(); f.directory.deleteRecursively(); Dispatchers.resetMain() }
    }
}
