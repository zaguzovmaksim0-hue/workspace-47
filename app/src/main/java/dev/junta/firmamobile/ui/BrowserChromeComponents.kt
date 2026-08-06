package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaDisplayFont
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import dev.junta.firmamobile.ui.theme.JuntaTealDark

internal val BrowserIndustrialToolbarHeight = 72.dp
internal const val BROWSER_PROFILE_TITLE_TAG = "browser_profile_title"
internal const val BROWSER_PROFILE_STATUS_TAG = "browser_profile_status"
internal const val BROWSER_LOADING_TAG = "browser_loading"
internal const val BROWSER_NOTICE_TAG = "browser_notice"

/**
 * Native browser chrome only. The selected portal remains entirely owned by the WebView below it.
 */
@Composable
internal fun IndustrialBrowserTopBar(
    profileName: String,
    host: String,
    trustLabel: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onChangeCertificate: () -> Unit,
    onLockCertificate: () -> Unit,
    onClearCurrentSiteRequested: () -> Unit,
    onClearSessionRequested: () -> Unit,
    onDeleteAllBrowserDataRequested: () -> Unit,
    onIdentityClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    editingContent: (@Composable () -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JuntaPaper)
            .windowInsetsPadding(windowInsets),
    ) {
        Surface(
            color = JuntaPaperElevated,
            contentColor = JuntaInk,
            modifier = Modifier
                .fillMaxWidth()
                .height(BrowserIndustrialToolbarHeight - 3.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowserChromeIconButton(
                    iconRes = R.drawable.ic_browser_back,
                    contentDescription = stringResource(R.string.browser_action_back),
                    onClick = onBack,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    if (editingContent == null) {
                        BrowserServiceIdentity(
                            profileName = profileName,
                            host = host,
                            trustLabel = trustLabel,
                            modifier = Modifier
                                .then(
                                    if (onIdentityClick != null) {
                                        Modifier.clickable(
                                            role = Role.Button,
                                            onClick = onIdentityClick,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .testTag(BROWSER_ADDRESS_LABEL_TAG),
                        )
                    } else {
                        editingContent()
                    }
                }

                BrowserChromeIconButton(
                    iconRes = R.drawable.ic_browser_home,
                    contentDescription = stringResource(R.string.browser_action_home),
                    onClick = onHome,
                )
                BrowserChromeIconButton(
                    iconRes = R.drawable.ic_browser_reload,
                    contentDescription = stringResource(R.string.browser_action_reload),
                    onClick = onReload,
                )
                Box {
                    BrowserChromeIconButton(
                        iconRes = R.drawable.ic_browser_more,
                        contentDescription = stringResource(R.string.browser_action_more),
                        onClick = { menuExpanded = true },
                    )
                    BrowserOverflowMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onChangeCertificate = {
                            menuExpanded = false
                            onChangeCertificate()
                        },
                        onLockCertificate = {
                            menuExpanded = false
                            onLockCertificate()
                        },
                        onClearCurrentSiteRequested = {
                            menuExpanded = false
                            onClearCurrentSiteRequested()
                        },
                        onClearSessionRequested = {
                            menuExpanded = false
                            onClearSessionRequested()
                        },
                        onDeleteAllBrowserDataRequested = {
                            menuExpanded = false
                            onDeleteAllBrowserDataRequested()
                        },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(JuntaTeal),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(JuntaInk.copy(alpha = 0.42f)),
        )
    }
}

@Composable
internal fun BrowserServiceIdentity(
    profileName: String,
    host: String,
    trustLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = profileName,
            color = JuntaTealDark,
            fontFamily = JuntaDisplayFont,
            fontSize = 20.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.45.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(BROWSER_PROFILE_TITLE_TAG),
        )
        Text(
            text = stringResource(R.string.browser_host_and_trust, host, trustLabel),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(BROWSER_PROFILE_STATUS_TAG),
        )
    }
}

@Composable
internal fun BrowserOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onChangeCertificate: () -> Unit,
    onLockCertificate: () -> Unit,
    onClearCurrentSiteRequested: () -> Unit,
    onClearSessionRequested: () -> Unit,
    onDeleteAllBrowserDataRequested: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .widthIn(min = 232.dp, max = 280.dp)
            .background(JuntaPaperElevated, BrowserMenuShape)
            .border(1.5.dp, JuntaInk, BrowserMenuShape),
    ) {
        BrowserMenuItem(
            iconRes = R.drawable.ic_browser_change_certificate,
            text = stringResource(R.string.browser_change_certificate),
            onClick = onChangeCertificate,
        )
        BrowserMenuItem(
            iconRes = R.drawable.ic_browser_lock,
            text = stringResource(R.string.browser_lock_certificate),
            onClick = onLockCertificate,
        )
        BrowserMenuItem(
            iconRes = R.drawable.ic_browser_logout,
            text = stringResource(R.string.browser_clear_current_site),
            onClick = onClearCurrentSiteRequested,
        )
        BrowserMenuItem(
            iconRes = R.drawable.ic_browser_logout,
            text = stringResource(R.string.browser_clear_session),
            onClick = onClearSessionRequested,
        )
        BrowserMenuItem(
            iconRes = R.drawable.ic_browser_logout,
            text = stringResource(R.string.browser_delete_all_data),
            contentColor = MaterialTheme.colorScheme.error,
            onClick = onDeleteAllBrowserDataRequested,
        )
    }
}

@Composable
private fun BrowserMenuItem(
    @DrawableRes iconRes: Int,
    text: String,
    onClick: () -> Unit,
    contentColor: Color = JuntaInk,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
internal fun BrowserCertificateStrip(
    certificateOwner: String,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    Surface(
        color = JuntaPaperElevated,
        contentColor = JuntaInk,
        border = BorderStroke(1.5.dp, JuntaTeal),
        shape = BrowserCertificateShape,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_jfm_shield_check),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.browser_certificate_active),
                    color = JuntaTealDark,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = certificateOwner,
                    color = JuntaMutedInk,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun BrowserLoadingIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (visible) {
        LinearProgressIndicator(
            color = JuntaTeal,
            trackColor = JuntaHairline.copy(alpha = 0.55f),
            modifier = modifier
                .fillMaxWidth()
                .height(3.dp)
                .testTag(BROWSER_LOADING_TAG),
        )
    }
}

@Composable
internal fun BrowserNoticeBanner(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    liveRegionMode: LiveRegionMode = LiveRegionMode.Assertive,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = JuntaPanelShape,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(BROWSER_NOTICE_TAG)
            .semantics { liveRegion = liveRegionMode },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_browser_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                BrowserChromeIconButton(
                    iconRes = R.drawable.ic_browser_reload,
                    contentDescription = stringResource(R.string.browser_retry),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onRetry,
                )
            } else {
                Spacer(modifier = Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun BrowserChromeIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = JuntaTealDark,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

private val BrowserMenuShape = CutCornerShape(
    topStart = 7.dp,
    topEnd = 4.dp,
    bottomEnd = 7.dp,
    bottomStart = 4.dp,
)

private val BrowserCertificateShape = CutCornerShape(
    topStart = 5.dp,
    topEnd = 4.dp,
    bottomEnd = 0.dp,
    bottomStart = 0.dp,
)
