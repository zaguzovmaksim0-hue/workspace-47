package dev.junta.firmamobile.ui

import androidx.annotation.StringRes
import dev.junta.firmamobile.R
import dev.junta.firmamobile.profile.CompatibilityStatus

@StringRes
internal fun compatibilityStatusLabelRes(status: CompatibilityStatus): Int = when (status) {
    CompatibilityStatus.VERIFIED_E2E -> R.string.compatibility_status_verified_e2e
    CompatibilityStatus.VERIFIED_CONTRACT -> R.string.compatibility_status_verified_contract
    CompatibilityStatus.EXPERIMENTAL -> R.string.compatibility_status_experimental
    CompatibilityStatus.BROWSE_ONLY -> R.string.compatibility_status_browse_only
    CompatibilityStatus.UNSUPPORTED -> R.string.compatibility_status_unsupported
}
