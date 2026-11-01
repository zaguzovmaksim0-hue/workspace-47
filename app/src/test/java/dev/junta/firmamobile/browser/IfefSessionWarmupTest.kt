package dev.junta.firmamobile.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IfefSessionWarmupTest {
    private val deepLink =
        "https://sedeelectronica.ifef.es/portalCiudadano/sede/buscador.do?utm_source=chatgpt.com"
    private val redirectUrl =
        "https://sedeelectronica.ifef.es/portalCiudadano/portal/entidades.do?ent_id=5&idioma=1"

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
        assertFalse(IfefSessionWarmup.shouldWarmUp(redirectUrl, alreadyWarmed = false))
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
    fun `real redirect sequence loads warm up and target exactly once`() {
        val loads = mutableListOf<String>()
        val controller = newController(loads)

        assertTrue(controller.startIfEligible(deepLink, navigationEpoch = 10))
        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL), loads)

        controller.onNavigationStarted(IfefSessionWarmup.WARMUP_URL, navigationEpoch = 11)
        controller.onNavigationStarted(redirectUrl, navigationEpoch = 12)
        controller.onPageFinished(redirectUrl, navigationEpoch = 13)

        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL, deepLink), loads)
        assertTrue(controller.warmed)
    }

    @Test
    fun `second cold deep link in same session is not warmed again`() {
        val loads = mutableListOf<String>()
        val controller = newController(loads)

        assertTrue(controller.startIfEligible(deepLink, navigationEpoch = 10))
        controller.onNavigationStarted(IfefSessionWarmup.WARMUP_URL, navigationEpoch = 11)
        controller.onNavigationStarted(redirectUrl, navigationEpoch = 12)
        controller.onPageFinished(redirectUrl, navigationEpoch = 13)

        assertFalse(controller.startIfEligible(deepLink, navigationEpoch = 14))
        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL, deepLink), loads)
    }

    @Test
    fun `other host navigation during warm up cancels target`() {
        val loads = mutableListOf<String>()
        var timeoutAction: (() -> Unit)? = null
        val controller = IfefSessionWarmupController(
            scheduleTimeout = { action -> timeoutAction = action },
            loadTarget = loads::add,
        )

        assertTrue(controller.startIfEligible(deepLink, navigationEpoch = 10))
        controller.onNavigationStarted("https://example.com/", navigationEpoch = 11)
        controller.onPageFinished(redirectUrl, navigationEpoch = 12)
        timeoutAction?.invoke()

        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL), loads)
        assertFalse(controller.warmed)
    }

    @Test
    fun `timeout fails open and loads original target once`() {
        val loads = mutableListOf<String>()
        var timeoutAction: (() -> Unit)? = null
        val controller = IfefSessionWarmupController(
            scheduleTimeout = { action -> timeoutAction = action },
            loadTarget = loads::add,
        )

        assertTrue(controller.startIfEligible(deepLink, navigationEpoch = 10))
        timeoutAction?.invoke()

        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL, deepLink), loads)
        assertTrue(controller.warmed)
    }

    @Test
    fun `timeout after completion does nothing`() {
        val loads = mutableListOf<String>()
        var timeoutAction: (() -> Unit)? = null
        val controller = IfefSessionWarmupController(
            scheduleTimeout = { action -> timeoutAction = action },
            loadTarget = loads::add,
        )

        assertTrue(controller.startIfEligible(deepLink, navigationEpoch = 10))
        controller.onNavigationStarted(IfefSessionWarmup.WARMUP_URL, navigationEpoch = 11)
        controller.onNavigationStarted(redirectUrl, navigationEpoch = 12)
        controller.onPageFinished(redirectUrl, navigationEpoch = 13)
        timeoutAction?.invoke()

        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL, deepLink), loads)
    }

    @Test
    fun `unrelated same host navigation cancels warm up`() {
        val loads = mutableListOf<String>()
        val controller = newController(loads)

        assertTrue(controller.startIfEligible(deepLink, navigationEpoch = 10))
        controller.onNavigationStarted(
            "https://sedeelectronica.ifef.es/portalCiudadano/sede/page.do",
            navigationEpoch = 11,
        )
        controller.onPageFinished(redirectUrl, navigationEpoch = 12)

        assertEquals(listOf(IfefSessionWarmup.WARMUP_URL), loads)
        assertFalse(controller.warmed)
    }

    private fun newController(loads: MutableList<String>): IfefSessionWarmupController {
        return IfefSessionWarmupController(
            scheduleTimeout = {},
            loadTarget = loads::add,
        )
    }
}
