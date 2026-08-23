package dev.junta.firmamobile.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PortalCatalogScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val catalog = BuiltInSiteProfiles.catalog
    private val repository by lazy {
        PortalCatalogRepository(
            registry = SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
            profileCatalog = catalog,
            publicCatalog = loadBundledPublicPortalCatalog(),
        )
    }

    @Test
    fun `implemented profiles are shown in compatible section`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }

        assertEquals(
            repository.portals().count {
                it.supportStatus == PortalSupportStatus.VERIFIED_E2E ||
                    it.supportStatus == PortalSupportStatus.IMPLEMENTED_NOT_E2E
            },
            compatible.items.size,
        )
        val verifiedIds = setOf("carne-joven-andalucia", "aragon-siraw", "junta-ofvirtual", "unizar-tramitador")
        assertTrue(
            compatible.items.filter { it.profileId?.value in verifiedIds }
                .all { it.supportStatus == PortalSupportStatus.VERIFIED_E2E },
        )
        assertTrue(
            compatible.items.filterNot { it.profileId?.value in verifiedIds }
                .all { it.supportStatus == PortalSupportStatus.IMPLEMENTED_NOT_E2E },
        )
        val contractPending = sections.single { it.kind == PortalCatalogSectionKind.CONTRACT_PENDING }
        assertEquals(
            setOf(PortalId("age-acceda")),
            contractPending.items.map { it.portalId }.toSet(),
        )
        val fullCatalog = sections.single { it.kind == PortalCatalogSectionKind.FULL_CATALOG }
        assertEquals(
            repository.portals().size - compatible.items.size - contractPending.items.size,
            fullCatalog.items.size,
        )
        val education = fullCatalog.items.single { it.portalId == PortalId("educacion-convocatoria-46") }
        assertEquals(PortalSupportStatus.BROWSE_ONLY, education.supportStatus)
        assertTrue(education.isEnabled)
    }

    @Test
    fun `Cervantes REG alias is listed as compatible but remains pending E2E`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val cervantes = compatible.items.single {
            it.portalId == PortalId("age-instituto-cervantes")
        }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"), cervantes.profileId)
        assertEquals(java.net.URI("https://cervantes.sede.gob.es/"), cervantes.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, cervantes.supportStatus)
        assertTrue(cervantes.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"),
                java.net.URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(cervantes),
        )
    }

    @Test
    fun `Reina Sofia REG alias is compatible but remains pending E2E`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val reina = compatible.items.single {
            it.portalId == PortalId("age-museo-nacional-centro-de-arte-reina-sofia")
        }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"), reina.profileId)
        assertEquals(java.net.URI("https://museoreinasofia.sede.gob.es/"), reina.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, reina.supportStatus)
        assertTrue(reina.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"),
                java.net.URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(reina),
        )
    }

    @Test
    fun `contract pending browse only and unsupported are separated and disabled correctly`() {
        val base = repository.portals().first()
        val pending = base.copy(
            portalId = PortalId("pending"),
            profileId = dev.junta.firmamobile.profile.ProfileId("pending"),
            supportStatus = PortalSupportStatus.VERIFIED_CONTRACT,
            isEnabled = false,
        )
        val browse = base.copy(
            portalId = PortalId("browse"),
            profileId = dev.junta.firmamobile.profile.ProfileId("browse"),
            supportStatus = PortalSupportStatus.BROWSE_ONLY,
        )
        val unsupported = base.copy(
            portalId = PortalId("unsupported"),
            profileId = dev.junta.firmamobile.profile.ProfileId("unsupported"),
            supportStatus = PortalSupportStatus.UNSUPPORTED_PROTOCOL,
            isEnabled = false,
        )
        val sections = buildPortalCatalogSections(listOf(pending, browse, unsupported))

        assertEquals(listOf(pending), sections.single { it.kind == PortalCatalogSectionKind.CONTRACT_PENDING }.items)
        assertEquals(
            listOf(browse, unsupported),
            sections.single { it.kind == PortalCatalogSectionKind.FULL_CATALOG }.items,
        )
    }

    @Test
    fun `section model keeps every filtered repository item exactly once`() {
        val portals = repository.portals(
            PortalCatalogQuery(filter = PortalCatalogFilter.ELECTRONIC_SIGNATURE),
        )
        val sectionItems = buildPortalCatalogSections(portals).flatMap { it.items }

        assertEquals(portals.map { it.portalId }.toSet(), sectionItems.map { it.portalId }.toSet())
        assertEquals(portals.size, sectionItems.size)
    }

    @Test
    fun `search narrows the native catalog and open invokes the selected profile`() {
        var opened: PortalCatalogItem? = null
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    repository = repository,
                    onOpenPortal = { opened = it },
                )
            }
        }

        rule.onNodeWithText("Buscar servicio u organismo").performTextInput("zaragoza")
        rule.onNodeWithText("Universidad de Zaragoza — Oficina Virtual")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Abrir sede").performScrollTo().performClick()

        rule.runOnIdle {
            assertEquals("unizar-tramitador", opened?.profileId?.value)
        }
    }

    @Test
    fun `UNED REG alias is shown as pending compatible and opens exact existing profile`() {
        var opened: PortalCatalogItem? = null
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    repository = repository,
                    onOpenPortal = { opened = it },
                )
            }
        }

        rule.onNodeWithText("Buscar servicio u organismo").performTextInput("UNED")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasText("Registro Electrónico General (REG-AGE)"),
        )
        rule.onNodeWithText("Registro Electrónico General (REG-AGE)")
            .assertIsDisplayed()
        rule.onNodeWithText("IMPLEMENTADO · E2E PENDIENTE")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Abrir sede").performScrollTo().performClick()

        rule.runOnIdle {
            assertEquals("reg-age-redsara", opened?.profileId?.value)
            assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, opened?.supportStatus)
        }
    }

    @Test
    fun `verified UniZAR card states portal validation and verified signature`() {
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    repository = repository,
                    onOpenPortal = {},
                )
            }
        }

        rule.onNodeWithText("Buscar servicio u organismo")
            .performTextInput("zaragoza")
        rule.onNodeWithText("Universidad de Zaragoza — Oficina Virtual")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("VALIDADO CON EL PORTAL")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Verificado: Firma electrónica")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `verified Oficina Virtual card states portal validation and verified signature`() {
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    repository = repository,
                    onOpenPortal = {},
                )
            }
        }

        rule.onNodeWithText("Buscar servicio u organismo")
            .performTextInput("Junta de Andalucía")
        rule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Junta de Andalucía — Oficina Virtual"))
        rule.onNodeWithText("Junta de Andalucía — Oficina Virtual")
            .assertIsDisplayed()
        rule.onNodeWithText("VALIDADO CON EL PORTAL")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Verificado: Firma electrónica")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `Igualdad REG alias is listed as compatible but remains pending E2E`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val igualdad = compatible.items.single { it.portalId == PortalId("age-ministerio-de-igualdad") }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"), igualdad.profileId)
        assertEquals(java.net.URI("https://igualdad.sede.gob.es/"), igualdad.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, igualdad.supportStatus)
        assertTrue(igualdad.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"),
                java.net.URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(igualdad),
        )
    }

    @Test
    fun `metadata only browse record explains why integrated navigation is blocked`() {
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    repository = repository,
                    onOpenPortal = {},
                )
            }
        }

        rule.onNodeWithText("Buscar servicio u organismo")
            .performTextInput("SEPE")
        rule.onNodeWithText(
            "Sede pública catalogada; la navegación integrada, el certificado y la firma " +
                "están bloqueados hasta verificar un perfil técnico.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `catalog remains usable at narrow width and large font scale`() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                JuntaFirmaTheme {
                    PortalCatalogScreen(
                        repository = repository,
                        onOpenPortal = {},
                    )
                }
            }
        }

        rule.onNodeWithText("Buscar servicio u organismo")
            .performTextInput("carne joven europeo")
        rule.onNodeWithText("Carné Joven Europeo de Andalucía")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("VALIDADO CON EL PORTAL").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Favorito").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Abrir sede").assertIsDisplayed()
    }
    @Test
    fun `Inclusion REG alias is listed as compatible but remains pending E2E`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val inclusion = compatible.items.single {
            it.portalId == PortalId("age-ministerio-de-inclusion-seguridad-social-y-migraciones")
        }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"), inclusion.profileId)
        assertEquals(java.net.URI("https://sede.inclusion.gob.es/"), inclusion.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, inclusion.supportStatus)
        assertTrue(inclusion.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"),
                java.net.URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(inclusion),
        )
    }

    @Test
    fun `Industria REG alias is listed as compatible but remains pending E2E`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val industria = compatible.items.single {
            it.portalId == PortalId("age-ministerio-de-industria-y-turismo")
        }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"), industria.profileId)
        assertEquals(java.net.URI("https://sede.minetur.gob.es/"), industria.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, industria.supportStatus)
        assertTrue(industria.isEnabled)
        assertEquals(
            PortalLaunchTarget(
                dev.junta.firmamobile.profile.ProfileId("reg-age-redsara"),
                java.net.URI("https://reg.redsara.es/es/"),
            ),
            repository.resolveLaunch(industria),
        )
    }

    @Test
    fun `OEPM ProtegeO public launch is compatible while sensitive capabilities remain absent`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val oepm = compatible.items.single {
            it.portalId == PortalId("age-oficina-espanola-de-patentes-y-marcas")
        }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("oepm-protegeo-general"), oepm.profileId)
        assertEquals(
            java.net.URI("https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM"),
            oepm.entryUrl,
        )
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, oepm.supportStatus)
        assertTrue(oepm.capabilities.isEmpty())
        assertTrue(oepm.signatureFormats.isEmpty())
        assertTrue(oepm.isEnabled)
    }

    @Test
    fun `Castilla Leon QUJU public form is compatible while signing capabilities remain absent`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val quju = compatible.items.single { it.portalId == PortalId("castilla-leon-tramita") }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("castilla-leon-quju-public"), quju.profileId)
        assertEquals(java.net.URI("https://presidencia.jcyl.es/QUJU?O=1"), quju.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, quju.supportStatus)
        assertTrue(quju.capabilities.isEmpty())
        assertTrue(quju.signatureFormats.isEmpty())
        assertTrue(quju.isEnabled)
    }

    @Test
    fun `Ceuta ANI authenticated form boundary is compatible while signing remains absent`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val ceuta = compatible.items.single { it.portalId == PortalId("ceuta-sede") }
        assertEquals(dev.junta.firmamobile.profile.ProfileId("ceuta-sede"), ceuta.profileId)
        assertEquals(java.net.URI("https://sede.ceuta.es/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI"), ceuta.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, ceuta.supportStatus)
        assertTrue(ceuta.capabilities.isEmpty())
        assertTrue(ceuta.signatureFormats.isEmpty())
        assertTrue(ceuta.isEnabled)
    }

    @Test
    fun `BOE public Sede is compatible navigation with no sensitive capabilities`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val boe = compatible.items.single {
            it.portalId == PortalId("age-agencia-estatal-del-boletin-oficial-del-estado-boe")
        }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("boe-sede-public-home"), boe.profileId)
        assertEquals(java.net.URI("https://www.boe.es/informacion/index.php"), boe.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, boe.supportStatus)
        assertTrue(boe.capabilities.isEmpty())
        assertTrue(boe.signatureFormats.isEmpty())
        assertTrue(boe.isEnabled)
    }

    @Test
    fun `Portal Funciona public home is compatible but sensitive auth capabilities remain absent`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val funciona = compatible.items.single { it.portalId == PortalId("age-portal-funciona") }

        assertEquals(dev.junta.firmamobile.profile.ProfileId("portal-funciona-public-home"), funciona.profileId)
        assertEquals(java.net.URI("https://sede.funciona.gob.es/es/home"), funciona.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, funciona.supportStatus)
        assertTrue(funciona.capabilities.isEmpty())
        assertTrue(funciona.signatureFormats.isEmpty())
        assertTrue(funciona.isEnabled)
    }

    @Test
    fun `Hacienda central REG alias is compatible but remains pending E2E`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val hacienda = compatible.items.single {
            it.portalId == PortalId("age-sede-electronica-central-del-ministerio")
        }

        assertEquals("reg-age-redsara", hacienda.profileId?.value)
        assertEquals(java.net.URI("https://sede.hacienda.gob.es/"), hacienda.entryUrl)
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, hacienda.supportStatus)
        assertTrue(hacienda.isEnabled)
    }

    @Test
    fun `Junta VEA PEG is compatible navigation but remains pending E2E`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }
        val target = compatible.items.single { it.portalId == PortalId("junta-andalucia-sede") }

        assertEquals("junta-andalucia-vea-peg", target.profileId?.value)
        assertEquals(
            java.net.URI("https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA"),
            target.entryUrl,
        )
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, target.supportStatus)
        assertTrue(target.isEnabled)
        assertTrue(target.capabilities.isEmpty())
    }

}
