package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.network.HttpsProfileHttpTransport
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningModuleRegistryRegressionTest {
    @Test
    fun moduleRetainsSuppliedOwnersAndCallbacksWithoutOwningLifecycle() {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        val session = CertificateSession()
        val module = SigningModule(session, scope)
        var epoch = 3L
        var page = "https://example.invalid/first"
        val origin: () -> TrustedOrigin? = { null }
        val navigation: () -> Long = { epoch }
        val currentPage: () -> String? = { page }
        val ordinary = module.ordinary(HttpsProfileHttpTransport(), HttpsProfileHttpTransport(),
            origin, navigation, currentPage)
        val profile = checkNotNull(BuiltInSiteProfiles.catalog.profiles.singleOrNull {
            it.profileId == ProfileId("melilla-sede")
        })
        val batch = module.batch(origin, navigation, profile.displayName,
            profile.compatibilityStatus, BuiltInSiteProfiles.runtimeRegistry)
        try {
            for (coordinator in listOf(ordinary, batch)) {
                assertSame(session, field(coordinator, "certificateSession"))
                assertSame(origin, field(coordinator, "currentOrigin"))
                assertSame(navigation, field(coordinator, "currentNavigationEpoch"))
                assertSame(scope, field(field(coordinator, "expiryScheduler"), "scope"))
            }
            assertSame(currentPage, field(ordinary, "currentPageUrl"))
            assertSame(BuiltInSiteProfiles.runtimeRegistry, field(batch, "profileRegistry"))
            assertNotSame(field(ordinary, "localSignatureEngine"), field(batch, "localSignatureEngine"))
            assertNotSame(field(ordinary, "expiryScheduler"), field(batch, "expiryScheduler"))
            epoch = 9L
            page = "https://example.invalid/next"
            assertEquals(9L, navigation())
            assertEquals("https://example.invalid/next", currentPage())
            assertEquals(SigningUiState.Idle, ordinary.state.value)
            assertEquals(SigningUiState.Idle, batch.state.value)
            ordinary.close()
            batch.close()
            assertTrue("Coordinator cleanup must not cancel Activity lifecycle", job.isActive)
        } finally {
            ordinary.close()
            batch.close()
            job.cancel()
        }
    }

    private fun field(instance: Any, name: String): Any =
        instance.javaClass.getDeclaredField(name).run {
            isAccessible = true
            checkNotNull(get(instance))
        }

    @Test
    fun ordinaryRegistryPreservesAdapterOrderAndResolverInstances() {
        val adapters = RuntimeSigningAdapters.ordinary(
            directJuntaTransport = HttpsProfileHttpTransport(),
            ofvirtualTransport = HttpsProfileHttpTransport(),
        )

        assertEquals(
            listOf(
                JuntaTriPhaseAdapter::class.java,
                JuntaOfvirtualTriPhaseAdapter::class.java,
                LocalXadesDetachedAdapter::class.java,
                LocalCadesDetachedAdapter::class.java,
                DgtVerificationCadesAdapter::class.java,
                DiputacionLleidaCadesAdapter::class.java,
                DiputacionBadajozCadesAdapter::class.java,
                CanariasCertificateLoginCadesAdapter::class.java,
                UgrCadesDetachedAdapter::class.java,
                JccmCertificateLoginProbeCadesAdapter::class.java,
                MitesCertificateLoginCadesAdapter::class.java,
                CantabriaRecCertificateLoginCadesAdapter::class.java,
                GranCanariaPadesAdapter::class.java,
                FuerteventuraPadesAdapter::class.java,
                XuntaPadesTriPhaseAdapter::class.java,
                TransparenciaPadesAdapter::class.java,
                MinecoPadesAdapter::class.java,
                TenerifeCadesDetachedAdapter::class.java,
                EivissaCadesDetachedAdapter::class.java,
                SevillaAtseXadesEnvelopingAdapter::class.java,
                AirefXadesEnvelopingAdapter::class.java,
                CdtiXadesEnvelopingAdapter::class.java,
                TransportesXadesEnvelopedAdapter::class.java,
                PoliciaXadesDetachedAdapter::class.java,
                UnizarTriPhaseAdapter::class.java,
                AccedaPadesAdapter::class.java,
            ),
            adapters.all.map { it.javaClass },
        )
        adapters.all.forEach { adapter ->
            assertSame(adapter, adapters.resolve(adapter.id))
        }
    }

    @Test
    fun batchRegistryPreservesAdapterOrderAndResolverInstances() {
        val adapters = RuntimeSigningAdapters.batch()

        assertEquals(
            listOf(
                MelillaBatchProtocolAdapter::class.java,
                ExtremaduraBatchProtocolAdapter::class.java,
                LaPalmaBatchProtocolAdapter::class.java,
                HuescaBatchProtocolAdapter::class.java,
                LugoBatchProtocolAdapter::class.java,
                CaibBatchProtocolAdapter::class.java,
                BurgosBatchProtocolAdapter::class.java,
            ),
            adapters.all.map { it.javaClass },
        )
        adapters.all.forEach { adapter ->
            assertSame(adapter, adapters.resolve(adapter.id))
        }
    }
}
