package dev.junta.firmamobile.catalog

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
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

        val result = detector(source, spanishGeocoder("Aragón")).detect()

        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            source.attemptedProviders,
        )
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), source.cancelledProviders)
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ARAGON), result)
    }

    @Test
    fun `fused success stops network and gps fallback`() = runTest {
        val source = FakeRegionLocationSource(
            providers = MODERN_PROVIDERS,
            responses = mapOf(
                LocationManager.FUSED_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.FUSED_PROVIDER)),
                LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.NETWORK_PROVIDER)),
                LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)),
            ),
        )

        val result = detector(source, spanishGeocoder("Galicia")).detect()

        assertEquals(listOf(LocationManager.FUSED_PROVIDER), source.attemptedProviders)
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.GALICIA), result)
    }

    @Test
    fun `fused null falls back to network`() = runTest {
        val source = FakeRegionLocationSource(
            providers = MODERN_PROVIDERS,
            responses = mapOf(
                LocationManager.FUSED_PROVIDER to ProviderResponse.LocationValue(null),
                LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.NETWORK_PROVIDER)),
                LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)),
            ),
        )

        val result = detector(source, spanishGeocoder("Madrid")).detect()

        assertEquals(
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER),
            source.attemptedProviders,
        )
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.MADRID), result)
    }

    @Test
    fun `fused timeout continues through network to gps`() = runTest {
        val source = FakeRegionLocationSource(
            providers = MODERN_PROVIDERS,
            responses = mapOf(
                LocationManager.FUSED_PROVIDER to ProviderResponse.Hang,
                LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(null),
                LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.GPS_PROVIDER)),
            ),
        )

        val result = detector(source, spanishGeocoder("Andalucía")).detect()

        assertEquals(MODERN_PROVIDERS, source.attemptedProviders)
        assertEquals(listOf(LocationManager.FUSED_PROVIDER), source.cancelledProviders)
        assertEquals(RegionDetectionResult.Success(PortalRegionCode.ANDALUSIA), result)
    }

    @Test
    fun `all providers timeout or null return unavailable with diagnostic reason`() = runTest {
        val source = FakeRegionLocationSource(
            providers = MODERN_PROVIDERS,
            responses = mapOf(
                LocationManager.FUSED_PROVIDER to ProviderResponse.Hang,
                LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(null),
                LocationManager.GPS_PROVIDER to ProviderResponse.Hang,
            ),
        )

        val detail = detector(source, spanishGeocoder("Galicia")).detectDetailed()

        assertEquals(MODERN_PROVIDERS, source.attemptedProviders)
        assertEquals(
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER),
            source.cancelledProviders,
        )
        assertEquals(RegionDetectionResult.Unavailable, detail.result)
        assertEquals(RegionDetectionFailureReason.LOCATION_TIMEOUT, detail.failureReason)
    }

    @Test
    fun `all provider null results report location null`() = runTest {
        val source = FakeRegionLocationSource(
            providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            responses = mapOf(
                LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(null),
                LocationManager.GPS_PROVIDER to ProviderResponse.LocationValue(null),
            ),
        )

        val detail = detector(source, spanishGeocoder("Galicia")).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, detail.result)
        assertEquals(RegionDetectionFailureReason.LOCATION_NULL, detail.failureReason)
    }

    @Test
    fun `no providers returns unavailable without requesting a location`() = runTest {
        val source = FakeRegionLocationSource(providers = emptyList(), responses = emptyMap())

        val detail = detector(source, spanishGeocoder("Galicia")).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, detail.result)
        assertEquals(RegionDetectionFailureReason.NO_PROVIDER, detail.failureReason)
        assertTrue(source.attemptedProviders.isEmpty())
    }

    @Test
    fun `missing coarse permission returns permission denied without provider access`() = runTest {
        val source = FakeRegionLocationSource(
            providers = MODERN_PROVIDERS,
            responses = emptyMap(),
            permissionGranted = false,
        )
        val geocoder = spanishGeocoder("Galicia")

        val result = detector(source, geocoder).detect()

        assertEquals(RegionDetectionResult.PermissionDenied, result)
        assertEquals(0, source.availableProvidersCalls)
        assertTrue(source.attemptedProviders.isEmpty())
        assertEquals(0, geocoder.isPresentCalls)
    }

    @Test
    fun `disabled location returns location disabled without provider request`() = runTest {
        val source = FakeRegionLocationSource(
            providers = MODERN_PROVIDERS,
            responses = emptyMap(),
            locationEnabled = false,
        )
        val geocoder = spanishGeocoder("Galicia")

        val result = detector(source, geocoder).detect()

        assertEquals(RegionDetectionResult.LocationDisabled, result)
        assertEquals(0, source.availableProvidersCalls)
        assertTrue(source.attemptedProviders.isEmpty())
        assertEquals(0, geocoder.isPresentCalls)
    }

    @Test
    fun `geocoder unavailable returns unavailable without collecting location`() = runTest {
        val source = FakeRegionLocationSource(
            providers = MODERN_PROVIDERS,
            responses = emptyMap(),
        )
        val geocoder = FakeRegionGeocoder(present = false)

        val detail = detector(source, geocoder).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, detail.result)
        assertEquals(RegionDetectionFailureReason.GEOCODER_UNAVAILABLE, detail.failureReason)
        assertEquals(0, source.availableProvidersCalls)
    }

    @Test
    fun `geocoder io error returns unavailable`() = runTest {
        val detail = detector(
            successfulSource(),
            FakeRegionGeocoder(failure = IOException("synthetic geocoder failure")),
        ).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, detail.result)
        assertEquals(RegionDetectionFailureReason.GEOCODER_ERROR, detail.failureReason)
    }

    @Test
    fun `geocoder timeout returns unavailable`() = runTest {
        val detail = detector(
            successfulSource(),
            FakeRegionGeocoder(hang = true),
        ).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, detail.result)
        assertEquals(RegionDetectionFailureReason.GEOCODER_TIMEOUT, detail.failureReason)
    }

    @Test
    fun `null country is unresolved rather than outside spain`() = runTest {
        val detail = detector(
            successfulSource(),
            FakeRegionGeocoder(
                addresses = listOf(
                    RegionAddress(
                        countryCode = null,
                        adminArea = "Galicia",
                        subAdminArea = null,
                        locality = null,
                    ),
                ),
            ),
        ).detectDetailed()

        assertEquals(RegionDetectionResult.Unavailable, detail.result)
        assertEquals(RegionDetectionFailureReason.REGION_UNRESOLVED, detail.failureReason)
    }

    @Test
    fun `explicit non spanish country returns outside spain`() = runTest {
        val result = detector(
            successfulSource(),
            FakeRegionGeocoder(
                addresses = listOf(
                    RegionAddress(
                        countryCode = "PT",
                        adminArea = "Braga",
                        subAdminArea = null,
                        locality = null,
                    ),
                ),
            ),
        ).detect()

        assertEquals(RegionDetectionResult.OutsideSpain, result)
    }

    @Test
    fun `second geocoder result can resolve spanish region`() = runTest {
        val geocoder = FakeRegionGeocoder(
            addresses = listOf(
                RegionAddress(
                    countryCode = "ES",
                    adminArea = "Unknown administrative area",
                    subAdminArea = null,
                    locality = null,
                ),
                RegionAddress(
                    countryCode = "ES",
                    adminArea = "Galicia",
                    subAdminArea = null,
                    locality = null,
                ),
            ),
        )

        val result = detector(successfulSource(), geocoder).detect()

        assertEquals(RegionDetectionResult.Success(PortalRegionCode.GALICIA), result)
        assertEquals(listOf(3), geocoder.requestedMaxResults)
    }

    @Test
    fun `cancelling detection cancels the active provider request`() = runTest {
        val source = FakeRegionLocationSource(
            providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER),
            responses = mapOf(
                LocationManager.FUSED_PROVIDER to ProviderResponse.Hang,
                LocationManager.NETWORK_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.NETWORK_PROVIDER)),
            ),
        )
        val detector = detector(source, spanishGeocoder("Galicia"))

        val job = launch { detector.detect() }
        runCurrent()
        assertEquals(listOf(LocationManager.FUSED_PROVIDER), source.attemptedProviders)

        job.cancelAndJoin()

        assertEquals(listOf(LocationManager.FUSED_PROVIDER), source.cancelledProviders)
        assertEquals(listOf(LocationManager.FUSED_PROVIDER), source.attemptedProviders)
    }

    @Test
    fun `provider candidates use system fused only on api 31 plus`() {
        assertEquals(
            MODERN_PROVIDERS,
            regionLocationProviderCandidates(android.os.Build.VERSION_CODES.S),
        )
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            regionLocationProviderCandidates(android.os.Build.VERSION_CODES.R),
        )
    }

    @Test
    @Config(sdk = [35])
    fun `api 30 plus timeout cancels current platform request before fallback request`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowManager = shadowOf(manager)
        shadowManager.setLocationEnabled(true)
        shadowManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadowManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val source = AndroidRegionLocationSource(context, manager)

        val networkResult = async {
            withTimeoutOrNull(100L) {
                source.currentLocation(LocationManager.NETWORK_PROVIDER)
            }
        }
        runCurrent()
        assertTrue(shadowManager.getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).isNotEmpty())

        advanceTimeBy(101L)
        runCurrent()
        assertNull(networkResult.await())
        assertTrue(shadowManager.getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).isEmpty())

        val gpsResult = async { source.currentLocation(LocationManager.GPS_PROVIDER) }
        runCurrent()
        assertTrue(shadowManager.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isNotEmpty())

        gpsResult.cancelAndJoin()
        assertTrue(shadowManager.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
    }

    private fun detector(
        source: RegionLocationSource,
        geocoder: RegionGeocoder,
    ): AndroidRegionDetector = AndroidRegionDetector(
        locationSource = source,
        geocoder = geocoder,
        locationTimeoutMillis = 100L,
        geocoderTimeoutMillis = 100L,
    )

    private fun successfulSource(): FakeRegionLocationSource = FakeRegionLocationSource(
        providers = listOf(LocationManager.FUSED_PROVIDER),
        responses = mapOf(
            LocationManager.FUSED_PROVIDER to ProviderResponse.LocationValue(location(LocationManager.FUSED_PROVIDER)),
        ),
    )

    private fun spanishGeocoder(adminArea: String): FakeRegionGeocoder = FakeRegionGeocoder(
        addresses = listOf(
            RegionAddress(
                countryCode = "ES",
                adminArea = adminArea,
                subAdminArea = null,
                locality = null,
            ),
        ),
    )

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
        var availableProvidersCalls: Int = 0
            private set
        val attemptedProviders = mutableListOf<String>()
        val cancelledProviders = mutableListOf<String>()

        override fun hasCoarseLocationPermission(): Boolean = permissionGranted

        override fun isLocationEnabled(): Boolean = locationEnabled

        override fun availableProviders(): List<String> {
            availableProvidersCalls += 1
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

    private class FakeRegionGeocoder(
        private val present: Boolean = true,
        private val addresses: List<RegionAddress> = emptyList(),
        private val failure: IOException? = null,
        private val hang: Boolean = false,
    ) : RegionGeocoder {
        var isPresentCalls: Int = 0
            private set
        val requestedMaxResults = mutableListOf<Int>()

        override fun isPresent(): Boolean {
            isPresentCalls += 1
            return present
        }

        override suspend fun reverseGeocode(location: Location, maxResults: Int): List<RegionAddress> {
            requestedMaxResults += maxResults
            failure?.let { throw it }
            if (hang) {
                return suspendCancellableCoroutine<List<RegionAddress>> { }
            }
            return addresses.take(maxResults)
        }
    }

    private companion object {
        val MODERN_PROVIDERS = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
    }
}
