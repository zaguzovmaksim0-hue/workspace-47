package relay

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"io"
	"net"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestPumpPreservesBufferedPostCONNECTBytesAndCopiesBothDirections(t *testing.T) {
	downstream := newMemoryConn(nil)
	upstream := newMemoryConn([]byte("inner-tls-response"))
	downstreamReader := bufio.NewReaderSize(bytes.NewReader([]byte("buffered-inner-tls-request")), 64)

	result, err := Pump(context.Background(), downstream, downstreamReader, upstream, testPumpLimits())
	if err != nil {
		t.Fatalf("Pump() error = %v", err)
	}
	if got, want := upstream.writtenString(), "buffered-inner-tls-request"; got != want {
		t.Fatalf("upstream bytes = %q, want %q", got, want)
	}
	if got, want := downstream.writtenString(), "inner-tls-response"; got != want {
		t.Fatalf("downstream bytes = %q, want %q", got, want)
	}
	if result.DownstreamToUpstreamBytes != int64(len("buffered-inner-tls-request")) ||
		result.UpstreamToDownstreamBytes != int64(len("inner-tls-response")) {
		t.Fatalf("PumpResult = %+v", result)
	}
	if upstream.closeWriteCalls.Load() != 1 || downstream.closeWriteCalls.Load() != 1 {
		t.Fatalf("CloseWrite calls = upstream:%d downstream:%d, want 1 each",
			upstream.closeWriteCalls.Load(), downstream.closeWriteCalls.Load())
	}
	if !upstream.closed.Load() || !downstream.closed.Load() {
		t.Fatal("Pump did not close both owned connections before returning")
	}
}

func TestPumpHalfCloseLetsReverseDirectionFinishAfterRequestEOF(t *testing.T) {
	downstream := newMemoryConn(nil)
	upstream := newGateReadConn([]byte("late-response"))
	downstreamReader := bytes.NewReader([]byte("request"))

	done := make(chan struct{})
	var result PumpResult
	var pumpErr error
	go func() {
		defer close(done)
		result, pumpErr = Pump(context.Background(), downstream, downstreamReader, upstream, testPumpLimits())
	}()

	waitFor(t, time.Second, func() bool { return upstream.closeWriteCalls.Load() == 1 })
	if got, want := upstream.writtenString(), "request"; got != want {
		t.Fatalf("request bytes = %q, want %q", got, want)
	}
	upstream.releaseRead()

	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("Pump did not finish after reverse direction was released")
	}
	if pumpErr != nil {
		t.Fatalf("Pump() error = %v", pumpErr)
	}
	if got, want := downstream.writtenString(), "late-response"; got != want {
		t.Fatalf("reverse bytes = %q, want %q", got, want)
	}
	if result.DownstreamToUpstreamBytes != 7 || result.UpstreamToDownstreamBytes != 13 {
		t.Fatalf("PumpResult = %+v", result)
	}
}

func TestPumpEnforcesByteLimitIndependentlyInEachDirection(t *testing.T) {
	for _, tc := range []struct {
		name       string
		downstream []byte
		upstream   []byte
		wantDown   string
		wantUp     string
		wantD2U    int64
		wantU2D    int64
	}{
		{
			name:       "downstream to upstream",
			downstream: []byte("12345"),
			wantUp:     "1234",
			wantD2U:    4,
		},
		{
			name:     "upstream to downstream",
			upstream: []byte("abcde"),
			wantDown: "abcd",
			wantU2D:  4,
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			downstream := newMemoryConn(nil)
			upstream := newMemoryConn(tc.upstream)
			limits := testPumpLimits()
			limits.MaxBytesPerDirection = 4

			result, err := Pump(
				context.Background(),
				downstream,
				bytes.NewReader(tc.downstream),
				upstream,
				limits,
			)
			if !errors.Is(err, errPumpByteLimit) {
				t.Fatalf("Pump() error = %v, want byte limit", err)
			}
			if got := downstream.writtenString(); got != tc.wantDown {
				t.Fatalf("downstream bytes = %q, want %q", got, tc.wantDown)
			}
			if got := upstream.writtenString(); got != tc.wantUp {
				t.Fatalf("upstream bytes = %q, want %q", got, tc.wantUp)
			}
			if result.DownstreamToUpstreamBytes != tc.wantD2U ||
				result.UpstreamToDownstreamBytes != tc.wantU2D {
				t.Fatalf("PumpResult = %+v", result)
			}
		})
	}
}

