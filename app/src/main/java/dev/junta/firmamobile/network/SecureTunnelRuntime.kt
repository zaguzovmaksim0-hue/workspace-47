package dev.junta.firmamobile.network

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import java.net.IDN
import java.net.URI
import java.util.Locale

internal interface SecureTunnelRuntime {
    fun transportFor(
        profileId: ProfileId,
        endpoint: URI,
        observer: TunnelRouteObserver,
    ): ProfileHttpTransport
}

internal fun interface ProfileHttpTransportFactory {
    fun create(profileId: ProfileId, endpoint: URI): ProfileHttpTransport
}

internal data class SecureTunnelPublicConfig(
    val enabled: Boolean,
    val relayHost: String,
    val relayPort: Int,
    val relaySpkiPins: Set<String>,
)

internal class DirectOnlyTunnelRuntime(
    private val directTransportFactory: ProfileHttpTransportFactory = DefaultDirectTransportFactory,
) : SecureTunnelRuntime {
    override fun transportFor(
        profileId: ProfileId,
        endpoint: URI,
        observer: TunnelRouteObserver,
    ): ProfileHttpTransport = directTransportFactory.create(profileId, endpoint)
}

internal class QaSecureTunnelRuntime internal constructor(
    relay: SecureTunnelRelay,
    private val credentialProvider: TunnelCredentialProvider,
    private val directTransportFactory: ProfileHttpTransportFactory = DefaultDirectTransportFactory,
) : SecureTunnelRuntime {
    private val relay = relay.copy(spkiPins = relay.spkiPins.toSet())

    override fun transportFor(
        profileId: ProfileId,
        endpoint: URI,
        observer: TunnelRouteObserver,
    ): ProfileHttpTransport {
        val direct = directTransportFactory.create(profileId, endpoint)
        if (profileId != OFVIRTUAL_PROFILE || endpoint != OFVIRTUAL_ENDPOINT) {
            return direct
        }
        val socketFactoryProvider = TunnelSocketFactoryProvider { expectedHost, approvedAddresses, cancellation ->
            SecureTunnelSocketFactory(
                relay = relay,
                credentialProvider = credentialProvider,
                expectedUpstreamHost = expectedHost,
                approvedUpstreamAddresses = approvedAddresses,
                cancellation = cancellation,
            )
        }
        val tunnel = HttpsProfileHttpTransport(
            urlPolicy = SafeNetworkUrlPolicy(setOf(endpoint)),
            tunnelSocketFactoryProvider = socketFactoryProvider,
        )
        return DirectFirstProfileHttpTransport(
            profileId = profileId,
            endpoint = endpoint,
            policy = SecureTunnelPolicy.QA,
            direct = direct,
            tunnel = tunnel,
            observer = observer,
        )
    }

    private companion object {
        val OFVIRTUAL_PROFILE = ProfileId("junta-ofvirtual")
        val OFVIRTUAL_ENDPOINT = URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT)
    }
}

internal object SecureTunnelRuntimes {
    fun create(
        config: SecureTunnelPublicConfig,
        credentialProvider: TunnelCredentialProvider?,
        directTransportFactory: ProfileHttpTransportFactory = DefaultDirectTransportFactory,
    ): SecureTunnelRuntime {
        val relay = config.validatedRelayOrNull()
        if (relay == null || credentialProvider == null) {
            return DirectOnlyTunnelRuntime(directTransportFactory)
        }
        return QaSecureTunnelRuntime(
            relay = relay,
            credentialProvider = credentialProvider,
            directTransportFactory = directTransportFactory,
        )
    }

    private fun SecureTunnelPublicConfig.validatedRelayOrNull(): SecureTunnelRelay? {
        if (!enabled || !relayHost.isCanonicalRelayHost() || relayPort !in 1..65535) {
            return null
        }
        val pins = relaySpkiPins.toSet()
        if (pins.size < MINIMUM_RELAY_PINS) return null
        return runCatching {
            SecureTunnelRelay(
                host = relayHost,
                port = relayPort,
                spkiPins = pins,
            )
        }.getOrNull()
    }

    private fun String.isCanonicalRelayHost(): Boolean {
        if (isBlank() || length > MAX_HOST_CHARS || any(Char::isISOControl)) return false
        if (this != lowercase(Locale.ROOT) || endsWith('.') || equals("localhost", ignoreCase = true)) return false
        if (matches(IPV4_LIKE) || contains(':')) return false
        val ascii = runCatching { IDN.toASCII(this, IDN.USE_STD3_ASCII_RULES) }.getOrNull() ?: return false
        if (ascii != this || ascii.length > MAX_HOST_CHARS || '.' !in ascii) return false
        return ascii.split('.').all { label ->
            label.isNotEmpty() && label.length <= MAX_LABEL_CHARS &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit()
        }
    }

    private const val MINIMUM_RELAY_PINS = 2
    private const val MAX_HOST_CHARS = 253
    private const val MAX_LABEL_CHARS = 63
    private val IPV4_LIKE = Regex("[0-9.]+")
}

private object DefaultDirectTransportFactory : ProfileHttpTransportFactory {
    override fun create(profileId: ProfileId, endpoint: URI): ProfileHttpTransport = runCatching {
        HttpsProfileHttpTransport(SafeNetworkUrlPolicy(setOf(endpoint)))
    }.getOrElse {
        ProfileHttpTransport { _, cancellation ->
            ProfileHttpResult.Failure(cancellation.snapshotFailure(ProfileHttpFailure.INVALID_ENDPOINT))
        }
    }
}
