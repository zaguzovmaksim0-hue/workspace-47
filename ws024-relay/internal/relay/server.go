package relay

import (
	"bufio"
	"context"
	"crypto/tls"
	"errors"
	"io"
	"net"
	"net/netip"
	"sync"
	"time"
)

const (
	maxServerHandshakeTimeout       = 10 * time.Second
	maxServerConnectHeaderTimeout   = 10 * time.Second
	maxServerUpstreamConnectTimeout = 10 * time.Second

	successConnectResponse  = "HTTP/1.1 200 Connection Established\r\n\r\n"
	badRequestResponse      = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
	unauthorizedResponse    = "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
	tooManyRequestsResponse = "HTTP/1.1 429 Too Many Requests\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
	badGatewayResponse      = "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
)

var (
	ErrServerConfiguration = errors.New("invalid server configuration")

	errServerConnection = errors.New("relay connection rejected")
	errServerTLS        = errors.New("outer TLS rejected")
	errServerALPN       = errors.New("outer TLS protocol rejected")
	errServerProtocol   = errors.New("CONNECT rejected")
	errServerCredential = errors.New("credential rejected by server")
	errServerAdmission  = errors.New("session admission rejected")
	errServerUpstream   = errors.New("upstream unavailable")
	errServerResponse   = errors.New("relay response failed")
	errServerPump       = errors.New("relay pump failed")
	errServerAudit      = errors.New("relay audit failed")
)

// AuditRecorder accepts only already-sanitized AuditRecord values.
type AuditRecorder interface {
	Record(AuditRecord) error
}

// PumpFunc is the opaque bidirectional copy contract used by Server.
type PumpFunc func(
	ctx context.Context,
	downstream net.Conn,
	downstreamReader io.Reader,
	upstream net.Conn,
	limits PumpLimits,
) (PumpResult, error)

// ServerConfig contains only fixed-policy dependencies and bounded limits.
// There is deliberately no host or port field for the upstream destination.
type ServerConfig struct {
	TLSConfig              *tls.Config
	CredentialVerifier     CredentialVerifier
	AdmissionController    AdmissionController
	UpstreamDialer         FixedUpstreamDialer
	AuditRecorder          AuditRecorder
	PumpLimits             PumpLimits
	HandshakeTimeout       time.Duration
	ConnectHeaderTimeout   time.Duration
	UpstreamConnectTimeout time.Duration
}

// Server terminates only the outer TLS layer. The nested ws024 TLS stream is
// forwarded opaquely and is never parsed, logged, or persisted.
type Server struct {
	tlsConfig              *tls.Config
	credentialVerifier     CredentialVerifier
	admissionController    AdmissionController
	upstreamDialer         FixedUpstreamDialer
	auditRecorder          AuditRecorder
	pumpLimits             PumpLimits
	handshakeTimeout       time.Duration
	connectHeaderTimeout   time.Duration
	upstreamConnectTimeout time.Duration
	pump                   PumpFunc
	now                    func() time.Time
}

// OuterTLSConfig creates the only supported outer TLS policy. The relay uses
// ordinary TCP tls.Server with TLS 1.2 minimum and HTTP/1.1 ALPN only.
func OuterTLSConfig(cert tls.Certificate) *tls.Config {
	return &tls.Config{
		Certificates: []tls.Certificate{cert},
		MinVersion:   tls.VersionTLS12,
		NextProtos:   []string{"http/1.1"},
	}
}

func NewServer(config ServerConfig) (*Server, error) {
	return newServer(config, Pump, time.Now)
}

func newServer(config ServerConfig, pump PumpFunc, now func() time.Time) (*Server, error) {
	if !validServerConfig(config, pump, now) {
		return nil, ErrServerConfiguration
	}
	return &Server{
		tlsConfig:              cloneServerTLSConfig(config.TLSConfig),
		credentialVerifier:     config.CredentialVerifier,
		admissionController:    config.AdmissionController,
		upstreamDialer:         config.UpstreamDialer,
		auditRecorder:          config.AuditRecorder,
		pumpLimits:             config.PumpLimits,
		handshakeTimeout:       config.HandshakeTimeout,
		connectHeaderTimeout:   config.ConnectHeaderTimeout,
		upstreamConnectTimeout: config.UpstreamConnectTimeout,
		pump:                   pump,
		now:                    now,
	}, nil
}

