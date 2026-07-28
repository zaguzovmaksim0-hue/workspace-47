//go:build integration

package relay

import "errors"

// IntegrationStageForError exposes only a closed test-stage token. It is
// excluded from production builds and never includes request or peer data.
func IntegrationStageForError(err error) string {
	switch {
	case err == nil:
		return "success"
	case errors.Is(err, errServerTLS):
		return "outer_tls"
	case errors.Is(err, errServerALPN):
		return "alpn"
	case errors.Is(err, errServerProtocol):
		return "connect"
	case errors.Is(err, errServerCredential):
		return "credential"
	case errors.Is(err, errServerAdmission):
		return "admission"
	case errors.Is(err, errServerUpstream):
		return "upstream"
	case errors.Is(err, errServerResponse):
		return "response"
	case errors.Is(err, errServerPump):
		return "pump"
	default:
		return "internal"
	}
}
