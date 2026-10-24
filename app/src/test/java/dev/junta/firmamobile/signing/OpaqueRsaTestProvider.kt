package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.KeystorePlacement
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.security.AlgorithmParameters
import java.security.InvalidKeyException
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.SignatureException
import java.security.SignatureSpi
import java.security.spec.AlgorithmParameterSpec
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** JVM-only opaque-handle contract. This is not a mock Android implementation
 * and is not proof of a physical KeyChain/TEE operation. The private material
 * is visible only to this synthetic provider, never to production signing code. */
internal class OpaqueRsaFixture private constructor(
    /** Signature names the handle accepts; null accepts every service. */
    private val authorized: Set<String>?,
    placement: KeystorePlacement?,
) {
    private val source = nonExportableSyntheticIdentity().identity
    /** The same key as an in-memory PKCS#12 identity, for byte comparisons. */
    val softwareIdentity: UnlockedIdentity get() = source
    val encodingReads = AtomicInteger()
    val formatReads = AtomicInteger()
    val initializations = AtomicInteger()
    val signatures = AtomicInteger()
    var failAtSign = false
    var corruptSignature = false
    val algorithms = mutableListOf<String>()
    private val handle = object : PrivateKey {
        override fun getAlgorithm() = "RSA"
        override fun getFormat(): String? { formatReads.incrementAndGet(); return null }
        override fun getEncoded(): ByteArray? { encodingReads.incrementAndGet(); throw IllegalStateException("Opaque key is not exportable") }
    }
    val identity = UnlockedIdentity(handle, source.certificate, source.chain, source.summary, placement)
    private val provider = object : Provider("FirmaOpaqueTest-" + UUID.randomUUID(), 1.0, "Isolated synthetic opaque RSA contract") {
        init {
            for (name in SigningAlgorithm.entries.map { it.jcaName() } + NONE_WITH_RSA) {
                putService(object : Service(this, "Signature", name, Spi::class.java.name, emptyList(), emptyMap()) {
                    override fun supportsParameter(parameter: Any?) = parameter === handle
                    override fun newInstance(parameter: Any?): Any = Spi(name)
                })
            }
        }
    }
    private inner class Spi(private val algorithm: String) : SignatureSpi() {
        private var delegate: Signature? = null
        override fun engineInitSign(privateKey: PrivateKey) {
            if (privateKey !== handle) throw InvalidKeyException("Wrong opaque handle")
            // Mirrors a keystore key whose authorizations lack this digest.
            if (authorized != null && algorithm !in authorized) throw InvalidKeyException("Not authorized: $algorithm")
            initializations.incrementAndGet(); algorithms.add(algorithm)
            delegate = source.withPrivateKey { actual ->
                Signature.getInstance(algorithm, BouncyCastleProvider()).apply { initSign(actual) }
            }
        }
        override fun engineInitVerify(publicKey: PublicKey) { throw InvalidKeyException("Signing-only test provider") }
        override fun engineUpdate(value: Byte) { checkNotNull(delegate).update(value) }
        override fun engineUpdate(bytes: ByteArray, offset: Int, length: Int) { checkNotNull(delegate).update(bytes, offset, length) }
        override fun engineSign(): ByteArray {
            signatures.incrementAndGet()
            if (failAtSign) throw SignatureException("Simulated provider refusal; must not retry")
            val signed = checkNotNull(delegate).sign()
            if (corruptSignature) signed[0] = (signed[0].toInt() xor 1).toByte()
            return signed
        }
        override fun engineVerify(signature: ByteArray): Boolean = false
        @Deprecated("Legacy JCA API") override fun engineSetParameter(param: String?, value: Any?) { throw UnsupportedOperationException() }
        @Deprecated("Legacy JCA API") override fun engineGetParameter(param: String?): Any? = throw UnsupportedOperationException()
        override fun engineSetParameter(params: AlgorithmParameterSpec?) { if (params != null) throw UnsupportedOperationException() }
        override fun engineGetParameters(): AlgorithmParameters? = null
    }
    companion object {
        private val lock = Any()
        const val NONE_WITH_RSA = "NONEwithRSA"
        /** Digests IKeyMintDevice guarantees for StrongBox RSA keys. */
        val STRONGBOX_SIGNATURES = setOf("SHA256withRSA", NONE_WITH_RSA)

        fun <T> use(block: (OpaqueRsaFixture) -> T): T = use(null, null, block)

        fun <T> use(
            authorized: Set<String>?,
            placement: KeystorePlacement?,
            block: (OpaqueRsaFixture) -> T,
        ): T = synchronized(lock) {
            val value = OpaqueRsaFixture(authorized, placement)
            val originalProviders = Security.getProviders().map { it.name }
            check(Security.insertProviderAt(value.provider, 1) == 1)
            try { block(value) }
            finally {
                Security.removeProvider(value.provider.name)
                check(Security.getProviders().map { it.name } == originalProviders)
            }
        }
    }
}
