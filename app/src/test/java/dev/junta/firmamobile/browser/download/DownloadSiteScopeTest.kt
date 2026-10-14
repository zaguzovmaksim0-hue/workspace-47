package dev.junta.firmamobile.browser.download

import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** OkHttp reads the public suffix list from its Android asset, as on the device. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DownloadSiteScopeTest {
    private val lookup = CookieLookup { "session=synthetic" }

    @Before fun initializeOkHttp() { OkHttp.initialize(ApplicationProvider.getApplicationContext()) }

    @Test fun subdomainOfTheSameOrganismShareTheSession() {
        val plan = DocumentDownloadPlan.create("https://www.organismo.es/tramite", "https://sede.organismo.es/file",
            "a.pdf", null, 1, "agent", lookup)!!
        assertTrue(plan.sameSite); assertTrue(plan.sendsSession)
        assertEquals("organismo.es", DocumentDownloadPlan.site("SEDE.organismo.es"))
    }

    @Test fun publicSuffixIsNotASharedSite() {
        val plan = DocumentDownloadPlan.create("https://uno.gob.es/p", "https://dos.gob.es/f", "a.pdf", null, 1, "agent", lookup)!!
        assertFalse(plan.sameSite); assertFalse(plan.sendsSession)
        assertEquals("uno.gob.es", DocumentDownloadPlan.site("uno.gob.es"))
    }
}
