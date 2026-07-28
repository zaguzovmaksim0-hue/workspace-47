package relay

import (
	"bufio"
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"errors"
	"io"
	"math/big"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestOuterTLSConfigPinsServerPolicy(t *testing.T) {
	cert := testServerCertificate(t)
	config := OuterTLSConfig(cert)
	if config == nil {
		t.Fatal("OuterTLSConfig() returned nil")
	}
	if config.MinVersion != tls.VersionTLS12 {
		t.Fatalf("MinVersion = %x, want TLS 1.2", config.MinVersion)
	}
	if len(config.Certificates) != 1 {
		t.Fatalf("Certificates = %d, want 1", len(config.Certificates))
	}
	if got := config.NextProtos; len(got) != 1 || got[0] != "http/1.1" {
		t.Fatalf("NextProtos = %v, want [http/1.1]", got)
	}
	if config.ClientAuth != tls.NoClientCert {
		t.Fatalf("ClientAuth = %v, want NoClientCert", config.ClientAuth)
	}
}

func TestServerRejectsTLS11BeforeCredentialVerification(t *testing.T) {
	fixture := newServerFixture(t)
	client, serverDone := fixture.start(t)
	clientConfig := fixture.clientTLSConfig()
	clientConfig.MaxVersion = tls.VersionTLS11
	clientConfig.MinVersion = tls.VersionTLS11

	if err := tls.Client(client, clientConfig).Handshake(); err == nil {
		t.Fatal("TLS 1.1 handshake unexpectedly succeeded")
	}
	if err := <-serverDone; !errors.Is(err, errServerTLS) {
		t.Fatalf("ServeConn() error = %v, want TLS rejection", err)
	}
	if fixture.verifier.calls != 0 || fixture.admission.calls != 0 || fixture.dialer.calls != 0 {
		t.Fatalf("pre-TLS dependencies called: verify=%d admit=%d dial=%d", fixture.verifier.calls, fixture.admission.calls, fixture.dialer.calls)
	}
}

func TestServerRejectsMissingOrWrongALPNBeforeCONNECT(t *testing.T) {
	for _, protocols := range [][]string{nil, {"h2"}, {"ws024/1"}} {
		t.Run(strings.Join(protocols, ","), func(t *testing.T) {
			fixture := newServerFixture(t)
			client, serverDone := fixture.start(t)
			clientConfig := fixture.clientTLSConfig()
			clientConfig.NextProtos = protocols
			tlsClient := tls.Client(client, clientConfig)
			_ = tlsClient.SetDeadline(time.Now().Add(time.Second))
			if err := tlsClient.Handshake(); err != nil && len(protocols) == 0 {
				t.Fatalf("missing-ALPN client handshake error = %v", err)
			}
			_ = tlsClient.Close()

			if err := <-serverDone; !errors.Is(err, errServerALPN) && !errors.Is(err, errServerTLS) {
				t.Fatalf("ServeConn() error = %v, want ALPN/TLS rejection", err)
			}
			if fixture.verifier.calls != 0 || fixture.admission.calls != 0 || fixture.dialer.calls != 0 {
				t.Fatalf("post-handshake dependencies called: verify=%d admit=%d dial=%d", fixture.verifier.calls, fixture.admission.calls, fixture.dialer.calls)
			}
		})
	}
}

func TestServerSuccessfulSessionDoesNotWaitForTLSCloseNotify(t *testing.T) {
	fixture := newServerFixture(t)
	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	if _, err := io.WriteString(tlsClient, validConnectWithCredential("qa-secret")); err != nil {
		t.Fatalf("Write() error = %v", err)
	}
	if got := readHeader(t, tlsClient); got != successConnectResponse {
		t.Fatalf("response = %q, want 200", got)
	}
	select {
	case err := <-serverDone:
		if err != nil {
			t.Fatalf("ServeConn() error = %v", err)
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("ServeConn() waited for TLS close_notify instead of closing the raw TCP connection")
	}
}

func TestServerClearsConnectHeaderDeadlineBeforeCredentialVerification(t *testing.T) {
	fixture := newServerFixture(t)
	fixture.verifier.onVerify = func() {
		fixture.verifier.deadlineCleared = fixture.raw != nil && fixture.raw.lastDeadline().IsZero()
	}
	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	if _, err := io.WriteString(tlsClient, validConnectWithCredential("qa-secret")); err != nil {
		t.Fatalf("Write() error = %v", err)
	}
	if got := readHeader(t, tlsClient); got != successConnectResponse {
		t.Fatalf("response = %q, want 200", got)
	}
	if err := <-serverDone; err != nil {
		t.Fatalf("ServeConn() error = %v", err)
	}
	if !fixture.verifier.deadlineCleared {
		t.Fatal("CONNECT header deadline was still active during credential verification")
	}
}

func TestServerSuccessfulSessionUsesStrictOrderAndBufferedBytes(t *testing.T) {
	fixture := newServerFixture(t)
	opaque := []byte("\x16\x03\x03opaque-inner-tls")
	fixture.pump = func(
		ctx context.Context,
		downstream net.Conn,
		downstreamReader io.Reader,
		upstream net.Conn,
		limits PumpLimits,
	) (PumpResult, error) {
		fixture.sequence.add("pump")
		got := make([]byte, len(opaque))
		if _, err := io.ReadFull(downstreamReader, got); err != nil {
			return PumpResult{}, err
		}
		if string(got) != string(opaque) {
			return PumpResult{}, errors.New("buffered bytes mismatch")
		}
		return PumpResult{DownstreamToUpstreamBytes: int64(len(got)), UpstreamToDownstreamBytes: 19}, nil
	}

	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	request := validConnectWithCredential("qa-secret")
	if _, err := tlsClient.Write(append([]byte(request), opaque...)); err != nil {
		t.Fatalf("Write(CONNECT) error = %v", err)
	}
	response := readHeader(t, tlsClient)
	if response != successConnectResponse {
		t.Fatalf("response = %q, want exact 200", response)
	}
	if err := <-serverDone; err != nil {
		t.Fatalf("ServeConn() error = %v", err)
	}

	wantOrder := []string{"verify", "admit", "dial", "pump", "audit", "release"}
	if got := fixture.sequence.snapshot(); !equalStrings(got, wantOrder) {
		t.Fatalf("order = %v, want %v", got, wantOrder)
	}
	if fixture.audit.lastResult != AuditResultSuccess {
		t.Fatalf("audit result = %v, want success", fixture.audit.lastResult)
	}
	if fixture.audit.lastRecord.downstreamBucket != auditBytesOneToFourKiB {
		t.Fatalf("audit downstream bucket = %v", fixture.audit.lastRecord.downstreamBucket)
	}
	if fixture.verifier.lastPeer != netip.MustParseAddr("198.51.100.20") {
		t.Fatalf("peer = %v", fixture.verifier.lastPeer)
	}
	if !fixture.verifier.lastRawCleared() {
		t.Fatal("credential buffer was not cleared after verification")
	}
	if fixture.upstream.closedCount() != 1 {
		t.Fatalf("upstream close count = %d, want 1", fixture.upstream.closedCount())
	}
}

func TestServerAuditDoesNotExposeOpaquePayloadTokenExactSizeOrCertificate(t *testing.T) {
	fixture := newServerFixture(t)
	var auditOutput bytes.Buffer
	audit, err := NewSafeAudit(&auditOutput)
	if err != nil {
		t.Fatalf("NewSafeAudit() error = %v", err)
	}
	fixture.config.AuditRecorder = audit
	token := "qa-token-never-audit"
	canary := "opaque-inner-canary-never-audit"
	opaque := append([]byte("\x16\x03\x03"+canary), bytes.Repeat([]byte{0xa5}, 4_321)...)
	fixture.pump = func(
		ctx context.Context,
		downstream net.Conn,
		downstreamReader io.Reader,
		upstream net.Conn,
		limits PumpLimits,
	) (PumpResult, error) {
		got := make([]byte, len(opaque))
		if _, err := io.ReadFull(downstreamReader, got); err != nil {
			return PumpResult{}, err
		}
		if !bytes.Equal(got, opaque) {
			return PumpResult{}, errors.New("opaque input mismatch")
		}
		return PumpResult{
			DownstreamToUpstreamBytes: int64(len(got)),
			UpstreamToDownstreamBytes: 7_777,
		}, nil
	}
	server, err := newServer(fixture.config, func(
		ctx context.Context,
		downstream net.Conn,
		downstreamReader io.Reader,
		upstream net.Conn,
		limits PumpLimits,
	) (PumpResult, error) {
		return fixture.pump(ctx, downstream, downstreamReader, upstream, limits)
	}, time.Now)
	if err != nil {
		t.Fatalf("newServer() error = %v", err)
	}
	fixture.server = server

	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	request := append([]byte(validConnectWithCredential(token)), opaque...)
	_ = tlsClient.SetDeadline(time.Now().Add(2 * time.Second))
	writeDone := make(chan error, 1)
	go func() {
		_, err := tlsClient.Write(request)
		writeDone <- err
	}()
	if got := readHeader(t, tlsClient); got != successConnectResponse {
		t.Fatalf("response = %q, want exact 200", got)
	}
	if err := <-writeDone; err != nil {
		t.Fatalf("Write() error = %v", err)
	}
	if err := <-serverDone; err != nil {
		t.Fatalf("ServeConn() error = %v", err)
	}

	auditBytes := auditOutput.Bytes()
	for _, forbidden := range [][]byte{
		[]byte(token),
		[]byte(canary),
		[]byte(strconv.Itoa(len(opaque))),
		[]byte(FixedAuthority),
		[]byte("Authorization"),
		[]byte("POST "),
		[]byte("Content-Type"),
		fixture.serverCert.Certificate[0],
	} {
		if len(forbidden) != 0 && bytes.Contains(auditBytes, forbidden) {
			t.Fatalf("audit exposed forbidden material")
		}
	}
	if !bytes.Contains(auditBytes, []byte(`"result":"success"`)) {
		t.Fatalf("audit result is not the closed success token: %s", auditBytes)
	}
}

func TestServerRejectsMalformedCONNECTWithGenericEmptyResponse(t *testing.T) {
	fixture := newServerFixture(t)
	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	secret := "must-not-reflect-this-secret"
	malformed := "CONNECT attacker.example:443 HTTP/1.1\r\nAuthorization: Bearer " + secret + "\r\n\r\n"
	if _, err := io.WriteString(tlsClient, malformed); err != nil {
		t.Fatalf("Write() error = %v", err)
	}
	response := readHeader(t, tlsClient)
	assertGenericEmptyResponse(t, response, "400 Bad Request", secret, "attacker.example")
	if err := <-serverDone; !errors.Is(err, errServerProtocol) {
		t.Fatalf("ServeConn() error = %v, want protocol rejection", err)
	}
	if fixture.verifier.calls != 0 || fixture.admission.calls != 0 || fixture.dialer.calls != 0 {
		t.Fatalf("dependencies called after parser rejection: verify=%d admit=%d dial=%d", fixture.verifier.calls, fixture.admission.calls, fixture.dialer.calls)
	}
	if fixture.audit.lastResult != AuditResultProtocolRejected {
		t.Fatalf("audit result = %v", fixture.audit.lastResult)
	}
}

func TestServerCredentialRejectionStopsBeforeAdmissionAndClearsToken(t *testing.T) {
	fixture := newServerFixture(t)
	fixture.verifier.err = ErrCredentialRejected
	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	secret := "wrong-secret-token"
	if _, err := io.WriteString(tlsClient, validConnectWithCredential(secret)); err != nil {
		t.Fatalf("Write() error = %v", err)
	}
	response := readHeader(t, tlsClient)
	assertGenericEmptyResponse(t, response, "401 Unauthorized", secret, FixedAuthority)
	if err := <-serverDone; !errors.Is(err, errServerCredential) {
		t.Fatalf("ServeConn() error = %v, want credential rejection", err)
	}
	if fixture.admission.calls != 0 || fixture.dialer.calls != 0 {
		t.Fatalf("later stages called: admit=%d dial=%d", fixture.admission.calls, fixture.dialer.calls)
	}
	if !fixture.verifier.lastRawCleared() {
		t.Fatal("rejected credential buffer was not cleared")
	}
	if fixture.audit.lastResult != AuditResultCredentialRejected {
		t.Fatalf("audit result = %v", fixture.audit.lastResult)
	}
}

func TestServerAdmissionRejectionStopsBeforeDial(t *testing.T) {
	fixture := newServerFixture(t)
	fixture.admission.err = ErrAdmissionRejected
	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	if _, err := io.WriteString(tlsClient, validConnectWithCredential("qa-secret")); err != nil {
		t.Fatalf("Write() error = %v", err)
	}
	response := readHeader(t, tlsClient)
	assertGenericEmptyResponse(t, response, "429 Too Many Requests")
	if err := <-serverDone; !errors.Is(err, errServerAdmission) {
		t.Fatalf("ServeConn() error = %v, want admission rejection", err)
	}
	if fixture.dialer.calls != 0 || fixture.admission.releases != 0 {
		t.Fatalf("dial/releases = %d/%d, want 0/0", fixture.dialer.calls, fixture.admission.releases)
	}
	if fixture.audit.lastResult != AuditResultAdmissionRejected {
		t.Fatalf("audit result = %v", fixture.audit.lastResult)
	}
}

func TestServerUpstreamFailureReleasesAdmissionAndReturnsGeneric502(t *testing.T) {
	fixture := newServerFixture(t)
	fixture.dialer.err = errUpstreamDial
	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	if _, err := io.WriteString(tlsClient, validConnectWithCredential("qa-secret")); err != nil {
		t.Fatalf("Write() error = %v", err)
	}
	response := readHeader(t, tlsClient)
	assertGenericEmptyResponse(t, response, "502 Bad Gateway", FixedAuthority)
	if err := <-serverDone; !errors.Is(err, errServerUpstream) {
		t.Fatalf("ServeConn() error = %v, want upstream rejection", err)
	}
	if fixture.admission.releases != 1 {
		t.Fatalf("release count = %d, want 1", fixture.admission.releases)
	}
	if fixture.audit.lastResult != AuditResultUpstreamUnavailable {
		t.Fatalf("audit result = %v", fixture.audit.lastResult)
	}
}

func TestServerPumpFailureAfter200AuditsButWritesNoSecondHTTPResponse(t *testing.T) {
	fixture := newServerFixture(t)
	fixture.pump = func(context.Context, net.Conn, io.Reader, net.Conn, PumpLimits) (PumpResult, error) {
		fixture.sequence.add("pump")
		return PumpResult{DownstreamToUpstreamBytes: 10}, errPumpByteLimit
	}
	client, serverDone := fixture.start(t)
	tlsClient := fixture.handshake(t, client)
	if _, err := io.WriteString(tlsClient, validConnectWithCredential("qa-secret")); err != nil {
		t.Fatalf("Write() error = %v", err)
	}
	if got := readHeader(t, tlsClient); got != successConnectResponse {
		t.Fatalf("response = %q, want 200", got)
	}
	if err := <-serverDone; !errors.Is(err, errServerPump) {
		t.Fatalf("ServeConn() error = %v, want pump failure", err)
	}
	_ = tlsClient.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
	rest, _ := io.ReadAll(tlsClient)
	if strings.Contains(string(rest), "HTTP/") {
		t.Fatalf("server wrote a second HTTP response after tunnel establishment: %q", rest)
	}
	if fixture.audit.lastResult != AuditResultPumpByteLimit {
		t.Fatalf("audit result = %v, want byte limit", fixture.audit.lastResult)
	}
	if fixture.admission.releases != 1 {
		t.Fatalf("release count = %d, want 1", fixture.admission.releases)
	}
}

func TestNewServerDeepCopiesOuterTLSPolicy(t *testing.T) {
	fixture := newServerFixture(t)
	certificate := cloneTestCertificate(fixture.serverCert)
	original := OuterTLSConfig(certificate)
	config := fixture.config
	config.TLSConfig = original

	server, err := NewServer(config)
	if err != nil {
		t.Fatalf("NewServer() error = %v", err)
	}
	wantDER := server.tlsConfig.Certificates[0].Certificate[0][0]
	original.NextProtos[0] = "h2"
	original.Certificates[0].Certificate[0][0] ^= 0xff
	original.Certificates = nil

	if got := server.tlsConfig.NextProtos; len(got) != 1 || got[0] != "http/1.1" {
		t.Fatalf("server NextProtos mutated through caller config: %v", got)
	}
	if got := server.tlsConfig.Certificates[0].Certificate[0][0]; got != wantDER {
		t.Fatalf("server certificate DER mutated through caller config: got=%d want=%d", got, wantDER)
	}
}

func TestNewServerRejectsNilDependenciesAndUnsafeTimeouts(t *testing.T) {
	fixture := newServerFixture(t)
	valid := fixture.config
	cases := []ServerConfig{
		{},
		withServerConfig(valid, func(c *ServerConfig) { c.TLSConfig = nil }),
		withServerConfig(valid, func(c *ServerConfig) { c.CredentialVerifier = nil }),
		withServerConfig(valid, func(c *ServerConfig) { c.AdmissionController = nil }),
		withServerConfig(valid, func(c *ServerConfig) { c.UpstreamDialer = nil }),
		withServerConfig(valid, func(c *ServerConfig) { c.AuditRecorder = nil }),
		withServerConfig(valid, func(c *ServerConfig) { c.HandshakeTimeout = 0 }),
		withServerConfig(valid, func(c *ServerConfig) { c.HandshakeTimeout = 11 * time.Second }),
		withServerConfig(valid, func(c *ServerConfig) { c.ConnectHeaderTimeout = 11 * time.Second }),
		withServerConfig(valid, func(c *ServerConfig) { c.UpstreamConnectTimeout = 11 * time.Second }),
		withServerConfig(valid, func(c *ServerConfig) { c.PumpLimits.MaxBytesPerDirection = maxPumpBytesPerDirection + 1 }),
		withServerConfig(valid, func(c *ServerConfig) { c.TLSConfig = OuterTLSConfig(tls.Certificate{}) }),
		withServerConfig(valid, func(c *ServerConfig) { c.TLSConfig.MaxVersion = tls.VersionTLS11 }),
		withServerConfig(valid, func(c *ServerConfig) { c.TLSConfig.KeyLogWriter = io.Discard }),
	}
	for i, config := range cases {
		server, err := NewServer(config)
		if server != nil || !errors.Is(err, ErrServerConfiguration) {
			t.Fatalf("case %d: NewServer() = (%v, %v), want generic config rejection", i, server, err)
		}
	}
}

func TestServeConnRejectsNilOrInvalidPeerBeforeTLS(t *testing.T) {
	fixture := newServerFixture(t)
	if err := fixture.server.ServeConn(context.Background(), nil); !errors.Is(err, errServerConnection) {
		t.Fatalf("ServeConn(nil) error = %v", err)
	}
	client, server := net.Pipe()
	defer client.Close()
	if err := fixture.server.ServeConn(context.Background(), server); !errors.Is(err, errServerConnection) {
		t.Fatalf("ServeConn(pipe) error = %v", err)
	}
}

type serverFixture struct {
	config     ServerConfig
	server     *Server
	verifier   *recordingVerifier
	admission  *recordingAdmission
	dialer     *recordingFixedDialer
	audit      *recordingAudit
	upstream   *serverTestConn
	sequence   *sequenceRecorder
	pump       PumpFunc
	serverCert tls.Certificate
	raw        *remoteAddrConn
}

func newServerFixture(t *testing.T) *serverFixture {
	t.Helper()
	sequence := &sequenceRecorder{}
	upstream := &serverTestConn{remote: &net.TCPAddr{IP: net.ParseIP("8.8.8.8"), Port: 443}}
	fixture := &serverFixture{
		verifier:   &recordingVerifier{sequence: sequence, grant: CredentialGrant{ID: "qa-id", ExpiresAt: time.Now().Add(time.Hour)}},
		admission:  &recordingAdmission{sequence: sequence},
		dialer:     &recordingFixedDialer{sequence: sequence, conn: upstream, ip: netip.MustParseAddr("8.8.8.8")},
		audit:      &recordingAudit{sequence: sequence},
		upstream:   upstream,
		sequence:   sequence,
		serverCert: testServerCertificate(t),
	}
	fixture.pump = func(context.Context, net.Conn, io.Reader, net.Conn, PumpLimits) (PumpResult, error) {
		fixture.sequence.add("pump")
		return PumpResult{}, nil
	}
	fixture.config = ServerConfig{
		TLSConfig:              OuterTLSConfig(fixture.serverCert),
		CredentialVerifier:     fixture.verifier,
		AdmissionController:    fixture.admission,
		UpstreamDialer:         fixture.dialer,
		AuditRecorder:          fixture.audit,
		PumpLimits:             serverTestPumpLimits(),
		HandshakeTimeout:       time.Second,
		ConnectHeaderTimeout:   time.Second,
		UpstreamConnectTimeout: time.Second,
	}
	server, err := newServer(fixture.config, func(
		ctx context.Context,
		downstream net.Conn,
		downstreamReader io.Reader,
		upstream net.Conn,
		limits PumpLimits,
	) (PumpResult, error) {
		return fixture.pump(ctx, downstream, downstreamReader, upstream, limits)
	}, time.Now)
	if err != nil {
		t.Fatalf("newServer() error = %v", err)
	}
	fixture.server = server
	return fixture
}

func (fixture *serverFixture) start(t *testing.T) (net.Conn, <-chan error) {
	t.Helper()
	client, rawServer := net.Pipe()
	serverConn := &remoteAddrConn{
		Conn:   rawServer,
		remote: &net.TCPAddr{IP: net.ParseIP("198.51.100.20"), Port: 43123},
	}
	fixture.raw = serverConn
	done := make(chan error, 1)
	go func() {
		done <- fixture.server.ServeConn(context.Background(), serverConn)
	}()
	return client, done
}

func (fixture *serverFixture) clientTLSConfig() *tls.Config {
	return &tls.Config{
		InsecureSkipVerify: true, // synthetic certificate, verified only inside this unit test.
		ServerName:         "relay.test",
		MinVersion:         tls.VersionTLS12,
		NextProtos:         []string{"http/1.1"},
	}
}

func (fixture *serverFixture) handshake(t *testing.T, conn net.Conn) *tls.Conn {
	t.Helper()
	client := tls.Client(conn, fixture.clientTLSConfig())
	_ = client.SetDeadline(time.Now().Add(time.Second))
	if err := client.Handshake(); err != nil {
		t.Fatalf("Handshake() error = %v", err)
	}
	_ = client.SetDeadline(time.Time{})
	return client
}

type remoteAddrConn struct {
	net.Conn
	remote   net.Addr
	mu       sync.Mutex
	deadline time.Time
}

func (c *remoteAddrConn) RemoteAddr() net.Addr { return c.remote }
func (c *remoteAddrConn) SetDeadline(deadline time.Time) error {
	c.mu.Lock()
	c.deadline = deadline
	c.mu.Unlock()
	return c.Conn.SetDeadline(deadline)
}
func (c *remoteAddrConn) lastDeadline() time.Time {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.deadline
}

type recordingVerifier struct {
	sequence        *sequenceRecorder
	grant           CredentialGrant
	err             error
	calls           int
	lastRaw         []byte
	lastPeer        netip.Addr
	onVerify        func()
	deadlineCleared bool
}

func (v *recordingVerifier) Verify(_ context.Context, raw []byte, peer netip.Addr) (CredentialGrant, error) {
	v.calls++
	v.sequence.add("verify")
	v.lastRaw = raw
	v.lastPeer = peer
	if v.onVerify != nil {
		v.onVerify()
	}
	return v.grant, v.err
}

func (v *recordingVerifier) lastRawCleared() bool {
	if len(v.lastRaw) == 0 {
		return false
	}
	for _, b := range v.lastRaw {
		if b != 0 {
			return false
		}
	}
	return true
}

type recordingAdmission struct {
	sequence *sequenceRecorder
	err      error
	calls    int
	releases int
}

func (a *recordingAdmission) Admit(context.Context, string, netip.Addr) (func(), error) {
	a.calls++
	a.sequence.add("admit")
	if a.err != nil {
		return nil, a.err
	}
	var once sync.Once
	return func() {
		once.Do(func() {
			a.releases++
			a.sequence.add("release")
		})
	}, nil
}

type recordingFixedDialer struct {
	sequence *sequenceRecorder
	conn     net.Conn
	ip       netip.Addr
	err      error
	calls    int
}

func (d *recordingFixedDialer) DialContext(context.Context) (net.Conn, netip.Addr, error) {
	d.calls++
	d.sequence.add("dial")
	return d.conn, d.ip, d.err
}

type recordingAudit struct {
	sequence   *sequenceRecorder
	err        error
	calls      int
	lastResult AuditResultCode
	lastRecord AuditRecord
}

func (a *recordingAudit) Record(record AuditRecord) error {
	a.calls++
	a.sequence.add("audit")
	a.lastResult = record.result
	a.lastRecord = record
	return a.err
}

type sequenceRecorder struct {
	mu     sync.Mutex
	values []string
}

func (r *sequenceRecorder) add(value string) {
	r.mu.Lock()
	r.values = append(r.values, value)
	r.mu.Unlock()
}

func (r *sequenceRecorder) snapshot() []string {
	r.mu.Lock()
	defer r.mu.Unlock()
	return append([]string(nil), r.values...)
}

type serverTestConn struct {
	mu         sync.Mutex
	remote     net.Addr
	closeCalls int
}

func (c *serverTestConn) Read([]byte) (int, error)         { return 0, io.EOF }
func (c *serverTestConn) Write(p []byte) (int, error)      { return len(p), nil }
func (c *serverTestConn) Close() error                     { c.mu.Lock(); c.closeCalls++; c.mu.Unlock(); return nil }
func (c *serverTestConn) LocalAddr() net.Addr              { return &net.TCPAddr{} }
func (c *serverTestConn) RemoteAddr() net.Addr             { return c.remote }
func (c *serverTestConn) SetDeadline(time.Time) error      { return nil }
func (c *serverTestConn) SetReadDeadline(time.Time) error  { return nil }
func (c *serverTestConn) SetWriteDeadline(time.Time) error { return nil }
func (c *serverTestConn) CloseWrite() error                { return nil }
func (c *serverTestConn) closedCount() int                 { c.mu.Lock(); defer c.mu.Unlock(); return c.closeCalls }

func validConnectWithCredential(credential string) string {
	return "CONNECT " + FixedAuthority + " HTTP/1.1\r\n" +
		"Host: " + FixedAuthority + "\r\n" +
		"Authorization: Bearer " + credential + "\r\n" +
		"X-WS024-Tunnel-Version: " + TunnelProtocolVersion + "\r\n\r\n"
}

func readHeader(t *testing.T, reader io.Reader) string {
	t.Helper()
	buffered := bufio.NewReader(reader)
	var builder strings.Builder
	for {
		line, err := buffered.ReadString('\n')
		if err != nil {
			t.Fatalf("ReadString() error = %v, partial=%q", err, builder.String())
		}
		builder.WriteString(line)
		if strings.HasSuffix(builder.String(), "\r\n\r\n") {
			return builder.String()
		}
		if builder.Len() > maximumHeaderBytes {
			t.Fatal("response header exceeded bound")
		}
	}
}

func assertGenericEmptyResponse(t *testing.T, response, status string, forbidden ...string) {
	t.Helper()
	if !strings.HasPrefix(response, "HTTP/1.1 "+status+"\r\n") {
		t.Fatalf("response = %q, want status %s", response, status)
	}
	if !strings.Contains(response, "Content-Length: 0\r\n") || !strings.HasSuffix(response, "\r\n\r\n") {
		t.Fatalf("response is not an empty bounded response: %q", response)
	}
	for _, value := range forbidden {
		if value != "" && strings.Contains(response, value) {
			t.Fatalf("response reflects forbidden input %q: %q", value, response)
		}
	}
}

func withServerConfig(base ServerConfig, mutate func(*ServerConfig)) ServerConfig {
	copy := base
	mutate(&copy)
	return copy
}

func serverTestPumpLimits() PumpLimits {
	return PumpLimits{
		IdleTimeout:          100 * time.Millisecond,
		MaxSessionDuration:   time.Second,
		MaxBytesPerDirection: 64 * 1024,
	}
}

var (
	testCertificateOnce sync.Once
	testCertificate     tls.Certificate
	testCertificateErr  error
)

func testServerCertificate(t *testing.T) tls.Certificate {
	t.Helper()
	testCertificateOnce.Do(func() {
		key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		if err != nil {
			testCertificateErr = err
			return
		}
		template := &x509.Certificate{
			SerialNumber: big.NewInt(1),
			Subject:      pkix.Name{CommonName: "relay.test"},
			DNSNames:     []string{"relay.test"},
			NotBefore:    time.Now().Add(-time.Hour),
			NotAfter:     time.Now().Add(time.Hour),
			KeyUsage:     x509.KeyUsageDigitalSignature,
			ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		}
		der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
		if err != nil {
			testCertificateErr = err
			return
		}
		testCertificate = tls.Certificate{Certificate: [][]byte{der}, PrivateKey: key}
	})
	if testCertificateErr != nil {
		t.Fatalf("create certificate: %v", testCertificateErr)
	}
	return testCertificate
}

func cloneTestCertificate(certificate tls.Certificate) tls.Certificate {
	copy := certificate
	copy.Certificate = make([][]byte, len(certificate.Certificate))
	for i := range certificate.Certificate {
		copy.Certificate[i] = append([]byte(nil), certificate.Certificate[i]...)
	}
	copy.OCSPStaple = append([]byte(nil), certificate.OCSPStaple...)
	copy.SignedCertificateTimestamps = make([][]byte, len(certificate.SignedCertificateTimestamps))
	for i := range certificate.SignedCertificateTimestamps {
		copy.SignedCertificateTimestamps[i] = append([]byte(nil), certificate.SignedCertificateTimestamps[i]...)
	}
	return copy
}

func equalStrings(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
