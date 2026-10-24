package dev.junta.firmamobile.ui.history

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
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.history.OperationKind
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver

/** Production AppRoot, Verify controller/verifier, remembered journal, CreateDocument and resolver writer. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class VerificationJournalSafTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun realVerifyRecordsOneCoarseEventAndOnlyAnExplicitSafActionExportsItWithoutPersistence() = Fixture().use { fixture ->
        val composition = mutableIntStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides fixture.registry.owner) {
                JuntaFirmaTheme { key(composition.intValue) { AppRoot() } }
            }
        }
        // A no-start dismissal is not an operation, and the entry works without a certificate.
        scrollClick("open-offline-verification")
        click("offline-verification-close")
        scrollClick("open-verification-journal")
        rule.onNodeWithText(fixture.context.getString(R.string.verification_journal_empty)).performScrollTo().assertExists()
        assertTrue(fixture.registry.launches.isEmpty())
        assertEquals(0, fixture.provider.outputOpens.get())
        click("verification-journal-close")
        scrollClick("open-offline-verification")
        scrollClick("offline-verification-pick-cms")
        val selection = launch(fixture.registry, 1)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, selection.intent.action)
        rule.runOnIdle { assertTrue(fixture.registry.deliver(selection, fixture.inputUri)) }
        rule.waitForIdle()
        assertEquals(0, fixture.provider.inputOpens.get())
        assertEquals(0, fixture.provider.queries.get())
        click("offline-verification-verify")
        awaitTag("offline-verification-result")
        rule.onNodeWithText(fixture.context.getString(R.string.offline_verification_invalid)).performScrollTo().assertExists()
        assertEquals(1, fixture.provider.inputOpens.get())
        click("offline-verification-close")
        scrollClick("open-verification-journal")
        rule.onNodeWithTag("verification-journal-row-1").performScrollTo().assertExists()
        rule.onNodeWithTag("verification-journal-row-2").assertDoesNotExist()
        rule.onNodeWithText(fixture.context.getString(R.string.verification_journal_failed), substring = true)
            .performScrollTo().assertExists()
        assertEquals(1, fixture.registry.launches.size)
        assertEquals(0, fixture.provider.outputOpens.get())
        val queriesBeforeExport = fixture.provider.queries.get()
        scrollClick("verification-journal-export")
        val export = launch(fixture.registry, 2)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, export.intent.action)
        assertEquals("application/json", export.intent.type)
        assertEquals(JOURNAL_EXPORT_FILENAME, export.intent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(export.intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertTrue(export.intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertEquals(0, export.intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, fixture.provider.outputOpens.get())
        rule.runOnIdle { assertTrue(fixture.registry.deliver(export, fixture.outputUri)) }
        awaitText(fixture.context.getString(R.string.verification_journal_exported))
        assertEquals(1, fixture.provider.outputOpens.get())
        assertFalse(fixture.provider.outputOpenedOnMain.get())
        assertEquals(queriesBeforeExport, fixture.provider.queries.get()) // No destination/display-name query.
        assertTrue(fixture.context.contentResolver.persistedUriPermissions.isEmpty())
        val exportedText = fixture.output.readText()
        assertTrue(fixture.output.length() in 1L..16_384L)
        val json = JSONObject(exportedText)
        assertEquals(setOf("schemaVersion", "entries"), json.keys().asSequence().toSet())
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals(1, json.getJSONArray("entries").length())
        val entry = json.getJSONArray("entries").getJSONObject(0)
        assertEquals(setOf("sequence", "occurredAt", "kind", "outcome"), entry.keys().asSequence().toSet())
        assertEquals(1L, entry.getLong("sequence"))
        assertEquals("VERIFY", entry.getString("kind"))
        assertEquals("FAILED", entry.getString("outcome"))
        Instant.parse(entry.getString("occurredAt"))
        rule.onNodeWithText("PRIVATE-PROVIDER-CANARY", substring = true).assertDoesNotExist()
        click("verification-journal-close")
        scrollClick("open-verification-journal")
        rule.onNodeWithTag("verification-journal-row-1").performScrollTo().assertExists()
        click("verification-journal-close")
        rule.runOnIdle { composition.intValue++ } // A fresh AppRoot composition owns a new, empty journal.
        scrollClick("open-verification-journal")
        rule.onNodeWithText(fixture.context.getString(R.string.verification_journal_empty)).performScrollTo().assertExists()
        assertEquals(exportedText, fixture.output.readText()) // Prior external copies are not revoked.
    }

    @Test
    fun dismissedCancelledReplayedAndClearedSafResultsNeverWriteOrConsumeANewerRequest() = Fixture().use { fixture ->
        val journal = journal()
        val visible = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides fixture.registry.owner) {
                JuntaFirmaTheme {
                    verificationJournalEntry(onClick = { visible.value = true })
                    if (visible.value) verificationJournalDialog(journal, onDismiss = { visible.value = false })
                }
            }
        }
        scrollClick("verification-journal-export")
        val dismissed = launch(fixture.registry, 1)
        rule.onNodeWithTag("verification-journal-export").assertIsNotEnabled()
        click("verification-journal-close")
        rule.runOnIdle { fixture.registry.deliver(dismissed, fixture.outputUri) }
        rule.waitForIdle()
        assertEquals(0, fixture.provider.outputOpens.get())
        click("open-verification-journal")
        scrollClick("verification-journal-export")
        val cancelled = launch(fixture.registry, 2)
        rule.runOnIdle { fixture.registry.deliver(dismissed, fixture.outputUri) }
        rule.runOnIdle { assertTrue(fixture.registry.deliver(cancelled, null)) }
        awaitText(fixture.context.getString(R.string.verification_journal_export_cancelled))
        assertEquals(0, fixture.provider.outputOpens.get())
        scrollClick("verification-journal-export")
        val cleared = launch(fixture.registry, 3)
        scrollClick("verification-journal-clear")
        rule.runOnIdle { fixture.registry.deliver(cleared, fixture.outputUri) }
        rule.waitForIdle()
        assertTrue(journal.snapshot().entries.isEmpty())
        assertEquals(0, fixture.provider.outputOpens.get())
        rule.onNodeWithTag("verification-journal-export-status").assertDoesNotExist()
        assertTrue(fixture.context.contentResolver.persistedUriPermissions.isEmpty())
    }

    @Test
    fun providerFailureHasAnHonestPartialWriteWarningAndNeverDisplaysItsPrivateError() = Fixture().use { fixture ->
        fixture.provider.failOutput.set(true)
        val journal = journal()
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides fixture.registry.owner) {
                JuntaFirmaTheme { verificationJournalDialog(journal, onDismiss = {}) }
            }
        }
        scrollClick("verification-journal-export")
        val export = launch(fixture.registry, 1)
        rule.runOnIdle { assertTrue(fixture.registry.deliver(export, fixture.outputUri)) }
        awaitText(fixture.context.getString(R.string.verification_journal_export_failed))
        rule.onNodeWithText("PRIVATE-PROVIDER-CANARY", substring = true).assertDoesNotExist()
        assertEquals(1, fixture.provider.outputOpens.get())
        assertFalse(fixture.provider.outputOpenedOnMain.get())
        assertEquals(1L, fixture.output.length()) // Provider-side partial files cannot be atomically rolled back.
        assertTrue(fixture.context.contentResolver.persistedUriPermissions.isEmpty())
    }

    private fun click(tag: String) { rule.onNodeWithTag(tag).performClick() }
    private fun scrollClick(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun awaitTag(tag: String) {
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun awaitText(text: String) {
        awaitTag("verification-journal-export-status")
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text).performScrollTo().assertExists()
    }
    private fun launch(registry: CapturingRegistry, total: Int): Launch {
        rule.waitUntil(timeoutMillis = 10_000) { registry.launches.size == total }
        return registry.launches.last()
    }
    private fun journal() = InMemoryOperationJournal().apply {
        append(OperationKind.VERIFY, LocalOutcome.COMPLETED, Instant.EPOCH)
    }

    private data class Launch(val code: Int, val intent: Intent)

    private class CapturingRegistry(private val context: Context) : ActivityResultRegistry() {
        val launches = CopyOnWriteArrayList<Launch>()
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = this@CapturingRegistry
        }
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) { launches.add(Launch(requestCode, contract.createIntent(context, input))) }
        fun deliver(launch: Launch, uri: Uri?): Boolean = dispatchResult(
            launch.code, if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            uri?.let { Intent().setData(it) },
        )
    }

    private class Fixture : Closeable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        private val input = Files.createTempFile("journal-synthetic-input-", ".bin").toFile().apply {
            writeBytes(byteArrayOf(0x30, 0x00))
        }
        val output = Files.createTempFile("journal-synthetic-output-", ".json").toFile()
        val provider = SyntheticProvider(input, output)
        val registry = CapturingRegistry(context)
        val inputUri = Uri.parse("content://journal-synthetic/input")
        val outputUri = Uri.parse("content://journal-synthetic/output")
        init {
            provider.attachInfo(context, ProviderInfo().apply { authority = "journal-synthetic" })
            ShadowContentResolver.registerProviderInternal("journal-synthetic", provider)
        }
        override fun close() { input.delete(); output.delete() }
    }

    private class SyntheticProvider(private val input: File, private val output: File) : ContentProvider() {
        val inputOpens = AtomicInteger()
        val outputOpens = AtomicInteger()
        val queries = AtomicInteger()
        val outputOpenedOnMain = AtomicBoolean()
        val failOutput = AtomicBoolean()
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
            check(projection?.toList() == listOf(OpenableColumns.SIZE))
            return MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(input.length())) }
        }
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            if (mode == "r") {
                inputOpens.incrementAndGet()
                return AssetFileDescriptor(ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY), 0, input.length())
            }
            check(mode == "w")
            outputOpens.incrementAndGet()
            if (Looper.myLooper() == Looper.getMainLooper()) outputOpenedOnMain.set(true)
            if (failOutput.get()) {
                output.writeBytes(byteArrayOf(0x7b))
                throw IOException("content://private/PRIVATE-PROVIDER-CANARY.json")
            }
            val flags = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            return AssetFileDescriptor(ParcelFileDescriptor.open(output, flags), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
