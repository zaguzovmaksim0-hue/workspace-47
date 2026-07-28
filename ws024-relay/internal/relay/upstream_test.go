package relay

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"testing"
	"time"
)

func TestIsPublicRoutable(t *testing.T) {
	for _, tc := range []struct {
		ip   string
		want bool
	}{
		{"8.8.8.8", true},
		{"2606:4700:4700::1111", true},
		{"0.0.0.0", false}, {"10.0.0.1", false}, {"100.64.0.1", false},
		{"127.0.0.1", false}, {"169.254.1.1", false}, {"172.16.0.1", false},
		{"192.0.2.1", false}, {"192.168.1.1", false}, {"192.31.196.1", false},
		{"192.52.193.1", false}, {"192.88.99.1", false}, {"192.175.48.1", false},
		{"198.18.0.1", false}, {"198.51.100.1", false}, {"203.0.113.1", false},
		{"224.0.0.1", false}, {"240.0.0.1", false}, {"255.255.255.255", false},
		{"::", false}, {"::1", false}, {"::ffff:192.0.2.1", false},
		{"64:ff9b::192.0.2.1", false}, {"64:ff9b:1::1", false}, {"100::1", false},
		{"100:0:0:1::", false}, {"100:0:0:1:ffff:ffff:ffff:ffff", false},
		{"100:0:0:2::1", true},
		{"2001:2::1", false}, {"2001:db8::1", false}, {"2002::1", false},
		{"3fff::1", false}, {"5f00::1", false},
		{"fc00::1", false}, {"fe80::1", false}, {"ff02::1", false},
	} {
		t.Run(tc.ip, func(t *testing.T) {
			ip := netip.MustParseAddr(tc.ip)
			if got := IsPublicRoutable(ip); got != tc.want {
				t.Fatalf("IsPublicRoutable(%v) = %v, want %v", ip, got, tc.want)
			}
		})
	}
}

func TestFixedUpstreamDialerRejectsZonedDNSAddressWithoutDial(t *testing.T) {
	resolver := &fakeResolver{addrs: []netip.Addr{netip.MustParseAddr("2606:4700:4700::1111%resolver-zone")}}
	dialer := &fakeTCPDialer{}

	conn, ip, err := NewFixedUpstreamDialer(resolver, dialer).DialContext(context.Background())
	if !errors.Is(err, errUpstreamAddress) || conn != nil || ip.IsValid() {
		t.Fatalf("DialContext() = (%v, %v, %v), want rejected zoned DNS address", conn, ip, err)
	}
	if resolver.calls != 1 || dialer.calls != 0 {
		t.Fatalf("calls = lookup:%d dial:%d, want 1 and 0", resolver.calls, dialer.calls)
	}
}

func TestFixedUpstreamDialerDialsDeterministicLiteralAndVerifiesPeer(t *testing.T) {
	resolver := &fakeResolver{addrs: []netip.Addr{
		netip.MustParseAddr("2606:4700:4700::1111"), netip.MustParseAddr("8.8.8.8"),
	}}
	dialer := &fakeTCPDialer{}
	dialer.conn = &fakeConn{remote: &net.TCPAddr{IP: net.ParseIP("8.8.8.8"), Port: 443}}

	conn, ip, err := NewFixedUpstreamDialer(resolver, dialer).DialContext(context.Background())
	if err != nil {
		t.Fatalf("DialContext() error = %v", err)
	}
	if conn != dialer.conn {
		t.Fatal("DialContext() returned unexpected connection")
	}
	if got, want := ip.String(), "8.8.8.8"; got != want {
		t.Fatalf("selected IP = %q, want %q", got, want)
	}
	if resolver.calls != 1 || resolver.network != "ip" || resolver.host != "ws024.juntadeandalucia.es" {
		t.Fatalf("lookup = calls:%d network:%q host:%q", resolver.calls, resolver.network, resolver.host)
	}
	if dialer.calls != 1 || dialer.network != "tcp" || dialer.address != "8.8.8.8:443" {
		t.Fatalf("dial = calls:%d network:%q address:%q", dialer.calls, dialer.network, dialer.address)
	}
}

func TestFixedUpstreamDialerRejectsUnsafeOrDuplicateDNSWithoutDial(t *testing.T) {
	for _, addrs := range [][]netip.Addr{
		nil,
		{netip.MustParseAddr("8.8.8.8"), netip.MustParseAddr("10.0.0.1")},
		{netip.MustParseAddr("::ffff:8.8.8.8"), netip.MustParseAddr("8.8.8.8")},
	} {
		resolver := &fakeResolver{addrs: addrs}
		dialer := &fakeTCPDialer{}
		conn, ip, err := NewFixedUpstreamDialer(resolver, dialer).DialContext(context.Background())
		if err == nil || conn != nil || ip.IsValid() {
			t.Fatalf("DialContext(%v) = (%v, %v, %v), want failure", addrs, conn, ip, err)
		}
		if resolver.calls != 1 || dialer.calls != 0 {
			t.Fatalf("calls = lookup:%d dial:%d, want 1 and 0", resolver.calls, dialer.calls)
		}
	}
}

