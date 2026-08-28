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

internal enum class RegionDetectionFailureReason {
    NO_PROVIDER,
    LOCATION_TIMEOUT,
    LOCATION_NULL,
    GEOCODER_UNAVAILABLE,
    GEOCODER_TIMEOUT,
    GEOCODER_ERROR,
    REGION_UNRESOLVED,
}

internal enum class RegionProviderAttemptOutcome {
    TIMEOUT,
    NULL,
    LOCATION_RECEIVED,
}

internal data class RegionProviderAttempt(
    val provider: String,
    val outcome: RegionProviderAttemptOutcome,
)

internal data class RegionDetectionDetails(
    val result: RegionDetectionResult,
    val failureReason: RegionDetectionFailureReason? = null,
    val providerAttempts: List<RegionProviderAttempt> = emptyList(),
)

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

    internal suspend fun detectDetailed(): RegionDetectionDetails {
        if (!locationSource.hasCoarseLocationPermission()) {
            return RegionDetectionDetails(RegionDetectionResult.PermissionDenied)
        }
        if (!locationSource.isLocationEnabled()) {
            return RegionDetectionDetails(RegionDetectionResult.LocationDisabled)
        }
        if (!geocoder.isPresent()) {
            return RegionDetectionDetails(
                result = RegionDetectionResult.Unavailable,
                failureReason = RegionDetectionFailureReason.GEOCODER_UNAVAILABLE,
            )
        }

        val providers = locationSource.availableProviders()
        if (providers.isEmpty()) {
            return RegionDetectionDetails(
                result = RegionDetectionResult.Unavailable,
                failureReason = RegionDetectionFailureReason.NO_PROVIDER,
            )
        }

        val attempts = mutableListOf<RegionProviderAttempt>()
        var sawLocationTimeout = false
        var sawOutsideSpain = false
        var geocoderFailure: RegionDetectionFailureReason? = null

        for (provider in providers) {
            val locationAttempt = try {
                withTimeoutOrNull(locationTimeoutMillis) {
                    ProviderAttemptCompletion(locationSource.currentLocation(provider))
                }
            } catch (_: SecurityException) {
                return RegionDetectionDetails(
                    result = RegionDetectionResult.PermissionDenied,
                    providerAttempts = attempts.toList(),
                )
            }

            if (locationAttempt == null) {
                sawLocationTimeout = true
                attempts += RegionProviderAttempt(provider, RegionProviderAttemptOutcome.TIMEOUT)
                continue
            }
            val location = locationAttempt.location
            if (location == null) {
                attempts += RegionProviderAttempt(provider, RegionProviderAttemptOutcome.NULL)
                continue
            }
            attempts += RegionProviderAttempt(provider, RegionProviderAttemptOutcome.LOCATION_RECEIVED)

            val geocodeAttempt = try {
                reverseGeocode(location)
            } catch (_: IOException) {
                geocoderFailure = RegionDetectionFailureReason.GEOCODER_ERROR
                continue
            } catch (_: IllegalArgumentException) {
                geocoderFailure = RegionDetectionFailureReason.GEOCODER_ERROR
                continue
            }

            if (geocodeAttempt is GeocodeAttempt.Timeout) {
                geocoderFailure = RegionDetectionFailureReason.GEOCODER_TIMEOUT
                continue
            }
            geocodeAttempt as GeocodeAttempt.Addresses
            when (val resolution = resolveAddresses(geocodeAttempt.addresses)) {
                is AddressResolution.Success -> return RegionDetectionDetails(
                    result = RegionDetectionResult.Success(resolution.region),
                    providerAttempts = attempts.toList(),
                )
                AddressResolution.OutsideSpain -> sawOutsideSpain = true
                AddressResolution.Unresolved -> {
                    geocoderFailure = RegionDetectionFailureReason.REGION_UNRESOLVED
                }
            }
        }

        if (sawOutsideSpain && geocoderFailure == null) {
            return RegionDetectionDetails(
                result = RegionDetectionResult.OutsideSpain,
                providerAttempts = attempts.toList(),
            )
        }
        geocoderFailure?.let { reason ->
            return RegionDetectionDetails(
                result = RegionDetectionResult.Unavailable,
                failureReason = reason,
                providerAttempts = attempts.toList(),
            )
        }
        return RegionDetectionDetails(
            result = if (sawOutsideSpain) RegionDetectionResult.OutsideSpain else RegionDetectionResult.Unavailable,
            failureReason = if (sawOutsideSpain) {
                null
            } else if (sawLocationTimeout) {
                RegionDetectionFailureReason.LOCATION_TIMEOUT
            } else {
                RegionDetectionFailureReason.LOCATION_NULL
            },
            providerAttempts = attempts.toList(),
        )
    }

    private suspend fun reverseGeocode(location: Location): GeocodeAttempt =
        withTimeoutOrNull(geocoderTimeoutMillis) {
            GeocodeAttempt.Addresses(
                geocoder.reverseGeocode(location, MAX_GEOCODER_RESULTS),
            )
        } ?: GeocodeAttempt.Timeout

    private fun resolveAddresses(addresses: List<RegionAddress>): AddressResolution {
        var sawSpain = false
        var sawOutsideSpain = false
        addresses.forEach { address ->
            when (address.countryCode?.uppercase(Locale.ROOT)) {
                null -> Unit
                "ES" -> {
                    sawSpain = true
                    PortalRegionResolver.resolve(address)?.let { region ->
                        return AddressResolution.Success(region)
                    }
                }
                else -> sawOutsideSpain = true
            }
        }
        return when {
            sawSpain -> AddressResolution.Unresolved
            sawOutsideSpain -> AddressResolution.OutsideSpain
            else -> AddressResolution.Unresolved
        }
    }

    private data class ProviderAttemptCompletion(val location: Location?)

    private sealed interface GeocodeAttempt {
        data class Addresses(val addresses: List<RegionAddress>) : GeocodeAttempt
        data object Timeout : GeocodeAttempt
    }

    private sealed interface AddressResolution {
        data class Success(val region: PortalRegionCode) : AddressResolution
        data object OutsideSpain : AddressResolution
        data object Unresolved : AddressResolution
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 12_000L
        const val GEOCODER_TIMEOUT_MILLIS = 8_000L
        const val MAX_GEOCODER_RESULTS = 3
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
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    context.mainExecutor,
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (_: SecurityException) {
                cancellationSignal.cancel()
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private suspend fun currentLocationLegacy(provider: String): Location? {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
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
            try {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                locationManager.removeUpdates(listener)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}

internal fun regionLocationProviderCandidates(sdkInt: Int): List<String> = buildList {
    if (sdkInt >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
    add(LocationManager.NETWORK_PROVIDER)
    add(LocationManager.GPS_PROVIDER)
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
                        continuation.resumeWithException(IOException("Geocoder failed"))
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
