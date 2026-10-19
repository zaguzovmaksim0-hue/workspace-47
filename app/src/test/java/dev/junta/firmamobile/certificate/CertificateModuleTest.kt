package dev.junta.firmamobile.certificate

import android.net.Uri
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class CertificateModuleTest {
    @Test
    fun createsEachComponentEagerlyOnceInOrder() {
        val calls = mutableListOf<String>()
        val monotonicNanos = { 0L }
        val session = CertificateSession(monotonicNanos = monotonicNanos)
        val cache = FakeUnlockCache()
        val gateway = FakeGateway()
        val module = CertificateModule(
            monotonicNanos = monotonicNanos,
            sessionFactory = { suppliedMonotonicNanos ->
                calls += "session"
                assertSame(monotonicNanos, suppliedMonotonicNanos)
                session
            },
            unlockCacheFactory = {
                calls += "cache"
                cache
            },
            gatewayFactory = {
                calls += "gateway"
                gateway
            },
        )
        val expectedOrder = listOf("session", "cache", "gateway")
        assertEquals(expectedOrder, calls)

        repeat(2) {
            assertSame(session, module.session)
            assertSame(cache, module.unlockCache)
            assertSame(gateway, module.gateway)
        }
        assertEquals(expectedOrder, calls)
    }

    @Test
    fun separateModulesOwnSeparateComponents() {
        val monotonicNanos = { 0L }
        val cacheFactory = { FakeUnlockCache() }
        val gatewayFactory = { FakeGateway() }
        val first = CertificateModule(
            monotonicNanos = monotonicNanos,
            unlockCacheFactory = cacheFactory,
            gatewayFactory = gatewayFactory,
        )
        val second = CertificateModule(
            monotonicNanos = monotonicNanos,
            unlockCacheFactory = cacheFactory,
            gatewayFactory = gatewayFactory,
        )

        assertNotSame(first.session, second.session)
        assertNotSame(first.unlockCache, second.unlockCache)
        assertNotSame(first.gateway, second.gateway)
    }

    @Test
    fun defaultSessionUsesTheProvidedMonotonicCallback() {
        var nowNanos = 0L
        var calls = 0
        val module = CertificateModule(
            monotonicNanos = {
                calls++
                nowNanos
            },
            unlockCacheFactory = { FakeUnlockCache() },
            gatewayFactory = { FakeGateway() },
        )
        val lifetime = Duration.ofHours(1)
        val expiresAt = Instant.now().plus(lifetime)
        assertEquals(0, calls)

        val firstLease = module.session.createUnlockLease(expiresAt, lifetime)
        nowNanos++
        val secondLease = module.session.createUnlockLease(expiresAt, lifetime)

        assertEquals(0L, firstLease.observedAtMonotonicNanos)
        assertEquals(1L, secondLease.observedAtMonotonicNanos)
        assertEquals(lifetime.toNanos(), firstLease.lifetimeNanos)
        assertEquals(lifetime.toNanos(), secondLease.lifetimeNanos)
        assertEquals(2, calls)
    }

    private class FakeUnlockCache : CertificateUnlockCache by NoOpCertificateUnlockCache

    private class FakeGateway : CertificateGateway {
        override suspend fun currentReference(): StoredCertificateReference? = null

        override suspend fun select(uri: Uri): CertificateSelectionResult =
            CertificateSelectionResult.Failure(CertificateSelectionErrorCode.DOCUMENT_UNAVAILABLE)

        override suspend fun unlock(password: CharArray): CertificateLoadResult =
            CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)

        override suspend fun forget() = Unit
    }
}
