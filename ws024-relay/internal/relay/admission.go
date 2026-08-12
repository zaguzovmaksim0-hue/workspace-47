package relay

import (
	"context"
	"errors"
	"net/netip"
	"sync"
)

const (
	maxConcurrentPerCredential = 2
	maxConcurrentPerPeer       = 4
	maxConcurrentGlobally      = 64
)

var (
	// ErrAdmissionRejected is the sole public outcome for a rejected admission,
	// including cancellation and every capacity or input validation failure.
	ErrAdmissionRejected = errors.New("admission rejected")

	// ErrAdmissionConfiguration reports invalid limits without exposing values.
	ErrAdmissionConfiguration = errors.New("invalid admission configuration")
)

// AdmissionController reserves all active-session capacity atomically. The
// release function must be called when the admitted session finishes.
type AdmissionController interface {
	Admit(ctx context.Context, credentialID string, peer netip.Addr) (release func(), err error)
}

// AdmissionLimits may reduce, but never increase, the compiled safety caps.
type AdmissionLimits struct {
	MaxPerCredential int
	MaxPerPeer       int
	MaxGlobal        int
}

type admissionController struct {
	mu sync.Mutex

	active       int
	byCredential map[string]int
	byPeer       map[netip.Addr]int

	maxPerCredential int
	maxPerPeer       int
	maxGlobal        int

	// releaseStateObserver is a package-internal test seam. It only observes
	// whether sensitive admission state has been cleared, never its values.
	releaseStateObserver func(admissionReleaseState)
}

type admissionReleaseState struct {
	credentialIDCleared bool
	peerCleared         bool
}

type admissionReleaseTicket struct {
	controller   *admissionController
	credentialID string
	peer         netip.Addr
	once         sync.Once
}

// NewAdmissionController constructs an immediate-rejection controller: it
// never creates a wait queue or holds callers after a limit is reached.
func NewAdmissionController(limits AdmissionLimits) (AdmissionController, error) {
	if limits.MaxPerCredential <= 0 || limits.MaxPerCredential > maxConcurrentPerCredential ||
		limits.MaxPerPeer <= 0 || limits.MaxPerPeer > maxConcurrentPerPeer ||
		limits.MaxGlobal <= 0 || limits.MaxGlobal > maxConcurrentGlobally {
		return nil, ErrAdmissionConfiguration
	}
	return &admissionController{
		byCredential:     make(map[string]int),
		byPeer:           make(map[netip.Addr]int),
		maxPerCredential: limits.MaxPerCredential,
		maxPerPeer:       limits.MaxPerPeer,
		maxGlobal:        limits.MaxGlobal,
	}, nil
}

func (a *admissionController) Admit(ctx context.Context, credentialID string, peer netip.Addr) (func(), error) {
	if ctx == nil || ctx.Err() != nil || !validCredentialID(credentialID) || !validAdmissionPeer(peer) {
		return nil, ErrAdmissionRejected
	}

	a.mu.Lock()
	defer a.mu.Unlock()
	if ctx.Err() != nil || a.active >= a.maxGlobal ||
		a.byCredential[credentialID] >= a.maxPerCredential ||
		a.byPeer[peer] >= a.maxPerPeer {
		return nil, ErrAdmissionRejected
	}

	a.active++
	a.byCredential[credentialID]++
	a.byPeer[peer]++

	ticket := &admissionReleaseTicket{
		controller:   a,
		credentialID: credentialID,
		peer:         peer,
	}
	return ticket.release, nil
}

func (t *admissionReleaseTicket) release() {
	t.once.Do(func() {
		t.controller.mu.Lock()
		decrementCredentialCount(t.controller.byCredential, t.credentialID)
		decrementPeerCount(t.controller.byPeer, t.peer)
		t.controller.active--
		t.credentialID = ""
		t.peer = netip.Addr{}
		observer := t.controller.releaseStateObserver
		t.controller.mu.Unlock()

		if observer != nil {
			observer(admissionReleaseState{
				credentialIDCleared: t.credentialID == "",
				peerCleared:         t.peer == (netip.Addr{}),
			})
		}
	})
}

func validAdmissionPeer(peer netip.Addr) bool {
	return peer.IsValid() && peer.Zone() == "" && peer == peer.Unmap()
}

func decrementCredentialCount(counts map[string]int, id string) {
	if counts[id] == 1 {
		delete(counts, id)
		return
	}
	counts[id]--
}

func decrementPeerCount(counts map[netip.Addr]int, peer netip.Addr) {
	if counts[peer] == 1 {
		delete(counts, peer)
		return
	}
	counts[peer]--
}

type admissionSnapshot struct {
	Active            int
	CredentialEntries int
	PeerEntries       int
}

func (a *admissionController) snapshot() admissionSnapshot {
	a.mu.Lock()
	defer a.mu.Unlock()
	return admissionSnapshot{
		Active:            a.active,
		CredentialEntries: len(a.byCredential),
		PeerEntries:       len(a.byPeer),
	}
}
