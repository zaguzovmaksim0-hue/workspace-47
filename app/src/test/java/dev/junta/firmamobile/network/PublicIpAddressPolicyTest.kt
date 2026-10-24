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

    @Test
    fun ipv4DeniedRangeEdgesAndTheirNeighboursKeepCurrentDecisions() {
        val deniedRanges = listOf(
            "0.0.0.0" to "0.255.255.255",
            "10.0.0.0" to "10.255.255.255",
            "100.64.0.0" to "100.127.255.255",
            "127.0.0.0" to "127.255.255.255",
            "169.254.0.0" to "169.254.255.255",
            "172.16.0.0" to "172.31.255.255",
            "192.0.0.0" to "192.0.0.255",
            "192.0.2.0" to "192.0.2.255",
            "192.31.196.0" to "192.31.196.255",
            "192.52.193.0" to "192.52.193.255",
            "192.88.99.0" to "192.88.99.255",
            "192.168.0.0" to "192.168.255.255",
            "192.175.48.0" to "192.175.48.255",
            "198.18.0.0" to "198.19.255.255",
            "198.51.100.0" to "198.51.100.255",
            "203.0.113.0" to "203.0.113.255",
            "224.0.0.0" to "255.255.255.255",
        )
        deniedRanges.forEach { (first, last) ->
            assertIpv4AndEmbeddedDecisions(first, expected = false)
            assertIpv4AndEmbeddedDecisions(last, expected = false)
            val firstValue = ipv4Value(first)
            val lastValue = ipv4Value(last)
            if (firstValue > 0) assertIpv4AndEmbeddedDecisions(ipv4Literal(firstValue - 1), expected = true)
            if (lastValue < 0xffffffffL) {
                assertIpv4AndEmbeddedDecisions(ipv4Literal(lastValue + 1), expected = true)
            }
        }
    }

    @Test
    fun remainingGlobalIpv6DenyRangesHaveExactEdges() {
        val deniedEdges = listOf(
            "2001::",
            "2001:1ff:ffff:ffff:ffff:ffff:ffff:ffff",
            "2001:db8::",
            "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff",
            "2002::",
            "2002:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "2620:4f:8000::",
            "2620:4f:8000:ffff:ffff:ffff:ffff:ffff",
            "3fff::",
            "3fff:fff:ffff:ffff:ffff:ffff:ffff:ffff",
        )
        deniedEdges.forEach { raw ->
            assertFalse(raw, PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName(raw)))
        }
        val allowedNeighbours = listOf(
            "2000:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "2001:200::",
            "2001:db7:ffff:ffff:ffff:ffff:ffff:ffff",
            "2001:db9::",
            "2001:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "2003::",
            "2620:4f:7fff:ffff:ffff:ffff:ffff:ffff",
            "2620:4f:8001::",
            "3ffe:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "3fff:1000::",
            "2000::",
            "3fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        )
        allowedNeighbours.forEach { raw ->
            assertTrue(raw, PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName(raw)))
        }
    }

    @Test
    fun globalUnicastAndNat64OuterBoundariesFailClosed() {
        listOf(
            "1fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "4000::",
            "64:ff9a:ffff:ffff:ffff:ffff:ffff:ffff",
            "64:ff9b:0:0:0:1::",
            "::808:808",
        ).forEach { raw ->
            assertFalse(raw, PublicIpAddressPolicy.isPublicRoutable(InetAddress.getByName(raw)))
        }
    }

    @Test
    fun numericScopeRejectsGlobalAndNat64WithoutDependingOnHostInterfaces() {
        listOf("2606:4700:4700::1111", "64:ff9b::808:808").forEach { literal ->
            val raw = InetAddress.getByName(literal).address
            assertTrue(literal, PublicIpAddressPolicy.isPublicRoutable(Inet6Address.getByAddress(null, raw, 0)))
            assertFalse(literal, PublicIpAddressPolicy.isPublicRoutable(Inet6Address.getByAddress(null, raw, 1)))
        }
    }

    private fun assertIpv4AndEmbeddedDecisions(literal: String, expected: Boolean) {
        val ipv4 = InetAddress.getByName(literal)
        val nat64 = InetAddress.getByName("64:ff9b::$literal")
        assertTrue(literal, PublicIpAddressPolicy.isPublicRoutable(ipv4) == expected)
        assertTrue("NAT64 $literal", PublicIpAddressPolicy.isPublicRoutable(nat64) == expected)
        // Force Inet6Address: parsing a mapped literal may normalize it to Inet4Address.
        assertFalse("mapped $literal", PublicIpAddressPolicy.isPublicRoutable(ipv4Mapped(literal)))
    }

    private fun ipv4Value(literal: String): Long =
        literal.split('.').fold(0L) { value, octet -> (value shl 8) or octet.toLong() }

    private fun ipv4Literal(value: Long): String =
        (3 downTo 0).joinToString(".") { shift -> ((value ushr (shift * 8)) and 0xff).toString() }

    private fun ipv4Mapped(ipv4: String): Inet6Address {
        val raw = ByteArray(16)
        raw[10] = 0xff.toByte()
        raw[11] = 0xff.toByte()
        InetAddress.getByName(ipv4).address.copyInto(raw, destinationOffset = 12)
        return Inet6Address.getByAddress(null, raw, -1)
    }
}
