package dev.junta.firmamobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    var section by remember { mutableStateOf("menu") }
    var document by remember { mutableStateOf<LegalDocument?>(null) }
    fun back() {
        when {
            document != null -> document = null
            section != "menu" -> section = "menu"
            else -> onDismiss()
        }
    }
    val title = document?.title ?: when (section) {
        "licenses" -> stringResource(R.string.legal_licenses)
        else -> stringResource(R.string.legal_information)
    }
    Dialog(onDismissRequest = { back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { back() }
        Surface(modifier = Modifier.fillMaxSize().testTag("legal-information-dialog")) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { back() }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.legal_back))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.close))
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 12.dp).semantics { heading() })
                HorizontalDivider()
                val selected = document
                when {
                    selected != null -> {
                        val content by produceState<String?>(null, selected.asset) {
                            value = withContext(Dispatchers.IO) {
                                runCatching {
                                    require(validLegalAsset(selected.asset))
                                    context.assets.open(selected.asset).bufferedReader(Charsets.UTF_8).use {
                                        val text = it.readText()
                                        require(text.length <= 2_000_000)
                                        text
                                    }
                                }.getOrElse { context.getString(R.string.legal_load_error) }
                            }
                        }
                        val chunks = remember(content) { content?.let(::legalTextChunks).orEmpty() }
                        if (content == null) CircularProgressIndicator(Modifier.padding(16.dp))
                        else LazyColumn(Modifier.weight(1f).testTag("legal-document-text"),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(chunks.size) { index -> Text(chunks[index], style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    section == "licenses" -> {
                        val documents by produceState<List<LegalDocument>?>(null) {
                            value = withContext(Dispatchers.IO) {
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
                            }
                        }
                        val list = documents
                        if (list == null) CircularProgressIndicator(Modifier.padding(16.dp))
                        else if (list.isEmpty()) Text(stringResource(R.string.legal_load_error))
                        else LazyColumn(Modifier.weight(1f)) {
                            items(list, key = { it.asset }) { item ->
                                TextButton(onClick = { document = item },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(item.title, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    else -> {
                        TextButton(onClick = { document = LegalDocument(context.getString(R.string.legal_privacy), "legal/privacy.txt") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.legal_privacy))
                        }
                        TextButton(onClick = { section = "licenses" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.legal_licenses))
                        }
                        TextButton(onClick = { document = LegalDocument(context.getString(R.string.legal_about), "legal/about.txt") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.legal_about))
                        }
                    }
                }
            }
        }
    }
}
