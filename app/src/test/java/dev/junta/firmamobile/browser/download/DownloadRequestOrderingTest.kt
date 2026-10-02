package dev.junta.firmamobile.browser.download

import org.junit.Assert.*
import org.junit.Test

class DownloadRequestOrderingTest {
    private var nowNanos = 0L
    private val evidence = DownloadGetEvidence { nowNanos }
    private val url = "https://download.synthetic.example/file"

    @Test
    fun mainFrameGetSurvivesIgnoredIframePost() {
        evidence.record(url, "GET", false)
        assertFalse(evidence.consume(url))

        evidence.record(url, "GET", true)
        nowNanos += 1_000_000_000L
        evidence.record(url, "POST", false)
        evidence.navigationStarted(url)

        assertTrue(evidence.consume(url))
        assertFalse(evidence.consume(url))
    }

    @Test
    fun actualMainFramePostInvalidatesEarlierGet() {
        evidence.record(url, "GET", true)
        nowNanos += 1_000_000_000L
        evidence.record(url, "POST", true)
        evidence.navigationStarted(url)

        assertFalse(evidence.consume(url))
    }

    @Test
    fun redirectOrDifferentDocumentNavigationInvalidatesOldCandidate() {
        val redirectedUrl = "$url?redirected=true"

        evidence.record(url, "GET", true)
        nowNanos += 1_000_000_000L
        evidence.record(redirectedUrl, "GET", true)
        assertFalse(evidence.consume(url))

        evidence.clear()
        evidence.record(url, "GET", true)
        nowNanos += 1_000_000_000L
        evidence.navigationStarted(redirectedUrl)

        assertFalse(evidence.consume(url))
        assertFalse(evidence.consume(redirectedUrl))
    }
}
