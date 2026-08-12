package main

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"io"
	"math/big"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestParseConfigRequiresExplicitListenTLSAndCredentials(t *testing.T) {
	validArgs := []string{
		"-listen", "127.0.0.1:8443",
		"-tls-cert", "/tmp/relay-cert.pem",
		"-tls-key", "/tmp/relay-key.pem",
		"-qa-credentials", "/tmp/credentials.json",
	}
	config, err := parseConfig(validArgs)
	if err != nil {
		t.Fatalf("parseConfig(valid) error = %v", err)
	}
	if config.listen != "127.0.0.1:8443" || config.tlsCertPath == "" || config.tlsKeyPath == "" || config.credentialPath == "" {
		t.Fatalf("config = %#v", config)
	}
	if config.handshakeTimeout != 10*time.Second || config.connectHeaderTimeout != 10*time.Second ||
		config.upstreamConnectTimeout != 10*time.Second || config.idleTimeout != 30*time.Second ||
		config.maxSessionDuration != 90*time.Second || config.maxBytesPerDirection != 4*1024*1024 {
		t.Fatalf("unsafe or unexpected defaults: %#v", config)
	}
	if config.maxPerCredential != 2 || config.maxPerPeer != 4 || config.maxGlobal != 64 {
		t.Fatalf("admission defaults = %#v", config)
	}

	for _, flagName := range []string{"-listen", "-tls-cert", "-tls-key", "-qa-credentials"} {
		args := removeFlagPair(validArgs, flagName)
		if _, err := parseConfig(args); !errors.Is(err, errCLIConfig) {
			t.Fatalf("missing %s: error = %v, want generic config rejection", flagName, err)
		}
	}
}

func TestParseConfigRejectsArbitraryUpstreamAndUnsafeOverrides(t *testing.T) {
	base := []string{
		"-listen", "127.0.0.1:8443",
		"-tls-cert", "cert.pem",
		"-tls-key", "key.pem",
		"-qa-credentials", "credentials.json",
	}
	cases := [][]string{
		appendCopy(base, "-upstream", "attacker.example:443"),
		appendCopy(base, "-listen", "relay.example:443"),
		appendCopy(base, "-listen", "127.0.0.1:0"),
		appendCopy(base, "-listen", "127.0.0.1:65536"),
		appendCopy(base, "-tls-cert", "bad\npath"),
		appendCopy(base, "-handshake-timeout", "11s"),
		appendCopy(base, "-connect-header-timeout", "11s"),
		appendCopy(base, "-upstream-connect-timeout", "11s"),
		appendCopy(base, "-idle-timeout", "31s"),
		appendCopy(base, "-max-session-duration", "91s"),
		appendCopy(base, "-max-bytes-per-direction", "4194305"),
		appendCopy(base, "-max-per-credential", "3"),
		appendCopy(base, "-max-per-peer", "5"),
		appendCopy(base, "-max-global", "65"),
		appendCopy(base, "unexpected-positional"),
	}
	for i, args := range cases {
		if _, err := parseConfig(args); !errors.Is(err, errCLIConfig) {
			t.Fatalf("case %d: parseConfig() error = %v, want generic rejection", i, err)
		}
	}
}

func TestParseConfigAcceptsOnlyConservativeLowerOverrides(t *testing.T) {
	config, err := parseConfig([]string{
		"-listen", "[::1]:9443",
		"-tls-cert", "cert.pem",
		"-tls-key", "key.pem",
		"-qa-credentials", "credentials.json",
		"-handshake-timeout", "2s",
		"-connect-header-timeout", "3s",
		"-upstream-connect-timeout", "4s",
		"-idle-timeout", "5s",
		"-max-session-duration", "12s",
		"-max-bytes-per-direction", "65536",
		"-max-per-credential", "1",
		"-max-per-peer", "2",
		"-max-global", "8",
	})
	if err != nil {
		t.Fatalf("parseConfig() error = %v", err)
	}
	if config.listen != "[::1]:9443" || config.maxBytesPerDirection != 65536 || config.maxGlobal != 8 {
		t.Fatalf("config = %#v", config)
	}
}

