package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SignatureFormat
import dev.junta.firmamobile.profile.SiteProfileCatalog
import dev.junta.firmamobile.profile.SiteProfileRegistry
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
class PortalCatalogRepositoryTest {
    private val catalog = BuiltInSiteProfiles.catalog
    private val publicCatalog by lazy(::loadBundledPublicPortalCatalog)
    private val releaseRepository by lazy {
        PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.RELEASE),
            catalog,
            publicCatalog,
        )
    }
    private val qaRepository by lazy {
        PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            publicCatalog,
        )
    }

    @Test
    fun `public catalog stays complete while release opens only eligible trusted profiles`() {
        val qaPortals = qaRepository.portals()
        val releasePortals = releaseRepository.portals()

        assertEquals(1, qaRepository.bundledCatalogVersion)
        assertEquals(publicCatalog.entries.size, qaPortals.size)
        assertEquals(publicCatalog.entries.size, releasePortals.size)
        assertEquals(
            setOf(
                "junta-andalucia",
                "junta-andalucia-vea-peg",
                "mjusticia-fundaciones-idp75",
                "comunidad-madrid-registro-general",
                "reg-age-redsara",
                "unizar-tramitador",
                "carne-joven-andalucia",
                "junta-ofvirtual",
                "educacion-convocatoria",
                "aragon-siraw",
                "aragon-solicitud-general-client-auth",
                "aeat-mis-datos-censales",
                "aemet-public-solicitud-navigation",
                "dgt-verificacion-equipo",
                "ugr-certificado-login",
                "cantabria-rec-cert-login",
                "jccm-certificate-login-probe",
                "jccm-registro-generico",
                "mites-certificate-login",
                "transportes-qys-cert-login",
                "sevilla-atse-certificate-login",
                "airef-instancia-general",
                "mugeju-remision-documentacion-client-auth",
                "melilla-sede",
                "ceuta-sede",
                "age-acceda",
                "extremadura-tramites",
                "extremadura-pattex-client-auth",
                "navarra-sede-registro-general",
                "diputacion-valladolid-sede",
                "diputacion-burgos-portal",
                "la-palma-sede-electronica",
                "diputacion-huesca-portal",
                "diputacion-lugo-sede",
                "diputacion-leon-sede",
                "diputacion-albacete-portal",
                "diputacion-jaen-sede",
                "consell-mallorca-sede",
                "diputacion-cuenca-portal",
                "generalitat-valenciana-client-auth",
                "tgss-importass-client-auth",
                "ministerio-sanidad-certificado",
                "tea-alegaciones-certificado",
                "tenerife-sede-electronica",
                "fuerteventura-sede-electronica",
                "gran-canaria-sede-electronica",
                "age-portal-de-la-transparencia",
                "caib-portafib",
                "ministerio-economia-instancia-generica",
                "diputacion-toledo-sede",
                "isciii-certificate-selection",
                "diputacion-valencia-sede",
                "diputacion-alicante-solicitud-general",
                "diputacion-almeria-solicitud-general",
                "diputacion-granada-sede-public",
                "diputacion-castellon-instancia-general",
                "policia-solicitud-generica",
                "diputacion-lleida-sede",
                "diputacion-badajoz-portal",
                "diputacion-alava-registro-comun",
                "diputacion-bizkaia-instancia-generica",
                "oepm-protegeo-general",
                "portal-funciona-public-home",
                "madrid-sede-tarjeta-azul",
                "fondos-europeos-sede-public-home",
                "diputacion-teruel-instancia-general",
                "sepes-transportes-public-complaints",
                "dgsfp-sede-public-home",
                "cnmv-sede-public-home",
                "aesa-solicitud-general-public",
                "boe-sede-public-home",
                "cnmc-remision-solicitudes-public",
                "adif-sede-public-home",
                "castilla-leon-quju-public",
                "diputacion-avila-instancia-general",
                "diputacion-guadalajara-instancia-general",
                "diputacion-segovia-registro",
                "ctbg-solicitud-informacion",
                "catastro-solicitudes-genericas",
                "fega-solicitud-general-ofvsg02",
                "diputacion-huelva-sede-public",
                "diputacion-ciudad-real-registro-telematico",
                "diputacion-cordoba-solicitud-generica",
                "diputacion-caceres-instancia-general",
                "cdti-certificate-validation",
                "xunta-galicia-solicitude-xenerica",
                "la-rioja-oficina-electronica",
                "asturias-miprincipado",
                "asturias-sede-tramite-navigation",
                "menorca-carpeta-ciutadana",
                "canarias-sede",
                "comunidad-madrid-cuenta-digital-53f1",
                "justicia-sede-judicial-private-area",
                "diputacion-gipuzkoa-registro-public",
                "diputacion-barcelona-solicitud-generica-2057",
                "la-gomera-instancia-general",
                "la-gomera-sede-public-navigation",
                "lanzarote-instancia-general",
                "lanzarote-sede-public-navigation",
                "zamora-sede-public-navigation",
                "zaragoza-sede-public-navigation",
                "diputacion-pontevedra-instancia-xenerica",
                "diputacion-malaga-instancia-general",
                "diputacion-girona-instancia-generica",
                "diputacion-cadiz-solicitud-generica",
                "diputacion-tarragona-sede",
                "eivissa-sede-electronica",
                "diputacion-salamanca-instancia-general",
                "catalunya-peticio-generica-client-auth",
                "murcia-carm-pase",
                "enaire-sede-public",
                "dgoj-public-navigation",
                "guardia-civil-sede-public",
                "csn-sede-public",
                "csd-sede-public",
                "cmt-public-navigation",
                "diputacion-palencia-solicitud-general",
                "el-hierro-solicitud-general",
                "catalunya-seu-registre-client-auth",
                "diputacion-ourense-sede",
                "diputacion-sevilla-sede",
                "diputacion-a-coruna-solicitud-general",
                "euskadi-sede-electronica",
                "formentera-sede-electronica",
                "iac-sede-public-navigation",
                "icac-sede-public-navigation",
                "itj-sede-public-navigation",
                "red-es-sede-public-navigation",
                "formentera-portal-institucional-navigation",
                "ico-sede-public-navigation",
                "el-hierro-sede-public-navigation",
                "imserso-sede-public-navigation",
                "ine-sede-public-navigation",
                "isfas-sede-public-navigation",
            ),
            qaPortals.mapNotNull { it.profileId?.value }.toSet(),
        )
        val metadataOnly = qaPortals.filter { it.profileId == null }
        assertTrue(metadataOnly.all { !it.isEnabled })
        assertTrue(metadataOnly.all { it.capabilities.isEmpty() && it.signatureFormats.isEmpty() })
        assertTrue(metadataOnly.all { qaRepository.resolveLaunch(it) == null })
        assertEquals(
            setOf(PortalInventoryStatus.UNSUPPORTED_PROTOCOL, PortalInventoryStatus.INACCESSIBLE),
            metadataOnly.map { it.inventoryStatus }.toSet(),
        )
        assertTrue(
            metadataOnly.all {
                it.supportStatus in setOf(
                    PortalSupportStatus.UNSUPPORTED_PROTOCOL,
                    PortalSupportStatus.INACCESSIBLE,
                )
            },
        )

        val verifiedIds = setOf(
            ProfileId("carne-joven-andalucia"),
            ProfileId("aragon-siraw"),
            ProfileId("junta-ofvirtual"),
            ProfileId("unizar-tramitador"),
        )
        verifiedIds.forEach { profileId ->
            assertEquals(PortalSupportStatus.VERIFIED_E2E, qaPortals.single { it.profileId == profileId }.supportStatus)
        }
        qaPortals.filter { it.profileId != null && it.profileId !in verifiedIds }.forEach { portal ->
            assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
            assertTrue(portal.isEnabled)
        }

        verifiedIds.forEach { profileId ->
            val releasePortal = releasePortals.single { it.profileId == profileId }
            assertEquals(PortalSupportStatus.VERIFIED_E2E, releasePortal.supportStatus)
            assertTrue(releasePortal.isEnabled)
        }
        releasePortals.filter { it.profileId != null && it.profileId !in verifiedIds }.forEach { portal ->
            when (portal.profileId) {
                ProfileId("junta-andalucia") -> {
                    assertEquals(PortalSupportStatus.BROWSE_ONLY, portal.supportStatus)
                    assertFalse(portal.isEnabled)
                    assertEquals(null, releaseRepository.resolveLaunch(portal))
                }
                else -> {
                    assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, portal.supportStatus)
                    assertFalse(portal.isEnabled)
                    assertEquals(null, releaseRepository.resolveLaunch(portal))
                }
            }
        }
    }

    @Test
    fun `Educacion REG alias keeps ministry metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-educacion-formacion-profesional-y-deportes")
        val profileId = ProfileId("reg-age-redsara")
        val ministryEntry = java.net.URI(
            "https://www.educacionfpydeportes.gob.es/servicios-al-ciudadano/catalogo/general/20/203317/italia/laboral-liceo-cervantes-roma-2026.html",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(ministryEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, ministryEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }


    @Test
    fun `Ciencia alias keeps Ministry metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-ciencia-innovacion-y-universidades")
        val profileId = ProfileId("reg-age-redsara")
        val cienciaEntry = java.net.URI("https://ciencia.sede.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(cienciaEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, cienciaEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertNull(tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Transportes QYS exact launch is QA enabled and release fail closed`() {
        val profileId = ProfileId("transportes-qys-cert-login")
        val exact = java.net.URI("https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002")
        val qaPortal = qaRepository.portals().single { it.profileId == profileId }

        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, qaPortal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, exact), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, exact), qaRepository.resolveLaunch(profileId, exact))
        assertEquals(
            null,
            qaRepository.resolveLaunch(
                profileId,
                java.net.URI("https://sede.transportes.gob.es/MFOM.genericprocedure.web/Autenticacion.aspx"),
            ),
        )

        val releasePortal = releaseRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `MITES exact Sede root is QA enabled and release fail closed`() {
        val profileId = ProfileId("mites-certificate-login")
        val exact = java.net.URI("https://sede.mites.gob.es/")
        val qaPortal = qaRepository.portals().single { it.profileId == profileId }

        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, qaPortal.inventoryStatus)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertEquals(PortalLaunchTarget(profileId, exact), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, exact), qaRepository.resolveLaunch(profileId, exact))
        assertEquals(null, qaRepository.resolveLaunch(profileId, java.net.URI("https://sede.mites.gob.es/auth")))

        val releasePortal = releaseRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `CDTI exact validation page is QA enabled and release fail closed`() {
        val profileId = ProfileId("cdti-certificate-validation")
        val exact = java.net.URI(
            "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx",
        )
        val qaPortal = qaRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(exact, qaPortal.entryUrl)
        assertEquals(setOf(SignatureFormat.XADES), qaPortal.signatureFormats)
        assertEquals(PortalLaunchTarget(profileId, exact), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `PAG REG alias keeps PAG metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-pag-reg")
        val profileId = ProfileId("reg-age-redsara")
        val pagEntry = java.net.URI(
            "https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html?idioma=es&imprimir=1",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(pagEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, pagEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `US alias keeps US metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("us-sede")
        val profileId = ProfileId("reg-age-redsara")
        val usEntry = java.net.URI(
            "https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(usEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, usEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Cantabria REC contract is launchable only from QA registry`() {
        val portalId = PortalId("cantabria-registro-electronico-comun")
        val profileId = ProfileId("cantabria-rec-cert-login")
        val expectedUrl = java.net.URI("https://rec.cantabria.es/rec/bienvenida.htm")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(setOf(SignatureFormat.CADES), qaPortal.signatureFormats)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in qaPortal.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in qaPortal.observedMechanisms)
        assertEquals(PortalLaunchTarget(profileId, expectedUrl), qaRepository.resolveLaunch(qaPortal))
        assertTrue(qaPortal.limitations.contains("E2E", ignoreCase = true))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `Melilla batch contract is launchable only from QA registry pending E2E`() {
        val portalId = PortalId("melilla-sede")
        val profileId = ProfileId("melilla-sede")
        val expectedUrl = java.net.URI(
            "https://sede.melilla.es/sta/CarpetaPublic/doEvent?" +
                "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999",
        )

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(setOf(SignatureFormat.CADES), qaPortal.signatureFormats)
        assertTrue(PortalServiceCapability.ELECTRONIC_SIGNATURE in qaPortal.capabilities)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in qaPortal.observedMechanisms)
        assertTrue(PortalMechanism.AUTOSCRIPT in qaPortal.observedMechanisms)
        assertEquals(PortalLaunchTarget(profileId, expectedUrl), qaRepository.resolveLaunch(qaPortal))
        assertTrue(qaPortal.limitations.contains("E2E", ignoreCase = true))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, releasePortal.profileId)
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `Sevilla ATSE certificate login is launchable only from QA registry`() {
        val profileId = ProfileId("sevilla-atse-certificate-login")
        val expectedUrl = java.net.URI(
            "https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente",
        )

        val qaPortal = qaRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(setOf(SignatureFormat.XADES), qaPortal.signatureFormats)
        assertTrue(PortalServiceCapability.ELECTRONIC_SIGNATURE in qaPortal.capabilities)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in qaPortal.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in qaPortal.observedMechanisms)
        assertEquals(PortalLaunchTarget(profileId, expectedUrl), qaRepository.resolveLaunch(qaPortal))
        assertTrue(qaPortal.limitations.contains("E2E", ignoreCase = true))
        assertTrue(qaPortal.limitations.contains("authenticate", ignoreCase = true))

        val releasePortal = releaseRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `UGR is implemented and launchable only from QA registry`() {
        val profileId = ProfileId("ugr-certificado-login")
        val expectedUrl = java.net.URI(
            "https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp",
        )

        val qaPortal = qaRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(setOf(SignatureFormat.CADES), qaPortal.signatureFormats)
        assertTrue(PortalServiceCapability.ELECTRONIC_SIGNATURE in qaPortal.capabilities)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in qaPortal.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in qaPortal.observedMechanisms)
        assertEquals(PortalLaunchTarget(profileId, expectedUrl), qaRepository.resolveLaunch(qaPortal))
        assertTrue(qaPortal.limitations.contains("E2E", ignoreCase = true))

        val releasePortal = releaseRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }


    @Test
    fun `profile evidence and public catalog E2E status remain consistent`() {
        publicCatalog.entries.filter { it.profileId != null }.forEach { entry ->
            val profile = catalog.profiles.single { it.profileId == entry.profileId }
            val metadataIsE2e = entry.catalogStatus == PublicCatalogStatus.E2E_VERIFIED &&
                entry.inventoryStatus == PortalInventoryStatus.VERIFIED_E2E

            assertEquals(
                "${entry.portalId.value} / ${profile.profileId.value}",
                profile.compatibilityStatus == CompatibilityStatus.VERIFIED_E2E,
                metadataIsE2e,
            )
        }
    }

    @Test
    fun `Junta Oficina Virtual verified authentication is enabled in qa and release`() {
        val profileId = ProfileId("junta-ofvirtual")
        val expectedUrl = java.net.URI(
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
        )

        listOf(qaRepository, releaseRepository).forEach { repository ->
            val portal = repository.portals().single { it.profileId == profileId }
            assertEquals(PortalSupportStatus.VERIFIED_E2E, portal.supportStatus)
            assertTrue(portal.isEnabled)
            assertEquals(setOf(SignatureFormat.CADES), portal.signatureFormats)
            assertTrue(PortalMechanism.CERTIFICATE_ACCESS in portal.observedMechanisms)
            assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in portal.observedMechanisms)
            assertEquals(PortalLaunchTarget(profileId, expectedUrl), repository.resolveLaunch(portal))
            assertTrue(portal.limitations.contains("portal real aceptó", ignoreCase = true))
            assertTrue(portal.limitations.contains("login", ignoreCase = true))
        }
    }

    @Test
    fun `unizar verified authentication is enabled in qa and release`() {
        val profileId = ProfileId("unizar-tramitador")
        val expectedUrl = java.net.URI(
            "https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent",
        )

        listOf(qaRepository, releaseRepository).forEach { repository ->
            val portal = repository.portals().single { it.profileId == profileId }
            assertEquals(PortalSupportStatus.VERIFIED_E2E, portal.supportStatus)
            assertTrue(portal.isEnabled)
            assertEquals(setOf(SignatureFormat.CADES), portal.signatureFormats)
            assertEquals(
                setOf(PortalServiceCapability.ELECTRONIC_SIGNATURE),
                portal.capabilities,
            )
            assertTrue(PortalMechanism.CERTIFICATE_ACCESS in portal.observedMechanisms)
            assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in portal.observedMechanisms)
            assertEquals(PortalLaunchTarget(profileId, expectedUrl), repository.resolveLaunch(portal))
            assertTrue(portal.limitations.contains("portal real aceptó", ignoreCase = true))
            assertTrue(portal.limitations.contains("autenticación", ignoreCase = true))
        }
    }

    @Test
    fun `aragon siraw verified login is enabled in qa and release`() {
        val profileId = ProfileId("aragon-siraw")
        val expectedUrl = java.net.URI(
            "https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw",
        )
        val qaPortal = qaRepository.portals().single { it.profileId == profileId }

        assertEquals(PortalSupportStatus.VERIFIED_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(setOf(SignatureFormat.CADES), qaPortal.signatureFormats)
        assertEquals(
            setOf(PortalServiceCapability.ELECTRONIC_SIGNATURE),
            qaPortal.capabilities,
        )
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in qaPortal.observedMechanisms)
        assertEquals(PortalLaunchTarget(profileId, expectedUrl), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.VERIFIED_E2E, releasePortal.supportStatus)
        assertTrue(releasePortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, expectedUrl), releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `aragon solicitud general exposes only qa certificate access capability`() {
        val profileId = ProfileId("aragon-solicitud-general-client-auth")
        val expectedUrl = java.net.URI(
            "https://aplicaciones.aragon.es/tramitar/solicitud-general/identificacion",
        )
        val qaPortal = qaRepository.portals().single { it.profileId == profileId }

        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(emptySet<SignatureFormat>(), qaPortal.signatureFormats)
        assertEquals(setOf(PortalServiceCapability.CERTIFICATE_ACCESS), qaPortal.capabilities)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in qaPortal.observedMechanisms)
        assertEquals(PortalLaunchTarget(profileId, expectedUrl), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.profileId == profileId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `does not elevate capabilities or signature formats beyond the profile`() {
        qaRepository.portals().filter { it.profileId != null }.forEach { portal ->
            val profile = catalog.profiles.single { it.profileId == portal.profileId }
            assertEquals(
                Capability.SIGN in profile.capabilities,
                PortalServiceCapability.ELECTRONIC_SIGNATURE in portal.capabilities,
            )
            assertEquals(
                Capability.SELECT_CERTIFICATE in profile.capabilities ||
                    Capability.CLIENT_TLS_AUTH in profile.capabilities,
                PortalServiceCapability.CERTIFICATE_ACCESS in portal.capabilities,
            )
            assertEquals(
                profile.operationPolicies.values.mapNotNull { it.format }.toSet(),
                portal.signatureFormats,
            )
        }

        val carneJoven = qaRepository.portals().single {
            it.profileId == ProfileId("carne-joven-andalucia")
        }
        assertEquals(PortalSupportStatus.VERIFIED_E2E, carneJoven.supportStatus)
        assertTrue(PortalServiceCapability.CERTIFICATE_ACCESS in carneJoven.capabilities)
        assertFalse(PortalServiceCapability.ELECTRONIC_SIGNATURE in carneJoven.capabilities)
        assertTrue(carneJoven.signatureFormats.isEmpty())
        assertEquals(
            setOf(PortalMechanism.CERTIFICATE_ACCESS, PortalMechanism.CLIENT_TLS_AUTH),
            carneJoven.observedMechanisms,
        )

        val redSara = qaRepository.portals().single { it.portalId == PortalId("age-reg-redsara") }
        assertEquals(setOf(SignatureFormat.XADES), redSara.signatureFormats)
    }

    @Test
    fun `formentera pending navigation launch accepts only the exact canonical seed URL in qa`() {
        val id = ProfileId("formentera-sede-electronica")
        val exact = java.net.URI("https://ovac.conselldeformentera.cat/")
        val qaItem = qaRepository.portals().single { it.profileId == id }

        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaItem.supportStatus)
        assertTrue(qaItem.isEnabled)
        assertTrue(qaItem.capabilities.isEmpty())
        assertTrue(qaItem.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(id, exact), qaRepository.resolveLaunch(qaItem))
        assertEquals(PortalLaunchTarget(id, exact), qaRepository.resolveLaunch(id, exact))

        val releaseItem = releaseRepository.portals().single { it.profileId == id }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releaseItem.supportStatus)
        assertFalse(releaseItem.isEnabled)
        assertNull(releaseRepository.resolveLaunch(releaseItem))
        assertNull(releaseRepository.resolveLaunch(id, exact))

        listOf(
            "http://ovac.conselldeformentera.cat/",
            "https://user@ovac.conselldeformentera.cat/",
            "https://ovac.conselldeformentera.cat:8443/",
            "https://evil.ovac.conselldeformentera.cat/",
            "https://ovac.conselldeformentera.cat/ovac/",
            "https://ovac.conselldeformentera.cat/ovac/catala/emiservicio/41E6BF9D755E4825AF8E6B49E85B5079.asp",
        ).forEach { rejected ->
            assertNull(rejected, qaRepository.resolveLaunch(id, java.net.URI(rejected)))
        }
    }

    @Test
    fun `supports accent insensitive search and public filters`() {
        val autonomous = qaRepository.portals(
            PortalCatalogQuery(filter = PortalCatalogFilter.AUTONOMOUS_COMMUNITIES),
        )
        assertTrue(autonomous.size >= 31)
        assertTrue(autonomous.all { it.governmentLevel == PortalGovernmentLevel.AUTONOMOUS_COMMUNITY })
        assertTrue(PortalId("junta-andalucia-carne-joven") in autonomous.map { it.portalId })

        val zaragoza = qaRepository.portals(PortalCatalogQuery(searchText = "zaragoza"))
        assertTrue(
            zaragoza.map { it.portalId }.toSet().containsAll(
                setOf(PortalId("unizar-tramitador"), PortalId("diputacion-zaragoza-sede")),
            ),
        )

        val certificateResults = qaRepository.portals(
            PortalCatalogQuery(
                searchText = "carne joven",
                filter = PortalCatalogFilter.CERTIFICATE_ACCESS,
            ),
        )
        assertEquals(
            setOf(
                PortalId("comunidad-madrid-cuenta-digital-carne-joven"),
                PortalId("junta-andalucia-carne-joven"),
            ),
            certificateResults.map { it.portalId }.toSet(),
        )
        assertTrue(certificateResults.all { PortalMechanism.CERTIFICATE_ACCESS in it.observedMechanisms })
    }

    @Test
    fun `favorites and recents are caller supplied and recent order is preserved`() {
        assertEquals(
            setOf(PortalId("age-reg-redsara"), PortalId("aeat-sede")),
            qaRepository.portals(
                PortalCatalogQuery(
                    filter = PortalCatalogFilter.FAVORITES,
                    favoritePortalIds = setOf(PortalId("age-reg-redsara"), PortalId("aeat-sede")),
                ),
            ).map { it.portalId }.toSet(),
        )

        val recentIds = listOf(PortalId("aeat-sede"), PortalId("unizar-tramitador"), PortalId("age-reg-redsara"))
        assertEquals(
            recentIds,
            qaRepository.portals(
                PortalCatalogQuery(
                    filter = PortalCatalogFilter.RECENT,
                    recentPortalIds = recentIds,
                ),
            ).map { it.portalId },
        )
    }

    @Test
    fun `fails closed when registry and catalog do not describe the same profiles`() {
        val emptyCatalog = SiteProfileCatalog(
            schemaVersion = catalog.schemaVersion,
            catalogVersion = catalog.catalogVersion,
            profiles = emptyList(),
        )
        val mismatched = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.RELEASE),
            emptyCatalog,
            publicCatalog,
        )

        assertEquals(publicCatalog.entries.size, mismatched.portals().size)
        assertTrue(mismatched.portals().all { !it.isEnabled })
        assertTrue(mismatched.portals().all { mismatched.resolveLaunch(it) == null })
    }

    @Test
    fun `tampered public binding stays visible but cannot inherit profile trust`() {
        val portalId = PortalId("age-reg-redsara")
        val wrongEntryUrl = java.net.URI("https://reg.redsara.es/")
        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) entry.copy(entryUrl = wrongEntryUrl) else entry
            },
        )
        val repository = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )

        val tampered = repository.portals().single { it.portalId == portalId }
        assertFalse(tampered.isEnabled)
        assertTrue(tampered.capabilities.isEmpty())
        assertTrue(tampered.signatureFormats.isEmpty())
        assertEquals(null, repository.resolveLaunch(tampered))
        assertEquals(null, repository.resolveLaunch(ProfileId("reg-age-redsara"), wrongEntryUrl))

        val unaffected = repository.portals().single { it.portalId == PortalId("junta-andalucia-ovorion") }
        assertTrue(unaffected.isEnabled)
        assertEquals(
            PortalLaunchTarget(checkNotNull(unaffected.profileId), unaffected.entryUrl),
            repository.resolveLaunch(unaffected),
        )
    }

    @Test
    fun `launch resolution accepts only canonical active profile and exact entry URL`() {
        val item = qaRepository.portals().single { it.portalId == PortalId("age-reg-redsara") }

        assertEquals(
            PortalLaunchTarget(checkNotNull(item.profileId), item.entryUrl),
            qaRepository.resolveLaunch(item),
        )
        assertEquals(
            null,
            qaRepository.resolveLaunch(checkNotNull(item.profileId), java.net.URI("https://reg.redsara.es/")),
        )
        assertEquals(
            null,
            qaRepository.resolveLaunch(ProfileId("unknown-profile"), item.entryUrl),
        )
    }

    @Test
    fun `education client auth launch accepts only the exact canonical seed URL in qa`() {
        val id = ProfileId("educacion-convocatoria")
        val exact = java.net.URI(
            "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46",
        )
        val item = qaRepository.portals().single { it.profileId == id }

        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, item.supportStatus)
        assertEquals(setOf(PortalServiceCapability.CERTIFICATE_ACCESS), item.capabilities)
        assertTrue(item.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(id, exact), qaRepository.resolveLaunch(id, exact))
        assertNull(releaseRepository.resolveLaunch(id, exact))

        listOf(
            "http://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46",
            "https://user@sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46",
            "https://sede.educacion.gob.es.evil.example/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46",
            "https://sede.educacion.gob.es/sede/login/loginConv.jjsp",
            "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=yes&idConvocatoria=46",
            "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?idConvocatoria=46&iA=no",
            "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46&idConvocatoria=46",
            "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46&extra=1",
        ).forEach { rejected ->
            assertEquals(rejected, null, qaRepository.resolveLaunch(id, java.net.URI(rejected)))
        }

    }

    @Test
    fun `public support status never treats contract evidence as implementation`() {
        assertEquals(
            PortalSupportStatus.VERIFIED_CONTRACT,
            resolvePortalSupportStatus(CompatibilityStatus.VERIFIED_CONTRACT, isImplemented = false),
        )
        assertEquals(
            PortalSupportStatus.IMPLEMENTED_NOT_E2E,
            resolvePortalSupportStatus(CompatibilityStatus.VERIFIED_CONTRACT, isImplemented = true),
        )
        assertEquals(
            PortalSupportStatus.BROWSE_ONLY,
            resolvePortalSupportStatus(CompatibilityStatus.BROWSE_ONLY, isImplemented = true),
        )
        assertEquals(
            PortalSupportStatus.UNSUPPORTED_PROTOCOL,
            resolvePortalSupportStatus(CompatibilityStatus.UNSUPPORTED, isImplemented = false),
        )
    }

    @Test
    fun `BNE alias keeps BNE metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-biblioteca-nacional-de-espana")
        val profileId = ProfileId("reg-age-redsara")
        val bneEntry = java.net.URI("https://sede.bne.gob.es/es/tramites/quejas-sugerencias")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(bneEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, bneEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Mallorca institutional alias keeps portal metadata and resolves only exact QA Registre launch`() {
        val portalId = PortalId("mallorca-portal-institucional")
        val profileId = ProfileId("consell-mallorca-sede")
        val institutionalEntry = java.net.URI("https://www.conselldemallorca.es/")
        val registreStart = java.net.URI(
            "https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082",
        )

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(institutionalEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, registreStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, registreStart),
            qaRepository.resolveLaunch(profileId, institutionalEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(
                        launchUrl = java.net.URI(
                            "https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12083",
                        ),
                    )
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Tenerife institutional alias keeps portal metadata and inherits only exact QA Sede launch`() {
        val portalId = PortalId("tenerife-portal-institucional")
        val profileId = ProfileId("tenerife-sede-electronica")
        val institutionalEntry = java.net.URI("https://www.tenerife.es/")
        val sedeStart = java.net.URI("https://sede.tenerife.es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(institutionalEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, sedeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, sedeStart),
            qaRepository.resolveLaunch(profileId, institutionalEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://sede.tenerife.es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `DSCA REG alias keeps ministry metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-derechos-sociales-consumo-y-agenda-2030")
        val profileId = ProfileId("reg-age-redsara")
        val ministryEntry = java.net.URI(
            "https://www.dsca.gob.es/es/derechos-sociales/derechos-animales/premios/artisticos/v-certamen-clipmetraje",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(ministryEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, ministryEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Igualdad alias keeps institutional metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-igualdad")
        val profileId = ProfileId("reg-age-redsara")
        val igualdadEntry = java.net.URI("https://igualdad.sede.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(igualdadEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, igualdadEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Reina Sofia REG alias keeps institutional metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-museo-nacional-centro-de-arte-reina-sofia")
        val profileId = ProfileId("reg-age-redsara")
        val museoEntry = java.net.URI("https://museoreinasofia.sede.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(museoEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, museoEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `INAP alias keeps INAP metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-instituto-nacional-de-administracion-publica-inap")
        val profileId = ProfileId("reg-age-redsara")
        val inapEntry = java.net.URI("https://sede.inap.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(inapEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, inapEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Cantabria Sede alias launches only the exact implemented REC profile in QA`() {
        val portalId = PortalId("cantabria-sede")
        val profileId = ProfileId("cantabria-rec-cert-login")
        val entryUrl = java.net.URI("https://sede.cantabria.es/sede/")
        val launchUrl = java.net.URI("https://rec.cantabria.es/rec/bienvenida.htm")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(entryUrl, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, launchUrl), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            publicCatalog.copy(
                entries = publicCatalog.entries.map { entry ->
                    if (entry.portalId == portalId) {
                        entry.copy(launchUrl = java.net.URI("https://rec.cantabria.es/rec/not-the-profile-start"))
                    } else {
                        entry
                    }
                },
            ),
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `La Palma institutional alias launches only the exact implemented Sede profile in QA`() {
        val portalId = PortalId("la-palma-portal-institucional")
        val profileId = ProfileId("la-palma-sede-electronica")
        val entryUrl = java.net.URI("https://www.cabildodelapalma.es/")
        val launchUrl = java.net.URI("https://sedeelectronica.cabildodelapalma.es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(entryUrl, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, launchUrl), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            publicCatalog.copy(
                entries = publicCatalog.entries.map { entry ->
                    if (entry.portalId == portalId) {
                        entry.copy(launchUrl = java.net.URI("https://sedeelectronica.cabildodelapalma.es/not-the-profile-start"))
                    } else {
                        entry
                    }
                },
            ),
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Cervantes REG alias keeps institutional metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-instituto-cervantes")
        val profileId = ProfileId("reg-age-redsara")
        val cervantesEntry = java.net.URI("https://cervantes.sede.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(cervantesEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, cervantesEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `MITECO REG alias keeps ministry metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-para-la-transicion-ecologica-y-el-reto-demografico")
        val profileId = ProfileId("reg-age-redsara")
        val ministryEntry = java.net.URI(
            "https://www.miteco.gob.es/es/costas/participacion-publica/30-cnc12-07-30-0006.html",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(ministryEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, ministryEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `AEMPS alias keeps AEMPS metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-agencia-espanola-de-medicamentos-y-productos-sanitarios-aemps")
        val profileId = ProfileId("reg-age-redsara")
        val aempsEntry = java.net.URI("https://sede.aemps.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(aempsEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, aempsEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Puertos alias keeps Puertos metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-puertos-del-estado")
        val profileId = ProfileId("reg-age-redsara")
        val puertosEntry = java.net.URI("https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(puertosEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, puertosEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Exteriores REG alias keeps ministry metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-asuntos-exteriores-union-europea-y-cooperacion")
        val profileId = ProfileId("reg-age-redsara")
        val exterioresEntry = java.net.URI(
            "https://www.exteriores.gob.es/Consulados/monterrey/es/ServiciosConsulares/Paginas/index.aspx?scca=Inscripci%C3%B3n+Consular&scco=M%C3%A9xico&scd=198&scs=Baja+del+Registro+de+Matr%C3%ADcula",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(exterioresEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, exterioresEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `MIVAU alias keeps ministry metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-vivienda-y-agenda-urbana")
        val profileId = ProfileId("reg-age-redsara")
        val ministryEntry = java.net.URI(
            "https://mivau.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(ministryEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, ministryEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `MAPA alias keeps MAPA metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-agricultura-pesca-y-alimentacion")
        val profileId = ProfileId("reg-age-redsara")
        val mapaEntry = java.net.URI("https://sede.mapa.gob.es/portal/site/seMAPA")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(mapaEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, mapaEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Cultura REG AGE alias keeps Ministry metadata and inherits only the exact QA launch`() {
        val portalId = PortalId("age-ministerio-de-cultura")
        val profileId = ProfileId("reg-age-redsara")
        val ministryEntry = java.net.URI("https://cultura.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(ministryEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, ministryEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `Juventud e Infancia REG alias keeps ministry metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-juventud-e-infancia")
        val profileId = ProfileId("reg-age-redsara")
        val juventudEntry = java.net.URI("https://juventudeinfancia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(juventudEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, juventudEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Defensa alias keeps Ministry metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-defensa")
        val profileId = ProfileId("reg-age-redsara")
        val defensaEntry = java.net.URI("https://sede.defensa.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(defensaEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, defensaEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/en/"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `MPR REG alias keeps ministry metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-la-presidencia-justicia-y-relaciones-con-las-cortes")
        val profileId = ProfileId("reg-age-redsara")
        val mprEntry = java.net.URI("https://mpr.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(mprEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, mprEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Inclusion alias keeps institutional metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-inclusion-seguridad-social-y-migraciones")
        val profileId = ProfileId("reg-age-redsara")
        val inclusionEntry = java.net.URI("https://sede.inclusion.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(inclusionEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, inclusionEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start")) else entry
            },
        )
        val tampered = PortalCatalogRepository(SiteProfileRegistry(catalog, BuildTrustPolicy.QA), catalog, tamperedCatalog)
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `MPTMD REG alias keeps ministry metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-politica-territorial-y-memoria-democratica")
        val profileId = ProfileId("reg-age-redsara")
        val mptmdEntry = java.net.URI("https://mptmd.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(mptmdEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, mptmdEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Industria alias keeps institutional metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-de-industria-y-turismo")
        val profileId = ProfileId("reg-age-redsara")
        val industriaEntry = java.net.URI("https://sede.minetur.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(industriaEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, industriaEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start")) else entry
            },
        )
        val tampered = PortalCatalogRepository(SiteProfileRegistry(catalog, BuildTrustPolicy.QA), catalog, tamperedCatalog)
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Interior REG alias keeps ministry metadata and inherits only the exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-del-interior")
        val profileId = ProfileId("reg-age-redsara")
        val interiorEntry = java.net.URI("https://sede.interior.gob.es/portal/sede/tramites?codAgrupacion=GENERAL")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(interiorEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, interiorEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Transformacion Digital alias keeps ministry metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-ministerio-para-la-transformacion-digital-y-de-la-funcion-publica")
        val profileId = ProfileId("reg-age-redsara")
        val ministryEntry = java.net.URI("https://digital.sede.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(ministryEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, ministryEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `OEPM ProtegeO profile opens only exact catalog start in QA and remains fail closed in release`() {
        val portalId = PortalId("age-oficina-espanola-de-patentes-y-marcas")
        val profileId = ProfileId("oepm-protegeo-general")
        val start = java.net.URI(
            "https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM",
        )
        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(start, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, start), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(
                        entryUrl = java.net.URI(
                            "https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=OTRO",
                        ),
                    )
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Castilla Leon QUJU opens only exact public form in QA and remains fail closed`() {
        val portalId = PortalId("castilla-leon-tramita")
        val profileId = ProfileId("castilla-leon-quju-public")
        val publicForm = java.net.URI("https://presidencia.jcyl.es/QUJU?O=1")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(publicForm, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, publicForm), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, publicForm), qaRepository.resolveLaunch(profileId, publicForm))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(entryUrl = java.net.URI("https://presidencia.jcyl.es/QUJU?O=2"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `SEPES opens only its exact Transportes public page while the shared signer remains isolated`() {
        val portalId = PortalId("age-entidad-publica-empresarial-de-suelo-sepes")
        val profileId = ProfileId("sepes-transportes-public-complaints")
        val publicPage = java.net.URI("https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(publicPage, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, publicPage), qaRepository.resolveLaunch(qaPortal))

        val signingStart = java.net.URI("https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002")
        assertEquals(
            ProfileId("transportes-qys-cert-login"),
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA).resolve(signingStart)?.profile?.profileId,
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(entryUrl = java.net.URI("https://sede.transportes.gob.es/Procedimiento/?procedureKey=7601"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertNull(tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `BOE public Sede opens only exact information page in QA while extranet remains fail closed`() {
        val portalId = PortalId("age-agencia-estatal-del-boletin-oficial-del-estado-boe")
        val profileId = ProfileId("boe-sede-public-home")
        val publicHome = java.net.URI("https://www.boe.es/informacion/index.php")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(publicHome, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, publicHome), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, publicHome), qaRepository.resolveLaunch(profileId, publicHome))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) entry.copy(entryUrl = java.net.URI("https://extranet.boe.es/quejas_el/")) else entry
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertNull(tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Portal Funciona opens only exact public home in QA while authentication remains fail closed`() {
        val portalId = PortalId("age-portal-funciona")
        val profileId = ProfileId("portal-funciona-public-home")
        val publicHome = java.net.URI("https://sede.funciona.gob.es/es/home")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(publicHome, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, publicHome), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, publicHome), qaRepository.resolveLaunch(profileId, publicHome))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(entryUrl = java.net.URI("https://auth-api.redsara.es/auth/realms/sgad-appfactory/"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Fondos Europeos opens only exact public Sede in QA while external services remain fail closed`() {
        val portalId = PortalId("age-direccion-general-de-fondos-europeos")
        val profileId = ProfileId("fondos-europeos-sede-public-home")
        val publicHome = java.net.URI("https://sedefondoscomunitarios.gob.es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(publicHome, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, publicHome), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(entries = publicCatalog.entries.map { entry ->
            if (entry.portalId == portalId) entry.copy(entryUrl = java.net.URI("https://tramitesfondoseuropeos.hacienda.gob.es/dossier")) else entry
        })
        val tampered = PortalCatalogRepository(SiteProfileRegistry(catalog, BuildTrustPolicy.QA), catalog, tamperedCatalog)
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `DGSFP opens only exact public Sede in QA while sensitive capabilities remain fail closed`() {
        val portalId = PortalId("age-direccion-general-de-seguros-y-fondos-de-pensiones")
        val profileId = ProfileId("dgsfp-sede-public-home")
        val publicHome = java.net.URI("https://www.sededgsfp.gob.es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(publicHome, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, publicHome), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, publicHome), qaRepository.resolveLaunch(profileId, publicHome))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(entries = publicCatalog.entries.map { entry ->
            if (entry.portalId == portalId) entry.copy(entryUrl = java.net.URI("https://www.sededgsfp.gob.es.evil.example/")) else entry
        })
        val tampered = PortalCatalogRepository(SiteProfileRegistry(catalog, BuildTrustPolicy.QA), catalog, tamperedCatalog)
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Comercio alias keeps exact procedure metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-secretaria-de-estado-de-comercio")
        val profileId = ProfileId("reg-age-redsara")
        val procedure = java.net.URI(
            "https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio/detalle-procedimiento?val=3057517",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(procedure, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, procedure))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Digital Sede REG alias keeps migrated SEDIA SETID metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-sede-electronica-de-la-s-e-de-digitalizacion-e-inteligencia-artificial-y-s-e-de-telecomunica")
        val profileId = ProfileId("reg-age-redsara")
        val historicalEntry = java.net.URI("https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(historicalEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, historicalEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Hacienda central alias keeps Hacienda metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-sede-electronica-central-del-ministerio")
        val profileId = ProfileId("reg-age-redsara")
        val haciendaEntry = java.net.URI("https://sede.hacienda.gob.es/")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(haciendaEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, haciendaEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Tesoro alias keeps exact procedure metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-tesoro-publico")
        val profileId = ProfileId("reg-age-redsara")
        val procedure = java.net.URI(
            "https://www.tesoropublico.gob.es/es/servicios/adhesion-al-codigo-de-buenas-practicas-para-deudores-hipotecarios-en-riesgo-de",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(procedure, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, procedure))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `SEPE REG alias keeps SEPE metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("sepe-sede")
        val profileId = ProfileId("reg-age-redsara")
        val sepeEntry = java.net.URI("https://sede.sepe.gob.es/portalSede/registro-electronico.html")
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(sepeEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(profileId, sepeEntry))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Junta VEA PEG keeps only exact QA public navigation and no signing capability`() {
        val portalId = PortalId("junta-andalucia-sede")
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val start = java.net.URI("https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(start, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(profileId, start), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(entryUrl = java.net.URI("https://api-veaja.cloud.juntadeandalucia.es/auth/login"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `UNED alias keeps UNED metadata and inherits only exact QA REG AGE launch`() {
        val portalId = PortalId("age-universidad-nacional-de-educacion-a-distancia-uned")
        val profileId = ProfileId("reg-age-redsara")
        val unedEntry = java.net.URI(
            "https://uned.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General",
        )
        val regAgeStart = java.net.URI("https://reg.redsara.es/es/")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(unedEntry, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, regAgeStart), qaRepository.resolveLaunch(qaPortal))
        assertEquals(
            PortalLaunchTarget(profileId, regAgeStart),
            qaRepository.resolveLaunch(profileId, unedEntry),
        )

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(launchUrl = java.net.URI("https://reg.redsara.es/es/not-the-profile-start"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Murcia CARM opens only exact QA procedure and stays fail closed in release`() {
        val portalId = PortalId("murcia-sede")
        val profileId = ProfileId("murcia-carm-pase")
        val start = java.net.URI(
            "https://sede.carm.es/web/pagina?IDCONTENIDO=385&IDTIPO=240&RASTRO=c%24m40293%2C62654%2C40288",
        )

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(start, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(profileId, start), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(entryUrl = java.net.URI("https://pase.carm.es/pase/login"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `MJusticia exact fundaciones launch is QA only`() {
        val portalId = PortalId("mjusticia-sede")
        val profileId = ProfileId("mjusticia-fundaciones-idp75")
        val institutionalPage = java.net.URI("https://sede.mjusticia.gob.es/tramites/organos-gobierno")
        val launch = java.net.URI("https://sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(launch, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, launch), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, launch), qaRepository.resolveLaunch(profileId, launch))
        assertEquals(null, qaRepository.resolveLaunch(profileId, institutionalPage))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(entryUrl = java.net.URI("https://sede2.mjusticia.gob.es/login/index/idp/75"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Asturias Sede procedure keeps only exact QA redirect navigation and no signing capability`() {
        val portalId = PortalId("asturias-sede-tramite-autofirma")
        val profileId = ProfileId("asturias-sede-tramite-navigation")
        val start = java.net.URI("https://sede.asturias.es/ast/-/dboid-6269000011903512107573")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(start, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(profileId, start), qaRepository.resolveLaunch(qaPortal))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))
    }

    @Test
    fun `Gipuzkoa Registro opens exact QA public start while Izenpe client auth remains unavailable`() {
        val portalId = PortalId("diputacion-gipuzkoa-sede")
        val profileId = ProfileId("diputacion-gipuzkoa-registro-public")
        val start = java.net.URI(
            "https://egoitza.gipuzkoa.eus/WAS/CORP/WATTramiteakWEB/inicio.do?idioma=C&app=00001",
        )

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(start, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, start), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, start), qaRepository.resolveLaunch(profileId, start))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(entryUrl = java.net.URI("https://eidas2.izenpe.com/cert-authn-external-validation/authenticate"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

    @Test
    fun `Madrid Cuenta Digital 53F1 opens exact QA entry while auth and signing stay fail closed`() {
        val portalId = PortalId("comunidad-madrid-cuenta-digital-carne-joven")
        val profileId = ProfileId("comunidad-madrid-cuenta-digital-53f1")
        val start = java.net.URI("https://digital.comunidad.madrid/ext/53F1")

        val qaPortal = qaRepository.portals().single { it.portalId == portalId }
        assertEquals(profileId, qaPortal.profileId)
        assertEquals(start, qaPortal.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(profileId, start), qaRepository.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, start), qaRepository.resolveLaunch(profileId, start))

        val releasePortal = releaseRepository.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, releaseRepository.resolveLaunch(releasePortal))

        val tamperedCatalog = publicCatalog.copy(
            entries = publicCatalog.entries.map { entry ->
                if (entry.portalId == portalId) {
                    entry.copy(entryUrl = java.net.URI("https://gestiona2.comunidad.madrid/auto_certificado/SelCertificado"))
                } else {
                    entry
                }
            },
        )
        val tampered = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            catalog,
            tamperedCatalog,
        )
        val tamperedPortal = tampered.portals().single { it.portalId == portalId }
        assertFalse(tamperedPortal.isEnabled)
        assertTrue(tamperedPortal.capabilities.isEmpty())
        assertTrue(tamperedPortal.signatureFormats.isEmpty())
        assertEquals(null, tampered.resolveLaunch(tamperedPortal))
    }

}
