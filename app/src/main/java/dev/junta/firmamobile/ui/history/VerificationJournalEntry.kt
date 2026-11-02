package dev.junta.firmamobile.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.JuntaTone
import dev.junta.firmamobile.ui.JuntaToolTile

@Composable
internal fun verificationJournalEntry(onClick: () -> Unit) {
    JuntaToolTile(
        iconRes = R.drawable.ic_ui_history,
        title = stringResource(R.string.verification_journal_entry),
        onClick = onClick,
        tone = JuntaTone.VIOLET,
        modifier = Modifier.testTag("open-verification-journal"),
    )
}
