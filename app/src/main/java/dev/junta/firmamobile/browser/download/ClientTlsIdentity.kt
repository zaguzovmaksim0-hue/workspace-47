package dev.junta.firmamobile.browser.download

import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/** The unlocked certificate of the dedicated certificate session, offered only
 * to the exact host that requested it. It is read when the request is made, so
 * a locked session fails instead of using a stale key. */
internal class ClientTlsIdentity(host: String, private val provider: () -> UnlockedIdentity?) {
    val host: String = host.lowercase(Locale.ROOT)
    fun appliesTo(candidate: String) = candidate.lowercase(Locale.ROOT) == host
    fun keyManager(): X509ExtendedKeyManager? {
        val identity = provider() ?: return null
        val chain = identity.chain.ifEmpty { listOf(identity.certificate) }.toTypedArray()
        return identity.withPrivateKey { key ->
            check(key.algorithm.equals(identity.certificate.publicKey.algorithm, ignoreCase = true))
            SingleIdentityKeyManager(key, chain)
        }
    }
}

private class SingleIdentityKeyManager(private val key: PrivateKey, private val chain: Array<X509Certificate>) : X509ExtendedKeyManager() {
    private fun alias(keyTypes: Array<out String>?) =
        ALIAS.takeIf { keyTypes == null || keyTypes.any { it.equals(key.algorithm, ignoreCase = true) } }
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias(keyType)
    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = alias(keyType)
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) =
        alias(keyType?.let { arrayOf(it) })?.let { arrayOf(it) }
    override fun getCertificateChain(alias: String?) = if (alias == ALIAS) chain.clone() else null
    override fun getPrivateKey(alias: String?) = if (alias == ALIAS) key else null
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    private companion object { const val ALIAS = "firma-mobile-client" }
}