func TestFixedUpstreamDialerClosesMismatchedPeer(t *testing.T) {
	resolver := &fakeResolver{addrs: []netip.Addr{netip.MustParseAddr("8.8.8.8")}}
	conn := &fakeConn{remote: &net.TCPAddr{IP: net.ParseIP("1.1.1.1"), Port: 443}}
	dialer := &fakeTCPDialer{conn: conn}

	gotConn, ip, err := NewFixedUpstreamDialer(resolver, dialer).DialContext(context.Background())
	if err == nil || gotConn != nil || ip.IsValid() {
		t.Fatalf("DialContext() = (%v, %v, %v), want failure", gotConn, ip, err)
	}
	if !conn.closed {
		t.Fatal("mismatched connection was not closed")
	}
}

func TestFixedUpstreamDialerClosesZonedPeer(t *testing.T) {
	resolver := &fakeResolver{addrs: []netip.Addr{netip.MustParseAddr("8.8.8.8")}}
	conn := &fakeConn{remote: &net.TCPAddr{IP: net.ParseIP("8.8.8.8"), Port: 443, Zone: "peer-zone"}}
	dialer := &fakeTCPDialer{conn: conn}

	gotConn, ip, err := NewFixedUpstreamDialer(resolver, dialer).DialContext(context.Background())
	if !errors.Is(err, errUpstreamPeer) || gotConn != nil || ip.IsValid() {
		t.Fatalf("DialContext() = (%v, %v, %v), want rejected zoned peer", gotConn, ip, err)
	}
	if !conn.closed {
		t.Fatal("zoned connection was not closed")
	}
}

func TestFixedUpstreamDialerRespectsCanceledContext(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	resolver := &fakeResolver{addrs: []netip.Addr{netip.MustParseAddr("8.8.8.8")}}
	dialer := &fakeTCPDialer{}
	_, _, err := NewFixedUpstreamDialer(resolver, dialer).DialContext(ctx)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("DialContext() error = %v, want context cancellation", err)
	}
	if resolver.calls != 0 || dialer.calls != 0 {
		t.Fatalf("calls = lookup:%d dial:%d, want 0 and 0", resolver.calls, dialer.calls)
	}
}

func TestFixedUpstreamDialerClosesConnectionWhenCanceledDuringDial(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	resolver := &fakeResolver{addrs: []netip.Addr{netip.MustParseAddr("8.8.8.8")}}
	conn := &fakeConn{remote: &net.TCPAddr{IP: net.ParseIP("8.8.8.8"), Port: 443}}
	dialer := &fakeTCPDialer{conn: conn, afterDial: cancel}

	gotConn, ip, err := NewFixedUpstreamDialer(resolver, dialer).DialContext(ctx)
	if !errors.Is(err, context.Canceled) || gotConn != nil || ip.IsValid() {
		t.Fatalf("DialContext() = (%v, %v, %v), want cancellation", gotConn, ip, err)
	}
	if !conn.closed {
		t.Fatal("connection returned after cancellation was not closed")
	}
}

type fakeResolver struct {
	addrs         []netip.Addr
	err           error
	calls         int
	network, host string
}

func (r *fakeResolver) LookupNetIP(_ context.Context, network, host string) ([]netip.Addr, error) {
	r.calls++
	r.network, r.host = network, host
	return r.addrs, r.err
}

type fakeTCPDialer struct {
	conn             net.Conn
	err              error
	afterDial        func()
	calls            int
	network, address string
}

func (d *fakeTCPDialer) DialContext(_ context.Context, network, address string) (net.Conn, error) {
	d.calls++
	d.network, d.address = network, address
	if d.afterDial != nil {
		d.afterDial()
	}
	return d.conn, d.err
}

type fakeConn struct {
	remote net.Addr
	closed bool
}

func (c *fakeConn) Read([]byte) (int, error)           { return 0, errors.New("not implemented") }
func (c *fakeConn) Write([]byte) (int, error)          { return 0, errors.New("not implemented") }
func (c *fakeConn) Close() error                       { c.closed = true; return nil }
func (c *fakeConn) LocalAddr() net.Addr                { return &net.TCPAddr{} }
func (c *fakeConn) RemoteAddr() net.Addr               { return c.remote }
func (c *fakeConn) SetDeadline(_ time.Time) error      { return nil }
func (c *fakeConn) SetReadDeadline(_ time.Time) error  { return nil }
func (c *fakeConn) SetWriteDeadline(_ time.Time) error { return nil }
