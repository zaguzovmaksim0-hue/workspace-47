package relay

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"errors"
	"net/netip"
	"time"
)

var (
	// ErrCredentialRejected is the sole public outcome for failed credential
	// verification. Its text deliberately contains no credential-specific data.
	ErrCredentialRejected = errors.New("credential rejected")

	// ErrCredentialConfiguration reports an unsafe credential configuration
	// without disclosing any configuration value.
	ErrCredentialConfiguration = errors.New("invalid credential configuration")
)

// CredentialGrant identifies an active credential after successful
// verification. It contains no credential material.
type CredentialGrant struct {
	ID        string
	ExpiresAt time.Time
}

// CredentialVerifier verifies a caller-owned credential buffer. Verify reads
// raw but never clears, mutates, or retains it: the caller owns clearing raw
// after this method returns. peer is intentionally accepted for the interface
// contract only and is neither retained nor used for a decision here.
type CredentialVerifier interface {
	Verify(ctx context.Context, raw []byte, peer netip.Addr) (CredentialGrant, error)
}

// CredentialRecord is a digest-only credential configuration entry. Raw
// credentials must never be placed in this type or its construction path.
type CredentialRecord struct {
	ID        string
	Digest    [sha256.Size]byte
	ExpiresAt time.Time
	Revoked   bool
}

type credentialRecord struct {
	id        string
	digest    [sha256.Size]byte
	expiresAt time.Time
	revoked   bool
}

type credentialVerifier struct {
	records        []credentialRecord
	comparisonHook func()
}

// NewCredentialVerifier copies and validates digest-only records so callers
// cannot mutate verifier configuration after construction.
func NewCredentialVerifier(records []CredentialRecord) (CredentialVerifier, error) {
	return newCredentialVerifier(records, nil)
}

// newCredentialVerifier permits a test-only comparison counter. Digest
// comparison itself remains pinned to crypto/subtle.ConstantTimeCompare.
func newCredentialVerifier(records []CredentialRecord, comparisonHook func()) (CredentialVerifier, error) {
	if len(records) == 0 {
		return nil, ErrCredentialConfiguration
	}

	immutable := make([]credentialRecord, len(records))
	for i, record := range records {
		if !validCredentialID(record.ID) || record.ExpiresAt.IsZero() || zeroDigest(record.Digest) {
			return nil, ErrCredentialConfiguration
		}
		for j := 0; j < i; j++ {
			if record.ID == immutable[j].id || record.Digest == immutable[j].digest {
				return nil, ErrCredentialConfiguration
			}
		}
		immutable[i] = credentialRecord{
			id:        record.ID,
			digest:    record.Digest,
			expiresAt: record.ExpiresAt,
			revoked:   record.Revoked,
		}
	}
	return &credentialVerifier{records: immutable, comparisonHook: comparisonHook}, nil
}

func (v *credentialVerifier) Verify(ctx context.Context, raw []byte, peer netip.Addr) (CredentialGrant, error) {
	_ = peer
	if ctx == nil {
		return CredentialGrant{}, ErrCredentialRejected
	}

	digest := sha256.Sum256(raw)
	now := time.Now()
	var grant CredentialGrant
	accepted := false
	for _, record := range v.records {
		if v.comparisonHook != nil {
			v.comparisonHook()
		}
		matches := subtle.ConstantTimeCompare(digest[:], record.digest[:])
		if matches == 1 && !record.revoked && now.Before(record.expiresAt) {
			grant = CredentialGrant{ID: record.id, ExpiresAt: record.expiresAt}
			accepted = true
		}
	}
	if ctx.Err() != nil || !accepted {
		return CredentialGrant{}, ErrCredentialRejected
	}
	return grant, nil
}

func validCredentialID(id string) bool {
	if len(id) == 0 || len(id) > 128 {
		return false
	}
	for i := range id {
		b := id[i]
		if !((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') || b == '-' || b == '_' || b == '.') {
			return false
		}
	}
	return true
}

func zeroDigest(digest [sha256.Size]byte) bool {
	var zero [sha256.Size]byte
	return digest == zero
}
