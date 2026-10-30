package dev.junta.firmamobile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationHostTest {
    @Test
    fun freshHostStartsOnCertificateWithClosedPanel() {
        val navigation = AppNavigationState()

        assertEquals(AppDestination.Certificate, navigation.destination)
        assertFalse(navigation.certificatePanelVisible)
        assertNull(navigation.activeBrowserSessionId)
    }

    @Test
    fun browserRouteContainsOnlyOpaqueSessionId() {
        val navigation = AppNavigationState()
        val entryUrl = URI("https://public.example/secret-path?query=private")

        val sessionId = navigation.openBrowser(null, entryUrl)
        val route = AppNavigationRoutes.browser(sessionId)

        assertEquals("browser/$sessionId", route)
        assertFalse(route.contains(entryUrl.toASCIIString()))
        assertEquals(AppDestination.Browser(null, entryUrl), navigation.destination)
    }

    @Test
    fun unknownBrowserHandleFailsClosedToCertificate() {
        val navigation = AppNavigationState()

        assertNull(navigation.resolveBrowserSession("restored-after-process-death"))
        navigation.showCertificate()

        assertEquals(AppDestination.Certificate, navigation.destination)
        assertNull(navigation.activeBrowserSessionId)
    }

    @Test
    fun browserExitInvalidatesSessionAndReplacesRootWithCatalog() {
        val navigation = AppNavigationState()
        val sessionId = navigation.openBrowser(null, URI("https://public.example/"))

        navigation.invalidateBrowserSession(sessionId)
        navigation.showCatalog()

        assertNull(navigation.resolveBrowserSession(sessionId))
        assertEquals(AppDestination.Catalog, navigation.destination)
        assertNull(navigation.activeBrowserSessionId)
    }

    @Test
    fun certificatePanelRemainsOverlayOverBrowser() {
        val navigation = AppNavigationState()
        navigation.openBrowser(null, URI("https://public.example/"))

        navigation.showCertificatePanel()

        assertTrue(navigation.certificatePanelVisible)
        assertTrue(navigation.destination is AppDestination.Browser)

        navigation.hideCertificatePanel()

        assertFalse(navigation.certificatePanelVisible)
        assertTrue(navigation.destination is AppDestination.Browser)
    }

    @Test
    fun clearBrowserSessionsMakesAllHandlesUnresolvable() {
        val navigation = AppNavigationState()
        val first = navigation.openBrowser(null, URI("https://public.example/one"))
        val second = navigation.openBrowser(null, URI("https://public.example/two"))

        navigation.clearBrowserSessions()

        assertNull(navigation.resolveBrowserSession(first))
        assertNull(navigation.resolveBrowserSession(second))
        assertNotEquals(first, second)
    }
}
