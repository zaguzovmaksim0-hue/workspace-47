package relay

import (
	"context"
	"crypto/sha256"
	"errors"
	"net/netip"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func credentialRecordFor(id, token string, expiresAt time.Time, revoked bool) CredentialRecord {
	return CredentialRecord{
		ID:        id,
		Digest:    sha256.Sum256([]byte(token)),
		ExpiresAt: expiresAt,
		Revoked:   revoked,
	}
}

func TestCredentialVerifierGrantsActiveCredential(t *testing.T) {
	expiresAt := time.Now().Add(time.Hour).Round(0)
	verifier, err := NewCredentialVerifier([]CredentialRecord{
		credentialRecordFor("qa-credential-1", "correct-token", expiresAt, false),
	})
	if err != nil {
		t.Fatalf("NewCredentialVerifier() error = %v", err)
	}

	grant, err := verifier.Verify(context.Background(), []byte("correct-token"), netip.MustParseAddr("203.0.113.7"))
	if err != nil {
		t.Fatalf("Verify() error = %v", err)
	}
	if grant.ID != "qa-credential-1" || !grant.ExpiresAt.Equal(expiresAt) {
		t.Fatalf("Verify() grant = %#v, want active credential grant", grant)
	}
}

func TestCredentialVerifierRejectsAllInvalidCredentialsWithOneError(t *testing.T) {
	secret := "credential-secret-that-must-not-leak"
	digestText := "0123456789abcdef"
	peer := netip.MustParseAddr("2001:db8::45")
	now := time.Now()
	verifier, err := NewCredentialVerifier([]CredentialRecord{
		credentialRecordFor("active-credential", secret, now.Add(time.Hour), false),
		credentialRecordFor("expired-credential", "expired-secret", now.Add(-time.Second), false),
		credentialRecordFor("revoked-credential", "revoked-secret", now.Add(time.Hour), true),
	})
	if err != nil {
		t.Fatalf("NewCredentialVerifier() error = %v", err)
	}

	canceled, cancel := context.WithCancel(context.Background())
	cancel()
	for _, tc := range []struct {
		name string
		ctx  context.Context
		raw  []byte
	}{
		{"missing", context.Background(), nil},
		{"empty", context.Background(), []byte{}},
		{"malformed", context.Background(), []byte{0xff, 0x00, 0x01}},
		{"wrong", context.Background(), []byte("wrong-secret")},
		{"expired", context.Background(), []byte("expired-secret")},
		{"revoked", context.Background(), []byte("revoked-secret")},
		{"canceled", canceled, []byte(secret)},
	} {
		t.Run(tc.name, func(t *testing.T) {
			grant, err := verifier.Verify(tc.ctx, tc.raw, peer)
			if err != ErrCredentialRejected {
				t.Fatalf("Verify() error = %v, want ErrCredentialRejected", err)
			}
			if !errors.Is(err, ErrCredentialRejected) {
				t.Fatal("Verify() error does not match ErrCredentialRejected")
			}
			if grant != (CredentialGrant{}) {
				t.Fatalf("Verify() grant = %#v, want zero grant on rejection", grant)
			}
			for _, forbidden := range []string{secret, digestText, "active-credential", peer.String(), "expired", "revoked"} {
				if strings.Contains(err.Error(), forbidden) {
					t.Fatalf("rejection text leaks %q: %q", forbidden, err)
				}
			}
		})
	}
}

func TestCredentialVerifierRejectsEmptyRawAgainstEmptyDigestTestSeam(t *testing.T) {
	verifier := &credentialVerifier{records: []credentialRecord{{
		id:        "empty-token-test-seam",
		digest:    sha256.Sum256(nil),
		expiresAt: time.Now().Add(time.Hour),
	}}}

	grant, err := verifier.Verify(context.Background(), []byte{}, netip.Addr{})
	if err != ErrCredentialRejected {
		t.Fatalf("Verify() error = %v, want ErrCredentialRejected", err)
	}
	if grant != (CredentialGrant{}) {
		t.Fatalf("Verify() grant = %#v, want zero grant on rejection", grant)
	}
}

func TestCredentialVerifierCopiesRecordConfiguration(t *testing.T) {
	expiresAt := time.Now().Add(time.Hour)
	records := []CredentialRecord{credentialRecordFor("credential-1", "correct-token", expiresAt, false)}
	verifier, err := NewCredentialVerifier(records)
	if err != nil {
		t.Fatalf("NewCredentialVerifier() error = %v", err)
	}
	records[0] = credentialRecordFor("changed", "different-token", expiresAt, true)

	grant, err := verifier.Verify(context.Background(), []byte("correct-token"), netip.Addr{})
	if err != nil {
		t.Fatalf("Verify() error after caller configuration mutation = %v", err)
	}
	if grant.ID != "credential-1" {
		t.Fatalf("Verify() grant ID = %q, want immutable record ID", grant.ID)
	}
}

func TestCredentialVerifierScansEveryRecordAfterMatch(t *testing.T) {
	now := time.Now()
	records := []CredentialRecord{
		credentialRecordFor("first", "matching-token", now.Add(time.Hour), false),
		credentialRecordFor("second", "other-token-1", now.Add(time.Hour), false),
		credentialRecordFor("third", "other-token-2", now.Add(time.Hour), false),
	}
	var comparisons atomic.Int32
	verifier, err := newCredentialVerifier(records, func(observation credentialVerificationObservation) {
		if observation.Compared {
			comparisons.Add(1)
		}
	})
	if err != nil {
		t.Fatalf("newCredentialVerifier() error = %v", err)
	}

	if _, err := verifier.Verify(context.Background(), []byte("matching-token"), netip.Addr{}); err != nil {
		t.Fatalf("Verify() error = %v", err)
	}
	if got, want := comparisons.Load(), int32(len(records)); got != want {
		t.Fatalf("digest comparisons = %d, want full scan of %d records", got, want)
	}
}

func TestCredentialVerifierEvaluatesEveryRecordPolicyStateOnRejection(t *testing.T) {
	now := time.Now()
	records := []CredentialRecord{
		credentialRecordFor("active", "active-token", now.Add(time.Hour), false),
		credentialRecordFor("revoked", "revoked-token", now.Add(time.Hour), true),
		credentialRecordFor("expired", "expired-token", now.Add(-time.Hour), false),
	}

	for _, tc := range []struct {
		name string
		raw  []byte
	}{
		{"wrong", []byte("wrong-token")},
		{"revoked", []byte("revoked-token")},
		{"expired", []byte("expired-token")},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var comparisons, revocations, expiries, aggregations atomic.Int32
			verifier, err := newCredentialVerifier(records, func(observation credentialVerificationObservation) {
				if observation.Compared {
					comparisons.Add(1)
				}
				if observation.RevocationEvaluated {
					revocations.Add(1)
				}
				if observation.ExpiryEvaluated {
					expiries.Add(1)
				}
				if observation.Aggregated {
					aggregations.Add(1)
				}
			})
			if err != nil {
				t.Fatalf("newCredentialVerifier() error = %v", err)
			}

			if _, err := verifier.Verify(context.Background(), tc.raw, netip.Addr{}); err != ErrCredentialRejected {
				t.Fatalf("Verify() error = %v, want ErrCredentialRejected", err)
			}
			for name, got := range map[string]int32{
				"comparisons":  comparisons.Load(),
				"revocations":  revocations.Load(),
				"expiries":     expiries.Load(),
				"aggregations": aggregations.Load(),
			} {
				if want := int32(len(records)); got != want {
					t.Fatalf("%s = %d, want %d per-record evaluations", name, got, want)
				}
			}
		})
	}
}

