//go:build integration

package main

import (
	"context"
	"crypto/tls"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"net/netip"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/zaguzovmaksim0-hue/workspace-47/ws024-relay/internal/relay"
)

var errIntegrationConfiguration = errors.New("invalid integration configuration")

type integrationConfig struct {
	listen    string
	tlsCert   string
	tlsKey    string
	upstream  netip.AddrPort
	auditFile string
	stageFile string
}

type integrationCredentialVerifier struct{}

func (integrationCredentialVerifier) Verify(
	ctx context.Context,
	raw []byte,
	peer netip.Addr,
) (relay.CredentialGrant, error) {
	if ctx == nil || ctx.Err() != nil || !peer.IsValid() || peer.Zone() != "" || len(raw) == 0 || len(raw) > 512 {
		return relay.CredentialGrant{}, relay.ErrCredentialRejected
	}
	for _, value := range raw {
		if value < '!' || value > '~' {
			return relay.CredentialGrant{}, relay.ErrCredentialRejected
		}
	}
	return relay.CredentialGrant{
		ID:        "synthetic-integration",
		ExpiresAt: time.Now().Add(time.Hour),
	}, nil
}

type integrationLoopbackDialer struct {
	upstream netip.AddrPort
	dialer   net.Dialer
}

func (dialer *integrationLoopbackDialer) DialContext(
	ctx context.Context,
) (net.Conn, netip.Addr, error) {
	if ctx == nil || ctx.Err() != nil || !validLoopbackUpstream(dialer.upstream) {
		return nil, netip.Addr{}, errIntegrationConfiguration
	}
	connection, err := dialer.dialer.DialContext(ctx, "tcp", dialer.upstream.String())
	if err != nil || connection == nil {
		if connection != nil {
			_ = connection.Close()
		}
		return nil, netip.Addr{}, errIntegrationConfiguration
	}
	remote, ok := connection.RemoteAddr().(*net.TCPAddr)
	if !ok || remote == nil || remote.Zone != "" || remote.Port != int(dialer.upstream.Port()) {
		_ = connection.Close()
		return nil, netip.Addr{}, errIntegrationConfiguration
	}
	remoteIP, ok := netip.AddrFromSlice(remote.IP)
	if !ok || remoteIP.Unmap() != dialer.upstream.Addr() {
		_ = connection.Close()
		return nil, netip.Addr{}, errIntegrationConfiguration
	}
	return connection, dialer.upstream.Addr(), nil
}