func TestLoadCredentialRecordsAcceptsStrictDigestOnlyFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "credentials.json")
	token := "qa-token-never-stored-in-file"
	digest := sha256.Sum256([]byte(token))
	expires := time.Now().UTC().Add(time.Hour).Truncate(time.Second)
	content := credentialJSON(t, map[string]any{
		"version": 1,
		"credentials": []any{
			map[string]any{
				"id":         "qa-credential-1",
				"sha256":     hex.EncodeToString(digest[:]),
				"expires_at": expires.Format(time.RFC3339),
				"revoked":    false,
			},
		},
	})
	writePrivateFile(t, path, content)

	records, err := loadCredentialRecords(path)
	if err != nil {
		t.Fatalf("loadCredentialRecords() error = %v", err)
	}
	if len(records) != 1 || records[0].ID != "qa-credential-1" || records[0].Digest != digest ||
		!records[0].ExpiresAt.Equal(expires) || records[0].Revoked {
		t.Fatalf("records = %#v", records)
	}
	if bytes.Contains(content, []byte(token)) {
		t.Fatal("credential fixture unexpectedly contains raw token")
	}
}

func TestLoadCredentialRecordsRejectsRawTokenUnknownFieldsAndMalformedFilesGenerically(t *testing.T) {
	digest := sha256.Sum256([]byte("valid-token"))
	expires := time.Now().UTC().Add(time.Hour).Truncate(time.Second).Format(time.RFC3339)
	validEntry := map[string]any{
		"id":         "qa-1",
		"sha256":     hex.EncodeToString(digest[:]),
		"expires_at": expires,
		"revoked":    false,
	}
	cases := []struct {
		name    string
		content []byte
		mode    os.FileMode
	}{
		{"raw token", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{mergeMap(validEntry, "token", "secret-token")}}), 0o600},
		{"unknown root", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{validEntry}, "extra": true}), 0o600},
		{"wrong version", credentialJSON(t, map[string]any{"version": 2, "credentials": []any{validEntry}}), 0o600},
		{"empty", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{}}), 0o600},
		{"too many", credentialJSON(t, map[string]any{"version": 1, "credentials": repeatedEntries(validEntry, 65)}), 0o600},
		{"bad digest", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{mergeMap(validEntry, "sha256", "xyz")}}), 0o600},
		{"uppercase digest", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{mergeMap(validEntry, "sha256", strings.ToUpper(hex.EncodeToString(digest[:])))}}), 0o600},
		{"bad expiry", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{mergeMap(validEntry, "expires_at", "tomorrow")}}), 0o600},
		{"non canonical expiry", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{mergeMap(validEntry, "expires_at", "2026-07-28T20:00:00+02:00")}}), 0o600},
		{"trailing json", append(credentialJSON(t, map[string]any{"version": 1, "credentials": []any{validEntry}}), []byte("{}")...), 0o600},
		{"world writable", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{validEntry}}), 0o622},
		{"world readable", credentialJSON(t, map[string]any{"version": 1, "credentials": []any{validEntry}}), 0o644},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "credential-secret-name.json")
			if err := os.WriteFile(path, tc.content, tc.mode); err != nil {
				t.Fatal(err)
			}
			if err := os.Chmod(path, tc.mode); err != nil {
				t.Fatal(err)
			}
			records, err := loadCredentialRecords(path)
			if records != nil || !errors.Is(err, errCredentialFile) {
				t.Fatalf("loadCredentialRecords() = (%v, %v), want generic rejection", records, err)
			}
			for _, forbidden := range []string{"secret-token", path, "qa-1", "xyz"} {
				if strings.Contains(err.Error(), forbidden) {
					t.Fatalf("error leaks %q: %q", forbidden, err)
				}
			}
		})
	}
}

func TestLoadCredentialRecordsRejectsSymlinkDirectoryAndOversize(t *testing.T) {
	dir := t.TempDir()
	target := filepath.Join(dir, "target.json")
	writePrivateFile(t, target, []byte(`{"version":1,"credentials":[]}`))
	link := filepath.Join(dir, "link.json")
	if err := os.Symlink(target, link); err != nil {
		t.Fatal(err)
	}
	for _, path := range []string{link, dir} {
		if _, err := loadCredentialRecords(path); !errors.Is(err, errCredentialFile) {
			t.Fatalf("loadCredentialRecords(%q) error = %v", path, err)
		}
	}
	oversize := filepath.Join(dir, "oversize.json")
	writePrivateFile(t, oversize, bytes.Repeat([]byte{'x'}, maxCredentialFileBytes+1))
	if _, err := loadCredentialRecords(oversize); !errors.Is(err, errCredentialFile) {
		t.Fatalf("oversize error = %v", err)
	}
}

