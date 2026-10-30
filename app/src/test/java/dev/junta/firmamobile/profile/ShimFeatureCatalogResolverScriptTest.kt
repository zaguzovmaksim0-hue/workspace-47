package dev.junta.firmamobile.profile

import dev.junta.firmamobile.browser.AfirmaJavascriptShimAssembler
import dev.junta.firmamobile.browser.AfirmaJavascriptShimConfiguration
import dev.junta.firmamobile.browser.AfirmaShimCompatibilityFlags
import dev.junta.firmamobile.browser.BurgosBatchBridgeAdapter
import dev.junta.firmamobile.browser.CaibBatchBridgeAdapter
import dev.junta.firmamobile.browser.ExtremaduraBatchBridgeAdapter
import dev.junta.firmamobile.browser.HuescaBatchBridgeAdapter
import dev.junta.firmamobile.browser.LaPalmaBatchBridgeAdapter
import dev.junta.firmamobile.browser.LugoBatchBridgeAdapter
import dev.junta.firmamobile.browser.MelillaBatchBridgeAdapter
import dev.junta.firmamobile.browser.MiniAppletBridgeMode
import dev.junta.firmamobile.browser.WebMessageBridge
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric supplies the real org.json implementation required by the assembler.
@RunWith(RobolectricTestRunner::class)
class ShimFeatureCatalogResolverScriptTest {
    private val legacyResolver by lazy { ShimFeatureResolver(BuiltInSiteProfiles.catalog) }
    private val batchOrigins = mapOf(
        "melilla-sede" to MelillaBatchBridgeAdapter.SOURCE_ORIGIN,
        "extremadura-tramites" to ExtremaduraBatchBridgeAdapter.SOURCE_ORIGIN,
        "la-palma-sede-electronica" to LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN,
        "diputacion-huesca-portal" to HuescaBatchBridgeAdapter.SOURCE_ORIGIN,
        "diputacion-burgos-portal" to BurgosBatchBridgeAdapter.SOURCE_ORIGIN,
        "diputacion-lugo-sede" to LugoBatchBridgeAdapter.SOURCE_ORIGIN,
        "caib-portafib" to CaibBatchBridgeAdapter.SOURCE_ORIGIN,
    )
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw"))
        .first { it.isDirectory }

    @Test
    fun resolverFeaturesProduceIdenticalLegacyScriptsForEveryProfile() {
        for (build in BuildTrustPolicy.entries) {
            val registry = SiteProfileRegistry(BuiltInSiteProfiles.catalog, build)
            for (profile in BuiltInSiteProfiles.catalog.profiles) {
                val id = profile.profileId
                val active = registry.profile(id) != null
                for (gate in listOf(false, true)) {
                    val enabled = gate && active && id.value in batchOrigins
                    val oldFlags = WebMessageBridge.shimCompatibilityFlags(id, active, enabled)
                    val newFlags = AfirmaShimCompatibilityFlags.fromFeatures(
                        legacyResolver.resolve(registry, id, enabled, batchOrigins[id.value]),
                    )
                    assertEquals("$build / $id / $gate", oldFlags, newFlags)
                    val configuration = AfirmaJavascriptShimConfiguration(
                        MiniAppletBridgeMode.FUNCTIONAL,
                        false,
                        oldFlags,
                        batchOrigins[id.value] ?: "https://sede.example.test",
                    )
                    val oldScript = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
                        File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
                    }
                    val newScript = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(
                        configuration.copy(flags = newFlags),
                    ) { File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8) }
                    assertEquals("$build / $id / $gate", oldScript, newScript)
                }
            }
        }
    }
}
