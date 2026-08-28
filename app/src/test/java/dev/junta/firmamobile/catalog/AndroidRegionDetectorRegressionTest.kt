package dev.junta.firmamobile.catalog

import android.Manifest
import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
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
@Config(sdk = [35])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AndroidRegionDetectorRegressionTest {
    @Test
    fun `provider timeout cancels current request and next provider can start`() = runTest {
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
}
