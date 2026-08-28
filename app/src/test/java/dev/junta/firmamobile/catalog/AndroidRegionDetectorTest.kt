package dev.junta.firmamobile.catalog

import android.location.Location
import android.location.LocationManager
import java.io.IOException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val source = source(
            LocationManager.NETWORK_PROVIDER to ProviderResponse.Hang,
            LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)),
        )
        val detector = detector(source = source, addresses = listOf(aragonAddress()))

        val result = detector.detect()

        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            source.attemptedProviders,
        )
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), source.cancelledProviders)
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ARAGON), result)
    }

    @Test
    fun `fused success stops network and gps fallback`() = runTest {
        val source = source(
            LocationManager.FUSED_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.FUSED_PROVIDER)),
            LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.NETWORK_PROVIDER)),
            LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)),
        )

        val result = detector(source = source, addresses = listOf(aragonAddress())).detect()

        assertEquals(listOf(LocationManager.FUSED_PROVIDER), source.attemptedProviders)
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ARAGON), result)
    }

    @Test
    fun `fused null falls back to network`() = runTest {
        val source = source(
            LocationManager.FUSED_PROVIDER to ProviderResponse.LocationValue(null),
            LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.NETWORK_PROVIDER)),
            LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)),
        )

        val result = detector(source = source, addresses = listOf(aragonAddress())).detect()

        assertEquals(
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER),
            source.attemptedProviders,
        )
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ARAGON), result)
    }

    @Test
    fun `fused timeout continues through network to gps`() = runTest {
        val source = source(
            LocationManager.FUSED_PROVIDER to ProviderResponse.Hang,
            LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(null),
            LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)),
        )

        val details = detector(source = source, addresses = listOf(aragonAddress())).detectDetailed()

        assertEquals(
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            source.attemptedProviders,
        )
        assertEquals(listOf(LocationManager.FUSED_PROVIDER), source.cancelledProviders)
        assertEquals(
            listOf(
                RegionProviderAttempt(LocationManager.FUSED_PROVIDER, RegionProviderAttemptOutcome.TIMEOUT),
                RegionProviderAttempt(LocationManager.NETWORK_PROVIDER, RegionProviderAttemptOutcome.NULL),
                RegionProviderAttempt(LocationManager.GPS_PROVIDER, RegionProviderAttemptOutcome.LOCATION_RECEIVED),
            ),
            details.providerAttempts,
        )
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ARAGON), details.result)
    }

    @Test
    fun `all providers timeout or null return unavailable with diagnostic reason`() = runTest {
        val source = source(
            LocationManager.FUSED_PROVIDER to ProviderResponse.Hang,
            LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(null),
            LocationManager.GPS_PROVIDER to ProviderResponse.Hang,
        )

        val details = detector(source = source, addresses = listOf(aragonAddress())).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, details.result)
        assertEquals(RegionDetectionFailureReason.LOCATION_TIMEOUT, details.failureReason)
        assertEquals(
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER),
            source.cancelledProviders,
        )
    }

    @Test
    fun `all providers returning null report location null`() = runTest {
        val source = source(
            LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(null),
            LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(null),
        )

        val details = detector(source = source, addresses = listOf(aragonAddress())).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, details.result)
        assertEquals(RegionDetectionFailureReason.LOCATION_NULL, details.failureReason)
        assertEquals(
            listOf(
                RegionProviderAttempt(LocationManager.NETWORK_PROVIDER, RegionProviderAttemptOutcome.NULL),
                RegionProviderAttempt(LocationManager.GPS_PROVIDER, RegionProviderAttemptOutcome.NULL),
            ),
            details.providerAttempts,
        )
    }

    @Test
    fun `no providers returns unavailable without requesting a location`() = runTest {
        val source = FakeRegionLocationSource(emptyList(), emptyMap())

        val details = detector(source = source, addresses = listOf(aragonAddress())).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, details.result)
        assertEquals(RegionDetectionFailureReason.NO_PROVIDER, details.failureReason)
        assertTrue(source.attemptedProviders.isEmpty())
    }

    @Test
    fun `missing coarse permission returns permission denied without provider access`() = runTest {
        val source = FakeRegionLocationSource(
            providers = listOf(LocationManager.GPS_PROVIDER),
            responses = mapOf(LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER))),
            permissionGranted = false,
        )

        val result = detector(source = source, addresses = listOf(aragonAddress())).detect()

        assertEquals(RegionDetectionResult.PermissionDenied, result)
        assertEquals(0, source.availableProvidersCalls)
        assertTrue(source.attemptedProviders.isEmpty())
    }

    @Test
    fun `disabled location returns location disabled without provider request`() = runTest {
        val source = FakeRegionLocationSource(
            providers = listOf(LocationManager.GPS_PROVIDER),
            responses = mapOf(LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER))),
            locationEnabled = false,
        )

        val result = detector(source = source, addresses = listOf(aragonAddress())).detect()

        assertEquals(RegionDetectionResult.LocationDisabled, result)
        assertEquals(0, source.availableProvidersCalls)
        assertTrue(source.attemptedProviders.isEmpty())
    }

    @Test
    fun `geocoder unavailable returns unavailable without collecting location`() = runTest {
        val source = source(LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)))
        val geocoder = FakeRegionGeocoder(present = false)

        val details = detector(source = source, geocoder = geocoder).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, details.result)
        assertEquals(RegionDetectionFailureReason.GEOCODER_UNAVAILABLE, details.failureReason)
        assertTrue(source.attemptedProviders.isEmpty())
    }

    @Test
    fun `geocoder io error returns unavailable`() = runTest {
        val source = source(LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)))
        val geocoder = FakeRegionGeocoder(response = GeocoderResponse.IoError)

        val details = detector(source = source, geocoder = geocoder).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, details.result)
        assertEquals(RegionDetectionFailureReason.GEOCODER_ERROR, details.failureReason)
    }

    @Test
    fun `geocoder timeout returns unavailable`() = runTest {
        val source = source(LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)))
        val geocoder = FakeRegionGeocoder(response = GeocoderResponse.Hang)

        val details = detector(source = source, geocoder = geocoder).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, details.result)
        assertEquals(RegionDetectionFailureReason.GEOCODER_TIMEOUT, details.failureReason)
    }

    @Test
    fun `null country is unresolved rather than outside spain`() = runTest {
        val source = source(LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)))

        val details = detector(
            source = source,
            addresses = listOf(RegionAddress(null, "Aragón", null, null)),
        ).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, details.result)
        assertEquals(RegionDetectionFailureReason.REGION_UNRESOLVED, details.failureReason)
    }

    @Test
    fun `explicit non spanish country returns outside spain`() = runTest {
        val source = source(LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)))

        val result = detector(
            source = source,
            addresses = listOf(RegionAddress("PT", "Alentejo", null, null)),
        ).detect()

        assertEquals(RegionDetectionResult.OutsideSpain, result)
    }

    @Test
    fun `second geocoder result can resolve spanish region`() = runTest {
        val source = source(LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)))
        val geocoder = FakeRegionGeocoder(
            response = GeocoderResponse.Addresses(
                listOf(
                    RegionAddress("ES", "Área desconocida", null, null),
                    aragonAddress(),
                    RegionAddress("ES", "Otra área", null, null),
                    RegionAddress("ES", "No debe solicitarse", null, null),
                ),
            ),
        )

        val result = detector(source = source, geocoder = geocoder).detect()

        assertEquals(3, geocoder.lastMaxResults)
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ARAGON), result)
    }

    @Test
    fun `cancelling detection cancels the active provider request`() = runTest {
        val source = source(LocationManager.NETWORK_PROVIDER to ProviderResponse.Hang)
        val detector = detector(source = source, addresses = listOf(aragonAddress()))
        val job = launch { detector.detect() }
        runCurrent()

        job.cancelAndJoin()

        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), source.cancelledProviders)
    }

    @Test
    fun `provider candidates use system fused only on api 31 plus`() {
        assertEquals(
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            regionLocationProviderCandidates(android.os.Build.VERSION_CODES.S),
        )
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            regionLocationProviderCandidates(android.os.Build.VERSION_CODES.R),
        )
    }

    private fun detector(
        source: FakeRegionLocationSource,
        addresses: List<RegionAddress> = emptyList(),
        geocoder: FakeRegionGeocoder = FakeRegionGeocoder(response = GeocoderResponse.Addresses(addresses)),
    ): AndroidRegionDetector = AndroidRegionDetector(
        locationSource = source,
        geocoder = geocoder,
        locationTimeoutMillis = 100L,
        geocoderTimeoutMillis = 100L,
    )

    private fun source(vararg responses: Pair<String, ProviderResponse>): FakeRegionLocationSource =
        FakeRegionLocationSource(
            providers = responses.map { it.first },
            responses = responses.toMap(),
        )

    private fun location(provider: String): Location = Location(provider)

    private fun aragonAddress(): RegionAddress = RegionAddress(
        countryCode = "ES",
        adminArea = "Aragón",
        subAdminArea = null,
        locality = null,
    )

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
        var availableProvidersCalls: Int = 0
            private set

        override fun hasCoarseLocationPermission(): Boolean = permissionGranted

        override fun isLocationEnabled(): Boolean = locationEnabled

        override fun availableProviders(): List<String> {
            availableProvidersCalls++
            return providers
        }

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

    private sealed interface GeocoderResponse {
        data class Addresses(val values: List<RegionAddress>) : GeocoderResponse
        data object IoError : GeocoderResponse
        data object Hang : GeocoderResponse
    }

    private class FakeRegionGeocoder(
        private val present: Boolean = true,
        private val response: GeocoderResponse = GeocoderResponse.Addresses(emptyList()),
    ) : RegionGeocoder {
        var lastMaxResults: Int? = null
            private set

        override fun isPresent(): Boolean = present

        override suspend fun reverseGeocode(location: Location, maxResults: Int): List<RegionAddress> {
            lastMaxResults = maxResults
            return when (response) {
                is GeocoderResponse.Addresses -> response.values.take(maxResults)
                GeocoderResponse.IoError -> throw IOException("synthetic geocoder failure")
                GeocoderResponse.Hang -> suspendCancellableCoroutine { }
            }
        }
    }
}
