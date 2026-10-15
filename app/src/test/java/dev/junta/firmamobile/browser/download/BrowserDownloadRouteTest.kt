package dev.junta.firmamobile.browser.download

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserDownloadRouteTest {
    @Test
    fun dataUrlTakesPrecedenceOverObservedMethod() {
        for (method in listOf(null, ObservedMethod.GET, ObservedMethod.POST, ObservedMethod.OTHER)) {
            for (url in listOf("data:application/pdf;base64,JVBERg==", "DaTa:text/plain,document")) {
                assertEquals("$url / $method", BrowserDownloadRoute.DATA, selectBrowserDownloadRoute(url, method))
            }
        }
    }

    @Test
    fun blobUrlTakesPrecedenceOverObservedMethod() {
        for (method in listOf(null, ObservedMethod.GET, ObservedMethod.POST, ObservedMethod.OTHER)) {
            for (url in listOf("blob:https://download.synthetic.example/id", "BlOb:https://download.synthetic.example/id")) {
                assertEquals("$url / $method", BrowserDownloadRoute.BLOB, selectBrowserDownloadRoute(url, method))
            }
        }
    }

    @Test
    fun observedPostRequiresFormCapture() {
        assertEquals(BrowserDownloadRoute.POST,
            selectBrowserDownloadRoute("https://download.synthetic.example/file", ObservedMethod.POST))
    }

    @Test
    fun observedGetUsesDirectDownload() {
        assertEquals(BrowserDownloadRoute.GET,
            selectBrowserDownloadRoute("https://download.synthetic.example/file", ObservedMethod.GET))
    }

    @Test
    fun unobservedRedirectTargetUsesGet() {
        assertEquals(BrowserDownloadRoute.GET,
            selectBrowserDownloadRoute("https://download.synthetic.example/redirected.pdf", null))
    }

    @Test
    fun otherObservedMethodsAreUnsupported() {
        assertEquals(BrowserDownloadRoute.UNSUPPORTED,
            selectBrowserDownloadRoute("https://download.synthetic.example/file", ObservedMethod.OTHER))
    }

    @Test
    fun inlineSchemeMustBeAtTheStartAndIncludeColon() {
        for (url in listOf("https://download.synthetic.example/data:file", "https://download.synthetic.example/blob:file",
            "data-file", "blob-file")) {
            assertEquals(url, BrowserDownloadRoute.POST, selectBrowserDownloadRoute(url, ObservedMethod.POST))
        }
    }
}
