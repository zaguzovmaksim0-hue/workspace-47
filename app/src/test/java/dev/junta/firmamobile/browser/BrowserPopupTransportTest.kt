package dev.junta.firmamobile.browser

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class BrowserPopupTransportTest {
    @Test fun invalidMessagesDoNotCreateAWindowTransport() = withFixture { f ->
        assertNull(BrowserPopupTransport.create(f.parent, Message.obtain()) { true })
        val wrong = Message.obtain(f.handler).apply { obj = "not a transport" }
        assertNull(BrowserPopupTransport.create(f.parent, wrong) { true })
        val noTarget = Message.obtain().apply { obj = f.native }
        assertNull(BrowserPopupTransport.create(f.parent, noTarget) { true })
        assertEquals(0, f.deliveries)
    }
    @Test fun cancellationSendsOneNullChildAndNeverRepeatsTheMessage() = withFixture { f ->
        val transfer = f.transfer()
        transfer.cancel(); transfer.cancel(); assertFalse(transfer.attach(f.child))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, f.deliveries); assertNull(f.deliveredChild); assertFalse(transfer.attached)
    }
    @Test fun originalParentAndUnattachedViewsCannotBecomeTheChild() = withFixture { f ->
        val transfer = f.transfer(); val unattached = WebView(f.activity)
        assertFalse(transfer.attach(f.parent)); assertFalse(transfer.attach(unattached))
        assertEquals(0, f.deliveries)
        transfer.cancel(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, f.deliveries); assertNull(f.deliveredChild)
        unattached.destroy()
    }
    @Test fun handoffDeliversTheActualFreshChildExactlyOnce() = withFixture { f ->
        val transfer = f.transfer()
        assertTrue(f.child.isAttachedToWindow)
        assertTrue(transfer.attach(f.child)); assertTrue(transfer.attached)
        assertFalse(transfer.attach(f.child)); transfer.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, f.deliveries); assertSame(f.child, f.deliveredChild)
        assertNull(f.child.url)
    }
    @Test fun revokedParentNeverReceivesANewWindowAndCanBeReleased() = withFixture { f ->
        val transfer = checkNotNull(BrowserPopupTransport.create(f.parent, f.message) { false })
        assertFalse(transfer.attach(f.child)); assertFalse(transfer.attached)
        transfer.cancel(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, f.deliveries); assertNull(f.deliveredChild)
    }
    @Test fun reentrantCancellationDuringOwnershipCheckDoesNotSendTwice() = withFixture { f ->
        lateinit var transfer: BrowserPopupTransport
        transfer = checkNotNull(BrowserPopupTransport.create(f.parent, f.message) { transfer.cancel(); true })
        assertFalse(transfer.attach(f.child))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, f.deliveries); assertNull(f.deliveredChild)
    }
    private class Fixture(val activity: Activity) {
        val parent = WebView(activity)
        val child = WebView(activity).also { activity.setContentView(it) }
        val native: WebView.WebViewTransport = Shadow.newInstanceOf(WebView.WebViewTransport::class.java)
        var deliveries = 0
        var deliveredChild: WebView? = null
        val handler = Handler(Looper.getMainLooper()) { message ->
            deliveries++
            assertSame(native, message.obj)
            deliveredChild = (message.obj as WebView.WebViewTransport).webView
            true
        }
        val message: Message = Message.obtain(handler).apply { obj = native }
        fun transfer() = checkNotNull(BrowserPopupTransport.create(parent, message) { true })
    }
    private fun withFixture(block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val f = Fixture(controller.get())
        try { block(f) } finally { f.child.destroy(); f.parent.destroy(); controller.pause().stop().destroy() }
    }
}
