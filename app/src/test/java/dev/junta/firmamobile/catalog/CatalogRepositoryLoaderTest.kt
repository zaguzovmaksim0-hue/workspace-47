package dev.junta.firmamobile.catalog

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CatalogRepositoryLoaderTest {
    @Test fun concurrentScreensShareOneSuccessfulLoad() = runTest {
        var loads = 0
        val value = Any()
        val loader = CatalogRepositoryLoader { loads++; delay(1); value }
        val results = List(10) { async { loader.get() } }.awaitAll()
        assertEquals(1, loads)
        assertTrue(results.all { it === value })
    }
    @Test fun failedLoadDoesNotPoisonRetry() = runTest {
        var loads = 0
        val loader = CatalogRepositoryLoader { loads++; if (loads == 1) error("bad input") else "ready" }
        assertTrue(runCatching { loader.get() }.isFailure)
        assertEquals("ready", loader.get())
        assertEquals("ready", loader.get())
        assertEquals(2, loads)
    }
}
