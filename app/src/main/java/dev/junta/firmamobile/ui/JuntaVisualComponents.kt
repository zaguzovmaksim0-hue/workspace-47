package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated

/** Dialogs, sheets and plates share one moderate corner radius. */
internal val JuntaPanelShape = RoundedCornerShape(22.dp)

/** Fields, keys and compact surfaces use the key radius of the device. */
internal val JuntaFieldShape = RoundedCornerShape(14.dp)

/** Wide keys: the main action and the light secondary key. */
internal val FirmaWideKeyShape = RoundedCornerShape(14.dp)

/**
 * A flat plate screwed onto the body: a slightly lighter surface with a hairline edge.
 * Unpadded plates let an edge-to-edge tappable surface own the whole plate.
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(JuntaPanelShape)
            .background(JuntaPaperElevated)
            .border(1.dp, JuntaHairline, JuntaPanelShape)
            .then(
                if (padded) {
                    Modifier.padding(start = 18.dp, end = 18.dp, top = if (ribbon) 20.dp else 18.dp, bottom = 18.dp)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/** The one strong action of a screen: the orange key. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) {
    FirmaWideKey(
        text = text,
        onClick = onClick,
        style = FirmaKeyStyle.ORANGE,
        modifier = modifier,
        enabled = enabled,
        iconRes = iconRes,
        depth = 4.dp,
        minHeight = 54.dp,
    )
}

/**
 * The large orange key of the home screen: the label printed at the start and a glyph at the
 * end, like the main key of the device. Disabled, it rests sunk with a muted label.
 */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaMainKey(
    text: String,
    onClick: () -> Unit,
    @DrawableRes trailingIconRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ink = if (enabled) FirmaKeyStyle.ORANGE.colors.ink else JuntaMutedInk
    FirmaKey(
        onClick = onClick,
        style = FirmaKeyStyle.ORANGE,
        shape = MainKeyShape,
        modifier = modifier.fillMaxWidth(),
        depth = 5.dp,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(start = 24.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                color = ink,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                painter = painterResource(trailingIconRes),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private val MainKeyShape = RoundedCornerShape(16.dp)

/** Quieter light key for secondary actions. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaOutlinedAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) {
    FirmaWideKey(
        text = text,
        onClick = onClick,
        style = FirmaKeyStyle.LIGHT,
        modifier = modifier,
        enabled = enabled,
        iconRes = iconRes,
        depth = 3.dp,
        minHeight = 48.dp,
    )
}

@Suppress("LongParameterList", "FunctionNaming")
@Composable
private fun FirmaWideKey(
    text: String,
    onClick: () -> Unit,
    style: FirmaKeyStyle,
    modifier: Modifier,
    enabled: Boolean,
    @DrawableRes iconRes: Int?,
    depth: androidx.compose.ui.unit.Dp,
    minHeight: androidx.compose.ui.unit.Dp,
) {
    val ink = if (enabled) style.colors.ink else JuntaMutedInk
    FirmaKey(
        onClick = onClick,
        style = style,
        shape = FirmaWideKeyShape,
        modifier = modifier.fillMaxWidth(),
        depth = depth,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = minHeight)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = ink,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Small recessed counter, e.g. a section's service count. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaCountPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .firmaBodyRecess(CircleShape, depth = 2.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = JuntaInk,
        style = firmaMono(12.5.sp),
        maxLines = 1,
    )
}
