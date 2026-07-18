package dev.junta.firmamobile.browser

import android.os.Bundle
import dev.junta.firmamobile.network.JuntaOriginPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class WebViewStateHolderTest {
    @Test
    fun loadsStartUrlWhenThereIsNoSavedHistory() {
        val target = FakeTarget()

        val restored = WebViewStateHolder(null).restoreOrLoad(target)

        assertFalse(restored)
        assertEquals(listOf(JuntaOriginPolicy.START_URL), target.loadedUrls)
    }

    @Test
    fun restoresSavedHistoryOnceWithoutLoadingOverIt() {
        val saved = Bundle().apply {
            putBundle(WebViewStateHolder.STATE_KEY, Bundle().apply { putString("marker", "ok") })
        }
        val target = FakeTarget(restoreResult = true)
        val holder = WebViewStateHolder(saved)

        assertTrue(holder.restoreOrLoad(target))
        assertEquals("ok", target.restoredState?.getString("marker"))
        assertTrue(target.loadedUrls.isEmpty())

        assertFalse(holder.restoreOrLoad(target))
        assertEquals(listOf(JuntaOriginPolicy.START_URL), target.loadedUrls)
    }

    @Test
    fun restoredHistoryPublishesItsActualCurrentUrl() {
        val restoredUrl = "https://ssoweb.juntadeandalucia.es/restored"
        val saved = Bundle().apply {
            putBundle(WebViewStateHolder.STATE_KEY, Bundle())
        }
        val target = FakeTarget(
            restoreResult = true,
            currentUrlValue = restoredUrl,
        )
        var publishedUrl: String? = null

        assertTrue(
            WebViewStateHolder(saved).restoreOrLoad(target) { url ->
                publishedUrl = url
            },
        )

        assertEquals(restoredUrl, publishedUrl)
        assertTrue(target.loadedUrls.isEmpty())
    }

    @Test
    fun failedRestoreFallsBackToTheExactStartUrl() {
        val saved = Bundle().apply {
            putBundle(WebViewStateHolder.STATE_KEY, Bundle())
        }
        val target = FakeTarget(restoreResult = false)

        assertFalse(WebViewStateHolder(saved).restoreOrLoad(target))
        assertEquals(listOf(JuntaOriginPolicy.START_URL), target.loadedUrls)
    }

    @Test
    fun catalogEntryUrlIsUsedAsTheFailClosedFallback() {
        val target = FakeTarget()
        val entryUrl = "https://reg.redsara.es/es/"

        assertFalse(WebViewStateHolder(null).restoreOrLoad(target, entryUrl))
        assertEquals(listOf(entryUrl), target.loadedUrls)
    }

    @Test
    fun savesHistoryOnlyWhenWebViewReportsUsableState() {
        val successfulOutState = Bundle()
        WebViewStateHolder(null).save(FakeTarget(saveResult = true), successfulOutState)
        assertNotNull(successfulOutState.getBundle(WebViewStateHolder.STATE_KEY))

        val failedOutState = Bundle()
        WebViewStateHolder(null).save(FakeTarget(saveResult = false), failedOutState)
        assertNull(failedOutState.getBundle(WebViewStateHolder.STATE_KEY))
    }

    @Test
    fun capturedInMemoryHistoryCanBeClearedBeforeAUserReturns() {
        val holder = WebViewStateHolder(null)
        assertTrue(holder.capture(FakeTarget(saveResult = true)))
        holder.clear()
        val target = FakeTarget(restoreResult = true)

        assertFalse(holder.restoreOrLoad(target))
        assertNull(target.restoredState)
        assertEquals(listOf(JuntaOriginPolicy.START_URL), target.loadedUrls)
    }

    private class FakeTarget(
        private val restoreResult: Boolean = false,
        private val saveResult: Boolean = false,
        private val currentUrlValue: String? = null,
    ) : WebViewStateTarget {
        var restoredState: Bundle? = null
        val loadedUrls = mutableListOf<String>()

        override fun restoreState(state: Bundle): Boolean {
            restoredState = state
            return restoreResult
        }

        override fun saveState(state: Bundle): Boolean {
            state.putString("saved", "yes")
            return saveResult
        }

        override fun currentUrl(): String? = currentUrlValue

        override fun loadUrl(url: String) {
            loadedUrls += url
        }
    }
}
