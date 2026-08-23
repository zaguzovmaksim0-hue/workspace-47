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
    private val juntaVeaPeg = ProfileId("junta-andalucia-vea-peg")
    private val madridRegistro = ProfileId("comunidad-madrid-registro-general")
    private val education = ProfileId("educacion-convocatoria")
    private val aragon = ProfileId("aragon-siraw")
    private val aeat = ProfileId("aeat-mis-datos-censales")
    private val dgt = ProfileId("dgt-verificacion-equipo")
    private val ugr = ProfileId("ugr-certificado-login")
    private val cantabria = ProfileId("cantabria-rec-cert-login")
    private val jccm = ProfileId("jccm-certificate-login-probe")
    private val jccmRegistro = ProfileId("jccm-registro-generico")
    private val mites = ProfileId("mites-certificate-login")
    private val sevilla = ProfileId("sevilla-atse-certificate-login")
    private val airef = ProfileId("airef-instancia-general")
    private val cdti = ProfileId("cdti-certificate-validation")
    private val transportes = ProfileId("transportes-qys-cert-login")
    private val sepes = ProfileId("sepes-transportes-public-complaints")
    private val melilla = ProfileId("melilla-sede")
    private val ceuta = ProfileId("ceuta-sede")
    private val extremadura = ProfileId("extremadura-tramites")
    private val navarra = ProfileId("navarra-sede-registro-general")
    private val valladolid = ProfileId("diputacion-valladolid-sede")
    private val burgos = ProfileId("diputacion-burgos-portal")
    private val laPalma = ProfileId("la-palma-sede-electronica")
    private val huesca = ProfileId("diputacion-huesca-portal")
    private val lugo = ProfileId("diputacion-lugo-sede")
    private val leon = ProfileId("diputacion-leon-sede")
    private val albacete = ProfileId("diputacion-albacete-portal")
    private val sanidad = ProfileId("ministerio-sanidad-certificado")
    private val tea = ProfileId("tea-alegaciones-certificado")
    private val tenerife = ProfileId("tenerife-sede-electronica")
    private val transparencia = ProfileId("age-portal-de-la-transparencia")
    private val toledo = ProfileId("diputacion-toledo-sede")
    private val valencia = ProfileId("diputacion-valencia-sede")
    private val policia = ProfileId("policia-solicitud-generica")
    private val lleida = ProfileId("diputacion-lleida-sede")
    private val badajoz = ProfileId("diputacion-badajoz-portal")
    private val xunta = ProfileId("xunta-galicia-solicitude-xenerica")
    private val canarias = ProfileId("canarias-sede")
    private val oepm = ProfileId("oepm-protegeo-general")
    private val funciona = ProfileId("portal-funciona-public-home")
    private val asturiasSede = ProfileId("asturias-sede-tramite-navigation")
    private val dgsfp = ProfileId("dgsfp-sede-public-home")
    private val mjusticia = ProfileId("mjusticia-fundaciones-idp75")
    private val cnmv = ProfileId("cnmv-sede-public-home")
    private val fuerteventura = ProfileId("fuerteventura-sede-electronica")
    private val alava = ProfileId("diputacion-alava-registro-comun")
    private val barcelona2057 = ProfileId("diputacion-barcelona-solicitud-generica-2057")
    private val avila = ProfileId("diputacion-avila-instancia-general")
    private val ctbg = ProfileId("ctbg-solicitud-informacion")
    private val catastro = ProfileId("catastro-solicitudes-genericas")
    private val fega = ProfileId("fega-solicitud-general-ofvsg02")
    private val murcia = ProfileId("murcia-carm-pase")
    private val dgoj = ProfileId("dgoj-public-navigation")

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
            "veaja.cloud.juntadeandalucia.es",
            "gestiona.comunidad.madrid",
            "sede.educacion.gob.es",
            "www.educacion.gob.es",
            "aplicaciones.aragon.es",
            "sede.agenciatributaria.gob.es",
            "www1.agenciatributaria.gob.es",
            "sede.dgt.gob.es",
            "sede.ugr.es",
            "rec.cantabria.es",
            "ventanillaelectronica.jccm.es",
            "registrounicociudadanos.jccm.es",
            "sso.jccm.es",
            "sede.mites.gob.es",
            "www.sevilla.org",
            "sede.airef.es",
            "sedemugeju.gob.es",
            "pasarela.clave.gob.es",
            "pasarela-ident.clave.gob.es",
            "sede.cdti.gob.es",
            "sede.transportes.gob.es",
            "sede.melilla.es",
            "sede.ceuta.es",
            "tramites.juntaex.es",
            "pattex.juntaex.es",
            "www.navarra.es",
            "administracionelectronica.navarra.es",
            "ateka.navarra.es",
            "www.sede.diputaciondevalladolid.es",
            "diputacionalicante.sedelectronica.es",
            "registro.diputaciondeburgos.es",
            "sedeelectronica.cabildodelapalma.es",
            "ovc24.dphuesca.es",
            "sede.deputacionlugo.org",
            "sede.dipuleon.es",
            "sede.dipualba.es",
            "identificacionssl.sedipualba.es",
            "cim.secimallorca.net",
            "www.tramita.gva.es",
            "ptt-clave.gva.es",
            "ptt-clave-clientcert.gva.es",
            "sede.mscbs.gob.es",
            "sede.tea.hacienda.gob.es",
            "www1.tea.hacienda.gob.es",
            "sede.tenerife.es",
            "transparencia.sede.gob.es",
            "www.caib.es",
            "intranet.caib.es",
            "sede.grancanaria.com",
            "sede.cabildofuer.es",
            "serviciosede.mineco.gob.es",
            "pasarela.clave.gob.es",
            "pasarela-ident.clave.gob.es",
            "sede.asturias.es",
            "miprincipado.asturias.es",
            "tramita.asturias.es",
            "rhsso.asturias.es",
            "diputacion.toledo.gob.es",
            "sede.isciii.gob.es",
            "portafirmas.dival.es",
            "sede.policia.gob.es",
            "seu.diputaciolleida.cat",
            "sede.dip-badajoz.es",
            "sede.xunta.gal",
            "ias1.larioja.org",
            "www.carpetaciutadana.org",
            "sede.gobiernodecanarias.org",
            "sede.oepm.gob.es",
            "sede.funciona.gob.es",
            "www.sededgsfp.gob.es",
            "sede2.mjusticia.gob.es",
            "sede.cnmv.gob.es",
            "presidencia.jcyl.es",
            "egoitza.araba.eus",
            "seuelectronica.diba.cat",
            "valid.aoc.cat",
            "cert.valid.aoc.cat",
            "aplicacions.diba.cat",
            "tramits.diba.cat",
            "tramits.gencat.cat",
            "ovt.gencat.cat",
            "diputacionavila.sedelectronica.es",
            "sede.consejodetransparencia.gob.es",
            "www.sedecatastro.gob.es",
            "www3.sede.fega.gob.es",
            "pasarela-ident-sistemas.clave.gob.es",
            "seu.conselldeivissa.es",
            "sede.carm.es",
            "sede.ordenacionjuego.gob.es",
            "validate.perfdrive.com",
            "pase.carm.es",
            "conclave.carm.es",
            "enaire.sede.gob.es",
            "sede.guardiacivil.gob.es",
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
        assertEquals(setOf("veaja.cloud.juntadeandalucia.es"), JuntaOriginPolicy.browserAllowedHosts(juntaVeaPeg))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(juntaVeaPeg).isEmpty())
        assertEquals(setOf("gestiona.comunidad.madrid"), JuntaOriginPolicy.browserAllowedHosts(madridRegistro))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(madridRegistro).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidor.icm"),
                madridRegistro,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://api-veaja.cloud.juntadeandalucia.es/auth/login"),
                juntaVeaPeg,
            ),
        )
        assertEquals(
            setOf("sede.educacion.gob.es", "www.educacion.gob.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(education),
        )
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
        assertEquals(
            setOf("registrounicociudadanos.jccm.es", "sso.jccm.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(jccmRegistro),
        )
        assertEquals(
            setOf("https://registrounicociudadanos.jccm.es"),
            JuntaOriginPolicy.webMessageOriginRules(jccmRegistro),
        )
        assertEquals(setOf("sede.mites.gob.es"), JuntaOriginPolicy.browserAllowedHosts(mites))
        assertEquals(setOf("www.sevilla.org"), JuntaOriginPolicy.browserAllowedHosts(sevilla))
        assertEquals(
            setOf("sede.airef.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(airef),
        )
        assertEquals(setOf("sede.cdti.gob.es"), JuntaOriginPolicy.browserAllowedHosts(cdti))
        assertEquals(
            setOf("sede.transportes.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(transportes),
        )
        assertEquals(
            setOf("sede.transportes.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(sepes),
        )
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones"),
                sepes,
            ),
        )
        assertEquals(setOf("sede.melilla.es"), JuntaOriginPolicy.browserAllowedHosts(melilla))
        assertEquals(setOf("sede.ceuta.es"), JuntaOriginPolicy.browserAllowedHosts(ceuta))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(ceuta).isEmpty())
        assertEquals(setOf("seu.diputaciolleida.cat"), JuntaOriginPolicy.browserAllowedHosts(lleida))
        assertEquals(setOf("https://seu.diputaciolleida.cat"), JuntaOriginPolicy.webMessageOriginRules(lleida))
        assertEquals(setOf("sede.dip-badajoz.es"), JuntaOriginPolicy.browserAllowedHosts(badajoz))
        assertEquals(setOf("https://sede.dip-badajoz.es"), JuntaOriginPolicy.webMessageOriginRules(badajoz))
        assertEquals(setOf("sede.xunta.gal"), JuntaOriginPolicy.browserAllowedHosts(xunta))
        assertEquals(setOf("https://sede.xunta.gal"), JuntaOriginPolicy.webMessageOriginRules(xunta))
        assertEquals(setOf("sede.oepm.gob.es"), JuntaOriginPolicy.browserAllowedHosts(oepm))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(oepm).isEmpty())
        assertEquals(setOf("sede.funciona.gob.es"), JuntaOriginPolicy.browserAllowedHosts(funciona))
        assertEquals(setOf("sede.ordenacionjuego.gob.es"), JuntaOriginPolicy.browserAllowedHosts(dgoj))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(dgoj).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://sede.ordenacionjuego.gob.es/"), dgoj))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://clave.gob.es/"), dgoj))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(funciona).isEmpty())
        assertEquals(setOf("www.sededgsfp.gob.es"), JuntaOriginPolicy.browserAllowedHosts(dgsfp))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(dgsfp).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://www.sededgsfp.gob.es/"), dgsfp))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://www.sededgsfp.gob.es.evil.example/"), dgsfp))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://www.sededgsfp.gob.es:444/"), dgsfp))
        assertEquals(setOf("sede.cnmv.gob.es"), JuntaOriginPolicy.browserAllowedHosts(cnmv))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(cnmv).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx"), cnmv))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://sede.cnmv.gob.es.evil.example/"), cnmv))
        assertEquals(setOf("sede.cabildofuer.es"), JuntaOriginPolicy.browserAllowedHosts(fuerteventura))
        assertEquals(
            setOf("https://sede.cabildofuer.es"),
            JuntaOriginPolicy.webMessageOriginRules(fuerteventura),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
                fuerteventura,
            ),
        )
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://sede.funciona.gob.es/es/home"), funciona))
        assertEquals(
            setOf("sede.asturias.es", "miprincipado.asturias.es"),
            JuntaOriginPolicy.browserAllowedHosts(asturiasSede),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(asturiasSede).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.asturias.es/ast/-/dboid-6269000011903512107573"),
                asturiasSede,
            ),
        )
        assertEquals(setOf("egoitza.araba.eus"), JuntaOriginPolicy.browserAllowedHosts(alava))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(alava).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://egoitza.araba.eus/izapidetu/at/01/es/0000301"),
                alava,
            ),
        )
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://auth-api.redsara.es/"), funciona))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://autentica.redsara.es/"), funciona))
        assertEquals(setOf("sede2.mjusticia.gob.es"), JuntaOriginPolicy.browserAllowedHosts(mjusticia))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(mjusticia).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75"),
                mjusticia,
            ),
        )
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://sede.mjusticia.gob.es/"), mjusticia))
        assertEquals(
            setOf(
                "seuelectronica.diba.cat",
                "valid.aoc.cat",
                "cert.valid.aoc.cat",
                "aplicacions.diba.cat",
                "tramits.diba.cat",
            ),
            JuntaOriginPolicy.browserAllowedHosts(barcelona2057),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(barcelona2057).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://seuelectronica.diba.cat/es/sol%C2%B7licitud-gen%C3%A8rica"),
                barcelona2057,
            ),
        )
        assertEquals(
            setOf(
                "diputacionavila.sedelectronica.es",
                "pasarela.clave.gob.es",
                "pasarela-ident.clave.gob.es",
                "pasarela-ident-sistemas.clave.gob.es",
            ),
            JuntaOriginPolicy.browserAllowedHosts(avila),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(avila).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://diputacionavila.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5"),
                avila,
            ),
        )
        assertEquals(
            setOf("www.sedecatastro.gob.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(catastro),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(catastro).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://www.sedecatastro.gob.es/Accesos/SECAccProcedimientos.aspx?Dest=22"),
                catastro,
            ),
        )
        assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://www.sedecatastro.gob.es/Accesos/SECAccPIN.aspx?Dest=22&texp=REGI"), catastro))
        assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://pasarela.clave.gob.es/Proxy2/ResponseRedirect"), catastro))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"), catastro))
        assertEquals(
            setOf("www3.sede.fega.gob.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(fega),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(fega).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://www3.sede.fega.gob.es/ConRegExt/regmantenimientos/inicioAsientos.action?tramite=OFVSG02"),
                fega,
            ),
        )
        assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"), fega))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"), fega))
        assertEquals(
            setOf("sede.carm.es", "validate.perfdrive.com", "pase.carm.es", "conclave.carm.es"),
            JuntaOriginPolicy.browserAllowedHosts(murcia),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(murcia).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://sede.carm.es/presentador/inicio/385/DI155"), murcia))
        assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://pase.carm.es/pase/login"), murcia))
        assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://conclave.carm.es/TokenServlet"), murcia))
        assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://validate.perfdrive.com/challenge"), murcia))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://pasarela.clave.gob.es/Clave2/"), murcia))
        assertEquals(
            setOf("sede.consejodetransparencia.gob.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(ctbg),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(ctbg).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.consejodetransparencia.gob.es/catalog/tw/01b4b72b-7f21-4d7c-9576-e1d7871624a6"),
                ctbg,
            ),
        )
        assertEquals(
            setOf("tramites.juntaex.es"),
            JuntaOriginPolicy.browserAllowedHosts(extremadura),
        )
        assertEquals(
            setOf("www.navarra.es", "administracionelectronica.navarra.es", "ateka.navarra.es"),
            JuntaOriginPolicy.browserAllowedHosts(navarra),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(navarra).isEmpty())
        assertEquals(
            setOf("www.sede.diputaciondevalladolid.es"),
            JuntaOriginPolicy.browserAllowedHosts(valladolid),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(valladolid).isEmpty())
        assertEquals(
            setOf("registro.diputaciondeburgos.es"),
            JuntaOriginPolicy.browserAllowedHosts(burgos),
        )
        assertEquals(
            setOf("https://registro.diputaciondeburgos.es"),
            JuntaOriginPolicy.webMessageOriginRules(burgos),
        )
        assertEquals(
            setOf("sedeelectronica.cabildodelapalma.es"),
            JuntaOriginPolicy.browserAllowedHosts(laPalma),
        )
        assertEquals(setOf("ovc24.dphuesca.es"), JuntaOriginPolicy.browserAllowedHosts(huesca))
        assertEquals(setOf("sede.deputacionlugo.org"), JuntaOriginPolicy.browserAllowedHosts(lugo))
        assertEquals(setOf("sede.dipuleon.es"), JuntaOriginPolicy.browserAllowedHosts(leon))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(leon).isEmpty())
        assertEquals(setOf("sede.dipualba.es"), JuntaOriginPolicy.browserAllowedHosts(albacete))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(albacete).isEmpty())
        assertEquals(setOf("sede.mscbs.gob.es"), JuntaOriginPolicy.browserAllowedHosts(sanidad))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(sanidad).isEmpty())
        assertEquals(setOf("sede.tea.hacienda.gob.es"), JuntaOriginPolicy.browserAllowedHosts(tea))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(tea).isEmpty())
        assertEquals(setOf("sede.tenerife.es"), JuntaOriginPolicy.browserAllowedHosts(tenerife))
        assertEquals(
            setOf("https://sede.tenerife.es"),
            JuntaOriginPolicy.webMessageOriginRules(tenerife),
        )
        assertEquals(
            setOf("transparencia.sede.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(transparencia),
        )
        assertEquals(
            setOf("https://transparencia.sede.gob.es"),
            JuntaOriginPolicy.webMessageOriginRules(transparencia),
        )
        assertEquals(
            setOf("https://ventanillaelectronica.jccm.es"),
            JuntaOriginPolicy.webMessageOriginRules(jccm),
        )
        assertEquals(
            setOf("https://sede.mites.gob.es"),
            JuntaOriginPolicy.webMessageOriginRules(mites),
        )
        assertEquals(
            setOf("https://www.sevilla.org"),
            JuntaOriginPolicy.webMessageOriginRules(sevilla),
        )
        assertEquals(
            setOf("https://sede.airef.es"),
            JuntaOriginPolicy.webMessageOriginRules(airef),
        )
        assertEquals(
            setOf("https://sede.cdti.gob.es"),
            JuntaOriginPolicy.webMessageOriginRules(cdti),
        )
        assertEquals(
            setOf("https://sede.transportes.gob.es"),
            JuntaOriginPolicy.webMessageOriginRules(transportes),
        )
        assertEquals(
            setOf("https://sede.melilla.es"),
            JuntaOriginPolicy.webMessageOriginRules(melilla),
        )
        assertEquals(
            setOf("https://tramites.juntaex.es"),
            JuntaOriginPolicy.webMessageOriginRules(extremadura),
        )
        assertEquals(
            setOf("https://sedeelectronica.cabildodelapalma.es"),
            JuntaOriginPolicy.webMessageOriginRules(laPalma),
        )
        assertEquals(
            setOf("https://ovc24.dphuesca.es"),
            JuntaOriginPolicy.webMessageOriginRules(huesca),
        )
        assertEquals(
            setOf("https://sede.deputacionlugo.org"),
            JuntaOriginPolicy.webMessageOriginRules(lugo),
        )
        assertEquals(setOf("diputacion.toledo.gob.es"), JuntaOriginPolicy.browserAllowedHosts(toledo))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(toledo).isEmpty())
        assertEquals(setOf("portafirmas.dival.es"), JuntaOriginPolicy.browserAllowedHosts(valencia))
        assertEquals(
            setOf("https://portafirmas.dival.es"),
            JuntaOriginPolicy.webMessageOriginRules(valencia),
        )
        assertEquals(setOf("sede.policia.gob.es"), JuntaOriginPolicy.browserAllowedHosts(policia))
        assertEquals(setOf("sede.gobiernodecanarias.org"), JuntaOriginPolicy.browserAllowedHosts(canarias))
        assertEquals(
            setOf("https://sede.policia.gob.es"),
            JuntaOriginPolicy.webMessageOriginRules(policia),
        )
        assertEquals(
            setOf("https://sede.gobiernodecanarias.org"),
            JuntaOriginPolicy.webMessageOriginRules(canarias),
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
