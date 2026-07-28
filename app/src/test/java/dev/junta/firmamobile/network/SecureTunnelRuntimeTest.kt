package dev.junta.firmamobile.network

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import dev.junta.firmamobile.signing.UnizarTriPhaseAdapter
import java.net.URI
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTunnelRuntimeTest {
    @Test
    fun exactQaMiniApplet15TupleReturnsDirectFirstTransport() {
        val direct = markerTransport()
        val factoryCalls = AtomicInteger()
        val runtime = SecureTunnelRuntimes.create(
            config = validConfig(),
            credentialProvider = TunnelCredentialProvider { null },
            directTransportFactory = ProfileHttpTransportFactory { profileId, endpoint ->
                factoryCalls.incrementAndGet()
                assertEquals(OFVIRTUAL_PROFILE, profileId)
                assertEquals(OFVIRTUAL_ENDPOINT, endpoint)
                direct
            },
        )

        val transport = runtime.transportFor(
            profileId = OFVIRTUAL_PROFILE,
            endpoint = OFVIRTUAL_ENDPOINT,
            observer = TunnelRouteObserver { _, _ -> },
        )

        assertTrue(runtime is QaSecureTunnelRuntime)
        assertTrue(transport is DirectFirstProfileHttpTransport)
        assertTrue(factoryCalls.get() == 1)
    }

    @Test
    fun everyNonExactProfileOrEndpointRemainsDirectOnly() {
        val cases = listOf(
            OFVIRTUAL_PROFILE to JUNTA_14_ENDPOINT,
            ProfileId("junta-andalucia") to JUNTA_14_ENDPOINT,
            ProfileId("unizar-tramitador") to URI(UnizarTriPhaseAdapter.ENDPOINT),
            ProfileId("junta-andalucia") to OFVIRTUAL_ENDPOINT,
            ProfileId("unizar-tramitador") to OFVIRTUAL_ENDPOINT,
        )
        for ((profileId, endpoint) in cases) {
            val direct = markerTransport()
            val runtime = SecureTunnelRuntimes.create(
                config = validConfig(),
                credentialProvider = TunnelCredentialProvider { null },
                directTransportFactory = ProfileHttpTransportFactory { _, _ -> direct },
            )

            val transport = runtime.transportFor(profileId, endpoint, TunnelRouteObserver { _, _ -> })

            assertSame("case=$profileId/$endpoint", direct, transport)
        }
    }

    @Test
    fun absentPartialOrInvalidConfigurationFailsClosedToDirectOnlyRuntime() {
        val validPin = pin(1)
        val cases = listOf(
            SecureTunnelPublicConfig(false, "", 443, emptySet()),
            SecureTunnelPublicConfig(false, "relay.example", 443, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "", 443, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "relay.example", 0, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "relay.example", 65536, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "relay.example\n", 443, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "Relay.Example", 443, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "relay.example.", 443, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "127.0.0.1", 443, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "localhost", 443, setOf(validPin, pin(2))),
            SecureTunnelPublicConfig(true, "relay.example", 443, emptySet()),
            SecureTunnelPublicConfig(true, "relay.example", 443, setOf(validPin)),
            SecureTunnelPublicConfig(true, "relay.example", 443, setOf(validPin, validPin)),
            SecureTunnelPublicConfig(true, "relay.example", 443, setOf("sha256/not-base64", pin(2))),
        )
        for (config in cases) {
            val direct = markerTransport()
            val runtime = SecureTunnelRuntimes.create(
                config = config,
                credentialProvider = TunnelCredentialProvider { null },
                directTransportFactory = ProfileHttpTransportFactory { _, _ -> direct },
            )

            val transport = runtime.transportFor(
                OFVIRTUAL_PROFILE,
                OFVIRTUAL_ENDPOINT,
                TunnelRouteObserver { _, _ -> },
            )

            assertTrue("config=$config", runtime is DirectOnlyTunnelRuntime)
            assertSame("config=$config", direct, transport)
        }
    }

    @Test
    fun missingCredentialProviderFailsClosedEvenWithValidPublicConfiguration() {
        val direct = markerTransport()
        val runtime = SecureTunnelRuntimes.create(
            config = validConfig(),
            credentialProvider = null,
            directTransportFactory = ProfileHttpTransportFactory { _, _ -> direct },
        )

        assertTrue(runtime is DirectOnlyTunnelRuntime)
        assertSame(
            direct,
            runtime.transportFor(OFVIRTUAL_PROFILE, OFVIRTUAL_ENDPOINT, TunnelRouteObserver { _, _ -> }),
        )
    }

    @Test
    fun directOnlyRuntimeNeverConstructsTunnelRegardlessOfTuple() {
        val direct = markerTransport()
        val runtime = DirectOnlyTunnelRuntime(
            ProfileHttpTransportFactory { _, _ -> direct },
        )

        assertSame(
            direct,
            runtime.transportFor(OFVIRTUAL_PROFILE, OFVIRTUAL_ENDPOINT, TunnelRouteObserver { _, _ -> }),
        )
    }

    @Test
    fun configuredRuntimeCopiesPinConfigurationBeforeUse() {
        val mutablePins = linkedSetOf(pin(1), pin(2))
        val config = SecureTunnelPublicConfig(true, "relay.example", 443, mutablePins)
        val runtime = SecureTunnelRuntimes.create(
            config = config,
            credentialProvider = TunnelCredentialProvider { null },
            directTransportFactory = ProfileHttpTransportFactory { _, _ -> markerTransport() },
        )
        mutablePins.clear()

        assertTrue(runtime is QaSecureTunnelRuntime)
        assertTrue(
            runtime.transportFor(OFVIRTUAL_PROFILE, OFVIRTUAL_ENDPOINT, TunnelRouteObserver { _, _ -> })
                is DirectFirstProfileHttpTransport,
        )
    }

    private fun markerTransport(): ProfileHttpTransport = ProfileHttpTransport { _, _ ->
        ProfileHttpResult.Failure(ProfileHttpFailure.INVALID_ENDPOINT)
    }

    private fun validConfig() = SecureTunnelPublicConfig(
        enabled = true,
        relayHost = "relay.example",
        relayPort = 443,
        relaySpkiPins = linkedSetOf(pin(1), pin(2)),
    )

    private fun pin(value: Int): String = "sha256/" + Base64.getEncoder().encodeToString(
        ByteArray(32) { value.toByte() },
    )

    private companion object {
        val OFVIRTUAL_PROFILE = ProfileId("junta-ofvirtual")
        val OFVIRTUAL_ENDPOINT = URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT)
        val JUNTA_14_ENDPOINT = URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT)
    }
}
