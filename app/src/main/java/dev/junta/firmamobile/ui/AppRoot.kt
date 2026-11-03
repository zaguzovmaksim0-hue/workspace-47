package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.CertificateUnlockRetention
import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.ui.history.verificationJournalHome
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val STATE_FADE_IN_MILLIS = 260
private const val STATE_FADE_OUT_MILLIS = 120
private const val ISSUER_WEIGHT = 1.2f
private const val EXPIRY_WEIGHT = 0.8f
private val HomeGutter = 20.dp

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
    val options = CertificateOptions(onLock = onLock, onSelectCertificate = onSelectCertificate, onForget = onForget)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JuntaPaper),
    ) {
        JuntaBackdrop(modifier = Modifier.fillMaxSize().testTag("jfm-home-background"))
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = viewport)
                        .padding(horizontal = HomeGutter),
                ) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HomeHeader(state)
                    Spacer(modifier = Modifier.height(18.dp))
                    CertificateReader(state)
                    CertificatePlate(state, options)
                    Spacer(modifier = Modifier.height(10.dp))
                    CertificateBody(
                        state = state,
                        onSelectCertificate = onSelectCertificate,
                        onUnlock = onUnlock,
                        onRestoreSavedUnlock = onRestoreSavedUnlock,
                        onContinue = onContinue,
                    )
                    if (state !is CertificateUiState.Unlocked) BrowseWithoutCertificate(onContinue)
                    Spacer(modifier = Modifier.height(24.dp))
                    JuntaTileGrid {
                        localFileSigningEntry(state is CertificateUiState.Unlocked)
                        verificationJournalHome(verificationJournal)
                        certificateReminderSettings()
                    }
                    savedCertificatesSection(state)
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    HomeFooter(state = state, onLegalInformation = { legalInformationVisible = true })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/** Opens the catalogue without unlocking anything first. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun BrowseWithoutCertificate(onContinue: () -> Unit) {
    Spacer(modifier = Modifier.height(12.dp))
    JuntaOutlinedAction(
        text = stringResource(R.string.browse_without_certificate),
        onClick = onContinue,
        modifier = Modifier.testTag("browse-without-certificate"),
        iconRes = R.drawable.ic_ui_globe,
    )
}

/** Lowercase wordmark, the disclosure under it and the device status LED. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun HomeHeader(state: CertificateUiState) {
    val appName = stringResource(R.string.app_name)
    val locale = currentLocale()
    val (statusRes, led) = homeStatus(state)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .testTag("jfm-brand-title")
                    .clearAndSetSemantics {
                        text = AnnotatedString(appName)
                        heading()
                    },
            ) {
                Text(
                    text = appName.lowercase(locale),
                    color = JuntaInk,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 21.sp,
                    letterSpacing = (-0.4).sp,
                )
            }
            Text(
                text = stringResource(R.string.unofficial_disclosure),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FirmaLed(led)
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = stringResource(statusRes),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 14.5.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun homeStatus(state: CertificateUiState): Pair<Int, FirmaLedState> {
    val keySelected = LocalSavedCertificates.current?.state?.activeKey != null
    return when (state) {
        CertificateUiState.LoadingReference -> R.string.home_status_loading to FirmaLedState.BUSY
        is CertificateUiState.NoCertificate -> when {
            state.error != null -> R.string.home_status_none to FirmaLedState.ALERT
            keySelected -> R.string.home_status_locked to FirmaLedState.OFF
            else -> R.string.home_status_none to FirmaLedState.OFF
        }
        is CertificateUiState.Locked ->
            R.string.home_status_locked to if (state.error != null) FirmaLedState.ALERT else FirmaLedState.OFF
        is CertificateUiState.Unlocking -> R.string.home_status_unlocking to FirmaLedState.BUSY
        is CertificateUiState.Unlocked -> R.string.home_status_ready to FirmaLedState.ON
    }
}

/** The reader with whatever is inserted: the printed card, a protected card or the empty outline. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateReader(state: CertificateUiState) {
    val selectedKey = LocalSavedCertificates.current?.state?.activeKey?.signingKey
    val inserted = state.insertedKey(selectedAlias = selectedKey?.alias)
    FirmaCardReader(insertedKey = inserted, modifier = Modifier.testTag("jfm-certificate-card")) {
        when (state) {
            CertificateUiState.LoadingReference -> FirmaGhostCard(text = null)
            is CertificateUiState.NoCertificate -> if (selectedKey != null) {
                ProtectedCard(copy = stringResource(R.string.saved_keystore_locked))
            } else {
                FirmaGhostCard(text = stringResource(R.string.certificate_copy))
            }
            is CertificateUiState.Locked -> state.summary?.let { SummaryCard(it, locked = true) }
                ?: ProtectedCard(copy = stringResource(R.string.certificate_status_locked_copy))
            is CertificateUiState.Unlocking -> state.summary?.let { SummaryCard(it, locked = true) }
                ?: ProtectedCard(copy = stringResource(R.string.certificate_status_unlocking_copy))
            is CertificateUiState.Unlocked -> SummaryCard(state.summary, locked = false)
        }
    }
}

/** What is in the reader; a new value plays the insert animation once. */
private fun CertificateUiState.insertedKey(selectedAlias: Any?): Any? = when (this) {
    CertificateUiState.LoadingReference -> null
    is CertificateUiState.NoCertificate -> selectedAlias
    is CertificateUiState.Locked -> reference.uri
    is CertificateUiState.Unlocking -> reference.uri
    is CertificateUiState.Unlocked -> reference.uri
}

