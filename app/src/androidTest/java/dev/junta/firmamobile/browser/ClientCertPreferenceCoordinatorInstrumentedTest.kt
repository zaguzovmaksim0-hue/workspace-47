package dev.junta.firmamobile.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientCertPreferenceCoordinatorInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun platformPreferenceClearCompletesWithoutOpeningAWebView() {
        val callback = CountDownLatch(1)
        val result = AtomicReference<ClientCertPreferenceClearResult>()
        lateinit var coordinator: ClientCertPreferenceCoordinator

        instrumentation.runOnMainSync {
            coordinator = ClientCertPreferenceCoordinator()
            coordinator.requestClear { _, completed ->
                result.set(completed)
                callback.countDown()
            }
            assertTrue(
                coordinator.state.value == ClientCertPreferenceBarrierState.CLEARING ||
                    coordinator.state.value == ClientCertPreferenceBarrierState.IDLE,
            )
        }

        assertTrue(callback.await(10, TimeUnit.SECONDS))
        assertEquals(ClientCertPreferenceClearResult.CLEARED, result.get())
        assertEquals(ClientCertPreferenceBarrierState.IDLE, coordinator.state.value)
    }
}
