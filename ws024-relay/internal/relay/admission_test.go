package relay

import (
	"context"
	"errors"
	"net/netip"
	"strconv"
	"sync"
	"testing"
)

func newTestAdmission(t *testing.T, limits AdmissionLimits) AdmissionController {
	t.Helper()
	controller, err := NewAdmissionController(limits)
	if err != nil {
		t.Fatalf("NewAdmissionController() error = %v", err)
	}
	return controller
}

func TestAdmissionRejectsInvalidLimits(t *testing.T) {
	for _, limits := range []AdmissionLimits{
		{},
		{MaxPerCredential: 0, MaxPerPeer: 1, MaxGlobal: 1},
		{MaxPerCredential: 1, MaxPerPeer: 0, MaxGlobal: 1},
		{MaxPerCredential: 1, MaxPerPeer: 1, MaxGlobal: 0},
		{MaxPerCredential: maxConcurrentPerCredential + 1, MaxPerPeer: 1, MaxGlobal: 1},
		{MaxPerCredential: 1, MaxPerPeer: maxConcurrentPerPeer + 1, MaxGlobal: 1},
		{MaxPerCredential: 1, MaxPerPeer: 1, MaxGlobal: maxConcurrentGlobally + 1},
	} {
		controller, err := NewAdmissionController(limits)
		if controller != nil || err != ErrAdmissionConfiguration {
			t.Fatalf("NewAdmissionController(%+v) = (%v, %v), want configuration rejection", limits, controller, err)
		}
	}
}

func TestAdmissionEnforcesEachLimitAndKeepsFailureAtomic(t *testing.T) {
	peer1 := netip.MustParseAddr("198.51.100.1")
	peer2 := netip.MustParseAddr("198.51.100.2")

	t.Run("credential", func(t *testing.T) {
		controller := newTestAdmission(t, AdmissionLimits{MaxPerCredential: 2, MaxPerPeer: 4, MaxGlobal: 64})
		var releases []func()
		for _, peer := range []netip.Addr{peer1, peer2} {
			release, err := controller.Admit(context.Background(), "credential-a", peer)
			if err != nil {
				t.Fatalf("Admit() error = %v", err)
			}
			releases = append(releases, release)
		}
		defer func() {
			for _, release := range releases {
				release()
			}
		}()
		assertAdmissionRejectedWithoutMutation(t, controller, "credential-a", netip.MustParseAddr("198.51.100.3"))
	})

	t.Run("peer", func(t *testing.T) {
		controller := newTestAdmission(t, AdmissionLimits{MaxPerCredential: 2, MaxPerPeer: 4, MaxGlobal: 64})
		var releases []func()
		for i := range 4 {
			release, err := controller.Admit(context.Background(), "credential-"+strconv.Itoa(i), peer1)
			if err != nil {
				t.Fatalf("Admit() error = %v", err)
			}
			releases = append(releases, release)
		}
		defer func() {
			for _, release := range releases {
				release()
			}
		}()
		assertAdmissionRejectedWithoutMutation(t, controller, "credential-e", peer1)
	})

	t.Run("global", func(t *testing.T) {
		controller := newTestAdmission(t, AdmissionLimits{MaxPerCredential: 2, MaxPerPeer: 4, MaxGlobal: 64})
		var releases []func()
		for i := range 64 {
			peer := netip.AddrFrom4([4]byte{198, 51, 100, byte(i + 1)})
			release, err := controller.Admit(context.Background(), "credential-global-"+strconv.Itoa(i), peer)
			if err != nil {
				t.Fatalf("Admit() at %d error = %v", i, err)
			}
			releases = append(releases, release)
		}
		defer func() {
			for _, release := range releases {
				release()
			}
		}()
		assertAdmissionRejectedWithoutMutation(t, controller, "credential-over-global", netip.MustParseAddr("198.51.100.200"))
	})
}

func assertAdmissionRejectedWithoutMutation(t *testing.T, controller AdmissionController, credentialID string, peer netip.Addr) {
	t.Helper()
	before := controller.(*admissionController).snapshot()
	release, err := controller.Admit(context.Background(), credentialID, peer)
	if release != nil || err != ErrAdmissionRejected || !errors.Is(err, ErrAdmissionRejected) {
		t.Fatalf("Admit() returned release=%t error=%v, want generic rejection", release != nil, err)
	}
	if after := controller.(*admissionController).snapshot(); after != before {
		t.Fatalf("failed Admit() changed counters from %+v to %+v", before, after)
	}
}

func TestAdmissionRejectsCanceledAndInvalidPeersWithoutMutation(t *testing.T) {
	controller := newTestAdmission(t, AdmissionLimits{MaxPerCredential: 2, MaxPerPeer: 4, MaxGlobal: 64})
	canceled, cancel := context.WithCancel(context.Background())
	cancel()
	for _, tc := range []struct {
		name string
		ctx  context.Context
		peer netip.Addr
	}{
		{"canceled", canceled, netip.MustParseAddr("203.0.113.1")},
		{"invalid", context.Background(), netip.Addr{}},
		{"mapped", context.Background(), netip.MustParseAddr("::ffff:203.0.113.1")},
		{"zoned", context.Background(), netip.MustParseAddr("2001:db8::1%client-zone")},
	} {
		t.Run(tc.name, func(t *testing.T) {
			before := controller.(*admissionController).snapshot()
			release, err := controller.Admit(tc.ctx, "credential-a", tc.peer)
			if release != nil || err != ErrAdmissionRejected {
				t.Fatalf("Admit() returned release=%t error=%v, want generic rejection", release != nil, err)
			}
			if after := controller.(*admissionController).snapshot(); after != before {
				t.Fatalf("rejected Admit() changed counters from %+v to %+v", before, after)
			}
		})
	}
}

func TestAdmissionReleaseIsConcurrentSafeAndCleansUp(t *testing.T) {
	controller := newTestAdmission(t, AdmissionLimits{MaxPerCredential: 2, MaxPerPeer: 4, MaxGlobal: 64})
	release, err := controller.Admit(context.Background(), "credential-a", netip.MustParseAddr("203.0.113.1"))
	if err != nil {
		t.Fatalf("Admit() error = %v", err)
	}

	var wait sync.WaitGroup
	for range 16 {
		wait.Add(1)
		go func() {
			defer wait.Done()
			release()
		}()
	}
	wait.Wait()
	release()
	if got, want := controller.(*admissionController).snapshot(), (admissionSnapshot{}); got != want {
		t.Fatalf("counters after repeated release = %+v, want %+v", got, want)
	}
}

func TestAdmissionParallelAdmitAndRelease(t *testing.T) {
	controller := newTestAdmission(t, AdmissionLimits{MaxPerCredential: 2, MaxPerPeer: 4, MaxGlobal: 64})
	peers := []netip.Addr{
		netip.MustParseAddr("198.51.100.1"),
		netip.MustParseAddr("198.51.100.2"),
		netip.MustParseAddr("198.51.100.3"),
		netip.MustParseAddr("198.51.100.4"),
	}
	var wait sync.WaitGroup
	for i := range 128 {
		wait.Add(1)
		go func(i int) {
			defer wait.Done()
			release, err := controller.Admit(context.Background(), "credential-parallel", peers[i%len(peers)])
			if err == nil {
				release()
				return
			}
			if err != ErrAdmissionRejected {
				t.Errorf("Admit() error = %v, want only ErrAdmissionRejected", err)
			}
		}(i)
	}
	wait.Wait()
	if got, want := controller.(*admissionController).snapshot(), (admissionSnapshot{}); got != want {
		t.Fatalf("counters after parallel work = %+v, want %+v", got, want)
	}
}
