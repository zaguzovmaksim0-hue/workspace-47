package dev.junta.firmamobile.browser

import android.Manifest
import android.R as AndroidR
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
import android.webkit.WebView
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.R
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

    @Test
    fun probeAllowsScreenshotsAndReservesTheCurrentNavigationInset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            ActivityScenario.launch(ProtocolProbeActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(0, activity.window.attributes.flags and FLAG_SECURE)
                    assertEquals(
                        SOFT_INPUT_ADJUST_RESIZE,
                        activity.window.attributes.softInputMode and SOFT_INPUT_MASK_ADJUST,
                    )
                    val content = activity.findViewById<ViewGroup>(AndroidR.id.content)
                    val root = content.findViewById<ViewGroup>(R.id.protocol_probe_root)
                    val navigationReservation = maxOf(
                        root.paddingLeft,
                        root.paddingRight,
                        root.paddingBottom,
                    )
                    assertTrue(
                        "The current navigation mode must reserve a non-top system edge",
                        navigationReservation > 0,
                    )

                    val navigation = WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, 0, 96),
                        )
                        .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                        .setVisible(WindowInsetsCompat.Type.ime(), false)
                        .build()
                    ViewCompat.dispatchApplyWindowInsets(root, navigation)
                    assertEquals(96, root.paddingBottom)

                    val keyboard = WindowInsetsCompat.Builder(navigation)
                        .setInsets(
                            WindowInsetsCompat.Type.ime(),
                            Insets.of(0, 0, 0, 320),
                        )
                        .setVisible(WindowInsetsCompat.Type.ime(), true)
                        .build()
                    ViewCompat.dispatchApplyWindowInsets(root, keyboard)
                    assertEquals(320, root.paddingBottom)
                    ViewCompat.dispatchApplyWindowInsets(root, navigation)
                    assertEquals(96, root.paddingBottom)
                }
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun longProbeObservationKeepsWebContentVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            ActivityScenario.launch(ProtocolProbeActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<TextView>(R.id.protocol_probe_status).text =
                        List(80) { index -> "safe observation $index" }.joinToString("\n")
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(R.id.protocol_probe_root)
                    val status = activity.findViewById<TextView>(R.id.protocol_probe_status)
                    val webView = activity.findViewById<WebView>(R.id.protocol_probe_webview)
                    assertTrue(root.height > 0)
                    assertTrue(status.height < root.height / 2)
                    assertTrue(webView.height > 0)
                }
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
