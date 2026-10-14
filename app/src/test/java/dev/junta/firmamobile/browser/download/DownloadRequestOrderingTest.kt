package dev.junta.firmamobile.browser.download

import org.junit.Assert.*
import org.junit.Test

class DownloadRequestOrderingTest {
    private var nowNanos = 0L
    private val evidence = DownloadRequestEvidence { nowNanos }
    private val url = "https://download.synthetic.example/file"

    @Test
    fun iframeRequestsAreEvidenceToo() {
        evidence.record(url, "GET")
        assertEquals(ObservedMethod.GET, evidence.methodOf(url))
    }

    @Test
    fun laterFormPostToTheSamePageAddressIsAPost() {
        // JSF pages post back to their own address.
        evidence.record(url, "GET")
        nowNanos += 1_000_000_000L
        evidence.record(url, "POST")
        assertEquals(ObservedMethod.POST, evidence.methodOf(url))
    }

    @Test
    fun clearForgetsEverything() {
        evidence.record(url, "POST")
        evidence.clear()
        assertNull(evidence.methodOf(url))
    }
}
