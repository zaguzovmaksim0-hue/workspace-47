package dev.junta.firmamobile.certificate

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Provider
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

internal object TestCertificateFactory {
    val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
    private val defaultNotBefore = now.minusSeconds(86_400)
    private val defaultNotAfter = now.plusSeconds(86_400 * 365)
    private val serials = AtomicLong(1)
    private val provider: Provider = BouncyCastleProvider()
    private val random = SecureRandom()

    private val validIdentity by lazy {
        identity(
            commonName = "Persona de Prueba",
            algorithm = "RSA",
            notBefore = defaultNotBefore,
            notAfter = defaultNotAfter,
            keyUsage = KeyUsage.digitalSignature,
        )
    }

    fun password(): CharArray = "test-password-123".toCharArray()

    fun validRsa(): ByteArray = store {
        setKeyEntry(
            "identidad",
            validIdentity.keyPair.private,
            password(),
            arrayOf(validIdentity.certificate),
        )
    }

    fun freshValidRsa(): ByteArray {
        val identity = identity(
            commonName = "Persona de Prueba",
            algorithm = "RSA",
            notBefore = defaultNotBefore,
            notAfter = defaultNotAfter,
            keyUsage = KeyUsage.digitalSignature,
        )
        return identityStore("fresh-identidad", identity)
    }

    fun issuedRsa(): ByteArray {
        val issuerKeyPair = generateKeyPair("RSA")
        val issuerName = X500Name("CN=CA de Prueba,O=Junta Firma Mobile Tests,C=ES")
        val issuerCertificate = certificate(
            subject = issuerName,
            subjectKeyPair = issuerKeyPair,
            issuer = issuerName,
            issuerKeyPair = issuerKeyPair,
            notBefore = defaultNotBefore,
            notAfter = defaultNotAfter,
            keyUsage = KeyUsage.keyCertSign or KeyUsage.cRLSign,
            isCertificateAuthority = true,
        )
        val leafKeyPair = generateKeyPair("RSA")
        val leafName = X500Name("CN=Persona Emitida,O=Junta Firma Mobile Tests,C=ES")
        val leafCertificate = certificate(
            subject = leafName,
            subjectKeyPair = leafKeyPair,
            issuer = issuerName,
            issuerKeyPair = issuerKeyPair,
            notBefore = defaultNotBefore,
            notAfter = defaultNotAfter,
            keyUsage = KeyUsage.digitalSignature,
            isCertificateAuthority = false,
        )
        return store {
            setKeyEntry(
                "issued-identidad",
                leafKeyPair.private,
                password(),
                arrayOf(leafCertificate, issuerCertificate),
            )
        }
    }

    fun certificateOnly(): ByteArray = store {
        setCertificateEntry("certificado", validIdentity.certificate)
    }

    fun ecIdentity(): ByteArray {
        val identity = identity(
            commonName = "Identidad EC",
            algorithm = "EC",
            notBefore = defaultNotBefore,
            notAfter = defaultNotAfter,
            keyUsage = KeyUsage.digitalSignature,
        )
        return store {
            setKeyEntry(
                "ec",
                identity.keyPair.private,
                password(),
                arrayOf(identity.certificate),
            )
        }
    }

    fun expired(): ByteArray {
        val identity = identity(
            commonName = "Certificado Caducado",
            algorithm = "RSA",
            notBefore = now.minusSeconds(86_400 * 365),
            notAfter = now.minusSeconds(1),
            keyUsage = KeyUsage.digitalSignature,
        )
        return identityStore("expired", identity)
    }

    fun notYetValid(): ByteArray {
        val identity = identity(
            commonName = "Certificado Futuro",
            algorithm = "RSA",
            notBefore = now.plusSeconds(1),
            notAfter = now.plusSeconds(86_400 * 365),
            keyUsage = KeyUsage.digitalSignature,
        )
        return identityStore("future", identity)
    }

    fun withoutDigitalSignatureUsage(): ByteArray {
        val identity = identity(
            commonName = "Sin Firma Digital",
            algorithm = "RSA",
            notBefore = defaultNotBefore,
            notAfter = defaultNotAfter,
            keyUsage = KeyUsage.keyEncipherment,
        )
        return identityStore("no-sign", identity)
    }

    fun multiplePrivateEntries(): ByteArray {
        val second = identity(
            commonName = "Segunda Persona",
            algorithm = "RSA",
            notBefore = defaultNotBefore,
            notAfter = defaultNotAfter,
            keyUsage = KeyUsage.digitalSignature,
        )
        return store {
            setKeyEntry(
                "first",
                validIdentity.keyPair.private,
                password(),
                arrayOf(validIdentity.certificate),
            )
            setKeyEntry(
                "second",
                second.keyPair.private,
                password(),
                arrayOf(second.certificate),
            )
        }
    }

