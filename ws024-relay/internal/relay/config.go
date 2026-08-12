package relay

const (
	FixedAuthority        = "ws024.juntadeandalucia.es:443"
	TunnelProtocolVersion = "1"
	maximumHeaderBytes    = 8192
)

type ConnectRequest struct {
	Credential []byte
}
