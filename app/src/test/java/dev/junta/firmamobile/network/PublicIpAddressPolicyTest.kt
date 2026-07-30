package dev.junta.firmamobile.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicIpAddressPolicyTest {
    @Test
    fun registryRevisionIsPinnedToTheReviewedIanaSnapshot() {
        assertTrue(PublicIpAddressPolicy.IANA_IPV6_REGISTRY_REVISION == "2025-10-09")
    }

    @Test
    fun ordinaryGlobalIpv4AndIpv6AreAllowed() {
        listOf(
            "8.8.8.8",
            "217.12.21.226",
            "2001:4860:4860::8888",
            "2606:4700:4700::1111",
            "2a00:1450:4003:80b::200e",
            "2001:200::1",
            "3fff:1000::1",
        ).forEach { raw ->
            assertTrue(raw, PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName(raw)))
        }
    }

    @Test
    fun allReviewedIanaSpecialPurposeFamiliesAreRejected() {
        listOf(
            "::",
            "::1",
            "64:ff9b:1::1",
            "100::1",
            "100:0:0:1::1",
            "2001::1",
            "2001:1::1",
            "2001:1::2",
            "2001:1::3",
            "2001:2::1",
            "2001:3::1",
            "2001:4:112::1",
            "2001:10::1",
            "2001:20::1",
            "2001:30::1",
            "2001:db8::1",
            "2002::1",
            "2620:4f:8000::1",
            "3fff::1",
            "3fff:0fff:ffff:ffff:ffff:ffff:ffff:ffff",
            "5f00::1",
            "fc00::1",
            "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fe80::1",
            "ff02::1",
            "192.31.196.1",
            "192.52.193.1",
            "192.175.48.1",
        ).forEach { raw ->
            assertFalse(raw, PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName(raw)))
        }
        assertFalse(PublicIpAddressPolicy.isPublicRoutable(ipv4Mapped("8.8.8.8")))
    }

    @Test
    fun unallocatedOrNonGlobalIpv6OutsideGlobalUnicastFailsClosed() {
        listOf(
            "0100::1",
            "4000::1",
            "7fff::1",
            "8000::1",
        ).forEach { raw ->
            assertFalse(raw, PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName(raw)))
        }
    }

    @Test
    fun wellKnownNat64RequiresAPublicEmbeddedIpv4() {
        listOf(
            "64:ff9b::808:808",
            "64:ff9b::d90c:15e2",
        ).forEach { raw ->
            assertTrue(raw, PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName(raw)))
        }
        listOf(
            "64:ff9b::a00:1",
            "64:ff9b::7f00:1",
            "64:ff9b::c000:201",
            "64:ff9b::c633:6401",
            "64:ff9b::cb00:7101",
            "64:ff9b::c01f:c401",
            "64:ff9b::c034:c101",
            "64:ff9b::c0af:3001",
        ).forEach { raw ->
            assertFalse(raw, PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName(raw)))
        }
    }

    @Test
    fun ipv6PrefixBoundariesAreExact() {
        assertFalse(PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName("2001:1ff:ffff::1")))
        assertTrue(PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName("2001:200::1")))
        assertFalse(PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName("3fff:0fff::1")))
        assertTrue(PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName("3fff:1000::1")))
        assertFalse(PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName("5f00:ffff::1")))
    }

    @Test
    fun scopedGlobalIpv6IsRejectedAsAmbiguous() {
        val interfaceWithIndex = NetworkInterface.getNetworkInterfaces().toList()
            .firstOrNull { it.index > 0 }
            ?: return
        val raw = InetAddress.getByName("2606:4700:4700::1111").address
        val scoped = Inet6Address.getByAddress(null, raw, interfaceWithIndex.index)

        assertFalse(PublicIpAddressPolicy.isPublicRoutable(scoped))
    }

    private fun ipv4Mapped(ipv4: String): Inet6Address {
        val raw = ByteArray(16)
        raw[10] = 0xff.toByte()
        raw[11] = 0xff.toByte()
        InetAddress.getByName(ipv4).address.copyInto(raw, destinationOffset = 12)
        return Inet6Address.getByAddress(null, raw, -1)
    }
}