    fun tooManyAliases(): ByteArray = store {
        repeat(33) { index ->
            setCertificateEntry("certificate-$index", validIdentity.certificate)
        }
    }

    fun oversizedChain(): ByteArray = store {
        val keyPairs = List(17) { generateKeyPair("RSA", rsaBits = 1024) }
        val names = List(17) { index ->
            X500Name("CN=Chain Certificate $index,O=Junta Firma Mobile Tests,C=ES")
        }
        val certificates = MutableList<X509Certificate?>(17) { null }
        for (index in 16 downTo 0) {
            val issuerIndex = if (index == 16) index else index + 1
            certificates[index] = certificate(
                subject = names[index],
                subjectKeyPair = keyPairs[index],
                issuer = names[issuerIndex],
                issuerKeyPair = keyPairs[issuerIndex],
                notBefore = defaultNotBefore,
                notAfter = defaultNotAfter,
                keyUsage = if (index == 0) {
                    KeyUsage.digitalSignature
                } else {
                    KeyUsage.keyCertSign or KeyUsage.cRLSign
                },
                isCertificateAuthority = index != 0,
            )
        }
        setKeyEntry(
            "long-chain",
            keyPairs.first().private,
            password(),
            certificates.map { checkNotNull(it) }.toTypedArray(),
        )
    }

    fun mismatchedKeyAndCertificate(): ByteArray {
        val other = identity(
            commonName = "Otra Persona",
            algorithm = "RSA",
            notBefore = defaultNotBefore,
            notAfter = defaultNotAfter,
            keyUsage = KeyUsage.digitalSignature,
        )
        return store {
            setKeyEntry(
                "mismatch",
                validIdentity.keyPair.private,
                password(),
                arrayOf(other.certificate),
            )
        }
    }

    private fun identityStore(alias: String, identity: TestIdentity): ByteArray = store {
        setKeyEntry(
            alias,
            identity.keyPair.private,
            password(),
            arrayOf(identity.certificate),
        )
    }

    private fun store(configure: KeyStore.() -> Unit): ByteArray {
        val password = password()
        return try {
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(null, password)
            keyStore.configure()
            ByteArrayOutputStream().use { output ->
                keyStore.store(output, password)
                output.toByteArray()
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun identity(
        commonName: String,
        algorithm: String,
        notBefore: Instant,
        notAfter: Instant,
        keyUsage: Int,
    ): TestIdentity {
        val keyPair = generateKeyPair(algorithm)
        val subject = X500Name("CN=$commonName,O=Junta Firma Mobile Tests,C=ES")
        val certificate = certificate(
            subject = subject,
            subjectKeyPair = keyPair,
            issuer = subject,
            issuerKeyPair = keyPair,
            notBefore = notBefore,
            notAfter = notAfter,
            keyUsage = keyUsage,
            isCertificateAuthority = false,
        )
        return TestIdentity(keyPair, certificate)
    }

    private fun certificate(
        subject: X500Name,
        subjectKeyPair: KeyPair,
        issuer: X500Name,
        issuerKeyPair: KeyPair,
        notBefore: Instant,
        notAfter: Instant,
        keyUsage: Int,
        isCertificateAuthority: Boolean,
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(serials.getAndIncrement()),
            Date.from(notBefore),
            Date.from(notAfter),
            subject,
            subjectKeyPair.public,
        )
        builder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(isCertificateAuthority),
        )
        builder.addExtension(Extension.keyUsage, true, KeyUsage(keyUsage))
        val signatureAlgorithm = if (issuerKeyPair.private.algorithm == "EC") {
            "SHA256withECDSA"
        } else {
            "SHA256withRSA"
        }
        val signer = JcaContentSignerBuilder(signatureAlgorithm)
            .setProvider(provider)
            .build(issuerKeyPair.private)
        val certificate = JcaX509CertificateConverter()
            .setProvider(provider)
            .getCertificate(builder.build(signer))
        certificate.verify(issuerKeyPair.public, provider)
        return certificate
    }

    private fun generateKeyPair(algorithm: String, rsaBits: Int = 2048): KeyPair {
        val generator = KeyPairGenerator.getInstance(algorithm)
        if (algorithm == "EC") {
            generator.initialize(256, random)
        } else {
            generator.initialize(rsaBits, random)
        }
        return generator.generateKeyPair()
    }

    private data class TestIdentity(
        val keyPair: KeyPair,
        val certificate: X509Certificate,
    )
}
