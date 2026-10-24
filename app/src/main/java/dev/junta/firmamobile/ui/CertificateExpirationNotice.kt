package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateExpirationPolicy
import dev.junta.firmamobile.certificate.CertificateExpirationWindow
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** In-app metadata only. This does not change validation, session state or signing eligibility. */
// Compose UI entry points use PascalCase by convention.
@Suppress("FunctionNaming")
@Composable
internal fun CertificateExpirationNotice(
    validUntil: Instant,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val now = rememberForegroundInstant(clock)
    val warning = CertificateExpirationPolicy.warning(validUntil, now, clock.zone) ?: return
    val timestamp = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss XXX")
        .withZone(clock.zone).format(validUntil)
    val title = stringResource(when (warning.window) {
        CertificateExpirationWindow.WITHIN_THIRTY_DAYS -> R.string.certificate_expiration_soon_title
        CertificateExpirationWindow.WITHIN_SEVEN_DAYS -> R.string.certificate_expiration_week_title
        CertificateExpirationWindow.TODAY -> R.string.certificate_expiration_today_title
        CertificateExpirationWindow.EXPIRED -> R.string.certificate_expiration_expired_title
    })
    val copy = if (warning.daysRemaining > 0L) {
        val days = warning.daysRemaining.toInt()
        pluralStringResource(R.plurals.certificate_expiration_days, days, days, timestamp)
    } else {
        stringResource(R.string.certificate_expiration_timestamp_copy, timestamp)
    }
    val urgent = warning.window != CertificateExpirationWindow.WITHIN_THIRTY_DAYS
    Surface(
        modifier = modifier.fillMaxWidth().testTag("certificate-expiration-notice")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        color = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (urgent) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            Text(copy, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Visible-screen refresh only; repeatOnLifecycle cancels the loop while the screen is stopped. */
@Composable
private fun rememberForegroundInstant(clock: Clock): Instant {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(clock.instant(), lifecycle, clock) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = clock.instant()
                delay(FOREGROUND_REFRESH_MILLIS)
            }
        }
    }
    return now
}

private const val FOREGROUND_REFRESH_MILLIS = 60_000L
