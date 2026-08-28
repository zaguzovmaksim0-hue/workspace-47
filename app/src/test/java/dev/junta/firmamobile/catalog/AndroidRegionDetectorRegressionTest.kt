package dev.junta.firmamobile.catalog

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import org.robolectric.shadows.ShadowGeocoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AndroidRegionDetectorRegressionTest {
    @Test
    fun `network timeout does not starve gps fallback`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        ShadowGeocoder.setIsPresent(true)

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowManager = shadowOf(manager)
        shadowManager.setLocationEnabled(true)
        shadowManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadowManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val detector = AndroidRegionDetector(context, manager)
        val result = async { invokeCurrentLocation(detector) }
        runCurrent()

        assertTrue(shadowManager.getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).isNotEmpty())
        assertTrue(shadowManager.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())

        val gpsLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 41.6488
            longitude = -0.8891
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
        shadowManager.simulateLocation(LocationManager.GPS_PROVIDER, gpsLocation)

        advanceTimeBy(12_001)
        runCurrent()

        assertEquals(gpsLocation, result.await())
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeCurrentLocation(detector: AndroidRegionDetector): Location? = suspendCoroutine { continuation ->
        val method = AndroidRegionDetector::class.java.getDeclaredMethod(
            "currentLocation",
            Continuation::class.java,
        ).apply { isAccessible = true }
        try {
            val returned = method.invoke(detector, continuation)
            if (returned !== COROUTINE_SUSPENDED) continuation.resume(returned as Location?)
        } catch (failure: java.lang.reflect.InvocationTargetException) {
            continuation.resumeWithException(failure.targetException)
        } catch (failure: Throwable) {
            continuation.resumeWithException(failure)
        }
    }
}
