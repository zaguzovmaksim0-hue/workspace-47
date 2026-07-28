package relay

import (
	"context"
	"errors"
	"io"
	"net"
	"sync"
	"time"
)

const (
	maxPumpIdleTimeout          = 30 * time.Second
	maxPumpSessionDuration      = 90 * time.Second
	maxPumpBytesPerDirection    = 4 * 1024 * 1024
	defaultPumpDirectionBufSize = 32 * 1024
	maxConsecutiveEmptyReads    = 100
)

var (
	errInvalidPumpLimits   = errors.New("invalid pump parameters")
	errPumpIdleTimeout     = errors.New("pump idle timeout")
	errPumpSessionDuration = errors.New("pump session duration exceeded")
	errPumpByteLimit       = errors.New("pump byte limit exceeded")
	errPumpIO              = errors.New("pump I/O failed")
)

// PumpLimits bounds one opaque nested-TLS session. Values may be lower than
// the design maxima for tests or conservative deployment configuration, but
// they can never raise those maxima.
type PumpLimits struct {
	IdleTimeout          time.Duration
	MaxSessionDuration   time.Duration
	MaxBytesPerDirection int64
}

// PumpResult contains exact in-memory counters for later conversion to coarse
// audit buckets. It never contains payload, peer, authority, credential, or a
// request identifier.
type PumpResult struct {
	DownstreamToUpstreamBytes int64
	UpstreamToDownstreamBytes int64
}

// Pump copies the opaque inner-TLS stream in both directions. downstreamReader
// must be the reader that parsed CONNECT so bytes already buffered after the
// header are forwarded before additional reads from downstream.
func Pump(
	ctx context.Context,
	downstream net.Conn,
	downstreamReader io.Reader,
	upstream net.Conn,
	limits PumpLimits,
) (PumpResult, error) {
	return pumpWithBufferFactory(
		ctx,
		downstream,
		downstreamReader,
		upstream,
		limits,
		func() []byte { return make([]byte, defaultPumpDirectionBufSize) },
	)
}

type pumpDirection uint8

const (
	pumpDownstreamToUpstream pumpDirection = iota
	pumpUpstreamToDownstream
)

type pumpDirectionResult struct {
	direction pumpDirection
	bytes     int64
	err       error
	dst       net.Conn
}

type closeWriter interface {
	CloseWrite() error
}

func pumpWithBufferFactory(
	ctx context.Context,
	downstream net.Conn,
	downstreamReader io.Reader,
	upstream net.Conn,
	limits PumpLimits,
	bufferFactory func() []byte,
) (PumpResult, error) {
	if !validPumpParameters(ctx, downstream, downstreamReader, upstream, limits, bufferFactory) {
		return PumpResult{}, errInvalidPumpLimits
	}

	downstreamBuffer := bufferFactory()
	upstreamBuffer := bufferFactory()
	if len(downstreamBuffer) == 0 || len(upstreamBuffer) == 0 {
		clear(downstreamBuffer)
		clear(upstreamBuffer)
		return PumpResult{}, errInvalidPumpLimits
	}
	defer clear(downstreamBuffer)
	defer clear(upstreamBuffer)

	var closeOnce sync.Once
	closeBoth := func() {
		closeOnce.Do(func() {
			_ = downstream.Close()
			_ = upstream.Close()
		})
	}
	if err := ctx.Err(); err != nil {
		closeBoth()
		return PumpResult{}, err
	}

	activity := make(chan struct{}, 1)
	results := make(chan pumpDirectionResult, 2)
	var workers sync.WaitGroup
	workers.Add(2)

	go func() {
		defer workers.Done()
		n, err := copyPumpDirection(
			downstreamReader,
			upstream,
			limits.MaxBytesPerDirection,
			activity,
			downstreamBuffer,
		)
		results <- pumpDirectionResult{
			direction: pumpDownstreamToUpstream,
			bytes:     n,
			err:       err,
			dst:       upstream,
		}
	}()
	go func() {
		defer workers.Done()
		n, err := copyPumpDirection(
			upstream,
			downstream,
			limits.MaxBytesPerDirection,
			activity,
			upstreamBuffer,
		)
		results <- pumpDirectionResult{
			direction: pumpUpstreamToDownstream,
			bytes:     n,
			err:       err,
			dst:       downstream,
		}
	}()

	idleTimer := time.NewTimer(limits.IdleTimeout)
	sessionTimer := time.NewTimer(limits.MaxSessionDuration)
	defer stopTimer(idleTimer)
	defer stopTimer(sessionTimer)

	var result PumpResult
	var terminalErr error
	completed := 0
	ctxDone := ctx.Done()
	idleDone := idleTimer.C
	sessionDone := sessionTimer.C
	terminate := func(err error) {
		if terminalErr != nil {
			return
		}
		terminalErr = err
		ctxDone = nil
		idleDone = nil
		sessionDone = nil
		closeBoth()
	}

	for completed < 2 {
		select {
		case directionResult := <-results:
			completed++
			switch directionResult.direction {
			case pumpDownstreamToUpstream:
				result.DownstreamToUpstreamBytes = directionResult.bytes
			case pumpUpstreamToDownstream:
				result.UpstreamToDownstreamBytes = directionResult.bytes
			}

			if directionResult.err != nil {
				if terminalErr == nil {
					if errors.Is(directionResult.err, errPumpByteLimit) {
						terminate(errPumpByteLimit)
					} else {
						terminate(errPumpIO)
					}
				}
				continue
			}
			if terminalErr == nil {
				writer, ok := directionResult.dst.(closeWriter)
				if !ok || writer.CloseWrite() != nil {
					terminate(errPumpIO)
				}
			}

		case <-activity:
			if terminalErr == nil {
				resetTimer(idleTimer, limits.IdleTimeout)
			}

		case <-idleDone:
			terminate(errPumpIdleTimeout)

		case <-sessionDone:
			terminate(errPumpSessionDuration)

		case <-ctxDone:
			terminate(ctx.Err())
		}
	}

	workers.Wait()
	closeBoth()
	return result, terminalErr
}

