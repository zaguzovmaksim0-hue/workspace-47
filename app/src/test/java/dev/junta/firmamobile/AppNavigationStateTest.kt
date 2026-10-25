package dev.junta.firmamobile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun freshHostStartsOnCertificateWithThePanelClosed() {
        val navigation = AppNavigationState()
        assertEquals(AppDestination.Certificate, navigation.destination)
        assertFalse(navigation.certificatePanelVisible)
    }

    @Test
    fun commandsReplaceTheRootWithoutTouchingThePanel() {
        val navigation = AppNavigationState()
        val entry = URI("https://public.example/")
        navigation.showCatalog()
        assertEquals(AppDestination.Catalog, navigation.destination)
        navigation.openBrowser(null, entry)
        assertEquals(AppDestination.Browser(null, entry), navigation.destination)
        navigation.showCertificatePanel()
        assertTrue(navigation.certificatePanelVisible)
        assertEquals(AppDestination.Browser(null, entry), navigation.destination)
        navigation.hideCertificatePanel()
        assertFalse(navigation.certificatePanelVisible)
        navigation.showCertificate()
        assertEquals(AppDestination.Certificate, navigation.destination)
    }
}
