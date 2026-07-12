package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import java.net.IDN
import java.util.Locale

internal val BrowserToolbarHeight = 64.dp
internal const val BROWSER_TOOLBAR_TAG = "browser_toolbar"
internal const val BROWSER_ADDRESS_LABEL_TAG = "browser_address_label"
internal const val BROWSER_ADDRESS_FIELD_TAG = "browser_address_field"
internal const val BROWSER_BOTTOM_BAR_TAG = "browser_bottom_bar"
internal const val BROWSER_CONTENT_TAG = "browser_content"

object BrowserAddressPresentation {
    fun hostOf(url: String): String = runCatching {
        val uri = Uri.parse(url)
        require(!uri.isOpaque)
        require(uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true))
        require(uri.encodedUserInfo == null)
        val rawHost = uri.host?.takeIf { it.isNotBlank() } ?: return INVALID_ADDRESS
        IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    }.getOrDefault(INVALID_ADDRESS)

    private const val HTTPS_SCHEME = "https"
    private const val INVALID_ADDRESS = "dirección no disponible"
}

@Composable
internal fun BrowserAddressBar(
    currentUrl: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editableUrl by remember { mutableStateOf(currentUrl) }
    var editFieldHasFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun stopEditing(submit: Boolean) {
        val candidate = editableUrl
        onEditingChange(false)
        keyboard?.hide()
        if (submit) onSubmit(candidate) else editableUrl = currentUrl
    }

    BackHandler(enabled = editing) { stopEditing(submit = false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        if (editing) {
            val editDescription = stringResource(R.string.browser_address_edit_description)
            BasicTextField(
                value = editableUrl,
                onValueChange = { editableUrl = it },
                singleLine = true,
                maxLines = 1,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { stopEditing(submit = true) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            editFieldHasFocused = true
                        } else if (editFieldHasFocused && editing) {
                            stopEditing(submit = false)
                        }
                    }
                    .testTag(BROWSER_ADDRESS_FIELD_TAG)
                    .semantics { contentDescription = editDescription },
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        } else {
            val host = BrowserAddressPresentation.hostOf(currentUrl)
            val currentDescription = stringResource(
                R.string.browser_address_current_description,
                host,
            )
            Text(
                text = host,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        editableUrl = currentUrl
                        editFieldHasFocused = false
                        onEditingChange(true)
                    }
                    .testTag(BROWSER_ADDRESS_LABEL_TAG)
                    .semantics { contentDescription = currentDescription },
            )
        }
    }
}
