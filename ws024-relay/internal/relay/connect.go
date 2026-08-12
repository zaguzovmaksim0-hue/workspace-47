package relay

import (
	"bufio"
	"errors"
	"strings"
)

var (
	errInvalidHeaderLimit = errors.New("invalid header limit")
	errInvalidConnect     = errors.New("invalid connect request")
)

// ParseFixedCONNECT reads exactly one bounded CONNECT header. It stops after
// the first CRLFCRLF, leaving any bytes already buffered in r for the caller.
func ParseFixedCONNECT(r *bufio.Reader, maxHeaderBytes int64) (ConnectRequest, error) {
	if r == nil || maxHeaderBytes <= 0 || maxHeaderBytes > maximumHeaderBytes {
		return ConnectRequest{}, errInvalidHeaderLimit
	}

	header := make([]byte, 0, minInt64(maxHeaderBytes, 1024))
	for {
		if int64(len(header)) == maxHeaderBytes {
			return ConnectRequest{}, errInvalidConnect
		}
		b, err := r.ReadByte()
		if err != nil {
			return ConnectRequest{}, errInvalidConnect
		}
		header = append(header, b)
		if len(header) >= 4 && string(header[len(header)-4:]) == "\r\n\r\n" {
			break
		}
	}

	return parseConnectHeader(header)
}

func parseConnectHeader(header []byte) (ConnectRequest, error) {
	if len(header) < 4 || !strings.HasSuffix(string(header), "\r\n\r\n") {
		return ConnectRequest{}, errInvalidConnect
	}
	for i, b := range header {
		if b == '\r' {
			if i+1 >= len(header) || header[i+1] != '\n' {
				return ConnectRequest{}, errInvalidConnect
			}
			continue
		}
		if b == '\n' {
			if i == 0 || header[i-1] != '\r' {
				return ConnectRequest{}, errInvalidConnect
			}
			continue
		}
		if b < 0x20 || b == 0x7f {
			return ConnectRequest{}, errInvalidConnect
		}
	}

	lines := strings.Split(string(header[:len(header)-4]), "\r\n")
	if len(lines) != 4 || lines[0] != "CONNECT "+FixedAuthority+" HTTP/1.1" {
		return ConnectRequest{}, errInvalidConnect
	}

	seen := make(map[string]bool, 3)
	var credential []byte
	for _, line := range lines[1:] {
		if line == "" || line[0] == ' ' {
			return ConnectRequest{}, errInvalidConnect
		}
		name, value, ok := strings.Cut(line, ": ")
		if !ok {
			return ConnectRequest{}, errInvalidConnect
		}
		if !validHeaderName(name) {
			return ConnectRequest{}, errInvalidConnect
		}
		key := asciiLower(name)
		if seen[key] {
			return ConnectRequest{}, errInvalidConnect
		}
		seen[key] = true

		switch key {
		case "host":
			if value != FixedAuthority {
				return ConnectRequest{}, errInvalidConnect
			}
		case "authorization":
			const bearer = "Bearer "
			if !strings.HasPrefix(value, bearer) || !printableASCII(value[len(bearer):]) {
				return ConnectRequest{}, errInvalidConnect
			}
			credential = []byte(value[len(bearer):])
		case "x-ws024-tunnel-version":
			if value != TunnelProtocolVersion {
				return ConnectRequest{}, errInvalidConnect
			}
		default:
			return ConnectRequest{}, errInvalidConnect
		}
	}
	if !seen["host"] || !seen["authorization"] || !seen["x-ws024-tunnel-version"] {
		return ConnectRequest{}, errInvalidConnect
	}
	return ConnectRequest{Credential: credential}, nil
}

func validHeaderName(s string) bool {
	if s == "" {
		return false
	}
	for i := range s {
		b := s[i]
		if !((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9') || b == '-') {
			return false
		}
	}
	return true
}

func asciiLower(s string) string {
	b := []byte(s)
	for i := range b {
		if b[i] >= 'A' && b[i] <= 'Z' {
			b[i] += 'a' - 'A'
		}
	}
	return string(b)
}

func printableASCII(s string) bool {
	if s == "" {
		return false
	}
	for i := range s {
		if s[i] < 0x20 || s[i] > 0x7e {
			return false
		}
	}
	return true
}

func minInt64(a int64, b int) int {
	if a < int64(b) {
		return int(a)
	}
	return b
}
