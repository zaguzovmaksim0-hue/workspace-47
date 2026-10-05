package dev.junta.firmamobile.catalog

import android.location.Location
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
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
class RegionRecoveryTest {
    @Test fun onlyFreshAccurateCachedFixesAreAccepted() {
        val location = Location("gps").apply {
            elapsedRealtimeNanos = 1_000_000_000L
            accuracy = 500f
            latitude = 36.7
            longitude = -6.1
        }
        assertTrue(isUsableRecentRegionLocation(location, 2_000_000_000L))
        assertFalse(isUsableRecentRegionLocation(location, 122_000_000_000L))
        assertFalse(isUsableRecentRegionLocation(location, 0L))
        location.accuracy = 50_000f
        assertFalse(isUsableRecentRegionLocation(location, 2_000_000_000L))
    }

    @Test fun geocodingRetriesOneTransientFailureWithoutAnotherLocationRequest() = runTest {
        var calls = 0
        val result = retryRegionGeocoding {
            calls++
            if (calls == 1) throw IOException("Synthetic transient failure")
            listOf("Andalucía")
        }
        assertEquals(listOf("Andalucía"), result)
        assertEquals(2, calls)
    }

    @Test fun emptyGeocoderResponseIsRetriedOnlyOnce() = runTest {
        var calls = 0
        assertTrue(retryRegionGeocoding<String> { calls++; emptyList() }.isEmpty())
        assertEquals(2, calls)
    }

    @Test fun recentGpsFixAvoidsWaitingForUnavailableNetworkProviders() = runTest {
        val location = Location("gps")
        var requested = false
        val source = object : RegionLocationSource {
            override fun hasCoarseLocationPermission() = true
            override fun isLocationEnabled() = true
            override fun availableProviders() = listOf("fused", "network", "gps")
            override fun recentLocation() = location
            override suspend fun currentLocation(provider: String): Location? { requested = true; return null }
        }
        val geocoder = object : RegionGeocoder {
            override fun isPresent() = true
            override suspend fun reverseGeocode(location: Location, maxResults: Int) =
                listOf(RegionAddress("ES", "Andalucía", "Cádiz", "Jerez de la Frontera"))
        }
        val result = AndroidRegionDetector(source, geocoder).detect()
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ANDALUSIA), result)
        assertFalse(requested)
    }
}