/** Holder, issuer and expiry printed on the card; each field reads as its full sentence. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun SummaryCard(summary: CertificateSummary, locked: Boolean) {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val owner = certificateDisplayNameText(summary.ownerName)
    val issuer = certificateDisplayNameText(summary.issuerName)
    FirmaCard(locked = locked) {
        FirmaCardField(
            label = stringResource(R.string.card_holder_label),
            value = owner.uppercase(locale),
            spoken = stringResource(R.string.certificate_owner, owner),
            mono = true,
            maxLines = 2,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            FirmaCardField(
                label = stringResource(R.string.card_issuer_label),
                value = issuer,
                spoken = stringResource(R.string.certificate_issuer, issuer),
                modifier = Modifier.weight(ISSUER_WEIGHT),
                maxLines = 2,
            )
            Spacer(modifier = Modifier.width(12.dp))
            FirmaCardField(
                label = stringResource(R.string.card_valid_until_label),
                value = CARD_EXPIRY.withZone(zone).format(summary.validUntil),
                spoken = stringResource(
                    R.string.certificate_validity,
                    ISO_DATE.withZone(zone).format(summary.validFrom),
                    ISO_DATE.withZone(zone).format(summary.validUntil),
                ),
                modifier = Modifier.weight(EXPIRY_WEIGHT),
                mono = true,
            )
        }
    }
}

/** A card whose contents stay unreadable until the password opens it. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun ProtectedCard(copy: String) {
    FirmaCard(locked = true) {
        Text(
            text = stringResource(R.string.certificate_status_locked_title),
            color = Firma.cardInk,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = copy,
            color = Firma.cardMuted,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
        )
    }
}

/**
 * The strip under the reader: the inserted file's name, the eject key and the options key.
 * The name is printed alone but announced as "Certificate selected: name".
 */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificatePlate(state: CertificateUiState, options: CertificateOptions) {
    val selectedKey = LocalSavedCertificates.current?.state?.activeKey?.signingKey
    val name = when (state) {
        CertificateUiState.LoadingReference -> null
        is CertificateUiState.NoCertificate -> selectedKey?.displayName
        is CertificateUiState.Locked -> state.reference.displayName
        is CertificateUiState.Unlocking -> state.reference.displayName
        is CertificateUiState.Unlocked -> state.reference.displayName
    }?.let { certificateDisplayNameText(it) } ?: return
    val spoken = stringResource(R.string.certificate_selected, name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 2.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (state is CertificateUiState.NoCertificate) {
                        Modifier.testTag("selected-keystore-certificate")
                    } else {
                        Modifier
                    },
                )
                .clearAndSetSemantics { text = AnnotatedString(spoken) },
        ) {
            Text(
                text = name,
                color = JuntaMutedInk,
                style = firmaMono(13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.offersCertificateOptions()) {
            Spacer(modifier = Modifier.width(10.dp))
            EjectKey(onClick = options.onSelectCertificate)
            CertificateOptionsMenu(state, options)
        }
    }
}