func cloneServerTLSConfig(config *tls.Config) *tls.Config {
	cloned := config.Clone()
	cloned.NextProtos = append([]string(nil), config.NextProtos...)
	cloned.CipherSuites = append([]uint16(nil), config.CipherSuites...)
	cloned.CurvePreferences = append([]tls.CurveID(nil), config.CurvePreferences...)
	cloned.Certificates = make([]tls.Certificate, len(config.Certificates))
	for i, certificate := range config.Certificates {
		cloned.Certificates[i] = cloneServerCertificate(certificate)
	}
	return cloned
}

func cloneServerCertificate(certificate tls.Certificate) tls.Certificate {
	cloned := certificate
	cloned.Certificate = make([][]byte, len(certificate.Certificate))
	for i := range certificate.Certificate {
		cloned.Certificate[i] = append([]byte(nil), certificate.Certificate[i]...)
	}
	cloned.OCSPStaple = append([]byte(nil), certificate.OCSPStaple...)
	cloned.SignedCertificateTimestamps = make([][]byte, len(certificate.SignedCertificateTimestamps))
	for i := range certificate.SignedCertificateTimestamps {
		cloned.SignedCertificateTimestamps[i] = append([]byte(nil), certificate.SignedCertificateTimestamps[i]...)
	}
	return cloned
}

func validServerTLSConfig(config *tls.Config) bool {
	if config == nil || len(config.Certificates) != 1 || config.NameToCertificate != nil ||
		config.MinVersion < tls.VersionTLS12 ||
		(config.MaxVersion != 0 && config.MaxVersion < tls.VersionTLS12) ||
		(config.MaxVersion != 0 && config.MaxVersion < config.MinVersion) ||
		len(config.NextProtos) != 1 || config.NextProtos[0] != "http/1.1" ||
		config.GetConfigForClient != nil || config.GetCertificate != nil ||
		config.KeyLogWriter != nil {
		return false
	}
	certificate := config.Certificates[0]
	if certificate.PrivateKey == nil || len(certificate.Certificate) == 0 {
		return false
	}
	for _, der := range certificate.Certificate {
		if len(der) == 0 {
			return false
		}
	}
	return true
}

func validServerConfig(config ServerConfig, pump PumpFunc, now func() time.Time) bool {
	if config.TLSConfig == nil || nilInterface(config.CredentialVerifier) ||
		nilInterface(config.AdmissionController) || nilInterface(config.UpstreamDialer) ||
		nilInterface(config.AuditRecorder) || pump == nil || now == nil {
		return false
	}
	if !validServerTLSConfig(config.TLSConfig) {
		return false
	}
	if config.HandshakeTimeout <= 0 || config.HandshakeTimeout > maxServerHandshakeTimeout ||
		config.ConnectHeaderTimeout <= 0 || config.ConnectHeaderTimeout > maxServerConnectHeaderTimeout ||
		config.UpstreamConnectTimeout <= 0 || config.UpstreamConnectTimeout > maxServerUpstreamConnectTimeout {
		return false
	}
	return validPumpLimits(config.PumpLimits)
}

