package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import dev.junta.firmamobile.ui.theme.JuntaWordmarkFont

/** Cards, dialogs and notices share one soft corner radius. */
internal val JuntaPanelShape = RoundedCornerShape(20.dp)

/** Buttons and text fields use a slightly tighter radius than the card around them. */
internal val JuntaFieldShape = RoundedCornerShape(14.dp)

private val JuntaBannerShape = RoundedCornerShape(16.dp)

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaBrandHeader() {
    val appName = stringResource(R.string.app_name)
    val accent = appName.substringAfterLast(' ')
    val primary = appName.removeSuffix(accent).trimEnd()
    val title = buildAnnotatedString {
        withStyle(SpanStyle(color = JuntaInk)) {
            append(primary)
        }
        append(" ")
        withStyle(SpanStyle(color = JuntaTeal)) {
            append(accent)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_jfm_shield_check),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier
                    .testTag("jfm-brand-title")
                    .semantics { heading() },
                fontFamily = JuntaWordmarkFont,
                fontSize = 34.sp,
                lineHeight = 34.sp,
                letterSpacing = 0.6.sp,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = stringResource(R.string.unofficial_disclosure),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaElevatedPanel(
    modifier: Modifier = Modifier,
    testTag: String = "jfm-certificate-card",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .shadow(
                elevation = 2.dp,
                shape = JuntaPanelShape,
                ambientColor = JuntaInk.copy(alpha = 0.2f),
                spotColor = JuntaInk.copy(alpha = 0.2f),
            )
            .background(JuntaPaperElevated, JuntaPanelShape)
            .border(1.dp, JuntaHairline, JuntaPanelShape)
            .padding(18.dp),
        content = content,
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = JuntaFieldShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = JuntaTeal,
            contentColor = JuntaPaperElevated,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaOutlinedAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = JuntaFieldShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) JuntaTeal.copy(alpha = 0.45f) else JuntaHairline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = JuntaTeal,
            containerColor = JuntaPaperElevated,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaStatusBanner(
    title: String,
    copy: String,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, JuntaBannerShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ui_shield),
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = onContainer,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = copy,
                color = onContainer.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
