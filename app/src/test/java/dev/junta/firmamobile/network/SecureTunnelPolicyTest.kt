package dev.junta.firmamobile.network

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTunnelPolicyTest {
    @Test
    fun qaAllowsOnlyOfvirtualMiniApplet15ExactTuple() {
        assertTrue(SecureTunnelPolicy.QA.allows(ProfileId("junta-ofvirtual"), OFVIRTUAL_15))
        assertFalse(SecureTunnelPolicy.QA.allows(ProfileId("junta-andalucia"), JUNTA_14))
        assertFalse(
            SecureTunnelPolicy.QA.allows(
                ProfileId("junta-ofvirtual"),
                URI("https://ws024.juntadeandalucia.es/"),
            ),
        )
        assertFalse(SecureTunnelPolicy.QA.allows(ProfileId("unizar-tramitador"), OFVIRTUAL_15))
    }

    @Test
    fun releaseAllowsNoTunnelBindings() {
        assertFalse(SecureTunnelPolicy.RELEASE.allows(ProfileId("junta-ofvirtual"), OFVIRTUAL_15))
    }

    private companion object {
        val OFVIRTUAL_15 = URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT)
        val JUNTA_14 = URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT)
    }
}
