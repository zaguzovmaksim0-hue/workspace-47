// BrowserPopupHeader.kt
package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R

@Composable
internal fun BrowserPopupHeader(onClose: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("browser-popup-header"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.browser_popup_title),
                modifier = Modifier.weight(1f),
                softWrap = true,
            )
            TextButton(
                onClick = onClose,
                modifier = Modifier.weight(1.5f).testTag("browser-popup-close"),
            ) {
                Text(
                    text = stringResource(R.string.browser_popup_close),
                    softWrap = true,
                )
            }
        }
    }
}