/** "Eject": a light pill key that swaps the inserted certificate for another file. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun EjectKey(onClick: () -> Unit) {
    val description = stringResource(R.string.home_eject_description)
    FirmaCompactKey(
        onClick = onClick,
        style = FirmaKeyStyle.LIGHT,
        shape = CircleShape,
        faceModifier = Modifier.height(34.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_eject),
                contentDescription = null,
                tint = Firma.keyLightInk,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_eject),
                color = Firma.keyLightInk,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.5.sp,
            )
        }
    }
}

/** Header menu actions; destructive and secondary choices live here instead of extra buttons. */
private class CertificateOptions(
    val onLock: () -> Unit,
    val onSelectCertificate: () -> Unit,
    val onForget: () -> Unit,
)

private fun CertificateUiState.offersCertificateOptions(): Boolean =
    this is CertificateUiState.Unlocked || (this is CertificateUiState.Locked && choices.isEmpty())

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateOptionsMenu(state: CertificateUiState, options: CertificateOptions) {
    var expanded by remember(state::class) { mutableStateOf(false) }
    val description = stringResource(R.string.certificate_options)
    Box {
        FirmaCompactKey(
            onClick = { expanded = true },
            style = FirmaKeyStyle.LIGHT,
            shape = CircleShape,
            faceModifier = Modifier.size(width = 34.dp, height = 34.dp),
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_browser_more),
                contentDescription = null,
                tint = Firma.keyLightInk,
                modifier = Modifier.size(20.dp),
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
            )
        },
        leadingIcon = { JuntaIconBubble(iconRes = iconRes, tone = tone, size = 32.dp, iconSize = 17.dp) },
        onClick = onClick,
    )
}

