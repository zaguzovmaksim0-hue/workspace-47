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

// credentialVerificationObservation contains only operation markers for
// package-internal tests. It never carries credential material or metadata.
type credentialVerificationObservation struct {
	Compared            bool
	RevocationEvaluated bool
	ExpiryEvaluated     bool
	Aggregated          bool
}

type credentialVerifier struct {
	records         []credentialRecord
	observationHook func(credentialVerificationObservation)
}

// NewCredentialVerifier copies and validates digest-only records so callers
// cannot mutate verifier configuration after construction.
func NewCredentialVerifier(records []CredentialRecord) (CredentialVerifier, error) {
	return newCredentialVerifier(records, nil)
}

// newCredentialVerifier permits a test-only operation observer. Digest
// comparison itself remains pinned to crypto/subtle.ConstantTimeCompare.
func newCredentialVerifier(records []CredentialRecord, observationHook func(credentialVerificationObservation)) (CredentialVerifier, error) {
	if len(records) == 0 {
		return nil, ErrCredentialConfiguration
	}

	immutable := make([]credentialRecord, len(records))
	emptyDigest := sha256.Sum256(nil)
	for i, record := range records {
		if !validCredentialID(record.ID) || record.ExpiresAt.IsZero() || zeroDigest(record.Digest) || record.Digest == emptyDigest {
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
	return &credentialVerifier{records: immutable, observationHook: observationHook}, nil
}

func (v *credentialVerifier) Verify(ctx context.Context, raw []byte, peer netip.Addr) (CredentialGrant, error) {
	_ = peer
	if ctx == nil {
		return CredentialGrant{}, ErrCredentialRejected
	}

	digest := sha256.Sum256(raw)
	now := time.Now()
	accepted := 0
	acceptedIndex := 0
	for i, record := range v.records {
		matches := v.compare(digest, record)
		revoked := v.revocationState(record)
		expired := v.expiryState(now, record)
		candidate := v.aggregate(matches, revoked, expired, len(raw) == 0)

		accepted |= candidate
		acceptedIndex = subtle.ConstantTimeSelect(candidate, i, acceptedIndex)
	}
	if ctx.Err() != nil || accepted != 1 {
		return CredentialGrant{}, ErrCredentialRejected
	}
	record := v.records[acceptedIndex]
	return CredentialGrant{ID: record.id, ExpiresAt: record.expiresAt}, nil
}

func (v *credentialVerifier) compare(digest [sha256.Size]byte, record credentialRecord) int {
	matches := subtle.ConstantTimeCompare(digest[:], record.digest[:])
	v.observe(credentialVerificationObservation{Compared: true})
	return matches
}

func (v *credentialVerifier) revocationState(record credentialRecord) bool {
	revoked := record.revoked
	v.observe(credentialVerificationObservation{RevocationEvaluated: true})
	return revoked
}

func (v *credentialVerifier) expiryState(now time.Time, record credentialRecord) bool {
	expired := !now.Before(record.expiresAt)
	v.observe(credentialVerificationObservation{ExpiryEvaluated: true})
	return expired
}

func (v *credentialVerifier) aggregate(matches int, revoked, expired, emptyRaw bool) int {
	candidate := 0
	if matches == 1 && !revoked && !expired && !emptyRaw {
		candidate = 1
	}
	v.observe(credentialVerificationObservation{Aggregated: true})
	return candidate
}

func (v *credentialVerifier) observe(observation credentialVerificationObservation) {
	if v.observationHook != nil {
		v.observationHook(observation)
	}
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