func TestPumpAcceptsExactByteLimitWhenSourceEnds(t *testing.T) {
	downstream := newMemoryConn(nil)
	upstream := newMemoryConn(nil)
	limits := testPumpLimits()
	limits.MaxBytesPerDirection = 4

	result, err := Pump(context.Background(), downstream, bytes.NewReader([]byte("1234")), upstream, limits)
	if err != nil {
		t.Fatalf("Pump() error = %v", err)
	}
	if got, want := upstream.writtenString(), "1234"; got != want {
		t.Fatalf("upstream bytes = %q, want %q", got, want)
	}
	if result.DownstreamToUpstreamBytes != 4 || result.UpstreamToDownstreamBytes != 0 {
		t.Fatalf("PumpResult = %+v", result)
	}
}

func TestPumpIdleTimeoutClosesConnectionsAndJoinsReaders(t *testing.T) {
	downstream := newBlockingConn()
	upstream := newBlockingConn()
	limits := testPumpLimits()
	limits.IdleTimeout = 30 * time.Millisecond
	limits.MaxSessionDuration = time.Second

	started := time.Now()
	_, err := Pump(context.Background(), downstream, downstream, upstream, limits)
	if !errors.Is(err, errPumpIdleTimeout) {
		t.Fatalf("Pump() error = %v, want idle timeout", err)
	}
	if elapsed := time.Since(started); elapsed >= 500*time.Millisecond {
		t.Fatalf("idle timeout returned too late: %v", elapsed)
	}
	assertBlockingConnStopped(t, downstream)
	assertBlockingConnStopped(t, upstream)
}

func TestPumpSessionDurationClosesConnectionsAndJoinsReaders(t *testing.T) {
	downstream := newBlockingConn()
	upstream := newBlockingConn()
	limits := testPumpLimits()
	limits.IdleTimeout = time.Second
	limits.MaxSessionDuration = 30 * time.Millisecond

	started := time.Now()
	_, err := Pump(context.Background(), downstream, downstream, upstream, limits)
	if !errors.Is(err, errPumpSessionDuration) {
		t.Fatalf("Pump() error = %v, want session duration", err)
	}
	if elapsed := time.Since(started); elapsed >= 500*time.Millisecond {
		t.Fatalf("session timeout returned too late: %v", elapsed)
	}
	assertBlockingConnStopped(t, downstream)
	assertBlockingConnStopped(t, upstream)
}

func TestPumpCancellationClosesConnectionsAndJoinsReaders(t *testing.T) {
	downstream := newBlockingConn()
	upstream := newBlockingConn()
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() {
		_, err := Pump(ctx, downstream, downstream, upstream, testPumpLimits())
		done <- err
	}()

	waitFor(t, time.Second, func() bool {
		return downstream.activeReads.Load() == 1 && upstream.activeReads.Load() == 1
	})
	cancel()

	select {
	case err := <-done:
		if !errors.Is(err, context.Canceled) {
			t.Fatalf("Pump() error = %v, want context cancellation", err)
		}
	case <-time.After(time.Second):
		t.Fatal("Pump did not return after cancellation")
	}
	assertBlockingConnStopped(t, downstream)
	assertBlockingConnStopped(t, upstream)
}

