package relay

import (
	"bufio"
	"bytes"
	"io"
	"strings"
	"testing"
)

const validConnect = "CONNECT ws024.juntadeandalucia.es:443 HTTP/1.1\r\n" +
	"Host: ws024.juntadeandalucia.es:443\r\n" +
	"Authorization: Bearer credential-123\r\n" +
	"X-WS024-Tunnel-Version: 1\r\n\r\n"

func TestParseFixedCONNECTAcceptsExactRequest(t *testing.T) {
	req, err := ParseFixedCONNECT(bufio.NewReader(strings.NewReader(validConnect)), maximumHeaderBytes)
	if err != nil {
		t.Fatalf("ParseFixedCONNECT() error = %v", err)
	}
	if got, want := string(req.Credential), "credential-123"; got != want {
		t.Fatalf("credential = %q, want %q", got, want)
	}
}

func TestParseFixedCONNECTAcceptsCaseVariantHeaderNames(t *testing.T) {
	input := strings.Replace(validConnect, "Host:", "hOsT:", 1)
	input = strings.Replace(input, "Authorization:", "aUtHoRiZaTiOn:", 1)
	input = strings.Replace(input, "X-WS024-Tunnel-Version:", "x-wS024-TuNnEl-vErSiOn:", 1)
	req, err := ParseFixedCONNECT(bufio.NewReader(strings.NewReader(input)), maximumHeaderBytes)
	if err != nil {
		t.Fatalf("ParseFixedCONNECT() error = %v", err)
	}
	if got, want := string(req.Credential), "credential-123"; got != want {
		t.Fatalf("credential = %q, want %q", got, want)
	}
}

func TestParseFixedCONNECTFragmentedAndLeavesPostHeaderBytes(t *testing.T) {
	reader := bufio.NewReaderSize(&fragmentReader{chunks: [][]byte{
		[]byte("CONNECT ws024.juntadeandalucia.es:443 HTTP/1.1\r\nHost: ws024.junta"),
		[]byte("deandalucia.es:443\r\nAuthorization: Bearer fragmented\r\nX-WS024-Tunnel-Version: 1\r\n\r\n"),
		[]byte("\x16\x03\x01opaque-inner-tls"),
	}}, 4096)
	req, err := ParseFixedCONNECT(reader, maximumHeaderBytes)
	if err != nil {
		t.Fatalf("ParseFixedCONNECT() error = %v", err)
	}
	if got, want := string(req.Credential), "fragmented"; got != want {
		t.Fatalf("credential = %q, want %q", got, want)
	}
	got, err := io.ReadAll(reader)
	if err != nil {
		t.Fatalf("ReadAll() error = %v", err)
	}
	if want := "\x16\x03\x01opaque-inner-tls"; string(got) != want {
		t.Fatalf("post-header bytes = %q, want %q", got, want)
	}
}

func TestParseFixedCONNECTHeaderLimitBoundaries(t *testing.T) {
	base := strings.Replace(validConnect, "credential-123", "x", 1)
	padding := maximumHeaderBytes - len(base)
	if padding < 1 {
		t.Fatal("test fixture exceeds header maximum")
	}
	within := strings.Replace(base, "Bearer x", "Bearer "+strings.Repeat("a", padding+1), 1)
	if len(within) != maximumHeaderBytes {
		t.Fatalf("within header length = %d, want %d", len(within), maximumHeaderBytes)
	}
	for _, tc := range []struct {
		name  string
		input string
		limit int64
		ok    bool
	}{
		{"exact hard maximum", within, maximumHeaderBytes, true},
		{"over hard maximum", strings.Replace(within, "\r\n\r\n", "x\r\n\r\n", 1), maximumHeaderBytes, false},
		{"caller smaller maximum", validConnect, int64(len(validConnect) - 1), false},
		{"caller exceeds hard maximum", validConnect, maximumHeaderBytes + 1, false},
		{"zero limit", validConnect, 0, false},
		{"negative limit", validConnect, -1, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, err := ParseFixedCONNECT(bufio.NewReader(strings.NewReader(tc.input)), tc.limit)
			if (err == nil) != tc.ok {
				t.Fatalf("ParseFixedCONNECT() error = %v, want success=%v", err, tc.ok)
			}
		})
	}
}

