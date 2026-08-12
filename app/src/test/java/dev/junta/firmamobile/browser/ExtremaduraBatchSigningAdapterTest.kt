package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.CallbackContractId
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolInputAdapterId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.signing.ExtremaduraBatchProtocolAdapter
import dev.junta.firmamobile.signing.SigningErrorCode
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
class ExtremaduraBatchSigningAdapterTest {
    @Test
    fun exactExtremaduraBridgeOwnershipNormalizesToItsDistinctProtocolOnly() {
        val bridge = ExtremaduraBatchBridgeAdapter(
            activeProfileId = { ProfileId(ExtremaduraBatchBridgeAdapter.PROFILE_ID) },
        )
        val routed = bridge.route(
            rawMessage = extremaduraEnvelope(),
            sourceOrigin = Uri.parse(ExtremaduraBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9L,
        ) as MelillaBatchBridgeRouteResult.Accepted

        val observedAt = Instant.parse("2026-08-12T00:00:00Z")
        val normalized = checkNotNull(
            ExtremaduraBatchSigningAdapter(
                registry = extremaduraRegistry(),
                clock = Clock.fixed(observedAt, ZoneOffset.UTC),
            ).normalize(routed.request),
        )
        assertEquals(ExtremaduraBatchProtocolAdapter.ID, normalized.protocolId)
        assertEquals("extremadura-tramites", normalized.context.profileId)
        assertEquals(1, normalized.context.profileVersion)
        assertEquals("tramites.juntaex.es", normalized.context.origin.host)
        assertEquals(9L, normalized.context.navigationEpoch)
        assertEquals(observedAt, normalized.context.observedAt)
        normalized.close()

        val wrongOrigin = ExtremaduraBatchBridgeAdapter(
            activeProfileId = { ProfileId(ExtremaduraBatchBridgeAdapter.PROFILE_ID) },
        ).route(
            rawMessage = extremaduraEnvelope(),
            sourceOrigin = Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9L,
        ) as MelillaBatchBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, wrongOrigin.code)

        val wrongProfile = ExtremaduraBatchBridgeAdapter(
            activeProfileId = { ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID) },
        ).route(
            rawMessage = extremaduraEnvelope(),
            sourceOrigin = Uri.parse(ExtremaduraBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9L,
        ) as MelillaBatchBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.PROFILE_NOT_ACTIVE, wrongProfile.code)
    }

    private fun extremaduraRegistry(): SiteProfileRegistry {
        val catalog = BuiltInSiteProfiles.catalog
        val extremadura = melillaTemplate().copy(
            profileId = ProfileId("extremadura-tramites"),
            displayName = "Extremadura test fixture",
            startUrl = URI("https://tramites.juntaex.es/"),
            initiatorOrigins = setOf(ExactOrigin.parse("https://tramites.juntaex.es")),
            operationPolicies = melillaTemplate().operationPolicies.mapValues { (operation, policy) ->
                if (operation == ProtocolOperation.SIGN) {
                    policy.copy(
                        inputAdapterId = ProtocolInputAdapterId(ExtremaduraBatchProtocolAdapter.ID.value),
                        callbackContractId = CallbackContractId("extremadura-batch-result-v1"),
                    )
                } else {
                    policy
                }
            },
            evidence = listOf(
                melillaTemplate().evidence.first().copy(
                    url = URI("https://tramites.juntaex.es/"),
                    reviewedOn = LocalDate.of(2026, 8, 12),
                ),
            ),
        )
        return SiteProfileRegistry(
            catalog.copy(profiles = catalog.profiles + extremadura),
            BuildTrustPolicy.QA,
        )
    }

    private fun melillaTemplate(): SiteProfile = BuiltInSiteProfiles.catalog.profiles.single {
        it.profileId.value == MelillaBatchBridgeAdapter.PROFILE_ID
    }

    private fun extremaduraEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_BATCH")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("batchPreSignerUrl", "$ORIGIN/sta/AutofirmaLote/presign/$OPERATION_ID")
        .put("batchPostSignerUrl", "$ORIGIN/sta/AutofirmaLote/postsign/$OPERATION_ID")
        .put("algorithm", "SHA256withRSA")
        .put("format", "CAdES")
        .put("suboperation", "sign")
        .put("stopOnError", false)
        .put(
            "documentos",
            JSONArray().put(
                JSONObject()
                    .put("id", "runtime-document-1")
                    .put(
                        "datareference",
                        "$ORIGIN/sta/AutofirmaLote/getdata/$OPERATION_ID/runtime-document-1",
                    ),
            ),
        )
        .toString()

    private companion object {
        const val ORIGIN = "https://tramites.juntaex.es"
        const val OPERATION_ID = "runtime-operation-1"
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174010"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174011"
    }
}
