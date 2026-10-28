package dev.junta.firmamobile.network

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLContext
import java.net.Socket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/** Completes a server chain with a narrowly matched bundled intermediate.
 * Trust is still established exclusively by [delegate]; hostname checking
 * remains with OkHttp.
 */
internal class ChainCompletingTrustManager(
    private val delegate: X509ExtendedTrustManager = defaultTrustManager(),
    private val bundledIntermediates: List<X509Certificate> = BundledIntermediates.certificates,
    private val clock: Clock = Clock.systemUTC(),
) : X509ExtendedTrustManager() {

    fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, host: String): List<X509Certificate> {
        // OkHttp owns hostname verification; this only rejects an absent caller-supplied host.
        require(host.isNotBlank())
        checkServerTrusted(chain, authType)
        return chain.toList()
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        checkServerTrusted(chain, authType, null, null)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        checkServerTrusted(chain, authType, socket, null)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        checkServerTrusted(chain, authType, null, engine)
    }

    private fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
        engine: SSLEngine?,
    ) {
        try {
            when {
                socket != null -> delegate.checkServerTrusted(chain, authType, socket)
                engine != null -> delegate.checkServerTrusted(chain, authType, engine)
                else -> delegate.checkServerTrusted(chain, authType)
            }
            return
        } catch (original: CertificateException) {
            if (!completeChain(chain, authType, socket, engine)) throw original
        }
    }

    private fun completeChain(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
        engine: SSLEngine?,
    ): Boolean {
        val leaf = chain.firstOrNull() ?: return false
        val now: Instant = clock.instant()
        return bundledIntermediates.any { intermediate ->
            intermediate.subjectX500Principal == leaf.issuerX500Principal &&
                (intermediate.notBefore.toInstant() <= now && now <= intermediate.notAfter.toInstant()) &&
                runCatching { leaf.verify(intermediate.publicKey) }.isSuccess &&
                runCatching {
                    val completed = chain + intermediate
                    when {
                        socket != null -> delegate.checkServerTrusted(completed, authType, socket)
                        engine != null -> delegate.checkServerTrusted(completed, authType, engine)
                        else -> delegate.checkServerTrusted(completed, authType)
                    }
                }.isSuccess
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        delegate.checkClientTrusted(chain, authType, socket)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        delegate.checkClientTrusted(chain, authType, engine)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    private companion object {
        fun defaultTrustManager(): X509ExtendedTrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers
                .filterIsInstance<X509ExtendedTrustManager>()
                .firstOrNull()
                ?: error("No X509ExtendedTrustManager is available")
        }
    }
}

internal val chainCompletingTrustManager: ChainCompletingTrustManager by lazy {
    ChainCompletingTrustManager()
}

internal val chainCompletingSslContext: SSLContext by lazy {
    SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(chainCompletingTrustManager), null)
    }
}
