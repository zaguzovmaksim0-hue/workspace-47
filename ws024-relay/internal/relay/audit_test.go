package relay

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestNewAuditRecordStoresOnlyClosedBucketsAndFixedResultCode(t *testing.T) {
	record, err := NewAuditRecord(
		AuditResultPumpByteLimit,
		12*time.Second+345*time.Millisecond+678*time.Microsecond,
		PumpResult{
			DownstreamToUpstreamBytes: 12_345,
			UpstreamToDownstreamBytes: 987_654,
		},
	)
	if err != nil {
		t.Fatalf("NewAuditRecord() error = %v", err)
	}

	encoded, err := json.Marshal(record)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}
	var fields map[string]any
	if err := json.Unmarshal(encoded, &fields); err != nil {
		t.Fatalf("Unmarshal() error = %v", err)
	}
	wantKeys := map[string]bool{
		"protocol_version":              true,
		"result":                        true,
		"duration_bucket":               true,
		"downstream_to_upstream_bucket": true,
		"upstream_to_downstream_bucket": true,
	}
	if len(fields) != len(wantKeys) {
		t.Fatalf("audit fields = %v, want exactly %v", fields, wantKeys)
	}
	for key := range fields {
		if !wantKeys[key] {
			t.Fatalf("unexpected audit field %q", key)
		}
	}
	if got, want := fields["protocol_version"], "1"; got != want {
		t.Fatalf("protocol version = %v, want %v", got, want)
	}
	if got, want := fields["result"], "pump_byte_limit"; got != want {
		t.Fatalf("result = %v, want %v", got, want)
	}
	if got, want := fields["duration_bucket"], "10_30s"; got != want {
		t.Fatalf("duration bucket = %v, want %v", got, want)
	}
	if got, want := fields["downstream_to_upstream_bucket"], "4_64kib"; got != want {
		t.Fatalf("downstream bucket = %v, want %v", got, want)
	}
	if got, want := fields["upstream_to_downstream_bucket"], "64kib_1mib"; got != want {
		t.Fatalf("upstream bucket = %v, want %v", got, want)
	}

	text := string(encoded)
	for _, forbidden := range []string{
		"12345",
		"987654",
		"12345678",
		FixedAuthority,
		"Authorization",
		"Bearer",
		"credential",
		"certificate",
		"payload",
		"request_id",
		"uuid",
		"peer",
		"ip",
	} {
		if strings.Contains(strings.ToLower(text), strings.ToLower(forbidden)) {
			t.Fatalf("audit output contains forbidden exact/sensitive value %q: %s", forbidden, text)
		}
	}
}

func TestAuditDurationBucketsHaveCoarseDeterministicBoundaries(t *testing.T) {
	for _, tc := range []struct {
		duration time.Duration
		want     string
	}{
		{0, "lt_1s"},
		{time.Second - time.Nanosecond, "lt_1s"},
		{time.Second, "1_10s"},
		{10*time.Second - time.Nanosecond, "1_10s"},
		{10 * time.Second, "10_30s"},
		{30*time.Second - time.Nanosecond, "10_30s"},
		{30 * time.Second, "30_60s"},
		{60*time.Second - time.Nanosecond, "30_60s"},
		{60 * time.Second, "ge_60s"},
		{90 * time.Second, "ge_60s"},
		{10 * time.Minute, "ge_60s"},
	} {
		t.Run(tc.duration.String(), func(t *testing.T) {
			record, err := NewAuditRecord(AuditResultSuccess, tc.duration, PumpResult{})
			if err != nil {
				t.Fatalf("NewAuditRecord() error = %v", err)
			}
			fields := decodeAuditRecord(t, record)
			if got := fields["duration_bucket"]; got != tc.want {
				t.Fatalf("duration bucket = %v, want %q", got, tc.want)
			}
		})
	}
}

func TestAuditByteBucketsHaveCoarseDeterministicBoundaries(t *testing.T) {
	for _, tc := range []struct {
		bytes int64
		want  string
	}{
		{0, "zero"},
		{1, "1_4kib"},
		{4 * 1024, "1_4kib"},
		{4*1024 + 1, "4_64kib"},
		{64 * 1024, "4_64kib"},
		{64*1024 + 1, "64kib_1mib"},
		{1024 * 1024, "64kib_1mib"},
		{1024*1024 + 1, "1_4mib"},
		{maxPumpBytesPerDirection, "1_4mib"},
	} {
		t.Run(fmt.Sprintf("%d", tc.bytes), func(t *testing.T) {
			record, err := NewAuditRecord(
				AuditResultSuccess,
				time.Second,
				PumpResult{
					DownstreamToUpstreamBytes: tc.bytes,
					UpstreamToDownstreamBytes: tc.bytes,
				},
			)
			if err != nil {
				t.Fatalf("NewAuditRecord() error = %v", err)
			}
			fields := decodeAuditRecord(t, record)
			if got := fields["downstream_to_upstream_bucket"]; got != tc.want {
				t.Fatalf("downstream bucket = %v, want %q", got, tc.want)
			}
			if got := fields["upstream_to_downstream_bucket"]; got != tc.want {
				t.Fatalf("upstream bucket = %v, want %q", got, tc.want)
			}
		})
	}
}

