package dev.junta.firmamobile.catalog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface RegionDetectionResult {
    data class Success(val region: PortalRegionCode) : RegionDetectionResult
    data object PermissionDenied : RegionDetectionResult
    data object LocationDisabled : RegionDetectionResult
    data object Unavailable : RegionDetectionResult
    data object OutsideSpain : RegionDetectionResult
}

fun interface RegionDetector {
    suspend fun detect(): RegionDetectionResult
}

internal enum class RegionDetectionFailureReason {
    NO_PROVIDER,
    LOCATION_TIMEOUT,
    LOCATION_NULL,
    GEOCODER_UNAVAILABLE,
    GEOCODER_TIMEOUT,
    GEOCODER_ERROR,
    REGION_UNRESOLVED,
}

internal data class RegionDetectionDetail(
    val result: RegionDetectionResult,
    val failureReason: RegionDetectionFailureReason? = null,
)

internal interface RegionLocationSource {
    fun hasCoarseLocationPermission(): Boolean
    fun isLocationEnabled(): Boolean
    fun availableProviders(): List<String>
    suspend fun currentLocation(provider: String): Location?
}

internal interface RegionGeocoder {
    fun isPresent(): Boolean
    suspend fun reverseGeocode(location: Location, maxResults: Int): List<RegionAddress>
}

internal fun regionLocationProviderCandidates(sdkInt: Int): List<String> =
    if (sdkInt >= Build.VERSION_CODES.S) {
        listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
    } else {
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    }

class AndroidRegionDetector internal constructor(
    private val locationSource: RegionLocationSource,
    private val geocoder: RegionGeocoder,
    private val locationTimeoutMillis: Long = LOCATION_TIMEOUT_MILLIS,
    private val geocoderTimeoutMillis: Long = GEOCODER_TIMEOUT_MILLIS,
) : RegionDetector {
    constructor(context: Context) : this(
        locationSource = AndroidRegionLocationSource(context),
        geocoder = AndroidRegionGeocoder(context),
    )

    override suspend fun detect(): RegionDetectionResult = detectDetailed().result

    internal suspend fun detectDetailed(): RegionDetectionDetail {
        if (!locationSource.hasCoarseLocationPermission()) {
            return RegionDetectionDetail(RegionDetectionResult.PermissionDenied)
        }
        if (!locationSource.isLocationEnabled()) {
            return RegionDetectionDetail(RegionDetectionResult.LocationDisabled)
        }
        if (!runCatching { geocoder.isPresent() }.getOrDefault(false)) {
            return unavailable(RegionDetectionFailureReason.GEOCODER_UNAVAILABLE)
        }

        val locationResult = try {
            currentLocationDetailed()
        } catch (_: SecurityException) {
            return RegionDetectionDetail(RegionDetectionResult.PermissionDenied)
        }
        val location = when (locationResult) {
            is LocationAcquisition.Success -> locationResult.location
            is LocationAcquisition.Failure -> return unavailable(locationResult.reason)
        }

        val addresses = when (val geocodeResult = reverseGeocode(location)) {
            is GeocodeAcquisition.Success -> geocodeResult.addresses
            is GeocodeAcquisition.Failure -> return unavailable(geocodeResult.reason)
        }
        return resolveAddresses(addresses)
    }

    private suspend fun currentLocationDetailed(): LocationAcquisition {
        val providers = locationSource.availableProviders()
        if (providers.isEmpty()) {
            return LocationAcquisition.Failure(RegionDetectionFailureReason.NO_PROVIDER)
        }

        var sawTimeout = false
        var sawNull = false
        for (provider in providers) {
            val attempt = try {
                withTimeoutOrNull(locationTimeoutMillis) {
                    ProviderAttempt(locationSource.currentLocation(provider))
                }
            } catch (_: IllegalArgumentException) {
                ProviderAttempt(null)
            }
            if (attempt == null) {
                sawTimeout = true
                continue
            }
            if (attempt.location != null) {
                return LocationAcquisition.Success(attempt.location)
            }
            sawNull = true
        }

        return LocationAcquisition.Failure(
            when {
                sawTimeout -> RegionDetectionFailureReason.LOCATION_TIMEOUT
                sawNull -> RegionDetectionFailureReason.LOCATION_NULL
                else -> RegionDetectionFailureReason.NO_PROVIDER
            },
        )
    }

    private suspend fun reverseGeocode(location: Location): GeocodeAcquisition {
        return try {
            val attempt = withTimeoutOrNull(geocoderTimeoutMillis) {
                GeocodeAttempt(
                    geocoder.reverseGeocode(
                        location = location,
                        maxResults = MAX_GEOCODER_RESULTS,
                    ),
                )
            } ?: return GeocodeAcquisition.Failure(RegionDetectionFailureReason.GEOCODER_TIMEOUT)
            GeocodeAcquisition.Success(attempt.addresses)
        } catch (_: IOException) {
            GeocodeAcquisition.Failure(RegionDetectionFailureReason.GEOCODER_ERROR)
        } catch (_: IllegalArgumentException) {
            GeocodeAcquisition.Failure(RegionDetectionFailureReason.GEOCODER_ERROR)
        }
    }

    private fun resolveAddresses(addresses: List<RegionAddress>): RegionDetectionDetail {
        if (addresses.isEmpty()) return unavailable(RegionDetectionFailureReason.REGION_UNRESOLVED)

        addresses.forEach { address ->
            if (address.countryCode?.uppercase(Locale.ROOT) == SPAIN_COUNTRY_CODE) {
                PortalRegionResolver.resolve(address)?.let { region ->
                    return RegionDetectionDetail(RegionDetectionResult.Success(region))
                }
            }
        }

        val hasUnknownCountry = addresses.any { address -> address.countryCode.isNullOrBlank() }
        val hasSpainResult = addresses.any { address ->
            address.countryCode?.uppercase(Locale.ROOT) == SPAIN_COUNTRY_CODE
        }
        return if (!hasUnknownCountry && !hasSpainResult) {
            RegionDetectionDetail(RegionDetectionResult.OutsideSpain)
        } else {
            unavailable(RegionDetectionFailureReason.REGION_UNRESOLVED)
        }
    }

    private fun unavailable(reason: RegionDetectionFailureReason): RegionDetectionDetail =
        RegionDetectionDetail(RegionDetectionResult.Unavailable, reason)

    private data class ProviderAttempt(val location: Location?)

    private sealed interface LocationAcquisition {
        data class Success(val location: Location) : LocationAcquisition
        data class Failure(val reason: RegionDetectionFailureReason) : LocationAcquisition
    }

    private data class GeocodeAttempt(val addresses: List<RegionAddress>)

    private sealed interface GeocodeAcquisition {
        data class Success(val addresses: List<RegionAddress>) : GeocodeAcquisition
        data class Failure(val reason: RegionDetectionFailureReason) : GeocodeAcquisition
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 12_000L
        const val GEOCODER_TIMEOUT_MILLIS = 8_000L
        const val MAX_GEOCODER_RESULTS = 3
        const val SPAIN_COUNTRY_CODE = "ES"
    }
}

