package dev.junta.firmamobile.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.junta.firmamobile.catalog.PortalLaunchTarget

/**
 * Host-owned switch from ordinary browsing to a reviewed portal mode; absent where the host cannot
 * open one. [targetFor] only consults the bundled catalog for a top-level page URL. [open] starts a
 * fresh browser session at the returned registry-validated start URL, never at the page URL.
 */
internal class PortalModeActions(
    val targetFor: (String) -> PortalLaunchTarget?,
    val open: (PortalLaunchTarget) -> Unit,
)

internal val LocalPortalModeActions = staticCompositionLocalOf<PortalModeActions?> { null }
