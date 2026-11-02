package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated

private const val DISABLED_ROW_ALPHA = 0.5f

/** Small heading above a group of home actions. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 22.dp, bottom = 8.dp)
            .semantics { heading() },
        color = JuntaMutedInk,
        style = MaterialTheme.typography.titleSmall,
    )
}

/** White grouped list surface; rows inside are separated by [JuntaListDivider]. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaListPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(JuntaPanelShape)
            .background(JuntaPaperElevated)
            .border(1.dp, JuntaHairline, JuntaPanelShape)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
        color = JuntaHairline,
        thickness = 1.dp,
    )
}

/** One full-width, tappable home action with an icon, optional explanation and chevron. */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
internal fun JuntaToolRow(
    @DrawableRes iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val actionAlpha = if (enabled) 1f else DISABLED_ROW_ALPHA
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .alpha(actionAlpha)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.alpha(actionAlpha),
                color = JuntaInk,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = JuntaMutedInk,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_ui_chevron_right),
            contentDescription = null,
            tint = JuntaMutedInk,
            modifier = Modifier
                .size(20.dp)
                .alpha(actionAlpha),
        )
    }
}
