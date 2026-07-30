package dev.junta.firmamobile.network

import java.net.Inet6Address
import java.net.InetAddress

/**
 * DNS-result policy reviewed against the IANA IPv6 Special-Purpose Address Space
 * registry revision [IANA_IPV6_REGISTRY_REVISION]. Profile URLs still require DNS
 * hostnames; this policy only classifies resolved addresses before a pinned connect.
 */
internal object PublicIpAddressPolicy {
    const val IANA_IPV6_REGISTRY_REVISION = "2025-10-09"

    fun isPublicRoutable(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        val raw = address.address
        return when (raw.size) {
            IPV4_BYTES -> raw.isGlobalIpv4()
            IPV6_BYTES -> isPublicIpv6(address as? Inet6Address ?: return false, raw)
            else -> false
        }
    }

    private fun isPublicIpv6(address: Inet6Address, raw: ByteArray): Boolean {
        if (address.scopeId != 0) return false
        if (IPV4_MAPPED.matches(raw)) return false

        if (WELL_KNOWN_NAT64.matches(raw)) {
            return raw.copyOfRange(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES).isGlobalIpv4()
        }

        if (!GLOBAL_UNICAST.matches(raw)) return false
        if (SPECIAL_PURPOSE_IPV6.any { it.matches(raw) }) return false
        return true
    }

    private fun ByteArray.isGlobalIpv4(): Boolean {
        if (size != IPV4_BYTES) return false
        val first = this[0].toInt() and 0xff
        val second = this[1].toInt() and 0xff
        val third = this[2].toInt() and 0xff
        return when {
            first == 0 || first == 10 || first == 127 || first >= 224 -> false
            first == 100 && second in 64..127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 168 -> false
            first == 192 && second == 0 && third == 0 -> false
            first == 192 && second == 0 && third == 2 -> false
            first == 192 && second == 31 && third == 196 -> false
            first == 192 && second == 52 && third == 193 -> false
            first == 192 && second == 88 && third == 99 -> false
            first == 192 && second == 175 && third == 48 -> false
            first == 198 && second in 18..19 -> false
            first == 198 && second == 51 && third == 100 -> false
            first == 203 && second == 0 && third == 113 -> false
            else -> true
        }
    }

    private data class Ipv6Prefix(
        private val bytes: ByteArray,
        private val prefixBits: Int,
    ) {
        init {
            require(bytes.size == IPV6_BYTES && prefixBits in 0..IPV6_BITS)
        }

        fun matches(candidate: ByteArray): Boolean {
            if (candidate.size != IPV6_BYTES) return false
            val completeBytes = prefixBits / Byte.SIZE_BITS
            for (index in 0 until completeBytes) {
                if (candidate[index] != bytes[index]) return false
            }
            val remainingBits = prefixBits % Byte.SIZE_BITS
            if (remainingBits == 0) return true
            val mask = (0xff shl (Byte.SIZE_BITS - remainingBits)) and 0xff
            return (candidate[completeBytes].toInt() and mask) ==
                (bytes[completeBytes].toInt() and mask)
        }
    }

    private fun prefix(prefixBits: Int, vararg hextets: Int): Ipv6Prefix {
        require(hextets.size <= IPV6_HEXTETS && hextets.all { it in 0..0xffff })
        val bytes = ByteArray(IPV6_BYTES)
        hextets.forEachIndexed { index, hextet ->
            bytes[index * 2] = (hextet ushr 8).toByte()
            bytes[index * 2 + 1] = hextet.toByte()
        }
        return Ipv6Prefix(bytes, prefixBits)
    }

    private val GLOBAL_UNICAST = prefix(3, 0x2000)
    private val IPV4_MAPPED = prefix(96, 0, 0, 0, 0, 0, 0xffff)
    private val WELL_KNOWN_NAT64 = prefix(96, 0x0064, 0xff9b)

    /**
     * Closed special-purpose deny-set. Blocks that are outside 2000::/3 are listed
     * explicitly for auditability even though GLOBAL_UNICAST already rejects them.
     * More-specific globally reachable protocol anycast blocks remain denied: a
     * government portal DNS answer must be an ordinary global unicast destination.
     */
    private val SPECIAL_PURPOSE_IPV6 = listOf(
        prefix(96, 0, 0, 0, 0, 0, 0), // IPv4-compatible / low address space
        IPV4_MAPPED,
        prefix(48, 0x0064, 0xff9b, 0x0001),
        prefix(64, 0x0100, 0, 0, 0),
        prefix(64, 0x0100, 0, 0, 0x0001),
        prefix(23, 0x2001, 0),
        prefix(32, 0x2001, 0x0db8),
        prefix(16, 0x2002),
        prefix(48, 0x2620, 0x004f, 0x8000),
        prefix(20, 0x3fff, 0),
        prefix(16, 0x5f00),
        prefix(7, 0xfc00),
        prefix(10, 0xfe80),
        prefix(8, 0xff00),
    )

    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
    private const val IPV6_BITS = IPV6_BYTES * Byte.SIZE_BITS
    private const val IPV6_HEXTETS = IPV6_BYTES / 2
}
