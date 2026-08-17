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
                "reg-age-redsara",
                "unizar-tramitador",
                "carne-joven-andalucia",
                "junta-ofvirtual",
                "educacion-convocatoria",
                "aragon-siraw",
                "aeat-mis-datos-censales",
                "dgt-verificacion-equipo",
                "ugr-certificado-login",
                "cantabria-rec-cert-login",
                "jccm-certificate-login-probe",
                "sevilla-atse-certificate-login",
                "melilla-sede",
                "ceuta-sede",
                "extremadura-tramites",
                "diputacion-valladolid-sede",
                "diputacion-burgos-portal",
                "la-palma-sede-electronica",
                "diputacion-huesca-portal",
                "diputacion-lugo-sede",
                "diputacion-leon-sede",
                "ministerio-sanidad-certificado",
                "tea-alegaciones-certificado",
                "tenerife-sede-electronica",
                "gran-canaria-sede-electronica",
                "diputacion-toledo-sede",
                "isciii-certificate-selection",
                "diputacion-valencia-sede",
                "policia-solicitud-generica",
                "diputacion-lleida-sede",
            ),
            qaPortals.mapNotNull { it.profileId?.value }.toSet(),
        )
        val metadataOnly = qaPortals.filter { it.profileId == null }
        assertEquals(qaPortals.size - 34, metadataOnly.size)
        assertTrue(metadataOnly.all { !it.isEnabled })
        assertTrue(metadataOnly.all { it.capabilities.isEmpty() && it.signatureFormats.isEmpty() })
        assertTrue(metadataOnly.all { qaRepository.resolveLaunch(it) == null })
        val metadataBrowseOnly = metadataOnly.filter {
            it.inventoryStatus == PortalInventoryStatus.BROWSE_ONLY
        }
        assertTrue(metadataBrowseOnly.isNotEmpty())
        assertTrue(metadataBrowseOnly.all { it.supportStatus in setOf(PortalSupportStatus.DISCOVERED, PortalSupportStatus.CATALOGED) })

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
            val expectedStatus = if (portal.profileId in setOf(ProfileId("educacion-convocatoria"), ProfileId("ceuta-sede"))) {
                PortalSupportStatus.BROWSE_ONLY
            } else {
                PortalSupportStatus.IMPLEMENTED_NOT_E2E
            }
            assertEquals(expectedStatus, portal.supportStatus)
            assertTrue(portal.isEnabled)
        }

        verifiedIds.forEach { profileId ->
            val releasePortal = releasePortals.single { it.profileId == profileId }
            assertEquals(PortalSupportStatus.VERIFIED_E2E, releasePortal.supportStatus)
            assertTrue(releasePortal.isEnabled)
        }
        releasePortals.filter { it.profileId != null && it.profileId !in verifiedIds }.forEach { portal ->
            when (portal.profileId) {
                ProfileId("educacion-convocatoria"), ProfileId("ceuta-sede") -> {
                    assertEquals(PortalSupportStatus.BROWSE_ONLY, portal.supportStatus)
                    assertTrue(portal.isEnabled)
                }
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
    fun `education browse-only launch accepts only the exact canonical seed URL`() {
        val id = ProfileId("educacion-convocatoria")
        val exact = java.net.URI(
            "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46",
        )
        val item = releaseRepository.portals().single { it.profileId == id }

        assertEquals(PortalSupportStatus.BROWSE_ONLY, item.supportStatus)
        assertTrue(item.capabilities.isEmpty())
        assertTrue(item.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(id, exact), releaseRepository.resolveLaunch(id, exact))

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
            assertEquals(rejected, null, releaseRepository.resolveLaunch(id, java.net.URI(rejected)))
            assertEquals(rejected, null, BuiltInSiteProfiles.releaseRegistry.resolve(java.net.URI(rejected)))
        }

        assertEquals(null, BuiltInSiteProfiles.releaseRegistry.resolve(java.net.URI("https://www.educacion.gob.es/")))
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
}
