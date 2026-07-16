package dev.junta.firmamobile.signing

import org.junit.Assert.assertTrue
import org.junit.Test

class SigningProtocolAdapterContractTest {
    @Test
    fun adapterContractExposesNoCertificateSessionPrivateKeyPasswordOrWebView() {
        val surfaces = listOf(
            SigningProtocolAdapter::class.java,
            TriPhaseExecutionAdapter::class.java,
            TriPhaseProtocolCodec::class.java,
            TriPhaseDecodedRequest::class.java,
            InterceptedSigningInput::class.java,
            NormalizedSignRequest::class.java,
            ProtocolCompletionResult::class.java,
        ).flatMap { type ->
            type.declaredMethods.map { it.toGenericString() } +
                type.declaredFields.map { it.toGenericString() }
        }

        val forbidden = listOf(
            "PrivateKey",
            "CertificateSession",
            "UnlockedIdentity",
            "CharArray",
            "password",
            "WebView",
            "Cookie",
        )
        assertTrue(surfaces.none { surface -> forbidden.any(surface::contains) })
    }
}
