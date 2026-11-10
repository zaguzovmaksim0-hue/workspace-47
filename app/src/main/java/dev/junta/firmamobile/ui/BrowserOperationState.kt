package dev.junta.firmamobile.ui

/** Current owners of browser work. These are sampled at admission time, not cached by Compose. */
internal enum class BrowserOperation {
    EXTERNAL_AFIRMA,
    POPUP,
    CERTIFICATE_PANEL,
    IN_PLACE_CLIENT_AUTH,
    CLIENT_AUTH_TARGET,
    CERTIFICATE_SELECTION,
    LEGACY_AFIRMA,
    INTERACTIVE_CLIENT_AUTH,
    NATIVE_AFIRMA,
    NATIVE_FALLBACK,
    RETRIEVAL,
    CLIENT_AUTH_PREPARATION,
    CLIENT_AUTH_GRANT,
    SIGNING,
    CLIENT_CERT_PREFERENCES,
}

/** Each phase has different ownership: a completed retrieval must not block its own handoff. */
internal enum class BrowserAdmission {
    NATIVE_REQUEST,
    PREPARED_NATIVE_REQUEST,
    INTERACTIVE_CLIENT_AUTH,
    EXTERNAL_APP,
    EXTERNAL_AFIRMA,
    NATIVE_SLOT,
    POPUP_CONFIRMATION,
    POPUP_REQUEST,
}

internal class BrowserOperationState(operations: Set<BrowserOperation>) {
    private val active = operations.toSet()

    fun blocks(admission: BrowserAdmission): Boolean = active.any { it in blockersFor(admission) }

    private fun blockersFor(admission: BrowserAdmission): Set<BrowserOperation> = when (admission) {
        BrowserAdmission.NATIVE_REQUEST -> NATIVE_REQUEST_BLOCKERS
        BrowserAdmission.PREPARED_NATIVE_REQUEST -> PREPARED_REQUEST_BLOCKERS
        BrowserAdmission.INTERACTIVE_CLIENT_AUTH -> INTERACTIVE_AUTH_BLOCKERS
        BrowserAdmission.EXTERNAL_APP, BrowserAdmission.POPUP_REQUEST -> ALL_OPERATIONS
        // SigningAdmission owns the external slot separately; this callback reports portal owners only.
        BrowserAdmission.EXTERNAL_AFIRMA -> PORTAL_OPERATIONS
        BrowserAdmission.NATIVE_SLOT -> NATIVE_SLOT_OWNERS
        BrowserAdmission.POPUP_CONFIRMATION -> POPUP_CONFIRMATION_BLOCKERS
    }
}

private val NATIVE_SLOT_OWNERS = setOf(
    BrowserOperation.EXTERNAL_AFIRMA, BrowserOperation.POPUP, BrowserOperation.NATIVE_AFIRMA,
    BrowserOperation.NATIVE_FALLBACK, BrowserOperation.RETRIEVAL,
)
private val NATIVE_REQUEST_BLOCKERS = NATIVE_SLOT_OWNERS + setOf(
    BrowserOperation.IN_PLACE_CLIENT_AUTH, BrowserOperation.CLIENT_AUTH_TARGET,
    BrowserOperation.CERTIFICATE_SELECTION, BrowserOperation.LEGACY_AFIRMA,
    BrowserOperation.INTERACTIVE_CLIENT_AUTH, BrowserOperation.CLIENT_AUTH_PREPARATION, BrowserOperation.SIGNING,
)
// Retrieval's controller independently verifies foreground, current document and popup ownership.
private val PREPARED_REQUEST_BLOCKERS = NATIVE_REQUEST_BLOCKERS - setOf(
    BrowserOperation.RETRIEVAL, BrowserOperation.POPUP,
)
// The TLS controller decides whether another matching challenge may join its existing prompt.
private val INTERACTIVE_AUTH_BLOCKERS = NATIVE_REQUEST_BLOCKERS - BrowserOperation.INTERACTIVE_CLIENT_AUTH
private val ALL_OPERATIONS = BrowserOperation.entries.toSet()
private val PORTAL_OPERATIONS = ALL_OPERATIONS - BrowserOperation.EXTERNAL_AFIRMA
private val POPUP_CONFIRMATION_BLOCKERS = ALL_OPERATIONS - BrowserOperation.POPUP
