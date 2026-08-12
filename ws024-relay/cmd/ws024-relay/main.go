package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"net/netip"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/zaguzovmaksim0-hue/workspace-47/ws024-relay/internal/relay"
)

const (
	maxCredentialFileBytes = 64 * 1024
	maxTLSFileBytes        = 1024 * 1024
	maxConfigPathBytes     = 4096
	maxCredentialRecords   = 64

	defaultHandshakeTimeout       = 10 * time.Second
	defaultConnectHeaderTimeout   = 10 * time.Second
	defaultUpstreamConnectTimeout = 10 * time.Second
	defaultIdleTimeout            = 30 * time.Second
	defaultMaxSessionDuration     = 90 * time.Second
	defaultMaxBytesPerDirection   = int64(4 * 1024 * 1024)
	defaultMaxPerCredential       = 2
	defaultMaxPerPeer             = 4
	defaultMaxGlobal              = 64
	maxActiveConnections          = 128
)

var (
	errCLIConfig      = errors.New("invalid relay configuration")
	errCredentialFile = errors.New("invalid credential file")
	errTLSFiles       = errors.New("invalid TLS files")
	errApplication    = errors.New("relay initialization failed")
	errServe          = errors.New("relay service failed")
)

type cliConfig struct {
	listen                 string
	tlsCertPath            string
	tlsKeyPath             string
	credentialPath         string
	handshakeTimeout       time.Duration
	connectHeaderTimeout   time.Duration
	upstreamConnectTimeout time.Duration
	idleTimeout            time.Duration
	maxSessionDuration     time.Duration
	maxBytesPerDirection   int64
	maxPerCredential       int
	maxPerPeer             int
	maxGlobal              int
}

type application struct {
	listen string
	server *relay.Server
}

type connectionServer interface {
	ServeConn(context.Context, net.Conn) error
}

type credentialFileDocument struct {
	Version     int                   `json:"version"`
	Credentials []credentialFileEntry `json:"credentials"`
}

type credentialFileEntry struct {
	ID        string `json:"id"`
	SHA256    string `json:"sha256"`
	ExpiresAt string `json:"expires_at"`
	Revoked   bool   `json:"revoked"`
}

