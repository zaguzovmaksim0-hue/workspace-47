package dev.junta.firmamobile.network

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Clock
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
        return checkedServerChain(chain, authType, null, null).toList()
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        checkedServerChain(chain, authType, null, null)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        checkedServerChain(chain, authType, socket, null)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        checkedServerChain(chain, authType, null, engine)
    }

    private fun checkedServerChain(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
        engine: SSLEngine?,
    ): Array<X509Certificate> {
        try {
            delegateServerCheck(chain, authType, socket, engine)
            return chain
        } catch (original: CertificateException) {
            return completeChain(chain, authType, socket, engine) ?: throw original
        }
    }

    private fun delegateServerCheck(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
        engine: SSLEngine?,
    ) {
        when {
            socket != null -> delegate.checkServerTrusted(chain, authType, socket)
            engine != null -> delegate.checkServerTrusted(chain, authType, engine)
            else -> delegate.checkServerTrusted(chain, authType)
        }
    }

    private fun completeChain(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
        engine: SSLEngine?,
    ): Array<X509Certificate>? {
        val leaf = chain.firstOrNull() ?: return null
        if (chain.size > MAX_CHAIN_LENGTH || bundledIntermediates.size > MAX_CANDIDATES) return null
        val candidates = (chain.drop(1) + bundledIntermediates).distinct()
        val pending = ArrayDeque<List<X509Certificate>>()
        pending.add(listOf(leaf))
        var attempts = 0
        while (pending.isNotEmpty() && attempts < MAX_ATTEMPTS) {
            val path = pending.removeFirst()
            if (path.size >= MAX_CHAIN_LENGTH) continue
            for (issuer in candidates) {
                if (issuer in path || !isIssuer(path.last(), issuer)) continue
                if (++attempts > MAX_ATTEMPTS) return null
                val completed = (path + issuer).toTypedArray()
                try {
                    // A bundled certificate supplies a missing link only. The
                    // platform still decides trust, validity and constraints.
                    delegateServerCheck(completed, authType, socket, engine)
                    return completed
                } catch (_: CertificateException) {
                    pending.add(completed.toList())
                }
            }
        }
        return null
    }

    private fun isIssuer(child: X509Certificate, issuer: X509Certificate): Boolean {
        val now = clock.instant()
        return issuer.subjectX500Principal == child.issuerX500Principal &&
            issuer.basicConstraints >= 0 &&
            (issuer.keyUsage?.getOrElse(KEY_CERT_SIGN_BIT) { false } != false) &&
            issuer.notBefore.toInstant() <= now && now <= issuer.notAfter.toInstant() &&
            runCatching { child.verify(issuer.publicKey) }.isSuccess
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
        const val MAX_CHAIN_LENGTH = 10
        const val MAX_CANDIDATES = 32
        const val MAX_ATTEMPTS = 64
        const val KEY_CERT_SIGN_BIT = 5

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
