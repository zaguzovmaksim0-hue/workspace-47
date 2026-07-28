package relay

import (
	"encoding/json"
	"errors"
	"io"
	"reflect"
	"sync"
	"time"
)

var (
	errInvalidAuditRecord = errors.New("invalid audit record")
	errInvalidAuditWriter = errors.New("invalid audit writer")
	errAuditWrite         = errors.New("audit write failed")
)

// AuditResultCode is a closed set of non-sensitive relay outcomes. Values are
// validated before a record can be created or serialized.
type AuditResultCode uint8

const (
	AuditResultSuccess AuditResultCode = iota + 1
	AuditResultProtocolRejected
	AuditResultCredentialRejected
	AuditResultAdmissionRejected
	AuditResultUpstreamUnavailable
	AuditResultPumpIdleTimeout
	AuditResultPumpSessionLimit
	AuditResultPumpByteLimit
	AuditResultPumpIOFailure
	AuditResultInternalFailure
)

type auditDurationBucket uint8

const (
	auditDurationLessThanOneSecond auditDurationBucket = iota + 1
	auditDurationOneToTenSeconds
	auditDurationTenToThirtySeconds
	auditDurationThirtyToSixtySeconds
	auditDurationAtLeastSixtySeconds
)

type auditByteBucket uint8

const (
	auditBytesZero auditByteBucket = iota + 1
	auditBytesOneToFourKiB
	auditBytesFourToSixtyFourKiB
	auditBytesSixtyFourKiBToOneMiB
	auditBytesOneToFourMiB
)

// AuditRecord is immutable outside this package. It stores only closed enums
// and buckets; exact duration, exact byte counts, peers, credentials, payload,
// authority input, certificates, and request identifiers are discarded by the
// constructor.
type AuditRecord struct {
	protocolVersion  string
	result           AuditResultCode
	duration         auditDurationBucket
	downstreamBucket auditByteBucket
	upstreamBucket   auditByteBucket
}

// NewAuditRecord immediately reduces exact in-memory measurements to coarse
// buckets. The returned record cannot be mutated into an arbitrary log entry.
func NewAuditRecord(
	result AuditResultCode,
	duration time.Duration,
	pump PumpResult,
) (AuditRecord, error) {
	if !validAuditResult(result) || duration < 0 ||
		pump.DownstreamToUpstreamBytes < 0 ||
		pump.DownstreamToUpstreamBytes > maxPumpBytesPerDirection ||
		pump.UpstreamToDownstreamBytes < 0 ||
		pump.UpstreamToDownstreamBytes > maxPumpBytesPerDirection {
		return AuditRecord{}, errInvalidAuditRecord
	}
	return AuditRecord{
		protocolVersion:  TunnelProtocolVersion,
		result:           result,
		duration:         durationBucket(duration),
		downstreamBucket: byteBucket(pump.DownstreamToUpstreamBytes),
		upstreamBucket:   byteBucket(pump.UpstreamToDownstreamBytes),
	}, nil
}

// MarshalJSON emits exactly five fixed fields. No caller-provided string is
// accepted anywhere in AuditRecord.
func (record AuditRecord) MarshalJSON() ([]byte, error) {
	if !record.valid() {
		return nil, errInvalidAuditRecord
	}
	return json.Marshal(struct {
		ProtocolVersion            string `json:"protocol_version"`
		Result                     string `json:"result"`
		DurationBucket             string `json:"duration_bucket"`
		DownstreamToUpstreamBucket string `json:"downstream_to_upstream_bucket"`
		UpstreamToDownstreamBucket string `json:"upstream_to_downstream_bucket"`
	}{
		ProtocolVersion:            TunnelProtocolVersion,
		Result:                     auditResultName(record.result),
		DurationBucket:             durationBucketName(record.duration),
		DownstreamToUpstreamBucket: byteBucketName(record.downstreamBucket),
		UpstreamToDownstreamBucket: byteBucketName(record.upstreamBucket),
	})
}

func (record AuditRecord) valid() bool {
	return record.protocolVersion == TunnelProtocolVersion &&
		validAuditResult(record.result) &&
		validDurationBucket(record.duration) &&
		validByteBucket(record.downstreamBucket) &&
		validByteBucket(record.upstreamBucket)
}

// SafeAudit serializes records as complete JSON lines under one lock so
// concurrent sessions cannot interleave output.
type SafeAudit struct {
	mu     sync.Mutex
	writer io.Writer
	failed bool
}

