package dev.junta.firmamobile.ui

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
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
        val compact = maxWidth < 360.dp
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
                maxLines = 2,
                overflow = TextOverflow.Clip,
                style = if (compact) {
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = 39.sp,
                        lineHeight = 37.sp,
                    )
                } else {
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = 44.sp,
                        lineHeight = 41.sp,
                    )
                },
            )
            Icon(
                painter = painterResource(R.drawable.ic_jfm_shield_check),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(if (compact) 48.dp else 54.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(5.dp))
    Text(
        text = stringResource(R.string.unofficial_disclosure),
        color = JuntaInk,
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(modifier = Modifier.height(9.dp))
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
                .padding(horizontal = 18.dp, vertical = 15.dp),
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
                .heightIn(min = 50.dp)
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = JuntaPaperElevated.copy(alpha = buttonAlpha),
                fontFamily = JuntaDisplayFont,
                fontSize = 23.sp,
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
            .heightIn(min = 44.dp),
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
            fontSize = 17.sp,
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_jfm_shield_check),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(40.dp),
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
