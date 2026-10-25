package dev.junta.firmamobile.browser

import android.webkit.ClientCertRequest
import dev.junta.firmamobile.signing.issuedSyntheticIdentity
import dev.junta.firmamobile.signing.syntheticEcIdentity
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientCertificateEcRejectionTest {
    private val request = object : ClientCertRequest() {
        override fun getHost(): String = "portal.example"
        override fun getPort(): Int = 443
        override fun getKeyTypes(): Array<String> = arrayOf("RSA", "ECDSA", "EC")
        override fun getPrincipals(): Array<Principal> = emptyArray()
        override fun proceed(privateKey: PrivateKey, chain: Array<X509Certificate>) = error("not offered")
        override fun ignore() = Unit
        override fun cancel() = Unit
    }

    @Test
    fun ecIdentitiesAreNotOfferedEvenWhenTheProfileAndServerAllowEc() {
        for (curve in listOf("secp256r1", "secp384r1")) {
            val identity = syntheticEcIdentity(curve)
            assertEquals(
                curve,
                ClientAuthRequestDiagnostic.REJECTED_ALGORITHM,
                clientCertificateRejection(
                    request,
                    identity,
                    allowedKeyAlgorithms = setOf("RSA", "EC"),
                    clock = Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC),
                ),
            )
        }
    }

    @Test
    fun rsaControlIsStillAccepted() {
        assertNull(
            clientCertificateRejection(
                request,
                issuedSyntheticIdentity(),
                allowedKeyAlgorithms = setOf("RSA", "EC"),
                requireTlsClientAuthExtendedKeyUsage = false,
                clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
        )
    }
}
