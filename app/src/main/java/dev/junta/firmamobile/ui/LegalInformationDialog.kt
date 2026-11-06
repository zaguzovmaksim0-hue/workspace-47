package dev.junta.firmamobile.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaTeal
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
    val titleRes = if (section == "licenses") R.string.legal_licenses else R.string.legal_information
    val title = document?.localizedTitle() ?: stringResource(titleRes)
    Dialog(onDismissRequest = { back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { back() }
        Surface(modifier = Modifier.fillMaxSize().testTag("legal-information-dialog"), color = JuntaPaper) {
            Column(Modifier.safeDrawingPadding()) {
                legalDialogHeader(
                    title = title,
                    atMenu = document == null && section == "menu",
                    onBack = { back() },
                    onDismiss = onDismiss,
                )
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
                    section == "licenses" -> {
                        val documents by produceState<List<LegalDocument>?>(null) {
                            value = withContext(Dispatchers.IO) {
                                readLegalDocuments(context)
                            }
                        }
                        legalLicenseList(documents, onSelect = { document = it })
                    }
                    else -> {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            legalMenuItems(
                                onPrivacy = { document = LegalDocument(privacyTitle, "legal/privacy.txt") },
                                onLicenses = { section = "licenses" },
                                onAbout = { document = LegalDocument(aboutTitle, "legal/about.txt") },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The first level closes the dialog; deeper levels go back one step, like the system back gesture. */
@Composable
private fun legalDialogHeader(title: String, atMenu: Boolean, onBack: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = if (atMenu) onDismiss else onBack) {
            Icon(
                painter = painterResource(if (atMenu) R.drawable.ic_ui_close else R.drawable.ic_ui_arrow_back),
                contentDescription = stringResource(if (atMenu) R.string.close else R.string.legal_back),
                tint = JuntaInk,
            )
        }
        Text(
            title,
            color = JuntaInk,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
        )
    }
    HorizontalDivider(color = JuntaHairline)
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
private fun legalLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = JuntaTeal)
    }
}

@Composable
private fun ColumnScope.legalDocumentContent(content: String?, chunks: List<String>) {
    if (content == null) legalLoading()
    else LazyColumn(Modifier.weight(1f).testTag("legal-document-text"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(chunks.size) { index ->
            Text(chunks[index], color = JuntaInk, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ColumnScope.legalLicenseList(list: List<LegalDocument>?, onSelect: (LegalDocument) -> Unit) {
    if (list == null) legalLoading()
    else if (list.isEmpty()) {
        Text(stringResource(R.string.legal_load_error), color = JuntaMutedInk, modifier = Modifier.padding(20.dp))
    } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(list, key = { it.asset }) { item ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onSelect(item) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.localizedTitle(), modifier = Modifier.weight(1f).padding(end = 12.dp),
                    color = JuntaInk, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Icon(painterResource(R.drawable.ic_ui_chevron_right), contentDescription = null,
                    tint = JuntaMutedInk, modifier = Modifier.size(20.dp))
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = JuntaHairline)
        }
    }
}

@Composable
private fun legalMenuItems(onPrivacy: () -> Unit, onLicenses: () -> Unit, onAbout: () -> Unit) {
    JuntaListPanel {
        JuntaToolRow(iconRes = R.drawable.ic_ui_lock, title = stringResource(R.string.legal_privacy),
            onClick = onPrivacy, tone = JuntaTone.MINT)
        JuntaListDivider()
        JuntaToolRow(iconRes = R.drawable.ic_ui_document, title = stringResource(R.string.legal_licenses),
            onClick = onLicenses, tone = JuntaTone.SKY)
        JuntaListDivider()
        JuntaToolRow(
            iconRes = R.drawable.ic_ui_info,
            title = stringResource(R.string.legal_about),
            onClick = onAbout,
            subtitle = stringResource(R.string.legal_version_value, BuildConfig.VERSION_NAME),
            tone = JuntaTone.SAFFRON,
        )
    }
}