// ServeConn processes exactly one accepted raw TCP connection.
func (server *Server) ServeConn(ctx context.Context, raw net.Conn) error {
	if server == nil || ctx == nil || raw == nil {
		return errServerConnection
	}
	peer, ok := normalizedPeer(raw.RemoteAddr())
	if !ok {
		_ = raw.Close()
		return errServerConnection
	}

	started := server.now()
	outerTLS := tls.Server(raw, server.tlsConfig.Clone())
	downstream := &onceCloseConn{Conn: outerTLS, closeFunc: raw.Close}
	defer downstream.Close()
	stopCancellation := context.AfterFunc(ctx, func() { _ = downstream.Close() })
	defer stopCancellation()

	if err := downstream.SetDeadline(server.now().Add(server.handshakeTimeout)); err != nil {
		return server.finish(started, AuditResultProtocolRejected, PumpResult{}, errServerTLS)
	}
	if err := outerTLS.HandshakeContext(ctx); err != nil {
		return server.finish(started, AuditResultProtocolRejected, PumpResult{}, errServerTLS)
	}
	if outerTLS.ConnectionState().NegotiatedProtocol != "http/1.1" {
		return server.finish(started, AuditResultProtocolRejected, PumpResult{}, errServerALPN)
	}
	if err := downstream.SetDeadline(server.now().Add(server.connectHeaderTimeout)); err != nil {
		return server.finish(started, AuditResultProtocolRejected, PumpResult{}, errServerProtocol)
	}

	reader := bufio.NewReaderSize(downstream, maximumHeaderBytes)
	request, err := ParseFixedCONNECT(reader, maximumHeaderBytes)
	if err != nil {
		_ = writeFixedResponse(downstream, badRequestResponse)
		return server.finish(started, AuditResultProtocolRejected, PumpResult{}, errServerProtocol)
	}
	if err := downstream.SetDeadline(time.Time{}); err != nil {
		clear(request.Credential)
		return server.finish(started, AuditResultInternalFailure, PumpResult{}, errServerConnection)
	}
	credential := request.Credential
	request.Credential = nil
	grant, verifyErr := server.credentialVerifier.Verify(ctx, credential, peer)
	clear(credential)
	credential = nil
	if verifyErr != nil {
		_ = writeFixedResponse(downstream, unauthorizedResponse)
		return server.finish(started, AuditResultCredentialRejected, PumpResult{}, errServerCredential)
	}

	release, admitErr := server.admissionController.Admit(ctx, grant.ID, peer)
	if admitErr != nil || release == nil {
		_ = writeFixedResponse(downstream, tooManyRequestsResponse)
		return server.finish(started, AuditResultAdmissionRejected, PumpResult{}, errServerAdmission)
	}
	defer release()

	upstreamContext, cancelUpstream := context.WithTimeout(ctx, server.upstreamConnectTimeout)
	upstreamRaw, _, dialErr := server.upstreamDialer.DialContext(upstreamContext)
	cancelUpstream()
	if dialErr != nil || upstreamRaw == nil {
		if upstreamRaw != nil {
			_ = upstreamRaw.Close()
		}
		_ = writeFixedResponse(downstream, badGatewayResponse)
		return server.finish(started, AuditResultUpstreamUnavailable, PumpResult{}, errServerUpstream)
	}
	upstream := &onceCloseConn{Conn: upstreamRaw}
	defer upstream.Close()

	if err := writeFixedResponse(downstream, successConnectResponse); err != nil {
		return server.finish(started, AuditResultInternalFailure, PumpResult{}, errServerResponse)
	}
	pumpResult, pumpErr := server.pump(ctx, downstream, reader, upstream, server.pumpLimits)
	if pumpErr != nil {
		return server.finish(started, auditResultForPumpError(pumpErr), pumpResult, errServerPump)
	}
	return server.finish(started, AuditResultSuccess, pumpResult, nil)
}

func (server *Server) finish(
	started time.Time,
	result AuditResultCode,
	pump PumpResult,
	primary error,
) error {
	duration := server.now().Sub(started)
	if duration < 0 {
		duration = 0
	}
	record, err := NewAuditRecord(result, duration, pump)
	if err != nil {
		if primary == nil {
			return errServerAudit
		}
		return errors.Join(primary, errServerAudit)
	}
	if err := server.auditRecorder.Record(record); err != nil {
		if primary == nil {
			return errServerAudit
		}
		return errors.Join(primary, errServerAudit)
	}
	return primary
}

func normalizedPeer(address net.Addr) (netip.Addr, bool) {
	tcpAddress, ok := address.(*net.TCPAddr)
	if !ok || tcpAddress == nil || tcpAddress.Zone != "" {
		return netip.Addr{}, false
	}
	peer, ok := netip.AddrFromSlice(tcpAddress.IP)
	if !ok {
		return netip.Addr{}, false
	}
	peer = peer.Unmap()
	return peer, peer.IsValid() && peer.Zone() == ""
}

func writeFixedResponse(writer io.Writer, response string) error {
	encoded := []byte(response)
	defer clear(encoded)
	_, err := writeAll(writer, encoded)
	if err != nil {
		return errServerResponse
	}
	return nil
}

func auditResultForPumpError(err error) AuditResultCode {
	switch {
	case errors.Is(err, errPumpIdleTimeout):
		return AuditResultPumpIdleTimeout
	case errors.Is(err, errPumpSessionDuration):
		return AuditResultPumpSessionLimit
	case errors.Is(err, errPumpByteLimit):
		return AuditResultPumpByteLimit
	default:
		return AuditResultPumpIOFailure
	}
}

type onceCloseConn struct {
	net.Conn
	closeFunc func() error
	once      sync.Once
	err       error
}

func (conn *onceCloseConn) Close() error {
	conn.once.Do(func() {
		if conn.closeFunc != nil {
			conn.err = conn.closeFunc()
			return
		}
		conn.err = conn.Conn.Close()
	})
	return conn.err
}