func parseConfig(args []string) (integrationConfig, error) {
	var config integrationConfig
	var upstream string
	flags := flag.NewFlagSet("ws024-relay-integration", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.StringVar(&config.listen, "listen", "", "")
	flags.StringVar(&config.tlsCert, "tls-cert", "", "")
	flags.StringVar(&config.tlsKey, "tls-key", "", "")
	flags.StringVar(&upstream, "upstream", "", "")
	flags.StringVar(&config.auditFile, "audit-file", "", "")
	flags.StringVar(&config.stageFile, "stage-file", "", "")
	if err := flags.Parse(args); err != nil || flags.NArg() != 0 {
		return integrationConfig{}, errIntegrationConfiguration
	}
	parsedUpstream, err := netip.ParseAddrPort(upstream)
	if err != nil {
		return integrationConfig{}, errIntegrationConfiguration
	}
	config.upstream = parsedUpstream
	if !validConfig(config) {
		return integrationConfig{}, errIntegrationConfiguration
	}
	return config, nil
}

func validConfig(config integrationConfig) bool {
	if config.listen != "127.0.0.1:0" || !validLoopbackUpstream(config.upstream) {
		return false
	}
	for _, path := range []string{config.tlsCert, config.tlsKey, config.auditFile, config.stageFile} {
		if path == "" || len(path) > 4096 || strings.IndexFunc(path, func(r rune) bool { return r < 0x20 || r == 0x7f }) >= 0 {
			return false
		}
	}
	return true
}

func validLoopbackUpstream(upstream netip.AddrPort) bool {
	address := upstream.Addr()
	return upstream.IsValid() && upstream.Port() != 0 && address.IsValid() &&
		address.Zone() == "" && address == address.Unmap() && address.IsLoopback()
}

func openPrivateOutput(path string) (*os.File, error) {
	if _, err := os.Lstat(path); !os.IsNotExist(err) {
		return nil, errIntegrationConfiguration
	}
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
	if err != nil {
		return nil, errIntegrationConfiguration
	}
	return file, nil
}

func buildServer(config integrationConfig, auditWriter io.Writer) (*relay.Server, error) {
	certificate, err := tls.LoadX509KeyPair(config.tlsCert, config.tlsKey)
	if err != nil {
		return nil, errIntegrationConfiguration
	}
	admission, err := relay.NewAdmissionController(relay.AdmissionLimits{
		MaxPerCredential: 2,
		MaxPerPeer:       4,
		MaxGlobal:        8,
	})
	if err != nil {
		return nil, errIntegrationConfiguration
	}
	audit, err := relay.NewSafeAudit(auditWriter)
	if err != nil {
		return nil, errIntegrationConfiguration
	}
	server, err := relay.NewServer(relay.ServerConfig{
		TLSConfig:           relay.OuterTLSConfig(certificate),
		CredentialVerifier:  integrationCredentialVerifier{},
		AdmissionController: admission,
		UpstreamDialer:      &integrationLoopbackDialer{upstream: config.upstream},
		AuditRecorder:       audit,
		PumpLimits: relay.PumpLimits{
			IdleTimeout:          5 * time.Second,
			MaxSessionDuration:   20 * time.Second,
			MaxBytesPerDirection: 4 * 1024 * 1024,
		},
		HandshakeTimeout:       5 * time.Second,
		ConnectHeaderTimeout:   5 * time.Second,
		UpstreamConnectTimeout: 5 * time.Second,
	})
	if err != nil {
		return nil, errIntegrationConfiguration
	}
	return server, nil
}

func serve(
	ctx context.Context,
	listener net.Listener,
	server *relay.Server,
	stageWriter io.Writer,
) error {
	if ctx == nil || listener == nil || server == nil || stageWriter == nil {
		return errIntegrationConfiguration
	}
	stopClose := context.AfterFunc(ctx, func() { _ = listener.Close() })
	defer stopClose()
	var sessions sync.WaitGroup
	var stageMu sync.Mutex
	defer sessions.Wait()
	for {
		connection, err := listener.Accept()
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return errIntegrationConfiguration
		}
		sessions.Add(1)
		go func() {
			defer sessions.Done()
			stage := relay.IntegrationStageForError(server.ServeConn(ctx, connection))
			stageMu.Lock()
			_, _ = fmt.Fprintln(stageWriter, stage)
			stageMu.Unlock()
		}()
	}
}

func run(ctx context.Context, args []string, stdout io.Writer) error {
	config, err := parseConfig(args)
	if err != nil || stdout == nil {
		return errIntegrationConfiguration
	}
	audit, err := openPrivateOutput(config.auditFile)
	if err != nil {
		return errIntegrationConfiguration
	}
	defer audit.Close()
	stage, err := openPrivateOutput(config.stageFile)
	if err != nil {
		return errIntegrationConfiguration
	}
	defer stage.Close()
	server, err := buildServer(config, audit)
	if err != nil {
		return errIntegrationConfiguration
	}
	listener, err := net.Listen("tcp", config.listen)
	if err != nil {
		return errIntegrationConfiguration
	}
	defer listener.Close()
	address, ok := listener.Addr().(*net.TCPAddr)
	if !ok || address == nil || !address.IP.IsLoopback() || address.Port < 1 || address.Port > 65535 {
		return errIntegrationConfiguration
	}
	if _, err := fmt.Fprintf(stdout, "READY %d\n", address.Port); err != nil {
		return errIntegrationConfiguration
	}
	return serve(ctx, listener, server, stage)
}

func main() {
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()
	if err := run(ctx, os.Args[1:], os.Stdout); err != nil {
		_, _ = fmt.Fprintln(os.Stderr, "ws024-relay-integration: failed")
		os.Exit(1)
	}
}
