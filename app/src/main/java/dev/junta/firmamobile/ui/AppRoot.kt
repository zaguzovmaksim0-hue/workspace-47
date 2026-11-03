package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateExpirationPolicy
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.CertificateUnlockRetention
import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.ui.history.verificationJournalHome
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How far the certificate panel rises over the bottom of the brand hero. */
private val HeroOverlap = 34.dp
private const val STATE_FADE_IN_MILLIS = 280
private const val STATE_FADE_OUT_MILLIS = 140
private const val STATE_SCALE_FROM = 0.96f
private const val ERROR_TINT_ALPHA = 0.08f
private const val BROWSE_MOSAIC_ALPHA = 0.22f
private const val BROWSE_MOSAIC_FROM = 0.55f

@Composable
fun AppRoot(
    state: CertificateUiState = CertificateUiState.NoCertificate(),
    onSelectCertificate: () -> Unit = {},
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit = { _, _ -> },
    onRestoreSavedUnlock: (() -> Unit)? = null,
    onLock: () -> Unit = {},
    onForget: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    var legalInformationVisible by remember { mutableStateOf(false) }
    if (legalInformationVisible) LegalInformationDialog(onDismiss = { legalInformationVisible = false })
    val verificationJournal = remember { InMemoryOperationJournal() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JuntaPaper),
    ) {
        JuntaBackdrop(modifier = Modifier.fillMaxSize().testTag("jfm-home-background"))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            JuntaBrandHero(bottomInset = HeroOverlap)
            Box(modifier = Modifier.juntaOverlapAbove(HeroOverlap).padding(horizontal = 6.dp)) {
                CertificatePanel(
                    state = state,
                    onSelectCertificate = onSelectCertificate,
                    onUnlock = onUnlock,
                    onRestoreSavedUnlock = onRestoreSavedUnlock,
                    onLock = onLock,
                    onForget = onForget,
                    onContinue = onContinue,
                )
            }
            if (state !is CertificateUiState.Unlocked) {
                Spacer(modifier = Modifier.height(14.dp))
                BrowseWithoutCertificateCard(onClick = onContinue)
            }
            JuntaSectionLabel(stringResource(R.string.home_tools_title))
            JuntaTileGrid {
                localFileSigningEntry(state is CertificateUiState.Unlocked)
                verificationJournalHome(verificationJournal)
                certificateReminderSettings()
            }
            Spacer(modifier = Modifier.height(20.dp))
            HomeLegalFooter(onClick = { legalInformationVisible = true })
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Public browsing stays one tap away, as a quiet card with a blue azulejo corner. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun BrowseWithoutCertificateCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val mosaic = JuntaTone.SKY.accent.copy(alpha = BROWSE_MOSAIC_ALPHA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .juntaPressScale(interaction)
            .clip(JuntaPanelShape)
            .background(JuntaPaperElevated)
            .drawBehind { drawMosaic(mosaic, cell = 22.dp.toPx(), fromFraction = BROWSE_MOSAIC_FROM) }
            .border(1.dp, JuntaHairline, JuntaPanelShape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("browse-without-certificate"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JuntaIconBubble(iconRes = R.drawable.ic_ui_globe, tone = JuntaTone.SKY)
        Text(
            text = stringResource(R.string.browse_without_certificate),
            modifier = Modifier.weight(1f),
            color = JuntaInk,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(JuntaTone.SKY.brush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_arrow_forward),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Legal information and the app version, as one small centred link at the end of the page. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun HomeLegalFooter(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp)
                .testTag("open-legal-information"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.legal_information),
                color = JuntaInk,
                style = MaterialTheme.typography.labelLarge,
            )
            Box(modifier = Modifier.size(4.dp).background(JuntaMutedInk, CircleShape))
            Text(
                text = stringResource(R.string.legal_version_value, BuildConfig.VERSION_NAME),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun CertificatePanel(
    state: CertificateUiState,
    onSelectCertificate: () -> Unit,
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit,
    onRestoreSavedUnlock: (() -> Unit)?,
    onLock: () -> Unit,
    onForget: () -> Unit,
    onContinue: () -> Unit,
) {
    JuntaElevatedPanel(ribbon = true) {
        CertificatePanelHeader(
            state = state,
            options = CertificateOptions(
                onLock = onLock,
                onSelectCertificate = onSelectCertificate,
                onForget = onForget,
            ),
        )
        Spacer(modifier = Modifier.height(18.dp))
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(durationMillis = STATE_FADE_IN_MILLIS)) + scaleIn(initialScale = STATE_SCALE_FROM))
                    .togetherWith(fadeOut(tween(durationMillis = STATE_FADE_OUT_MILLIS)))
                    .using(SizeTransform(clip = false))
            },
            label = "certificate-state",
        ) { current ->
            Column(modifier = Modifier.fillMaxWidth()) {
                CertificateStateBody(
                    state = current,
                    onSelectCertificate = onSelectCertificate,
                    onUnlock = onUnlock,
                    onRestoreSavedUnlock = onRestoreSavedUnlock,
                    onContinue = onContinue,
                )
            }
        }
        savedCertificatesSection(state)
    }
}

/** Header menu actions; destructive and secondary choices live here instead of extra buttons. */
private class CertificateOptions(
    val onLock: () -> Unit,
    val onSelectCertificate: () -> Unit,
    val onForget: () -> Unit,
)

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateStateBody(
    state: CertificateUiState,
    onSelectCertificate: () -> Unit,
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit,
    onRestoreSavedUnlock: (() -> Unit)?,
    onContinue: () -> Unit,
) {
    when (state) {
        CertificateUiState.LoadingReference -> LoadingCertificate()
        is CertificateUiState.NoCertificate -> NoCertificate(
            state = state,
            onSelectCertificate = onSelectCertificate,
        )
        is CertificateUiState.Locked -> if (state.choices.isNotEmpty()) {
            certificateEntryChooser(state)
        } else {
            LockedCertificate(state = state, onUnlock = onUnlock, onRestoreSavedUnlock = onRestoreSavedUnlock)
        }
        is CertificateUiState.Unlocking -> UnlockingCertificate(state)
        is CertificateUiState.Unlocked -> UnlockedCertificate(state = state, onContinue = onContinue)
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificatePanelHeader(state: CertificateUiState, options: CertificateOptions) {
    val (statusRes, statusTone) = certificateStatus(state)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JuntaIconBubble(
            iconRes = R.drawable.ic_ui_certificate,
            tone = JuntaTone.VIOLET,
            size = 52.dp,
            iconSize = 26.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.certificate_title),
                color = JuntaInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            JuntaStatusPill(text = stringResource(statusRes), tone = statusTone)
        }
        if (state.offersCertificateOptions()) CertificateOptionsMenu(state, options)
    }
}

@Composable
private fun certificateStatus(state: CertificateUiState): Pair<Int, JuntaTone> {
    val selectedKey = LocalSavedCertificates.current?.state?.activeKey != null
    return when (state) {
        CertificateUiState.LoadingReference -> R.string.certificate_status_loading_title to JuntaTone.SKY
        is CertificateUiState.NoCertificate -> if (selectedKey) {
            R.string.certificate_status_locked_title to JuntaTone.SAFFRON
        } else {
            R.string.certificate_status_ready_title to JuntaTone.MINT
        }
        is CertificateUiState.Locked -> R.string.certificate_status_locked_title to JuntaTone.SAFFRON
        is CertificateUiState.Unlocking -> R.string.certificate_status_unlocking_title to JuntaTone.SKY
        is CertificateUiState.Unlocked -> R.string.certificate_status_unlocked_title to JuntaTone.MINT
    }
}

private fun CertificateUiState.offersCertificateOptions(): Boolean =
    this is CertificateUiState.Unlocked || (this is CertificateUiState.Locked && choices.isEmpty())

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateOptionsMenu(state: CertificateUiState, options: CertificateOptions) {
    var expanded by remember(state::class) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = JuntaInk,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_browser_more),
                contentDescription = stringResource(R.string.certificate_options),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = JuntaFieldShape,
            containerColor = JuntaPaperElevated,
        ) {
            if (state is CertificateUiState.Unlocked) {
                CertificateMenuItem(R.string.lock_certificate, R.drawable.ic_ui_lock, JuntaTone.VIOLET) {
                    expanded = false
                    options.onLock()
                }
            }
            CertificateMenuItem(R.string.choose_another_certificate, R.drawable.ic_ui_document, JuntaTone.SKY) {
                expanded = false
                options.onSelectCertificate()
            }
            CertificateMenuItem(R.string.forget_certificate, R.drawable.ic_ui_delete, JuntaTone.CORAL) {
                expanded = false
                options.onForget()
            }
        }
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateMenuItem(
    @StringRes text: Int,
    @DrawableRes iconRes: Int,
    tone: JuntaTone,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(text),
                color = if (tone == JuntaTone.CORAL) MaterialTheme.colorScheme.error else JuntaInk,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        },
        leadingIcon = { JuntaIconBubble(iconRes = iconRes, tone = tone, size = 32.dp, iconSize = 17.dp) },
        onClick = onClick,
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun LoadingCertificate() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = JuntaTeal,
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
        Text(
            text = stringResource(R.string.certificate_loading),
            color = JuntaInk,
        )
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun NoCertificate(
    state: CertificateUiState.NoCertificate,
    onSelectCertificate: () -> Unit,
) {
    state.error?.let { CertificateError(it) }
    val selected = LocalSavedCertificates.current?.state?.activeKey?.signingKey
    if (selected != null) {
        Text(
            stringResource(R.string.certificate_selected, selected.displayName),
            color = JuntaInk,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("selected-keystore-certificate"),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.saved_keystore_locked),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        EmptyCertificateSlot(text = stringResource(R.string.certificate_copy))
    }
    Spacer(modifier = Modifier.height(14.dp))
    HomeNote(iconRes = R.drawable.ic_ui_shield, tone = JuntaTone.MINT, text = stringResource(R.string.privacy_copy))
    Spacer(modifier = Modifier.height(18.dp))
    JuntaPrimaryButton(
        text = stringResource(R.string.select_certificate),
        onClick = onSelectCertificate,
        iconRes = R.drawable.ic_ui_add,
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun LockedCertificate(
    state: CertificateUiState.Locked,
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit,
    onRestoreSavedUnlock: (() -> Unit)?,
) {
    LockedCertificateCard(state)
    var retention by remember(state.reference.uri) { mutableStateOf(CertificateUnlockRetention.FIFTEEN_MINUTES) }
    val passwordState = remember(state.reference.uri) { TextFieldState() }
    DisposableEffect(passwordState) {
        onDispose { passwordState.clearText() }
    }
    if (state.revocationPending) {
        Text(
            stringResource(R.string.certificate_revoking),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    CertificatePasswordField(passwordState)
    Spacer(modifier = Modifier.height(12.dp))
    CertificateUnlockRetentionSelector(retention, !state.revocationPending) { retention = it }
    Spacer(modifier = Modifier.height(16.dp))
    JuntaPrimaryButton(
        text = stringResource(R.string.unlock_certificate),
        onClick = {
            val password = CharArray(passwordState.text.length) { index ->
                passwordState.text[index]
            }
            passwordState.clearText()
            onUnlock(password, retention)
        },
        enabled = passwordState.text.isNotEmpty() && !state.revocationPending,
        iconRes = R.drawable.ic_ui_lock,
    )
    SavedCertificateUnlockAction(onRestoreSavedUnlock, !state.revocationPending)
    Spacer(modifier = Modifier.height(12.dp))
    HomeNote(iconRes = R.drawable.ic_ui_shield, tone = JuntaTone.MINT, text = stringResource(R.string.privacy_copy))
}

/** The selected file as a dark, still-locked identity card, followed by its expiry notice and errors. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun LockedCertificateCard(state: CertificateUiState.Locked) {
    val name = certificateDisplayNameText(state.reference.displayName)
    CertificateIdCard(mood = CertificateCardMood.LOCKED, label = null) {
        Text(
            text = certificateCardLine(stringResource(R.string.certificate_selected, name), name),
            color = Color.White.copy(alpha = ID_CARD_TEXT_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.summary?.let { summary ->
            Spacer(modifier = Modifier.height(10.dp))
            CertificateCardFacts(summary = summary, emphasizeOwner = false)
        }
    }
    state.summary?.let {
        CertificateExpirationNotice(validUntil = it.validUntil, modifier = Modifier.padding(top = 10.dp))
    }
    Spacer(modifier = Modifier.height(16.dp))
    state.error?.let { CertificateError(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificatePasswordField(passwordState: TextFieldState) {
    val passwordLabel = stringResource(R.string.certificate_password)
    OutlinedSecureTextField(
        state = passwordState,
        label = { Text(passwordLabel) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_ui_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        },
        shape = JuntaFieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = JuntaTeal,
            unfocusedBorderColor = JuntaHairline,
            focusedContainerColor = JuntaPaperElevated,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = passwordLabel },
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun UnlockingCertificate(state: CertificateUiState.Unlocking) {
    val name = certificateDisplayNameText(state.reference.displayName)
    CertificateIdCard(mood = CertificateCardMood.BUSY, label = null) {
        Text(
            text = certificateCardLine(stringResource(R.string.certificate_selected, name), name),
            color = Color.White.copy(alpha = ID_CARD_TEXT_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.certificate_unlocking),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun UnlockedCertificate(state: CertificateUiState.Unlocked, onContinue: () -> Unit) {
    CertificateIdCard(mood = CertificateCardMood.READY, label = stringResource(R.string.certificate_found)) {
        CertificateCardFacts(summary = state.summary, emphasizeOwner = true)
    }
    CertificateExpirationNotice(
        validUntil = state.summary.validUntil,
        modifier = Modifier.padding(top = 10.dp),
    )
    Spacer(modifier = Modifier.height(18.dp))
    JuntaPrimaryButton(
        text = stringResource(R.string.continue_to_portal),
        onClick = onContinue,
        iconRes = R.drawable.ic_ui_arrow_forward,
    )
    Spacer(modifier = Modifier.height(12.dp))
    HomeNote(
        iconRes = R.drawable.ic_ui_history,
        tone = JuntaTone.SAFFRON,
        text = stringResource(R.string.certificate_status_unlocked_copy),
    )
}

/** Holder, issuer and validity printed on the card, with a slim bar for the validity still left. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateCardFacts(summary: CertificateSummary, emphasizeOwner: Boolean) {
    val owner = certificateDisplayNameText(summary.ownerName)
    val ownerLine = stringResource(R.string.certificate_owner, owner)
    val soft = Color.White.copy(alpha = ID_CARD_TEXT_ALPHA)
    Text(
        text = if (emphasizeOwner) certificateCardLine(ownerLine, owner) else AnnotatedString(ownerLine),
        color = soft,
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.certificate_issuer, certificateDisplayNameText(summary.issuerName)),
        color = soft,
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = stringResource(
            R.string.certificate_validity,
            summary.validFrom.toDisplayDate(),
            summary.validUntil.toDisplayDate(),
        ),
        color = soft,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(modifier = Modifier.height(12.dp))
    val span = remember(summary) { summary.validitySpan(Instant.now()) }
    CertificateValidityMeter(
        remaining = span.remaining,
        caption = span.daysLeft?.let { pluralStringResource(R.plurals.certificate_validity_days_left, it, it) },
    )
}

/** Share of the validity period still ahead, and whole days left when no expiry notice covers it. */
private class ValiditySpan(val remaining: Float, val daysLeft: Int?)

private fun CertificateSummary.validitySpan(now: Instant): ValiditySpan {
    if (now.isBefore(validFrom)) return ValiditySpan(remaining = 1f, daysLeft = null)
    val total = Duration.between(validFrom, validUntil).toMillis().coerceAtLeast(1L)
    val left = Duration.between(now, validUntil)
    val remaining = (left.toMillis().toDouble() / total).toFloat().coerceIn(0f, 1f)
    val quiet = CertificateExpirationPolicy.warning(validUntil, now, ZoneId.systemDefault()) == null
    val days = if (quiet && !left.isNegative) left.toDays().toInt() else null
    return ValiditySpan(remaining = remaining, daysLeft = days)
}

/** Small tinted icon plus a muted sentence, used for privacy and session reminders. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun HomeNote(@DrawableRes iconRes: Int, tone: JuntaTone, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JuntaIconBubble(iconRes = iconRes, tone = tone, size = 30.dp, iconSize = 16.dp)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateError(error: CertificateUiError) {
    val tint = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = ERROR_TINT_ALPHA), JuntaFieldShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_browser_warning),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = error.message(),
            color = tint,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun CertificateUiError.message(): String = stringResource(
    when (this) {
        CertificateUiError.PASSWORD_INVALID_OR_FILE -> R.string.error_password_or_file
        CertificateUiError.INVALID_PASSWORD -> R.string.error_invalid_password
        CertificateUiError.INVALID_PKCS12_FILE -> R.string.error_invalid_pkcs12_file
        CertificateUiError.UNSUPPORTED_PKCS12_ENCRYPTION -> R.string.error_unsupported_pkcs12_encryption
        CertificateUiError.FILE_TOO_LARGE -> R.string.error_file_too_large
        CertificateUiError.PRIVATE_KEY_MISSING -> R.string.error_private_key_missing
        CertificateUiError.MULTIPLE_PRIVATE_KEYS -> R.string.error_multiple_private_keys
        CertificateUiError.CERTIFICATE_NOT_X509 -> R.string.error_certificate_not_x509
        CertificateUiError.UNSUPPORTED_KEY_TYPE -> R.string.error_unsupported_key_type
        CertificateUiError.CERTIFICATE_EXPIRED -> R.string.error_certificate_expired
        CertificateUiError.CERTIFICATE_NOT_YET_VALID -> R.string.error_certificate_not_yet_valid
        CertificateUiError.KEY_USAGE_NOT_PERMITTED -> R.string.error_key_usage
        CertificateUiError.KEY_CERTIFICATE_MISMATCH -> R.string.error_key_mismatch
        CertificateUiError.UNSUPPORTED_FILE -> R.string.error_unsupported_file
        CertificateUiError.DOCUMENT_UNAVAILABLE -> R.string.error_document_unavailable
        CertificateUiError.PERMISSION_DENIED -> R.string.error_permission_denied
        CertificateUiError.CATALOG_FULL -> R.string.error_catalog_full
        CertificateUiError.ALREADY_SAVED -> R.string.error_already_saved
        CertificateUiError.STORAGE_FAILURE -> R.string.error_storage
        CertificateUiError.CERTIFICATE_NOT_SELECTED -> R.string.error_not_selected
        CertificateUiError.CERTIFICATE_NOT_USABLE -> R.string.error_not_usable
    },
)

private fun Instant.toDisplayDate(): String = DATE_FORMATTER.format(this)

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    .withZone(ZoneId.systemDefault())
