package dev.junta.firmamobile.browser

import dev.junta.firmamobile.network.BurgosBatchUrlPolicy
import dev.junta.firmamobile.network.CaibBatchUrlPolicy
import dev.junta.firmamobile.network.ExtremaduraBatchUrlPolicy
import dev.junta.firmamobile.network.HuescaBatchUrlPolicy
import dev.junta.firmamobile.network.LaPalmaBatchUrlPolicy
import dev.junta.firmamobile.network.LugoBatchUrlPolicy
import dev.junta.firmamobile.network.MelillaBatchUrlPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The batch bridges keep one origin literal each (in the URL policy); the catalogue and the
// parser pin must agree with it, so the bridge never trusts a host the catalogue does not list.
class StaBatchOriginSourceTest {
    private data class Row(val profileId: String, val sourceOrigin: String, val policyOrigin: String)

    private val rows = listOf(
        Row(MelillaBatchBridgeAdapter.PROFILE_ID, MelillaBatchBridgeAdapter.SOURCE_ORIGIN, MelillaBatchUrlPolicy.ORIGIN),
        Row(ExtremaduraBatchBridgeAdapter.PROFILE_ID, ExtremaduraBatchBridgeAdapter.SOURCE_ORIGIN, ExtremaduraBatchUrlPolicy.ORIGIN),
        Row(LaPalmaBatchBridgeAdapter.PROFILE_ID, LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN, LaPalmaBatchUrlPolicy.ORIGIN),
        Row(HuescaBatchBridgeAdapter.PROFILE_ID, HuescaBatchBridgeAdapter.SOURCE_ORIGIN, HuescaBatchUrlPolicy.ORIGIN),
        Row(BurgosBatchBridgeAdapter.PROFILE_ID, BurgosBatchBridgeAdapter.SOURCE_ORIGIN, BurgosBatchUrlPolicy.ORIGIN),
        Row(LugoBatchBridgeAdapter.PROFILE_ID, LugoBatchBridgeAdapter.SOURCE_ORIGIN, LugoBatchUrlPolicy.ORIGIN),
        Row(CaibBatchBridgeAdapter.PROFILE_ID, CaibBatchBridgeAdapter.SOURCE_ORIGIN, CaibBatchUrlPolicy.ORIGIN),
    )

    @Test
    fun bridgeOriginEqualsPolicyOriginAndIsListedByCatalogue() {
        for (row in rows) {
            assertEquals(row.profileId, row.policyOrigin, row.sourceOrigin)
            val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == ProfileId(row.profileId) }
            assertTrue(row.profileId, ExactOrigin.parse(row.sourceOrigin) in profile.initiatorOrigins)
        }
    }

    @Test
    fun trustedOriginIsTheHttps443FormOfTheSourceOrigin() {
        for (row in rows) {
            val trusted = httpsTrustedOrigin(row.sourceOrigin)
            assertEquals(row.profileId, row.sourceOrigin, trusted.serialized)
            assertEquals(row.profileId, 443, trusted.port)
        }
    }
}
