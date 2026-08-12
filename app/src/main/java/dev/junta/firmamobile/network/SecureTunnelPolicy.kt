package dev.junta.firmamobile.network

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import java.net.URI

internal data class SecureTunnelBinding(
    val profileId: ProfileId,
    val endpoint: URI,
)

internal class SecureTunnelPolicy private constructor(bindings: Set<SecureTunnelBinding>) {
    private val bindings = bindings.toSet()

    fun allows(profileId: ProfileId, endpoint: URI): Boolean =
        SecureTunnelBinding(profileId, endpoint) in bindings

    companion object {
        val QA = SecureTunnelPolicy(
            setOf(
                SecureTunnelBinding(
                    profileId = ProfileId("junta-ofvirtual"),
                    endpoint = URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT),
                ),
            ),
        )

        val RELEASE = SecureTunnelPolicy(emptySet())
    }
}