/** What the device asks for next: select, unlock, wait or continue. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateBody(
    state: CertificateUiState,
    onSelectCertificate: () -> Unit,
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit,
    onRestoreSavedUnlock: (() -> Unit)?,
    onContinue: () -> Unit,
) {
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            fadeIn(tween(durationMillis = STATE_FADE_IN_MILLIS))
                .togetherWith(fadeOut(tween(durationMillis = STATE_FADE_OUT_MILLIS)))
                .using(SizeTransform(clip = false))
        },
        label = "certificate-state",
    ) { current ->
        Column(modifier = Modifier.fillMaxWidth()) {
            when (current) {
                CertificateUiState.LoadingReference -> LoadingCertificate()
                is CertificateUiState.NoCertificate -> NoCertificate(current, onSelectCertificate)
                is CertificateUiState.Locked -> if (current.choices.isNotEmpty()) {
                    certificateEntryChooser(current)
                } else {
                    LockedCertificate(current, onUnlock, onRestoreSavedUnlock)
                }
                is CertificateUiState.Unlocking ->
                    FirmaBusyDisplay(text = stringResource(R.string.certificate_unlocking))
                is CertificateUiState.Unlocked -> UnlockedCertificate(current, onContinue)
            }
        }
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun LoadingCertificate() {
    Row(
        modifier = Modifier.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FirmaLed(FirmaLedState.BUSY)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = stringResource(R.string.certificate_loading), color = JuntaMutedInk)
    }
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun NoCertificate(state: CertificateUiState.NoCertificate, onSelectCertificate: () -> Unit) {
    state.error?.let { CertificateError(it) }
    JuntaMainKey(
        text = stringResource(R.string.select_certificate),
        onClick = onSelectCertificate,
        trailingIconRes = R.drawable.ic_ui_add,
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun LockedCertificate(
    state: CertificateUiState.Locked,
    onUnlock: (CharArray, CertificateUnlockRetention) -> Unit,
    onRestoreSavedUnlock: (() -> Unit)?,
) {
    state.error?.let { CertificateError(it) }
    state.summary?.let {
        CertificateExpirationNotice(validUntil = it.validUntil, modifier = Modifier.padding(bottom = 14.dp))
    }
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
        Spacer(modifier = Modifier.height(10.dp))
    }
    val canUnlock = passwordState.text.isNotEmpty() && !state.revocationPending
    val submit = {
        val password = CharArray(passwordState.text.length) { index -> passwordState.text[index] }
        passwordState.clearText()
        onUnlock(password, retention)
    }
    CertificatePasswordField(passwordState, onDone = { if (canUnlock) submit() })
    Spacer(modifier = Modifier.height(16.dp))
    CertificateUnlockRetentionSelector(retention, !state.revocationPending) { retention = it }
    Spacer(modifier = Modifier.height(20.dp))
    JuntaMainKey(
        text = stringResource(R.string.unlock_certificate),
        onClick = submit,
        trailingIconRes = R.drawable.ic_ui_arrow_forward,
        enabled = canUnlock,
    )
    SavedCertificateUnlockAction(onRestoreSavedUnlock, !state.revocationPending)
}

/** The password goes into a recess of the body; focus draws the accent ring of the device. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificatePasswordField(passwordState: TextFieldState, onDone: () -> Unit) {
    val label = stringResource(R.string.certificate_password)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = Firma.accent
    val shape = JuntaFieldShape
    BasicSecureTextField(
        state = passwordState,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        textStyle = TextStyle(color = JuntaInk, fontSize = 18.sp, letterSpacing = 1.5.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        interactionSource = interaction,
        cursorBrush = SolidColor(ring),
        decorator = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .firmaBodyRecess(shape)
                    .firmaFocusRing(shape, ring, visible = focused, gap = 2.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ui_lock),
                    contentDescription = null,
                    tint = JuntaMutedInk,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (passwordState.text.isEmpty()) {
                        Text(
                            text = label,
                            color = JuntaMutedInk,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    field()
                }
            }
        },
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun UnlockedCertificate(state: CertificateUiState.Unlocked, onContinue: () -> Unit) {
    FirmaValidityDisplay(summary = state.summary)
    CertificateExpirationNotice(
        validUntil = state.summary.validUntil,
        modifier = Modifier.padding(top = 12.dp),
    )
    Spacer(modifier = Modifier.height(22.dp))
    JuntaMainKey(
        text = stringResource(R.string.continue_to_portal),
        onClick = onContinue,
        trailingIconRes = R.drawable.ic_ui_arrow_forward,
    )
}

/** Session or privacy reminder on the left, legal information and the version on the right. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun HomeFooter(state: CertificateUiState, onLegalInformation: () -> Unit) {
    val version = BuildConfig.VERSION_NAME
    val versionSpoken = stringResource(R.string.legal_version_value, version)
    Column(modifier = Modifier.fillMaxWidth()) {
        FirmaDivider()
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(
                    if (state is CertificateUiState.Unlocked) {
                        R.string.certificate_status_unlocked_copy
                    } else {
                        R.string.privacy_copy
                    },
                ),
                modifier = Modifier.weight(1f).padding(top = 12.dp),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button, onClick = onLegalInformation)
                        .padding(start = 8.dp)
                        .testTag("open-legal-information"),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = stringResource(R.string.legal_information),
                        color = JuntaInk,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 14.sp,
                    )
                }
                Text(
                    text = "v$version",
                    color = JuntaMutedInk,
                    style = firmaMono(13.sp),
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(versionSpoken) },
                )
            }
        }
    }
}

/** Errors light the red LED next to a sentence that is announced at once. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun CertificateError(error: CertificateUiError) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .firmaBodyRecess(JuntaFieldShape, depth = 3.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FirmaLed(FirmaLedState.ALERT, modifier = Modifier.padding(top = 6.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = error.message(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
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

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val CARD_EXPIRY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yy")
