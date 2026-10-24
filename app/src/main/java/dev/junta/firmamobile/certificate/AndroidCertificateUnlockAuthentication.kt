package dev.junta.firmamobile.certificate

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withResumed
import dev.junta.firmamobile.R
import java.lang.ref.WeakReference
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Activity-bound, per-operation authorization. No password or key is handed to the UI. */
class AndroidCertificateUnlockAuthentication {
    private var host = WeakReference<ComponentActivity>(null)
    @Volatile private var signal: CancellationSignal? = null
    val isPending: Boolean get() = signal != null

    fun bind(activity: ComponentActivity) {
        if (host.get() !== activity) signal?.cancel()
        host = WeakReference(activity)
    }

    fun unbind(activity: Activity) {
        if (host.get() === activity) {
            signal?.cancel()
            host.clear()
        }
    }

    suspend fun authenticateCipher(cipher: Cipher): Boolean = withContext(Dispatchers.Main.immediate) {
        val activity = host.get()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || activity == null) return@withContext false
        if (signal != null || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            return@withContext false
        }
        val cancellation = CancellationSignal()
        signal = cancellation
        try {
            withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MILLIS) {
                val authenticated = requestAuthentication(activity, cipher, cancellation)
                authenticated && activity.lifecycle.withResumed { host.get() === activity && !activity.isDestroyed }
            } ?: false
        } finally {
            cancellation.cancel()
            if (signal === cancellation) signal = null
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestAuthentication(
        activity: ComponentActivity,
        cipher: Cipher,
        cancellation: CancellationSignal,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancellation.cancel() }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.certificate_auth_title))
            .setSubtitle(activity.getString(R.string.certificate_auth_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(BiometricPrompt.CryptoObject(cipher), cancellation, activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(result.cryptoObject?.cipher === cipher)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
    }

    private companion object {
        const val AUTHENTICATION_TIMEOUT_MILLIS = 60_000L
    }
}
