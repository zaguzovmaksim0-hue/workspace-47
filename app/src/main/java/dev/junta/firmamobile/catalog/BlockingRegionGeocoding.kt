package dev.junta.firmamobile.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

// A stuck system geocoder must not accumulate blocked IO workers on repeated requests.
private val regionGeocodingDispatcher = Dispatchers.IO.limitedParallelism(1)

internal suspend fun <T> blockingRegionGeocoding(call: () -> T): T {
    // Legacy Geocoder calls cannot be interrupted. Do not make cancellation wait for them.
    val request = CoroutineScope(regionGeocodingDispatcher).async { call() }
    return try {
        request.await()
    } finally {
        request.cancel()
    }
}
