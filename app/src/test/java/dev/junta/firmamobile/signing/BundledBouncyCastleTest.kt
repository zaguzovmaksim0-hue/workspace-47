package dev.junta.firmamobile.signing

import java.security.Security
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class BundledBouncyCastleTest {
    @Test
    fun concurrentCallersShareOneIsolatedProviderWithoutChangingGlobalProviders() {
        val before = Security.getProviders().toList()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val providers = executor.invokeAll(List(16) { Callable { BundledBouncyCastle.provider } })
                .map { it.get(5, TimeUnit.SECONDS) }
            val provider = providers.first()
            providers.forEach { assertSame(provider, it) }
            assertEquals("BC", provider.name)
            assertFalse(Security.getProviders().any { it === provider })
            assertEquals(before, Security.getProviders().toList())
        } finally {
            executor.shutdownNow()
        }
    }
}
