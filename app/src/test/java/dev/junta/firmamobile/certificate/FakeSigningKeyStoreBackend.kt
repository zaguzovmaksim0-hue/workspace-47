package dev.junta.firmamobile.certificate

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.jcaName
import java.security.AlgorithmParameters
import java.security.InvalidKeyException
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureSpi
import java.security.cert.X509Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.UUID
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Opaque keystore handle: material stays inside the synthetic provider, like AndroidKeyStore. */
internal class HardwareKey(val material: PrivateKey, val algorithms: Set<String>) : PrivateKey {
    override fun getAlgorithm() = "RSA"
    override fun getFormat(): String? = null
    override fun getEncoded(): ByteArray? = null
}

internal class FakeSigningKeyStoreBackend(
    override val strongBoxAvailable: Boolean,
    private val strongBoxRejects: Boolean = false,
    private val strongBoxAlgorithms: Set<String> = setOf("SHA256withRSA", "NONEwithRSA"),
    private val exportable: Boolean = false,
    private val defaultLevel: KeystoreSecurityLevel = KeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
    private val substituteKey: Boolean = false,
) : SigningKeyStoreBackend {
    val keys = linkedMapOf<String, PrivateKey>()
    val chains = mutableMapOf<String, List<X509Certificate>>()
    val levels = mutableMapOf<String, KeystoreSecurityLevel>()
    val placements = mutableListOf<KeystorePlacement>()
    var failDelete = false
    val deleted = mutableListOf<String>()

    override fun importKey(alias: String, key: PrivateKey, chain: List<X509Certificate>, placement: KeystorePlacement) {
        placements += placement
        chains[alias] = chain
        if (placement == KeystorePlacement.STRONGBOX && strongBoxRejects) throw StrongBoxRejectedException()
        val material = if (substituteKey) {
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        } else {
            key
        }
        keys[alias] = when {
            exportable -> material
            placement == KeystorePlacement.STRONGBOX -> HardwareKey(material, strongBoxAlgorithms)
            else -> HardwareKey(material, SigningAlgorithm.entries.map { it.jcaName() }.toSet() + "NONEwithRSA")
        }
        levels[alias] = if (placement == KeystorePlacement.STRONGBOX) KeystoreSecurityLevel.STRONGBOX else defaultLevel
    }

    override fun certificateChain(alias: String) = chains[alias].orEmpty()
    override fun aliases() = keys.keys.toSet()
    override fun privateKey(alias: String) = keys[alias]
    override fun securityLevel(alias: String) = levels.getValue(alias)
    override fun delete(alias: String) {
        if (failDelete) throw java.io.IOException("Synthetic keystore delete failure")
        deleted += alias
        keys.remove(alias)
    }
}

internal class HardwareKeyProvider : Provider("JfmHardwareKeyTest-" + UUID.randomUUID(), 1.0, "Synthetic keystore") {
    val usedAlgorithms = mutableListOf<String>()

    init {
        for (name in SigningAlgorithm.entries.map { it.jcaName() } + "NONEwithRSA") {
            putService(object : Service(this, "Signature", name, Spi::class.java.name, emptyList(), emptyMap()) {
                override fun supportsParameter(parameter: Any?) = parameter is HardwareKey
                override fun newInstance(parameter: Any?): Any = Spi(name)
            })
        }
    }

    private inner class Spi(private val algorithm: String) : SignatureSpi() {
        private var delegate: Signature? = null

        override fun engineInitSign(privateKey: PrivateKey) {
            val key = privateKey as? HardwareKey ?: throw InvalidKeyException("Not a hardware handle")
            // Mirrors a keystore key whose authorizations lack this digest.
            if (algorithm !in key.algorithms) throw InvalidKeyException("Digest not authorized: $algorithm")
            usedAlgorithms += algorithm
            delegate = Signature.getInstance(algorithm, BouncyCastleProvider()).apply { initSign(key.material) }
        }

        override fun engineInitVerify(publicKey: PublicKey) {
            throw InvalidKeyException("Signing-only test provider")
        }
        override fun engineUpdate(value: Byte) { checkNotNull(delegate).update(value) }
        override fun engineUpdate(bytes: ByteArray, offset: Int, length: Int) {
            checkNotNull(delegate).update(bytes, offset, length)
        }
        override fun engineSign(): ByteArray = checkNotNull(delegate).sign()
        override fun engineVerify(signature: ByteArray): Boolean = false
        @Deprecated("Legacy JCA API")
        override fun engineSetParameter(param: String?, value: Any?) { throw UnsupportedOperationException() }
        @Deprecated("Legacy JCA API")
        override fun engineGetParameter(param: String?): Any? = throw UnsupportedOperationException()
        override fun engineSetParameter(params: AlgorithmParameterSpec?) {
            if (params != null) throw UnsupportedOperationException()
        }
        override fun engineGetParameters(): AlgorithmParameters? = null
    }
}
