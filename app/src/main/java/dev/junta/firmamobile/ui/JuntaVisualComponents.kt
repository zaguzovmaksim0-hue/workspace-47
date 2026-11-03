package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated

/** Cards, dialogs and notices share one generous corner radius. */
internal val JuntaPanelShape = RoundedCornerShape(28.dp)

/** Text fields and compact surfaces use a slightly tighter radius than the card around them. */
internal val JuntaFieldShape = RoundedCornerShape(18.dp)

private const val PANEL_SHADOW_ALPHA = 0.2f
private const val PANEL_AMBIENT_ALPHA = 0.1f

/**
 * White card with a violet-tinted soft shadow and, optionally, the four-colour mosaic ribbon.
 * Unpadded panels let an edge-to-edge tappable surface own the whole card.
 */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaElevatedPanel(
    modifier: Modifier = Modifier,
    testTag: String = "jfm-certificate-card",
    ribbon: Boolean = false,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shadow = JuntaTone.VIOLET.accent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .shadow(
                elevation = 12.dp,
                shape = JuntaPanelShape,
                ambientColor = shadow.copy(alpha = PANEL_AMBIENT_ALPHA),
                spotColor = shadow.copy(alpha = PANEL_SHADOW_ALPHA),
            )
            .clip(JuntaPanelShape)
            .background(JuntaPaperElevated)
            .drawBehind {
                if (ribbon) {
                    drawRect(
                        brush = Brush.horizontalGradient(JuntaGradients.Ribbon),
                        size = Size(width = size.width, height = 5.dp.toPx()),
                    )
                }
            }
            .border(1.dp, JuntaHairline, JuntaPanelShape)
            .then(
                if (padded) {
                    Modifier.padding(start = 18.dp, end = 18.dp, top = if (ribbon) 22.dp else 18.dp, bottom = 18.dp)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/** The one strong action of a screen: a violet-to-magenta pill with a coloured glow. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val glow = if (enabled) {
        Modifier
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = JuntaGradients.Action.first(),
                spotColor = JuntaGradients.Action.last(),
            )
            .background(Brush.horizontalGradient(JuntaGradients.Action), CircleShape)
    } else {
        Modifier
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .juntaPressScale(interaction)
            .then(glow),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = JuntaMutedInk,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
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

/** Quieter tonal pill for secondary actions. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaOutlinedAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .juntaPressScale(interaction),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = JuntaMutedInk,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
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
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Small pill label, e.g. a section's service count. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaCountPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}
