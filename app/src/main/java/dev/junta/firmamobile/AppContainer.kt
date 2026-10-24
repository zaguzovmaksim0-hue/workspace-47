package dev.junta.firmamobile

import android.app.Application
import dev.junta.firmamobile.certificate.CertificateModule
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.signing.SigningModule
import kotlinx.coroutines.CoroutineScope

/** Application-owned construction boundary; factories are invoked, not retained. */
internal class AppContainer(
    certificateModuleFactory: () -> CertificateModule,
) {
    val certificateModule: CertificateModule = certificateModuleFactory()

    /** The caller supplies its current session and retains ownership of its scope. */
    fun createSigningModule(
        certificateSession: CertificateSession,
        scope: CoroutineScope,
    ): SigningModule = SigningModule(certificateSession, scope)

    companion object {
        fun create(application: Application): AppContainer =
            AppContainer { CertificateModule.create(application) }
    }
}
