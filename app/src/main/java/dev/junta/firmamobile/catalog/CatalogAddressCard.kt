package dev.junta.firmamobile.catalog

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import dev.junta.firmamobile.ui.theme.JuntaTealDark

/** Small inline component above the catalog, never a modal address dialog. */
@Composable
internal fun CatalogAddressCard(
    state: CatalogAddressInput,
    onEdit: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CutCornerShape(8.dp)
    Column(
        modifier = modifier.fillMaxWidth().background(JuntaPaperElevated, shape)
            .border(1.dp, JuntaHairline, shape).padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("catalog-address-card"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.catalog_address_title), color = JuntaTealDark,
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.catalog_address_hint), color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = state.text, onValueChange = onEdit,
            label = { Text(stringResource(R.string.catalog_address_label)) },
            placeholder = { Text("sede.example.org") },
            singleLine = true, enabled = !state.opening, isError = state.error != null,
            shape = shape,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().testTag("public-web-address"),
            trailingIcon = {
                Row {
                    if (state.text.isEmpty()) {
                        TextButton(onClick = onPaste, enabled = !state.opening,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("catalog-address-paste")) {
                            Text(stringResource(R.string.catalog_address_paste), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (state.text.isNotEmpty() || state.error != null) {
                        val clearLabel = stringResource(R.string.catalog_address_clear)
                        TextButton(onClick = onClear, enabled = !state.opening,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("catalog-address-clear")
                                .semantics { contentDescription = clearLabel }) { Text("×") }
                    }
                }
            },
            supportingText = {
                val error = state.error
                if (error != null) {
                    Text(stringResource(when (error) {
                        CatalogAddressInput.Error.EMPTY -> R.string.catalog_address_required
                        CatalogAddressInput.Error.INVALID -> R.string.catalog_address_invalid
                        CatalogAddressInput.Error.TOO_LONG -> R.string.catalog_address_too_long
                        CatalogAddressInput.Error.CLIPBOARD_EMPTY -> R.string.catalog_address_clipboard_empty
                    }), modifier = Modifier.testTag("catalog-address-error"))
                } else if (state.hostPreview != null) {
                    Text(stringResource(R.string.catalog_address_destination, checkNotNull(state.hostPreview)),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("catalog-address-host"))
                }
            },
        )
        AddressOpenButton(state, onSubmit, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AddressOpenButton(state: CatalogAddressInput, onSubmit: () -> Unit, modifier: Modifier) {
    Button(onClick = onSubmit, enabled = state.canSubmit,
        colors = ButtonDefaults.buttonColors(containerColor = JuntaTeal, contentColor = JuntaPaperElevated),
        modifier = modifier.heightIn(min = 48.dp).testTag("public-web-open-confirm"), shape = CutCornerShape(8.dp)) {
        Text(stringResource(if (state.opening) R.string.catalog_address_opening else R.string.catalog_open_site))
    }
}

/** Access is only called by the explicit paste action. Never coerce content
 * URIs, open clipboard Intents or perform a background clipboard read. */
internal fun readCatalogClipboardText(context: Context): CharSequence? = try {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = clipboard?.primaryClip
    if (clip == null || clip.itemCount != 1) null else {
        clip.getItemAt(0).text?.let { text ->
            // One excess character records a length failure without retaining
            // a large clipboard payload or opening its truncated prefix.
            text.subSequence(0, minOf(text.length, CatalogAddressInput.MAX_INPUT_CHARS + 1)).toString()
        }
    }
} catch (_: SecurityException) { null }
