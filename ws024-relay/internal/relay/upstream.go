package relay

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"sort"
)

const (
	fixedUpstreamHost        = "ws024.juntadeandalucia.es"
	IANAIPv6RegistryRevision = "2025-10-09"
)

var (
	errUpstreamResolution = errors.New("upstream resolution failed")
	errUpstreamAddress    = errors.New("upstream address rejected")
	errUpstreamDial       = errors.New("upstream dial failed")
	errUpstreamPeer       = errors.New("upstream peer mismatch")
)

type Resolver interface {
	LookupNetIP(ctx context.Context, network, host string) ([]netip.Addr, error)
}

type TCPDialer interface {
	DialContext(ctx context.Context, network, address string) (net.Conn, error)
}

type FixedUpstreamDialer interface {
	DialContext(ctx context.Context) (net.Conn, netip.Addr, error)
}

type fixedUpstreamDialer struct {
	resolver Resolver
	dialer   TCPDialer
}

// NewFixedUpstreamDialer creates a dialer whose only destination is the
// compiled-in ws024 HTTPS authority. Dependencies exist solely for unit tests;
// neither the host nor port is configurable.
func NewFixedUpstreamDialer(resolver Resolver, dialer TCPDialer) FixedUpstreamDialer {
	if resolver == nil {
		resolver = net.DefaultResolver
	}
	if dialer == nil {
		dialer = &net.Dialer{}
	}
	return &fixedUpstreamDialer{resolver: resolver, dialer: dialer}
}

// DialContext resolves once, validates every response address, then dials the
// numerically lowest normalized address. Sorting makes this choice stable even
// when resolver response order changes.
func (d *fixedUpstreamDialer) DialContext(ctx context.Context) (net.Conn, netip.Addr, error) {
	if err := ctx.Err(); err != nil {
		return nil, netip.Addr{}, err
	}
	addresses, err := d.resolver.LookupNetIP(ctx, "ip", fixedUpstreamHost)
	if err != nil {
		if ctxErr := ctx.Err(); ctxErr != nil {
			return nil, netip.Addr{}, ctxErr
		}
		return nil, netip.Addr{}, errUpstreamResolution
	}
	if err := ctx.Err(); err != nil {
		return nil, netip.Addr{}, err
	}
	if len(addresses) == 0 {
		return nil, netip.Addr{}, errUpstreamAddress
	}

	normalized := make([]netip.Addr, 0, len(addresses))
	seen := make(map[netip.Addr]struct{}, len(addresses))
	for _, address := range addresses {
		if address.Zone() != "" || address.Is4In6() {
			return nil, netip.Addr{}, errUpstreamAddress
		}
		address = address.Unmap()
		if !IsPublicRoutable(address) {
			return nil, netip.Addr{}, errUpstreamAddress
		}
		if _, exists := seen[address]; exists {
			return nil, netip.Addr{}, errUpstreamAddress
		}
		seen[address] = struct{}{}
		normalized = append(normalized, address)
	}
	sort.Slice(normalized, func(i, j int) bool { return normalized[i].Compare(normalized[j]) < 0 })
	chosen := normalized[0]

	conn, err := d.dialer.DialContext(ctx, "tcp", net.JoinHostPort(chosen.String(), "443"))
	if err != nil {
		if ctxErr := ctx.Err(); ctxErr != nil {
			return nil, netip.Addr{}, ctxErr
		}
		return nil, netip.Addr{}, errUpstreamDial
	}
	if conn == nil {
		return nil, netip.Addr{}, errUpstreamDial
	}
	if err := ctx.Err(); err != nil {
		_ = conn.Close()
		return nil, netip.Addr{}, err
	}
	if !matchesChosenPeer(conn.RemoteAddr(), chosen) {
		_ = conn.Close()
		return nil, netip.Addr{}, errUpstreamPeer
	}
	return conn, chosen, nil
}

func matchesChosenPeer(remote net.Addr, chosen netip.Addr) bool {
	tcpAddr, ok := remote.(*net.TCPAddr)
	if !ok || tcpAddr.Zone != "" || tcpAddr.Port != 443 {
		return false
	}
	remoteIP, ok := netip.AddrFromSlice(tcpAddr.IP)
	return ok && remoteIP.Unmap() == chosen
}

// IsPublicRoutable allows ordinary public IPv4, ordinary IPv6 global unicast
// in 2000::/3, and well-known NAT64 only when its embedded IPv4 is public.
// It is reviewed against the IANA IPv6 special-purpose registry revision above.
func IsPublicRoutable(ip netip.Addr) bool {
	if !ip.IsValid() || ip.Zone() != "" || ip.Is4In6() {
		return false
	}
	if ip.Is4() {
		return isPublicIPv4(ip)
	}
	if wellKnownNAT64.Contains(ip) {
		raw := ip.As16()
		embedded := netip.AddrFrom4([4]byte{raw[12], raw[13], raw[14], raw[15]})
		return isPublicIPv4(embedded)
	}
	if !ipv6GlobalUnicast.Contains(ip) {
		return false
	}
	for _, prefix := range nonPublicIPv6Prefixes {
		if prefix.Contains(ip) {
			return false
		}
	}
	return true
}

func isPublicIPv4(ip netip.Addr) bool {
	if !ip.IsValid() || !ip.Is4() || !ip.IsGlobalUnicast() || ip.IsPrivate() {
		return false
	}
	for _, prefix := range nonPublicIPv4Prefixes {
		if prefix.Contains(ip) {
			return false
		}
	}
	return true
}

var (
	ipv6GlobalUnicast = netip.MustParsePrefix("2000::/3")
	wellKnownNAT64    = netip.MustParsePrefix("64:ff9b::/96")
)

var nonPublicIPv4Prefixes = mustPrefixes([]string{
	"0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
	"169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
	"192.31.196.0/24", "192.52.193.0/24", "192.88.99.0/24", "192.168.0.0/16",
	"192.175.48.0/24", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24",
	"224.0.0.0/4", "240.0.0.0/4",
})

var nonPublicIPv6Prefixes = mustPrefixes([]string{
	"::/96", "::ffff:0:0/96", "64:ff9b:1::/48", "100::/64", "100:0:0:1::/64",
	"2001::/23", "2001:db8::/32", "2002::/16", "2620:4f:8000::/48",
	"3fff::/20", "5f00::/16", "fc00::/7", "fe80::/10", "ff00::/8",
})

func mustPrefixes(values []string) []netip.Prefix {
	prefixes := make([]netip.Prefix, 0, len(values))
	for _, value := range values {
		prefixes = append(prefixes, netip.MustParsePrefix(value))
	}
	return prefixes
}
