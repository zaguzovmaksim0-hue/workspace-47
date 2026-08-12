package dev.junta.firmamobile

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.certificate.CertificateErrorCode
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.CertificateSelectionErrorCode
import dev.junta.firmamobile.certificate.CertificateSelectionResult
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.StoredCertificateReference

internal class TestCertificateDependencies private constructor(
    private val application: JuntaFirmaApplication,
    private val previousGateway: CertificateGateway,
    private val previousSession: CertificateSession,
) : AutoCloseable {
    override fun close() {
        application.certificateSession.forget()
        application.certificateGateway = previousGateway
        application.certificateSession = previousSession
    }

    companion object {
        fun install(
            gateway: CertificateGateway = EmptyCertificateGateway(),
            session: CertificateSession = CertificateSession(),
        ): TestCertificateDependencies {
            val application = InstrumentationRegistry.getInstrumentation()
                .targetContext
                .applicationContext as JuntaFirmaApplication
            val override = TestCertificateDependencies(
                application = application,
                previousGateway = application.certificateGateway,
                previousSession = application.certificateSession,
            )
            application.certificateGateway = gateway
            application.certificateSession = session
            return override
        }
    }
}

private class EmptyCertificateGateway : CertificateGateway {
    override suspend fun currentReference(): StoredCertificateReference? = null

    override suspend fun select(uri: Uri): CertificateSelectionResult =
        CertificateSelectionResult.Failure(CertificateSelectionErrorCode.DOCUMENT_UNAVAILABLE)

    override suspend fun unlock(password: CharArray): CertificateLoadResult =
        CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)

    override suspend fun forget() = Unit
}