func TestReadRegularFileRejectsPathSwapBetweenMetadataAndOpen(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "checked.json")
	replacement := filepath.Join(dir, "replacement.json")
	writePrivateFile(t, path, []byte("original"))
	writePrivateFile(t, replacement, []byte("replacement"))

	opener := func(name string) (*os.File, error) {
		if err := os.Remove(name); err != nil {
			return nil, err
		}
		if err := os.Symlink(replacement, name); err != nil {
			return nil, err
		}
		return os.Open(name)
	}
	content, err := readRegularFileWithOpener(path, 1024, true, opener)
	if content != nil || err == nil {
		t.Fatalf("readRegularFileWithOpener() = (%q, %v), want path-swap rejection", content, err)
	}
}

func TestLoadTLSCertificateRejectsMissingSymlinkAndUnsafeKeyPermissions(t *testing.T) {
	dir := t.TempDir()
	certPath, keyPath := writeTestTLSFiles(t, dir)
	certificate, err := loadTLSCertificate(certPath, keyPath)
	if err != nil || len(certificate.Certificate) == 0 || certificate.PrivateKey == nil {
		t.Fatalf("loadTLSCertificate() = (%v, %v)", certificate, err)
	}

	link := filepath.Join(dir, "key-link.pem")
	if err := os.Symlink(keyPath, link); err != nil {
		t.Fatal(err)
	}
	if _, err := loadTLSCertificate(certPath, link); !errors.Is(err, errTLSFiles) {
		t.Fatalf("symlink key error = %v", err)
	}
	if err := os.Chmod(keyPath, 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := loadTLSCertificate(certPath, keyPath); !errors.Is(err, errTLSFiles) {
		t.Fatalf("unsafe key mode error = %v", err)
	}
	if _, err := loadTLSCertificate(filepath.Join(dir, "missing-cert.pem"), keyPath); !errors.Is(err, errTLSFiles) {
		t.Fatalf("missing cert error = %v", err)
	}
}

func TestBuildApplicationRequiresValidFilesAndCreatesFixedServer(t *testing.T) {
	dir := t.TempDir()
	certPath, keyPath := writeTestTLSFiles(t, dir)
	credentialPath := filepath.Join(dir, "credentials.json")
	digest := sha256.Sum256([]byte("qa-runtime-token"))
	writePrivateFile(t, credentialPath, credentialJSON(t, map[string]any{
		"version": 1,
		"credentials": []any{map[string]any{
			"id": "qa-runtime", "sha256": hex.EncodeToString(digest[:]),
			"expires_at": time.Now().UTC().Add(time.Hour).Truncate(time.Second).Format(time.RFC3339),
			"revoked":    false,
		}},
	}))
	config, err := parseConfig([]string{
		"-listen", "127.0.0.1:8443",
		"-tls-cert", certPath,
		"-tls-key", keyPath,
		"-qa-credentials", credentialPath,
	})
	if err != nil {
		t.Fatal(err)
	}
	var audit bytes.Buffer
	application, err := buildApplication(config, &audit)
	if err != nil || application == nil || application.server == nil {
		t.Fatalf("buildApplication() = (%v, %v)", application, err)
	}
	if strings.Contains(audit.String(), "qa-runtime-token") {
		t.Fatal("raw credential appeared in audit output")
	}
}

func TestServeBoundsPreAuthenticationConnections(t *testing.T) {
	listener := &countingListener{}
	server := &blockingConnectionServer{}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- serve(ctx, listener, server) }()

	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) && server.active.Load() < int32(maxActiveConnections) {
		time.Sleep(time.Millisecond)
	}
	if got := server.active.Load(); got != int32(maxActiveConnections) {
		cancel()
		t.Fatalf("active connections = %d, want cap %d", got, maxActiveConnections)
	}
	time.Sleep(25 * time.Millisecond)
	if got := listener.accepts.Load(); got != int32(maxActiveConnections) {
		cancel()
		t.Fatalf("Accept calls = %d, want bounded at %d", got, maxActiveConnections)
	}
	cancel()
	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("serve() error = %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("serve() did not join bounded connection workers")
	}
	if got := server.maxActive.Load(); got > int32(maxActiveConnections) {
		t.Fatalf("maximum active connections = %d, cap = %d", got, maxActiveConnections)
	}
}

