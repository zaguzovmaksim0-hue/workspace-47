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
            IPV6_BYTES -> address is Inet6Address && isPublicIpv6(address, raw)
            else -> false
        }
    }

    private fun isPublicIpv6(address: Inet6Address, raw: ByteArray): Boolean = when {
        address.scopeId != 0 -> false
        IPV4_MAPPED.matches(raw) -> false
        WELL_KNOWN_NAT64.matches(raw) ->
            raw.copyOfRange(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES).isGlobalIpv4()
        !GLOBAL_UNICAST.matches(raw) -> false
        else -> SPECIAL_PURPOSE_IPV6.none { it.matches(raw) }
    }

    private fun ByteArray.isGlobalIpv4(): Boolean {
        if (size != IPV4_BYTES) return false
        val first = this[0].toInt() and OCTET_MASK
        val second = this[1].toInt() and OCTET_MASK
        val third = this[2].toInt() and OCTET_MASK
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
            val mask = (OCTET_MASK shl (Byte.SIZE_BITS - remainingBits)) and OCTET_MASK
            return (candidate[completeBytes].toInt() and mask) ==
                (bytes[completeBytes].toInt() and mask)
        }
    }

    private fun prefix(prefixBits: Int, vararg hextets: Int): Ipv6Prefix {
        require(hextets.size <= IPV6_HEXTETS && hextets.all { it in 0..HEXTET_MASK })
        val bytes = ByteArray(IPV6_BYTES)
        hextets.forEachIndexed { index, hextet ->
            bytes[index * 2] = (hextet ushr Byte.SIZE_BITS).toByte()
            bytes[index * 2 + 1] = hextet.toByte()
        }
        return Ipv6Prefix(bytes, prefixBits)
    }

    private val GLOBAL_UNICAST = prefix(GLOBAL_UNICAST_PREFIX_BITS, 0x2000)
    private val IPV4_MAPPED = prefix(EMBEDDED_IPV4_PREFIX_BITS, 0, 0, 0, 0, 0, HEXTET_MASK)
    private val WELL_KNOWN_NAT64 = prefix(EMBEDDED_IPV4_PREFIX_BITS, 0x0064, 0xff9b)

    /**
     * Closed special-purpose deny-set. Blocks that are outside 2000::/3 are listed
     * explicitly for auditability even though GLOBAL_UNICAST already rejects them.
     * More-specific globally reachable protocol anycast blocks remain denied: a
     * government portal DNS answer must be an ordinary global unicast destination.
     */
    private val SPECIAL_PURPOSE_IPV6 = listOf(
        prefix(EMBEDDED_IPV4_PREFIX_BITS, 0, 0, 0, 0, 0, 0), // IPv4-compatible / low address space
        IPV4_MAPPED,
        prefix(SITE_PREFIX_BITS, 0x0064, 0xff9b, 0x0001),
        prefix(64, 0x0100, 0, 0, 0),
        prefix(64, 0x0100, 0, 0, 0x0001),
        prefix(PROTOCOL_ASSIGNMENTS_PREFIX_BITS, 0x2001, 0),
        prefix(Int.SIZE_BITS, 0x2001, 0x0db8),
        prefix(16, 0x2002),
        prefix(SITE_PREFIX_BITS, 0x2620, 0x004f, 0x8000),
        prefix(DOCUMENTATION_PREFIX_BITS, 0x3fff, 0),
        prefix(16, 0x5f00),
        prefix(UNIQUE_LOCAL_PREFIX_BITS, 0xfc00),
        prefix(10, 0xfe80),
        prefix(Byte.SIZE_BITS, 0xff00),
    )

    private const val OCTET_MASK = 0xff
    private const val HEXTET_MASK = 0xffff
    private const val GLOBAL_UNICAST_PREFIX_BITS = 3
    private const val PROTOCOL_ASSIGNMENTS_PREFIX_BITS = 23
    private const val DOCUMENTATION_PREFIX_BITS = 20
    private const val UNIQUE_LOCAL_PREFIX_BITS = 7
    private const val SITE_PREFIX_BITS = 48

    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
    private const val IPV6_BITS = IPV6_BYTES * Byte.SIZE_BITS
    private const val EMBEDDED_IPV4_PREFIX_BITS = IPV6_BITS - Int.SIZE_BITS
    private const val IPV6_HEXTETS = IPV6_BYTES / 2
}
