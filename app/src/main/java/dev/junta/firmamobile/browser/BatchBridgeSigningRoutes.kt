package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.signing.BatchSigningReplySink
import dev.junta.firmamobile.signing.NormalizedBatchSigningRequest

/** Batch bridge dispatch: each profile's normalizer and reply sink come from one adapter instance. */
internal class BatchBridgeSigningRoutes(registry: SiteProfileRegistry) {
    class Routed(val normalized: NormalizedBatchSigningRequest?, val replySink: BatchSigningReplySink)

    private class Route(
        val normalize: (MelillaBatchBridgeRequest) -> NormalizedBatchSigningRequest?,
        val replySink: (MelillaBatchReplyChannel) -> BatchSigningReplySink,
    )

    private val routes: Map<String, Route> = mapOf(
        MelillaBatchBridgeAdapter.PROFILE_ID to
            MelillaBatchSigningAdapter(registry).let { Route(it::normalize, it::replySink) },
        ExtremaduraBatchBridgeAdapter.PROFILE_ID to
            ExtremaduraBatchSigningAdapter(registry).let { Route(it::normalize, it::replySink) },
        LaPalmaBatchBridgeAdapter.PROFILE_ID to
            LaPalmaBatchSigningAdapter(registry).let { Route(it::normalize, it::replySink) },
        HuescaBatchBridgeAdapter.PROFILE_ID to
            HuescaBatchSigningAdapter(registry).let { Route(it::normalize, it::replySink) },
        LugoBatchBridgeAdapter.PROFILE_ID to
            LugoBatchSigningAdapter(registry).let { Route(it::normalize, it::replySink) },
        CaibBatchBridgeAdapter.PROFILE_ID to
            CaibBatchSigningAdapter(registry).let { Route(it::normalize, it::replySink) },
        BurgosBatchBridgeAdapter.PROFILE_ID to
            BurgosBatchSigningAdapter(registry).let { Route(it::normalize, it::replySink) },
    )

    val profileIds: Set<String> get() = routes.keys

    /** Null for an unknown profile, which is never normalized; the caller rejects on the raw channel. */
    fun dispatch(request: MelillaBatchBridgeRequest, reply: MelillaBatchReplyChannel): Routed? =
        routes[request.profileId.value]?.let { route -> Routed(route.normalize(request), route.replySink(reply)) }
}
