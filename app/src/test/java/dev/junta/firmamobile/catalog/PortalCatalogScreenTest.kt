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

        assertEquals(12, compatible.items.size)
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
            .performTextInput("Punto de Acceso General")
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
}