func TestParseFixedCONNECTRejectsMalformedRequests(t *testing.T) {
	secret := "never-return-this-credential"
	valid := strings.Replace(validConnect, "credential-123", secret, 1)
	cases := []struct {
		name  string
		input string
	}{
		{"method", strings.Replace(valid, "CONNECT ", "GET ", 1)},
		{"authority case", strings.Replace(valid, "ws024.juntadeandalucia.es:443 HTTP", "WS024.juntadeandalucia.es:443 HTTP", 1)},
		{"http version", strings.Replace(valid, "HTTP/1.1", "HTTP/1.0", 1)},
		{"missing host", strings.Replace(valid, "Host: ws024.juntadeandalucia.es:443\r\n", "", 1)},
		{"host case value", strings.Replace(valid, "Host: ws024.juntadeandalucia.es:443", "Host: WS024.juntadeandalucia.es:443", 1)},
		{"duplicate host casing", strings.Replace(valid, "Authorization:", "hOsT: ws024.juntadeandalucia.es:443\r\nAuthorization:", 1)},
		{"missing authorization", strings.Replace(valid, "Authorization: Bearer "+secret+"\r\n", "", 1)},
		{"duplicate authorization casing", strings.Replace(valid, "X-WS024", "aUtHoRiZaTiOn: Bearer second\r\nX-WS024", 1)},
		{"wrong bearer prefix", strings.Replace(valid, "Bearer "+secret, "bearer "+secret, 1)},
		{"empty credential", strings.Replace(valid, "Bearer "+secret, "Bearer ", 1)},
		{"credential control", strings.Replace(valid, secret, "bad\x7f", 1)},
		{"missing version", strings.Replace(valid, "X-WS024-Tunnel-Version: 1\r\n", "", 1)},
		{"wrong version", strings.Replace(valid, "X-WS024-Tunnel-Version: 1", "X-WS024-Tunnel-Version: 2", 1)},
		{"duplicate version casing", strings.Replace(valid, "\r\n\r\n", "\r\nx-ws024-tunnel-version: 1\r\n\r\n", 1)},
		{"unknown header", strings.Replace(valid, "\r\n\r\n", "\r\nX-Unknown: 1\r\n\r\n", 1)},
		{"content length", strings.Replace(valid, "\r\n\r\n", "\r\nContent-Length: 0\r\n\r\n", 1)},
		{"transfer encoding", strings.Replace(valid, "\r\n\r\n", "\r\nTransfer-Encoding: chunked\r\n\r\n", 1)},
		{"obs fold", strings.Replace(valid, "X-WS024", " X-Injected: value\r\nX-WS024", 1)},
		{"lf only", strings.ReplaceAll(valid, "\r\n", "\n")},
		{"bare cr", strings.Replace(valid, "Host:", "Host:\r", 1)},
		{"userinfo", strings.Replace(valid, "ws024.juntadeandalucia.es:443 HTTP", "user@ws024.juntadeandalucia.es:443 HTTP", 1)},
		{"path", strings.Replace(valid, "ws024.juntadeandalucia.es:443 HTTP", "ws024.juntadeandalucia.es:443/path HTTP", 1)},
		{"query", strings.Replace(valid, "ws024.juntadeandalucia.es:443 HTTP", "ws024.juntadeandalucia.es:443?x=1 HTTP", 1)},
		{"fragment", strings.Replace(valid, "ws024.juntadeandalucia.es:443 HTTP", "ws024.juntadeandalucia.es:443#x HTTP", 1)},
		{"trailing dot", strings.Replace(valid, "ws024.juntadeandalucia.es:443 HTTP", "ws024.juntadeandalucia.es.:443 HTTP", 1)},
		{"ip literal", strings.Replace(valid, "ws024.juntadeandalucia.es:443 HTTP", "192.0.2.1:443 HTTP", 1)},
		{"second request before terminator", strings.Replace(valid, "\r\n\r\n", "\r\nCONNECT ws024.juntadeandalucia.es:443 HTTP/1.1\r\n\r\n", 1)},
		{"extra bytes before terminator", strings.Replace(valid, "\r\n\r\n", "\r\nopaque\r\n\r\n", 1)},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req, err := ParseFixedCONNECT(bufio.NewReader(strings.NewReader(tc.input)), maximumHeaderBytes)
			if err == nil {
				t.Fatal("ParseFixedCONNECT() unexpectedly succeeded")
			}
			if len(req.Credential) != 0 {
				t.Fatalf("credential returned on error: %q", req.Credential)
			}
			if strings.Contains(err.Error(), secret) || strings.Contains(err.Error(), FixedAuthority) {
				t.Fatalf("error leaks request data: %q", err)
			}
		})
	}
}

func FuzzParseFixedCONNECTNoLeakOrPanic(f *testing.F) {
	f.Add([]byte(validConnect))
	f.Add([]byte("CONNECT bad\nAuthorization: Bearer secret\n\n"))
	f.Fuzz(func(t *testing.T, input []byte) {
		req, err := ParseFixedCONNECT(bufio.NewReader(bytes.NewReader(input)), maximumHeaderBytes)
		if err != nil && len(req.Credential) != 0 {
			t.Fatal("credential returned on parser error")
		}
		if err != nil && err != errInvalidConnect && err != errInvalidHeaderLimit {
			t.Fatalf("parser returned a non-sentinel error: %v", err)
		}
	})
}

type fragmentReader struct {
	chunks [][]byte
}

func (r *fragmentReader) Read(p []byte) (int, error) {
	if len(r.chunks) == 0 {
		return 0, io.EOF
	}
	n := copy(p, r.chunks[0])
	r.chunks[0] = r.chunks[0][n:]
	if len(r.chunks[0]) == 0 {
		r.chunks = r.chunks[1:]
	}
	return n, nil
}