func parseConfig(args []string) (cliConfig, error) {
	var config cliConfig
	flags := flag.NewFlagSet("ws024-relay", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.StringVar(&config.listen, "listen", "", "")
	flags.StringVar(&config.tlsCertPath, "tls-cert", "", "")
	flags.StringVar(&config.tlsKeyPath, "tls-key", "", "")
	flags.StringVar(&config.credentialPath, "qa-credentials", "", "")
	flags.DurationVar(&config.handshakeTimeout, "handshake-timeout", defaultHandshakeTimeout, "")
	flags.DurationVar(&config.connectHeaderTimeout, "connect-header-timeout", defaultConnectHeaderTimeout, "")
	flags.DurationVar(&config.upstreamConnectTimeout, "upstream-connect-timeout", defaultUpstreamConnectTimeout, "")
	flags.DurationVar(&config.idleTimeout, "idle-timeout", defaultIdleTimeout, "")
	flags.DurationVar(&config.maxSessionDuration, "max-session-duration", defaultMaxSessionDuration, "")
	flags.Int64Var(&config.maxBytesPerDirection, "max-bytes-per-direction", defaultMaxBytesPerDirection, "")
	flags.IntVar(&config.maxPerCredential, "max-per-credential", defaultMaxPerCredential, "")
	flags.IntVar(&config.maxPerPeer, "max-per-peer", defaultMaxPerPeer, "")
	flags.IntVar(&config.maxGlobal, "max-global", defaultMaxGlobal, "")

	if err := flags.Parse(args); err != nil || flags.NArg() != 0 || !validCLIConfig(config) {
		return cliConfig{}, errCLIConfig
	}
	return config, nil
}

func validCLIConfig(config cliConfig) bool {
	return validListenAddress(config.listen) &&
		validConfigPath(config.tlsCertPath) &&
		validConfigPath(config.tlsKeyPath) &&
		validConfigPath(config.credentialPath) &&
		config.handshakeTimeout > 0 && config.handshakeTimeout <= defaultHandshakeTimeout &&
		config.connectHeaderTimeout > 0 && config.connectHeaderTimeout <= defaultConnectHeaderTimeout &&
		config.upstreamConnectTimeout > 0 && config.upstreamConnectTimeout <= defaultUpstreamConnectTimeout &&
		config.idleTimeout > 0 && config.idleTimeout <= defaultIdleTimeout &&
		config.maxSessionDuration > 0 && config.maxSessionDuration <= defaultMaxSessionDuration &&
		config.maxBytesPerDirection > 0 && config.maxBytesPerDirection <= defaultMaxBytesPerDirection &&
		config.maxPerCredential > 0 && config.maxPerCredential <= defaultMaxPerCredential &&
		config.maxPerPeer > 0 && config.maxPerPeer <= defaultMaxPerPeer &&
		config.maxGlobal > 0 && config.maxGlobal <= defaultMaxGlobal
}

func validListenAddress(address string) bool {
	if address == "" || len(address) > 512 || strings.IndexFunc(address, isControl) >= 0 {
		return false
	}
	host, portText, err := net.SplitHostPort(address)
	if err != nil {
		return false
	}
	port, err := strconv.Atoi(portText)
	if err != nil || port < 1 || port > 65535 {
		return false
	}
	if host == "" {
		return true
	}
	ip, err := netip.ParseAddr(host)
	return err == nil && ip.IsValid() && ip.Zone() == ""
}

func validConfigPath(path string) bool {
	return path != "" && len(path) <= maxConfigPathBytes && strings.IndexFunc(path, isControl) < 0
}

func isControl(r rune) bool {
	return r < 0x20 || r == 0x7f
}

func loadCredentialRecords(path string) ([]relay.CredentialRecord, error) {
	raw, err := readRegularFile(path, maxCredentialFileBytes, true)
	if err != nil {
		return nil, errCredentialFile
	}
	defer clear(raw)

	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	var document credentialFileDocument
	if err := decoder.Decode(&document); err != nil {
		return nil, errCredentialFile
	}
	var trailing any
	if err := decoder.Decode(&trailing); err != io.EOF {
		return nil, errCredentialFile
	}
	if document.Version != 1 || len(document.Credentials) == 0 || len(document.Credentials) > maxCredentialRecords {
		return nil, errCredentialFile
	}

	records := make([]relay.CredentialRecord, len(document.Credentials))
	for i, entry := range document.Credentials {
		digest, ok := parseCanonicalDigest(entry.SHA256)
		if !ok {
			return nil, errCredentialFile
		}
		expiresAt, ok := parseCanonicalUTC(entry.ExpiresAt)
		if !ok {
			return nil, errCredentialFile
		}
		records[i] = relay.CredentialRecord{
			ID:        entry.ID,
			Digest:    digest,
			ExpiresAt: expiresAt,
			Revoked:   entry.Revoked,
		}
	}
	if _, err := relay.NewCredentialVerifier(records); err != nil {
		return nil, errCredentialFile
	}
	return records, nil
}

func parseCanonicalDigest(value string) ([sha256.Size]byte, bool) {
	var digest [sha256.Size]byte
	if len(value) != sha256.Size*2 || value != strings.ToLower(value) {
		return digest, false
	}
	decoded, err := hex.DecodeString(value)
	if err != nil || len(decoded) != sha256.Size {
		clear(decoded)
		return digest, false
	}
	copy(digest[:], decoded)
	clear(decoded)
	return digest, true
}

func parseCanonicalUTC(value string) (time.Time, bool) {
	if !strings.HasSuffix(value, "Z") {
		return time.Time{}, false
	}
	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil || parsed.IsZero() || parsed.Location() != time.UTC || parsed.Format(time.RFC3339) != value {
		return time.Time{}, false
	}
	return parsed, true
}

func loadTLSCertificate(certPath, keyPath string) (tls.Certificate, error) {
	certPEM, err := readRegularFile(certPath, maxTLSFileBytes, false)
	if err != nil {
		return tls.Certificate{}, errTLSFiles
	}
	defer clear(certPEM)
	keyPEM, err := readRegularFile(keyPath, maxTLSFileBytes, true)
	if err != nil {
		return tls.Certificate{}, errTLSFiles
	}
	defer clear(keyPEM)
	certificate, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil || len(certificate.Certificate) == 0 || certificate.PrivateKey == nil {
		return tls.Certificate{}, errTLSFiles
	}
	return certificate, nil
}

func readRegularFile(path string, maxBytes int64, private bool) ([]byte, error) {
	return readRegularFileWithOpener(path, maxBytes, private, os.Open)
}

func readRegularFileWithOpener(
	path string,
	maxBytes int64,
	private bool,
	opener func(string) (*os.File, error),
) ([]byte, error) {
	if !validConfigPath(path) || maxBytes <= 0 || opener == nil {
		return nil, errors.New("invalid file")
	}
	checkedInfo, err := os.Lstat(path)
	if err != nil || !validRegularFileInfo(checkedInfo, private) {
		return nil, errors.New("invalid file")
	}
	file, err := opener(path)
	if err != nil {
		return nil, errors.New("invalid file")
	}
	defer file.Close()
	openedInfo, err := file.Stat()
	if err != nil || !validRegularFileInfo(openedInfo, private) || !os.SameFile(checkedInfo, openedInfo) {
		return nil, errors.New("invalid file")
	}
	content, err := io.ReadAll(io.LimitReader(file, maxBytes+1))
	if err != nil || int64(len(content)) > maxBytes || len(content) == 0 {
		clear(content)
		return nil, errors.New("invalid file")
	}
	return content, nil
}

func validRegularFileInfo(info os.FileInfo, private bool) bool {
	if info == nil || !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 {
		return false
	}
	permissions := info.Mode().Perm()
	return permissions&0o022 == 0 && (!private || permissions&0o077 == 0)
}

func buildApplication(config cliConfig, auditWriter io.Writer) (*application, error) {
	if !validCLIConfig(config) || auditWriter == nil {
		return nil, errApplication
	}
	certificate, err := loadTLSCertificate(config.tlsCertPath, config.tlsKeyPath)
	if err != nil {
		return nil, errApplication
	}
	records, err := loadCredentialRecords(config.credentialPath)
	if err != nil {
		return nil, errApplication
	}
	verifier, err := relay.NewCredentialVerifier(records)
	if err != nil {
		return nil, errApplication
	}
	admission, err := relay.NewAdmissionController(relay.AdmissionLimits{
		MaxPerCredential: config.maxPerCredential,
		MaxPerPeer:       config.maxPerPeer,
		MaxGlobal:        config.maxGlobal,
	})
	if err != nil {
		return nil, errApplication
	}
	audit, err := relay.NewSafeAudit(auditWriter)
	if err != nil {
		return nil, errApplication
	}
	server, err := relay.NewServer(relay.ServerConfig{
		TLSConfig:           relay.OuterTLSConfig(certificate),
		CredentialVerifier:  verifier,
		AdmissionController: admission,
		UpstreamDialer:      relay.NewFixedUpstreamDialer(nil, nil),
		AuditRecorder:       audit,
		PumpLimits: relay.PumpLimits{
			IdleTimeout:          config.idleTimeout,
			MaxSessionDuration:   config.maxSessionDuration,
			MaxBytesPerDirection: config.maxBytesPerDirection,
		},
		HandshakeTimeout:       config.handshakeTimeout,
		ConnectHeaderTimeout:   config.connectHeaderTimeout,
		UpstreamConnectTimeout: config.upstreamConnectTimeout,
	})
	if err != nil {
		return nil, errApplication
	}
	return &application{listen: config.listen, server: server}, nil
}

func serve(ctx context.Context, listener net.Listener, server connectionServer) error {
	if ctx == nil || listener == nil || server == nil {
		return errServe
	}
	stopClose := context.AfterFunc(ctx, func() { _ = listener.Close() })
	defer stopClose()
	var sessions sync.WaitGroup
	defer sessions.Wait()
	slots := make(chan struct{}, maxActiveConnections)

	for {
		select {
		case slots <- struct{}{}:
		case <-ctx.Done():
			return nil
		}

		connection, err := listener.Accept()
		if err != nil {
			<-slots
			if ctx.Err() != nil {
				return nil
			}
			return errServe
		}
		sessions.Add(1)
		go func() {
			defer sessions.Done()
			defer func() { <-slots }()
			_ = server.ServeConn(ctx, connection)
		}()
	}
}

func run(ctx context.Context, args []string, auditWriter io.Writer) error {
	config, err := parseConfig(args)
	if err != nil {
		return errCLIConfig
	}
	application, err := buildApplication(config, auditWriter)
	if err != nil {
		return errApplication
	}
	listener, err := net.Listen("tcp", application.listen)
	if err != nil {
		return errServe
	}
	return serve(ctx, listener, application.server)
}

func main() {
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()
	if err := run(ctx, os.Args[1:], os.Stdout); err != nil {
		_, _ = fmt.Fprintln(os.Stderr, "ws024-relay: startup failed")
		os.Exit(1)
	}
}
