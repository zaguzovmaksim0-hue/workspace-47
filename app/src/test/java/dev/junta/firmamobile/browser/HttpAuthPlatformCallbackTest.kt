package dev.junta.firmamobile.browser

import android.app.Activity
import android.content.Context
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(shadows = [HttpAuthPlatformCallbackTest.HandlerShadow::class])
class HttpAuthPlatformCallbackTest {
    @Implements(HttpAuthHandler::class)
    class HandlerShadow {
        var cancels = 0
        var proceeds = 0

        @Implementation
        fun cancel() { cancels++ }

        @Implementation
        fun proceed(username: String, password: String) { proceeds++ }
    }

    private lateinit var view: WebView
    private lateinit var wrapper: HttpAuthWebViewClient
    private lateinit var handler: HttpAuthHandler
    private lateinit var handlerShadow: HandlerShadow
    private var delegateCalls = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        view = WebView(context)
        wrapper = HttpAuthWebViewClient(object : WebViewClient() {
            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String,
            ) {
                delegateCalls++
                handler.proceed("synthetic-user", "synthetic-password")
            }
        })
        handler = Shadow.newInstanceOf(HttpAuthHandler::class.java)
        handlerShadow = Shadow.extract<HandlerShadow>(handler)
        assertFalse(context is Activity)
        assertFalse(view.isAttachedToWindow)
        assertFalse(view.hasWindowFocus())
    }

    @After
    fun tearDown() {
        try {
            wrapper.close()
        } finally {
            view.destroy()
        }
    }

    @Test
    fun detachedRequestCancelsOnceWithoutProceeding() {
        wrapper.onReceivedHttpAuthRequest(view, handler, "auth.example.invalid", "synthetic-realm")
        assertEquals(1, handlerShadow.cancels)
        assertEquals(0, handlerShadow.proceeds)
    }

    @Test
    fun closedWrapperCancelsOnceWithoutProceeding() {
        wrapper.close()
        wrapper.onReceivedHttpAuthRequest(view, handler, "auth.example.invalid", "synthetic-realm")
        assertEquals(1, handlerShadow.cancels)
        assertEquals(0, handlerShadow.proceeds)
    }

    @Test
    fun rejectedRequestDoesNotReachAutomaticCredentialDelegate() {
        wrapper.onReceivedHttpAuthRequest(view, handler, "auth.example.invalid", "synthetic-realm")
        assertEquals(0, delegateCalls)
        assertEquals(1, handlerShadow.cancels)
        assertEquals(0, handlerShadow.proceeds)
    }
}
