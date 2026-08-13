package dev.junta.firmamobile.network

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class JuntaOriginPolicyTest {
    private val junta = ProfileId("junta-andalucia")
    private val redSara = ProfileId("reg-age-redsara")
    private val unizar = ProfileId("unizar-tramitador")
    private val carneJoven = ProfileId("carne-joven-andalucia")
    private val juntaOfvirtual = ProfileId("junta-ofvirtual")
    private val education = ProfileId("educacion-convocatoria")
    private val aragon = ProfileId("aragon-siraw")
    private val aeat = ProfileId("aeat-mis-datos-censales")
    private val dgt = ProfileId("dgt-verificacion-equipo")
    private val ugr = ProfileId("ugr-certificado-login")
    private val cantabria = ProfileId("cantabria-rec-cert-login")
    private val jccm = ProfileId("jccm-certificate-login-probe")
    private val sevilla = ProfileId("sevilla-atse-certificate-login")
    private val melilla = ProfileId("melilla-sede")
    private val extremadura = ProfileId("extremadura-tramites")
    private val valladolid = ProfileId("diputacion-valladolid-sede")
    private val toledo = ProfileId("diputacion-toledo-sede")

    @Test
    fun keepsTheCatalogUnionOnlyForGenericNonBrowserResolution() {
        val expectedHosts = setOf(
            "www.juntadeandalucia.es",
            "sede.juntadeandalucia.es",
            "ssoweb.juntadeandalucia.es",
            "pfirma.juntadeandalucia.es",
            "ws024.juntadeandalucia.es",
            "ws050.juntadeandalucia.es",
            "reg.redsara.es",
            "tramita.unizar.es",
            "ws104.juntadeandalucia.es",
            "ws235.juntadeandalucia.es",
            "ws072.juntadeandalucia.es",
            "sede.educacion.gob.es",
            "aplicaciones.aragon.es",
            "sede.agenciatributaria.gob.es",
            "www1.agenciatributaria.gob.es",
            "sede.dgt.gob.es",
            "sede.ugr.es",
            "rec.cantabria.es",
            "ventanillaelectronica.jccm.es",
            "www.sevilla.org",
            "sede.melilla.es",
            "tramites.juntaex.es",
            "www.sede.diputaciondevalladolid.es",
            "diputacion.toledo.gob.es",
        )

        assertEquals(expectedHosts, JuntaOriginPolicy.allowedHosts)
        expectedHosts.forEach { host ->
            assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://$host/path")))
            assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://$host:443/path")))
        }
    }

    @Test
    fun browserAndBridgeAllowlistsAreProfileScoped() {
        assertEquals(
            setOf(
                "www.juntadeandalucia.es",
                "sede.juntadeandalucia.es",
                "ssoweb.juntadeandalucia.es",
                "pfirma.juntadeandalucia.es",
                "ws024.juntadeandalucia.es",
                "ws050.juntadeandalucia.es",
            ),
            JuntaOriginPolicy.browserAllowedHosts(junta),
        )
        assertEquals(setOf("reg.redsara.es"), JuntaOriginPolicy.browserAllowedHosts(redSara))
        assertEquals(setOf("tramita.unizar.es"), JuntaOriginPolicy.browserAllowedHosts(unizar))
        assertEquals(setOf("ws104.juntadeandalucia.es"), JuntaOriginPolicy.browserAllowedHosts(carneJoven))
        assertEquals(setOf("ws072.juntadeandalucia.es"), JuntaOriginPolicy.browserAllowedHosts(juntaOfvirtual))
        assertEquals(setOf("sede.educacion.gob.es"), JuntaOriginPolicy.browserAllowedHosts(education))
        assertEquals(setOf("aplicaciones.aragon.es"), JuntaOriginPolicy.browserAllowedHosts(aragon))
        assertEquals(setOf("sede.agenciatributaria.gob.es"), JuntaOriginPolicy.browserAllowedHosts(aeat))
        assertEquals(setOf("sede.dgt.gob.es"), JuntaOriginPolicy.browserAllowedHosts(dgt))
        assertEquals(setOf("sede.ugr.es"), JuntaOriginPolicy.browserAllowedHosts(ugr))
        assertEquals(setOf("rec.cantabria.es"), JuntaOriginPolicy.browserAllowedHosts(cantabria))

        assertEquals(
            setOf("https://www.juntadeandalucia.es"),
            JuntaOriginPolicy.webMessageOriginRules(junta),
        )
        assertEquals(
            setOf("https://reg.redsara.es"),
            JuntaOriginPolicy.webMessageOriginRules(redSara),
        )
        assertEquals(
            setOf("https://tramita.unizar.es"),
            JuntaOriginPolicy.webMessageOriginRules(unizar),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(carneJoven).isEmpty())
        assertEquals(
            setOf("https://ws072.juntadeandalucia.es"),
            JuntaOriginPolicy.webMessageOriginRules(juntaOfvirtual),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(education).isEmpty())
        assertEquals(
            setOf("https://aplicaciones.aragon.es"),
            JuntaOriginPolicy.webMessageOriginRules(aragon),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(aeat).isEmpty())
        assertEquals(setOf("https://sede.dgt.gob.es"), JuntaOriginPolicy.webMessageOriginRules(dgt))
        assertEquals(setOf("https://sede.ugr.es"), JuntaOriginPolicy.webMessageOriginRules(ugr))
        assertEquals(
            setOf("https://rec.cantabria.es"),
            JuntaOriginPolicy.webMessageOriginRules(cantabria),
        )
        assertEquals(
            setOf("ventanillaelectronica.jccm.es"),
            JuntaOriginPolicy.browserAllowedHosts(jccm),
        )
        assertEquals(setOf("www.sevilla.org"), JuntaOriginPolicy.browserAllowedHosts(sevilla))
        assertEquals(setOf("sede.melilla.es"), JuntaOriginPolicy.browserAllowedHosts(melilla))
        assertEquals(
            setOf("tramites.juntaex.es"),
            JuntaOriginPolicy.browserAllowedHosts(extremadura),
        )
        assertEquals(
            setOf("www.sede.diputaciondevalladolid.es"),
            JuntaOriginPolicy.browserAllowedHosts(valladolid),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(valladolid).isEmpty())
        assertEquals(
            setOf("diputacion.toledo.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(toledo),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(toledo).isEmpty())
        assertEquals(
            setOf("https://ventanillaelectronica.jccm.es"),
            JuntaOriginPolicy.webMessageOriginRules(jccm),
        )
        assertEquals(
            setOf("https://www.sevilla.org"),
            JuntaOriginPolicy.webMessageOriginRules(sevilla),
        )
        assertEquals(
            setOf("https://sede.melilla.es"),
            JuntaOriginPolicy.webMessageOriginRules(melilla),
        )
        assertEquals(
            setOf("https://tramites.juntaex.es"),
            JuntaOriginPolicy.webMessageOriginRules(extremadura),
        )
    }

    @Test
    fun profileScopedResolutionRejectsOtherCatalogProfiles() {
        assertTrue(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://ssoweb.juntadeandalucia.es/login"),
                junta,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://reg.redsara.es/es/"),
                junta,
            ),
        )
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.juntadeandalucia.es/path"),
                junta,
            ),
        )
        assertEquals(
            "www.juntadeandalucia.es",
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://www.juntadeandalucia.es/login"),
                junta,
            )?.host,
        )
    }

    @Test
    fun rejectsHttpUserInfoNonDefaultPortsAndLookalikeHosts() {
        val rejected = listOf(
            "http://www.juntadeandalucia.es/",
            "https://user@www.juntadeandalucia.es/",
            "https://www.juntadeandalucia.es:8443/",
            "https://www.juntadeandalucia.es.evil.example/",
            "https://juntadeandalucia.es/",
            "https://www.juntadeandalucia.es./",
            "https://xn--juntadeandaluca-2nb.example/",
            "https://127.0.0.1/",
        )

        rejected.forEach { rawUrl ->
            assertFalse(rawUrl, JuntaOriginPolicy.isAllowed(Uri.parse(rawUrl)))
            assertNull(rawUrl, JuntaOriginPolicy.originFor(Uri.parse(rawUrl)))
            assertNull(rawUrl, JuntaOriginPolicy.originFor(Uri.parse(rawUrl), junta))
        }
    }

    @Test
    fun serializesCanonicalOriginWithoutPathQueryOrFragment() {
        val origin = JuntaOriginPolicy.originFor(
            Uri.parse("https://WWW.JUNTADEANDALUCIA.ES:443/a?b=c#fragment"),
            junta,
        )

        assertEquals(
            TrustedOrigin("https", "www.juntadeandalucia.es", 443),
            origin,
        )
        assertEquals("https://www.juntadeandalucia.es", origin?.serialized)
    }
}
