package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaDisplayFont
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import dev.junta.firmamobile.ui.theme.JuntaTealBright
import dev.junta.firmamobile.ui.theme.JuntaTealDark

internal val JuntaPanelShape = CutCornerShape(
    topStart = 8.dp,
    topEnd = 7.dp,
    bottomEnd = 8.dp,
    bottomStart = 7.dp,
)

private val JuntaButtonShape = CutCornerShape(
    topStart = 6.dp,
    topEnd = 5.dp,
    bottomEnd = 6.dp,
    bottomStart = 5.dp,
)

@Composable
internal fun JuntaBrandHeader() {
    val appName = stringResource(R.string.app_name)
    val accent = appName.substringAfterLast(' ')
    val primary = appName.removeSuffix(accent).trimEnd()
    val title: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = JuntaInk)) {
            append(primary)
        }
        append(" ")
        withStyle(SpanStyle(color = JuntaTeal)) {
            append(accent)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 400.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .testTag("jfm-brand-title")
                    .semantics { heading() },
                maxLines = 3,
                overflow = TextOverflow.Clip,
                style = if (compact) {
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = 54.sp,
                        lineHeight = 50.sp,
                    )
                } else {
                    MaterialTheme.typography.displayLarge
                },
            )
            Icon(
                painter = painterResource(R.drawable.ic_jfm_shield_check),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(if (compact) 64.dp else 78.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.unofficial_disclosure),
        color = JuntaInk,
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(modifier = Modifier.height(15.dp))
    JuntaDoubleRule()
}

@Composable
private fun JuntaDoubleRule() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(JuntaTeal),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(JuntaTeal),
        )
    }
}

@Composable
internal fun JuntaElevatedPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 6.dp, bottom = 7.dp)
            .testTag("jfm-certificate-card"),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 7.dp)
                .background(JuntaInk, JuntaPanelShape),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(JuntaPaperElevated.copy(alpha = 0.96f), JuntaPanelShape)
                .border(2.dp, JuntaInk, JuntaPanelShape)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            content = content,
        )
    }
}

@Composable
internal fun JuntaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val buttonAlpha = if (enabled) 1f else 0.48f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 5.dp, bottom = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 6.dp)
                .background(JuntaInk.copy(alpha = buttonAlpha), JuntaButtonShape),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(JuntaButtonShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JuntaTealBright.copy(alpha = buttonAlpha),
                            JuntaTealDark.copy(alpha = buttonAlpha),
                        ),
                    ),
                )
                .border(2.dp, JuntaInk.copy(alpha = buttonAlpha), JuntaButtonShape)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                }
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = JuntaPaperElevated.copy(alpha = buttonAlpha),
                fontFamily = JuntaDisplayFont,
                fontSize = 26.sp,
                letterSpacing = 1.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun JuntaOutlinedAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = JuntaButtonShape,
        border = BorderStroke(1.5.dp, JuntaInk),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = JuntaInk,
            containerColor = JuntaPaperElevated.copy(alpha = 0.76f),
        ),
    ) {
        Text(
            text = text,
            fontFamily = JuntaDisplayFont,
            fontSize = 19.sp,
            letterSpacing = 0.7.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun JuntaStatusBanner(
    title: String,
    copy: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JuntaPaperElevated.copy(alpha = 0.86f), JuntaPanelShape)
            .border(1.5.dp, JuntaTeal, JuntaPanelShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_jfm_shield_check),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = JuntaTeal,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = copy,
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun JuntaHomeNavigation() {
    val items = listOf(
        HomeNavigationItem(R.drawable.ic_jfm_home, R.string.navigation_home, true),
        HomeNavigationItem(R.drawable.ic_jfm_history, R.string.navigation_history, false),
        HomeNavigationItem(R.drawable.ic_jfm_settings, R.string.navigation_settings, false),
        HomeNavigationItem(R.drawable.ic_jfm_help, R.string.navigation_help, false),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 5.dp)
            .testTag("jfm-home-navigation"),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 5.dp)
                .background(JuntaInk, JuntaPanelShape),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JuntaPaperElevated.copy(alpha = 0.94f), JuntaPanelShape)
                .border(2.dp, JuntaInk, JuntaPanelShape)
                .padding(horizontal = 7.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items.forEach { item ->
                JuntaHomeNavigationItem(
                    item = item,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun JuntaHomeNavigationItem(
    item: HomeNavigationItem,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(item.labelRes)
    Column(
        modifier = modifier
            .heightIn(min = 62.dp)
            .then(
                if (item.active) {
                    Modifier
                        .background(JuntaTeal.copy(alpha = 0.10f), JuntaButtonShape)
                        .border(1.dp, JuntaTeal, JuntaButtonShape)
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Tab
                selected = item.active
                if (!item.active) disabled()
            }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            tint = if (item.active) JuntaTeal else JuntaInk,
            modifier = Modifier.size(26.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (item.active) JuntaTeal else JuntaInk,
            fontFamily = JuntaDisplayFont,
            fontSize = 14.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private data class HomeNavigationItem(
    @param:DrawableRes val iconRes: Int,
    val labelRes: Int,
    val active: Boolean,
)