internal class AndroidRegionLocationSource(
    private val context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
) : RegionLocationSource {
    override fun hasCoarseLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    override fun isLocationEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        candidateProviders().any(::isProviderEnabled)
    }

    override fun availableProviders(): List<String> = candidateProviders().filter(::isProviderEnabled)

    override suspend fun currentLocation(provider: String): Location? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            currentLocationApi30(provider)
        } else {
            currentLocationLegacy(provider)
        }

    private fun candidateProviders(): List<String> = regionLocationProviderCandidates(Build.VERSION.SDK_INT)

    private fun isProviderEnabled(provider: String): Boolean =
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun currentLocationApi30(provider: String): Location? {
        if (!hasCoarseLocationPermission()) return null
        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                context.mainExecutor,
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private suspend fun currentLocationLegacy(provider: String): Location? {
        if (!hasCoarseLocationPermission()) return null
        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }

                override fun onProviderDisabled(provider: String) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }
}

internal class AndroidRegionGeocoder(
    private val context: Context,
) : RegionGeocoder {
    override fun isPresent(): Boolean = Geocoder.isPresent()

    override suspend fun reverseGeocode(location: Location, maxResults: Int): List<RegionAddress> {
        val geocoder = Geocoder(context, Locale.forLanguageTag("es-ES"))
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reverseGeocodeApi33(geocoder, location, maxResults)
        } else {
            reverseGeocodeLegacy(geocoder, location, maxResults)
        }
        return addresses.map { address -> address.toRegionAddress() }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun reverseGeocodeApi33(
        geocoder: Geocoder,
        location: Location,
        maxResults: Int,
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(
            location.latitude,
            location.longitude,
            maxResults,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(addresses.toList())
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException("Geocoder request failed"))
                    }
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeLegacy(
        geocoder: Geocoder,
        location: Location,
        maxResults: Int,
    ): List<Address> = withContext(Dispatchers.IO) {
        geocoder.getFromLocation(location.latitude, location.longitude, maxResults).orEmpty()
    }

    private fun Address.toRegionAddress(): RegionAddress = RegionAddress(
        countryCode = countryCode,
        adminArea = adminArea,
        subAdminArea = subAdminArea,
        locality = locality,
    )
}
