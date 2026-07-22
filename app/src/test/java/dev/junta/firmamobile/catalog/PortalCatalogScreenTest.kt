package dev.junta.firmamobile.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
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
class PortalCatalogScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val catalog = BuiltInSiteProfiles.catalog
    private val repository = PortalCatalogRepository(
        registry = SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
        profileCatalog = catalog,
    )

    @Test
    fun `implemented profiles are shown in compatible section`() {
        val sections = buildPortalCatalogSections(repository.portals())
        val compatible = sections.single { it.kind == PortalCatalogSectionKind.COMPATIBLE }

        assertEquals(5, compatible.items.size)
        val verifiedIds = setOf("junta-andalucia", "carne-joven-andalucia")
        assertTrue(
            compatible.items.filter { it.profileId.value in verifiedIds }
                .all { it.supportStatus == PortalSupportStatus.VERIFIED_E2E },
        )
        assertTrue(
            compatible.items.filterNot { it.profileId.value in verifiedIds }
                .all { it.supportStatus == PortalSupportStatus.IMPLEMENTED_NOT_E2E },
        )
        assertFalse(sections.any { it.kind == PortalCatalogSectionKind.CONTRACT_PENDING })
        val fullCatalog = sections.single { it.kind == PortalCatalogSectionKind.FULL_CATALOG }
        assertEquals(listOf("educacion-convocatoria"), fullCatalog.items.map { it.profileId.value })
        assertEquals(PortalSupportStatus.BROWSE_ONLY, fullCatalog.items.single().supportStatus)
    }

    @Test
    fun `contract pending browse only and unsupported are separated and disabled correctly`() {
        val base = repository.portals().first()
        val pending = base.copy(
            profileId = dev.junta.firmamobile.profile.ProfileId("pending"),
            supportStatus = PortalSupportStatus.VERIFIED_CONTRACT,
            isEnabled = false,
        )
        val browse = base.copy(
            profileId = dev.junta.firmamobile.profile.ProfileId("browse"),
            supportStatus = PortalSupportStatus.BROWSE_ONLY,
        )
        val unsupported = base.copy(
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

        assertEquals(portals.map { it.profileId }.toSet(), sectionItems.map { it.profileId }.toSet())
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
}
