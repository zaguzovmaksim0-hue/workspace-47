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

class AndroidRegionDetector(
    private val context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
) : RegionDetector {
    override suspend fun detect(): RegionDetectionResult {
        if (!hasCoarseLocationPermission()) return RegionDetectionResult.PermissionDenied
        if (!isLocationEnabled()) return RegionDetectionResult.LocationDisabled
        if (!Geocoder.isPresent()) return RegionDetectionResult.Unavailable

        val location = try {
            currentLocation()
        } catch (_: SecurityException) {
            return RegionDetectionResult.PermissionDenied
        } ?: return RegionDetectionResult.Unavailable

        val address = try {
            reverseGeocode(location)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return RegionDetectionResult.Unavailable

        if (address.countryCode?.uppercase(Locale.ROOT) != "ES") {
            return RegionDetectionResult.OutsideSpain
        }
        val region = PortalRegionResolver.resolve(
            RegionAddress(
                countryCode = address.countryCode,
                adminArea = address.adminArea,
                subAdminArea = address.subAdminArea,
                locality = address.locality,
            ),
        ) ?: return RegionDetectionResult.Unavailable
        return RegionDetectionResult.Success(region)
    }

    private fun hasCoarseLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
    }

    private suspend fun currentLocation(): Location? = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
        for (provider in providers) {
            val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                currentLocationApi30(provider)
            } else {
                currentLocationLegacy(provider)
            }
            if (location != null) return@withTimeoutOrNull location
        }
        null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun currentLocationApi30(provider: String): Location? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
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
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
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

    private suspend fun reverseGeocode(location: Location): Address? =
        withTimeoutOrNull(GEOCODER_TIMEOUT_MILLIS) {
            val geocoder = Geocoder(context, Locale.forLanguageTag("es-ES"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reverseGeocodeApi33(geocoder, location)
            } else {
                reverseGeocodeLegacy(geocoder, location)
            }
        }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun reverseGeocodeApi33(geocoder: Geocoder, location: Location): Address? =
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeLegacy(geocoder: Geocoder, location: Location): Address? =
        withContext(Dispatchers.IO) {
            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 12_000L
        const val GEOCODER_TIMEOUT_MILLIS = 8_000L
    }
}