func TestPumpActivityResetsIdleTimeout(t *testing.T) {
	downstreamServer, downstreamClient := net.Pipe()
	upstreamServer, upstreamClient := net.Pipe()
	defer downstreamClient.Close()
	defer upstreamClient.Close()
	limits := testPumpLimits()
	limits.IdleTimeout = 80 * time.Millisecond
	limits.MaxSessionDuration = time.Second

	done := make(chan error, 1)
	go func() {
		_, err := Pump(context.Background(), downstreamServer, downstreamServer, upstreamServer, limits)
		done <- err
	}()

	for _, chunk := range []string{"one", "two", "three"} {
		if _, err := downstreamClient.Write([]byte(chunk)); err != nil {
			t.Fatalf("downstream write error = %v", err)
		}
		got := make([]byte, len(chunk))
		if _, err := io.ReadFull(upstreamClient, got); err != nil {
			t.Fatalf("upstream read error = %v", err)
		}
		if string(got) != chunk {
			t.Fatalf("chunk = %q, want %q", got, chunk)
		}
		time.Sleep(35 * time.Millisecond)
	}

	_ = downstreamClient.Close()
	_ = upstreamClient.Close()
	select {
	case err := <-done:
		if err != nil && !errors.Is(err, errPumpIO) {
			t.Fatalf("Pump() error = %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("Pump did not finish after peers closed")
	}
}

func TestPumpClearsEveryNonPooledDirectionBufferBeforeReturn(t *testing.T) {
	downstream := newMemoryConn(nil)
	upstream := newMemoryConn(nil)
	var buffersMu sync.Mutex
	var buffers [][]byte
	factory := func() []byte {
		buffer := bytes.Repeat([]byte{0xa5}, 64)
		buffersMu.Lock()
		buffers = append(buffers, buffer)
		buffersMu.Unlock()
		return buffer
	}

	_, err := pumpWithBufferFactory(
		context.Background(),
		downstream,
		bytes.NewReader([]byte("sensitive-inner-tls-record")),
		upstream,
		testPumpLimits(),
		factory,
	)
	if err != nil {
		t.Fatalf("pumpWithBufferFactory() error = %v", err)
	}
	buffersMu.Lock()
	defer buffersMu.Unlock()
	if len(buffers) != 2 {
		t.Fatalf("buffer count = %d, want 2", len(buffers))
	}
	for i, buffer := range buffers {
		for j, b := range buffer {
			if b != 0 {
				t.Fatalf("buffer %d byte %d = %x, want zero", i, j, b)
			}
		}
	}
}

func TestPumpRejectsInvalidOrOverDesignLimitsBeforeIO(t *testing.T) {
	valid := testPumpLimits()
	cases := []PumpLimits{
		{},
		{IdleTimeout: -time.Second, MaxSessionDuration: valid.MaxSessionDuration, MaxBytesPerDirection: valid.MaxBytesPerDirection},
		{IdleTimeout: maxPumpIdleTimeout + time.Nanosecond, MaxSessionDuration: valid.MaxSessionDuration, MaxBytesPerDirection: valid.MaxBytesPerDirection},
		{IdleTimeout: valid.IdleTimeout, MaxSessionDuration: -time.Second, MaxBytesPerDirection: valid.MaxBytesPerDirection},
		{IdleTimeout: valid.IdleTimeout, MaxSessionDuration: maxPumpSessionDuration + time.Nanosecond, MaxBytesPerDirection: valid.MaxBytesPerDirection},
		{IdleTimeout: valid.IdleTimeout, MaxSessionDuration: valid.MaxSessionDuration, MaxBytesPerDirection: -1},
		{IdleTimeout: valid.IdleTimeout, MaxSessionDuration: valid.MaxSessionDuration, MaxBytesPerDirection: maxPumpBytesPerDirection + 1},
	}
	for i, limits := range cases {
		downstream := newMemoryConn(nil)
		upstream := newMemoryConn(nil)
		_, err := Pump(context.Background(), downstream, downstream, upstream, limits)
		if !errors.Is(err, errInvalidPumpLimits) {
			t.Fatalf("case %d error = %v, want invalid limits", i, err)
		}
		if downstream.readCalls.Load() != 0 || upstream.readCalls.Load() != 0 {
			t.Fatalf("case %d performed I/O before rejecting limits", i)
		}
	}
}

func TestPumpRejectsNilDependenciesGenerically(t *testing.T) {
	limits := testPumpLimits()
	downstream := newMemoryConn(nil)
	upstream := newMemoryConn(nil)
	for i, tc := range []struct {
		ctx              context.Context
		downstream       net.Conn
		downstreamReader io.Reader
		upstream         net.Conn
	}{
		{nil, downstream, downstream, upstream},
		{context.Background(), nil, downstream, upstream},
		{context.Background(), downstream, nil, upstream},
		{context.Background(), downstream, downstream, nil},
	} {
		_, err := Pump(tc.ctx, tc.downstream, tc.downstreamReader, tc.upstream, limits)
		if !errors.Is(err, errInvalidPumpLimits) {
			t.Fatalf("case %d error = %v, want generic invalid pump parameters", i, err)
		}
	}
}

func testPumpLimits() PumpLimits {
	return PumpLimits{
		IdleTimeout:          time.Second,
		MaxSessionDuration:   2 * time.Second,
		MaxBytesPerDirection: 1024,
	}
}

func waitFor(t *testing.T, timeout time.Duration, condition func() bool) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if condition() {
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatal("condition was not met before timeout")
}

type memoryConn struct {
	reader          io.Reader
	mu              sync.Mutex
	written         bytes.Buffer
	closed          atomic.Bool
	closeWriteCalls atomic.Int32
	readCalls       atomic.Int32
}

func newMemoryConn(readData []byte) *memoryConn {
	return &memoryConn{reader: bytes.NewReader(readData)}
}

func (c *memoryConn) Read(p []byte) (int, error) {
	c.readCalls.Add(1)
	return c.reader.Read(p)
}

func (c *memoryConn) Write(p []byte) (int, error) {
	if c.closed.Load() {
		return 0, net.ErrClosed
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.written.Write(p)
}

func (c *memoryConn) Close() error {
	c.closed.Store(true)
	return nil
}

func (c *memoryConn) CloseWrite() error {
	c.closeWriteCalls.Add(1)
	return nil
}

func (c *memoryConn) writtenString() string {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.written.String()
}

func (c *memoryConn) LocalAddr() net.Addr              { return testAddr("local") }
func (c *memoryConn) RemoteAddr() net.Addr             { return testAddr("remote") }
func (c *memoryConn) SetDeadline(time.Time) error      { return nil }
func (c *memoryConn) SetReadDeadline(time.Time) error  { return nil }
func (c *memoryConn) SetWriteDeadline(time.Time) error { return nil }

type gateReadConn struct {
	*memoryConn
	gate     chan struct{}
	released atomic.Bool
}

func newGateReadConn(readData []byte) *gateReadConn {
	return &gateReadConn{memoryConn: newMemoryConn(readData), gate: make(chan struct{})}
}

func (c *gateReadConn) Read(p []byte) (int, error) {
	<-c.gate
	return c.memoryConn.Read(p)
}

func (c *gateReadConn) releaseRead() {
	if c.released.CompareAndSwap(false, true) {
		close(c.gate)
	}
}

func (c *gateReadConn) Close() error {
	c.releaseRead()
	return c.memoryConn.Close()
}

type blockingConn struct {
	closed      chan struct{}
	closeOnce   sync.Once
	closedFlag  atomic.Bool
	activeReads atomic.Int32
}

func newBlockingConn() *blockingConn {
	return &blockingConn{closed: make(chan struct{})}
}

func (c *blockingConn) Read([]byte) (int, error) {
	c.activeReads.Add(1)
	defer c.activeReads.Add(-1)
	<-c.closed
	return 0, net.ErrClosed
}

func (c *blockingConn) Write(p []byte) (int, error) {
	select {
	case <-c.closed:
		return 0, net.ErrClosed
	default:
		return len(p), nil
	}
}

func (c *blockingConn) Close() error {
	c.closeOnce.Do(func() {
		c.closedFlag.Store(true)
		close(c.closed)
	})
	return nil
}

func (c *blockingConn) CloseWrite() error               { return nil }
func (c *blockingConn) LocalAddr() net.Addr             { return testAddr("local") }
func (c *blockingConn) RemoteAddr() net.Addr            { return testAddr("remote") }
func (c *blockingConn) SetDeadline(time.Time) error     { return nil }
func (c *blockingConn) SetReadDeadline(time.Time) error { return nil }
func (c *blockingConn) SetWriteDeadline(time.Time) error {
	return nil
}

func assertBlockingConnStopped(t *testing.T, conn *blockingConn) {
	t.Helper()
	if !conn.closedFlag.Load() {
		t.Fatal("connection was not closed")
	}
	if conn.activeReads.Load() != 0 {
		t.Fatalf("active reads = %d, want 0 after Pump return", conn.activeReads.Load())
	}
}

type testAddr string

func (a testAddr) Network() string { return "test" }
func (a testAddr) String() string  { return string(a) }
