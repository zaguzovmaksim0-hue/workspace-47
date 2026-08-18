package dev.junta.firmamobile.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
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
class CaibAfirmaJavascriptShimTest {
    @Test
    fun caibBatchShimIsExactMiniAppletContractAndSeparatelyFeatureGated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            caibBatchCompatibilityEnabled = true,
            staBatchOrigin = CaibBatchBridgeAdapter.SOURCE_ORIGIN,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
        )
        assertTrue(enabled.contains("const caibBatchCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const caibBatchCompatibilityEnabled = false"))
        assertTrue(enabled.contains("const caibOrigin = \"https://intranet.caib.es\""))
        assertTrue(enabled.contains("CAIB_BATCH_SIGN"))
        assertTrue(enabled.contains("CAIB_SET_FORCE_WS_MODE"))
        assertTrue(enabled.contains("CAIB_SET_SERVLETS"))
        assertTrue(enabled.contains("type: \"CAIB_XML_BATCH\""))
        assertTrue(enabled.contains("pending.caibXml === true"))
        assertTrue(enabled.contains("/-1\\/index$/"))
        assertFalse(enabled.contains("__JFM_CAIB_BATCH_COMPATIBILITY_ENABLED__"))
    }
}
