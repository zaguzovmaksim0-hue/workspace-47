package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.certificate.TestCertificateFactory
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.interfaces.RSAPrivateKey
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

internal data class NonExportableSyntheticIdentity(
    val identity: UnlockedIdentity,
    val encodedReads: AtomicInteger,
)

internal fun syntheticIdentity(): UnlockedIdentity = runBlocking {
    loadSyntheticIdentity(TestCertificateFactory.validRsa())
}

internal fun freshSyntheticIdentity(): UnlockedIdentity = runBlocking {
    loadSyntheticIdentity(TestCertificateFactory.freshValidRsa())
}

internal fun issuedSyntheticIdentity(): UnlockedIdentity = runBlocking {
    loadSyntheticIdentity(TestCertificateFactory.issuedRsa())
}

private suspend fun loadSyntheticIdentity(bytes: ByteArray): UnlockedIdentity {
    val password = TestCertificateFactory.password()
    return try {
        val result = Pkcs12Loader(
            clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
        ).load(
            input = ByteArrayInputStream(bytes),
            declaredSize = bytes.size.toLong(),
            password = password,
        )
        (result as CertificateLoadResult.Success).identity
    } finally {
        bytes.fill(0)
        password.fill('\u0000')
    }
}

internal fun nonExportableSyntheticIdentity(): NonExportableSyntheticIdentity {
    val source = syntheticIdentity()
    val encodedReads = AtomicInteger()
    val wrapped = source.withPrivateKey { privateKey ->
        val rsaKey = privateKey as RSAPrivateKey
        UnlockedIdentity(
            privateKey = NonExportableRsaPrivateKey(rsaKey, encodedReads),
            certificate = source.certificate,
            chain = source.chain,
            summary = source.summary,
        )
    }
    return NonExportableSyntheticIdentity(wrapped, encodedReads)
}

private class NonExportableRsaPrivateKey(
    private val delegate: RSAPrivateKey,
    private val encodedReads: AtomicInteger,
) : RSAPrivateKey {
    override fun getAlgorithm(): String = delegate.algorithm

    override fun getFormat(): String? = null

    override fun getEncoded(): ByteArray {
        encodedReads.incrementAndGet()
        error("Private key encoding must never be read")
    }

    override fun getModulus(): BigInteger = delegate.modulus

    override fun getPrivateExponent(): BigInteger = delegate.privateExponent
}
