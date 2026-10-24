package dev.junta.firmamobile.certificate

import androidx.activity.ComponentActivity
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidCertificateUnlockAuthenticationTest {
    @Test fun unboundControllerFailsClosed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val controller = AndroidCertificateUnlockAuthentication()
            assertFalse(controller.authenticateCipher(Cipher.getInstance("AES/GCM/NoPadding")))
            assertFalse(controller.isPending)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun inactiveOrUnboundHostCannotAuthorizeACipher() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val host = Robolectric.buildActivity(ComponentActivity::class.java).create()
        try {
            val controller = AndroidCertificateUnlockAuthentication()
            controller.bind(host.get())
            assertFalse(controller.authenticateCipher(Cipher.getInstance("AES/GCM/NoPadding")))
            controller.unbind(host.get())
            assertFalse(controller.authenticateCipher(Cipher.getInstance("AES/GCM/NoPadding")))
            assertFalse(controller.isPending)
        } finally { host.destroy(); Dispatchers.resetMain() }
    }
}
