package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.KeystorePlacement
import dev.junta.firmamobile.certificate.KeystoreRsaSigning
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.PrivateKey
import java.security.Provider
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** CMS signer for an RSA identity, or null for any other key type. */
internal fun UnlockedIdentity.rsaContentSigner(
    algorithm: SigningAlgorithm,
    provider: Provider,
    secureRandom: SecureRandom,
): ContentSigner? {
    val placement = keystorePlacement
    return withPrivateKey { privateKey ->
        when {
            !privateKey.algorithm.equals("RSA", ignoreCase = true) -> null
            // StrongBox may lack the requested digest; KeystoreRsaSigning picks
            // the DigestInfo route once, without a retry (ADR 0005).
            placement != null -> KeystoreContentSigner(privateKey, algorithm, placement)
            else -> JcaContentSignerBuilder(algorithm.jcaName())
                .apply {
                    // Software keys use the isolated bundled provider.
                    // Opaque KeyChain/Keystore handles must stay with
                    // their JCA provider; never export or retry them.
                    if (privateKey is RSAPrivateKey) setProvider(provider)
                }
                .setSecureRandom(secureRandom)
                .build(privateKey)
        }
    }
}

private class KeystoreContentSigner(
    private val key: PrivateKey,
    private val algorithm: SigningAlgorithm,
    private val placement: KeystorePlacement,
) : ContentSigner {
    private val signedAttributes = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier(): AlgorithmIdentifier =
        DefaultSignatureAlgorithmIdentifierFinder().find(algorithm.jcaName())

    override fun getOutputStream(): OutputStream = signedAttributes

    override fun getSignature(): ByteArray {
        val data = signedAttributes.toByteArray()
        signedAttributes.reset()
        return try {
            KeystoreRsaSigning.sign(key, algorithm, data, placement)
        } finally {
            data.fill(0)
        }
    }
}
