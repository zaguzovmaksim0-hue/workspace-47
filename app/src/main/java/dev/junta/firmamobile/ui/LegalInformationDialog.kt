package dev.junta.firmamobile.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.junta.firmamobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal data class LegalDocument(val title: String, val asset: String)

internal fun validLegalAsset(asset: String): Boolean =
    asset.startsWith("legal/") && asset.endsWith(".txt") &&
        asset.split('/').all { it.isNotBlank() && it != "." && it != ".." } &&
        asset.none { it == '\\' || it.isISOControl() }

internal fun legalTextChunks(text: String): List<String> =
    text.split("\n\n").flatMap { paragraph -> paragraph.chunked(2_000) }.filter(String::isNotBlank)

@Composable
internal fun LegalInformationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val loadError = stringResource(R.string.legal_load_error)
    val privacyTitle = stringResource(R.string.legal_privacy)
    val aboutTitle = stringResource(R.string.legal_about)
    var section by remember { mutableStateOf("menu") }
    var document by remember { mutableStateOf<LegalDocument?>(null) }
    fun back() {
        when {
            document != null -> document = null
            section != "menu" -> section = "menu"
            else -> onDismiss()
        }
    }
    val title = legalDialogTitle(document, section)
    Dialog(onDismissRequest = { back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { back() }
        Surface(modifier = Modifier.fillMaxSize().testTag("legal-information-dialog")) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                legalDialogHeader(title, onBack = { back() }, onDismiss = onDismiss)
                val selected = document
                when {
                    selected != null -> {
                        val content by produceState<String?>(null, selected.asset, loadError) {
                            value = withContext(Dispatchers.IO) {
                                readLegalDocument(context, selected.asset, loadError)
                            }
                        }
                        val chunks = remember(content) { content?.let(::legalTextChunks).orEmpty() }
                        legalDocumentContent(content, chunks)
                    }
                    section == "feedback" -> {
                        val report = remember { currentCompatibilityFeedbackReport() }
                        compatibilityFeedbackPane(
                            report = report,
                            onCopy = { copyCompatibilityFeedback(context, it) },
                            onOpenIssue = { openCompatibilityFeedback(context, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    section == "licenses" -> {
                        val documents by produceState<List<LegalDocument>?>(null) {
                            value = withContext(Dispatchers.IO) {
                                readLegalDocuments(context)
                            }
                        }
                        legalLicenseList(documents, onSelect = { document = it })
                    }
                    else -> {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            legalMenuItems(
                                onPrivacy = { document = LegalDocument(privacyTitle, "legal/privacy.txt") },
                                onLicenses = { section = "licenses" },
                                onAbout = { document = LegalDocument(aboutTitle, "legal/about.txt") },
                                onFeedback = { section = "feedback" },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun legalDialogTitle(document: LegalDocument?, section: String): String =
    document?.localizedTitle() ?: when (section) {
        "licenses" -> stringResource(R.string.legal_licenses)
        "feedback" -> stringResource(R.string.compatibility_feedback_title)
        else -> stringResource(R.string.legal_information)
    }

@Composable
private fun legalDialogHeader(title: String, onBack: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.legal_back))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.close))
        }
    }
    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 12.dp).semantics { heading() })
    HorizontalDivider()
}

private fun readLegalDocument(context: Context, asset: String, loadError: String): String =
    runCatching {
        require(validLegalAsset(asset))
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).use {
            val text = it.readText()
            require(text.length <= 2_000_000)
            text
        }
    }.getOrElse { loadError }

private fun readLegalDocuments(context: Context): List<LegalDocument> =
    runCatching {
        val data = context.assets.open("legal/documents.json").bufferedReader().use { it.readText() }
        val array = JSONArray(data)
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            LegalDocument(item.getString("title"), item.getString("asset")).also {
                require(validLegalAsset(it.asset))
            }
        }
    }.getOrElse { emptyList() }

@Composable
private fun ColumnScope.legalDocumentContent(content: String?, chunks: List<String>) {
    if (content == null) CircularProgressIndicator(Modifier.padding(16.dp))
    else LazyColumn(Modifier.weight(1f).testTag("legal-document-text"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(chunks.size) { index -> Text(chunks[index], style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ColumnScope.legalLicenseList(list: List<LegalDocument>?, onSelect: (LegalDocument) -> Unit) {
    if (list == null) CircularProgressIndicator(Modifier.padding(16.dp))
    else if (list.isEmpty()) Text(stringResource(R.string.legal_load_error))
    else LazyColumn(Modifier.weight(1f)) {
        items(list, key = { it.asset }) { item ->
            TextButton(onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(
                    item.localizedTitle(), modifier = Modifier.fillMaxWidth(),
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun legalMenuItems(
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
    onAbout: () -> Unit,
    onFeedback: () -> Unit,
) {
    TextButton(onClick = onPrivacy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.legal_privacy))
    }
    TextButton(onClick = onLicenses,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.legal_licenses))
    }
    TextButton(onClick = onAbout,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.legal_about))
    }
    TextButton(
        onClick = onFeedback,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("legal-compatibility-feedback"),
    ) {
        Text(stringResource(R.string.compatibility_feedback_title))
    }
}
