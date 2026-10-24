package dev.junta.firmamobile.ui.history

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext

internal const val JOURNAL_EXPORT_FILENAME = "local-verification-journal.json"

internal class LocalJournalCreateDocument : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

/** Registered only after an explicit click, with an immutable per-request ticket and a fresh registry key. */
@Composable
internal fun journalCreateDocumentPicker(controller: VerificationJournalController, ticket: JournalExportTicket) {
    key(controller, ticket) {
        val resolver = LocalContext.current.contentResolver
        val launcher = rememberLauncherForActivityResult(LocalJournalCreateDocument()) { uri ->
            controller.exportTo(ticket, uri?.let { SafJournalExportDestination(resolver, it) })
        }
        LaunchedEffect(controller, ticket) {
            try {
                launcher.launch(JOURNAL_EXPORT_FILENAME)
            } catch (_: RuntimeException) {
                controller.exportLaunchFailed(ticket)
            }
        }
    }
}

/** Only a transient reference; no display-name query, persistence grant, logging or sharing. */
private class SafJournalExportDestination(private val resolver: ContentResolver, private val uri: Uri) :
    JournalExportDestination {
    override fun open() = resolver.openOutputStream(uri, "w")
}
