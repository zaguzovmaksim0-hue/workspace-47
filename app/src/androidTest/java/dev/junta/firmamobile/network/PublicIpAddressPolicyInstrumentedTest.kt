package dev.junta.firmamobile.network

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicIpAddressPolicyInstrumentedTest {
    @Test
    fun platformInetAddressClassificationMatchesTheReviewedPolicy() {
        val global = PublicIpAddressPolicy.isPublicRoutable(
            InetAddress.getByName("2606:4700:4700::1111"),
        )
        val safeNat64 = PublicIpAddressPolicy.isPublicRoutable(
            InetAddress.getByName("64:ff9b::808:808"),
        )
        val blockedNat64 = PublicIpAddressPolicy.isPublicRoutable(
            InetAddress.getByName("64:ff9b::7f00:1"),
        )

        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("iana_ipv6_registry_revision", PublicIpAddressPolicy.IANA_IPV6_REGISTRY_REVISION)
                putString("ordinary_global_ipv6", global.toString())
                putString("public_embedded_nat64", safeNat64.toString())
                putString("nonpublic_embedded_nat64", blockedNat64.toString())
            },
        )

        assertTrue(global)
        assertTrue(safeNat64)
        assertFalse(blockedNat64)
    }
}