func TestAuditResultCodesAreClosedAndDeterministic(t *testing.T) {
	cases := map[AuditResultCode]string{
		AuditResultSuccess:             "success",
		AuditResultProtocolRejected:    "protocol_rejected",
		AuditResultCredentialRejected:  "credential_rejected",
		AuditResultAdmissionRejected:   "admission_rejected",
		AuditResultUpstreamUnavailable: "upstream_unavailable",
		AuditResultPumpIdleTimeout:     "pump_idle_timeout",
		AuditResultPumpSessionLimit:    "pump_session_limit",
		AuditResultPumpByteLimit:       "pump_byte_limit",
		AuditResultPumpIOFailure:       "pump_io_failure",
		AuditResultInternalFailure:     "internal_failure",
	}
	for code, want := range cases {
		record, err := NewAuditRecord(code, 0, PumpResult{})
		if err != nil {
			t.Fatalf("NewAuditRecord(%d) error = %v", code, err)
		}
		if got := decodeAuditRecord(t, record)["result"]; got != want {
			t.Fatalf("result for %d = %v, want %q", code, got, want)
		}
	}
}

func TestAuditRejectsInvalidResultDurationAndExactCounts(t *testing.T) {
	for _, tc := range []struct {
		name     string
		code     AuditResultCode
		duration time.Duration
		result   PumpResult
	}{
		{"zero result code", 0, 0, PumpResult{}},
		{"unknown result code", AuditResultCode(255), 0, PumpResult{}},
		{"negative duration", AuditResultSuccess, -time.Nanosecond, PumpResult{}},
		{"negative downstream bytes", AuditResultSuccess, 0, PumpResult{DownstreamToUpstreamBytes: -1}},
		{"negative upstream bytes", AuditResultSuccess, 0, PumpResult{UpstreamToDownstreamBytes: -1}},
		{"downstream above design maximum", AuditResultSuccess, 0, PumpResult{DownstreamToUpstreamBytes: maxPumpBytesPerDirection + 1}},
		{"upstream above design maximum", AuditResultSuccess, 0, PumpResult{UpstreamToDownstreamBytes: maxPumpBytesPerDirection + 1}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, err := NewAuditRecord(tc.code, tc.duration, tc.result)
			if !errors.Is(err, errInvalidAuditRecord) {
				t.Fatalf("NewAuditRecord() error = %v, want invalid audit record", err)
			}
		})
	}
}

func TestZeroValueAuditRecordCannotBeSerializedOrRecorded(t *testing.T) {
	if _, err := json.Marshal(AuditRecord{}); !errors.Is(err, errInvalidAuditRecord) {
		t.Fatalf("Marshal(zero record) error = %v, want invalid audit record", err)
	}
	var output bytes.Buffer
	audit, err := NewSafeAudit(&output)
	if err != nil {
		t.Fatalf("NewSafeAudit() error = %v", err)
	}
	if err := audit.Record(AuditRecord{}); !errors.Is(err, errInvalidAuditRecord) {
		t.Fatalf("Record(zero record) error = %v, want invalid audit record", err)
	}
	if output.Len() != 0 {
		t.Fatalf("invalid record wrote %d bytes", output.Len())
	}
}

func TestAuditRecordHasNoExportedMutableFields(t *testing.T) {
	typeOfRecord := reflect.TypeOf(AuditRecord{})
	for i := 0; i < typeOfRecord.NumField(); i++ {
		field := typeOfRecord.Field(i)
		if field.IsExported() {
			t.Fatalf("AuditRecord field %q is exported and mutable", field.Name)
		}
	}
}

func TestSafeAuditWritesOneDeterministicJSONLinePerConcurrentRecord(t *testing.T) {
	var output bytes.Buffer
	audit, err := NewSafeAudit(&output)
	if err != nil {
		t.Fatalf("NewSafeAudit() error = %v", err)
	}
	record, err := NewAuditRecord(
		AuditResultSuccess,
		5*time.Second,
		PumpResult{DownstreamToUpstreamBytes: 128, UpstreamToDownstreamBytes: 256},
	)
	if err != nil {
		t.Fatalf("NewAuditRecord() error = %v", err)
	}

	const records = 100
	var workers sync.WaitGroup
	workers.Add(records)
	for i := 0; i < records; i++ {
		go func() {
			defer workers.Done()
			if err := audit.Record(record); err != nil {
				t.Errorf("Record() error = %v", err)
			}
		}()
	}
	workers.Wait()

	lines := strings.Split(strings.TrimSuffix(output.String(), "\n"), "\n")
	if len(lines) != records {
		t.Fatalf("audit line count = %d, want %d", len(lines), records)
	}
	for i, line := range lines {
		var fields map[string]any
		if err := json.Unmarshal([]byte(line), &fields); err != nil {
			t.Fatalf("line %d is not one complete JSON object: %v", i, err)
		}
		if len(fields) != 5 || fields["result"] != "success" {
			t.Fatalf("line %d fields = %v", i, fields)
		}
	}
}

