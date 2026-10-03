package dev.junta.firmamobile

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.signing.*
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.certificate.CertificateSummary
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.security.auth.x500.X500Principal
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeRsaProviderInstrumentedTest {
    private val payload = "FirmaMobile synthetic RSA test payload".toByteArray(Charsets.UTF_8)

    @Test
    fun softwareRsaSignsAllAlgorithmsWithoutEncodingOrProviderChanges() {
        val providersBefore = Security.getProviders().map { it.name }
        val pair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        val provider = BouncyCastleProvider()
        val name = X500Principal("CN=FirmaMobile RSA Test")
        val now = Instant.now()
        val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(
            JcaX509v3CertificateBuilder(
                name, BigInteger.ONE, Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)), name, pair.public
            ).build(
                JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(provider).build(pair.private)
            )
        )
        val encodedReads = AtomicInteger()
        val delegate = pair.private as RSAPrivateKey
        val key = object : RSAPrivateKey {
            override fun getAlgorithm(): String = delegate.algorithm
            override fun getPrivateExponent(): BigInteger = delegate.privateExponent
            override fun getModulus(): BigInteger = delegate.modulus
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray {
                encodedReads.incrementAndGet()
                throw UnsupportedOperationException("Private key encoding is unavailable")
            }
        }
        val identity = identity(key, certificate)
        val algorithms = listOf(
            SigningAlgorithm.SHA1_WITH_RSA to "SHA1withRSA",
            SigningAlgorithm.SHA256_WITH_RSA to "SHA256withRSA",
            SigningAlgorithm.SHA384_WITH_RSA to "SHA384withRSA",
            SigningAlgorithm.SHA512_WITH_RSA to "SHA512withRSA"
        )
        for ((algorithm, jcaName) in algorithms) {
            assertValidSignature(identity, certificate, algorithm, jcaName)
            assertEquals("Private key encoding was accessed", 0, encodedReads.get())
            assertEquals(
                "Global security providers changed", providersBefore,
                Security.getProviders().map { it.name }
            )
        }
    }

    @Test
    fun opaqueAndroidKeyStoreRsaSignsSha256AndSha384() {
        val alias = "firmamobile-test-rsa-" + UUID.randomUUID()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse("Test alias must be unused", keyStore.containsAlias(alias))
        try {
            val pair = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
            ).apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
            }.generateKeyPair()
            val certificate = keyStore.getCertificate(alias) as X509Certificate
            assertFalse(
                "AndroidKeyStore private key must be opaque",
                pair.private is RSAPrivateKey
            )
            assertValidSignature(
                identity(pair.private, certificate), certificate,
                SigningAlgorithm.SHA256_WITH_RSA, "SHA256withRSA"
            )
            assertValidSignature(
                identity(pair.private, certificate), certificate,
                SigningAlgorithm.SHA384_WITH_RSA, "SHA384withRSA"
            )
        } finally {
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }

    private fun identity(key: PrivateKey, certificate: X509Certificate) = UnlockedIdentity(
        privateKey = key,
        certificate = certificate,
        chain = listOf(certificate),
        summary = CertificateSummary(
            ownerName = certificate.subjectX500Principal.name,
            issuerName = certificate.issuerX500Principal.name,
            validFrom = certificate.notBefore.toInstant(),
            validUntil = certificate.notAfter.toInstant()
        )
    )

    private fun assertValidSignature(
        identity: UnlockedIdentity,
        certificate: X509Certificate,
        algorithm: SigningAlgorithm,
        jcaName: String
    ) {
        val result = JcaLocalSignatureEngine().sign(payload, identity, algorithm)
        assertTrue("Signing must succeed for $algorithm", result is LocalSignatureResult.Success)
        (result as LocalSignatureResult.Success).signature.use { signature ->
            signature.withBytes { bytes ->
                val verifier = Signature.getInstance(jcaName)
                verifier.initVerify(certificate.publicKey)
                verifier.update(payload)
                assertTrue("Signature verification failed for $algorithm", verifier.verify(bytes))
            }
        }
    }
}
