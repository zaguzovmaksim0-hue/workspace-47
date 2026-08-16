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
    private val aragonTramites = ProfileId("aragon-solicitud-general-client-auth")
    private val aeat = ProfileId("aeat-mis-datos-censales")
    private val aemet = ProfileId("aemet-public-solicitud-navigation")
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
    private val jaen = ProfileId("diputacion-jaen-sede")
    private val sanidad = ProfileId("ministerio-sanidad-certificado")
    private val tea = ProfileId("tea-alegaciones-certificado")
    private val tenerife = ProfileId("tenerife-sede-electronica")
    private val transparencia = ProfileId("age-portal-de-la-transparencia")
    private val toledo = ProfileId("diputacion-toledo-sede")
    private val valencia = ProfileId("diputacion-valencia-sede")
    private val formentera = ProfileId("formentera-sede-electronica")
    private val acceda = ProfileId("age-acceda")
    private val policia = ProfileId("policia-solicitud-generica")
    private val lleida = ProfileId("diputacion-lleida-sede")
    private val badajoz = ProfileId("diputacion-badajoz-portal")
    private val xunta = ProfileId("xunta-galicia-solicitude-xenerica")
    private val canarias = ProfileId("canarias-sede")
    private val catalunyaSeu = ProfileId("catalunya-seu-registre-client-auth")
    private val oepm = ProfileId("oepm-protegeo-general")
    private val funciona = ProfileId("portal-funciona-public-home")
    private val madrid53f1 = ProfileId("comunidad-madrid-cuenta-digital-53f1")
    private val justicia = ProfileId("justicia-sede-judicial-private-area")
    private val gipuzkoa = ProfileId("diputacion-gipuzkoa-registro-public")
    private val fondosEuropeos = ProfileId("fondos-europeos-sede-public-home")
    private val asturiasSede = ProfileId("asturias-sede-tramite-navigation")
    private val dgsfp = ProfileId("dgsfp-sede-public-home")
    private val mjusticia = ProfileId("mjusticia-fundaciones-idp75")
    private val cnmv = ProfileId("cnmv-sede-public-home")
    private val boe = ProfileId("boe-sede-public-home")
    private val cnmc = ProfileId("cnmc-remision-solicitudes-public")
    private val fuerteventura = ProfileId("fuerteventura-sede-electronica")
    private val alava = ProfileId("diputacion-alava-registro-comun")
    private val bizkaia = ProfileId("diputacion-bizkaia-instancia-generica")
    private val barcelona2057 = ProfileId("diputacion-barcelona-solicitud-generica-2057")
    private val ourense = ProfileId("diputacion-ourense-sede")
    private val sevillaDiputacion = ProfileId("diputacion-sevilla-sede")
    private val coruna = ProfileId("diputacion-a-coruna-solicitud-general")
    private val malaga = ProfileId("diputacion-malaga-instancia-general")
    private val avila = ProfileId("diputacion-avila-instancia-general")
    private val girona = ProfileId("diputacion-girona-instancia-generica")
    private val segovia = ProfileId("diputacion-segovia-registro")
    private val salamanca = ProfileId("diputacion-salamanca-instancia-general")
    private val teruel = ProfileId("diputacion-teruel-instancia-general")
    private val ctbg = ProfileId("ctbg-solicitud-informacion")
    private val catastro = ProfileId("catastro-solicitudes-genericas")
    private val fega = ProfileId("fega-solicitud-general-ofvsg02")
    private val elHierro = ProfileId("el-hierro-solicitud-general")
    private val murcia = ProfileId("murcia-carm-pase")
    private val dgoj = ProfileId("dgoj-public-navigation")
    private val huelva = ProfileId("diputacion-huelva-sede-public")
    private val almeria = ProfileId("diputacion-almeria-solicitud-general")
    private val ciudadReal = ProfileId("diputacion-ciudad-real-registro-telematico")
    private val cordoba = ProfileId("diputacion-cordoba-solicitud-generica")
    private val castellon = ProfileId("diputacion-castellon-instancia-general")
    private val caceres = ProfileId("diputacion-caceres-instancia-general")
    private val euskadi = ProfileId("euskadi-sede-electronica")

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
            "login.loginssl.aragon.es",
            "login1.loginssl.aragon.es",
            "sede.agenciatributaria.gob.es",
            "sede.aemet.gob.es",
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
            "sede.dipgra.es",
            "registro.diputaciondeburgos.es",
            "sedeelectronica.cabildodelapalma.es",
            "ovc24.dphuesca.es",
            "sede.deputacionlugo.org",
            "sede.dipuleon.es",
            "sede.dipualba.es",
            "identificacionssl.sedipualba.es",
            "sede.dipujaen.es",
            "cert2.dipujaen.es",
            "cim.secimallorca.net",
            "www.tramita.gva.es",
            "ptt-clave.gva.es",
            "ptt-clave-clientcert.gva.es",
            "portal.seg-social.gob.es",
            "idp.seg-social.es",
            "ipce.seg-social.es",
            "sede.mscbs.gob.es",
            "sede.tea.hacienda.gob.es",
            "www1.tea.hacienda.gob.es",
            "sede.tenerife.es",
            "transparencia.sede.gob.es",
            "sede.malaga.es",
            "clave.malaga.es",
            "www.caib.es",
            "sede.cmt.gob.es",
            "intranet.caib.es",
            "sede.grancanaria.com",
            "sede.cabildofuer.es",
            "lanzaroteylagraciosa.sedelectronica.es",
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
            "ovac.conselldeformentera.cat",
            "sede.administracionespublicas.gob.es",
            "sede.policia.gob.es",
            "seu.diputaciolleida.cat",
            "sede.dip-badajoz.es",
            "sede.xunta.gal",
            "ias1.larioja.org",
            "www.carpetaciutadana.org",
            "sede.gobiernodecanarias.org",
            "sede.oepm.gob.es",
            "sede.funciona.gob.es",
            "digital.comunidad.madrid",
            "gestiona.comunidad.madrid",
            "sedejudicial.justicia.es",
            "am.justicia.es",
            "sede.madrid.es",
            "servcla.madrid.es",
            "cas.madrid.es",
            "egoitza.gipuzkoa.eus",
            "sede.diputaciondesalamanca.gob.es",
            "sedefondoscomunitarios.gob.es",
            "www.sededgsfp.gob.es",
            "sede2.mjusticia.gob.es",
            "sede.cnmv.gob.es",
            "sede.seguridadaerea.gob.es",
            "www.boe.es",
            "sede.cnmc.gob.es",
            "sede.adif.gob.es",
            "presidencia.jcyl.es",
            "egoitza.araba.eus",
            "appsec.ebizkaia.eus",
            "appstac.ebizkaia.eus",
            "eidasbiz.izenpe.com",
            "seuelectronica.diba.cat",
            "valid.aoc.cat",
            "cert.valid.aoc.cat",
            "aplicacions.diba.cat",
            "tramits.diba.cat",
            "tramits.gencat.cat",
            "ovt.gencat.cat",
            "seuelectronica.dipta.cat",
            "egovern.altanet.org",
            "diputacionavila.sedelectronica.es",
            "elhierro.sedelectronica.es",
            "dguadalajara.sedelectronica.es",
            "sede.dipsegovia.es",
            "dpteruel.sedelectronica.es",
            "sede.consejodetransparencia.gob.es",
            "www.sedecatastro.gob.es",
            "www3.sede.fega.gob.es",
            "sede.diphuelva.es",
            "ov.dipalme.org",
            "sede.dipucr.es",
            "sede.dipucordoba.es",
            "dipcas.sedelectronica.es",
            "sede.dip-caceres.es",
            "sede.depourense.es",
            "sedeelectronicadipusevilla.es",
            "sede.dacoruna.gal",
            "www.dacoruna.gal",
            "pasarela-ident-sistemas.clave.gob.es",
            "sede.depo.gal",
            "lagomera.sedelectronica.es",
            "seu.conselldeivissa.es",
            "seu-e.cat",
            "etram.seu-e.cat",
            "sede.carm.es",
            "sede.ordenacionjuego.gob.es",
            "validate.perfdrive.com",
            "pase.carm.es",
            "conclave.carm.es",
            "enaire.sede.gob.es",
            "sede.guardiacivil.gob.es",
            "sede.csn.gob.es",
            "sede.csd.gob.es",
            "sede.diputaciondepalencia.es",
            "sede.dipucadiz.es",
            "sso.dipucadiz.es",
            "sede.dipucuenca.es",
            "web.gencat.cat",
            "www.euskadi.eus",
            "eidas.izenpe.com",
            "eidas2.izenpe.com",
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
        assertEquals(
            setOf("aplicaciones.aragon.es", "login.loginssl.aragon.es"),
            JuntaOriginPolicy.browserAllowedHosts(aragonTramites),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(aragonTramites).isEmpty())
        assertEquals(setOf("sede.agenciatributaria.gob.es"), JuntaOriginPolicy.browserAllowedHosts(aeat))
        assertEquals(setOf("sede.aemet.gob.es"), JuntaOriginPolicy.browserAllowedHosts(aemet))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(aemet).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso"),
                aemet,
            ),
        )
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
        assertEquals(setOf("sedefondoscomunitarios.gob.es"), JuntaOriginPolicy.browserAllowedHosts(fondosEuropeos))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(fondosEuropeos).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://sedefondoscomunitarios.gob.es/"), fondosEuropeos))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://tramitesfondoseuropeos.hacienda.gob.es/dossier"), fondosEuropeos))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://reg.redsara.es/es/"), fondosEuropeos))
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
        assertEquals(setOf("www.boe.es"), JuntaOriginPolicy.browserAllowedHosts(boe))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(boe).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://www.boe.es/informacion/index.php"), boe))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://extranet.boe.es/quejas_el/"), boe))
        assertEquals(setOf("sede.cnmc.gob.es"), JuntaOriginPolicy.browserAllowedHosts(cnmc))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(cnmc).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://sede.cnmc.gob.es/tramites/general/remision-de-solicitudes-escritos-y-comunicaciones"), cnmc))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://tramites.cnmc.gob.es/formulario/21"), cnmc))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://tramitesclave.cnmc.gob.es/formulario/21"), cnmc))
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
        assertEquals(
            setOf("appsec.ebizkaia.eus", "appstac.ebizkaia.eus", "eidasbiz.izenpe.com"),
            JuntaOriginPolicy.browserAllowedHosts(bizkaia),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(bizkaia).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://appsec.ebizkaia.eus/JXSS001C/?procedimiento=1664&formulario=4912&idioma=C&sede=S"),
                bizkaia,
            ),
        )
        assertTrue(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://eidasbiz.izenpe.com/trustedx-authserver/izenpe/flowSelector.xhtml"),
                bizkaia,
            ),
        )
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://izenpe.com/"), bizkaia))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://auth-api.redsara.es/"), funciona))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://autentica.redsara.es/"), funciona))
        assertEquals(
            setOf("digital.comunidad.madrid", "gestiona.comunidad.madrid"),
            JuntaOriginPolicy.browserAllowedHosts(madrid53f1),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(madrid53f1).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://digital.comunidad.madrid/ext/53F1"),
                madrid53f1,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://gestiona2.comunidad.madrid/auto_certificado/SelCertificado"),
                madrid53f1,
            ),
        )
        assertEquals(
            setOf("sedejudicial.justicia.es", "am.justicia.es"),
            JuntaOriginPolicy.browserAllowedHosts(justicia),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(justicia).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sedejudicial.justicia.es/group/guest/area-privada"),
                justicia,
            ),
        )
        assertTrue(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://am.justicia.es/selfservice-ext/saml2/sp/login/clave"),
                justicia,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
                justicia,
            ),
        )
        assertEquals(setOf("egoitza.gipuzkoa.eus"), JuntaOriginPolicy.browserAllowedHosts(gipuzkoa))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(gipuzkoa).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://egoitza.gipuzkoa.eus/WAS/CORP/WATTramiteakWEB/inicio.do?idioma=C&app=00001"),
                gipuzkoa,
            ),
        )
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://eidas.izenpe.com/"), gipuzkoa))
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://eidas2.izenpe.com/"), gipuzkoa))
        assertEquals(
            setOf("www.euskadi.eus", "eidas.izenpe.com"),
            JuntaOriginPolicy.browserAllowedHosts(euskadi),
        )
        assertEquals(
            setOf("https://eidas.izenpe.com"),
            JuntaOriginPolicy.webMessageOriginRules(euskadi),
        )
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://eidas2.izenpe.com/"), euskadi))
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://eidas.izenpe.com/"), euskadi))
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
            setOf("sede.depourense.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(ourense),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(ourense).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse(
                    "https://sede.depourense.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&" +
                        "PAGE_CODE=CATALOGO&DETALLE=6269000946476474507610&lang=ES",
                ),
                ourense,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"),
                ourense,
            ),
        )
        assertEquals(
            setOf("sedeelectronicadipusevilla.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(sevillaDiputacion),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(sevillaDiputacion).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sedeelectronicadipusevilla.es/opencms/opencms/sede"),
                sevillaDiputacion,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"),
                sevillaDiputacion,
            ),
        )
        assertEquals(
            setOf("www.dacoruna.gal", "sede.dacoruna.gal", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(coruna),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(coruna).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.dacoruna.gal/tramitador/entrada?idLogica=accesoDirecto"),
                coruna,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"),
                coruna,
            ),
        )
        assertEquals(
            setOf("sede.malaga.es", "clave.malaga.es"),
            JuntaOriginPolicy.browserAllowedHosts(malaga),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(malaga).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.malaga.es/instancia-general/nueva-instancia-general/"),
                malaga,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
                malaga,
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
            setOf("sede.dipsegovia.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(segovia),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(segovia).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.dipsegovia.es/registro"),
                segovia,
            ),
        )
        assertTrue(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
                segovia,
            ),
        )
        assertEquals(
            setOf("sede.diputaciondesalamanca.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(salamanca),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(salamanca).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.diputaciondesalamanca.gob.es/moad/oficina-moad/tramites/acceso.do?id=12183&entity=1496&siteCode=DIPT_SALAM_SEDE"),
                salamanca,
            ),
        )
        assertEquals(setOf("dpteruel.sedelectronica.es"), JuntaOriginPolicy.browserAllowedHosts(teruel))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(teruel).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://dpteruel.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5"),
                teruel,
            ),
        )
        assertEquals(setOf("sede.diphuelva.es"), JuntaOriginPolicy.browserAllowedHosts(huelva))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(huelva).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.diphuelva.es/"),
                huelva,
            ),
        )
        assertEquals(setOf("ov.dipalme.org"), JuntaOriginPolicy.browserAllowedHosts(almeria))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(almeria).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://ov.dipalme.org/TiProceeding/ciudadano?entrada=ciudadano&idLogica=accesoDirecto&idExpediente=800210_SolicitudGeneral&idEntidad=400000"),
                almeria,
            ),
        )
        assertEquals(setOf("sede.dipucr.es"), JuntaOriginPolicy.browserAllowedHosts(ciudadReal))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(ciudadReal).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.dipucr.es/iniciaTramite/20"),
                ciudadReal,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://sede.diphuelva.es.evil.example/"),
                huelva,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://sede.dipucr.es:4443/SIGEM_AutenticacionWeb/seleccionEntidad.do"),
                ciudadReal,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://ov.dipalme.org.evil.example/"),
                almeria,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://sede.dipucr.es.evil.example/"),
                ciudadReal,
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
        assertEquals(setOf("sede.dipucordoba.es"), JuntaOriginPolicy.browserAllowedHosts(cordoba))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(cordoba).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.dipucordoba.es/diputacion/tramites/procedimiento/8876/solicitud-generica"),
                cordoba,
            ),
        )
        assertEquals(setOf("dipcas.sedelectronica.es"), JuntaOriginPolicy.browserAllowedHosts(castellon))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(castellon).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://dipcas.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5"),
                castellon,
            ),
        )
        assertEquals(setOf("sede.dip-caceres.es", "pasarela.clave.gob.es"), JuntaOriginPolicy.browserAllowedHosts(caceres))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(caceres).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://sede.dip-caceres.es/carpetaCiudadano/fichaprocedimiento.do?idproc=341"),
                caceres,
            ),
        )
        assertEquals(
            setOf("elhierro.sedelectronica.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(elHierro),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(elHierro).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://elhierro.sedelectronica.es/catalog/tw/7944e884-3b98-48fc-abcd-d6db6ef8bd71"),
                elHierro,
            ),
        )
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://elhierro.sedelectronica.es.evil.example/"),
                elHierro,
            ),
        )
        assertEquals(
            setOf("seu-e.cat", "etram.seu-e.cat"),
            JuntaOriginPolicy.browserAllowedHosts(girona),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(girona).isEmpty())
        assertNull(
            JuntaOriginPolicy.signingOriginFor(
                Uri.parse("https://seu-e.cat/tramits/8001760009/instancia-generica"),
                girona,
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
        assertEquals(setOf("sede.dipujaen.es"), JuntaOriginPolicy.browserAllowedHosts(jaen))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(jaen).isEmpty())
        assertFalse(JuntaOriginPolicy.isAllowed(Uri.parse("https://cert2.dipujaen.es/"), jaen))
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
        assertEquals(setOf("ovac.conselldeformentera.cat"), JuntaOriginPolicy.browserAllowedHosts(formentera))
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(formentera).isEmpty())
        assertEquals(setOf("sede.administracionespublicas.gob.es"), JuntaOriginPolicy.browserAllowedHosts(acceda))
        assertEquals(
            setOf("https://sede.administracionespublicas.gob.es"),
            JuntaOriginPolicy.webMessageOriginRules(acceda),
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
        assertEquals(
            setOf("web.gencat.cat", "tramits.gencat.cat", "ovt.gencat.cat", "valid.aoc.cat"),
            JuntaOriginPolicy.browserAllowedHosts(catalunyaSeu),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(catalunyaSeu).isEmpty())
        assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://cert.valid.aoc.cat/o/oauth2/cert")))
        assertFalse(
            JuntaOriginPolicy.isAllowed(
                Uri.parse("https://cert.valid.aoc.cat/o/oauth2/cert"),
                catalunyaSeu,
            ),
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