func TestSafeAuditHandlesShortWritesAndTerminatesEachRecordWithNewline(t *testing.T) {
	writer := &shortAuditWriter{maxPerWrite: 1}
	audit, err := NewSafeAudit(writer)
	if err != nil {
		t.Fatalf("NewSafeAudit() error = %v", err)
	}
	record, err := NewAuditRecord(AuditResultSuccess, 0, PumpResult{})
	if err != nil {
		t.Fatalf("NewAuditRecord() error = %v", err)
	}
	if err := audit.Record(record); err != nil {
		t.Fatalf("Record() error = %v", err)
	}
	if !bytes.HasSuffix(writer.Bytes(), []byte("\n")) {
		t.Fatalf("audit output lacks newline: %q", writer.Bytes())
	}
	var fields map[string]any
	if err := json.Unmarshal(bytes.TrimSuffix(writer.Bytes(), []byte("\n")), &fields); err != nil {
		t.Fatalf("audit output is invalid JSON: %v", err)
	}
}

func TestSafeAuditPermanentlyFailsClosedAfterPartialWriterError(t *testing.T) {
	writer := &recoveringPartialAuditWriter{firstWriteBytes: 7}
	audit, err := NewSafeAudit(writer)
	if err != nil {
		t.Fatalf("NewSafeAudit() error = %v", err)
	}
	record, err := NewAuditRecord(AuditResultSuccess, 0, PumpResult{})
	if err != nil {
		t.Fatalf("NewAuditRecord() error = %v", err)
	}

	if err := audit.Record(record); !errors.Is(err, errAuditWrite) {
		t.Fatalf("first Record() error = %v, want audit write failure", err)
	}
	partialLength := writer.Len()
	if partialLength == 0 {
		t.Fatal("test writer did not create a partial line")
	}
	if err := audit.Record(record); !errors.Is(err, errAuditWrite) {
		t.Fatalf("second Record() error = %v, want permanent audit write failure", err)
	}
	if writer.Len() != partialLength {
		t.Fatalf("failed audit accepted more bytes: before=%d after=%d", partialLength, writer.Len())
	}
}

func TestSafeAuditMapsWriterFailureToGenericErrorWithoutLeakingCause(t *testing.T) {
	const secret = "writer-error-containing-token-and-peer"
	audit, err := NewSafeAudit(failingAuditWriter{err: errors.New(secret)})
	if err != nil {
		t.Fatalf("NewSafeAudit() error = %v", err)
	}
	record, err := NewAuditRecord(AuditResultInternalFailure, 0, PumpResult{})
	if err != nil {
		t.Fatalf("NewAuditRecord() error = %v", err)
	}
	err = audit.Record(record)
	if !errors.Is(err, errAuditWrite) {
		t.Fatalf("Record() error = %v, want generic audit write error", err)
	}
	if strings.Contains(err.Error(), secret) {
		t.Fatalf("audit error leaks writer cause: %q", err)
	}
}

func TestNewSafeAuditRejectsTypedNilWriter(t *testing.T) {
	var writer *bytes.Buffer
	audit, err := NewSafeAudit(writer)
	if audit != nil || !errors.Is(err, errInvalidAuditWriter) {
		t.Fatalf("NewSafeAudit(typed nil) = (%v, %v), want generic rejection", audit, err)
	}
}

func TestNewSafeAuditRejectsNilWriter(t *testing.T) {
	audit, err := NewSafeAudit(nil)
	if audit != nil || !errors.Is(err, errInvalidAuditWriter) {
		t.Fatalf("NewSafeAudit(nil) = (%v, %v), want generic rejection", audit, err)
	}
}

func decodeAuditRecord(t *testing.T, record AuditRecord) map[string]any {
	t.Helper()
	encoded, err := json.Marshal(record)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}
	var fields map[string]any
	if err := json.Unmarshal(encoded, &fields); err != nil {
		t.Fatalf("Unmarshal() error = %v", err)
	}
	return fields
}

type shortAuditWriter struct {
	bytes.Buffer
	maxPerWrite int
}

func (w *shortAuditWriter) Write(p []byte) (int, error) {
	if len(p) > w.maxPerWrite {
		p = p[:w.maxPerWrite]
	}
	return w.Buffer.Write(p)
}

type failingAuditWriter struct {
	err error
}

func (w failingAuditWriter) Write([]byte) (int, error) {
	return 0, w.err
}

type recoveringPartialAuditWriter struct {
	bytes.Buffer
	firstWriteBytes int
	failedOnce      bool
}

func (w *recoveringPartialAuditWriter) Write(p []byte) (int, error) {
	if !w.failedOnce {
		w.failedOnce = true
		if len(p) > w.firstWriteBytes {
			p = p[:w.firstWriteBytes]
		}
		n, _ := w.Buffer.Write(p)
		return n, errors.New("synthetic partial writer failure")
	}
	return w.Buffer.Write(p)
}
