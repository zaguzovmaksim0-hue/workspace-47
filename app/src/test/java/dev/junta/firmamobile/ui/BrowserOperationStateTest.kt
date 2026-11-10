package dev.junta.firmamobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserOperationStateTest {
    @Test fun anIdleBrowserCanAdmitEveryKindOfOperation() {
        val idle = BrowserOperationState(emptySet())
        BrowserAdmission.entries.forEach { assertFalse(it.name, idle.blocks(it)) }
    }

    @Test fun anExistingConsentOrSignatureCannotBeReplacedByAnotherNativeRequest() {
        val conflicting = setOf(
            BrowserOperation.EXTERNAL_AFIRMA, BrowserOperation.POPUP,
            BrowserOperation.IN_PLACE_CLIENT_AUTH, BrowserOperation.CLIENT_AUTH_TARGET,
            BrowserOperation.CERTIFICATE_SELECTION, BrowserOperation.LEGACY_AFIRMA,
            BrowserOperation.INTERACTIVE_CLIENT_AUTH, BrowserOperation.NATIVE_AFIRMA,
            BrowserOperation.NATIVE_FALLBACK, BrowserOperation.RETRIEVAL,
            BrowserOperation.CLIENT_AUTH_PREPARATION, BrowserOperation.SIGNING,
        )
        for (operation in conflicting) {
            assertTrue(operation.name, state(operation).blocks(BrowserAdmission.NATIVE_REQUEST))
        }
    }

    @Test fun completedRetrievalCanHandOffItsOwnRequestButCannotReplaceAnotherConsent() {
        assertFalse(state(BrowserOperation.RETRIEVAL).blocks(BrowserAdmission.PREPARED_NATIVE_REQUEST))
        for (operation in listOf(BrowserOperation.NATIVE_AFIRMA, BrowserOperation.INTERACTIVE_CLIENT_AUTH,
            BrowserOperation.CERTIFICATE_SELECTION, BrowserOperation.SIGNING, BrowserOperation.EXTERNAL_AFIRMA)) {
            assertTrue(operation.name, state(operation).blocks(BrowserAdmission.PREPARED_NATIVE_REQUEST))
        }
    }

    @Test fun interactiveTlsCanCoalesceItsOwnPromptButCannotInterruptSigning() {
        assertFalse(state(BrowserOperation.INTERACTIVE_CLIENT_AUTH).blocks(BrowserAdmission.INTERACTIVE_CLIENT_AUTH))
        assertTrue(state(BrowserOperation.NATIVE_AFIRMA).blocks(BrowserAdmission.INTERACTIVE_CLIENT_AUTH))
        assertTrue(state(BrowserOperation.RETRIEVAL).blocks(BrowserAdmission.INTERACTIVE_CLIENT_AUTH))
        assertTrue(state(BrowserOperation.SIGNING).blocks(BrowserAdmission.INTERACTIVE_CLIENT_AUTH))
    }

    @Test fun anExternalAppCannotInterruptAnyOwnedBrowserOperation() {
        for (operation in BrowserOperation.entries) {
            assertTrue(operation.name, state(operation).blocks(BrowserAdmission.EXTERNAL_APP))
        }
    }

    @Test fun externalSigningAdmissionChecksAllPortalOwnersWithoutBlockingItself() {
        for (operation in BrowserOperation.entries.filter { it != BrowserOperation.EXTERNAL_AFIRMA }) {
            assertTrue(operation.name, state(operation).blocks(BrowserAdmission.EXTERNAL_AFIRMA))
        }
        assertFalse(state(BrowserOperation.EXTERNAL_AFIRMA).blocks(BrowserAdmission.EXTERNAL_AFIRMA))
    }

    @Test fun nativeSlotChecksOnlyTheNativeAndPopupOwners() {
        for (operation in listOf(BrowserOperation.EXTERNAL_AFIRMA, BrowserOperation.POPUP,
            BrowserOperation.NATIVE_AFIRMA, BrowserOperation.NATIVE_FALLBACK, BrowserOperation.RETRIEVAL)) {
            assertTrue(operation.name, state(operation).blocks(BrowserAdmission.NATIVE_SLOT))
        }
        assertFalse(state(BrowserOperation.IN_PLACE_CLIENT_AUTH).blocks(BrowserAdmission.NATIVE_SLOT))
        assertFalse(state(BrowserOperation.CERTIFICATE_SELECTION).blocks(BrowserAdmission.NATIVE_SLOT))
    }

    @Test fun aSnapshotCannotBeChangedThroughTheCallersMutableSet() {
        val active = mutableSetOf(BrowserOperation.SIGNING)
        val captured = BrowserOperationState(active)
        active.clear()
        assertTrue(captured.blocks(BrowserAdmission.NATIVE_REQUEST))
    }

    private fun state(operation: BrowserOperation) = BrowserOperationState(setOf(operation))
}