func TestCredentialVerifierDoesNotMutateOrRetainCallerRaw(t *testing.T) {
	verifier, err := NewCredentialVerifier([]CredentialRecord{
		credentialRecordFor("credential-1", "mutable-token", time.Now().Add(time.Hour), false),
	})
	if err != nil {
		t.Fatalf("NewCredentialVerifier() error = %v", err)
	}

	raw := []byte("mutable-token")
	original := append([]byte(nil), raw...)
	if _, err := verifier.Verify(context.Background(), raw, netip.Addr{}); err != nil {
		t.Fatalf("Verify() error = %v", err)
	}
	if got := string(raw); got != string(original) {
		t.Fatalf("Verify() mutated raw from %q to %q", original, raw)
	}
	clear(raw)
	if _, err := verifier.Verify(context.Background(), []byte("wrong-token"), netip.Addr{}); err != ErrCredentialRejected {
		t.Fatalf("Verify() accepted a wrong credential after caller cleared raw: %v", err)
	}
	if _, err := verifier.Verify(context.Background(), original, netip.Addr{}); err != nil {
		t.Fatalf("Verify() failed after caller cleared the original buffer: %v", err)
	}
}

func TestCredentialVerifierRejectsUnsafeConfigurationWithoutSecrets(t *testing.T) {
	now := time.Now().Add(time.Hour)
	valid := credentialRecordFor("credential-1", "first-secret", now, false)
	duplicateID := credentialRecordFor("credential-1", "second-secret", now, false)
	duplicateDigest := credentialRecordFor("credential-2", "first-secret", now, false)
	zeroDigest := CredentialRecord{ID: "credential-3", ExpiresAt: now}
	emptyDigest := credentialRecordFor("credential-4", "", now, false)
	for _, tc := range []struct {
		name    string
		records []CredentialRecord
	}{
		{"empty", nil},
		{"unsafe id", []CredentialRecord{credentialRecordFor("unsafe id", "token", now, false)}},
		{"zero expiry", []CredentialRecord{{ID: "credential-4", Digest: sha256.Sum256([]byte("token"))}}},
		{"zero digest", []CredentialRecord{zeroDigest}},
		{"empty digest", []CredentialRecord{emptyDigest}},
		{"duplicate id", []CredentialRecord{valid, duplicateID}},
		{"duplicate digest", []CredentialRecord{valid, duplicateDigest}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, err := NewCredentialVerifier(tc.records)
			if err != ErrCredentialConfiguration {
				t.Fatalf("NewCredentialVerifier() error = %v, want ErrCredentialConfiguration", err)
			}
			for _, forbidden := range []string{"first-secret", "second-secret", "credential-1", "unsafe id"} {
				if strings.Contains(err.Error(), forbidden) {
					t.Fatalf("configuration error leaks %q: %q", forbidden, err)
				}
			}
		})
	}
}
