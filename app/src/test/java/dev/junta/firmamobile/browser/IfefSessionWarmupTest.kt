package dev.junta.firmamobile.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IfefSessionWarmupTest {
    private val deepLink =
        "https://sedeelectronica.ifef.es/portalCiudadano/sede/buscador.do?utm_source=chatgpt.com"

    @Test
    fun `deep link requires warm up once then is already warmed`() {
        assertTrue(IfefSessionWarmup.shouldWarmUp(deepLink, alreadyWarmed = false))
        assertFalse(IfefSessionWarmup.shouldWarmUp(deepLink, alreadyWarmed = true))
    }

    @Test
    fun `exact https host is required`() {
        assertTrue(
            IfefSessionWarmup.shouldWarmUp(
                "https://sedeelectronica.ifef.es/portalCiudadano/sede/page.do",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `subdomain is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "https://evil.sedeelectronica.ifef.es/portalCiudadano/sede/page.do",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `host suffix is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "https://sedeelectronica.ifef.es.evil.com/portalCiudadano/sede/page.do",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `http is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "http://sedeelectronica.ifef.es/portalCiudadano/sede/page.do",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `userinfo is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "https://user@sedeelectronica.ifef.es/portalCiudadano/sede/page.do",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `explicit port is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "https://sedeelectronica.ifef.es:8443/portalCiudadano/sede/page.do",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `root url is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "https://sedeelectronica.ifef.es/",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `entidades url is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "https://sedeelectronica.ifef.es/portalCiudadano/portal/entidades.do?ent_id=5&idioma=1",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `url with entity context is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "https://sedeelectronica.ifef.es/portalCiudadano/sede/page.do?ent_id=5",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `other host is rejected`() {
        assertFalse(
            IfefSessionWarmup.shouldWarmUp(
                "https://example.com/portalCiudadano/sede/page.do",
                alreadyWarmed = false,
            ),
        )
    }

    @Test
    fun `invalid url is rejected`() {
        assertFalse(IfefSessionWarmup.shouldWarmUp("not a url", alreadyWarmed = false))
    }

    @Test
    fun `cold deep link loads root then original target once`() {
        val loads = mutableListOf<String>()
        var timeoutAction: (() -> Unit)? = null
        val controller = IfefSessionWarmupController(
            scheduleTimeout = { action -> timeoutAction = action },
            loadTarget = loads::add,
        )
        val target = "https://sedeelectronica.ifef.es/portalCiudadano/sede/buscador.do?utm_source=chatgpt.com"

        assertEquals(IfefSessionWarmup.WARMUP_URL, controller.startIfEligible(target, navigationEpoch = 7))
        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL), loads)

        assertEquals(target, controller.onPageFinished(IfefSessionWarmup.WARMUP_URL, navigationEpoch = 7))
        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL, target), loads)
        assertTrue(controller.warmed)

        assertNull(controller.startIfEligible(target, navigationEpoch = 8))
        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL, target), loads)
    }

    @Test
    fun `timeout fails open and loads original target`() {
        val loads = mutableListOf<String>()
        var timeoutAction: (() -> Unit)? = null
        val controller = IfefSessionWarmupController(
            scheduleTimeout = { action -> timeoutAction = action },
            loadTarget = loads::add,
        )
        val target = "https://sedeelectronica.ifef.es/portalCiudadano/sede/buscador.do"

        controller.startIfEligible(target, navigationEpoch = 3)
        timeoutAction?.invoke()

        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL, target), loads)
        assertTrue(controller.warmed)
    }

    @Test
    fun `epoch change prevents late target load`() {
        val loads = mutableListOf<String>()
        var timeoutAction: (() -> Unit)? = null
        val controller = IfefSessionWarmupController(
            scheduleTimeout = { action -> timeoutAction = action },
            loadTarget = loads::add,
        )
        val target = "https://sedeelectronica.ifef.es/portalCiudadano/sede/buscador.do"

        controller.startIfEligible(target, navigationEpoch = 3)
        controller.onNavigationStarted("https://example.com/", navigationEpoch = 4)
        assertNull(controller.onPageFinished(IfefSessionWarmup.WARMUP_URL, navigationEpoch = 4))
        timeoutAction?.invoke()

        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL), loads)
    }

    @Test
    fun `external navigation during warm up cancels it`() {
        val loads = mutableListOf<String>()
        var timeoutAction: (() -> Unit)? = null
        val controller = IfefSessionWarmupController(
            scheduleTimeout = { action -> timeoutAction = action },
            loadTarget = loads::add,
        )
        val target = "https://sedeelectronica.ifef.es/portalCiudadano/sede/buscador.do"

        controller.startIfEligible(target, navigationEpoch = 2)
        controller.onNavigationStarted("https://example.com/", navigationEpoch = 2)
        assertNull(controller.onPageFinished(IfefSessionWarmup.WARMUP_URL, navigationEpoch = 2))
        timeoutAction?.invoke()

        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL), loads)
    }
}
