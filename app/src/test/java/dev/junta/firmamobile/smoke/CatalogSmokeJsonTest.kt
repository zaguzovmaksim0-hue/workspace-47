package dev.junta.firmamobile.smoke

import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CatalogSmokeJsonTest {
    @Test
    fun `schema two exposes bounded runtime evidence without arbitrary payload fields`() {
        val snapshot = CatalogSmokeRuntimeSnapshot(
            runId = "run-json",
            profileId = ProfileId("reg-age-redsara"),
            browserSessionBound = true,
            webViewActive = true,
            navigationEpoch = 2L,
            currentHost = "reg.redsara.es",
            currentPathLength = 4,
            currentPathSha256_8 = "8df12444",
            currentUrlAllowed = true,
            clientCertRequestObserved = true,
            clientCertAcceptedObserved = true,
            clientAuthConfirmationRequired = false,
            certificateSelectionRequired = false,
            afirmaRequestObserved = false,
            autofirmaIntentObserved = false,
            signingConfirmationRequired = false,
            signingStartedObserved = false,
            signingCompletedObserved = false,
            signingFailedObserved = false,
            portalCallbackObserved = false,
            renderProcessGone = false,
            failureCode = null,
            events = listOf(
                CatalogSmokeRuntimeEvent(
                    sequence = 1L,
                    code = CatalogSmokeEventCode.CLIENT_CERT_ACCEPTED,
                    navigationEpoch = 2L,
                    host = "reg.redsara.es",
                    detail = "443",
                ),
            ),
        )
        val serialized = CatalogSmokeOutcome(
            runId = "run-json",
            portalId = PortalId("age-reg-redsara"),
            profileId = ProfileId("reg-age-redsara"),
            adapterId = "client-tls-auth",
            entryUrl = "https://reg.redsara.es/es/",
            supportStatus = "IMPLEMENTED_NOT_E2E",
            result = CatalogSmokeResultCode.WEBVIEW_ACTIVE,
            runtime = snapshot,
        ).toJson()

        assertTrue(serialized.contains("\"schemaVersion\":2"))
        assertTrue(serialized.contains("\"clientCertAcceptedObserved\":true"))
        assertTrue(serialized.contains("\"currentPathLength\":4"))
        assertTrue(serialized.contains("\"currentPathSha256_8\":\"8df12444\""))
        assertFalse(serialized.contains("\"currentPath\":"))
        assertFalse(serialized.contains("\"path\":"))
        assertFalse(serialized.contains("privateKey", ignoreCase = true))
        assertFalse(serialized.contains("password", ignoreCase = true))
        assertFalse(serialized.contains("cookie", ignoreCase = true))
        assertFalse(serialized.contains("payload", ignoreCase = true))
    }
}
