package dev.junta.firmamobile.catalog

import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.SignatureFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PublicPortalCatalogParserTest {
    private val json by lazy {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.resources.openRawResource(R.raw.public_portal_catalog_v1)
            .bufferedReader().use { it.readText() }
    }

    @Test
    fun `bundled catalog contains the complete inventory with only exact profile bindings`() {
        val catalog = PublicPortalCatalogParser.parse(json)

        assertEquals(1, catalog.schemaVersion)
        val inventoryCount = catalog.entries.count { it.inventoryId != null }
        assertTrue(inventoryCount >= 183)
        assertEquals(inventoryCount, catalog.entries.size)
        assertEquals(catalog.entries.size, catalog.entries.map { it.portalId }.toSet().size)
        assertEquals(catalog.entries.size, catalog.entries.map { it.entryUrl }.toSet().size)
        assertEquals(
            setOf(
                ProfileId("junta-andalucia"),
                ProfileId("junta-andalucia-vea-peg"),
                ProfileId("comunidad-madrid-registro-general"),
                ProfileId("reg-age-redsara"),
                ProfileId("unizar-tramitador"),
                ProfileId("carne-joven-andalucia"),
                ProfileId("junta-ofvirtual"),
                ProfileId("educacion-convocatoria"),
                ProfileId("aragon-siraw"),
                ProfileId("aragon-solicitud-general-client-auth"),
                ProfileId("aeat-mis-datos-censales"),
                ProfileId("aemet-public-solicitud-navigation"),
                ProfileId("dgt-verificacion-equipo"),
                ProfileId("mjusticia-fundaciones-idp75"),
                ProfileId("ugr-certificado-login"),
                ProfileId("cantabria-rec-cert-login"),
                ProfileId("catalunya-peticio-generica-client-auth"),
                ProfileId("catalunya-seu-registre-client-auth"),
                ProfileId("diputacion-ourense-sede"),
                ProfileId("diputacion-sevilla-sede"),
                ProfileId("diputacion-a-coruna-solicitud-general"),
                ProfileId("euskadi-sede-electronica"),
                ProfileId("jccm-certificate-login-probe"),
                ProfileId("jccm-registro-generico"),
                ProfileId("mites-certificate-login"),
                ProfileId("transportes-qys-cert-login"),
                ProfileId("sevilla-atse-certificate-login"),
                ProfileId("airef-instancia-general"),
                ProfileId("mugeju-remision-documentacion-client-auth"),
                ProfileId("melilla-sede"),
                ProfileId("ceuta-sede"),
                ProfileId("age-acceda"),
                ProfileId("extremadura-tramites"),
                ProfileId("extremadura-pattex-client-auth"),
                ProfileId("navarra-sede-registro-general"),
                ProfileId("diputacion-valladolid-sede"),
                ProfileId("diputacion-burgos-portal"),
                ProfileId("la-palma-sede-electronica"),
                ProfileId("diputacion-huesca-portal"),
                ProfileId("diputacion-lugo-sede"),
                ProfileId("diputacion-leon-sede"),
                ProfileId("diputacion-albacete-portal"),
                ProfileId("diputacion-jaen-sede"),
                ProfileId("consell-mallorca-sede"),
                ProfileId("diputacion-cuenca-portal"),
                ProfileId("generalitat-valenciana-client-auth"),
                ProfileId("tgss-importass-client-auth"),
                ProfileId("ministerio-sanidad-certificado"),
                ProfileId("tea-alegaciones-certificado"),
                ProfileId("tenerife-sede-electronica"),
                ProfileId("fuerteventura-sede-electronica"),
                ProfileId("gran-canaria-sede-electronica"),
                ProfileId("age-portal-de-la-transparencia"),
                ProfileId("caib-portafib"),
                ProfileId("ministerio-economia-instancia-generica"),
                ProfileId("diputacion-toledo-sede"),
                ProfileId("isciii-certificate-selection"),
                ProfileId("diputacion-valencia-sede"),
                ProfileId("diputacion-alicante-solicitud-general"),
                ProfileId("diputacion-almeria-solicitud-general"),
                ProfileId("diputacion-granada-sede-public"),
                ProfileId("diputacion-castellon-instancia-general"),
                ProfileId("policia-solicitud-generica"),
                ProfileId("diputacion-lleida-sede"),
                ProfileId("diputacion-badajoz-portal"),
                ProfileId("diputacion-alava-registro-comun"),
                ProfileId("diputacion-bizkaia-instancia-generica"),
                ProfileId("oepm-protegeo-general"),
                ProfileId("portal-funciona-public-home"),
                ProfileId("fondos-europeos-sede-public-home"),
                ProfileId("diputacion-teruel-instancia-general"),
                ProfileId("sepes-transportes-public-complaints"),
                ProfileId("dgsfp-sede-public-home"),
                ProfileId("cnmv-sede-public-home"),
                ProfileId("aesa-solicitud-general-public"),
                ProfileId("boe-sede-public-home"),
                ProfileId("cnmc-remision-solicitudes-public"),
                ProfileId("adif-sede-public-home"),
                ProfileId("castilla-leon-quju-public"),
                ProfileId("diputacion-avila-instancia-general"),
                ProfileId("diputacion-guadalajara-instancia-general"),
                ProfileId("diputacion-segovia-registro"),
                ProfileId("ctbg-solicitud-informacion"),
                ProfileId("catastro-solicitudes-genericas"),
                ProfileId("fega-solicitud-general-ofvsg02"),
                ProfileId("diputacion-huelva-sede-public"),
                ProfileId("diputacion-ciudad-real-registro-telematico"),
                ProfileId("diputacion-cordoba-solicitud-generica"),
                ProfileId("diputacion-caceres-instancia-general"),
                ProfileId("cdti-certificate-validation"),
                ProfileId("xunta-galicia-solicitude-xenerica"),
                ProfileId("la-rioja-oficina-electronica"),
                ProfileId("asturias-miprincipado"),
                ProfileId("asturias-sede-tramite-navigation"),
                ProfileId("menorca-carpeta-ciutadana"),
                ProfileId("canarias-sede"),
                ProfileId("comunidad-madrid-cuenta-digital-53f1"),
                ProfileId("justicia-sede-judicial-private-area"),
                ProfileId("madrid-sede-tarjeta-azul"),
                ProfileId("diputacion-gipuzkoa-registro-public"),
                ProfileId("diputacion-barcelona-solicitud-generica-2057"),
                ProfileId("la-gomera-instancia-general"),
                ProfileId("lanzarote-instancia-general"),
                ProfileId("diputacion-pontevedra-instancia-xenerica"),
                ProfileId("diputacion-malaga-instancia-general"),
                ProfileId("diputacion-girona-instancia-generica"),
                ProfileId("diputacion-cadiz-solicitud-generica"),
                ProfileId("diputacion-tarragona-sede"),
                ProfileId("eivissa-sede-electronica"),
                ProfileId("diputacion-salamanca-instancia-general"),
                ProfileId("murcia-carm-pase"),
                ProfileId("enaire-sede-public"),
                ProfileId("dgoj-public-navigation"),
                ProfileId("guardia-civil-sede-public"),
                ProfileId("csn-sede-public"),
                ProfileId("csd-sede-public"),
                ProfileId("cmt-public-navigation"),
                ProfileId("diputacion-palencia-solicitud-general"),
                ProfileId("el-hierro-solicitud-general"),
                ProfileId("formentera-sede-electronica"),
                ProfileId("iac-sede-public-navigation"),
                ProfileId("icac-sede-public-navigation"),
                ProfileId("itj-sede-public-navigation"),
            ),
            catalog.entries.mapNotNull { it.profileId }.toSet(),
        )
        assertTrue(catalog.entries.any { it.catalogStatus == PublicCatalogStatus.DISCOVERED })
        assertTrue(catalog.entries.any { it.inventoryStatus == PortalInventoryStatus.BROWSE_ONLY })
    }

    @Test
    fun `Formentera catalog entry binds the exact pending navigation contract`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val formentera = catalog.entries.single { it.portalId == PortalId("formentera-sede-electronica") }

        assertEquals(ProfileId("formentera-sede-electronica"), formentera.profileId)
        assertEquals("ES-PUB-0124", formentera.inventoryId)
        assertEquals("https://ovac.conselldeformentera.cat/", formentera.entryUrl.toString())
        assertEquals("ABSIS_OVAC_PUBLIC_NAVIGATION", formentera.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, formentera.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, formentera.catalogStatus)
        assertTrue(formentera.observedSignatureFormats.isEmpty())
        assertEquals("2026-08-16", formentera.reviewedOn.toString())
    }

    @Test
    fun `Ciencia alias retains the Ministry Sede URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-ciencia-innovacion-y-universidades")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://ciencia.sede.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(
            PublicCatalogStatus.E2E_PENDING,
            catalog.entries.single { it.portalId == portal.portalId }.catalogStatus,
        )
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Ceuta ANI resolves only the exact QA authenticated form boundary without signing capability`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val profiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(SiteProfileRegistry(profiles, BuildTrustPolicy.QA), profiles, catalog)
        val metadata = catalog.entries.single { it.portalId == PortalId("ceuta-sede") }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val start = URI("https://sede.ceuta.es/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI")
        assertEquals("ES-PUB-0106", metadata.inventoryId)
        assertEquals(ProfileId("ceuta-sede"), metadata.profileId)
        assertEquals(start, metadata.entryUrl)
        assertEquals("CEUTA_AUTHENTICATED_FORM_BOUNDARY", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals(setOf("CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"), metadata.observedMechanisms.map { it.name }.toSet())
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(ProfileId("ceuta-sede"), start), repository.resolveLaunch(portal))
    }

    @Test
    fun `Lugo catalog entry exposes only the bounded XML batch contract`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val lugo = catalog.entries.single { it.portalId == PortalId("diputacion-lugo-sede") }

        assertEquals(ProfileId("diputacion-lugo-sede"), lugo.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, lugo.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, lugo.catalogStatus)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in lugo.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in lugo.observedMechanisms)
        assertTrue(PortalMechanism.AUTOSCRIPT in lugo.observedMechanisms)
        assertTrue(lugo.limitations.contains("un lote CAdES", ignoreCase = true))

    }

    @Test
    fun `Transportes QYS exposes only the bounded pending XAdES Enveloped login`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val portal = catalog.entries.single {
            it.portalId == PortalId("age-ministerio-de-transportes-y-movilidad-sostenible")
        }

        assertEquals(ProfileId("transportes-qys-cert-login"), portal.profileId)
        assertEquals("ES-PUB-0075", portal.inventoryId)
        assertEquals(
            "https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002",
            portal.entryUrl.toString(),
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in portal.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in portal.observedMechanisms)
        assertTrue(PortalMechanism.MINIAPPLET in portal.observedMechanisms)
        assertEquals(setOf(SignatureFormat.XADES), portal.observedSignatureFormats)
    }

    @Test
    fun `MITES certificate login exposes only the exact QA CAdES contract`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val mites = catalog.entries.single {
            it.portalId == PortalId("age-ministerio-de-trabajo-y-economia-social")
        }

        assertEquals(ProfileId("mites-certificate-login"), mites.profileId)
        assertEquals("ES-PUB-0074", mites.inventoryId)
        assertEquals("https://sede.mites.gob.es/", mites.entryUrl.toString())
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, mites.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, mites.catalogStatus)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in mites.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in mites.observedMechanisms)
        assertTrue(PortalMechanism.AUTOSCRIPT in mites.observedMechanisms)
        assertTrue(PortalMechanism.MINIAPPLET in mites.observedMechanisms)
        assertEquals(setOf(SignatureFormat.CADES), mites.observedSignatureFormats)
    }

    @Test
    fun `CDTI certificate validation exposes only the exact QA XAdES Enveloping contract`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val cdti = catalog.entries.single {
            it.portalId == PortalId("age-centro-para-el-desarrollo-tecnologico-industrial-cdti")
        }

        assertEquals(ProfileId("cdti-certificate-validation"), cdti.profileId)
        assertEquals("ES-PUB-0030", cdti.inventoryId)
        assertEquals(
            "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx",
            cdti.entryUrl.toString(),
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, cdti.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, cdti.catalogStatus)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in cdti.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in cdti.observedMechanisms)
        assertTrue(PortalMechanism.AUTOSCRIPT in cdti.observedMechanisms)
        assertTrue(PortalMechanism.MINIAPPLET in cdti.observedMechanisms)
        assertEquals(setOf(SignatureFormat.XADES), cdti.observedSignatureFormats)
    }

    @Test
    fun `AEAT record binds only the exact pending Client TLS profile`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val aeat = catalog.entries.single { it.portalId == PortalId("aeat-sede") }

        assertEquals(ProfileId("aeat-mis-datos-censales"), aeat.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, aeat.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, aeat.catalogStatus)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in aeat.observedMechanisms)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in aeat.observedMechanisms)
        assertFalse(aeat.observedSignatureFormats.isNotEmpty())
    }

    @Test
    fun `UGR catalog entry exposes the implemented contract without E2E promotion`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val ugr = catalog.entries.single { it.portalId == PortalId("ugr-sede") }

        assertEquals(ProfileId("ugr-certificado-login"), ugr.profileId)
        assertEquals("ES-PUB-0018", ugr.inventoryId)
        assertEquals(
            "https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp",
            ugr.entryUrl.toString(),
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, ugr.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, ugr.catalogStatus)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in ugr.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in ugr.observedMechanisms)
        assertTrue(PortalMechanism.AUTOSCRIPT in ugr.observedMechanisms)
        assertTrue(PortalMechanism.MINIAPPLET in ugr.observedMechanisms)
        assertTrue(ugr.limitations.contains("E2E", ignoreCase = true))
    }

    @Test
    fun `Cantabria REC catalog exposes only the QA implemented contract pending E2E`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val portal = catalog.entries.single {
            it.portalId == PortalId("cantabria-registro-electronico-comun")
        }

        assertEquals(ProfileId("cantabria-rec-cert-login"), portal.profileId)
        assertEquals("ES-PUB-0101", portal.inventoryId)
        assertEquals(
            "https://rec.cantabria.es/rec/bienvenida.htm",
            portal.entryUrl.toString(),
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in portal.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in portal.observedMechanisms)
        assertTrue(PortalMechanism.AUTOFIRMA in portal.observedMechanisms)
        assertTrue(PortalMechanism.MINIAPPLET in portal.observedMechanisms)
        assertTrue(SignatureFormat.CADES in portal.observedSignatureFormats)
        assertTrue(portal.limitations.contains("E2E", ignoreCase = true))
    }

    @Test
    fun `Melilla catalog exposes the implemented QA batch contract without E2E promotion`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val portal = catalog.entries.single { it.portalId == PortalId("melilla-sede") }

        assertEquals(ProfileId("melilla-sede"), portal.profileId)
        assertEquals("ES-PUB-0107", portal.inventoryId)
        assertEquals(
            "https://sede.melilla.es/sta/CarpetaPublic/doEvent?" +
                "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999",
            portal.entryUrl.toString(),
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in portal.observedMechanisms)
        assertTrue(PortalMechanism.AUTOSCRIPT in portal.observedMechanisms)
        assertTrue(SignatureFormat.CADES in portal.observedSignatureFormats)
        assertTrue(portal.limitations.contains("E2E", ignoreCase = true))
    }

    @Test
    fun `Educacion REG alias retains ministry procedure metadata while resolving only exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-educacion-formacion-profesional-y-deportes")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            URI("https://www.educacionfpydeportes.gob.es/servicios-al-ciudadano/catalogo/general/20/203317/italia/laboral-liceo-cervantes-roma-2026.html"),
            portal.entryUrl,
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }


    @Test
    fun `PAG REG alias retains the official PAG URL while resolving the exact REG AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single { it.portalId == PortalId("age-pag-reg") }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            URI("https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html?idioma=es&imprimir=1"),
            portal.entryUrl,
        )
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `US alias retains its official procedure URL while resolving the exact REG-AGE launch URL`() {
        val aliasJson = json
            .replace(
                "\"inventoryId\": \"ES-PUB-0019\",\n      \"profileId\": null,",
                "\"inventoryId\": \"ES-PUB-0019\",\n      \"profileId\": \"reg-age-redsara\",",
            )
            .replace(
                "\"entryUrl\": \"https://sede.us.es/opencms/system/modules/sede/contents/pages/requisitosTecnicos\",",
                "\"entryUrl\": \"https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01\",\n" +
                    "      \"launchUrl\": \"https://reg.redsara.es/es/\",",
            )
        val aliasCatalog = PublicPortalCatalogParser.parse(aliasJson)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = aliasCatalog,
        )

        val portal = repository.portals().single { it.portalId == PortalId("us-sede") }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            URI("https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01"),
            portal.entryUrl,
        )
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `strict parser rejects unknown duplicate insecure and malformed records`() {
        listOf(
            json.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"unknown\": true"),
            json.replaceFirst("\"catalogVersion\": 1", "\"catalogVersion\": 1, \"catalogVersion\": 1"),
            json.replaceFirst("https://sede.administracion.gob.es/", "http://sede.administracion.gob.es/"),
            json.replaceFirst("https://sede.administracion.gob.es/", "https://user@sede.administracion.gob.es/"),
            json.replaceFirst("https://sede.administracion.gob.es/", "https://sede.administracion.gob.es/#fragment"),
            json.replaceFirst("https://sede.administracion.gob.es/", "https://sede.administracion.gob.es:443/"),
            json.replaceFirst("https://sede.administracion.gob.es/", "https://sede.administracion.gob.es/a/../"),
            json.replaceFirst("\"launchUrl\": \"https://reg.redsara.es/es/\"", "\"launchUrl\": \"http://reg.redsara.es/es/\""),
            json.replaceFirst("\"launchUrl\": \"https://reg.redsara.es/es/\"", "\"launchUrl\": \"https://user@reg.redsara.es/es/\""),
            json.replaceFirst("\"launchUrl\": \"https://reg.redsara.es/es/\"", "\"launchUrl\": \"https://reg.redsara.es:443/es/\""),
            json.replaceFirst("\"launchUrl\": \"https://reg.redsara.es/es/\"", "\"launchUrl\": \"https://reg.redsara.es/es/#fragment\""),
            json.replaceFirst("\"launchUrl\": \"https://reg.redsara.es/es/\"", "\"launchUrl\": \"https://reg.redsara.es/a/../es/\""),
            json.replaceFirst(
                "\"profileId\": \"reg-age-redsara\",\n      \"displayName\": \"Sede electrónica de la Universidad de Sevilla\"",
                "\"profileId\": null,\n      \"displayName\": \"Sede electrónica de la Universidad de Sevilla\"",
            ),
            json.replaceFirst("\"portalId\": \"age-pag-reg\"", "\"portalId\": \"INVALID\""),
            json.replaceFirst("\"portalId\": \"age-reg-redsara\"", "\"portalId\": \"age-pag-reg\""),
            json.replaceFirst(
                "\"profileId\": \"unizar-tramitador\"",
                "\"profileId\": \"reg-age-redsara\"",
            ),
            json.replaceFirst("\"catalogStatus\": \"CATALOGED\"", "\"catalogStatus\": \"IMPLEMENTED\""),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                PublicPortalCatalogParser.parse(invalid)
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            PublicPortalCatalogParser.parse(
                json + " ".repeat(PublicPortalCatalogParser.MAX_CATALOG_CHARS - json.length + 1),
            )
        }
    }

    @Test
    fun `BNE alias retains the official register page while resolving exact REG AGE launch`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-biblioteca-nacional-de-espana")
        }
        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://sede.bne.gob.es/es/tramites/quejas-sugerencias"), portal.entryUrl)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Mallorca institutional alias binds only the exact reviewed Registre launch`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val portal = catalog.entries.single {
            it.portalId == PortalId("mallorca-portal-institucional")
        }

        assertEquals("ES-PUB-0119", portal.inventoryId)
        assertEquals(ProfileId("consell-mallorca-sede"), portal.profileId)
        assertEquals(URI("https://www.conselldemallorca.es/"), portal.entryUrl)
        assertEquals(
            URI("https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082"),
            portal.launchUrl,
        )
        assertEquals("DELEGACION_MALLORCA_SEDE", portal.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(portal.observedMechanisms.isEmpty())
        assertTrue(portal.observedSignatureFormats.isEmpty())
    }

    @Test
    fun `Tenerife institutional alias binds only the exact Sede launch`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val portal = catalog.entries.single {
            it.portalId == PortalId("tenerife-portal-institucional")
        }

        assertEquals("ES-PUB-0127", portal.inventoryId)
        assertEquals(ProfileId("tenerife-sede-electronica"), portal.profileId)
        assertEquals(URI("https://www.tenerife.es/"), portal.entryUrl)
        assertEquals(URI("https://sede.tenerife.es/"), portal.launchUrl)
        assertEquals("DELEGACION_TENERIFE_SEDE", portal.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(portal.observedMechanisms.isEmpty())
        assertTrue(portal.observedSignatureFormats.isEmpty())
    }

    @Test
    fun `DSCA REG alias retains the live ministry procedure while resolving only exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-derechos-sociales-consumo-y-agenda-2030")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            URI("https://www.dsca.gob.es/es/derechos-sociales/derechos-animales/premios/artisticos/v-certamen-clipmetraje"),
            portal.entryUrl,
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Inclusion alias retains the official Sede URL while resolving the exact REG AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-inclusion-seguridad-social-y-migraciones")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://sede.inclusion.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Cervantes alias retains the official Sede URL while resolving the exact REG AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-instituto-cervantes")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://cervantes.sede.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Reina Sofia alias retains the official Sede URL while resolving the exact REG AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-museo-nacional-centro-de-arte-reina-sofia")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://museoreinasofia.sede.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `INAP alias retains the official Sede URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-instituto-nacional-de-administracion-publica-inap")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://sede.inap.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Igualdad alias retains the official Sede URL while resolving the exact REG AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-igualdad")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://igualdad.sede.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Cantabria Sede alias binds only the exact implemented REC launch`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val portal = catalog.entries.single { it.portalId == PortalId("cantabria-sede") }

        assertEquals(ProfileId("cantabria-rec-cert-login"), portal.profileId)
        assertEquals("ES-PUB-0100", portal.inventoryId)
        assertEquals("https://sede.cantabria.es/sede/", portal.entryUrl.toString())
        assertEquals(java.net.URI("https://rec.cantabria.es/rec/bienvenida.htm"), portal.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(portal.limitations.contains("alias", ignoreCase = true))
    }

    @Test
    fun `La Palma institutional alias binds only the exact implemented Sede launch`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val portal = catalog.entries.single { it.portalId == PortalId("la-palma-portal-institucional") }

        assertEquals(ProfileId("la-palma-sede-electronica"), portal.profileId)
        assertEquals("ES-PUB-0129", portal.inventoryId)
        assertEquals("https://www.cabildodelapalma.es/", portal.entryUrl.toString())
        assertEquals(java.net.URI("https://sedeelectronica.cabildodelapalma.es/"), portal.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(portal.limitations.contains("alias", ignoreCase = true))
    }

    @Test
    fun `MITECO REG alias retains ministry evidence while resolving only exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-para-la-transicion-ecologica-y-el-reto-demografico")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            URI("https://www.miteco.gob.es/es/costas/participacion-publica/30-cnc12-07-30-0006.html"),
            portal.entryUrl,
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `AEMPS alias retains the official Sede URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-agencia-espanola-de-medicamentos-y-productos-sanitarios-aemps")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://sede.aemps.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Puertos alias retains the official REG service URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-puertos-del-estado")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            URI("https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"),
            portal.entryUrl,
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Exteriores REG alias retains the official consular procedure while resolving only exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-asuntos-exteriores-union-europea-y-cooperacion")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            URI("https://www.exteriores.gob.es/Consulados/monterrey/es/ServiciosConsulares/Paginas/index.aspx?scca=Inscripci%C3%B3n+Consular&scco=M%C3%A9xico&scd=198&scs=Baja+del+Registro+de+Matr%C3%ADcula"),
            portal.entryUrl,
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `MIVAU REG alias retains official service while resolving only exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-vivienda-y-agenda-urbana")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            "ES-PUB-0076",
            catalog.entries.single { it.portalId == portal.portalId }.inventoryId,
        )
        assertEquals(
            URI("https://mivau.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"),
            portal.entryUrl,
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `MAPA alias retains the official Sede URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-agricultura-pesca-y-alimentacion")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://sede.mapa.gob.es/portal/site/seMAPA"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Cultura REG AGE alias binds only the exact existing profile launch`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val portal = catalog.entries.single { it.portalId == PortalId("age-ministerio-de-cultura") }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals("ES-PUB-0062", portal.inventoryId)
        assertEquals(
            "https://cultura.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General",
            portal.entryUrl.toString(),
        )
        assertEquals(java.net.URI("https://reg.redsara.es/es/"), portal.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(portal.limitations.contains("reg-age", ignoreCase = true))
        assertTrue(portal.limitations.contains("qa", ignoreCase = true))
    }

    @Test
    fun `Juventud e Infancia alias retains the official REG service URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-juventud-e-infancia")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://juventudeinfancia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Defensa alias retains the official Sede URL while resolving only exact REG AGE launch`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-defensa")
        }
        val catalogEntry = catalog.entries.single { it.portalId == portal.portalId }
        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals("ES-PUB-0063", catalogEntry.inventoryId)
        assertEquals(URI("https://sede.defensa.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalogEntry.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `MPR alias retains the official REG service URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-la-presidencia-justicia-y-relaciones-con-las-cortes")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://mpr.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `MPTMD alias retains the official REG service URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-politica-territorial-y-memoria-democratica")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://mptmd.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Industria alias retains the official Sede URL while resolving the exact REG AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-de-industria-y-turismo")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://sede.minetur.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Interior alias retains the official generic-form URL while resolving the exact REG-AGE launch URL`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )

        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-del-interior")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://sede.interior.gob.es/portal/sede/tramites?codAgrupacion=GENERAL"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Transformacion Digital REG alias retains official Sede while resolving exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-ministerio-para-la-transformacion-digital-y-de-la-funcion-publica")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://digital.sede.gob.es/"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Alava Registro Comun binds exact QA start without signer capability`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.inventoryId == "ES-PUB-0140" }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val start = URI("https://egoitza.araba.eus/izapidetu/at/01/es/0000301")

        assertEquals(ProfileId("diputacion-alava-registro-comun"), metadata.profileId)
        assertEquals(start, metadata.entryUrl)
        assertEquals(null, metadata.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals(
            setOf(PortalMechanism.CERTIFICATE_ACCESS, PortalMechanism.ELECTRONIC_SIGNATURE),
            metadata.observedMechanisms,
        )
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(ProfileId("diputacion-alava-registro-comun"), start),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `OEPM ProtegeO entry binds the exact QA public launch without sensitive observations`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = catalog.entries.single {
            it.portalId == PortalId("age-oficina-espanola-de-patentes-y-marcas")
        }
        val item = repository.portals().single { it.portalId == portal.portalId }

        assertEquals("ES-PUB-0082", portal.inventoryId)
        assertEquals(ProfileId("oepm-protegeo-general"), portal.profileId)
        assertEquals(
            URI("https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM"),
            portal.entryUrl,
        )
        assertEquals(null, portal.launchUrl)
        assertEquals("OEPM_PROTEGEO_PUBLIC_LAUNCH", portal.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, portal.catalogStatus)
        assertTrue(portal.observedMechanisms.isEmpty())
        assertTrue(portal.observedSignatureFormats.isEmpty())
        assertTrue(item.capabilities.isEmpty())
        assertTrue(item.signatureFormats.isEmpty())
        assertTrue(item.isEnabled)
        assertEquals(
            PortalLaunchTarget(ProfileId("oepm-protegeo-general"), portal.entryUrl),
            repository.resolveLaunch(item),
        )
    }

    @Test
    fun `Castilla Leon QUJU resolves only the exact QA public form without signing capability`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.portalId == PortalId("castilla-leon-tramita") }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val form = URI("https://presidencia.jcyl.es/QUJU?O=1")

        assertEquals("ES-PUB-0102", metadata.inventoryId)
        assertEquals(ProfileId("castilla-leon-quju-public"), metadata.profileId)
        assertEquals(form, metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals("JCYL_QUJU_PUBLIC_FORM_BOUNDARY", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertTrue(metadata.observedMechanisms.isEmpty())
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertEquals(PortalLaunchTarget(ProfileId("castilla-leon-quju-public"), form), repository.resolveLaunch(portal))
    }

    @Test
    fun `SEPES binds only the current Transportes public page and keeps signing unimplemented`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.inventoryId == "ES-PUB-0045" }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val start = URI("https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones")

        assertEquals(ProfileId("sepes-transportes-public-complaints"), metadata.profileId)
        assertEquals(start, metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals("SEPES_TRANSPORTES_PUBLIC_NAVIGATION", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals("REVIEWED", metadata.discoveryState.name)
        assertEquals(setOf(PortalMechanism.ELECTRONIC_SIGNATURE), metadata.observedMechanisms)
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertEquals(PortalLaunchTarget(ProfileId("sepes-transportes-public-complaints"), start), repository.resolveLaunch(portal))
    }

    @Test
    fun `BOE public Sede binds exact QA information page without sensitive capabilities`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single {
            it.portalId == PortalId("age-agencia-estatal-del-boletin-oficial-del-estado-boe")
        }
        val portal = repository.portals().single { it.portalId == metadata.portalId }

        assertEquals("ES-PUB-0026", metadata.inventoryId)
        assertEquals(ProfileId("boe-sede-public-home"), metadata.profileId)
        assertEquals(URI("https://www.boe.es/informacion/index.php"), metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals("BOE_SEDE_PUBLIC_NAVIGATION", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertTrue(metadata.observedMechanisms.isEmpty())
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(ProfileId("boe-sede-public-home"), URI("https://www.boe.es/informacion/index.php")),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Portal Funciona keeps official directory entry but resolves only the exact QA public home`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.portalId == PortalId("age-portal-funciona") }
        val portal = repository.portals().single { it.portalId == metadata.portalId }

        assertEquals("ES-PUB-0084", metadata.inventoryId)
        assertEquals(ProfileId("portal-funciona-public-home"), metadata.profileId)
        assertEquals(URI("https://sede.funciona.gob.es/es/home"), metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals("OIDC_PKCE_AUTENTICA_SAML_CLIENT_TLS_BOUNDARY", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals(
            setOf(PortalMechanism.CERTIFICATE_ACCESS, PortalMechanism.CLIENT_TLS_AUTH),
            metadata.observedMechanisms,
        )
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(ProfileId("portal-funciona-public-home"), URI("https://sede.funciona.gob.es/es/home")),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Fondos Europeos keeps exact official Sede and resolves only QA public navigation`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.portalId == PortalId("age-direccion-general-de-fondos-europeos") }
        val portal = repository.portals().single { it.portalId == metadata.portalId }

        assertEquals("ES-PUB-0039", metadata.inventoryId)
        assertEquals(ProfileId("fondos-europeos-sede-public-home"), metadata.profileId)
        assertEquals(URI("https://sedefondoscomunitarios.gob.es/"), metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals("DGFE_PUBLIC_SEDE_NAVIGATION", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals("REVIEWED", metadata.discoveryState.name)
        assertTrue(metadata.observedMechanisms.isEmpty())
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertEquals(PortalLaunchTarget(ProfileId("fondos-europeos-sede-public-home"), URI("https://sedefondoscomunitarios.gob.es/")), repository.resolveLaunch(portal))
    }

    @Test
    fun `DGSFP keeps exact official Sede and resolves only QA public navigation`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.portalId == PortalId("age-direccion-general-de-seguros-y-fondos-de-pensiones") }
        val portal = repository.portals().single { it.portalId == metadata.portalId }

        assertEquals("ES-PUB-0042", metadata.inventoryId)
        assertEquals(ProfileId("dgsfp-sede-public-home"), metadata.profileId)
        assertEquals(URI("https://www.sededgsfp.gob.es/"), metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals("DGSFP_PUBLIC_SEDE_NAVIGATION", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals("REVIEWED", metadata.discoveryState.name)
        assertTrue(metadata.observedMechanisms.isEmpty())
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertEquals(PortalLaunchTarget(ProfileId("dgsfp-sede-public-home"), URI("https://www.sededgsfp.gob.es/")), repository.resolveLaunch(portal))
    }

    @Test
    fun `Comercio REG alias retains exact public procedure while resolving exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-secretaria-de-estado-de-comercio")
        }
        val procedure = URI(
            "https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio/detalle-procedimiento?val=3057517",
        )

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(procedure, portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Digital Sede REG alias retains migrated SEDIA SETID entry while resolving exact REG AGE launch`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-sede-electronica-de-la-s-e-de-digitalizacion-e-inteligencia-artificial-y-s-e-de-telecomunica")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(URI("https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx"), portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(
            PublicCatalogStatus.E2E_PENDING,
            catalog.entries.single { it.portalId == portal.portalId }.catalogStatus,
        )
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Hacienda central alias retains institutional Sede while resolving exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single {
            it.portalId == PortalId("age-sede-electronica-central-del-ministerio")
        }
        val portal = repository.portals().single { it.portalId == metadata.portalId }

        assertEquals("ES-PUB-0088", metadata.inventoryId)
        assertEquals(ProfileId("reg-age-redsara"), metadata.profileId)
        assertEquals(URI("https://sede.hacienda.gob.es/"), metadata.entryUrl)
        assertEquals(URI("https://reg.redsara.es/es/"), metadata.launchUrl)
        assertEquals("DELEGACION_REG_AGE", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertTrue(metadata.observedMechanisms.isEmpty())
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(ProfileId("reg-age-redsara"), URI("https://reg.redsara.es/es/")),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Tesoro REC alias retains exact public procedure while resolving exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-tesoro-publico")
        }
        val procedure = URI(
            "https://www.tesoropublico.gob.es/es/servicios/adhesion-al-codigo-de-buenas-practicas-para-deudores-hipotecarios-en-riesgo-de",
        )

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(procedure, portal.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, catalog.entries.single { it.portalId == portal.portalId }.catalogStatus)
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Junta VEA PEG binds exact current public start without sensitive launch alias`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.inventoryId == "ES-PUB-0093" }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val start = URI("https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA")

        assertEquals(PortalId("junta-andalucia-sede"), metadata.portalId)
        assertEquals(ProfileId("junta-andalucia-vea-peg"), metadata.profileId)
        assertEquals(start, metadata.entryUrl)
        assertEquals(null, metadata.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals(
            setOf("AUTOFIRMA", "AUTOSCRIPT", "CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"),
            metadata.observedMechanisms.map { it.name }.toSet(),
        )
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertTrue(portal.capabilities.isEmpty())
        assertEquals(PortalLaunchTarget(ProfileId("junta-andalucia-vea-peg"), start), repository.resolveLaunch(portal))
    }

    @Test
    fun `UNED REG alias retains the current UNED service while resolving only exact REG AGE`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val portal = repository.portals().single {
            it.portalId == PortalId("age-universidad-nacional-de-educacion-a-distancia-uned")
        }

        assertEquals(ProfileId("reg-age-redsara"), portal.profileId)
        assertEquals(
            URI("https://uned.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"),
            portal.entryUrl,
        )
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, portal.inventoryStatus)
        assertEquals(
            PublicCatalogStatus.E2E_PENDING,
            catalog.entries.single { it.portalId == portal.portalId }.catalogStatus,
        )
        assertTrue(portal.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                profileId = ProfileId("reg-age-redsara"),
                entryUrl = URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(portal),
        )
    }

    @Test
    fun `Murcia CARM binds protected procedure navigation without native certificate or signing capability`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.inventoryId == "ES-PUB-0113" }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val start = URI(
            "https://sede.carm.es/web/pagina?IDCONTENIDO=385&IDTIPO=240&RASTRO=c%24m40293%2C62654%2C40288",
        )

        assertEquals(PortalId("murcia-sede"), metadata.portalId)
        assertEquals(ProfileId("murcia-carm-pase"), metadata.profileId)
        assertEquals(start, metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals(
            setOf("CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"),
            metadata.observedMechanisms.map { it.name }.toSet(),
        )
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(ProfileId("murcia-carm-pase"), start), repository.resolveLaunch(portal))
    }

    @Test
    fun `Asturias Sede entry binds the current redirect-only QA profile`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.inventoryId == "ES-PUB-0096" }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val start = URI("https://sede.asturias.es/ast/-/dboid-6269000011903512107573")

        assertEquals(PortalId("asturias-sede-tramite-autofirma"), metadata.portalId)
        assertEquals(ProfileId("asturias-sede-tramite-navigation"), metadata.profileId)
        assertEquals(start, metadata.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals(setOf("ELECTRONIC_SIGNATURE"), metadata.observedMechanisms.map { it.name }.toSet())
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertTrue(portal.capabilities.isEmpty())
        assertEquals(PortalLaunchTarget(ProfileId("asturias-sede-tramite-navigation"), start), repository.resolveLaunch(portal))
    }

    @Test
    fun `Gipuzkoa Registro binds exact QA public start without exposing observed client TLS as capability`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.inventoryId == "ES-PUB-0157" }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val start = URI(
            "https://egoitza.gipuzkoa.eus/WAS/CORP/WATTramiteakWEB/inicio.do?idioma=C&app=00001",
        )

        assertEquals(PortalId("diputacion-gipuzkoa-sede"), metadata.portalId)
        assertEquals(ProfileId("diputacion-gipuzkoa-registro-public"), metadata.profileId)
        assertEquals(start, metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals("GILTZA_OAUTH_DELEGATED_CLIENT_TLS", metadata.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals(
            setOf(PortalMechanism.CERTIFICATE_ACCESS, PortalMechanism.CLIENT_TLS_AUTH),
            metadata.observedMechanisms,
        )
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertTrue(portal.isEnabled)
        assertEquals(PortalLaunchTarget(ProfileId("diputacion-gipuzkoa-registro-public"), start), repository.resolveLaunch(portal))
    }

    @Test
    fun `Madrid Cuenta Digital 53F1 binds only bounded QA navigation`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val siteProfiles = BuiltInSiteProfiles.catalog
        val repository = PortalCatalogRepository(
            registry = SiteProfileRegistry(siteProfiles, BuildTrustPolicy.QA),
            profileCatalog = siteProfiles,
            publicCatalog = catalog,
        )
        val metadata = catalog.entries.single { it.inventoryId == "ES-PUB-0179" }
        val portal = repository.portals().single { it.portalId == metadata.portalId }
        val start = URI("https://digital.comunidad.madrid/ext/53F1")

        assertEquals(PortalId("comunidad-madrid-cuenta-digital-carne-joven"), metadata.portalId)
        assertEquals(ProfileId("comunidad-madrid-cuenta-digital-53f1"), metadata.profileId)
        assertEquals(start, metadata.entryUrl)
        assertNull(metadata.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, metadata.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, metadata.catalogStatus)
        assertEquals("CUENTA_DIGITAL_AUTH_CLIENT_TLS_BOUNDARY", metadata.protocolFamily)
        assertEquals(
            setOf(
                PortalMechanism.CERTIFICATE_ACCESS,
                PortalMechanism.CLIENT_TLS_AUTH,
                PortalMechanism.ELECTRONIC_SIGNATURE,
            ),
            metadata.observedMechanisms,
        )
        assertTrue(metadata.observedSignatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
        assertTrue(portal.isEnabled)
        assertTrue(portal.capabilities.isEmpty())
        assertTrue(portal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(ProfileId("comunidad-madrid-cuenta-digital-53f1"), start), repository.resolveLaunch(portal))
    }

}
