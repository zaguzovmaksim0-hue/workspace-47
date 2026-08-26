package dev.junta.firmamobile

import dev.junta.firmamobile.browser.JuntaNavigationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApplicationIdentityContractTest {
    @Test
    fun juntaFirmaMobileNeverUsesTheOfficialAutoFirmaPackageIdentity() {
        assertEquals(
            true,
            BuildConfig.APPLICATION_ID in setOf(
                "dev.junta.firmamobile",
                "dev.junta.firmamobile.redsarae2e",
            ),
        )
        assertNotEquals(JuntaNavigationPolicy.AUTOFIRMA_PACKAGE, BuildConfig.APPLICATION_ID)
        assertEquals("es.gob.afirma", JuntaNavigationPolicy.AUTOFIRMA_PACKAGE)
    }
}
