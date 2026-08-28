package dev.junta.firmamobile.catalog

import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
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
class AndroidRegionDetectorTest {
    @Test
    fun `network timeout does not starve gps fallback`() = runTest {
        val source = FakeRegionLocationSource(
            providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            responses = mapOf(
                LocationManager.NETWORK_PROVIDER to ProviderResponse.Hang,
                LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)),
            ),
        )
        val detector = AndroidRegionDetector(
            locationSource = source,
            geocoder = FakeRegionGeocoder(
                addresses = listOf(
                    RegionAddress(
                        countryCode = "ES",
                        adminArea = "Aragón",
                        subAdminArea = null,
                        locality = null,
                    ),
                ),
            ),
            locationTimeoutMillis = 100L,
            geocoderTimeoutMillis = 100L,
        )

        val result = detector.detect()

        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            source.attemptedProviders,
        )
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), source.cancelledProviders)
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ARAGON), result)
    }

    private fun location(provider: String): Location = Location(provider)

    private sealed interface ProviderResponse {
        data object Hang : ProviderResponse
        data class LocationValue(val location: Location?) : ProviderResponse
    }

    private class FakeRegionLocationSource(
        private val providers: List<String>,
        private val responses: Map<String, ProviderResponse>,
        private val permissionGranted: Boolean = true,
        private val locationEnabled: Boolean = true,
    ) : RegionLocationSource {
        val attemptedProviders = mutableListOf<String>()
        val cancelledProviders = mutableListOf<String>()

        override fun hasCoarseLocationPermission(): Boolean = permissionGranted

        override fun isLocationEnabled(): Boolean = locationEnabled

        override fun availableProviders(): List<String> = providers

        override suspend fun currentLocation(provider: String): Location? {
            attemptedProviders += provider
            return when (val response = responses.getValue(provider)) {
                ProviderResponse.Hang -> suspendCancellableCoroutine<Location?> { continuation ->
                    continuation.invokeOnCancellation { cancelledProviders += provider }
                }
                is ProviderResponse.LocationValue -> response.location
            }
        }
    }

    private class FakeRegionGeocoder(
        private val addresses: List<RegionAddress>,
    ) : RegionGeocoder {
        override fun isPresent(): Boolean = true

        override suspend fun reverseGeocode(location: Location, maxResults: Int): List<RegionAddress> =
            addresses.take(maxResults)
    }
}
