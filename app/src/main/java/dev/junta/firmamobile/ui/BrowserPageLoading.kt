package dev.junta.firmamobile.ui

/** The UI renders an indeterminate indicator; percentage updates need not recompose it. */
internal fun browserPageLoading(progress: Int): Boolean = progress in 0..99
