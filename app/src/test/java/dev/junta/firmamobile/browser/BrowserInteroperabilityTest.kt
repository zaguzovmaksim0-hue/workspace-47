package dev.junta.firmamobile.browser

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class BrowserInteroperabilityTest {
    @Test
    fun externalBrowserIntentContainsOnlyHttpsUrlAndBrowsableCategory() {
        val request = ExternalHandoff.Request(ExternalHandoff.Kind.BROWSER,
            Uri.parse("https://id.example/login?state=fixture#return"), 1L, "id.example")
        val intent = requireNotNull(ExternalHandoff.intentFor(request))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(request.uri, intent.data)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), intent.categories)
        assertNull(intent.component)
        assertNull(intent.selector)
        assertNull(intent.extras)
        assertEquals(0, intent.flags)
    }

    @Test
    fun officialAutoFirmaIntentIsPinnedAndDoesNotForwardArbitraryIntentExtras() {
        val request = ExternalHandoff.Request(ExternalHandoff.Kind.AUTOFIRMA,
            Uri.parse("afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj"), 1L, "source.example")
        val intent = requireNotNull(ExternalHandoff.intentFor(request))
        assertEquals("es.gob.afirma", intent.`package`)
        assertNull(intent.component)
        assertNull(intent.selector)
        assertNull(intent.extras)
        assertEquals(0, intent.flags)
    }

    @Test
    fun handoffRejectsUnsafeSchemeUserInfoLocalHostsAndRawIntentSyntax() {
        listOf("http://example.org", "https://user@example.org", "https://127.0.0.1/",
            "https://example.org:8443/", "file:///private/file", "javascript:alert(1)",
            "intent://sign#Intent;scheme=afirma;component=malicious/.Activity;end").forEach { raw ->
            assertNull(raw, ExternalHandoff.intentFor(ExternalHandoff.Request(
                ExternalHandoff.Kind.BROWSER, Uri.parse(raw), 1L, "source.example")))
        }
        listOf("afirma://sign/path?x=1", "afirma://other?x=1", "afirma://sign?x=1#extra", "afirma://sign:80?x=1").forEach { raw ->
            assertNull(raw, ExternalHandoff.intentFor(ExternalHandoff.Request(
                ExternalHandoff.Kind.AUTOFIRMA, Uri.parse(raw), 1L, "source.example")))
        }
    }

    @Test
    fun filePickerUsesOpenDocumentAndOnlyReturnsReadableSelectedContent() = withFixture { f ->
        assertTrue(f.chooser.open(f.view, f.callback, Params()))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, f.launched.single().action)
        assertTrue(f.launched.single().hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("application/pdf", f.launched.single().type)
        val selected = Uri.parse("content://picker.example/selected.pdf")
        f.chooser.deliver(Activity.RESULT_OK, Intent().setData(selected))
        assertArrayEquals(arrayOf(selected), f.results.single())
    }

    @Test
    fun filePickerCancellationAndInvalidUriReturnNullExactlyOnce() = withFixture { f ->
        assertTrue(f.chooser.open(f.view, f.callback, Params()))
        f.chooser.deliver(Activity.RESULT_CANCELED, null)
        f.chooser.deliver(Activity.RESULT_OK, Intent().setData(Uri.parse("content://picker.example/late")))
        assertEquals(1, f.results.size)
        assertNull(f.results.single())
        assertTrue(f.chooser.open(f.view, f.callback, Params()))
        f.chooser.deliver(Activity.RESULT_OK, Intent().setData(Uri.parse("file:///private/key.p12")))
        assertEquals(2, f.results.size)
        assertNull(f.results.last())
    }

    @Test
    fun selectedContentWithoutReadGrantIsNotReturnedToPage() = withFixture { f ->
        f.readable = false
        f.chooser.open(f.view, f.callback, Params())
        f.chooser.deliver(Activity.RESULT_OK, Intent().setData(Uri.parse("content://picker.example/private")))
        assertNull(f.results.single())
    }

    @Test
    fun resultCannotCrossNavigationEpochOrWebViewOwnership() = withFixture { f ->
        f.chooser.open(f.view, f.callback, Params())
        f.epoch++
        f.chooser.deliver(Activity.RESULT_OK, Intent().setData(Uri.parse("content://picker.example/file")))
        assertNull(f.results.single())
        f.chooser.open(f.view, f.callback, Params())
        f.current = false
        f.chooser.deliver(Activity.RESULT_OK, Intent().setData(Uri.parse("content://picker.example/file")))
        assertNull(f.results.last())
    }

    @Test
    fun canceledPickerRetainsTombstoneUntilOldAndroidResultArrives() = withFixture { f ->
        f.chooser.open(f.view, f.callback, Params())
        f.chooser.cancel()
        val later = mutableListOf<Array<Uri>?>()
        f.chooser.open(f.view, ValueCallback { later.add(it) }, Params())
        assertNull(later.single())
        assertEquals(1, f.launched.size)
        f.chooser.deliver(Activity.RESULT_OK, Intent().setData(Uri.parse("content://picker.example/old")))
        assertEquals(1, f.results.size)
        assertNull(f.results.single())
        f.chooser.open(f.view, ValueCallback { later.add(it) }, Params())
        assertEquals(2, f.launched.size)
        f.chooser.deliver(Activity.RESULT_OK, Intent().setData(Uri.parse("content://picker.example/new")))
        assertEquals("content://picker.example/new", later.last()?.single().toString())
    }

    @Test
    fun multipleSelectionRespectsRequestedModeAndBound() = withFixture { f ->
        val first = Uri.parse("content://picker.example/one")
        val second = Uri.parse("content://picker.example/two")
        val data = Intent().apply {
            clipData = ClipData.newUri(ApplicationProvider.getApplicationContext<Context>().contentResolver, "fixture", first)
            clipData!!.addItem(ClipData.Item(second))
        }
        f.chooser.open(f.view, f.callback, Params())
        f.chooser.deliver(Activity.RESULT_OK, data)
        assertNull(f.results.single())
        f.chooser.open(f.view, f.callback, Params(WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE))
        f.chooser.deliver(Activity.RESULT_OK, data)
        assertArrayEquals(arrayOf(first, second), f.results.last())
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.chooser.cancel(); fixture.view.destroy() }
    }

    private class Fixture {
        var epoch = 1L
        var current = true
        var readable = true
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        val results = mutableListOf<Array<Uri>?>()
        val callback = ValueCallback<Array<Uri>> { results.add(it) }
        val launched = mutableListOf<Intent>()
        val chooser = BrowserFileChooser({ epoch }, { current && it === view }, { readable }, launched::add)
    }

    private class Params(private val requestedMode: Int = MODE_OPEN) : WebChromeClient.FileChooserParams() {
        override fun getMode(): Int = requestedMode
        override fun getAcceptTypes(): Array<String> = arrayOf("application/pdf")
        override fun isCaptureEnabled(): Boolean = false
        override fun getTitle(): CharSequence = "fixture"
        override fun getFilenameHint(): String = ""
        override fun createIntent(): Intent = error("Page-controlled intent must not be launched")
    }
}
