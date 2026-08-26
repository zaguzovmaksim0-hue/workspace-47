package dev.junta.firmamobile

import dev.junta.firmamobile.browser.JuntaNavigationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationIdentityContractTest {
    @Test
    fun juntaFirmaMobileNeverUsesTheOfficialAutoFirmaPackageIdentity() {
        assertTrue(
            BuildConfig.APPLICATION_ID == "dev.junta.firmamobile" ||
                BuildConfig.APPLICATION_ID == "dev.junta.firmamobile.intente2e2",
        )
        assertNotEquals(JuntaNavigationPolicy.AUTOFIRMA_PACKAGE, BuildConfig.APPLICATION_ID)
        assertEquals("es.gob.afirma", JuntaNavigationPolicy.AUTOFIRMA_PACKAGE)
    }
}