func NewSafeAudit(writer io.Writer) (*SafeAudit, error) {
	if nilInterface(writer) {
		return nil, errInvalidAuditWriter
	}
	return &SafeAudit{writer: writer}, nil
}

func (audit *SafeAudit) Record(record AuditRecord) error {
	if audit == nil || nilInterface(audit.writer) {
		return errInvalidAuditWriter
	}
	encoded, err := record.MarshalJSON()
	if err != nil {
		return err
	}
	encoded = append(encoded, '\n')
	defer clear(encoded)

	audit.mu.Lock()
	defer audit.mu.Unlock()
	if audit.failed {
		return errAuditWrite
	}
	if _, writeErr := writeAll(audit.writer, encoded); writeErr != nil {
		audit.failed = true
		return errAuditWrite
	}
	return nil
}

func nilInterface(value any) bool {
	if value == nil {
		return true
	}
	reflected := reflect.ValueOf(value)
	switch reflected.Kind() {
	case reflect.Chan, reflect.Func, reflect.Interface, reflect.Map, reflect.Ptr, reflect.Slice:
		return reflected.IsNil()
	default:
		return false
	}
}

func validAuditResult(result AuditResultCode) bool {
	switch result {
	case AuditResultSuccess,
		AuditResultProtocolRejected,
		AuditResultCredentialRejected,
		AuditResultAdmissionRejected,
		AuditResultUpstreamUnavailable,
		AuditResultPumpIdleTimeout,
		AuditResultPumpSessionLimit,
		AuditResultPumpByteLimit,
		AuditResultPumpIOFailure,
		AuditResultInternalFailure:
		return true
	default:
		return false
	}
}

func auditResultName(result AuditResultCode) string {
	switch result {
	case AuditResultSuccess:
		return "success"
	case AuditResultProtocolRejected:
		return "protocol_rejected"
	case AuditResultCredentialRejected:
		return "credential_rejected"
	case AuditResultAdmissionRejected:
		return "admission_rejected"
	case AuditResultUpstreamUnavailable:
		return "upstream_unavailable"
	case AuditResultPumpIdleTimeout:
		return "pump_idle_timeout"
	case AuditResultPumpSessionLimit:
		return "pump_session_limit"
	case AuditResultPumpByteLimit:
		return "pump_byte_limit"
	case AuditResultPumpIOFailure:
		return "pump_io_failure"
	case AuditResultInternalFailure:
		return "internal_failure"
	default:
		return ""
	}
}

func durationBucket(duration time.Duration) auditDurationBucket {
	switch {
	case duration < time.Second:
		return auditDurationLessThanOneSecond
	case duration < 10*time.Second:
		return auditDurationOneToTenSeconds
	case duration < 30*time.Second:
		return auditDurationTenToThirtySeconds
	case duration < 60*time.Second:
		return auditDurationThirtyToSixtySeconds
	default:
		return auditDurationAtLeastSixtySeconds
	}
}

func validDurationBucket(bucket auditDurationBucket) bool {
	return bucket >= auditDurationLessThanOneSecond && bucket <= auditDurationAtLeastSixtySeconds
}

func durationBucketName(bucket auditDurationBucket) string {
	switch bucket {
	case auditDurationLessThanOneSecond:
		return "lt_1s"
	case auditDurationOneToTenSeconds:
		return "1_10s"
	case auditDurationTenToThirtySeconds:
		return "10_30s"
	case auditDurationThirtyToSixtySeconds:
		return "30_60s"
	case auditDurationAtLeastSixtySeconds:
		return "ge_60s"
	default:
		return ""
	}
}

func byteBucket(count int64) auditByteBucket {
	switch {
	case count == 0:
		return auditBytesZero
	case count <= 4*1024:
		return auditBytesOneToFourKiB
	case count <= 64*1024:
		return auditBytesFourToSixtyFourKiB
	case count <= 1024*1024:
		return auditBytesSixtyFourKiBToOneMiB
	default:
		return auditBytesOneToFourMiB
	}
}

func validByteBucket(bucket auditByteBucket) bool {
	return bucket >= auditBytesZero && bucket <= auditBytesOneToFourMiB
}

func byteBucketName(bucket auditByteBucket) string {
	switch bucket {
	case auditBytesZero:
		return "zero"
	case auditBytesOneToFourKiB:
		return "1_4kib"
	case auditBytesFourToSixtyFourKiB:
		return "4_64kib"
	case auditBytesSixtyFourKiBToOneMiB:
		return "64kib_1mib"
	case auditBytesOneToFourMiB:
		return "1_4mib"
	default:
		return ""
	}
}
