package dev.junta.firmamobile.network

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLEngine
import java.net.Socket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.Assert.assertThrows
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class ChainCompletingTrustManagerTest {
    private val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `leaf without intermediate is rejected`() {
        val pki = Pki()
        val manager = manager(pki, bundled = emptyList())
        assertThrows(CertificateException::class.java) {
            manager.checkServerTrusted(arrayOf(pki.leaf), "RSA")
        }
    }

    @Test
    fun `bundled intermediate completes leaf chain`() {
        val pki = Pki()
        val manager = manager(pki, bundled = listOf(pki.intermediate))
        manager.checkServerTrusted(arrayOf(pki.leaf), "RSA")
    }

    @Test
    fun `bundled intermediate completes chain with unrelated server certificate`() {
        val pki = Pki()
        val manager = manager(pki, bundled = listOf(pki.intermediate))
        manager.checkServerTrusted(arrayOf(pki.leaf, pki.root), "RSA")
    }

    @Test
    fun `server supplied intermediate is delegated without substitution`() {
        val pki = Pki()
        val counting = CountingTrustManager(manager(pki, bundled = listOf(pki.intermediate)))
        counting.checkServerTrusted(arrayOf(pki.leaf, pki.intermediate), "RSA")
        assertEquals(1, counting.serverCalls.get())
    }

    @Test
    fun `intermediate with same subject but different key is rejected`() {
        val pki = Pki()
        val impostor = pki.impostorIntermediate()
        val manager = manager(pki, bundled = listOf(impostor))
        assertThrows(CertificateException::class.java) {
            manager.checkServerTrusted(arrayOf(pki.leaf), "RSA")
        }
    }

    @Test
    fun `expired bundled intermediate is rejected`() {
        val pki = Pki()
        val lateClock = Clock.fixed(pki.intermediate.notAfter.toInstant().plusSeconds(1), ZoneOffset.UTC)
        val manager = ChainCompletingTrustManager(
            delegate = delegate(pki.root),
            bundledIntermediates = listOf(pki.intermediate),
            clock = lateClock,
        )
        assertThrows(CertificateException::class.java) {
            manager.checkServerTrusted(arrayOf(pki.leaf), "RSA")
        }
    }

    @Test
    fun `original exception is preserved when completion is impossible`() {
        val pki = Pki()
        val original = object : CertificateException("original failure") {}
        val failing = object : X509ExtendedTrustManager() {
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = throw original
            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String,
                socket: Socket?,
            ) = throw original
            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String,
                engine: SSLEngine?,
            ) = throw original
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw original
            override fun checkClientTrusted(
                chain: Array<X509Certificate>,
                authType: String,
                socket: Socket?,
            ) = throw original
            override fun checkClientTrusted(
                chain: Array<X509Certificate>,
                authType: String,
                engine: SSLEngine?,
            ) = throw original
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val manager = ChainCompletingTrustManager(
            delegate = failing,
            bundledIntermediates = listOf(pki.intermediate),
            clock = clock,
        )
        val thrown = assertThrows(CertificateException::class.java) {
            manager.checkServerTrusted(arrayOf(pki.leaf), "RSA")
        }
        assertSame(original, thrown)
    }

    private fun manager(pki: Pki, bundled: List<X509Certificate>): ChainCompletingTrustManager =
        ChainCompletingTrustManager(delegate(pki.root), bundled, clock)

    private fun delegate(root: X509Certificate): X509ExtendedTrustManager {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("test-root", root)
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()
    }

    private class CountingTrustManager(private val delegate: X509ExtendedTrustManager) : X509ExtendedTrustManager() {
        val serverCalls = AtomicInteger()

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            serverCalls.incrementAndGet()
            delegate.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
            serverCalls.incrementAndGet()
            delegate.checkServerTrusted(chain, authType, socket)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            serverCalls.incrementAndGet()
            delegate.checkServerTrusted(chain, authType, engine)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            delegate.checkClientTrusted(chain, authType)
        override fun checkClientTrusted(
            chain: Array<X509Certificate>,
            authType: String,
            socket: Socket?,
        ) = delegate.checkClientTrusted(chain, authType, socket)
        override fun checkClientTrusted(
            chain: Array<X509Certificate>,
            authType: String,
            engine: SSLEngine?,
        ) = delegate.checkClientTrusted(chain, authType, engine)
        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }

    private class Pki {
        val rootKey = keyPair()
        val intermediateKey = keyPair()
        val leafKey = keyPair()
        val impostorKey = keyPair()
        val rootName = X500Name("CN=Test Root,O=Chain Tests,C=ES")
        val intermediateName = X500Name("CN=Test Intermediate,O=Chain Tests,C=ES")
        val leafName = X500Name("CN=Test Leaf,O=Chain Tests,C=ES")

        val root = certificate(rootName, rootKey, rootName, rootKey, ca = true)
        val intermediate = certificate(intermediateName, intermediateKey, rootName, rootKey, ca = true)
        val leaf = certificate(leafName, leafKey, intermediateName, intermediateKey, ca = false)

        fun impostorIntermediate(): X509Certificate =
            certificate(intermediateName, impostorKey, rootName, rootKey, ca = true)

        private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()

        private fun certificate(
            subject: X500Name,
            subjectKey: KeyPair,
            issuer: X500Name,
            issuerKey: KeyPair,
            ca: Boolean,
        ): X509Certificate {
            val builder = JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.parse("2020-01-01T00:00:00Z")),
                Date.from(Instant.parse("2040-01-01T00:00:00Z")),
                subject,
                subjectKey.public,
            )
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
            builder.addExtension(
                Extension.keyUsage,
                true,
                if (ca) KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign) else KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(issuerKey.private)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }
    }
}
