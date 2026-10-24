package dev.junta.firmamobile

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.annotation.Resetter
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowResources
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-rES-w400dp-h1200dp", shadows = [CatalogReadFailureResources::class])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MainActivityCatalogStartupTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun failedBundledCatalogReadShowsRetryAndRetryReachesCertificateHome() {
        val app = ApplicationProvider.getApplicationContext<JuntaFirmaApplication>()
        // Match the manifest-declared normal permission before the real Activity starts.
        shadowOf(app).grantPermissions(Manifest.permission.HIDE_OVERLAY_WINDOWS)
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            app.checkSelfPermission(Manifest.permission.HIDE_OVERLAY_WINDOWS),
        )
        val failure = app.getString(R.string.catalog_open_failed)
        val retry = app.getString(R.string.browser_retry)
        CatalogReadFailureResources.failuresRemaining.set(1)
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(20_000) {
                rule.onAllNodesWithText(failure).fetchSemanticsNodes().size == 1
            }
            rule.onNodeWithText(retry).assertIsDisplayed()
            rule.onNodeWithTag("jfm-home-background").assertDoesNotExist()
            rule.onNodeWithTag("catalog-list").assertDoesNotExist()
            assertEquals(1, CatalogReadFailureResources.reads.get())

            rule.onNodeWithText(retry).performClick()
            rule.waitUntil(20_000) {
                rule.onAllNodesWithTag("browse-without-certificate").fetchSemanticsNodes().size == 1
            }
            rule.onNodeWithText(failure).assertDoesNotExist()
            rule.onNodeWithText(retry).assertDoesNotExist()
            rule.onNodeWithTag("jfm-home-background").assertIsDisplayed()
            rule.onNodeWithTag("catalog-list").assertDoesNotExist()
            assertEquals(2, CatalogReadFailureResources.reads.get())
        }
    }
}

/** Test-only Android resource fault, not a replacement Activity or navigation model.
 * Every other read, and the successful retry, uses the real bundled resources.
 */
@Implements(Resources::class)
class CatalogReadFailureResources : ShadowResources() {
    @RealObject private lateinit var realResources: Resources

    @Implementation
    protected fun openRawResource(id: Int): InputStream {
        if (id == R.raw.public_portal_catalog_v1) {
            reads.incrementAndGet()
            if (failuresRemaining.getAndUpdate { remaining -> maxOf(0, remaining - 1) } > 0) {
                throw Resources.NotFoundException("Synthetic catalog read failure")
            }
        }
        return Shadow.directlyOn(
            realResources,
            Resources::class.java,
            "openRawResource",
            ClassParameter.from(Int::class.javaPrimitiveType!!, id),
        )
    }

    companion object {
        val failuresRemaining = AtomicInteger()
        val reads = AtomicInteger()

        @JvmStatic @Resetter fun resetCatalogReadFault() {
            failuresRemaining.set(0)
            reads.set(0)
        }
    }
}
