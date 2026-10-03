package dev.junta.firmamobile.browser

import android.content.Context
import android.os.Message
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
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
class PopupChromeCallbacksTest {
    private val views = mutableListOf<WebView>()

    private fun newView(): WebView =
        WebView(ApplicationProvider.getApplicationContext<Context>()).also {
            views.add(it)
        }

    @After
    fun destroyViews() {
        views.forEach { it.destroy() }
    }

    @Test
    fun defaultRejectsWithoutCallback() {
        val client = JuntaWebChromeClient()
        val view = newView()
        val message = Message.obtain().apply { obj = Any() }

        assertNull(client.createWindow)
        assertFalse(client.onCreateWindow(view, true, false, message))
    }

    @Test
    fun callbackReturnsTrueAndForwardsExactArgumentsOnce() {
        val client = JuntaWebChromeClient()
        val view = newView()
        val isDialog = true
        val isGesture = false
        val message = Message.obtain().apply { obj = Any() }
        var calls = 0

        client.createWindow = { actualView, actualDialog, actualGesture, actualMessage ->
            calls++
            assertSame(view, actualView)
            assertEquals(isDialog, actualDialog)
            assertEquals(isGesture, actualGesture)
            assertSame(message, actualMessage)
            true
        }

        assertTrue(client.onCreateWindow(view, isDialog, isGesture, message))
        assertEquals(1, calls)
    }

    @Test
    fun closeWindowForwardsOnlyExactGivenWindowOnce() {
        val client = JuntaWebChromeClient()
        val window = newView()
        val unrelatedView = newView()
        var calls = 0

        client.closeWindow = { actualWindow ->
            calls++
            assertSame(window, actualWindow)
            assertNotSame(unrelatedView, actualWindow)
        }

        client.onCloseWindow(window)

        assertEquals(1, calls)
    }
}
