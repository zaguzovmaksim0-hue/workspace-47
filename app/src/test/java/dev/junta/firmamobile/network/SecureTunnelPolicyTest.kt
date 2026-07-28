package dev.junta.firmamobile.network

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTunnelPolicyTest {
    @Test
    fun qaAllowsOfvirtualMiniApplet15ExactTuple() {
        assertTrue(SecureTunnelPolicy.QA.allows(ProfileId("junta-ofvirtual"), OFVIRTUAL_15))
    }

    @Test
    fun qaRejectsOfvirtualMiniApplet14Tuple() {
        assertFalse(SecureTunnelPolicy.QA.allows(ProfileId("junta-ofvirtual"), JUNTA_14))
    }

    @Test
    fun qaRejectsWrongProfileForMiniApplet15() {
        assertFalse(SecureTunnelPolicy.QA.allows(ProfileId("junta-andalucia"), OFVIRTUAL_15))
    }

    @Test
    fun qaRejectsUnizarProfileForMiniApplet15() {
        assertFalse(SecureTunnelPolicy.QA.allows(ProfileId("unizar-tramitador"), OFVIRTUAL_15))
    }

    @Test
    fun qaRejectsNonExactOfvirtualUrl() {
        assertFalse(
            SecureTunnelPolicy.QA.allows(
                ProfileId("junta-ofvirtual"),
                URI("https://ws024.juntadeandalucia.es/"),
            ),
        )
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
