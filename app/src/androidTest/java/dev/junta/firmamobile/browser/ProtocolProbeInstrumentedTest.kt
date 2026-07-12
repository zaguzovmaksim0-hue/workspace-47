package dev.junta.firmamobile.browser

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtocolProbeInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test
    fun debugProbeIsExplicitlyProtectedByTheShellOnlyDumpPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, ProtocolProbeActivity::class.java),
            PackageManager.GET_META_DATA,
        )

        assertTrue(info.exported)
        assertEquals("android.permission.DUMP", info.permission)
        assertEquals(context.packageName, info.packageName)
    }
}