func validPumpParameters(
	ctx context.Context,
	downstream net.Conn,
	downstreamReader io.Reader,
	upstream net.Conn,
	limits PumpLimits,
	bufferFactory func() []byte,
) bool {
	return ctx != nil && downstream != nil && downstreamReader != nil && upstream != nil && bufferFactory != nil &&
		limits.IdleTimeout > 0 && limits.IdleTimeout <= maxPumpIdleTimeout &&
		limits.MaxSessionDuration > 0 && limits.MaxSessionDuration <= maxPumpSessionDuration &&
		limits.MaxBytesPerDirection > 0 && limits.MaxBytesPerDirection <= maxPumpBytesPerDirection
}

func copyPumpDirection(
	src io.Reader,
	dst io.Writer,
	maxBytes int64,
	activity chan<- struct{},
	buffer []byte,
) (int64, error) {
	var copied int64
	emptyReads := 0

	for {
		remaining := maxBytes - copied
		readSize := len(buffer)
		if remaining < int64(readSize) {
			readSize = int(remaining) + 1
		}

		n, readErr := src.Read(buffer[:readSize])
		if n > 0 {
			emptyReads = 0
			allowed := n
			exceeded := int64(n) > remaining
			if exceeded {
				allowed = int(remaining)
			}
			if allowed > 0 {
				written, writeErr := writeAll(dst, buffer[:allowed])
				copied += int64(written)
				if written > 0 {
					notePumpActivity(activity)
				}
				if writeErr != nil {
					clear(buffer[:n])
					return copied, errPumpIO
				}
			}
			clear(buffer[:n])
			if exceeded {
				return copied, errPumpByteLimit
			}
		} else {
			emptyReads++
		}

		if readErr == io.EOF {
			return copied, nil
		}
		if readErr != nil {
			return copied, errPumpIO
		}
		if emptyReads >= maxConsecutiveEmptyReads {
			return copied, errPumpIO
		}
	}
}

func writeAll(dst io.Writer, data []byte) (int, error) {
	written := 0
	for written < len(data) {
		n, err := dst.Write(data[written:])
		written += n
		if err != nil {
			return written, err
		}
		if n == 0 {
			return written, io.ErrNoProgress
		}
	}
	return written, nil
}

func notePumpActivity(activity chan<- struct{}) {
	select {
	case activity <- struct{}{}:
	default:
	}
}

func resetTimer(timer *time.Timer, duration time.Duration) {
	if !timer.Stop() {
		select {
		case <-timer.C:
		default:
		}
	}
	timer.Reset(duration)
}

func stopTimer(timer *time.Timer) {
	if !timer.Stop() {
		select {
		case <-timer.C:
		default:
		}
	}
}
