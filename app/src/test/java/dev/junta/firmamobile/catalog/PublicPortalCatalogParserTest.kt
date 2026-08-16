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
        assertEquals(30, catalog.entries.count { it.profileId != null })
        assertEquals(catalog.entries.size, catalog.entries.map { it.portalId }.toSet().size)
        assertEquals(catalog.entries.size, catalog.entries.map { it.entryUrl }.toSet().size)
        assertEquals(
            setOf(
                ProfileId("junta-andalucia"),
                ProfileId("reg-age-redsara"),
                ProfileId("unizar-tramitador"),
                ProfileId("carne-joven-andalucia"),
                ProfileId("junta-ofvirtual"),
                ProfileId("educacion-convocatoria"),
                ProfileId("aragon-siraw"),
                ProfileId("aeat-mis-datos-censales"),
                ProfileId("dgt-verificacion-equipo"),
                ProfileId("ugr-certificado-login"),
                ProfileId("cantabria-rec-cert-login"),
                ProfileId("jccm-certificate-login-probe"),
                ProfileId("sevilla-atse-certificate-login"),
                ProfileId("melilla-sede"),
                ProfileId("ceuta-sede"),
                ProfileId("extremadura-tramites"),
                ProfileId("diputacion-valladolid-sede"),
                ProfileId("la-palma-sede-electronica"),
                ProfileId("diputacion-huesca-portal"),
                ProfileId("diputacion-lugo-sede"),
                ProfileId("ministerio-sanidad-certificado"),
                ProfileId("tea-alegaciones-certificado"),
                ProfileId("tenerife-sede-electronica"),
                ProfileId("diputacion-toledo-sede"),
                ProfileId("isciii-certificate-selection"),
                ProfileId("diputacion-valencia-sede"),
                ProfileId("policia-solicitud-generica"),
                ProfileId("cdti-certificate-validation"),
            ),
            catalog.entries.mapNotNull { it.profileId }.toSet(),
        )
        assertTrue(catalog.entries.count { it.catalogStatus == PublicCatalogStatus.DISCOVERED } >= 67)
        assertTrue(catalog.entries.count { it.inventoryStatus == PortalInventoryStatus.BROWSE_ONLY } >= 148)
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
}