func TestServeStopsCleanlyWhenContextIsCancelled(t *testing.T) {
	dir := t.TempDir()
	certPath, keyPath := writeTestTLSFiles(t, dir)
	credentialPath := filepath.Join(dir, "credentials.json")
	digest := sha256.Sum256([]byte("qa-runtime-token"))
	writePrivateFile(t, credentialPath, credentialJSON(t, map[string]any{
		"version": 1,
		"credentials": []any{map[string]any{
			"id": "qa-runtime", "sha256": hex.EncodeToString(digest[:]),
			"expires_at": time.Now().UTC().Add(time.Hour).Truncate(time.Second).Format(time.RFC3339),
			"revoked":    false,
		}},
	}))
	config, err := parseConfig([]string{
		"-listen", "127.0.0.1:8443",
		"-tls-cert", certPath,
		"-tls-key", keyPath,
		"-qa-credentials", credentialPath,
	})
	if err != nil {
		t.Fatal(err)
	}
	application, err := buildApplication(config, &bytes.Buffer{})
	if err != nil {
		t.Fatal(err)
	}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- serve(ctx, listener, application.server) }()
	cancel()
	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("serve() error = %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("serve() did not stop after cancellation")
	}
}

type countingListener struct {
	accepts atomic.Int32
	closed  atomic.Bool
}

func (listener *countingListener) Accept() (net.Conn, error) {
	if listener.closed.Load() {
		return nil, net.ErrClosed
	}
	listener.accepts.Add(1)
	return &blockingConn{}, nil
}
func (listener *countingListener) Close() error   { listener.closed.Store(true); return nil }
func (listener *countingListener) Addr() net.Addr { return &net.TCPAddr{} }

type blockingConnectionServer struct {
	active    atomic.Int32
	maxActive atomic.Int32
}

func (server *blockingConnectionServer) ServeConn(ctx context.Context, conn net.Conn) error {
	active := server.active.Add(1)
	for {
		maximum := server.maxActive.Load()
		if active <= maximum || server.maxActive.CompareAndSwap(maximum, active) {
			break
		}
	}
	<-ctx.Done()
	server.active.Add(-1)
	_ = conn.Close()
	return ctx.Err()
}

type blockingConn struct{}

func (*blockingConn) Read([]byte) (int, error)         { return 0, io.EOF }
func (*blockingConn) Write(p []byte) (int, error)      { return len(p), nil }
func (*blockingConn) Close() error                     { return nil }
func (*blockingConn) LocalAddr() net.Addr              { return &net.TCPAddr{} }
func (*blockingConn) RemoteAddr() net.Addr             { return &net.TCPAddr{} }
func (*blockingConn) SetDeadline(time.Time) error      { return nil }
func (*blockingConn) SetReadDeadline(time.Time) error  { return nil }
func (*blockingConn) SetWriteDeadline(time.Time) error { return nil }

func removeFlagPair(args []string, name string) []string {
	result := make([]string, 0, len(args))
	for i := 0; i < len(args); i++ {
		if args[i] == name && i+1 < len(args) {
			i++
			continue
		}
		result = append(result, args[i])
	}
	return result
}

func appendCopy(base []string, values ...string) []string {
	result := append([]string(nil), base...)
	return append(result, values...)
}

func writePrivateFile(t *testing.T, path string, content []byte) {
	t.Helper()
	if err := os.WriteFile(path, content, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(path, 0o600); err != nil {
		t.Fatal(err)
	}
}

func credentialJSON(t *testing.T, value any) []byte {
	t.Helper()
	encoded, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	return encoded
}

func mergeMap(original map[string]any, key string, value any) map[string]any {
	copy := make(map[string]any, len(original)+1)
	for k, v := range original {
		copy[k] = v
	}
	copy[key] = value
	return copy
}

func repeatedEntries(entry map[string]any, count int) []any {
	values := make([]any, count)
	for i := range values {
		values[i] = mergeMap(entry, "id", "qa-"+big.NewInt(int64(i+1)).String())
	}
	return values
}

func writeTestTLSFiles(t *testing.T, dir string) (string, string) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
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
		t.Fatal(err)
	}
	keyDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	certPath := filepath.Join(dir, "cert.pem")
	keyPath := filepath.Join(dir, "key.pem")
	writePrivateFile(t, certPath, pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
	writePrivateFile(t, keyPath, pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER}))
	return certPath, keyPath
}
