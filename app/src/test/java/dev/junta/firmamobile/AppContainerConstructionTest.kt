package dev.junta.firmamobile

import android.net.Uri
import dev.junta.firmamobile.certificate.CertificateErrorCode
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.CertificateModule
import dev.junta.firmamobile.certificate.CertificateSelectionErrorCode
import dev.junta.firmamobile.certificate.CertificateSelectionResult
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.NoOpCertificateUnlockCache
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.signing.SigningUiState
import java.lang.reflect.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AppContainerConstructionTest {
    @Test
    fun createsCertificateGraphEagerlyOnceInTheExistingOrder() {
        val calls = mutableListOf<String>()
        val monotonicNanos = { 0L }
        val session = CertificateSession(monotonicNanos = monotonicNanos)
        val cache = TestUnlockCache()
        val gateway = TestGateway()
        val container = AppContainer {
            calls += "module"
            CertificateModule(
                monotonicNanos = monotonicNanos,
                sessionFactory = { suppliedClock ->
                    calls += "session"
                    assertSame(monotonicNanos, suppliedClock)
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
        }

        assertEquals(listOf("module", "session", "cache", "gateway"), calls)
        val module = container.certificateModule
        repeat(3) {
            assertSame(module, container.certificateModule)
            assertSame(session, container.certificateModule.session)
            assertSame(cache, container.certificateModule.unlockCache)
            assertSame(gateway, container.certificateModule.gateway)
        }
        assertEquals(listOf("module", "session", "cache", "gateway"), calls)
    }

    @Test
    fun separateContainersOwnSeparateCertificateGraphs() {
        val first = testContainer().certificateModule
        val second = testContainer().certificateModule

        assertNotSame(first, second)
        assertNotSame(first.session, second.session)
        assertNotSame(first.unlockCache, second.unlockCache)
        assertNotSame(first.gateway, second.gateway)
    }

    @Test
    fun signingRequestsAreFreshAndUseTheCurrentApplicationSessionAndScope() {
        val app = RuntimeEnvironment.getApplication() as JuntaFirmaApplication
        val container = app.appContainer
        val originalSession = app.certificateSession
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.Unconfined)
        val secondScope = CoroutineScope(secondJob + Dispatchers.Unconfined)
        try {
            val first = container.createSigningModule(app.certificateSession, firstScope)
            val replacementSession = CertificateSession()
            app.certificateSession = replacementSession
            val second = container.createSigningModule(app.certificateSession, secondScope)
            val repeated = container.createSigningModule(app.certificateSession, secondScope)

            assertNotSame(first, second)
            assertNotSame(second, repeated)
            assertSame(originalSession, field(first, "certificateSession"))
            assertSame(replacementSession, field(second, "certificateSession"))
            assertSame(replacementSession, field(repeated, "certificateSession"))
            assertSame(firstScope, field(first, "scope"))
            assertSame(secondScope, field(second, "scope"))
            assertSame(secondScope, field(repeated, "scope"))
            assertSame(originalSession, container.certificateModule.session)
            assertTrue(firstJob.isActive)
            assertTrue(secondJob.isActive)
        } finally {
            app.certificateSession = originalSession
            firstJob.cancel()
            secondJob.cancel()
        }
    }

    @Test
    fun coordinatorCleanupAndOneOwnerCancellationDoNotCancelTheOtherScope() {
        val container = testContainer()
        val session = container.certificateModule.session
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.Unconfined)
        val secondScope = CoroutineScope(secondJob + Dispatchers.Unconfined)
        val first = container.createSigningModule(session, firstScope).ordinary(
            HttpsProfileHttpTransport(), HttpsProfileHttpTransport(), { null }, { 0L }, { null },
        )
        val second = container.createSigningModule(session, secondScope).ordinary(
            HttpsProfileHttpTransport(), HttpsProfileHttpTransport(), { null }, { 0L }, { null },
        )
        try {
            assertSame(session, field(first, "certificateSession"))
            assertSame(session, field(second, "certificateSession"))
            assertSame(firstScope, field(field(first, "expiryScheduler"), "scope"))
            assertSame(secondScope, field(field(second, "expiryScheduler"), "scope"))
            assertNotSame(field(first, "expiryScheduler"), field(second, "expiryScheduler"))
            assertEquals(SigningUiState.Idle, first.state.value)
            assertEquals(SigningUiState.Idle, second.state.value)
            first.close()
            assertTrue(firstJob.isActive)
            firstJob.cancel()
            assertTrue(secondJob.isActive)
            second.close()
            assertTrue(secondJob.isActive)
        } finally {
            first.close()
            second.close()
            firstJob.cancel()
            secondJob.cancel()
        }
    }

    @Test
    fun applicationPublishesTheGraphWithoutRemovingMutableInjectionHooks() {
        val app = RuntimeEnvironment.getApplication() as JuntaFirmaApplication
        val container = app.appContainer
        val module = container.certificateModule
        assertSame(module.session, app.certificateSession)
        assertSame(module.unlockCache, app.certificateUnlockCache)
        assertSame(module.gateway, app.certificateGateway)
        try {
            val replacementSession = CertificateSession()
            val replacementCache = TestUnlockCache()
            val replacementGateway = TestGateway()
            app.certificateSession = replacementSession
            app.certificateUnlockCache = replacementCache
            app.certificateGateway = replacementGateway

            assertSame(replacementSession, app.certificateSession)
            assertSame(replacementCache, app.certificateUnlockCache)
            assertSame(replacementGateway, app.certificateGateway)
            assertSame(container, app.appContainer)
            assertSame(module, container.certificateModule)
            assertNotSame(replacementSession, module.session)
            assertNotSame(replacementCache, module.unlockCache)
            assertNotSame(replacementGateway, module.gateway)
        } finally {
            app.certificateSession = module.session
            app.certificateUnlockCache = module.unlockCache
            app.certificateGateway = module.gateway
        }
    }

    @Test
    fun containerStoresOnlyTheCertificateModuleNotScopesOrFactoryClosures() {
        val fields = AppContainer::class.java.declaredFields.filterNot {
            Modifier.isStatic(it.modifiers)
        }
        assertEquals(listOf(CertificateModule::class.java), fields.map { it.type })
    }

    private fun testContainer(): AppContainer = AppContainer {
        CertificateModule(
            monotonicNanos = { 0L },
            unlockCacheFactory = { TestUnlockCache() },
            gatewayFactory = { TestGateway() },
        )
    }

    // Test-only constructor ownership observations, as in SigningModuleRegistryRegressionTest.
    private fun field(instance: Any, name: String): Any =
        instance.javaClass.getDeclaredField(name).run {
            isAccessible = true
            checkNotNull(get(instance))
        }

    private class TestUnlockCache : CertificateUnlockCache by NoOpCertificateUnlockCache

    private class TestGateway : CertificateGateway {
        override suspend fun currentReference(): StoredCertificateReference? = null

        override suspend fun select(uri: Uri): CertificateSelectionResult =
            CertificateSelectionResult.Failure(CertificateSelectionErrorCode.DOCUMENT_UNAVAILABLE)

        override suspend fun unlock(password: CharArray): CertificateLoadResult =
            CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)

        override suspend fun forget() = Unit
    }
}
