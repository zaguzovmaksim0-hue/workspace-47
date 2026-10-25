package dev.junta.firmamobile.ui.offlineverification

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver

/** The production picker, resolver loader, controller and verifier all participate. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class OfflineVerificationPickerDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun safSelectionIsLocalAndTemporaryAndDoesNotReadOrVerifyUntilExplicitAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = Files.createTempFile("offline-cms-synthetic-", ".bin").toFile()
        file.writeBytes(byteArrayOf(0x30, 0x00))
        val provider = SyntheticProvider(file)
        val authority = "offline-verification-fixture"
        provider.attachInfo(context, ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val registry = CapturingRegistry(context)
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        try {
            rule.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    JuntaFirmaTheme { OfflineVerificationDialog(onDismiss = {}) }
                }
            }
            rule.onNodeWithTag("offline-verification-pick-cms").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, registry.intent!!.action)
                assertTrue(registry.intent!!.hasCategory(Intent.CATEGORY_OPENABLE))
                assertTrue(registry.intent!!.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
                registry.deliver(Uri.parse("content://$authority/document/synthetic"))
            }
            rule.waitForIdle()
            assertEquals(0, provider.queries.get())
            assertEquals(0, provider.opens.get())
            assertTrue(context.contentResolver.persistedUriPermissions.isEmpty())
            rule.onNodeWithTag("offline-verification-result").assertDoesNotExist()
            rule.onNodeWithTag("offline-verification-verify").performClick()
            rule.waitUntil(timeoutMillis = 10_000) { provider.opens.get() == 1 }
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodes(androidx.compose.ui.test.hasTestTag("offline-verification-result"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(context.getString(R.string.signature_verification_invalid)).performScrollTo().assertExists()
            assertEquals(listOf(OpenableColumns.SIZE), provider.projection.get())
            assertEquals(1, provider.closedCursors.get())
            assertTrue(context.contentResolver.persistedUriPermissions.isEmpty())
        } finally {
            file.delete()
        }
    }

    private class CapturingRegistry(private val context: Context) : ActivityResultRegistry() {
        var intent: Intent? = null
        private var pendingCode: Int? = null
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            pendingCode = requestCode
            intent = contract.createIntent(context, input)
        }
        fun deliver(uri: Uri) {
            check(dispatchResult(checkNotNull(pendingCode), Activity.RESULT_OK, Intent().setData(uri)))
            pendingCode = null
        }
    }

    private class SyntheticProvider(private val file: File) : ContentProvider() {
        val queries = AtomicInteger()
        val opens = AtomicInteger()
        val closedCursors = AtomicInteger()
        val projection = AtomicReference<List<String>>()
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/octet-stream"
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queries.incrementAndGet()
            this.projection.set(projection?.toList())
            return object : MatrixCursor(arrayOf(OpenableColumns.SIZE)) {
                override fun close() { closedCursors.incrementAndGet(); super.close() }
            }.apply { addRow(arrayOf<Any>(file.length())) }
        }
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            check(mode == "r")
            opens.incrementAndGet()
            return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
